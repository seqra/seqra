package org.opentaint.semgrep.pattern.conversion.taint

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
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
import org.opentaint.dataflow.util.PersistentBitSet
import org.opentaint.dataflow.util.PersistentBitSet.Companion.emptyPersistentBitSet
import org.opentaint.dataflow.util.contains
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.OperationCancelation
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula
import org.opentaint.semgrep.pattern.MetaVarConstraints
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
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodEnclosingClassName
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.Cube
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodName
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.automata.operations.brzozowskiAlgorithm
import org.opentaint.semgrep.pattern.conversion.generatedAnyValueGeneratorMethodName
import org.opentaint.semgrep.pattern.conversion.generatedStringConcatMethodName
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.StateRegister
import org.opentaint.semgrep.pattern.transform
import java.util.BitSet
import java.util.IdentityHashMap
import kotlin.time.Duration.Companion.seconds

fun convertToTaintRules(
    rule: SemgrepRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
    ruleId: String,
    meta: SinkMetaData,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace
): TaintRuleFromSemgrep = when (rule) {
    is SemgrepMatchingRule -> RuleConversionCtx(ruleId, meta, semgrepRuleTrace).convertMatchingRuleToTaintRules(rule)
    is SemgrepTaintRule -> RuleConversionCtx(ruleId, meta, semgrepRuleTrace).convertTaintRuleToTaintRules(rule)
}

private class RuleConversionCtx(
    val ruleId: String,
    val meta: SinkMetaData,
    val semgrepRuleTrace: SemgrepRuleLoadStepTrace
)

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
        automata.formulaManager, rule.metaVarInfo, automata.initialNode
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
        automata.formulaManager, rule.metaVarInfo, automata.initialNode
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
        automata.formulaManager, rule.metaVarInfo, automata.initialNode
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

private class TaintRegisterStateAutomataBuilder {
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    val acceptStates = hashSetOf<State>()
    val deadStates = hashSetOf<State>()
    val nodeIndex = hashMapOf<AutomataNode, Int>()

    fun newState(): State {
        val node = AutomataNode()
        nodeIndex[node] = nodeIndex.size
        return State(node, StateRegister(emptyMap()))
    }

    fun build(manager: MethodFormulaManager, initial: State) =
        TaintRegisterStateAutomata(manager, initial, acceptStates, deadStates, successors, nodeIndex)
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
        automata.formulaManager, metaVarInfo, automata.initialNode
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

private fun RuleConversionCtx.createAutomataWithEdgeElimination(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialNode: AutomataNode
): TaintRegisterStateAutomata? {
    val automata = createAutomata(formulaManager, metaVarInfo, initialNode)

    val anyValueGeneratorEdgeEliminator = edgeTypePreservingEdgeEliminator(::eliminateAnyValueGenerator)
    val automataWithoutGeneratedEdges = eliminateEdges(
        automata,
        anyValueGeneratorEdgeEliminator,
        ValueGeneratorCtx.EMPTY
    )

    val stringConcatEdgeEliminator = edgeTypePreservingEdgeEliminator(::eliminateStringConcat)
    val result = eliminateEdges(
        automataWithoutGeneratedEdges,
        stringConcatEdgeEliminator,
        StringConcatCtx.EMPTY
    )

    if (result.successors[result.initial].isNullOrEmpty()) {
        semgrepRuleTrace.error("Empty automata after generated edge elimination", Reason.WARNING)
        return null
    }

    return result
}

private fun RuleConversionCtx.generateAutomataWithTaintEdges(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
    automataId: String,
    acceptStateVars: Set<MetavarAtom>
): TaintRuleGenerationCtx {
    val simulated = simulateAutomata(automata)
    val meaningFullAutomata = removeMeaningLessEdges(simulated)
    val cleaned = removeUnreachabeStates(meaningFullAutomata)
    val rewritten = rewriteEdges(cleaned)
    val liveAutomata = eliminateDeadVariables(rewritten, acceptStateVars)
    val cleanAutomata = cleanupAutomata(liveAutomata, metaVarInfo)
    val generatedEdges =  generateTaintEdges(cleanAutomata, metaVarInfo, automataId)
    val resultAutomata = cleanupAutomata(generatedEdges)
    return resultAutomata
}

data class TaintRegisterStateAutomata(
    val formulaManager: MethodFormulaManager,
    val initial: State,
    val finalAcceptStates: Set<State>,
    val finalDeadStates: Set<State>,
    val successors: Map<State, Set<Pair<Edge, State>>>,
    val nodeIndex: Map<AutomataNode, Int>
) {
    data class StateRegister(
        val assignedVars: Map<MetavarAtom, Int>,
    )

    data class State(
        val node: AutomataNode,
        val register: StateRegister
    )

    data class MethodPredicate(
        val predicate: Predicate,
        val negated: Boolean,
    )

    data class EdgeCondition(
        val readMetaVar: Map<MetavarAtom, List<MethodPredicate>>,
        val other: List<MethodPredicate>
    )

    data class EdgeEffect(
        val assignMetaVar: Map<MetavarAtom, List<MethodPredicate>>
    )

    sealed interface Edge {
        sealed interface EdgeWithCondition : Edge {
            val condition: EdgeCondition
        }

        sealed interface EdgeWithEffect : Edge {
            val effect: EdgeEffect
        }

        data class MethodCall(
            override val condition: EdgeCondition,
            override val effect: EdgeEffect
        ) : EdgeWithCondition, EdgeWithEffect

        data class MethodEnter(
            override val condition: EdgeCondition,
            override val effect: EdgeEffect
        ) : EdgeWithCondition, EdgeWithEffect

        data class MethodExit(
            override val condition: EdgeCondition,
            override val effect: EdgeEffect
        ): EdgeWithCondition, EdgeWithEffect

        data object AnalysisEnd : Edge
    }

    fun stateId(state: State): Int = nodeIndex[state.node] ?: error("Missing node")
}

private val automataCreationTimeout = 2.seconds

private fun createAutomata(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialNode: AutomataNode
): TaintRegisterStateAutomata {
    val cancelation = OperationCancelation(automataCreationTimeout)

    val result = TaintRegisterStateAutomataBuilder()

    fun nodeId(node: AutomataNode): Int = result.nodeIndex.getOrPut(node) { result.nodeIndex.size }

    val emptyRegister = StateRegister(emptyMap())
    val startState = State(initialNode, emptyRegister)
    val initialState = startState

    val processedStates = hashSetOf<State>()
    val unprocessed = mutableListOf(startState)

    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!processedStates.add(state)) continue

        // force eval
        nodeId(state.node)

        if (state.node.accept) {
            result.acceptStates.add(state)
            // note: no need transitions from final state
            continue
        }

        for ((edgeCondition, dstNode) in state.node.outEdges) {
            for (simplifiedEdge in simplifyEdgeCondition(formulaManager, metaVarInfo, cancelation, edgeCondition)) {
                val nextState = State(dstNode, emptyRegister)
                result.successors.getOrPut(state, ::hashSetOf).add(simplifiedEdge to nextState)
                unprocessed.add(nextState)
            }
        }
    }

    check(result.acceptStates.isNotEmpty()) { "Automata has no accept state" }

    result.collapseEpsilonTransitions(initialState)

    return result.build(formulaManager, initialState)
}

private fun TaintRegisterStateAutomataBuilder.collapseEpsilonTransitions(initial: State) {
    val unprocessed = mutableListOf(initial)
    val visited = hashSetOf<State>()

    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!visited.add(state)) continue

        val epsilonClosure = computeEpsilonClosure(state)

        val stateSuccessors = successors.getOrPut(state, ::hashSetOf)
        stateSuccessors.removeAll { it.first.isEpsilonTransition() }

        for (s in epsilonClosure) {
            if (s == state) continue

            for ((edge, next) in successors[s].orEmpty()) {
                if (edge.isEpsilonTransition()) continue

                val dst = if (next in epsilonClosure) state else next
                stateSuccessors.add(edge to dst)
            }
        }

        if (epsilonClosure.any { it in acceptStates }) {
            acceptStates.add(state)
        }

        if (epsilonClosure.any { it in deadStates }) {
            deadStates.add(state)
        }

        successors[state]?.forEach { unprocessed.add(it.second) }
    }
}

