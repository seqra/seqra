package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamPatternMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamStringMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.Companion.mkFalse
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantCmpType
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantType
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantValue
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.OperationCancelation
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.SemgrepSinkTaintRequirement
import org.opentaint.semgrep.pattern.SemgrepTaintLabel
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.ParamCondition.StringValueMetaVar
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.semgrep.pattern.conversion.SpecificBoolValue
import org.opentaint.semgrep.pattern.conversion.SpecificStringValue
import org.opentaint.semgrep.pattern.conversion.TypeNamePattern
import org.opentaint.semgrep.pattern.conversion.automata.AutomataBuilderCtx
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodEnclosingClassName
import org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodName
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.automata.operations.brzozowskiAlgorithm
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.StateRegister
import org.opentaint.semgrep.pattern.transform
import kotlin.time.Duration.Companion.seconds

private val automataCreationTimeout = 2.seconds

fun convertToTaintRules(
    rule: SemgrepRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
    ruleId: String,
    meta: SinkMetaData,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace
): TaintRuleFromSemgrep = when (rule) {
    is SemgrepMatchingRule -> RuleConversionCtx(ruleId, meta, semgrepRuleTrace).convertMatchingRuleToTaintRules(rule)
    is SemgrepTaintRule -> RuleConversionCtx(ruleId, meta, semgrepRuleTrace).convertTaintRuleToTaintRules(rule)
}

private fun RuleConversionCtx.safeConvertToTaintRules(
    name: String,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>,
    convertToTaintRules: (RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>) -> List<SerializedItem>,
): List<SerializedItem>? =
    runCatching {
        try {
            convertToTaintRules(rule)
        } catch (_: LoopAssignVarsException) {
            val builderCtx = AutomataBuilderCtx(
                cancelation = OperationCancelation(automataCreationTimeout),
                formulaManager = rule.rule.formulaManager,
                metaVarInfo = rule.metaVarInfo
            )

            val minimized = rule.map {
                with (builderCtx) {
                    brzozowskiAlgorithm(it)
                }
            }
            convertToTaintRules(minimized)
        }
    }.onFailure { ex ->
        semgrepRuleTrace.error(
            "Failed to convert to taint rule for $name: ${ex.message}",
            Reason.ERROR,
        )
    }.getOrNull()

private fun RuleConversionCtx.convertMatchingRuleToTaintRules(
    rule: SemgrepMatchingRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
): TaintRuleFromSemgrep {
    if (rule.rules.isEmpty()) {
        error("No SemgrepRuleAutomatas received")
    }

    val ruleGroups = rule.rules.mapIndexedNotNull { idx, r ->
        val automataId = "$ruleId#$idx"

        val rules = safeConvertToTaintRules(automataId, r) { rule ->
            convertAutomataToTaintRules(rule.metaVarInfo, rule.rule, automataId)
        }

        rules?.let(TaintRuleFromSemgrep::TaintRuleGroup)
    }

    if (ruleGroups.isEmpty()) {
        error("Failed to generate any taintRuleGroup")
    }
    return TaintRuleFromSemgrep(ruleId, ruleGroups)
}

private fun RuleConversionCtx.convertTaintRuleToTaintRules(
    rule: SemgrepTaintRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
): TaintRuleFromSemgrep {
    val taintMarks = mutableSetOf<String>()
    val generatedRules = mutableListOf<SerializedItem>()

    fun taintMark(label: SemgrepTaintLabel?): String {
        val labelSuffix = label?.label?.let { "_$it" } ?: ""
        return "$ruleId#taint$labelSuffix"
    }

    for ((i, source) in rule.sources.withIndex()) {
        val taintMarkName = taintMark(source.label).also { taintMarks.add(it) }

        val requiresVarName = when (val r = source.requires) {
            null -> "dummy_unused_name"
            is SemgrepTaintLabel -> taintMark(r)
        }

        generatedRules += safeConvertToTaintRules("$ruleId: source #$i", source.pattern) { pattern ->
            val sourceCtx = convertTaintSourceRule(i, pattern, generateRequires = source.requires != null)
                ?: return@safeConvertToTaintRules emptyList()

            val ctx = SinkRuleGenerationCtx(
                sourceCtx.requirementVars, sourceCtx.requirementStateId,
                requiresVarName, sourceCtx.ctx
            )
            ctx.generateTaintSourceRules(sourceCtx.stateVars, taintMarkName, semgrepRuleTrace)
        }.orEmpty()
    }

    for ((i, sink) in rule.sinks.withIndex()) {
        val sinkRequiresMarks = when (sink.requires) {
            null -> taintMarks

            is SemgrepSinkTaintRequirement.Simple -> when (val r = sink.requires.requirement) {
                is SemgrepTaintLabel -> listOf(taintMark(r))
            }

            is SemgrepSinkTaintRequirement.MetaVarRequirement -> {
                semgrepRuleTrace.error("Rule $ruleId: sink requires ignored", Reason.NOT_IMPLEMENTED)
                taintMarks
            }
        }

        generatedRules += safeConvertToTaintRules("$ruleId: sink #$i", sink.pattern) { pattern ->
            val (ctx, stateVars, stateId) = convertTaintSinkRule(i, pattern)
                ?: return@safeConvertToTaintRules emptyList()

            sinkRequiresMarks.flatMap { taintMarkName ->
                val sinkCtx = SinkRuleGenerationCtx(stateVars, stateId, taintMarkName, ctx)
                sinkCtx.generateTaintSinkRules(ruleId, meta, semgrepRuleTrace) { _, cond ->
                    if (cond is SerializedCondition.True) {
                        semgrepRuleTrace.error(
                            "Taint rule $ruleId match anything",
                            Reason.WARNING,
                        )
                        return@generateTaintSinkRules false
                    }

                    true
                }
            }
        }.orEmpty()
    }

    for ((i, pass) in rule.propagators.withIndex()) {
        generatedRules += safeConvertToTaintRules("$ruleId: pass #$i", pass.pattern) { pattern ->
            val fromVar = MetavarAtom.create(pass.from)
            val toVar = MetavarAtom.create(pass.to)

            val (ctx, stateId) = generatePassRule(i, pattern, fromVar, toVar)
                ?: return@safeConvertToTaintRules emptyList()

            taintMarks.flatMap { taintMarkName ->
                val sinkCtx = SinkRuleGenerationCtx(setOf(fromVar), stateId, taintMarkName, ctx)
                sinkCtx.generateTaintPassRules(fromVar, toVar, taintMarkName, semgrepRuleTrace)
            }
        }.orEmpty()
    }

    for ((i, sanitizer) in rule.sanitizers.withIndex()) {
        // todo: sanitizer by side effect
        // todo: sanitizer focus metavar
        generatedRules += safeConvertToTaintRules("$ruleId: sanitizer #$i", sanitizer.pattern) {
            val sanitizerCtx = convertTaintSourceRule(i, sanitizer.pattern, generateRequires = false)
                ?: return@safeConvertToTaintRules emptyList()

            taintMarks.flatMap { taintMarkName ->
                sanitizerCtx.ctx.generateTaintSanitizerRules(taintMarkName, semgrepRuleTrace)
            }
        }.orEmpty()
    }

    val ruleGroup = TaintRuleFromSemgrep.TaintRuleGroup(generatedRules)
    return TaintRuleFromSemgrep(ruleId, listOf(ruleGroup))
}

