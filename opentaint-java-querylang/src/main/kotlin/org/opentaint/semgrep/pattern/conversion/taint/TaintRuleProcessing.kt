package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.org.opentaint.semgrep.pattern.Mark
import org.opentaint.org.opentaint.semgrep.pattern.Mark.Companion.markNamePrefix
import org.opentaint.semgrep.pattern.GeneratedTaintMark
import org.opentaint.semgrep.pattern.NoRequirement
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepSinkTaintRequirement
import org.opentaint.semgrep.pattern.SemgrepTaintAnd
import org.opentaint.semgrep.pattern.SemgrepTaintLabel
import org.opentaint.semgrep.pattern.SemgrepTaintNot
import org.opentaint.semgrep.pattern.SemgrepTaintOr
import org.opentaint.semgrep.pattern.SemgrepTaintRequires
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State

fun RuleConversionCtx.convertTaintRuleToTaintRules(
    rule: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
): TaintRuleFromSemgrep {
    val rulesWithVars = prepareTaintRules(rule)
    return convertTaintRuleToTaintRules(rulesWithVars, ignoreEmptySources = false)
}

fun RuleConversionCtx.convertTaintRuleToTaintRules(
    rulesWithVars: ProcessedTaintRule<RuleWithMetaVars<TaintRegisterStateAutomataWithStateVars, ResolvedMetaVarInfo>>,
    ignoreEmptySources: Boolean
): TaintRuleFromSemgrep {
    val taintAutomata = rulesWithVars.flatMap {
        safeConvertToTaintRules {
            listOf(generateTaintAutomataEdges(it.rule, it.metaVarInfo))
        }.orEmpty()
    }

    val taintCtx = generateEdgeCtx(taintAutomata)

    if (taintCtx.source.isEmpty() && !ignoreEmptySources) {
        trace.error("Taint rule without sources", Reason.WARNING)
    }

    val taintRules = taintCtx.flatMap {
        safeConvertToTaintRules {
            val generatedRules = it.generateTaintRules(this)
            val filteredRules = generatedRules.filter { r ->
                if (r !is SinkRule) return@filter true
                if (r.condition != null && r.condition !is SerializedCondition.True) return@filter true

                trace.error("Taint rule match anything", Reason.WARNING)
                false
            }
            listOf(TaintRuleFromSemgrep.TaintRuleGroup(filteredRules))
        }.orEmpty()
    }

    val ruleGroups = mutableListOf<TaintRuleFromSemgrep.TaintRuleGroup>()
    taintRules.source.mapTo(ruleGroups) { it.rule }
    taintRules.sink.mapTo(ruleGroups) { it.rule }
    taintRules.pass.mapTo(ruleGroups) { it.rule }
    taintRules.clean.mapTo(ruleGroups) { it.rule }
    return TaintRuleFromSemgrep(fullRuleId, ruleGroups)
}

data class ProcessedTaintSourceRule<R>(
    val rule: R,
    val taintedVars: Set<MetavarAtom>,
    val requires: TaintMarkCheckBuilder?,
    val label: String
) {
    fun <T> flatMap(body: (R) -> List<T>): List<ProcessedTaintSourceRule<T>> =
        body(rule).map { ProcessedTaintSourceRule(it, taintedVars, requires, label) }
}

data class ProcessedTaintSinkRule<R>(
    val rule: R,
    val requires: TaintMarkCheckBuilder,
) {
    fun <T> flatMap(body: (R) -> List<T>): List<ProcessedTaintSinkRule<T>> =
        body(rule).map { ProcessedTaintSinkRule(it, requires) }
}

data class ProcessedTaintPassRule<R>(
    val rule: R,
    val propagates: Map<String, TaintMarkCheckBuilder>,
) {
    fun <T> flatMap(body: (R) -> List<T>): List<ProcessedTaintPassRule<T>> =
        body(rule).map { ProcessedTaintPassRule(it, propagates) }
}

data class ProcessedTaintCleanRule<R>(
    val rule: R,
    val cleans: Set<String>
) {
    fun <T> flatMap(body: (R) -> List<T>): List<ProcessedTaintCleanRule<T>> =
        body(rule).map { ProcessedTaintCleanRule(it, cleans) }
}