private fun TaintRegisterStateAutomataBuilder.computeEpsilonClosure(startState: State): Set<State> {
    val unprocessed = mutableListOf(startState)
    val visitedStates = hashSetOf<State>()

    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!visitedStates.add(state)) continue

        successors[state]?.forEach { (edge, next) ->
            if (edge.isEpsilonTransition()){
                unprocessed.add(next)
            }
        }
    }

    return visitedStates
}

private fun Edge.isEpsilonTransition(): Boolean = when (this) {
    is Edge.AnalysisEnd -> false
    is Edge.MethodCall -> condition.isTrue() && effect.hasNoEffect()
    is Edge.MethodEnter -> condition.isTrue() && effect.hasNoEffect()
    is Edge.MethodExit -> condition.isTrue()
}

private fun EdgeCondition.isTrue(): Boolean = readMetaVar.isEmpty() && other.isEmpty()

private fun EdgeEffect.hasNoEffect(): Boolean = assignMetaVar.isEmpty()

data class SimulationState(
    val original: State,
    val state: State,
    val originalPath: PersistentMap<State, State>
)

private fun canClean(edge: Edge, from: State): Boolean {
    val condition = when (edge) {
        is Edge.MethodCall -> edge.condition
        is Edge.MethodEnter -> edge.condition
        is Edge.MethodExit -> edge.condition
        is Edge.AnalysisEnd -> return true
    }
    val assigned = mutableSetOf<MetavarAtom>()
    from.register.assignedVars.keys.forEach { assigned.addAll(it.basics) }
    return condition.readMetaVar.keys.all { it.basics.all { basic -> basic in assigned } }
}

private class LoopAssignVarsException : RuntimeException("Loop assign vars")

private fun RuleConversionCtx.simulateAutomata(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val initialSimulationState = SimulationState(
        automata.initial, automata.initial,
        persistentHashMapOf(automata.initial to automata.initial)
    )
    val unprocessed = mutableListOf(initialSimulationState)

    val finalAcceptStates = hashSetOf<State>()
    val finalDeadStates = hashSetOf<State>()
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    while (unprocessed.isNotEmpty()) {
        val simulationState = unprocessed.removeLast()
        val state = simulationState.state

        if (simulationState.original in automata.finalAcceptStates) {
            finalAcceptStates.add(state)
            continue
        }

        if (simulationState.original in automata.finalDeadStates) {
            finalDeadStates.add(state)
            continue
        }

        for ((simplifiedEdge, dstState) in automata.successors[simulationState.original].orEmpty()) {
            val loopStartState = simulationState.originalPath[dstState]
            if (loopStartState != null) {
                if (loopStartState.register == state.register) {
                    // loop has no assignments
                    continue
                }

//                throw LoopAssignVarsException()
                semgrepRuleTrace.error("Loop var assign", Reason.ERROR)
                continue
            }

            val dstStateId = automata.stateId(dstState)
            val updatedEdge = rewriteEdgeWrtComplexMetavars(simplifiedEdge, state.register)
            val dstStateRegister = simulateCondition(updatedEdge, dstStateId, state.register)

            val nextState = dstState.copy(register = dstStateRegister)
            successors.getOrPut(state, ::hashSetOf).add(simplifiedEdge to nextState)

            val nextPath = simulationState.originalPath.put(dstState, nextState)
            val nextSimulationState = SimulationState(dstState, nextState, nextPath)
            unprocessed.add(nextSimulationState)
        }
    }

    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial,
        finalAcceptStates, finalDeadStates,
        successors, automata.nodeIndex
    )
}

private fun rewriteEdges(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val unprocessed = mutableListOf(automata.initial)
    val visited = mutableSetOf<State>()
    val newSuccessors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    while (unprocessed.isNotEmpty()) {
        val srcState = unprocessed.removeLast()
        if (!visited.add(srcState)) continue

        for ((edge, dstState) in automata.successors[srcState].orEmpty()) {
            if (dstState in automata.finalDeadStates && !canClean(edge, srcState)) {
                // discarding the edge so it won't clean unassigned metavars
                continue
            }
            val updatedEdge = rewriteEdgeWrtComplexMetavars(edge, srcState.register)
            newSuccessors.getOrPut(srcState, ::hashSetOf).add(updatedEdge to dstState)
            unprocessed.add(dstState)
        }
    }

    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial,
        automata.finalAcceptStates, automata.finalDeadStates.filter { it in visited }.toHashSet(),
        newSuccessors, automata.nodeIndex
    )
}

private fun rewriteEdgeWrtComplexMetavars(edge: Edge, register: StateRegister): Edge {
    return when (edge) {
        is Edge.AnalysisEnd -> edge
        is Edge.MethodCall -> rewriteEdgeWrtComplexMetavars(edge.effect, edge.condition, register) { effect, condition ->
            Edge.MethodCall(condition, effect)
        }
        is Edge.MethodEnter -> rewriteEdgeWrtComplexMetavars(edge.effect, edge.condition, register) { effect, condition ->
            Edge.MethodEnter(condition, effect)
        }

        is Edge.MethodExit -> rewriteEdgeWrtComplexMetavars(edge.effect, edge.condition, register) { effect, condition ->
            Edge.MethodExit(condition, effect)
        }
    }
}

private inline fun rewriteEdgeWrtComplexMetavars(
    effect: EdgeEffect,
    condition: EdgeCondition,
    register: StateRegister,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge
): Edge {
    val effectWriteVars = mutableMapOf<MetavarAtom, MutableSet<MethodPredicate>>()
    val newReadMetavar = mutableMapOf<MetavarAtom, MutableSet<MethodPredicate>>()
    val newOther = condition.other.toMutableList()

    for ((metavar, preds) in condition.readMetaVar) {
        val uncheckedBasics = metavar.basics.toMutableSet()
        val inputMetavars = hashSetOf<MetavarAtom>()
        while (uncheckedBasics.isNotEmpty()) {
            val metaVarBasicIntersections = register.assignedVars.keys
                .map { it to it.basics.intersect(uncheckedBasics) }

            val assignedMetaVar = metaVarBasicIntersections.maxByOrNull { it.second.size }
            if (assignedMetaVar == null || assignedMetaVar.second.isEmpty()) break

            inputMetavars.add(assignedMetaVar.first)
            uncheckedBasics.removeAll(assignedMetaVar.second)
        }

        if (inputMetavars.isEmpty()) {
            preds.mapTo(newOther) { pred ->
                pred.replaceMetavar {
                    check(it == metavar) { "Unexpected metavar" }
                    null
                }
            }
        } else {
            inputMetavars.forEach { inputMetavar ->
                val newPreds = newReadMetavar.getOrPut(inputMetavar, ::mutableSetOf)
                preds.mapTo(newPreds) { pred ->
                    pred.replaceMetavar {
                        check(it == metavar) { "Unexpected metavar" }
                        inputMetavar
                    }
                }
            }
        }

        val writePreds = effect.assignMetaVar[metavar] ?: continue
        inputMetavars.forEach { inputMetavar ->
            val newPreds = effectWriteVars.getOrPut(inputMetavar, ::mutableSetOf)
            writePreds.mapTo(newPreds) { pred ->
                pred.replaceMetavar {
                    check(it == metavar) { "Unexpected metavar" }
                    inputMetavar
                }
            }
        }
    }


    effect.assignMetaVar.forEach { (metaVar, preds) ->
        effectWriteVars.getOrPut(metaVar, ::mutableSetOf).addAll(preds)
        if (metaVar.basics.size > 1) {
            metaVar.basics.forEach { basicMv ->
                effectWriteVars.getOrPut(basicMv, ::mutableSetOf) += preds.map { pred ->
                    pred.replaceMetavar {
                        check(it == metaVar) { "Unexpected metavar" }
                        basicMv
                    }
                }
            }
        }
    }

    val newCondition = EdgeCondition(newReadMetavar.mapValues { it.value.toList() }, newOther)
    val newEffect = EdgeEffect(effectWriteVars.mapValues { it.value.toList() })
    return rebuildEdge(newEffect, newCondition)
}

private fun MethodPredicate.replaceMetavar(replace: (MetavarAtom) -> MetavarAtom?): MethodPredicate {
    val constraint = predicate.constraint ?: return this
    val newConstraint = constraint.replaceMetavar(replace)

    return MethodPredicate(
        predicate = Predicate(
            signature = predicate.signature,
            constraint = newConstraint
        ),
        negated = negated
    )
}