private fun RuleConversionCtx.generatePassRule(
    passIdx: Int,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>,
    fromMetaVar: MetavarAtom,
    toMetaVar: MetavarAtom
): Pair<TaintRuleGenerationCtx, Int>? {
    val automata = rule.rule

    val taintAutomata = createAutomataWithEdgeElimination(
        automata.formulaManager, rule.metaVarInfo, automata.initialNode, automataCreationTimeout
    ) ?: return null

    val initialStateId = taintAutomata.stateId(taintAutomata.initial)
    val initialRegister = StateRegister(mapOf(fromMetaVar to initialStateId))
    val newInitial = taintAutomata.initial.copy(register = initialRegister)
    val taintAutomataWithState = taintAutomata.replaceInitialState(newInitial)

    val taintEdges = generateAutomataWithTaintEdges(
        taintAutomataWithState, rule.metaVarInfo,
        automataId = "$ruleId#pass_$passIdx", acceptStateVars = setOf(toMetaVar)
    )

    return taintEdges to initialStateId
}

// todo: check sink behaviour with multiple focus meta vars
private fun RuleConversionCtx.convertTaintSinkRule(
    sinkIdx: Int,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>
): Triple<TaintRuleGenerationCtx, Set<MetavarAtom>, Int>? {
    val automata = rule.rule

    val taintAutomata = createAutomataWithEdgeElimination(
        automata.formulaManager, rule.metaVarInfo, automata.initialNode, automataCreationTimeout
    ) ?: return null

    val (sinkAutomata, stateMetaVars) = ensureSinkStateVars(
        taintAutomata,
        rule.metaVarInfo.focusMetaVars.map { MetavarAtom.create(it) }.toSet()
    )

    val initialStateId = sinkAutomata.stateId(sinkAutomata.initial)
    val initialRegister = StateRegister(stateMetaVars.associateWith { initialStateId })
    val newInitial = sinkAutomata.initial.copy(register = initialRegister)
    val sinkAutomataWithState = sinkAutomata.replaceInitialState(newInitial)

    val taintEdges = generateAutomataWithTaintEdges(
        sinkAutomataWithState, rule.metaVarInfo,
        automataId = "$ruleId#sink_$sinkIdx", acceptStateVars = emptySet()
    )

    return Triple(taintEdges, stateMetaVars, initialStateId)
}

private data class SourceRuleGenerationCtx(
    val ctx: TaintRuleGenerationCtx,
    val stateVars: Set<MetavarAtom>,
    val requirementVars: Set<MetavarAtom>,
    val requirementStateId: Int
)