data class ProcessedTaintRule<R>(
    val source: List<ProcessedTaintSourceRule<R>>,
    val sink: List<ProcessedTaintSinkRule<R>>,
    val pass: List<ProcessedTaintPassRule<R>>,
    val clean: List<ProcessedTaintCleanRule<R>>
) {
    fun <T> flatMap(body: (R) -> List<T>): ProcessedTaintRule<T> = ProcessedTaintRule(
        source = source.flatMap { it.flatMap(body) },
        sink = sink.flatMap { it.flatMap(body) },
        pass = pass.flatMap { it.flatMap(body) },
        clean = clean.flatMap { it.flatMap(body) },
    )
}

private fun ProcessedTaintSourceRule<TaintAutomataEdges>.compositionStrategy() =
    object : TaintRuleGenerationCtx.CompositionStrategy {
        private val initialStateId = rule.automata.stateId(rule.automata.initial)

        override fun stateContains(
            state: State,
            varName: MetavarAtom,
            pos: PositionBaseWithModifiers
        ): SerializedCondition? {
            if (requires == null) return null

            val value = state.register.assignedVars[varName]
            if (value != initialStateId) return null

            return requires.build(pos)
        }

        override fun stateAssign(
            state: State,
            varName: MetavarAtom,
            pos: PositionBaseWithModifiers
        ): List<SerializedTaintAssignAction>? {
            if (state !in rule.automata.finalAcceptStates) return null
            if (varName !in taintedVars) return null
            return listOf(SerializedTaintAssignAction(label, pos = pos))
        }

        override fun stateAccessedMarks(state: State, varName: MetavarAtom): Set<String>? {
            if (requires == null) return null

            val value = state.register.assignedVars[varName]
            if (value == initialStateId) {
                return requires.collectLabels(hashSetOf())
            }

            if (state in rule.automata.finalAcceptStates) {
                if (varName in taintedVars) {
                    return setOf(label)
                }
            }

            return null
        }
    }

private fun ProcessedTaintSinkRule<TaintAutomataEdges>.compositionStrategy() =
    object : TaintRuleGenerationCtx.CompositionStrategy {
        private val initialStateId = rule.automata.stateId(rule.automata.initial)

        override fun stateContains(
            state: State,
            varName: MetavarAtom,
            pos: PositionBaseWithModifiers
        ): SerializedCondition? {
            val value = state.register.assignedVars[varName]
            if (value != initialStateId) return null
            return requires.build(pos)
        }

        override fun stateAccessedMarks(state: State, varName: MetavarAtom): Set<String>? {
            val value = state.register.assignedVars[varName]
            if (value != initialStateId) return null
            return requires.collectLabels(hashSetOf())
        }
    }


private fun ProcessedTaintPassRule<TaintAutomataEdges>.compositionStrategy(
    markName: String,
    markRequires: TaintMarkCheckBuilder
) = object : TaintRuleGenerationCtx.CompositionStrategy {
    private val initialStateId = rule.automata.stateId(rule.automata.initial)

    override fun stateContains(
        state: State,
        varName: MetavarAtom,
        pos: PositionBaseWithModifiers
    ): SerializedCondition? {
        val value = state.register.assignedVars[varName]
        if (value != initialStateId) return null
        return markRequires.build(pos)
    }

    override fun stateAssign(
        state: State,
        varName: MetavarAtom,
        pos: PositionBaseWithModifiers
    ): List<SerializedTaintAssignAction>? {
        if (state !in rule.automata.finalAcceptStates) return null
        return listOf(SerializedTaintAssignAction(markName, pos = pos))
    }

    override fun stateAccessedMarks(state: State, varName: MetavarAtom): Set<String>? {
        val value = state.register.assignedVars[varName]
        if (value == initialStateId) {
            return markRequires.collectLabels(hashSetOf())
        }
        if (state in rule.automata.finalAcceptStates) {
            return setOf(markName)
        }
        return null
    }
}

private fun ProcessedTaintCleanRule<TaintAutomataEdges>.compositionStrategy() =
    object : TaintRuleGenerationCtx.CompositionStrategy {
        override fun stateClean(
            state: State,
            stateBefore: State,
            varName: MetavarAtom?,
            pos: PositionBaseWithModifiers?
        ): List<SerializedTaintCleanAction>? {
            if (state !in rule.automata.finalAcceptStates) return null

            val cleanerPos = PositionBase.AnyArgument(classifier = "tainted").base()
            return cleans.map { SerializedTaintCleanAction(it, pos = cleanerPos) }
        }

        override fun stateAccessedMarks(state: State, varName: MetavarAtom): Set<String>? {
            if (state !in rule.automata.finalAcceptStates) return null
            return cleans
        }
    }