private fun MethodConstraint.replaceMetavar(replace: (MetavarAtom) -> MetavarAtom?): MethodConstraint? {
    if (this !is ParamConstraint) {
        return this
    }

    val newCondition = when (condition) {
        is IsMetavar -> IsMetavar(replace(condition.metavar) ?: return null)
        is StringValueMetaVar -> StringValueMetaVar(replace(condition.metaVar) ?: return null)
        else -> return this
    }

    return ParamConstraint(position, newCondition)
}

private fun automataPredecessors(automata: TaintRegisterStateAutomata): Map<State, Set<Pair<Edge, State>>> {
    val predecessors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    for ((state, edges) in automata.successors) {
        for ((edge, edgeDst) in edges) {
            predecessors.getOrPut(edgeDst, ::hashSetOf).add(edge to state)
        }
    }
    return predecessors
}

private fun RuleConversionCtx.removeMeaningLessEdges(
    automata: TaintRegisterStateAutomata
): TaintRegisterStateAutomata {
    val successors = automata.successors.mapValues { (srcState, edges) ->
        val resultEdges = hashSetOf<Pair<Edge, State>>()
        for ((edge, dstState) in edges) {
            val positiveEdge = edge.ensurePositiveCondition(this)
            if (positiveEdge == null) {
                check(srcState.register == dstState.register) { "State register modified with non-positive edge" }
                continue
            }

            resultEdges.add(positiveEdge to dstState)
        }
        resultEdges
    }

    return automata.copy(successors = successors)
}

private fun removeUnreachabeStates(
    automata: TaintRegisterStateAutomata
): TaintRegisterStateAutomata {
    val predecessors = automataPredecessors(automata)

    val reachableStates = hashSetOf<State>()
    val unprocessed = automata.finalAcceptStates.toMutableList()

    while (unprocessed.isNotEmpty()) {
        val stateId = unprocessed.removeLast()
        if (!reachableStates.add(stateId)) continue

        val predStates = predecessors[stateId] ?: continue
        for ((_, predState) in predStates) {
            unprocessed.add(predState)
        }
    }

    check(automata.initial in reachableStates) {
        "Initial state is unreachable"
    }

    var cleanerStateReachable = false
    val cleanerState = State(AutomataNode(), StateRegister(emptyMap()))
    val reachableSuccessors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    unprocessed.add(automata.initial)
    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (reachableSuccessors.containsKey(state)) continue

        if (state !in reachableStates) continue

        val newSuccessors = hashSetOf<Pair<Edge, State>>()
        for ((edge, successor) in automata.successors[state].orEmpty()) {
            if (successor in reachableStates) {
                newSuccessors.add(edge to successor)
                unprocessed.add(successor)
                continue
            }

            cleanerStateReachable = true
            newSuccessors.add(edge to cleanerState)
        }
        reachableSuccessors[state] = newSuccessors
    }

    if (!cleanerStateReachable) {
        return TaintRegisterStateAutomata(
            automata.formulaManager, automata.initial,
            automata.finalAcceptStates, automata.finalDeadStates,
            reachableSuccessors, automata.nodeIndex
        )
    }

    val nodeIndex = automata.nodeIndex.toMutableMap()
    nodeIndex[cleanerState.node] = nodeIndex.size

    val finalDeadNodes = automata.finalDeadStates + cleanerState
    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial,
        automata.finalAcceptStates, finalDeadNodes,
        reachableSuccessors, nodeIndex
    )
}

private fun eliminateDeadVariables(
    automata: TaintRegisterStateAutomata,
    acceptStateLiveVars: Set<MetavarAtom>
): TaintRegisterStateAutomata {
    // TODO: to we need to specially handle complex variables here?
    val predecessors = automataPredecessors(automata)

    val variableIdx = hashMapOf<MetavarAtom, Int>()
    val stateLiveVars = IdentityHashMap<State, PersistentBitSet>()

    val unprocessed = mutableListOf<Pair<State, PersistentBitSet>>()

    for (state in automata.finalDeadStates) {
        unprocessed.add(state to emptyPersistentBitSet())
    }

    for (state in automata.finalAcceptStates) {
        val liveVarIndices = acceptStateLiveVars.toBitSet {
            variableIdx.getOrPut(it) { variableIdx.size }
        }
        val liveVarSet = emptyPersistentBitSet().persistentAddAll(liveVarIndices)
        unprocessed.add(state to liveVarSet)
    }

    while (unprocessed.isNotEmpty()) {
        val (state, newLiveVars) = unprocessed.removeLast()

        val currentLiveVars = stateLiveVars[state]
        if (currentLiveVars == newLiveVars) continue

        val liveVars = currentLiveVars?.persistentAddAll(newLiveVars) ?: newLiveVars
        stateLiveVars[state] = liveVars

        for ((edge, predState) in predecessors[state].orEmpty()) {
            val readVariables = when (edge) {
                is Edge.AnalysisEnd -> emptySet()
                is Edge.EdgeWithCondition -> edge.condition.readMetaVar.keys
            }

            val readVariableSet = readVariables.toBitSet {
                variableIdx.getOrPut(it) { variableIdx.size }
            }
            val dstLiveVars = liveVars.persistentAddAll(readVariableSet)
            unprocessed.add(predState to dstLiveVars)
        }
    }

    val stateMapping = hashMapOf<State, State>()
    for (state in automata.allStates()) {
        val liveVars = stateLiveVars[state] ?: continue
        val liveRegisterValues = state.register.assignedVars.filterKeys {
            val idx = variableIdx[it] ?: return@filterKeys false
            idx in liveVars
        }
        if (liveRegisterValues == state.register.assignedVars) continue

        val register = StateRegister(liveRegisterValues)
        stateMapping[state] = state.copy(register = register)
    }

    if (stateMapping.isEmpty()) return automata

    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    for ((state, stateSuccessors) in automata.successors) {
        val mappedSuccessors = stateSuccessors.mapTo(hashSetOf()) { (edge, s) ->
            edge to (stateMapping[s] ?: s)
        }
        val mappedState = stateMapping[state] ?: state
        successors[mappedState] = mappedSuccessors
    }

    return TaintRegisterStateAutomata(
        automata.formulaManager,
        initial = stateMapping[automata.initial] ?: automata.initial,
        finalAcceptStates = automata.finalAcceptStates.mapTo(hashSetOf()) { stateMapping[it] ?: it },
        finalDeadStates = automata.finalDeadStates.mapTo(hashSetOf()) { stateMapping[it] ?: it },
        successors = successors,
        nodeIndex = automata.nodeIndex
    )
}

private fun cleanupAutomata(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
): TaintRegisterStateAutomata {
    val withoutRedundantEnd = removeEndEdge(automata)
    val withoutDummyEntry = tryRemoveDummyMethodEntry(withoutRedundantEnd, metaVarInfo)
    val withoutDummyCleaners = removeDummyCleaner(withoutDummyEntry)
    return withoutDummyCleaners
}

private fun removeEndEdge(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val predecessors = automataPredecessors(automata)

    data class StateReplacement(
        val edgeToRemove: Edge,
        val stateToRemove: State,
        val newState: State,
    )

    fun traverse(initial: Set<State>, replacement: MutableList<StateReplacement>) {
        val visited = hashSetOf<State>()
        val unprocessed = initial.toMutableList()
        while (unprocessed.isNotEmpty()) {
            val state = unprocessed.removeLast()
            if (!visited.add(state)) continue

            val preFinalEdges = predecessors[state] ?: continue
            for ((edge, preState) in preFinalEdges) {
                if (edge !is Edge.AnalysisEnd) continue

                unprocessed.add(preState)
                replacement += StateReplacement(edge, state, preState)
            }
        }
    }

    val finalAcceptReplacement = mutableListOf<StateReplacement>()
    traverse(automata.finalAcceptStates, finalAcceptReplacement)

    val finalDeadReplacement = mutableListOf<StateReplacement>()
    traverse(automata.finalDeadStates, finalDeadReplacement)

    if (finalAcceptReplacement.isEmpty() && finalDeadReplacement.isEmpty()) return automata

    val successors = automata.successors.mapValuesTo(hashMapOf()) { (_, edges) -> edges.toHashSet() }
    val finalAccept = automata.finalAcceptStates.toHashSet()
    val finalDead = automata.finalDeadStates.toHashSet()

    for (replacement in finalAcceptReplacement) {
        successors[replacement.newState]?.remove(replacement.edgeToRemove to replacement.stateToRemove)
        finalAccept.add(replacement.newState)
    }

    for (replacement in finalDeadReplacement) {
        successors[replacement.newState]?.remove(replacement.edgeToRemove to replacement.stateToRemove)
        finalDead.add(replacement.newState)
    }

    val replacements = finalAcceptReplacement + finalDeadReplacement

    fun stateHasPredecessor(state: State): Boolean = successors.values.any { edges ->
        edges.any { it.second == state }
    }

    for (replacement in replacements) {
        if (!stateHasPredecessor(replacement.stateToRemove)) {
            successors.remove(replacement.stateToRemove)
            finalAccept.remove(replacement.stateToRemove)
            finalDead.remove(replacement.stateToRemove)
        }
    }

    return TaintRegisterStateAutomata(
        automata.formulaManager,
        automata.initial,
        finalAccept, finalDead, successors,
        automata.nodeIndex
    )
}