private fun RuleConversionCtx.convertTaintSourceRule(
    sourceIdx: Int,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>,
    generateRequires: Boolean
): SourceRuleGenerationCtx? {
    val automata = rule.rule

    val taintAutomata = createAutomataWithEdgeElimination(
        automata.formulaManager, rule.metaVarInfo, automata.initialNode, automataCreationTimeout
    ) ?: return null

    val (rawSourceAutomata, stateMetaVars) = ensureSourceStateVars(
        taintAutomata,
        rule.metaVarInfo.focusMetaVars.map { MetavarAtom.create(it) }.toSet()
    )

    val (sourceAutomata, requirementVars, requirementStateId) = if (generateRequires) {
        val (sourceAutomataWithReq, requirementVars) = ensureSinkStateVars(rawSourceAutomata, emptySet())

        val initialStateId = sourceAutomataWithReq.stateId(sourceAutomataWithReq.initial)
        val initialRegister = StateRegister(requirementVars.associateWith { initialStateId })
        val newInitial = sourceAutomataWithReq.initial.copy(register = initialRegister)
        val sourceAutomataWithState = sourceAutomataWithReq.replaceInitialState(newInitial)
        Triple(sourceAutomataWithState, requirementVars, initialStateId)
    } else {
        Triple(rawSourceAutomata, emptySet(), -1)
    }

    val taintEdges = generateAutomataWithTaintEdges(
        sourceAutomata, rule.metaVarInfo,
        automataId = "$ruleId#source_$sourceIdx", acceptStateVars = stateMetaVars
    )

    val finalAcceptEdges = taintEdges.edgesToFinalAccept
    val assignedStateVars = finalAcceptEdges.flatMapTo(hashSetOf()) { it.stateTo.register.assignedVars.keys }
    assignedStateVars.retainAll(stateMetaVars)

    return SourceRuleGenerationCtx(taintEdges, assignedStateVars, requirementVars, requirementStateId)
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

private fun TaintRegisterStateAutomata.replaceInitialState(newInitial: State): TaintRegisterStateAutomata {
    val newFinalAccept = finalAcceptStates.toHashSet()
    if (newFinalAccept.remove(initial)) {
        newFinalAccept.add(newInitial)
    }

    val newFinalDead = finalDeadStates.toHashSet()
    if (newFinalDead.remove(initial)) {
        newFinalDead.add(newInitial)
    }

    val successors = hashMapOf<State, Set<Pair<Edge, State>>>()
    for ((state, stateSuccessors) in this.successors) {
        val newSuccessors = stateSuccessors.mapTo(hashSetOf()) { current ->
            if (current.second != initial) return@mapTo current

            current.first to newInitial
        }

        val newState = if (state != initial) state else newInitial
        successors[newState] = newSuccessors
    }

    return TaintRegisterStateAutomata(formulaManager, newInitial, newFinalAccept, newFinalDead, successors, nodeIndex)
}

private fun RuleConversionCtx.convertAutomataToTaintRules(
    metaVarInfo: ResolvedMetaVarInfo,
    automata: SemgrepRuleAutomata,
    automataId: String,
): List<SerializedItem> {
    val taintAutomata = createAutomataWithEdgeElimination(
        automata.formulaManager, metaVarInfo, automata.initialNode, automataCreationTimeout
    ) ?: return emptyList()

    val ctx = generateAutomataWithTaintEdges(
        taintAutomata, metaVarInfo, automataId, acceptStateVars = emptySet()
    )

    return ctx.generateTaintSinkRules(ruleId, meta, semgrepRuleTrace) { function, cond ->
        if (function.matchAnything() && cond is SerializedCondition.True) {
            semgrepRuleTrace.error(
                "Rule $ruleId match anything",
                Reason.WARNING,
            )
            return@generateTaintSinkRules false
        }

        true
    }
}

private class SinkRuleGenerationCtx(
    val initialStateVars: Set<MetavarAtom>,
    val initialVarValue: Int,
    val taintMarkName: String,
    uniqueRuleId: String,
    automata: TaintRegisterStateAutomata,
    metaVarInfo: TaintRuleGenerationMetaVarInfo,
    globalStateAssignStates: Set<State>,
    edges: List<TaintRuleEdge>,
    edgesToFinalAccept: List<TaintRuleEdge>,
    edgesToFinalDead: List<TaintRuleEdge>
) : TaintRuleGenerationCtx(
    uniqueRuleId, automata, metaVarInfo,
    globalStateAssignStates, edges,
    edgesToFinalAccept, edgesToFinalDead
) {
    constructor(
        initialStateVars: Set<MetavarAtom>, initialVarValue: Int, taintMarkName: String,
        ctx: TaintRuleGenerationCtx
    ) : this(
        initialStateVars, initialVarValue, taintMarkName,
        ctx.uniqueRuleId, ctx.automata, ctx.metaVarInfo,
        ctx.globalStateAssignStates, ctx.edges,
        ctx.edgesToFinalAccept, ctx.edgesToFinalDead
    )

    override fun allMarkValues(varName: MetavarAtom): List<String> {
        if (varName in initialStateVars) {
            return listOf(taintMarkName)
        }
        return super.allMarkValues(varName)
    }

    override fun stateMarkName(varName: MetavarAtom, varValue: Int): String {
        if (varName in initialStateVars && varValue == initialVarValue) {
            return taintMarkName
        }
        return super.stateMarkName(varName, varValue)
    }
}

private data class RegisterVarPosition(val varName: MetavarAtom, val positions: MutableSet<PositionBase>)

private data class RuleCondition(
    val enclosingClassPackage: SerializedNameMatcher,
    val enclosingClassName: SerializedNameMatcher,
    val name: SerializedNameMatcher,
    val condition: SerializedCondition,
)

private data class EvaluatedEdgeCondition(
    val ruleCondition: RuleCondition,
    val additionalFieldRules: List<SerializedFieldRule>,
    val accessedVarPosition: Map<MetavarAtom, RegisterVarPosition>
)

private fun TaintRuleGenerationCtx.generateTaintSinkRules(
    id: String, meta: SinkMetaData,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
    checkRule: (SerializedFunctionNameMatcher, SerializedCondition) -> Boolean,
): List<SerializedItem> {
    class SinkRuleGen : AcceptStateRuleGenerator {
        override fun generateAcceptStateRules(
            currentRules: List<SerializedItem>,
            ruleEdge: TaintRuleEdge,
            condition: EvaluatedEdgeCondition,
            function: SerializedFunctionNameMatcher,
            cond: SerializedCondition,
        ): List<SerializedItem> {
            if (!checkRule(function, cond)) {
                return emptyList()
            }

            val afterSinkActions = buildStateAssignAction(ruleEdge.stateTo, condition)

            return when (ruleEdge.edgeKind) {
                TaintRuleEdge.Kind.MethodEnter -> listOf(
                    SerializedRule.MethodEntrySink(
                        function, signature = null, overrides = false, cond,
                        trackFactsReachAnalysisEnd = afterSinkActions,
                        id, meta = meta
                    )
                )

                TaintRuleEdge.Kind.MethodCall -> listOf(
                    SerializedRule.Sink(
                        function, signature = null, overrides = true, cond,
                        trackFactsReachAnalysisEnd = afterSinkActions,
                        id, meta = meta
                    )
                )

                TaintRuleEdge.Kind.MethodExit -> {
                    generateEndSink(currentRules, cond, afterSinkActions, id, meta)
                }
            }
        }
    }

    return generateTaintRules(semgrepRuleTrace, SinkRuleGen())
}

private fun TaintRuleGenerationCtx.generateTaintSanitizerRules(
    taintMarkName: String,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<SerializedItem> {
    class SanitizerRuleGen : AcceptStateRuleGenerator {
        override fun generateAcceptStateRules(
            currentRules: List<SerializedItem>,
            ruleEdge: TaintRuleEdge,
            condition: EvaluatedEdgeCondition,
            function: SerializedFunctionNameMatcher,
            cond: SerializedCondition
        ): List<SerializedItem> {
            if (ruleEdge.stateTo.register.assignedVars.isNotEmpty()) {
                semgrepRuleTrace.error("Assigned vars after cleaner state", Reason.NOT_IMPLEMENTED)
            }

            if (ruleEdge.edgeKind != TaintRuleEdge.Kind.MethodCall) {
                semgrepRuleTrace.error("Non method call cleaner", Reason.NOT_IMPLEMENTED)
            }

            val cleanerPos = PositionBase.AnyArgument(classifier = "tainted")
            val action = SerializedTaintCleanAction(taintMarkName, PositionBaseWithModifiers.BaseOnly(cleanerPos))
            val rule = SerializedRule.Cleaner(function, signature = null, overrides = true, cond, listOf(action))

            return listOf(rule)
        }
    }

    return generateTaintRules(semgrepRuleTrace, SanitizerRuleGen())
}

private fun generateEndSink(
    currentRules: List<SerializedItem>,
    cond: SerializedCondition,
    afterSinkActions: List<SerializedTaintAssignAction>,
    id: String,
    meta: SinkMetaData,
): List<SinkRule> {
    val endActions = afterSinkActions.map { it.copy(pos = it.pos.rewriteAsEndPosition()) }
    return generateMethodEndRule(
        currentRules = currentRules,
        cond = cond,
        generateWithoutMatchedEp = { endCondition ->
            listOf(
                SerializedRule.MethodExitSink(
                    anyFunction(), signature = null, overrides = false, endCondition,
                    trackFactsReachAnalysisEnd = endActions,
                    id, meta = meta
                )
            )
        },
        generateWithEp = { ep, endCondition ->
            listOf(
                SerializedRule.MethodExitSink(
                    ep.function, ep.signature, ep.overrides, endCondition,
                    trackFactsReachAnalysisEnd = endActions,
                    id, meta = meta
                )
            )
        }
    )
}

private inline fun <R: SerializedItem> generateMethodEndRule(
    currentRules: List<SerializedItem>,
    cond: SerializedCondition,
    generateWithoutMatchedEp: (SerializedCondition) -> List<R>,
    generateWithEp: (SerializedRule.EntryPoint, SerializedCondition) -> List<R>,
): List<R> {
    val endCondition = cond.rewriteAsEndCondition()
    val entryPointRules = currentRules.filterIsInstance<SerializedRule.EntryPoint>()

    if (entryPointRules.isEmpty()) {
        return generateWithoutMatchedEp(endCondition)
    }

    return entryPointRules.flatMap { rule ->
        val generatedCond = SerializedCondition.and(listOf(rule.condition ?: SerializedCondition.True, endCondition))
        generateWithEp(rule, generatedCond)
    }
}

private fun SerializedCondition.rewriteAsEndCondition(): SerializedCondition = when (this) {
    is SerializedCondition.And -> SerializedCondition.and(allOf.map { it.rewriteAsEndCondition() })
    is SerializedCondition.Or -> SerializedCondition.Or(anyOf.map { it.rewriteAsEndCondition() })
    is SerializedCondition.Not -> SerializedCondition.not(not.rewriteAsEndCondition())
    SerializedCondition.True -> this
    is SerializedCondition.ClassAnnotated -> this
    is SerializedCondition.MethodAnnotated -> this
    is SerializedCondition.MethodNameMatches -> this
    is SerializedCondition.ClassNameMatches -> this
    is SerializedCondition.AnnotationType -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantCmp -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantEq -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantGt -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantLt -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ConstantMatches -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ContainsMark -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.IsConstant -> copy(isConstant = isConstant.rewriteAsEndPosition())
    is SerializedCondition.IsType -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.ParamAnnotated -> copy(pos = pos.rewriteAsEndPosition())
    is SerializedCondition.NumberOfArgs -> SerializedCondition.True
}

private fun PositionBaseWithModifiers.rewriteAsEndPosition() = when (this) {
    is PositionBaseWithModifiers.BaseOnly -> PositionBaseWithModifiers.BaseOnly(
        base.rewriteAsEndPosition()
    )

    is PositionBaseWithModifiers.WithModifiers -> PositionBaseWithModifiers.WithModifiers(
        base.rewriteAsEndPosition(), modifiers
    )
}

private fun PositionBase.rewriteAsEndPosition(): PositionBase = when (this) {
    is PositionBase.AnyArgument -> PositionBase.Result
    is PositionBase.Argument -> PositionBase.Result
    is PositionBase.ClassStatic -> this
    PositionBase.Result -> this
    PositionBase.This -> this
}

private fun generateMethodEndSource(
    currentRules: List<SerializedItem>,
    cond: SerializedCondition,
    actions: List<SerializedTaintAssignAction>,
): List<SerializedRule.MethodExitSource> {
    val endActions = actions.map { it.copy(pos = it.pos.rewriteAsEndPosition()) }
    return generateMethodEndRule(
        currentRules = currentRules,
        cond = cond,
        generateWithoutMatchedEp = { endCond ->
            listOf(
                SerializedRule.MethodExitSource(
                    anyFunction(), signature = null, overrides = false, endCond, endActions
                )
            )
        },
        generateWithEp = { ep, endCond ->
            listOf(
                SerializedRule.MethodExitSource(
                    ep.function, ep.signature, ep.overrides, endCond, endActions
                )
            )
        }
    )
}

private fun TaintRuleGenerationCtx.generateTaintSourceRules(
    stateVars: Set<MetavarAtom>, taintMarkName: String,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<SerializedItem> {
    class TaintSourceAcceptStateGen : AcceptStateRuleGenerator {
        override fun generateAcceptStateRules(
            currentRules: List<SerializedItem>,
            ruleEdge: TaintRuleEdge,
            condition: EvaluatedEdgeCondition,
            function: SerializedFunctionNameMatcher,
            cond: SerializedCondition
        ): List<SerializedItem> {
            val nonStateVars = ruleEdge.stateTo.register.assignedVars.keys - stateVars
            if (nonStateVars.isNotEmpty()) {
                semgrepRuleTrace.error("Final state has non-state vars assigned", Reason.ERROR)
            }

            val actions = stateVars.flatMapTo(mutableListOf()) { varName ->
                val varPosition = condition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
                varPosition.positions.map {
                    SerializedTaintAssignAction(taintMarkName, pos = PositionBaseWithModifiers.BaseOnly(it))
                }
            }

            if (actions.isEmpty()) return emptyList()

            return when (ruleEdge.edgeKind) {
                TaintRuleEdge.Kind.MethodCall -> listOf(
                    SerializedRule.Source(
                        function, signature = null, overrides = true, cond, actions
                    )
                )

                TaintRuleEdge.Kind.MethodEnter -> listOf(
                    SerializedRule.EntryPoint(
                        function, signature = null, overrides = false, cond, actions
                    )
                )

                TaintRuleEdge.Kind.MethodExit -> {
                    generateMethodEndSource(currentRules, cond, actions)
                }
            }
        }
    }
    return generateTaintRules(semgrepRuleTrace, TaintSourceAcceptStateGen())
}

private fun SinkRuleGenerationCtx.generateTaintPassRules(
    fromVar: MetavarAtom, toVar: MetavarAtom,
    taintMarkName: String,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): List<SerializedItem> {
    // todo: generate taint pass when possible
    return generateTaintSourceRules(setOf(toVar), taintMarkName, semgrepRuleTrace)
}

private interface AcceptStateRuleGenerator {
    fun generateAcceptStateRules(
        currentRules: List<SerializedItem>,
        ruleEdge: TaintRuleEdge,
        condition: EvaluatedEdgeCondition,
        function: SerializedFunctionNameMatcher,
        cond: SerializedCondition,
    ): List<SerializedItem>
}

private fun TaintRuleGenerationCtx.generateTaintRules(
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
    acceptStateRuleGen: AcceptStateRuleGenerator,
): List<SerializedItem> {
    val rules = mutableListOf<SerializedItem>()

    val evaluatedConditions = hashMapOf<TaintRuleEdge, EvaluatedEdgeCondition>()

    fun evaluate(edge: TaintRuleEdge): EvaluatedEdgeCondition =
        evaluatedConditions.getOrPut(edge) {
            evaluateMethodConditionAndEffect(edge.edgeCondition, edge.edgeEffect, semgrepRuleTrace)
        }

    for (ruleEdge in edges) {
        val state = ruleEdge.stateFrom

        val condition = evaluate(ruleEdge).addStateCheck(this, ruleEdge.checkGlobalState, state)
        rules += condition.additionalFieldRules

        val actions = buildStateAssignAction(ruleEdge.stateTo, condition)

        if (actions.isNotEmpty()) {
            rules += generateRules(condition.ruleCondition) { function, cond ->
                when (ruleEdge.edgeKind) {
                    TaintRuleEdge.Kind.MethodCall -> listOf(
                        SerializedRule.Source(
                            function, signature = null, overrides = true, cond, actions
                        )
                    )

                    TaintRuleEdge.Kind.MethodEnter -> listOf(
                        SerializedRule.EntryPoint(
                            function, signature = null, overrides = false, cond, actions
                        )
                    )

                    TaintRuleEdge.Kind.MethodExit -> {
                        generateMethodEndSource(rules, cond, actions)
                    }
                }
            }
        }
    }

    for (ruleEdge in edgesToFinalAccept) {
        val state = ruleEdge.stateFrom

        val condition = evaluate(ruleEdge).addStateCheck(this, ruleEdge.checkGlobalState, state)
        rules += condition.additionalFieldRules

        rules += generateRules(condition.ruleCondition) { function, cond ->
            acceptStateRuleGen.generateAcceptStateRules(rules, ruleEdge, condition, function, cond)
        }
    }

    for (ruleEdge in edgesToFinalDead) {
        val state = ruleEdge.stateFrom

        val condition = evaluate(ruleEdge).addStateCheck(this, ruleEdge.checkGlobalState, state)
        rules += condition.additionalFieldRules

        val actions = condition.accessedVarPosition.values.flatMapTo(mutableListOf()) { varPosition ->
            val value = state.register.assignedVars[varPosition.varName] ?: return@flatMapTo emptyList()
            val stateMark = stateMarkName(varPosition.varName, value)

            varPosition.positions.flatMap {
                val pos = PositionBaseWithModifiers.BaseOnly(it)
                listOf(SerializedTaintCleanAction(stateMark, pos = pos))
            }
        }

        if (state in globalStateAssignStates) {
            actions += SerializedTaintCleanAction(globalStateMarkName(state), stateVarPosition)
        }

        if (actions.isNotEmpty()) {
            when (ruleEdge.edgeKind) {
                TaintRuleEdge.Kind.MethodEnter, TaintRuleEdge.Kind.MethodExit -> {
                    semgrepRuleTrace.error("Non method call cleaner", Reason.NOT_IMPLEMENTED)
                    continue
                }

                TaintRuleEdge.Kind.MethodCall -> {
                    rules += generateRules(condition.ruleCondition) { function, cond ->
                        listOf(
                            SerializedRule.Cleaner(function, signature = null, overrides = true, cond, actions)
                        )
                    }
                }
            }
        }
    }

    return rules
}

private fun TaintRuleGenerationCtx.buildStateAssignAction(
    state: State,
    edgeCondition: EvaluatedEdgeCondition
): List<SerializedTaintAssignAction> {
    val requiredVariables = state.register.assignedVars.keys
    val stateId = automata.stateId(state)

    val result = requiredVariables.flatMapTo(mutableListOf()) { varName ->
        val varPosition = edgeCondition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
        val stateMark = stateMarkName(varPosition.varName, stateId)

        varPosition.positions.map {
            val pos = PositionBaseWithModifiers.BaseOnly(it)
            SerializedTaintAssignAction(stateMark, pos = pos)
        }
    }

    if (state in globalStateAssignStates) {
        result += SerializedTaintAssignAction(globalStateMarkName(state), pos = stateVarPosition)
    }

    return result
}

private fun EvaluatedEdgeCondition.addStateCheck(
    ctx: TaintRuleGenerationCtx,
    checkGlobalState: Boolean,
    state: State
): EvaluatedEdgeCondition {
    val stateChecks = mutableListOf<SerializedCondition.ContainsMark>()
    if (checkGlobalState) {
        stateChecks += SerializedCondition.ContainsMark(ctx.globalStateMarkName(state), ctx.stateVarPosition)
    } else {
        for ((metaVar, value) in state.register.assignedVars) {
            val markName = ctx.stateMarkName(metaVar, value)

            for (pos in accessedVarPosition[metaVar]?.positions.orEmpty()) {
                val position = PositionBaseWithModifiers.BaseOnly(pos)
                stateChecks += SerializedCondition.ContainsMark(markName, position)
            }
        }
    }

    if (stateChecks.isEmpty()) return this

    val stateCondition = serializedConditionOr(stateChecks)
    val rc = ruleCondition.condition
    return copy(ruleCondition = ruleCondition.copy(condition = SerializedCondition.and(listOf(stateCondition, rc))))
}

private inline fun <T> generateRules(
    condition: RuleCondition,
    body: (SerializedFunctionNameMatcher, SerializedCondition) -> T
): T {
    val functionMatcher = SerializedFunctionNameMatcher.Complex(
        condition.enclosingClassPackage,
        condition.enclosingClassName,
        condition.name
    )

    return body(functionMatcher, condition.condition)
}

private class RuleConditionBuilder {
    var enclosingClassPackage: SerializedNameMatcher? = null
    var enclosingClassName: SerializedNameMatcher? = null
    var methodName: SerializedNameMatcher? = null

    val conditions = hashSetOf<SerializedCondition>()

    fun build(): RuleCondition = RuleCondition(
        enclosingClassPackage ?: anyName(),
        enclosingClassName ?: anyName(),
        methodName ?: anyName(),
        SerializedCondition.and(conditions.toList())
    )
}

private fun TaintRuleGenerationCtx.evaluateMethodConditionAndEffect(
    condition: EdgeCondition,
    effect: EdgeEffect,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): EvaluatedEdgeCondition {
    val ruleBuilder = RuleConditionBuilder()
    val additionalFieldRules = mutableListOf<SerializedFieldRule>()

    val evaluatedSignature = evaluateConditionAndEffectSignatures(effect, condition, ruleBuilder, semgrepRuleTrace)

    condition.readMetaVar.values.flatten().forEach {
        val signature = it.predicate.signature.notEvaluatedSignature(evaluatedSignature)
        evaluateEdgePredicateConstraint(
            signature, it.predicate.constraint, it.negated, ruleBuilder, additionalFieldRules, semgrepRuleTrace
        )
    }

    condition.other.forEach {
        val signature = it.predicate.signature.notEvaluatedSignature(evaluatedSignature)
        evaluateEdgePredicateConstraint(
            signature, it.predicate.constraint, it.negated, ruleBuilder, additionalFieldRules, semgrepRuleTrace
        )
    }

    val varPositions = hashMapOf<MetavarAtom, RegisterVarPosition>()
    effect.assignMetaVar.values.flatten().forEach {
        findMetaVarPosition(it.predicate.constraint, varPositions)
    }

    return EvaluatedEdgeCondition(ruleBuilder.build(), additionalFieldRules, varPositions)
}

private fun MethodSignature.notEvaluatedSignature(evaluated: MethodSignature): MethodSignature? {
    if (this == evaluated) return null
    return MethodSignature(
        methodName = if (methodName == evaluated.methodName) {
            MethodName(SignatureName.AnyName)
        } else {
            methodName
        },
        enclosingClassName = if (enclosingClassName == evaluated.enclosingClassName) {
            MethodEnclosingClassName.anyClassName
        } else {
            enclosingClassName
        }
    )
}

private fun TaintRuleGenerationCtx.evaluateConditionAndEffectSignatures(
    effect: EdgeEffect,
    condition: EdgeCondition,
    ruleBuilder: RuleConditionBuilder,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): MethodSignature {
    val signatures = mutableListOf<MethodSignature>()

    effect.assignMetaVar.values.flatten().forEach {
        check(!it.negated) { "Negated effect" }
        signatures.add(it.predicate.signature)
    }

    condition.readMetaVar.values.flatten().forEach {
        if (!it.negated) {
            signatures.add(it.predicate.signature)
        }
    }

    condition.other.forEach {
        if (!it.negated) {
            signatures.add(it.predicate.signature)
        }
    }

    return evaluateFormulaSignature(signatures, ruleBuilder, semgrepRuleTrace)
}

private fun TaintRuleGenerationCtx.evaluateFormulaSignature(
    signatures: List<MethodSignature>,
    builder: RuleConditionBuilder,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): MethodSignature {
    val signature = signatures.first()

    if (signatures.any { it != signature }) {
        TODO("Signature mismatch")
    }

    if (signature.isGeneratedAnyValueGenerator()) {
        TODO("Eliminate generated method")
    }

    val methodName = signature.methodName.name
    builder.methodName = evaluateFormulaSignatureMethodName(methodName, builder.conditions, semgrepRuleTrace)

    val classSignatureMatcherFormula = typeMatcher(signature.enclosingClassName.name)
    if (classSignatureMatcherFormula == null) return signature

    if (classSignatureMatcherFormula !is MetaVarConstraintFormula.Constraint) {
        TODO("Complex class signature matcher")
    }

    val classSignatureMatcher = classSignatureMatcherFormula.constraint
    when (classSignatureMatcher) {
        is SerializedNameMatcher.ClassPattern -> {
            builder.enclosingClassPackage = classSignatureMatcher.`package`
            builder.enclosingClassName = classSignatureMatcher.`class`
        }

        is SerializedNameMatcher.Simple -> {
            val parts = classSignatureMatcher.value.split(".")
            val packageName = parts.dropLast(1).joinToString(separator = ".")
            builder.enclosingClassPackage = SerializedNameMatcher.Simple(packageName)
            builder.enclosingClassName = SerializedNameMatcher.Simple(parts.last())
        }

        is SerializedNameMatcher.Pattern -> {
            TODO("Signature class name pattern")
        }
    }
    return signature
}

private fun TaintRuleGenerationCtx.evaluateFormulaSignatureMethodName(
    methodName: SignatureName,
    conditions: MutableSet<SerializedCondition>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): SerializedNameMatcher.Simple? {
    return when (methodName) {
        SignatureName.AnyName -> null
        is SignatureName.Concrete -> SerializedNameMatcher.Simple(methodName.name)
        is SignatureName.MetaVar -> {
            val constraint = when (val constraints = metaVarInfo.constraints[methodName.metaVar]) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> {
                    semgrepRuleTrace.error(
                        "Placeholder: method name",
                        Reason.NOT_IMPLEMENTED
                    )
                    constraints.constraint
                }
            }

            val concrete = mutableListOf<String>()
            conditions += constraint?.constraint.toSerializedCondition { c, negated ->
                when (c) {
                    is MetaVarConstraint.Concrete -> {
                        if (!negated) {
                            concrete.add(c.value)
                            SerializedCondition.True
                        } else {
                            methodNameMatcherCondition(c.value)
                        }
                    }

                    is MetaVarConstraint.RegExp -> SerializedCondition.MethodNameMatches(c.regex)
                }
            }

            check(concrete.size <= 1) { "Multiple concrete names" }
            concrete.firstOrNull()?.let { SerializedNameMatcher.Simple(it) }
        }
    }
}

private fun methodNameMatcherCondition(methodNameConstraint: String): SerializedCondition {
    val methodName = methodNameConstraint.substringAfterLast('.')
    val className = methodNameConstraint.substringBeforeLast('.', "")

    val methodNameMatcher = SerializedCondition.MethodNameMatches(methodName)
    val classNameMatcher: SerializedCondition.ClassNameMatches? =
        className.takeIf { it.isNotEmpty() }?.let {
            SerializedCondition.ClassNameMatches(classNameMatcherFromConcreteString(it))
        }

    return SerializedCondition.and(listOfNotNull(methodNameMatcher, classNameMatcher))
}

private fun classNameMatcherFromConcreteString(name: String): SerializedNameMatcher {
    val parts = name.split(".")
    val packageName = parts.dropLast(1).joinToString(separator = ".")
    return SerializedNameMatcher.ClassPattern(
        SerializedNameMatcher.Simple(packageName),
        SerializedNameMatcher.Simple(parts.last())
    )
}

private fun TaintRuleGenerationCtx.evaluateEdgePredicateConstraint(
    signature: MethodSignature?,
    constraint: MethodConstraint?,
    negated: Boolean,
    builder: RuleConditionBuilder,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    if (!negated) {
        evaluateMethodConstraints(
            signature,
            constraint,
            builder.conditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
    } else {
        val negatedConditions = hashSetOf<SerializedCondition>()
        evaluateMethodConstraints(
            signature,
            constraint,
            negatedConditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
        builder.conditions += SerializedCondition.not(SerializedCondition.and(negatedConditions.toList()))
    }
}

private fun TaintRuleGenerationCtx.evaluateMethodConstraints(
    signature: MethodSignature?,
    constraint: MethodConstraint?,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    if (signature != null) {
        evaluateMethodSignatureCondition(signature, conditions, semgrepRuleTrace)
    }

    when (constraint) {
        null -> {}

        is ClassModifierConstraint -> {
            val annotation = signatureModifierConstraint(constraint.modifier)
            conditions += SerializedCondition.ClassAnnotated(annotation)
        }

        is MethodModifierConstraint -> {
            val annotation = signatureModifierConstraint(constraint.modifier)
            conditions += SerializedCondition.MethodAnnotated(annotation)
        }

        is NumberOfArgsConstraint -> conditions += SerializedCondition.NumberOfArgs(constraint.num)
        is ParamConstraint -> evaluateParamConstraints(
            constraint,
            conditions,
            additionalFieldRules,
            semgrepRuleTrace
        )
    }
}

private fun TaintRuleGenerationCtx.evaluateMethodSignatureCondition(
    signature: MethodSignature,
    conditions: MutableSet<SerializedCondition>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    val classType = typeMatcher(signature.enclosingClassName.name)
    conditions += classType.toSerializedCondition { typeMatcher, _ ->
        SerializedCondition.ClassNameMatches(typeMatcher)
    }

    val methodName = evaluateFormulaSignatureMethodName(signature.methodName.name, conditions, semgrepRuleTrace)
    if (methodName != null) {
        val methodNameRegex = "^${methodName.value}\$"
        conditions += SerializedCondition.MethodNameMatches(methodNameRegex)
    }
}

private fun findMetaVarPosition(
    constraint: MethodConstraint?,
    varPositions: MutableMap<MetavarAtom, RegisterVarPosition>
) {
    if (constraint !is ParamConstraint) return
    findMetaVarPosition(constraint, varPositions)
}

private fun TaintRuleGenerationCtx.typeMatcher(
    typeName: TypeNamePattern
): MetaVarConstraintFormula<SerializedNameMatcher>? {
    return when (typeName) {
        is TypeNamePattern.ClassName -> MetaVarConstraintFormula.Constraint(
            SerializedNameMatcher.ClassPattern(
                `package` = anyName(),
                `class` = SerializedNameMatcher.Simple(typeName.name)
            )
        )

        is TypeNamePattern.FullyQualified -> {
            MetaVarConstraintFormula.Constraint(
                SerializedNameMatcher.Simple(typeName.name)
            )
        }

        is TypeNamePattern.PrimitiveName -> {
            MetaVarConstraintFormula.Constraint(
                SerializedNameMatcher.Simple(typeName.name)
            )
        }

        TypeNamePattern.AnyType -> return null

        is TypeNamePattern.MetaVar -> {
            val constraints = metaVarInfo.constraints[typeName.metaVar] ?: return null

            val constraint = when (constraints) {
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> TODO("Placeholder: type name")
            }

            constraint.transform { value ->
                // todo hack: here we assume that if name contains '.' then name is fqn
                when (value) {
                    is MetaVarConstraint.Concrete -> {
                        if (value.value.contains('.')) {
                            SerializedNameMatcher.Simple(value.value)
                        } else {
                            SerializedNameMatcher.ClassPattern(
                                `package` = anyName(),
                                `class` = SerializedNameMatcher.Simple(value.value)
                            )
                        }
                    }

                    is MetaVarConstraint.RegExp -> {
                        val pkgPattern = value.regex.substringBeforeLast("\\.", missingDelimiterValue = "")
                        if (pkgPattern.isNotEmpty()) {
                            val clsPattern = value.regex.substringAfterLast("\\.")
                            if (clsPattern.patternCanMatchDot()){
                                SerializedNameMatcher.Pattern(value.regex)
                            } else {
                                SerializedNameMatcher.ClassPattern(
                                    `package` = SerializedNameMatcher.Pattern(pkgPattern),
                                    `class` = SerializedNameMatcher.Pattern(clsPattern)
                                )
                            }
                        } else {
                            SerializedNameMatcher.ClassPattern(
                                `package` = anyName(),
                                `class` = SerializedNameMatcher.Pattern(value.regex)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String.patternCanMatchDot(): Boolean =
    '.' in this || '-' in this // [A-Z]

private fun TaintRuleGenerationCtx.signatureModifierConstraint(
    modifier: SignatureModifier
): SerializedCondition.AnnotationConstraint {
    val typeMatcherFormula = typeMatcher(modifier.type)

    val type = when (typeMatcherFormula) {
        null -> anyName()
        is MetaVarConstraintFormula.Constraint -> typeMatcherFormula.constraint
        else -> TODO("Complex annotation type")
    }

    val params = when (val v = modifier.value) {
        SignatureModifierValue.AnyValue -> null
        SignatureModifierValue.NoValue -> emptyList()
        is SignatureModifierValue.StringValue -> listOf(
            AnnotationParamStringMatcher(v.paramName, v.value)
        )

        is SignatureModifierValue.StringPattern -> listOf(
            AnnotationParamPatternMatcher(v.paramName, v.pattern)
        )

        is SignatureModifierValue.MetaVar -> {
            val paramMatchers = mutableListOf<SerializedCondition.AnnotationParamMatcher>()

            val constraints = metaVarInfo.constraints[v.metaVar]
            val constraint = when (constraints) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> TODO("Placeholder: annotation")
            }

            constraint.toSerializedCondition { c, negated ->
                if (negated) {
                    TODO("Negated annotation param condition")
                }

                paramMatchers += when (c) {
                    is MetaVarConstraint.Concrete -> AnnotationParamStringMatcher(v.paramName, c.value)
                    is MetaVarConstraint.RegExp -> AnnotationParamPatternMatcher(v.paramName, c.regex)
                }

                SerializedCondition.True
            }
            paramMatchers
        }
    }

    return SerializedCondition.AnnotationConstraint(type, params)
}

private fun Position.toSerializedPosition(): PositionBase = when (this) {
    is Position.Argument -> when (index) {
        is Position.ArgumentIndex.Any -> PositionBase.AnyArgument(index.paramClassifier)
        is Position.ArgumentIndex.Concrete -> PositionBase.Argument(index.idx)
    }

    is Position.Object -> PositionBase.This
    is Position.Result -> PositionBase.Result
}

private fun TaintRuleGenerationCtx.evaluateParamConstraints(
    param: ParamConstraint,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
) {
    val position = param.position.toSerializedPosition()
    conditions += evaluateParamCondition(position, param.condition, additionalFieldRules, semgrepRuleTrace)
}

private fun findMetaVarPosition(
    param: ParamConstraint,
    varPositions: MutableMap<MetavarAtom, RegisterVarPosition>
) {
    val position = param.position.toSerializedPosition()
    findMetaVarPosition(position, param.condition, varPositions)
}

private fun findMetaVarPosition(
    position: PositionBase,
    condition: ParamCondition.Atom,
    varPositions: MutableMap<MetavarAtom, RegisterVarPosition>
) {
    if (condition !is IsMetavar) return
    val varPosition = varPositions.getOrPut(condition.metavar) {
        RegisterVarPosition(condition.metavar, hashSetOf())
    }
    varPosition.positions.add(position)
}

private fun TaintRuleGenerationCtx.evaluateParamCondition(
    position: PositionBase,
    condition: ParamCondition.Atom,
    additionalFieldRules: MutableList<SerializedFieldRule>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace,
): SerializedCondition {
    when (condition) {
        is IsMetavar -> {
            val constraints = metaVarInfo.constraints[condition.metavar.toString()]
            if (constraints != null) {
                // todo: semantic metavar constraint
                semgrepRuleTrace.error(
                    "Rule $uniqueRuleId: metavar ${condition.metavar} constraint ignored",
                    Reason.WARNING,
                )
            }

            val pos = PositionBaseWithModifiers.BaseOnly(position)
            val conditions = allMarkValues(condition.metavar).map {
                SerializedCondition.ContainsMark(it, pos)
            }
            return serializedConditionOr(conditions)
        }

        is ParamCondition.TypeIs -> {
            return typeMatcher(condition.typeName).toSerializedCondition { typeNameMatcher, _ ->
                SerializedCondition.IsType(typeNameMatcher, position)
            }
        }

        is ParamCondition.SpecificStaticFieldValue -> {
            val enclosingClassMatcherFormula = typeMatcher(condition.fieldClass)

            val enclosingClassMatcher = when (enclosingClassMatcherFormula) {
                null -> anyName()
                is MetaVarConstraintFormula.Constraint -> enclosingClassMatcherFormula.constraint
                else -> TODO("Complex static field type")
            }

            val mark = stateMarkName(
                MetavarAtom.create("__STATIC_FIELD_VALUE__${condition.fieldName}"),
                varValue = 0
            )

            val action = SerializedTaintAssignAction(
                mark, pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
            )
            additionalFieldRules += SerializedFieldRule.SerializedStaticFieldSource(
                enclosingClassMatcher, condition.fieldName, condition = null, listOf(action)
            )

            return SerializedCondition.ContainsMark(
                mark, PositionBaseWithModifiers.BaseOnly(position)
            )
        }

        ParamCondition.AnyStringLiteral -> {
            return SerializedCondition.IsConstant(position)
        }

        is SpecificBoolValue -> {
            val value = ConstantValue(ConstantType.Bool, condition.value.toString())
            return SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
        }

        is SpecificStringValue -> {
            val value = ConstantValue(ConstantType.Str, condition.value)
            return SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
        }

        is StringValueMetaVar -> {
            val constraints = metaVarInfo.constraints[condition.metaVar.toString()]
            val constraint = when (constraints) {
                null -> null
                is MetaVarConstraintOrPlaceHolder.Constraint -> constraints.constraint.constraint
                is MetaVarConstraintOrPlaceHolder.PlaceHolder -> TODO("Placeholder: string value")
            }
            return constraint.toSerializedCondition { c, _ ->
                when (c) {
                    is MetaVarConstraint.Concrete -> {
                        val value = ConstantValue(ConstantType.Str, c.value)
                        SerializedCondition.ConstantCmp(position, value, ConstantCmpType.Eq)
                    }

                    is MetaVarConstraint.RegExp -> {
                        SerializedCondition.ConstantMatches(c.regex, position)
                    }
                }
            }
        }

        is ParamCondition.ParamModifier -> {
            val annotation = signatureModifierConstraint(condition.modifier)
            return SerializedCondition.ParamAnnotated(position, annotation)
        }
    }
}

private fun anyName() = SerializedNameMatcher.Pattern(".*")

private fun anyFunction() = SerializedFunctionNameMatcher.Complex(anyName(), anyName(), anyName())

private fun SerializedFunctionNameMatcher.matchAnything(): Boolean =
    `class` == anyName() && `package` == anyName() && name == anyName()

private fun serializedConditionOr(args: List<SerializedCondition>): SerializedCondition {
    val result = mutableListOf<SerializedCondition>()
    for (arg in args) {
        if (arg is SerializedCondition.Or) {
            result.addAll(arg.anyOf)
            continue
        }

        if (arg is SerializedCondition.True) return SerializedCondition.True

        if (arg.isFalse()) continue

        result.add(arg)
    }

    return when (result.size) {
        0 -> mkFalse()
        1 -> result.single()
        else -> SerializedCondition.Or(result)
    }
}

private fun <T> MetaVarConstraintFormula<T>?.toSerializedCondition(
    transform: (T, Boolean) -> SerializedCondition,
): SerializedCondition {
    if (this == null) return SerializedCondition.True
    return toSerializedConditionUtil(negated = false, transform)
}

private fun <T> MetaVarConstraintFormula<T>.toSerializedConditionUtil(
    negated: Boolean,
    transform: (T, Boolean) -> SerializedCondition,
): SerializedCondition = when (this) {
    is MetaVarConstraintFormula.Constraint -> {
        transform(constraint, negated)
    }

    is MetaVarConstraintFormula.Not -> {
        SerializedCondition.not(this.negated.toSerializedConditionUtil(!negated, transform))
    }

    is MetaVarConstraintFormula.And -> {
        SerializedCondition.and(args.map { it.toSerializedConditionUtil(negated, transform) })
    }
}