private fun RuleConversionCtx.generateEdgeCtx(
    rule: ProcessedTaintRule<TaintAutomataEdges>
): ProcessedTaintRule<TaintRuleGenerationCtx> {
    val source = rule.source.flatMapIndexed { i, r ->
        val automata = r.rule
        val sourceAutomata = automata.copy(
            edges = automata.edges + automata.edgesToFinalAccept,
            edgesToFinalAccept = emptyList()
        )

        r.flatMap {
            TaintRuleGenerationCtx(
                uniqueRuleId = markNamePrefix(shortRuleId, "source_$i"),
                automataEdges = sourceAutomata,
                compositionStrategy = r.compositionStrategy()
            ).let { listOf(it) }
        }
    }

    val sink = rule.sink.flatMapIndexed { i, r ->
        r.flatMap {
            TaintRuleGenerationCtx(
                uniqueRuleId = markNamePrefix(shortRuleId, "sink_$i"),
                automataEdges = r.rule,
                compositionStrategy = r.compositionStrategy()
            ).let { listOf(it) }
        }
    }

    val pass = rule.pass.flatMapIndexed { i, r ->
        val automata = r.rule
        val passAutomata = automata.copy(
            edges = automata.edges + automata.edgesToFinalAccept,
            edgesToFinalAccept = emptyList()
        )

        r.flatMap {
            r.propagates.entries.mapIndexed { p, (markName, markCondition) ->
                TaintRuleGenerationCtx(
                    uniqueRuleId = markNamePrefix(shortRuleId, "pass_${i}_$p"),
                    automataEdges = passAutomata,
                    compositionStrategy = r.compositionStrategy(markName, markCondition)
                )
            }
        }
    }

    val clean = rule.clean.flatMapIndexed { i, r ->
        val automata = r.rule
        val cleanAutomata = automata.copy(
            edgesToFinalDead = automata.edgesToFinalDead + automata.edgesToFinalAccept,
            edgesToFinalAccept = emptyList()
        )

        r.flatMap {
            TaintRuleGenerationCtx(
                uniqueRuleId = markNamePrefix(shortRuleId, "clean_$i"),
                automataEdges = cleanAutomata,
                compositionStrategy = r.compositionStrategy()
            ).let { listOf(it) }
        }
    }

    return ProcessedTaintRule(source, sink, pass, clean)
}

fun RuleConversionCtx.taintMark(label: SemgrepTaintLabel): String {
    var labelSuffix = label.label
    if (labelSuffix.isNotBlank()) {
        labelSuffix = "_$labelSuffix"
    }
    return markNamePrefix(shortRuleId, "${Mark.GeneralTaintName}$labelSuffix")
}

fun RuleConversionCtx.prepareTaintRules(
    rule: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>
): ProcessedTaintRule<RuleWithMetaVars<TaintRegisterStateAutomataWithStateVars, ResolvedMetaVarInfo>> {
    val (sources, taintMarks) = prepareTaintSourceRules(rule)
    return prepareTaintNonSourceRules(rule, sources, taintMarks)
}

fun RuleConversionCtx.prepareTaintSourceRules(
    rule: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>
): Pair<List<ProcessedTaintSourceRule<RuleWithMetaVars<TaintRegisterStateAutomataWithStateVars, ResolvedMetaVarInfo>>>, Set<GeneratedTaintMark>> {
    val taintMarks = hashSetOf<GeneratedTaintMark>()
    val sources = rule.sources.map { source ->
        val taintMark = taintMark(source.label ?: SemgrepTaintLabel(""))
            .also { taintMarks.add(GeneratedTaintMark(it)) }

        val requiresCheck = source.requires?.let { createTaintMarkCheckBuilder(it, ::taintMark) }

        val sourceAutomata = source.pattern.map {
            prepareTaintSourceAutomata(source.pattern, generateRequires = requiresCheck != null)
        }

        ProcessedTaintSourceRule(
            sourceAutomata,
            sourceAutomata.rule.acceptStateVars,
            requiresCheck,
            taintMark
        )
    }
    return sources to taintMarks
}