private fun removeDummyCleaner(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val initialSuccessors = automata.successors[automata.initial] ?: return automata
    val successorsWithoutDummyCleaners = initialSuccessors.filterNotTo(hashSetOf()) { (_, dst) ->
        dst in automata.finalDeadStates
    }

    if (successorsWithoutDummyCleaners.size == initialSuccessors.size) return automata

    val newSuccessors = automata.successors.toMutableMap()
    newSuccessors[automata.initial] = successorsWithoutDummyCleaners
    return automata.copy(successors = newSuccessors)
}

private fun tryRemoveDummyMethodEntry(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
): TaintRegisterStateAutomata {
    val initialSuccessors = automata.successors[automata.initial].orEmpty()
    val dummyMethodEnters = mutableListOf<Pair<Edge.MethodEnter, State>>()
    for ((edge, state) in initialSuccessors) {
        if (edge !is Edge.MethodEnter) continue
        if (edge.effect.assignMetaVar.isNotEmpty()) continue
        if (!edge.condition.isDummyCondition(metaVarInfo)) continue

        dummyMethodEnters.add(edge to state)
    }

    if (dummyMethodEnters.isEmpty()) return automata

    val mutableSuccessors = automata.successors.mapValuesTo(hashMapOf()) { (_, edges) ->
        edges.toMutableSet()
    }

    val initialSucc = mutableSuccessors[automata.initial]!!
    for ((edge, state) in dummyMethodEnters) {
        val nextEdges = mutableSuccessors[state]
        initialSucc.remove(edge to state)
        nextEdges?.forEach { (e, s) ->
            initialSucc.add(e to s)
        }
    }

    val finalAccept = automata.finalAcceptStates.toHashSet()
    val finalDead = automata.finalDeadStates.toHashSet()

    val statesToRemove = dummyMethodEnters.mapTo(mutableListOf()) { it.second }
    do {
        var stateRemoved = false
        val stateIter = statesToRemove.listIterator()
        while (stateIter.hasNext()) {
            val state = stateIter.next()
            if (state == automata.initial) continue
            val stateReachable = mutableSuccessors.any { (s, edges) ->
                s != state && edges.any { it.second == state }
            }
            if (stateReachable) continue

            stateRemoved = true
            stateIter.remove()

            mutableSuccessors.remove(state)
            finalAccept.remove(state)
            finalDead.remove(state)
        }
    } while (stateRemoved && statesToRemove.isNotEmpty())

    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial,
        finalAccept, finalDead,
        mutableSuccessors, automata.nodeIndex
    )
}

private fun EdgeCondition.isDummyCondition(metaVarInfo: ResolvedMetaVarInfo): Boolean {
    for (cond in other) {
        if (cond.predicate.constraint != null) return false
        val sig = cond.predicate.signature

        when (val mn = sig.methodName.name) {
            is SignatureName.Concrete -> return false
            SignatureName.AnyName -> {}
            is SignatureName.MetaVar -> {
                if (metaVarInfo.metaVarConstraints[mn.metaVar] != null) {
                    return false
                }
            }
        }

        when (val cn = sig.enclosingClassName.name) {
            TypeNamePattern.AnyType -> {}
            is TypeNamePattern.MetaVar -> {
                if (metaVarInfo.metaVarConstraints[cn.metaVar] != null) {
                    return false
                }
            }
            is TypeNamePattern.ClassName,
            is TypeNamePattern.FullyQualified,
            is TypeNamePattern.PrimitiveName -> return false
        }
    }

    return true
}

private fun cleanupAutomata(automata: TaintRuleGenerationCtx): TaintRuleGenerationCtx {
    return dropUnassignedMarkChecks(automata)
}

private fun dropUnassignedMarkChecks(automata: TaintRuleGenerationCtx): TaintRuleGenerationCtx {
    val edges = automata.edges.map { it.copy(edgeCondition = it.edgeCondition.dropUnassignedMarkChecks(it.stateFrom)) }
    val edgesToFinalAccept = automata.edgesToFinalAccept.map { it.copy(edgeCondition = it.edgeCondition.dropUnassignedMarkChecks(it.stateFrom)) }
    val edgesToFinalDead = automata.edgesToFinalDead.map { it.copy(edgeCondition = it.edgeCondition.dropUnassignedMarkChecks(it.stateFrom)) }
    return TaintRuleGenerationCtx(
        automata.uniqueRuleId, automata.automata, automata.metaVarInfo, automata.globalStateAssignStates,
        edges, edgesToFinalAccept, edgesToFinalDead
    )
}

private fun EdgeCondition.dropUnassignedMarkChecks(state: State): EdgeCondition {
    val readMetaVar = readMetaVar.filterKeys { it in state.register.assignedVars }
    return EdgeCondition(readMetaVar, other)
}

private data class ValueGeneratorCtx(
    val valueConstraint: Map<MetavarAtom, List<ParamCondition.Atom>>
) {
    companion object {
        val EMPTY: ValueGeneratorCtx = ValueGeneratorCtx(emptyMap())
    }
}

private fun <CtxT> eliminateEdges(automata: TaintRegisterStateAutomata, edgeEliminator: EdgeEliminator<CtxT>, initialCtx: CtxT): TaintRegisterStateAutomata {
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    val finalAcceptStates = automata.finalAcceptStates.toHashSet()
    val finalDeadStates = automata.finalDeadStates.toHashSet()
    val removedStates = hashSetOf<State>()

    val unprocessed = mutableListOf(automata.initial to initialCtx)
    val visited = hashSetOf<Pair<State, CtxT>>()
    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!visited.add(state)) continue

        val stateSuccessors = successors.getOrPut(state.first, ::hashSetOf)
        eliminateEdgesForOneState(
            state.first, state.second, automata.successors,
            finalAcceptStates, finalDeadStates,
            removedStates,
            stateSuccessors, unprocessed, edgeEliminator
        )
    }

    finalAcceptStates.removeAll(removedStates)
    finalDeadStates.removeAll(removedStates)
    removedStates.forEach { successors.remove(it) }
    finalAcceptStates.forEach { successors.remove(it) }
    finalDeadStates.forEach { successors.remove(it) }

    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial,
        finalAcceptStates, finalDeadStates,
        successors, automata.nodeIndex
    )
}

private fun <CtxT> eliminateEdgesForOneState(
    state: State,
    ctx: CtxT,
    successors: Map<State, Set<Pair<Edge, State>>>,
    finalAcceptStates: MutableSet<State>,
    finalDeadStates: MutableSet<State>,
    removedStates: MutableSet<State>,
    resultStateSuccessors: MutableSet<Pair<Edge, State>>,
    unprocessed: MutableList<Pair<State, CtxT>>,
    edgeEliminator: EdgeEliminator<CtxT>
) {
    for ((edge, nextState) in successors[state].orEmpty()) {
        val elimResult = edgeEliminator.eliminateEdge(edge, ctx)
        when (elimResult) {
            EdgeEliminationResult.Unchanged -> {
                resultStateSuccessors.add(edge to nextState)
                unprocessed.add(nextState to ctx)
                continue
            }

            is EdgeEliminationResult.Replace -> {
                resultStateSuccessors.add(elimResult.newEdge to nextState)
                unprocessed.add(nextState to elimResult.ctx)
                continue
            }

            is EdgeEliminationResult.Eliminate -> {
                if (nextState in finalAcceptStates) {
                    val nextSuccessors = successors[nextState].orEmpty()
                    check(nextSuccessors.isEmpty())

                    removedStates.add(nextState)
                    finalAcceptStates.add(state)
                }

                if (nextState in finalDeadStates) {
                    val nextSuccessors = successors[nextState].orEmpty()
                    check(nextSuccessors.isEmpty())

                    removedStates.add(nextState)
                    finalDeadStates.add(state)
                }

                if (nextState == state) continue

                eliminateEdgesForOneState(
                    nextState, elimResult.ctx, successors, finalAcceptStates, finalDeadStates, removedStates,
                    resultStateSuccessors, unprocessed, edgeEliminator
                )
            }
        }
    }
}