fun RuleConversionCtx.prepareTaintNonSourceRules(
    rule: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    sources: List<ProcessedTaintSourceRule<RuleWithMetaVars<TaintRegisterStateAutomataWithStateVars, ResolvedMetaVarInfo>>>,
    taintMarks: Set<GeneratedTaintMark>
): ProcessedTaintRule<RuleWithMetaVars<TaintRegisterStateAutomataWithStateVars, ResolvedMetaVarInfo>> {
    val sinks = rule.sinks.map { sink ->
        var sinkRequiresExpr = when (sink.requires) {
            null -> taintMarkOr(taintMarks)
            is SemgrepSinkTaintRequirement.Simple -> sink.requires.requirement

            is SemgrepSinkTaintRequirement.MetaVarRequirement -> {
                trace.error("Sink requires ignored", Reason.NOT_IMPLEMENTED)
                taintMarkOr(taintMarks)
            }
        }

        if (sinkRequiresExpr == null) {
            trace.error("Taint rule has no labels", Reason.WARNING)
            sinkRequiresExpr = NoRequirement
        }

        val sinkRequiresCheck = createTaintMarkCheckBuilder(sinkRequiresExpr, ::taintMark)

        val sinkAutomata = sink.pattern.map {
            prepareTaintSinkAutomata(sink.pattern)
        }

        ProcessedTaintSinkRule(sinkAutomata, sinkRequiresCheck)
    }

    val pass = rule.propagators.map { pass ->
        val propagates = taintMarks.associate {
            it.mark to createTaintMarkCheckBuilder(it, ::taintMark)
        }

        val passAutomata = pass.pattern.map {
            val fromVar = MetavarAtom.create(pass.from)
            val toVar = MetavarAtom.create(pass.to)
            prepareTaintPassAutomata(pass.pattern, fromVar, toVar)
        }

        ProcessedTaintPassRule(passAutomata, propagates)
    }

    val cleaners = rule.sanitizers.map { clean ->
        // todo: sanitizer by side effect
        // todo: sanitizer focus metavar

        val generatedPos = MetavarAtom.create("generated_clean_pos")
        val cleanAutomata = clean.pattern.map {
            TaintRegisterStateAutomataWithStateVars(
                it,
                initialStateVars = setOf(generatedPos),
                acceptStateVars = setOf(generatedPos)
            )
        }

        ProcessedTaintCleanRule(cleanAutomata, taintMarks.mapTo(hashSetOf()) { it.mark })
    }

    return ProcessedTaintRule(sources, sinks, pass, cleaners)
}

private fun taintMarkOr(labels: Set<GeneratedTaintMark>): SemgrepTaintRequires? =
    labels.reduceOrNull<SemgrepTaintRequires, _> { acc, label -> SemgrepTaintOr(acc, label) }

private fun createTaintMarkCheckBuilder(
    requires: SemgrepTaintRequires,
    createTaineMark: (SemgrepTaintLabel) -> String,
): TaintMarkCheckBuilder = when (requires) {
    is SemgrepTaintLabel -> TaintMarkLabelCheckBuilder(createTaineMark(requires))

    is GeneratedTaintMark -> TaintMarkLabelCheckBuilder(requires.mark)

    is SemgrepTaintNot -> TaintMarkNotCheckBuilder(
        createTaintMarkCheckBuilder(requires.child, createTaineMark)
    )

    is SemgrepTaintAnd -> TaintMarkAndCheckBuilder(
        createTaintMarkCheckBuilder(requires.left, createTaineMark),
        createTaintMarkCheckBuilder(requires.right, createTaineMark),
    )

    is SemgrepTaintOr -> TaintMarkOrCheckBuilder(
        createTaintMarkCheckBuilder(requires.left, createTaineMark),
        createTaintMarkCheckBuilder(requires.right, createTaineMark),
    )

    is NoRequirement -> TaintMarkCheckNotRequiredBuilder
}

private fun prepareTaintPassAutomata(
    rule: RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>,
    fromMetaVar: MetavarAtom,
    toMetaVar: MetavarAtom
): TaintRegisterStateAutomataWithStateVars {
    val automataWithVars = TaintRegisterStateAutomataWithStateVars(
        rule.rule,
        initialStateVars = setOf(fromMetaVar),
        acceptStateVars = setOf(toMetaVar)
    )
    return automataWithVars
}

// todo: check sink behaviour with multiple focus meta vars
private fun prepareTaintSinkAutomata(
    rule: RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>,
): TaintRegisterStateAutomataWithStateVars {
    val (sinkAutomata, stateMetaVars) = ensureSinkStateVars(
        rule.rule,
        rule.metaVarInfo.focusMetaVars.map { MetavarAtom.create(it) }.toSet()
    )

    return TaintRegisterStateAutomataWithStateVars(
        sinkAutomata,
        initialStateVars = stateMetaVars, acceptStateVars = emptySet()
    )
}

private fun prepareTaintSourceAutomata(
    rule: RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>,
    generateRequires: Boolean
): TaintRegisterStateAutomataWithStateVars {
    val (rawSourceAutomata, stateMetaVars) = ensureSourceStateVars(
        rule.rule,
        rule.metaVarInfo.focusMetaVars.map { MetavarAtom.create(it) }.toSet()
    )

    val (sourceAutomata, requirementVars) = if (generateRequires) {
        ensureSinkStateVars(rawSourceAutomata, emptySet())
    } else {
        Pair(rawSourceAutomata, emptySet())
    }

    return TaintRegisterStateAutomataWithStateVars(
        sourceAutomata,
        initialStateVars = requirementVars,
        acceptStateVars = stateMetaVars
    )
}

private fun ensureSinkStateVars(
    automata: TaintRegisterStateAutomata,
    focusMetaVars: Set<MetavarAtom>
): Pair<TaintRegisterStateAutomata, Set<MetavarAtom>> {
    if (focusMetaVars.isNotEmpty()) return automata to focusMetaVars

    val freshVar = MetavarAtom.create("generated_sink_requirement")

    val newAutomata = TaintRegisterStateAutomataBuilder()
    val newInitialState = ensureSinkStateVars(freshVar, automata.initial, hashSetOf(), automata, newAutomata)

    check(newInitialState != null) {
        "unable to insert taint check"
    }

    val resultAutomata = newAutomata.build(automata.formulaManager, newInitialState)
    return resultAutomata to setOf(freshVar)
}

private fun ensureSinkStateVars(
    taintVar: MetavarAtom,
    state: State,
    processedStates: MutableSet<State>,
    current: TaintRegisterStateAutomata,
    newAutomata: TaintRegisterStateAutomataBuilder,
): State? {
    if (!processedStates.add(state)) return null

    if (state in current.finalAcceptStates || state in current.finalDeadStates) {
        return null
    }

    val currentStateSucc = current.successors[state] ?: return null

    val argumentIndex = Position.ArgumentIndex.Any(paramClassifier = "tainted")
    val expandPositions = listOf(
        Position.Argument(argumentIndex), Position.Object
    )

    val newSucc = hashSetOf<Pair<Edge, State>>()
    for ((edge, dst) in currentStateSucc) {
        ensureSinkStateVars(taintVar, dst, processedStates.toMutableSet(), current, newAutomata)?.let { newDst ->
            newSucc.add(edge to newDst)
        }

        when (edge) {
            is Edge.MethodCall -> {
                val positivePredicate = edge.condition.findPositivePredicate()
                    ?: continue

                for (pos in expandPositions) {
                    val conditionVars = edge.condition.readMetaVar.toMutableMap()
                    val condition = ParamConstraint(pos, IsMetavar(taintVar))
                    val predicate = Predicate(positivePredicate.signature, condition)

                    conditionVars[taintVar] = listOf(MethodPredicate(predicate, negated = false))
                    val edgeCondition = EdgeCondition(conditionVars, edge.condition.other)

                    val modifiedEdge = Edge.MethodCall(edgeCondition, edge.effect)
                    val dstWithTaint = forkState(dst, current, hashMapOf(), newAutomata)

                    newSucc.add(modifiedEdge to dstWithTaint)
                }
            }

            is Edge.AnalysisEnd,
            is Edge.MethodEnter,
            is Edge.MethodExit -> continue
        }
    }

    newAutomata.successors[state] = newSucc
    newAutomata.nodeIndex[state.node] = newAutomata.nodeIndex.size

    return state
}

private fun forkState(
    state: State,
    current: TaintRegisterStateAutomata,
    forkedStates: MutableMap<State, State>,
    newAutomata: TaintRegisterStateAutomataBuilder,
): State {
    val forked = forkedStates[state]
    if (forked != null) return forked

    val newNode = AutomataNode()
    newAutomata.nodeIndex[newNode] = newAutomata.nodeIndex.size

    val newState = State(newNode, state.register)
    forkedStates[state] = newState

    if (state in current.finalAcceptStates) {
        newAutomata.acceptStates.add(newState)
    }

    if (state in current.finalDeadStates) {
        newAutomata.deadStates.add(newState)
    }

    val currentStateSucc = current.successors[state]
        ?: return newState

    val newSucc = hashSetOf<Pair<Edge, State>>()
    for ((edge, dst) in currentStateSucc) {
        val forkedDst = forkState(dst, current, forkedStates, newAutomata)
        newSucc.add(edge to forkedDst)
    }

    newAutomata.successors[newState] = newSucc
    return newState
}