private fun interface EdgeEliminator<CtxT> {
    fun eliminateEdge(edge: Edge, ctx: CtxT): EdgeEliminationResult<CtxT>
}

private sealed interface EdgeEliminationResult<out CtxT> {
    data object Unchanged : EdgeEliminationResult<Nothing>
    data class Replace<CtxT>(val newEdge: Edge, val ctx: CtxT) : EdgeEliminationResult<CtxT>
    data class Eliminate<CtxT>(val ctx: CtxT) : EdgeEliminationResult<CtxT>
}

private fun <CtxT> edgeTypePreservingEdgeEliminator(
    eliminateEdge: (EdgeEffect, EdgeCondition, CtxT, (EdgeEffect, EdgeCondition) -> Edge) -> EdgeEliminationResult<CtxT>
): EdgeEliminator<CtxT> = EdgeEliminator { edge, ctx ->
    when (edge) {
        is Edge.AnalysisEnd -> EdgeEliminationResult.Unchanged
        is Edge.MethodCall -> eliminateEdge(edge.effect, edge.condition, ctx) { effect, cond ->
            Edge.MethodCall(cond, effect)
        }

        is Edge.MethodEnter -> eliminateEdge(edge.effect, edge.condition, ctx) { effect, cond ->
            Edge.MethodEnter(cond, effect)
        }

        is Edge.MethodExit -> eliminateEdge(edge.effect, edge.condition, ctx) { effect, cond ->
            Edge.MethodExit(cond, effect)
        }
    }
}

private fun eliminateAnyValueGenerator(
    effect: EdgeEffect,
    condition: EdgeCondition,
    ctx: ValueGeneratorCtx,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge,
): EdgeEliminationResult<ValueGeneratorCtx> {
    if (effect.anyValueGeneratorUsed()) {
        val metaVar = effect.assignMetaVar.keys.singleOrNull()
            ?: error("Value gen with multiple mata vars")

        val metaVarPred = effect.assignMetaVar.getValue(metaVar).first()
        check((metaVarPred.predicate.constraint as ParamConstraint).position is Position.Result) {
            "Unexpected constraint: $metaVarPred"
        }

        check(condition.readMetaVar.keys.all { it == metaVar }) {
            "Unexpected condition: $condition"
        }

        val metaVarConstraints = mutableListOf<ParamCondition.Atom>()
        for (constraint in condition.other) {
            when (val c = constraint.predicate.constraint) {
                is NumberOfArgsConstraint -> continue

                is ParamConstraint -> {
                    if (c.position !is Position.Result) {
                        error("Unexpected constraint: $c")
                    }

                    if (c.condition is IsMetavar) {
                        error("Unexpected condition: $c")
                    }

                    metaVarConstraints.add(c.condition)
                }

                null -> TODO("any value generator without constraints")
                is ClassModifierConstraint,
                is MethodModifierConstraint -> error("Unexpected any value generator constraint")
            }
        }

        val nextCtx = ValueGeneratorCtx(ctx.valueConstraint + (metaVar to metaVarConstraints))
        return EdgeEliminationResult.Eliminate(nextCtx)
    }

    val valueGenEffect = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
    for ((metaVar, preds) in effect.assignMetaVar) {
        for (pred in preds) {
            if (pred.anyValueGeneratorUsed()) {
                valueGenEffect.getOrPut(metaVar, ::mutableListOf).add(pred)
            }
        }
    }

    var resultCondition = condition
    val resultConstraint = ctx.valueConstraint.toMutableMap()
    val constraintIter = resultConstraint.iterator()
    while (constraintIter.hasNext()) {
        val (metaVar, constraint) = constraintIter.next()

        val metaVarEffect = effect.assignMetaVar[metaVar] ?: continue

        val readMetaVar = resultCondition.readMetaVar - metaVar
        val other = resultCondition.other.toMutableList()

        for (mp in metaVarEffect) {
            val paramConstraint = mp.predicate.constraint as? ParamConstraint ?: continue
            check(paramConstraint.condition is IsMetavar && paramConstraint.condition.metavar == metaVar)
            for (atom in constraint) {
                val newParamConstraint = paramConstraint.copy(condition = atom)
                val newPredicate = mp.predicate.copy(constraint = newParamConstraint)
                other += MethodPredicate(newPredicate, negated = false)
            }
        }

        resultCondition = EdgeCondition(readMetaVar, other)
        constraintIter.remove()
    }

    if (resultCondition === condition) return EdgeEliminationResult.Unchanged

    val newEdge = rebuildEdge(effect, resultCondition)
    return EdgeEliminationResult.Replace(newEdge, ValueGeneratorCtx(resultConstraint))
}

private fun EdgeEffect.anyValueGeneratorUsed(): Boolean =
    assignMetaVar.values.any { preds -> preds.any { it.anyValueGeneratorUsed() } }

private fun MethodPredicate.anyValueGeneratorUsed(): Boolean =
    predicate.signature.isGeneratedAnyValueGenerator()

private fun MethodSignature.isGeneratedAnyValueGenerator(): Boolean {
    val name = methodName.name
    if (name !is SignatureName.Concrete) return false
    return name.name == generatedAnyValueGeneratorMethodName
}

private data class StringConcatCtx(
    val metavarMapping: Map<MetavarAtom, Set<MetavarAtom>>
) {
    fun transform(condition: EdgeCondition): EdgeCondition {
        val transformedOther = mutableSetOf<MethodPredicate>()
        val transformedReadMetaVar = transform(condition.readMetaVar, ignoreNegatedPreds = false, transformedOther)
        condition.other.forEach { transformedOther.addAll(transform(it)) }
        return EdgeCondition(transformedReadMetaVar, transformedOther.toList())
    }

    fun transform(effect: EdgeEffect): EdgeEffect {
        val transformedAssign = transform(effect.assignMetaVar, ignoreNegatedPreds = true, newOther = hashSetOf())
        return EdgeEffect(transformedAssign)
    }

    private fun transform(
        preds: Map<MetavarAtom, List<MethodPredicate>>,
        ignoreNegatedPreds: Boolean,
        newOther: MutableSet<MethodPredicate>,
    ): Map<MetavarAtom, List<MethodPredicate>> {
        val result = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
        preds.forEach { (prevMetavar, prevPreds) ->
            val newMetavars = metavarMapping.getOrElse(prevMetavar) { setOf(prevMetavar) }

            newMetavars.forEach { newMetavar ->
                // Need to concretize context for `prevMetavar`
                val newCtx = StringConcatCtx(metavarMapping + (prevMetavar to setOf(newMetavar)))
                val newPreds = prevPreds.flatMap(newCtx::transform)

                val newMetaVarPreds = result.getOrPut(newMetavar, ::mutableListOf)
                for (p in newPreds) {
                    if (p.negated && ignoreNegatedPreds) continue

                    val metaVar = p.findMetaVarConstraint()
                    if (metaVar != null) {
                        check(metaVar == newMetavar) { "Error: unexpected meta var: $metaVar" }
                        newMetaVarPreds.add(p)
                    } else {
                        newOther.add(p)
                    }
                }
            }
        }
        return result
    }

    private fun transform(predicate: MethodPredicate): List<MethodPredicate> {
        return transform(predicate.predicate).map { newPredicate ->
            MethodPredicate(newPredicate, predicate.negated)
        }
    }

    private fun transform(predicate: Predicate): List<Predicate> {
        if (predicate.signature.isGeneratedStringConcat()) {
            // Replacing with String.concat()
            val newConstraints = predicate.constraint?.let { constraint ->
                transform(constraint) {
                    if (it is Position.Argument) {
                        val index = it.index
                        check(index is Position.ArgumentIndex.Concrete) { "Expected concrete argument index" }
                        check(index.idx in 0 until 2) { "Invalid index for string concat" }
                        if (index.idx == 0) {
                            Position.Object
                        } else {
                            Position.Argument(
                                Position.ArgumentIndex.Concrete(0)
                            )
                        }
                    } else {
                        it
                    }
                }
            }

            return (newConstraints ?: listOf(null)).map { newConstraint ->
                Predicate(stringConcatMethodSignature, newConstraint)
            }
        }

        val newConstraints = predicate.constraint?.let { constraint ->
            transform(constraint) { it }
        }
        return (newConstraints ?: listOf(null)).map { newConstraint ->
            Predicate(predicate.signature, newConstraint)
        }
    }

    private fun transform(
        constraint: MethodConstraint,
        positionTransform: (Position) -> Position
    ): List<MethodConstraint> {
        if (constraint !is ParamConstraint) {
            return listOf(constraint)
        }

        val newPosition = positionTransform(constraint.position)
        val newConditions = transform(constraint.condition)

        return newConditions.map { newCondition ->
            ParamConstraint(newPosition, newCondition)
        }
    }

    private fun transform(condition: ParamCondition.Atom): List<ParamCondition.Atom> {
        return when (condition) {
            is IsMetavar -> {
                val newMetavars = metavarMapping[condition.metavar] ?: return listOf(condition)
                val modified = newMetavars.map(::IsMetavar)

                if (condition.metavar !in newMetavars || newMetavars.size > 1) {
                    return modified + ParamCondition.TypeIs(TypeNamePattern.FullyQualified("java.lang.String"))
                } else {
                    return modified
                }
            }

            is StringValueMetaVar -> {
                val newMetavars = metavarMapping[condition.metaVar] ?: return listOf(condition)
                return newMetavars.map(::StringValueMetaVar)
            }

            else -> listOf(condition)
        }
    }

    companion object {
        val EMPTY: StringConcatCtx = StringConcatCtx(emptyMap())

        val stringConcatMethodSignature by lazy {
            MethodSignature(
                MethodName(SignatureName.Concrete("concat")),
                MethodEnclosingClassName(TypeNamePattern.FullyQualified("java.lang.String"))
            )
        }
    }
}