private fun ensureSourceStateVars(
    automata: TaintRegisterStateAutomata,
    focusMetaVars: Set<MetavarAtom>
): Pair<TaintRegisterStateAutomata, Set<MetavarAtom>> {
    if (focusMetaVars.isNotEmpty()) return automata to focusMetaVars

    val freshVar = MetavarAtom.create("generated_source")
    val edgeReplacement = mutableListOf<EdgeReplacement>()

    val predecessors = automataPredecessors(automata)

    val unprocessedStates = mutableListOf<State>()
    unprocessedStates += automata.finalAcceptStates

    while (unprocessedStates.isNotEmpty()) {
        val dstState = unprocessedStates.removeLast()
        for ((edge, srcState) in predecessors[dstState].orEmpty()) {
            when (edge) {
                is Edge.MethodCall -> {
                    val positivePredicate = edge.condition.findPositivePredicate() ?: continue
                    val effectVars = edge.effect.assignMetaVar.toMutableMap()

                    // todo: currently we taint only result, but semgrep taint all subexpr by default
                    val condition = ParamConstraint(Position.Result, IsMetavar(freshVar))
                    val predicate = Predicate(positivePredicate.signature, condition)
                    effectVars[freshVar] = listOf(MethodPredicate(predicate, negated = false))
                    val effect = EdgeEffect(effectVars)
                    val modifiedEdge = Edge.MethodCall(edge.condition, effect)

                    edgeReplacement += EdgeReplacement(srcState, dstState, edge, modifiedEdge)
                }

                is Edge.MethodEnter -> {
                    val positivePredicate = edge.condition.findPositivePredicate() ?: continue
                    val effectVars = edge.effect.assignMetaVar.toMutableMap()

                    val condition = ParamConstraint(
                        Position.Argument(Position.ArgumentIndex.Any("tainted")),
                        IsMetavar(freshVar)
                    )
                    val predicate = Predicate(positivePredicate.signature, condition)
                    effectVars[freshVar] = listOf(MethodPredicate(predicate, negated = false))
                    val effect = EdgeEffect(effectVars)
                    val modifiedEdge = Edge.MethodEnter(edge.condition, effect)

                    edgeReplacement += EdgeReplacement(srcState, dstState, edge, modifiedEdge)
                }

                is Edge.MethodExit -> {
                    val positivePredicate = edge.condition.findPositivePredicate() ?: continue
                    val effectVars = edge.effect.assignMetaVar.toMutableMap()

                    val condition = ParamConstraint(
                        Position.Argument(Position.ArgumentIndex.Concrete(idx = 0)),
                        IsMetavar(freshVar)
                    )
                    val predicate = Predicate(positivePredicate.signature, condition)
                    effectVars[freshVar] = listOf(MethodPredicate(predicate, negated = false))
                    val effect = EdgeEffect(effectVars)
                    val modifiedEdge = Edge.MethodExit(edge.condition, effect)

                    edgeReplacement += EdgeReplacement(srcState, dstState, edge, modifiedEdge)
                }

                is Edge.AnalysisEnd -> {
                    unprocessedStates.add(srcState)
                }
            }
        }
    }

    val resultAutomata = automata.replaceEdges(edgeReplacement)
    return resultAutomata to setOf(freshVar)
}

private data class EdgeReplacement(
    val stateFrom: State,
    val stateTo: State,
    val originalEdge: Edge,
    val newEdge: Edge
)

private fun TaintRegisterStateAutomata.replaceEdges(replacements: List<EdgeReplacement>): TaintRegisterStateAutomata {
    if (replacements.isEmpty()) return this

    val mutableSuccessors = successors.toMutableMap()
    for (replacement in replacements) {
        val currentSuccessors = mutableSuccessors[replacement.stateFrom] ?: continue
        val newSuccessors = currentSuccessors.toHashSet()
        newSuccessors.remove(replacement.originalEdge to replacement.stateTo)
        newSuccessors.add(replacement.newEdge to replacement.stateTo)
        mutableSuccessors[replacement.stateFrom] = newSuccessors
    }

    return TaintRegisterStateAutomata(
        formulaManager, initial, finalAcceptStates, finalDeadStates, mutableSuccessors, nodeIndex
    )
}