private fun eliminateStringConcat(
    effect: EdgeEffect,
    condition: EdgeCondition,
    ctx: StringConcatCtx,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge,
): EdgeEliminationResult<StringConcatCtx> {
    // TODO: rollback renaming of metavar when necessary (?)
    val generatedByConcatHelperMetavars = effect.assignMetaVar.mapNotNull { (metavar, preds) ->
        val isResultOfConcatHelper = preds.any {
            val predCondition = it.asConditionOnStringConcat<Position.Result>()
                ?: return@any false

            check(predCondition == IsMetavar(metavar)) { "Unexpected condition" }
            !it.negated
        }

        metavar.takeIf { isResultOfConcatHelper }
    }.toSet()

    if (generatedByConcatHelperMetavars.isEmpty()) {
        return ctx.transformEdge(effect, condition, rebuildEdge)
    }

    val metavarArguments = condition.readMetaVar.flatMap { (metavar, preds) ->
        val isArgumentOfConcatHelper = preds.any {
            val predCondition = it.asConditionOnStringConcat<Position.Argument>()
                ?: return@any false

            check(predCondition == IsMetavar(metavar)) { "Unexpected condition" }
            !it.negated
        }

        if (isArgumentOfConcatHelper) {
            ctx.metavarMapping.getOrElse(metavar) { setOf(metavar) }
        } else {
            emptyList()
        }
    }.toSet()

    val otherArguments = condition.other.mapNotNull {
        it.asConditionOnStringConcat<Position.Argument>()
    }

    if (otherArguments.isEmpty() || otherArguments.singleOrNull() == ParamCondition.AnyStringLiteral) {
        val newCtx = if (metavarArguments.size == 1 && metavarArguments == generatedByConcatHelperMetavars) {
            ctx
        } else {
            StringConcatCtx(
                metavarMapping = ctx.metavarMapping + generatedByConcatHelperMetavars.associateWith { metavarArguments }
            )
        }
        return EdgeEliminationResult.Eliminate(newCtx)
    }
    return ctx.transformEdge(effect, condition, rebuildEdge)
}

private fun StringConcatCtx.transformEdge(
    effect: EdgeEffect,
    condition: EdgeCondition,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge
): EdgeEliminationResult<StringConcatCtx> {
    val newEffect = transform(effect)
    val newCondition = transform(condition)

    return if (effect == newEffect && condition == newCondition) {
        EdgeEliminationResult.Unchanged
    } else {
        val newEdge = rebuildEdge(newEffect, newCondition)
        EdgeEliminationResult.Replace(newEdge, this)
    }
}

private inline fun <reified T : Position> MethodPredicate.asConditionOnStringConcat(): ParamCondition.Atom? {
    if (!predicate.signature.isGeneratedStringConcat()) {
        return null
    }

    val constraint = predicate.constraint as? ParamConstraint ?: return null

    if (constraint.position !is T) {
        return null
    }

    return constraint.condition
}

private fun MethodSignature.isGeneratedStringConcat(): Boolean {
    val name = methodName.name
    if (name !is SignatureName.Concrete) return false
    return name.name == generatedStringConcatMethodName
}


data class TaintRuleEdge(
    val stateFrom: State,
    val stateTo: State,
    val checkGlobalState: Boolean,
    val edgeCondition: EdgeCondition,
    val edgeEffect: EdgeEffect,
    val edgeKind: Kind,
) {
    enum class Kind {
        MethodEnter,
        MethodCall,
        MethodExit,
    }
}

sealed interface MetaVarConstraintOrPlaceHolder {
    data class Constraint(val constraint: MetaVarConstraints) : MetaVarConstraintOrPlaceHolder
    data class PlaceHolder(val constraint: MetaVarConstraints?) : MetaVarConstraintOrPlaceHolder
}

data class TaintRuleGenerationMetaVarInfo(
    val constraints: Map<String, MetaVarConstraintOrPlaceHolder>
)

open class TaintRuleGenerationCtx(
    val uniqueRuleId: String,
    val automata: TaintRegisterStateAutomata,
    val metaVarInfo: TaintRuleGenerationMetaVarInfo,
    val globalStateAssignStates: Set<State>,
    val edges: List<TaintRuleEdge>,
    val edgesToFinalAccept: List<TaintRuleEdge>,
    val edgesToFinalDead: List<TaintRuleEdge>,
) {
    private fun allStates(): List<State> {
        val result = mutableListOf<State>()
        edges.flatMapTo(result) { listOf(it.stateFrom, it.stateTo) }
        edgesToFinalAccept.flatMapTo(result) { listOf(it.stateFrom, it.stateTo) }
        edgesToFinalDead.flatMapTo(result) { listOf(it.stateFrom, it.stateTo) }
        return result
    }

    private val metaVarValues by lazy {
        val result = hashMapOf<MetavarAtom, MutableSet<Int>>()
        allStates().forEach {
            it.register.assignedVars.forEach { (mv, value) ->
                result.getOrPut(mv, ::hashSetOf).add(value)
            }
        }
        result
    }

    open fun allMarkValues(varName: MetavarAtom): List<String> {
        val varValues = metaVarValues[varName] ?: error("MetaVar is not assigned")
        return varValues.map { stateMarkName(varName, it) }
    }

    open fun stateMarkName(varName: MetavarAtom, varValue: Int): String =
        "${uniqueRuleId}|${varName}|$varValue"

    fun globalStateMarkName(state: State): String {
        val stateId = automata.stateId(state)
        return "${uniqueRuleId}__<STATE>__$stateId"
    }

    val stateVarPosition by lazy {
        PositionBaseWithModifiers.BaseOnly(
            PositionBase.ClassStatic("${uniqueRuleId}__<STATE>__")
        )
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

private fun TaintRegisterStateAutomata.allStates(): Set<State> {
    val states = hashSetOf<State>()
    states += initial
    states += finalAcceptStates
    states += finalDeadStates
    states += successors.keys
    return states
}

private fun RuleConversionCtx.generateTaintEdges(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
    uniqueRuleId: String
): TaintRuleGenerationCtx {
    val globalStateAssignStates = hashSetOf<State>()
    val taintRuleEdges = mutableListOf<TaintRuleEdge>()
    val finalAcceptEdges = mutableListOf<TaintRuleEdge>()
    val finalDeadEdges = mutableListOf<TaintRuleEdge>()

    val predecessors = automataPredecessors(automata)

    val outDegree = automata.successors.mapValuesTo(hashMapOf()) { it.value.size }

    val unprocessed = ArrayDeque<State>()

    fun enqueueState(state: State) {
        val current = outDegree[state]
        check(current != null && current > 0) { "Unexpected state degree: $current" }

        val next = current - 1
        outDegree[state] = next

        if (next == 0) {
            unprocessed.add(state)
        }
    }

    val allFinalStates = automata.finalAcceptStates + automata.finalDeadStates
    for (state in allFinalStates) {
        val current = outDegree[state]
        if (current == null || current == 0) {
            unprocessed.add(state)
        }
    }

    while (unprocessed.isNotEmpty()) {
        val dstState = unprocessed.removeFirst()

        val isFinal = dstState in automata.finalAcceptStates || dstState in automata.finalDeadStates

        for ((edge, state) in predecessors[dstState].orEmpty()) {
            enqueueState(state)

            val stateId = automata.stateId(state)
            val stateVars = state.register.assignedVars.filter { it.value == stateId }

            val globalVarRequired = when {
                state == automata.initial -> false
                stateVars.isEmpty() -> true
                else -> {
                    val readVars = when (edge) {
                        is Edge.EdgeWithCondition -> edge.condition.readMetaVar.keys
                        is Edge.AnalysisEnd -> emptySet()
                    }
                    stateVars.all { it.key !in readVars }
                }
            }

            if (isFinal) {
                val edgeDescriptor = edgeDescriptor(edge)
                    ?: continue

                if (globalVarRequired) {
                    globalStateAssignStates.add(state)
                }

                val edgeCollection = if (dstState in automata.finalAcceptStates) finalAcceptEdges else finalDeadEdges
                edgeCollection += TaintRuleEdge(
                    state, dstState,
                    checkGlobalState = globalVarRequired,
                    edgeDescriptor.condition, edgeDescriptor.effect, edgeDescriptor.kind
                )

                continue
            }

            val edgeRequired = state.register != dstState.register
                    || (dstState in globalStateAssignStates && dstState != state && edge.canAssignStateVar())

            if (!edgeRequired) continue

            if (globalVarRequired) {
                globalStateAssignStates.add(state)
            }

            val edgeDescriptor = edgeDescriptor(edge)
                ?: continue

            taintRuleEdges += TaintRuleEdge(
                state, dstState,
                checkGlobalState = globalVarRequired,
                edgeDescriptor.condition, edgeDescriptor.effect, edgeDescriptor.kind
            )
        }
    }

    check(outDegree.all { it.value == 0 }) { "Some states were not visited" }

    val initialStateWithGlobalAssign = hashSetOf<State>()
    for (state in globalStateAssignStates) {
        if (taintRuleEdges.any { it.stateTo == state }) continue
        if (finalAcceptEdges.any { it.stateTo == state }) continue
        if (finalDeadEdges.any { it.stateTo == state }) continue

        initialStateWithGlobalAssign.add(state)
    }

    if (initialStateWithGlobalAssign.isNotEmpty()) {
        globalStateAssignStates.removeAll(initialStateWithGlobalAssign)

        fun MutableList<TaintRuleEdge>.removeGlobalStateCheck() {
            for ((i, edge) in this.withIndex()) {
                if (edge.stateFrom in initialStateWithGlobalAssign) {
                    this[i] = edge.copy(checkGlobalState = false)
                }
            }
        }

        taintRuleEdges.removeGlobalStateCheck()
        finalAcceptEdges.removeGlobalStateCheck()
        finalDeadEdges.removeGlobalStateCheck()
    }

    val metVarConstraints = hashMapOf<String, MetaVarConstraintOrPlaceHolder>()

    val placeHolders = computePlaceHolders(taintRuleEdges, finalAcceptEdges, finalDeadEdges)
    placeHolders.placeHolderRequiredMetaVars.forEach {
        metVarConstraints[it] = MetaVarConstraintOrPlaceHolder.PlaceHolder(metaVarInfo.metaVarConstraints[it])
    }

    metaVarInfo.metaVarConstraints.forEach { (mv, c) ->
        if (mv !in metVarConstraints) {
            metVarConstraints[mv] = MetaVarConstraintOrPlaceHolder.Constraint(c)
        }
    }

    return TaintRuleGenerationCtx(
        uniqueRuleId, automata, TaintRuleGenerationMetaVarInfo(metVarConstraints),
        globalStateAssignStates, taintRuleEdges, finalAcceptEdges, finalDeadEdges
    )
}

private data class TaintEdgeDescriptor(
    val kind: TaintRuleEdge.Kind,
    val condition: EdgeCondition,
    val effect: EdgeEffect,
)

private fun RuleConversionCtx.edgeDescriptor(edge: Edge): TaintEdgeDescriptor? = when (edge) {
    is Edge.AnalysisEnd -> {
        semgrepRuleTrace.error("Unexpected analysis end edge", Reason.ERROR)
        null
    }

    is Edge.MethodCall -> TaintEdgeDescriptor(TaintRuleEdge.Kind.MethodCall, edge.condition, edge.effect)
    is Edge.MethodEnter -> TaintEdgeDescriptor(TaintRuleEdge.Kind.MethodEnter, edge.condition, edge.effect)
    is Edge.MethodExit -> TaintEdgeDescriptor(TaintRuleEdge.Kind.MethodExit, edge.condition, edge.effect)
}

private class MetaVarCtx {
    val metaVarIdx = hashMapOf<String, Int>()
    val metaVars = mutableListOf<String>()

    fun String.idx() = metaVarIdx.getOrPut(this) {
        metaVars.add(this)
        metaVarIdx.size
    }
}

private data class MetaVarPlaceHolders(
    val placeHolderRequiredMetaVars: Set<String>,
)

private fun computePlaceHolders(
    taintRuleEdges: List<TaintRuleEdge>,
    finalAcceptEdges: List<TaintRuleEdge>,
    finalDeadEdges: List<TaintRuleEdge>,
): MetaVarPlaceHolders {
    val predecessors = hashMapOf<State, MutableList<TaintRuleEdge>>()
    taintRuleEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }
    finalAcceptEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }
    finalDeadEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }

    val metaVarCtx = MetaVarCtx()

    val resultPlaceHolders = BitSet()
    val unprocessed = mutableListOf<Pair<State, PersistentBitSet>>()
    val visited = hashSetOf<Pair<State, PersistentBitSet>>()
    finalAcceptEdges.mapTo(unprocessed) { it.stateTo to emptyPersistentBitSet() }
    finalDeadEdges.mapTo(unprocessed) { it.stateTo to emptyPersistentBitSet() }

    while (unprocessed.isNotEmpty()) {
        val entry = unprocessed.removeLast()
        if (!visited.add(entry)) continue

        val (state, statePlaceholders) = entry

        for (edge in predecessors[state].orEmpty()) {
            val edgeMetaVars = BitSet()
            metaVarCtx.edgeConditionSignatureMetaVars(edge.edgeCondition, edgeMetaVars)
            metaVarCtx.edgeEffectSignatureMetaVars(edge.edgeEffect, edgeMetaVars)

            val nextMetaVars = statePlaceholders.persistentAddAll(edgeMetaVars)

            // metavar has multiple usages
            edgeMetaVars.and(statePlaceholders)
            resultPlaceHolders.or(edgeMetaVars)

            unprocessed.add(edge.stateFrom to nextMetaVars)
        }
    }

    if (resultPlaceHolders.isEmpty) {
        return MetaVarPlaceHolders(emptySet())
    }

    val placeHolders = hashSetOf<String>()
    resultPlaceHolders.forEach { placeHolders.add(metaVarCtx.metaVars[it]) }
    return MetaVarPlaceHolders(placeHolders)
}

private fun MetaVarCtx.edgeConditionSignatureMetaVars(condition: EdgeCondition, metaVars: BitSet) {
    condition.readMetaVar.values.forEach { predicates ->
        predicates.forEach { predicateSignatureMetaVars(it.predicate, metaVars) }
    }

    condition.other.forEach { predicateSignatureMetaVars(it.predicate, metaVars) }
}

private fun MetaVarCtx.edgeEffectSignatureMetaVars(effect: EdgeEffect, metaVars: BitSet) {
    effect.assignMetaVar.values.forEach { predicates ->
        predicates.forEach { predicateSignatureMetaVars(it.predicate, metaVars) }
    }
}

private fun MetaVarCtx.predicateSignatureMetaVars(predicate: Predicate, metaVars: BitSet) {
    methodSignatureMetaVars(predicate.signature, metaVars)
    predicate.constraint?.let { methodConstraintMetaVars(it, metaVars) }
}

private fun MetaVarCtx.methodSignatureMetaVars(signature: MethodSignature, metaVars: BitSet) {
    typeNameMetaVars(signature.enclosingClassName.name, metaVars)

    val name = signature.methodName.name
    if (name is SignatureName.MetaVar) {
        metaVars.set(name.metaVar.idx())
    }
}

private fun MetaVarCtx.methodConstraintMetaVars(signature: MethodConstraint, metaVars: BitSet) {
    when (signature) {
        is ClassModifierConstraint -> signatureModifierMetaVars(signature.modifier, metaVars)
        is MethodModifierConstraint -> signatureModifierMetaVars(signature.modifier, metaVars)
        is NumberOfArgsConstraint -> {}
        is ParamConstraint -> paramConditionMetaVars(signature.condition, metaVars)
    }
}

private fun MetaVarCtx.signatureModifierMetaVars(sm: SignatureModifier, metaVars: BitSet) {
    typeNameMetaVars(sm.type, metaVars)

    val value = sm.value
    if (value is SignatureModifierValue.MetaVar) {
        metaVars.set(value.metaVar.idx())
    }
}

private fun MetaVarCtx.paramConditionMetaVars(pc: ParamCondition.Atom, metaVars: BitSet) {
    when (pc) {
        is IsMetavar -> {} // handled semantically with taint engine
        is ParamCondition.ParamModifier -> signatureModifierMetaVars(pc.modifier, metaVars)

        is StringValueMetaVar -> {
            /**
             *  todo: for now we ignore metavar substitution
             *  "$A"; "$A" will trigger for different A values
             *  */
        }

        is ParamCondition.TypeIs -> {
            typeNameMetaVars(pc.typeName, metaVars)
        }

        is ParamCondition.SpecificStaticFieldValue -> {
            typeNameMetaVars(pc.fieldClass, metaVars)
        }

        ParamCondition.AnyStringLiteral,
        is SpecificBoolValue,
        is SpecificStringValue -> {
            // do nothing, no metavars
        }
    }
}

private fun MetaVarCtx.typeNameMetaVars(typeName: TypeNamePattern, metaVars: BitSet) {
    when (typeName) {
        is TypeNamePattern.MetaVar -> {
            metaVars.set(typeName.metaVar.idx())
        }

        TypeNamePattern.AnyType,
        is TypeNamePattern.ClassName,
        is TypeNamePattern.PrimitiveName,
        is TypeNamePattern.FullyQualified -> {
            // no metavars
        }
    }
}

private fun Edge.ensurePositiveCondition(ctx: RuleConversionCtx): Edge? = when (this) {
    is Edge.AnalysisEnd -> this
    is Edge.MethodCall -> condition.ensurePositiveCondition(ctx)?.let { copy(condition = it) }
    is Edge.MethodEnter -> condition.ensurePositiveCondition(ctx)?.let { copy(condition = it) }
    is Edge.MethodExit -> condition.ensurePositiveCondition(ctx)?.let { copy(condition = it) }
}

private fun EdgeCondition.ensurePositiveCondition(ctx: RuleConversionCtx): EdgeCondition? {
    if (containsPositivePredicate()) return this

    val signatures = hashSetOf<MethodSignature>()
    other.mapTo(signatures) { it.predicate.signature }
    readMetaVar.values.forEach { predicates -> predicates.mapTo(signatures) { it.predicate.signature } }

    if (signatures.size == 1) {
        // !f(a) /\ !f(b) -> f(*) /\ !f(a) /\ !f(b)
        val commonSignature = signatures.single()
        val positivePredicate = Predicate(commonSignature, constraint = null)
        val otherPredicates = other + MethodPredicate(positivePredicate, negated = false)
        return copy(other = otherPredicates)
    }

    ctx.semgrepRuleTrace.error(
        "Edge without positive predicate",
        Reason.ERROR
    )

    return null
}

private fun EdgeCondition.findPositivePredicate(): Predicate? =
    other.find { !it.negated }?.predicate
        ?: readMetaVar.values.firstNotNullOfOrNull { p -> p.find { !it.negated }?.predicate }

private fun EdgeCondition.containsPositivePredicate(): Boolean =
    other.any { !it.negated } || readMetaVar.values.any { p -> p.any { !it.negated } }

private fun Edge.canAssignStateVar(): Boolean = when (this) {
    is Edge.AnalysisEnd -> false
    is Edge.MethodCall -> true
    is Edge.MethodEnter -> true
    is Edge.MethodExit -> true
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

private fun simulateCondition(
    edge: Edge,
    stateId: Int,
    initialRegister: StateRegister
) = when (edge) {
    is Edge.MethodCall -> simulateEdgeEffect(edge.effect, stateId, initialRegister)
    is Edge.MethodEnter -> simulateEdgeEffect(edge.effect, stateId, initialRegister)
    is Edge.MethodExit -> simulateEdgeEffect(edge.effect, stateId, initialRegister)
    is Edge.AnalysisEnd -> StateRegister(emptyMap())
}

private fun simplifyEdgeCondition(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    cancelation: OperationCancelation,
    edge: AutomataEdgeType
) = when (edge) {
    is AutomataEdgeType.AutomataEdgeTypeWithFormula -> {
        simplifyMethodFormula(
            formulaManager, edge.formula, metaVarInfo, cancelation, applyNotEquivalentTransformations = true
        ).map {
            val (effect, cond) = edgeEffectAndCondition(it, formulaManager)

            when (edge) {
                is AutomataEdgeType.MethodCall -> Edge.MethodCall(cond, effect)
                is AutomataEdgeType.MethodEnter -> Edge.MethodEnter(cond, effect)
                is AutomataEdgeType.MethodExit -> Edge.MethodExit(cond, effect)
            }
        }
    }

    AutomataEdgeType.End -> listOf(Edge.AnalysisEnd)

    AutomataEdgeType.PatternEnd, AutomataEdgeType.PatternStart -> error("unexpected edge type: $edge")
}

private fun Cube.predicates(manager: MethodFormulaManager): List<MethodPredicate> {
    check(!negated) { "Negated cube" }

    val result = mutableListOf<MethodPredicate>()
    cube.positiveLiterals.forEach {
        result += MethodPredicate(manager.predicate(it), negated = false)
    }
    cube.negativeLiterals.forEach {
        result += MethodPredicate(manager.predicate(it), negated = true)
    }
    return result
}

private fun edgeEffectAndCondition(cube: Cube, formulaManager: MethodFormulaManager): Pair<EdgeEffect, EdgeCondition> {
    val predicates = cube.predicates(formulaManager)

    val metaVarWrite = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
    val metaVarRead = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
    val other = mutableListOf<MethodPredicate>()

    for (mp in predicates) {
        val metaVar = mp.findMetaVarConstraint()

        if (!mp.negated && metaVar != null) {
            metaVarWrite.getOrPut(metaVar, ::mutableListOf).add(mp)
        }

        if (metaVar != null) {
            metaVarRead.getOrPut(metaVar, ::mutableListOf).add(mp)
        } else {
            other.add(mp)
        }
    }

    return EdgeEffect(metaVarWrite) to EdgeCondition(metaVarRead, other)
}

private fun MethodPredicate.findMetaVarConstraint(): MetavarAtom? {
    val constraint = predicate.constraint
    return ((constraint as? ParamConstraint)?.condition as? IsMetavar)?.metavar
}

private fun simulateEdgeEffect(
    effect: EdgeEffect,
    stateId: Int,
    initialRegister: StateRegister,
): StateRegister {
    if (effect.assignMetaVar.isEmpty()) return initialRegister

    val newStateVars = initialRegister.assignedVars.toMutableMap()
    effect.assignMetaVar.keys.forEach {
        newStateVars[it] = stateId
    }

    effect.assignMetaVar.keys.forEach { metavar ->
        val basics = metavar.basics
        val toDelete = newStateVars.keys.filter {
            it.basics.intersect(basics).isNotEmpty() && it.basics.size < basics.size
        }
        toDelete.forEach(newStateVars::remove)
    }

    return StateRegister(newStateVars)
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
