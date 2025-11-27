package org.opentaint.org.opentaint.semgrep.pattern.conversion.taint

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import mu.KLogging
import org.opentaint.dataflow.configuration.jvm.serialized.AnalysisEndSink
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamPatternMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamStringMatcher
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
import org.opentaint.org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SpecificBoolValue
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SpecificStringValue
import org.opentaint.org.opentaint.semgrep.pattern.conversion.TypeNamePattern
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.Cube
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.StateRegister
import org.opentaint.org.opentaint.semgrep.pattern.conversion.opentaintAnyValueGeneratorMethodName
import org.opentaint.org.opentaint.semgrep.pattern.conversion.opentaintReturnValueMethod
import java.util.BitSet
import java.util.IdentityHashMap
import kotlin.math.absoluteValue

fun convertToTaintRules(
    rule: SemgrepRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
    ruleId: String,
    meta: SinkMetaData,
): TaintRuleFromSemgrep = when (rule) {
    is SemgrepMatchingRule -> convertMatchingRuleToTaintRules(rule, ruleId, meta)
    is SemgrepTaintRule -> convertTaintRuleToTaintRules(rule, ruleId, meta)
}

private fun convertMatchingRuleToTaintRules(
    rule: SemgrepMatchingRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
    ruleId: String,
    meta: SinkMetaData,
): TaintRuleFromSemgrep {
    val ruleGroups = rule.rules.mapIndexed { idx, r ->
        val automataId = "$ruleId#$idx"
        val rules = convertAutomataToTaintRules(r.metaVarInfo, r.rule, automataId, ruleId, meta)
        TaintRuleFromSemgrep.TaintRuleGroup(rules)
    }
    return TaintRuleFromSemgrep(ruleId, ruleGroups)
}

private fun convertTaintRuleToTaintRules(
    rule: SemgrepTaintRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
    ruleId: String,
    meta: SinkMetaData,
): TaintRuleFromSemgrep {
    val taintMarkName = "$ruleId#taint"
    val generatedRules = mutableListOf<SerializedItem>()

    for ((i, source) in rule.sources.withIndex()) {
        val (ctx, stateVars) = convertTaintSourceRule(ruleId, i, source)
        generatedRules += ctx.generateTaintSourceRules(stateVars, taintMarkName)
    }

    for ((i, sink) in rule.sinks.withIndex()) {
        val (ctx, stateVars, stateId) = convertTaintSinkRule(ruleId, i, sink)
        val sinkCtx = SinkRuleGenerationCtx(stateVars, stateId, taintMarkName, ctx)
        generatedRules += sinkCtx.generateTaintSinkRules(ruleId, meta)
    }

    for ((i, pass) in rule.propagators.withIndex()) {
        val (ctx, stateId) = generatePassRule(ruleId, i, pass.pattern, pass.from, pass.to)
        val sinkCtx = SinkRuleGenerationCtx(setOf(pass.from), stateId, taintMarkName, ctx)
        generatedRules += sinkCtx.generateTaintPassRules(pass.from, pass.to, taintMarkName)
    }

    if (rule.sanitizers.isNotEmpty()) {
        // todo: sanitizers
        logger.warn { "Rule $ruleId: sanitizers are not supported yet" }
    }

    val ruleGroup = TaintRuleFromSemgrep.TaintRuleGroup(generatedRules)
    return TaintRuleFromSemgrep(ruleId, listOf(ruleGroup))
}

private fun generatePassRule(
    ruleId: String,
    passIdx: Int,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>,
    fromMetaVar: String,
    toMetaVar: String
): Pair<TaintRuleGenerationCtx, Int> {
    val automata = rule.rule
    check(automata.isDeterministic) { "NFA not supported" }

    val taintAutomata = createAutomataWithoutGeneratedEdges(
        automata.formulaManager, rule.metaVarInfo, automata.initialNode
    )

    val initialStateId = taintAutomata.stateId(taintAutomata.initial)
    val initialRegister = StateRegister(mapOf(fromMetaVar to initialStateId))
    val newInitial = State(taintAutomata.initial.node, initialRegister)
    val taintAutomataWithState = taintAutomata.replaceInitialState(newInitial)

    val taintEdges = generateAutomataWithTaintEdges(
        taintAutomataWithState, rule.metaVarInfo,
        automataId = "$ruleId#pass_$passIdx", acceptStateVars = setOf(toMetaVar)
    )

    return taintEdges to initialStateId
}

private fun convertTaintSinkRule(
    ruleId: String,
    sinkIdx: Int,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>
): Triple<TaintRuleGenerationCtx, Set<String>, Int> {
    val automata = rule.rule
    check(automata.isDeterministic) { "NFA not supported" }

    val taintAutomata = createAutomataWithoutGeneratedEdges(
        automata.formulaManager, rule.metaVarInfo, automata.initialNode
    )
    val (sinkAutomata, stateMetaVars) = ensureSinkStateVars(taintAutomata, rule.metaVarInfo.focusMetaVars)

    val initialStateId = sinkAutomata.stateId(sinkAutomata.initial)
    val initialRegister = StateRegister(stateMetaVars.associateWith { initialStateId })
    val newInitial = State(sinkAutomata.initial.node, initialRegister)
    val sinkAutomataWithState = sinkAutomata.replaceInitialState(newInitial)

    val taintEdges = generateAutomataWithTaintEdges(
        sinkAutomataWithState, rule.metaVarInfo,
        automataId = "$ruleId#sink_$sinkIdx", acceptStateVars = emptySet()
    )

    return Triple(taintEdges, stateMetaVars, initialStateId)
}

private fun convertTaintSourceRule(
    ruleId: String,
    sourceIdx: Int,
    rule: RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>
): Pair<TaintRuleGenerationCtx, Set<String>> {
    val automata = rule.rule
    check(automata.isDeterministic) { "NFA not supported" }

    val taintAutomata = createAutomataWithoutGeneratedEdges(
        automata.formulaManager, rule.metaVarInfo, automata.initialNode
    )
    val (sourceAutomata, stateMetaVars) = ensureSourceStateVars(taintAutomata, rule.metaVarInfo.focusMetaVars)

    val taintEdges = generateAutomataWithTaintEdges(
        sourceAutomata, rule.metaVarInfo,
        automataId = "$ruleId#source_$sourceIdx", acceptStateVars = stateMetaVars
    )

    val finalAcceptEdges = taintEdges.finalEdges.filter { it.stateTo.node.accept }
    val assignedStateVars = finalAcceptEdges.flatMapTo(hashSetOf()) { it.stateTo.register.assignedVars.keys }
    assignedStateVars.retainAll(stateMetaVars)

    return taintEdges to assignedStateVars
}

private fun ensureSinkStateVars(
    automata: TaintRegisterStateAutomata,
    focusMetaVars: Set<String>
): Pair<TaintRegisterStateAutomata, Set<String>> {
    if (focusMetaVars.isNotEmpty()) return automata to focusMetaVars

    val freshVar = "generated_sink_requirement"
    val edgeReplacement = mutableListOf<EdgeReplacement>()

    for ((edge, dstState) in automata.successors[automata.initial].orEmpty()) {
        when (edge) {
            is Edge.MethodCall -> {
                val positivePredicate = edge.condition.findPositivePredicate() ?: continue

                val conditionVars = edge.condition.readMetaVar.toMutableMap()
                val argumentIndex = Position.ArgumentIndex.Any(paramClassifier = "tainted")
                val condition = ParamConstraint(Position.Argument(argumentIndex), IsMetavar(freshVar))
                val predicate = Predicate(positivePredicate.signature, condition)

                conditionVars[freshVar] = listOf(MethodPredicate(predicate, negated = false))
                val edgeCondition = EdgeCondition(conditionVars, edge.condition.other)

                val modifiedEdge = Edge.MethodCall(edgeCondition, edge.effect)
                edgeReplacement += EdgeReplacement(automata.initial, dstState, edge, modifiedEdge)
            }

            Edge.AnalysisEnd,
            is Edge.MethodEnter -> continue
        }
    }

    val resultAutomata = automata.replaceEdges(edgeReplacement)
    return resultAutomata to setOf(freshVar)
}

private fun ensureSourceStateVars(
    automata: TaintRegisterStateAutomata,
    focusMetaVars: Set<String>
): Pair<TaintRegisterStateAutomata, Set<String>> {
    if (focusMetaVars.isNotEmpty()) return automata to focusMetaVars

    val freshVar = "generated_source"
    val edgeReplacement = mutableListOf<EdgeReplacement>()

    val predecessors = automataPredecessors(automata)
    val acceptStates = automata.final.filter { it.node.accept }
    for (dstState in acceptStates) {
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

                Edge.AnalysisEnd,
                is Edge.MethodEnter -> continue
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
        formulaManager, initial, final, mutableSuccessors, nodeIndex
    )
}

private fun TaintRegisterStateAutomata.replaceInitialState(newInitial: State): TaintRegisterStateAutomata {
    val newFinal = final.toHashSet()
    if (newFinal.remove(initial)) {
        newFinal.add(newInitial)
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

    return TaintRegisterStateAutomata(formulaManager, newInitial, newFinal, successors, nodeIndex)
}

private fun convertAutomataToTaintRules(
    metaVarInfo: ResolvedMetaVarInfo,
    automata: SemgrepRuleAutomata,
    automataId: String,
    id: String, meta: SinkMetaData,
): List<SerializedItem> {
    check(automata.isDeterministic) { "NFA not supported" }

    val taintAutomata = createAutomataWithoutGeneratedEdges(
        automata.formulaManager, metaVarInfo, automata.initialNode
    )
    val ctx = generateAutomataWithTaintEdges(taintAutomata, metaVarInfo, automataId, acceptStateVars = emptySet())
    return ctx.generateTaintSinkRules(id, meta)
}

private fun createAutomataWithoutGeneratedEdges(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialNode: AutomataNode
): TaintRegisterStateAutomata {
    val automata = createAutomata(formulaManager, metaVarInfo, initialNode)
    val automataWithoutGeneratedEdges = eliminateAnyValueGenerator(automata)
    return automataWithoutGeneratedEdges
}

private fun generateAutomataWithTaintEdges(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
    automataId: String,
    acceptStateVars: Set<String>
): TaintRuleGenerationCtx {
    val simulated = simulateAutomata(automata)
    val cleaned = removeUnreachabeStates(simulated)
    val liveAutomata = eliminateDeadVariables(cleaned, acceptStateVars)
    val automataWithoutEnd = tryRemoveEndEdge(liveAutomata)
    return generateTaintEdges(automataWithoutEnd, metaVarInfo, automataId)
}

data class TaintRegisterStateAutomata(
    val formulaManager: MethodFormulaManager,
    val initial: State,
    val final: Set<State>,
    val successors: Map<State, Set<Pair<Edge, State>>>,
    val nodeIndex: Map<AutomataNode, Int>
) {
    data class StateRegister(
        val assignedVars: Map<String, Int>,
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
        val readMetaVar: Map<String, List<MethodPredicate>>,
        val other: List<MethodPredicate>
    )

    data class EdgeEffect(
        val assignMetaVar: Map<String, List<MethodPredicate>>
    )

    sealed interface Edge {
        data class MethodCall(val condition: EdgeCondition, val effect: EdgeEffect) : Edge
        data class MethodEnter(val condition: EdgeCondition, val effect: EdgeEffect) : Edge
        data object AnalysisEnd : Edge
    }

    fun stateId(state: State): Int = nodeIndex[state.node] ?: error("Missing node")
}

data class TaintRuleEdge(
    val stateFrom: State,
    val stateTo: State,
    val edge: Edge,
    val checkGlobalState: Boolean,
)

data class MetaVarPlaceHolders(
    val placeHolderRequiredMetaVars: Set<String>
)

open class TaintRuleGenerationCtx(
    val uniqueRuleId: String,
    val automata: TaintRegisterStateAutomata,
    val metaVarInfo: ResolvedMetaVarInfo,
    val placeHolders: MetaVarPlaceHolders,
    val globalStateAssignStates: Set<State>,
    val edges: List<TaintRuleEdge>,
    val finalEdges: List<TaintRuleEdge>,
) {
    open fun markName(varName: String, varValue: Int): String =
        "${uniqueRuleId}_${varName}_$varValue"

    fun stateMarkName(state: State): String {
        val stateId = automata.stateId(state)
        return "${uniqueRuleId}__STATE__$stateId"
    }

    val stateVarPosition by lazy {
        PositionBaseWithModifiers.BaseOnly(
            PositionBase.ClassStatic("${uniqueRuleId}__STATE__")
        )
    }
}

private class SinkRuleGenerationCtx(
    val initialStateVars: Set<String>,
    val initialVarValue: Int,
    val taintMarkName: String,
    uniqueRuleId: String,
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
    placeHolders: MetaVarPlaceHolders,
    globalStateAssignStates: Set<State>,
    edges: List<TaintRuleEdge>,
    finalEdges: List<TaintRuleEdge>
) : TaintRuleGenerationCtx(
    uniqueRuleId, automata, metaVarInfo, placeHolders,
    globalStateAssignStates, edges, finalEdges
) {
    constructor(
        initialStateVars: Set<String>, initialVarValue: Int, taintMarkName: String,
        ctx: TaintRuleGenerationCtx
    ) : this(
        initialStateVars, initialVarValue, taintMarkName,
        ctx.uniqueRuleId, ctx.automata, ctx.metaVarInfo, ctx.placeHolders,
        ctx.globalStateAssignStates, ctx.edges, ctx.finalEdges
    )

    override fun markName(varName: String, varValue: Int): String {
        if (varName in initialStateVars && varValue == initialVarValue) {
            return taintMarkName
        }
        return super.markName(varName, varValue)
    }
}

private fun TaintRegisterStateAutomata.allStates(): Set<State> {
    val states = hashSetOf<State>()
    states += initial
    states += final
    states += successors.keys
    return states
}

private fun createAutomata(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialNode: AutomataNode
): TaintRegisterStateAutomata {
    val nodeIds = hashMapOf<AutomataNode, Int>()
    fun nodeId(node: AutomataNode): Int = nodeIds.getOrPut(node) { nodeIds.size }

    val emptyRegister = StateRegister(emptyMap())
    val initialState = State(initialNode, emptyRegister)

    val processedStates = hashSetOf<State>()
    val unprocessed = mutableListOf(initialState)

    val finalStates = hashSetOf<State>()
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!processedStates.add(state)) continue

        // force eval
        nodeId(state.node)

        if (state.node.accept) {
            finalStates.add(state)
            // note: no need transitions from final state
            continue
        }

        for ((edgeCondition, dstNode) in state.node.outEdges) {
            for (simplifiedEdge in simplifyEdgeCondition(formulaManager, metaVarInfo, edgeCondition)) {
                val nextState = State(dstNode, emptyRegister)
                successors.getOrPut(state, ::hashSetOf).add(simplifiedEdge to nextState)
                unprocessed.add(nextState)
            }
        }
    }

    check(finalStates.isNotEmpty()) { "Automata has no accept state" }

    return TaintRegisterStateAutomata(formulaManager, initialState, finalStates, successors, nodeIds)
}

data class SimulationState(
    val original: State,
    val state: State,
    val originalPath: PersistentMap<State, State>
)

private fun simulateAutomata(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val initialSimulationState = SimulationState(
        automata.initial, automata.initial,
        persistentHashMapOf(automata.initial to automata.initial)
    )
    val unprocessed = mutableListOf(initialSimulationState)

    val finalStates = hashSetOf<State>()
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    while (unprocessed.isNotEmpty()) {
        val simulationState = unprocessed.removeLast()
        val state = simulationState.state

        if (simulationState.original in automata.final) {
            finalStates.add(state)
            continue
        }

        for ((simplifiedEdge, dstState) in automata.successors[simulationState.original].orEmpty()) {
            val loopStartState = simulationState.originalPath[dstState]
            if (loopStartState != null) {
                if (loopStartState.register == state.register) {
                    // loop has no assignments
                    continue
                }

                TODO("Loop assign vars")
            }

            val dstStateId = automata.stateId(dstState)
            val dstStateRegister = simulateCondition(simplifiedEdge, dstStateId, state.register)

            val nextState = dstState.copy(register = dstStateRegister)
            successors.getOrPut(state, ::hashSetOf).add(simplifiedEdge to nextState)

            val nextPath = simulationState.originalPath.put(dstState, nextState)
            val nextSimulationState = SimulationState(dstState, nextState, nextPath)
            unprocessed.add(nextSimulationState)
        }
    }

    return TaintRegisterStateAutomata(automata.formulaManager, automata.initial, finalStates, successors, automata.nodeIndex)
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

private fun removeUnreachabeStates(
    automata: TaintRegisterStateAutomata
): TaintRegisterStateAutomata {
    val predecessors = automataPredecessors(automata)

    val reachableStates = hashSetOf<State>()
    val unprocessed = automata.final.toMutableList()
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
        return TaintRegisterStateAutomata(automata.formulaManager, automata.initial, automata.final, reachableSuccessors, automata.nodeIndex)
    }

    val nodeIndex = automata.nodeIndex.toMutableMap()
    nodeIndex[cleanerState.node] = nodeIndex.size

    val finalNodes = automata.final + cleanerState
    return TaintRegisterStateAutomata(automata.formulaManager, automata.initial, finalNodes, reachableSuccessors, nodeIndex)
}

private fun eliminateDeadVariables(
    automata: TaintRegisterStateAutomata,
    acceptStateLiveVars: Set<String>
): TaintRegisterStateAutomata {
    val predecessors = automataPredecessors(automata)

    val variableIdx = hashMapOf<String, Int>()
    val stateLiveVars = IdentityHashMap<State, PersistentBitSet>()

    val unprocessed = mutableListOf<Pair<State, PersistentBitSet>>()
    for (state in automata.final) {
        if (!state.node.accept) {
            unprocessed.add(state to emptyPersistentBitSet())
            continue
        }

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
                Edge.AnalysisEnd -> emptySet()
                is Edge.MethodCall -> edge.condition.readMetaVar.keys
                is Edge.MethodEnter -> edge.condition.readMetaVar.keys
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
        stateMapping[state] = State(state.node, register)
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
        final = automata.final.mapTo(hashSetOf()) { stateMapping[it] ?: it },
        successors = successors,
        nodeIndex = automata.nodeIndex
    )
}

private fun tryRemoveEndEdge(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val predecessors = automataPredecessors(automata)
    val finalReplacement = mutableListOf<Pair<State, State>>()

    for (finalState in automata.final) {
        val preFinalEdges = predecessors[finalState] ?: continue
        if (preFinalEdges.size != 1) continue

        val (edge, predecessor) = preFinalEdges.single()
        if (edge !is Edge.AnalysisEnd) continue

        if (automata.successors[predecessor].orEmpty().size != 1) continue

        finalReplacement.add(finalState to predecessor)
    }

    if (finalReplacement.isEmpty()) return automata

    val successors = automata.successors.toMutableMap()
    val final = automata.final.toHashSet()

    for ((oldState, newState) in finalReplacement) {
        successors.remove(oldState)
        successors[newState] = emptySet()

        final.remove(oldState)

        if (oldState.node.accept) {
            newState.node.accept = true
        }
        final.add(newState)
    }

    return TaintRegisterStateAutomata(
        automata.formulaManager,
        automata.initial,
        final, successors,
        automata.nodeIndex
    )
}

private data class ValueGeneratorCtx(
    val valueConstraint: Map<String, List<ParamCondition.Atom>>
)

private data class StateWithValueGeneratorContext(
    val state: State,
    val valueGeneratorContext: ValueGeneratorCtx
)

private fun eliminateAnyValueGenerator(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    val finalStates = automata.final.toHashSet()
    val removedStates = hashSetOf<State>()

    val unprocessed = mutableListOf(StateWithValueGeneratorContext(automata.initial, ValueGeneratorCtx(emptyMap())))
    val visited = hashSetOf<StateWithValueGeneratorContext>()
    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!visited.add(state)) continue

        val stateSuccessors = successors.getOrPut(state.state, ::hashSetOf)
        eliminateAnyValueGeneratorEdges(state, automata.successors, finalStates, removedStates, stateSuccessors, unprocessed)
    }

    finalStates.removeAll(removedStates)
    removedStates.forEach { successors.remove(it) }
    finalStates.forEach { successors.remove(it) }

    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial, finalStates, successors, automata.nodeIndex
    )
}

private fun eliminateAnyValueGeneratorEdges(
    state: StateWithValueGeneratorContext,
    successors: Map<State, Set<Pair<Edge, State>>>,
    finalStates: MutableSet<State>,
    removedStates: MutableSet<State>,
    resultStateSuccessors: MutableSet<Pair<Edge, State>>,
    unprocessed: MutableList<StateWithValueGeneratorContext>
) {
    for ((edge, nextState) in successors[state.state].orEmpty()) {
        val elimResult = eliminateAnyValueGenerator(edge, state.valueGeneratorContext)
        when (elimResult) {
            EdgeEliminationResult.Unchanged -> {
                resultStateSuccessors.add(edge to nextState)
                unprocessed.add(StateWithValueGeneratorContext(nextState, state.valueGeneratorContext))
                continue
            }

            is EdgeEliminationResult.Replace -> {
                resultStateSuccessors.add(elimResult.newEdge to nextState)
                unprocessed.add(StateWithValueGeneratorContext(nextState, elimResult.ctx))
                continue
            }

            is EdgeEliminationResult.Eliminate -> {
                if (nextState in finalStates) {
                    val nextSuccessors = successors[nextState].orEmpty()
                    check(nextSuccessors.isEmpty())

                    removedStates.add(nextState)
                    finalStates.add(state.state)

                    if (nextState.node.accept) {
                        state.state.node.accept = true
                    }
                }

                if (nextState == state.state) continue

                eliminateAnyValueGeneratorEdges(
                    StateWithValueGeneratorContext(nextState, elimResult.ctx),
                    successors, finalStates, removedStates, resultStateSuccessors, unprocessed
                )
            }
        }
    }
}

private sealed interface EdgeEliminationResult {
    data object Unchanged : EdgeEliminationResult
    data class Replace(val newEdge: Edge, val ctx: ValueGeneratorCtx) : EdgeEliminationResult
    data class Eliminate(val ctx: ValueGeneratorCtx) : EdgeEliminationResult
}

private fun eliminateAnyValueGenerator(edge: Edge, ctx: ValueGeneratorCtx): EdgeEliminationResult = when (edge) {
    Edge.AnalysisEnd -> EdgeEliminationResult.Unchanged
    is Edge.MethodCall -> eliminateAnyValueGenerator(edge.effect, edge.condition, ctx) { effect, cond ->
        Edge.MethodCall(cond, effect)
    }

    is Edge.MethodEnter -> eliminateAnyValueGenerator(edge.effect, edge.condition, ctx) { effect, cond ->
        Edge.MethodEnter(cond, effect)
    }
}

private fun eliminateAnyValueGenerator(
    effect: EdgeEffect,
    condition: EdgeCondition,
    ctx: ValueGeneratorCtx,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge,
): EdgeEliminationResult {
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

    val valueGenEffect = hashMapOf<String, MutableList<MethodPredicate>>()
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
    predicate.signature.isOpentaintAnyValueGenerator()

private fun MethodSignature.isOpentaintAnyValueGenerator(): Boolean {
    val name = methodName.name
    if (name !is SignatureName.Concrete) return false
    return name.name == opentaintAnyValueGeneratorMethodName
}

private fun MethodSignature.isOpentaintReturnValue(): Boolean {
    val name = methodName.name
    if (name !is SignatureName.Concrete) return false
    return name.name == opentaintReturnValueMethod
}

private fun generateTaintEdges(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
    uniqueRuleId: String
): TaintRuleGenerationCtx {
    val globalStateAssignStates = hashSetOf<State>()
    val taintRuleEdges = mutableListOf<TaintRuleEdge>()
    val finalEdges = mutableListOf<TaintRuleEdge>()

    val predecessors = automataPredecessors(automata)

    val unprocessed = ArrayDeque<State>()
    unprocessed.addAll(automata.final)
    val visited = hashSetOf<State>()

    while (unprocessed.isNotEmpty()) {
        val dstState = unprocessed.removeFirst()
        if (!visited.add(dstState)) continue

        val isFinal = dstState in automata.final

        for ((edge, state) in predecessors[dstState].orEmpty()) {
            unprocessed.add(state)

            val stateId = automata.stateId(state)
            val stateVars = state.register.assignedVars.filter { it.value == stateId }

            val globalVarRequired = when {
                state == automata.initial -> false
                edge is Edge.AnalysisEnd -> true
                else -> {
                    val writeVars = when (edge) {
                        Edge.AnalysisEnd -> emptySet()
                        is Edge.MethodCall -> edge.effect.assignMetaVar.keys
                        is Edge.MethodEnter -> edge.effect.assignMetaVar.keys
                    }
                    state.register.assignedVars.isNotEmpty() && stateVars.all { it.key !in writeVars }
                }
            }

            if (isFinal) {
                if (dstState.node.accept || state.register.assignedVars.isNotEmpty()) {
                    if (globalVarRequired) {
                        globalStateAssignStates.add(state)
                    }

                    val taintEdge = edge.ensurePositiveCondition()
                    finalEdges += TaintRuleEdge(state, dstState, taintEdge, globalVarRequired)
                }

                continue
            }

            val edgeRequired = state.register != dstState.register
                    || (dstState in globalStateAssignStates && dstState != state && edge.canAssignStateVar())

            if (!edgeRequired) continue

            if (globalVarRequired) {
                globalStateAssignStates.add(state)
            }

            val taintEdge = edge.ensurePositiveCondition()
            taintRuleEdges += TaintRuleEdge(state, dstState, taintEdge, globalVarRequired)
        }
    }

    val placeHolders = computePlaceHolders(taintRuleEdges, finalEdges)
    return TaintRuleGenerationCtx(
        uniqueRuleId, automata, metaVarInfo, placeHolders,
        globalStateAssignStates, taintRuleEdges, finalEdges
    )
}

private class MetaVarCtx {
    val metaVarIdx = hashMapOf<String, Int>()
    val metaVars = mutableListOf<String>()

    fun String.idx() = metaVarIdx.getOrPut(this) {
        metaVars.add(this)
        metaVarIdx.size
    }
}

private fun computePlaceHolders(
    taintRuleEdges: List<TaintRuleEdge>,
    finalEdges: List<TaintRuleEdge>
): MetaVarPlaceHolders {
    val predecessors = hashMapOf<State, MutableList<TaintRuleEdge>>()
    taintRuleEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }
    finalEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }

    val metaVarCtx = MetaVarCtx()

    val resultPlaceHolders = BitSet()
    val unprocessed = mutableListOf<Pair<State, PersistentBitSet>>()
    val visited = hashSetOf<Pair<State, PersistentBitSet>>()
    finalEdges.mapTo(unprocessed) { it.stateTo to emptyPersistentBitSet() }

    while (unprocessed.isNotEmpty()) {
        val entry = unprocessed.removeLast()
        if (!visited.add(entry)) continue

        val (state, statePlaceholders) = entry

        for (edge in predecessors[state].orEmpty()) {
            val edgeMetaVars = metaVarCtx.signatureMetaVars(edge.edge)

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

private fun MetaVarCtx.signatureMetaVars(edge: Edge): BitSet = when (edge) {
    Edge.AnalysisEnd -> BitSet()

    is Edge.MethodCall -> {
        val metaVars = BitSet()
        edgeConditionSignatureMetaVars(edge.condition, metaVars)
        edgeEffectSignatureMetaVars(edge.effect, metaVars)
        metaVars
    }

    is Edge.MethodEnter -> {
        val metaVars = BitSet()
        edgeConditionSignatureMetaVars(edge.condition, metaVars)
        edgeEffectSignatureMetaVars(edge.effect, metaVars)
        metaVars
    }
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

        is ParamCondition.StringValueMetaVar -> {
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

private fun Edge.ensurePositiveCondition(): Edge = when (this) {
    Edge.AnalysisEnd -> this
    is Edge.MethodCall -> copy(condition = condition.ensurePositiveCondition())
    is Edge.MethodEnter -> copy(condition = condition.ensurePositiveCondition())
}

private fun EdgeCondition.ensurePositiveCondition(): EdgeCondition {
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

    TODO("Edge without positive predicate")
}

private fun EdgeCondition.findPositivePredicate(): Predicate? =
    other.find { !it.negated }?.predicate
        ?: readMetaVar.values.firstNotNullOfOrNull { p -> p.find { !it.negated }?.predicate }

private fun EdgeCondition.containsPositivePredicate(): Boolean =
    other.any { !it.negated } || readMetaVar.values.any { p -> p.any { !it.negated } }

private fun Edge.canAssignStateVar(): Boolean = when (this) {
    Edge.AnalysisEnd -> false
    is Edge.MethodCall -> effect.assignMetaVar.isNotEmpty()
    is Edge.MethodEnter -> effect.assignMetaVar.isNotEmpty()
}

private data class RegisterVarPosition(val varName: String, val positions: MutableSet<PositionBase>)

private data class RuleCondition(
    val enclosingClassPackage: SerializedNameMatcher,
    val enclosingClassName: SerializedNameMatcher,
    val name: SerializedNameMatcher,
    val condition: SerializedCondition,
)

private data class EvaluatedEdgeCondition(
    val ruleCondition: RuleCondition,
    val additionalFieldRules: List<SerializedFieldRule>,
    val accessedVarPosition: Map<String, RegisterVarPosition>
)

private fun TaintRuleGenerationCtx.generateTaintSinkRules(id: String, meta: SinkMetaData) =
    generateTaintRules { currentRules, ruleEdge, _, function, cond ->
        if (function.matchAnything() && cond is SerializedCondition.True) {
            logger.warn { "Rule $id match anything" }
            return@generateTaintRules emptyList()
        }

        if (function.isOpentaintReturnValue()) {
            return@generateTaintRules generateEndSink(currentRules, cond, id, meta)
        }

        val rule = when (ruleEdge.edge) {
            is Edge.MethodEnter -> SerializedRule.MethodEntrySink(
                function, signature = null, overrides = false, cond, id, meta = meta
            )

            is Edge.MethodCall -> SerializedRule.Sink(
                function, signature = null, overrides = false, cond, id, meta = meta
            )

            Edge.AnalysisEnd -> return@generateTaintRules generateEndSink(currentRules, cond, id, meta)
        }
        listOf(rule)
    }

private fun generateEndSink(
    currentRules: List<SerializedItem>,
    cond: SerializedCondition,
    id: String,
    meta: SinkMetaData
): List<SinkRule> {
    val endCondition = cond.rewriteAsEndCondition()
    val entryPointRules = currentRules.filterIsInstance<SerializedRule.EntryPoint>()

    if (entryPointRules.isEmpty()) {
        return listOf(AnalysisEndSink(endCondition, id, meta = meta))
    }

    return entryPointRules.map { rule ->
        val sinkCond = SerializedCondition.and(listOf(rule.condition ?: SerializedCondition.True, endCondition))
        SerializedRule.MethodExitSink(rule.function, rule.signature, rule.overrides, sinkCond, id, meta = meta)
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

private fun TaintRuleGenerationCtx.generateTaintSourceRules(
    stateVars: Set<String>, taintMarkName: String
) = generateTaintRules { _, ruleEdge, condition, function, cond ->
    val actions = stateVars.flatMapTo(mutableListOf()) { varName ->
        val varPosition = condition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
        varPosition.positions.map {
            SerializedTaintAssignAction(taintMarkName, pos = PositionBaseWithModifiers.BaseOnly(it))
        }
    }

    if (actions.isEmpty()) return@generateTaintRules emptyList()

    if (function.isOpentaintReturnValue()) {
        TODO("Eliminate opentaint return value")
    }

    val rule = when (ruleEdge.edge) {
        is Edge.MethodCall -> SerializedRule.Source(
            function, signature = null, overrides = false, cond, actions
        )

        is Edge.MethodEnter -> SerializedRule.EntryPoint(
            function, signature = null, overrides = false, cond, actions
        )

        Edge.AnalysisEnd -> TODO()
    }

    listOf(rule)
}

private fun SinkRuleGenerationCtx.generateTaintPassRules(
    fromVar: String, toVar: String,
    taintMarkName: String
): List<SerializedItem> {
    // todo: generate taint pass when possible
    return generateTaintSourceRules(setOf(toVar), taintMarkName)
}

private fun TaintRuleGenerationCtx.generateTaintRules(
    generateAcceptStateRules: (
        currentGeneratedRules: List<SerializedItem>,
        TaintRuleEdge,
        EvaluatedEdgeCondition,
        SerializedFunctionNameMatcher,
        SerializedCondition
    ) -> List<SerializedItem>
): List<SerializedItem> {
    val rules = mutableListOf<SerializedItem>()

    val evaluatedConditions = hashMapOf<State, MutableMap<Edge, EvaluatedEdgeCondition>>()

    fun evaluate(edge: Edge, state: State): EvaluatedEdgeCondition =
        evaluatedConditions
            .getOrPut(state, ::hashMapOf)
            .getOrPut(edge) { evaluateEdgeCondition(edge, state) }

    for (ruleEdge in edges) {
        val edge = ruleEdge.edge
        val state = ruleEdge.stateFrom

        val condition = evaluate(edge, state).addGlobalStateCheck(this, ruleEdge.checkGlobalState, state)
        rules += condition.additionalFieldRules

        val nodeId = automata.stateId(ruleEdge.stateTo)

        val requiredVariables = ruleEdge.stateTo.register.assignedVars.keys
        val actions = requiredVariables.flatMapTo(mutableListOf()) { varName ->
            val varPosition = condition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
            val mark = markName(varPosition.varName, nodeId)
            varPosition.positions.map {
                SerializedTaintAssignAction(mark, pos = PositionBaseWithModifiers.BaseOnly(it))
            }
        }

        if (ruleEdge.stateTo in globalStateAssignStates) {
            actions += SerializedTaintAssignAction(stateMarkName(ruleEdge.stateTo), pos = stateVarPosition)
        }

        if (actions.isNotEmpty()) {
            rules += generateRules(condition.ruleCondition) { function, cond ->
                if (function.isOpentaintReturnValue()) {
                    TODO("Eliminate opentaint return value")
                }

                when (edge) {
                    is Edge.MethodCall -> SerializedRule.Source(
                        function, signature = null, overrides = false, cond, actions
                    )

                    is Edge.MethodEnter -> SerializedRule.EntryPoint(
                        function, signature = null, overrides = false, cond, actions
                    )

                    Edge.AnalysisEnd -> TODO()
                }
            }
        }
    }

    for (ruleEdge in finalEdges) {
        val edge = ruleEdge.edge
        val state = ruleEdge.stateFrom

        val condition = evaluate(edge, state).addGlobalStateCheck(this, ruleEdge.checkGlobalState, state)
        rules += condition.additionalFieldRules

        if (ruleEdge.stateTo.node.accept) {
            rules += generateRules(condition.ruleCondition) { function, cond ->
                generateAcceptStateRules(rules, ruleEdge, condition, function, cond)
            }

            continue
        }

        val actions = condition.accessedVarPosition.values.flatMapTo(mutableListOf()) { varPosition ->
            val value = state.register.assignedVars[varPosition.varName] ?: return@flatMapTo emptyList()
            val mark = markName(varPosition.varName, value)
            varPosition.positions.map {
                SerializedTaintCleanAction(mark, PositionBaseWithModifiers.BaseOnly(it))
            }
        }

        if (state in globalStateAssignStates) {
            actions += SerializedTaintCleanAction(stateMarkName(state), stateVarPosition)
        }

        if (actions.isNotEmpty()) {
            if (edge !is Edge.MethodCall) {
                TODO()
            }

            rules += generateRules(condition.ruleCondition) { function, cond ->
                if (function.isOpentaintReturnValue()) {
                    TODO("Eliminate opentaint return value")
                }

                SerializedRule.Cleaner(function, signature = null, overrides = false, cond, actions)
            }
        }
    }

    return rules
}

private fun EvaluatedEdgeCondition.addGlobalStateCheck(
    ctx: TaintRuleGenerationCtx,
    checkGlobalState: Boolean,
    state: State
): EvaluatedEdgeCondition {
    if (!checkGlobalState) return this

    val condition = SerializedCondition.ContainsMark(ctx.stateMarkName(state), ctx.stateVarPosition)
    val rc = ruleCondition.condition
    return copy(ruleCondition = ruleCondition.copy(condition = SerializedCondition.and(listOf(condition, rc))))
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

private fun TaintRuleGenerationCtx.evaluateEdgeCondition(
    edge: Edge,
    state: State
): EvaluatedEdgeCondition = when (edge) {
    is Edge.MethodCall -> evaluateMethodConditionAndEffect(edge.condition, edge.effect, state)
    is Edge.MethodEnter -> evaluateMethodConditionAndEffect(edge.condition, edge.effect, state)
    Edge.AnalysisEnd -> EvaluatedEdgeCondition(RuleConditionBuilder().build(), emptyList(), emptyMap())
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
    state: State
): EvaluatedEdgeCondition {
    if (placeHolders.placeHolderRequiredMetaVars.isNotEmpty()) {
        TODO("Placeholders")
    }

    val ruleBuilder = RuleConditionBuilder()
    val additionalFieldRules = mutableListOf<SerializedFieldRule>()

    evaluateConditionAndEffectSignatures(effect, condition, ruleBuilder)

    condition.readMetaVar.values.flatten().forEach {
        evaluateEdgePredicateConstraint(it.predicate.constraint, it.negated, state, ruleBuilder, additionalFieldRules)
    }

    condition.other.forEach {
        evaluateEdgePredicateConstraint(it.predicate.constraint, it.negated, state, ruleBuilder, additionalFieldRules)
    }

    val varPositions = hashMapOf<String, RegisterVarPosition>()
    effect.assignMetaVar.values.flatten().forEach {
        findMetaVarPosition(it.predicate.constraint, varPositions)
    }

    return EvaluatedEdgeCondition(ruleBuilder.build(), additionalFieldRules, varPositions)
}

private fun TaintRuleGenerationCtx.evaluateConditionAndEffectSignatures(
    effect: EdgeEffect,
    condition: EdgeCondition,
    ruleBuilder: RuleConditionBuilder
) {
    val signatures = mutableListOf<MethodSignature>()
    val negatedSignatures = mutableListOf<MethodSignature>()

    effect.assignMetaVar.values.flatten().forEach {
        check(!it.negated) { "Negated effect" }
        signatures.add(it.predicate.signature)
    }

    condition.readMetaVar.values.flatten().forEach {
        if (it.negated) {
            negatedSignatures.add(it.predicate.signature)
        } else {
            signatures.add(it.predicate.signature)
        }
    }

    condition.other.forEach {
        if (it.negated) {
            negatedSignatures.add(it.predicate.signature)
        } else {
            signatures.add(it.predicate.signature)
        }
    }

    evaluateFormulaSignature(signatures, negatedSignatures, ruleBuilder)
}

private fun TaintRuleGenerationCtx.evaluateFormulaSignature(
    signatures: List<MethodSignature>,
    negatedSignatures: List<MethodSignature>,
    builder: RuleConditionBuilder,
) {
    val signature = signatures.first()

    if (signatures.any { it != signature }) {
        TODO("Signature mismatch")
    }

    if (negatedSignatures.any { !signature.isCompatibleWith(it, metaVarInfo) }) {
        TODO("Negative signature mismatch")
    }

    if (signature.isOpentaintAnyValueGenerator()) {
        TODO("Eliminate opentaint generated method")
    }

    val methodName = signature.methodName.name
    builder.methodName = evaluateFormulaSignatureMethodName(methodName, builder)

    val classSignatureMatcher = typeMatcher(signature.enclosingClassName.name)
    when (classSignatureMatcher) {
        null -> {}
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
}

private fun MethodSignature.isCompatibleWith(
    other: MethodSignature,
    metaVarInfo: ResolvedMetaVarInfo
): Boolean = this.unify(other, metaVarInfo) == this

private fun TaintRuleGenerationCtx.evaluateFormulaSignatureMethodName(
    methodName: SignatureName,
    builder: RuleConditionBuilder,
): SerializedNameMatcher {
    return when (methodName) {
        SignatureName.AnyName -> anyName()
        is SignatureName.Concrete -> SerializedNameMatcher.Simple(methodName.name)
        is SignatureName.MetaVar -> {
            val constraint = metaVarInfo.metaVarConstraints[methodName.metaVar] ?: return anyName()
            val concrete = mutableListOf<String>()
            val matches = mutableListOf<String>()
            for (c in constraint.constraints) {
                when (c) {
                    is MetaVarConstraint.Concrete -> {
                        concrete.add(c.value)
                    }
                    is MetaVarConstraint.RegExp -> {
                        matches.add(c.regex)
                    }
                }
            }

            check(concrete.size <= 1) { "Multiple concrete names" }

            matches.mapTo(builder.conditions) {
                SerializedCondition.MethodNameMatches(it)
            }

            concrete.firstOrNull()?.let { SerializedNameMatcher.Simple(it) } ?: anyName()
        }
    }
}

private fun TaintRuleGenerationCtx.evaluateEdgePredicateConstraint(
    constraint: MethodConstraint?,
    negated: Boolean,
    state: State,
    builder: RuleConditionBuilder,
    additionalFieldRules: MutableList<SerializedFieldRule>,
) {
    if (!negated) {
        evaluateMethodConstraints(constraint, state, builder.conditions, additionalFieldRules)
    } else {
        val negatedConditions = hashSetOf<SerializedCondition>()
        evaluateMethodConstraints(constraint, state, negatedConditions, additionalFieldRules)
        builder.conditions += SerializedCondition.not(SerializedCondition.and(negatedConditions.toList()))
    }
}

private fun TaintRuleGenerationCtx.evaluateMethodConstraints(
    constraint: MethodConstraint?,
    state: State,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
) {
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
        is ParamConstraint -> evaluateParamConstraints(constraint, state, conditions, additionalFieldRules)
    }
}

private fun findMetaVarPosition(
    constraint: MethodConstraint?,
    varPositions: MutableMap<String, RegisterVarPosition>
) {
    if (constraint !is ParamConstraint) return
    findMetaVarPosition(constraint, varPositions)
}

private fun TaintRuleGenerationCtx.typeMatcher(typeName: TypeNamePattern): SerializedNameMatcher? {
    return when (typeName) {
        is TypeNamePattern.ClassName -> SerializedNameMatcher.ClassPattern(
            `package` = anyName(),
            `class` = SerializedNameMatcher.Simple(typeName.name)
        )

        is TypeNamePattern.FullyQualified -> {
            SerializedNameMatcher.Simple(typeName.name)
        }

        is TypeNamePattern.PrimitiveName -> {
            SerializedNameMatcher.Simple(typeName.name)
        }

        TypeNamePattern.AnyType -> return null

        is TypeNamePattern.MetaVar -> {
            val constraint = metaVarInfo.metaVarConstraints[typeName.metaVar] ?: return null
            val value = constraint.constraints.singleOrNull()
                ?: TODO("Typename metavar multiple constraints")

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

private fun String.patternCanMatchDot(): Boolean =
    '.' in this || '-' in this // [A-Z]

private fun TaintRuleGenerationCtx.signatureModifierConstraint(
    modifier: SignatureModifier
): SerializedCondition.AnnotationConstraint {
    val type = typeMatcher(modifier.type) ?: anyName()

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
            val constraints = metaVarInfo.metaVarConstraints[v.metaVar]?.constraints.orEmpty()
            constraints.map { c ->
                when (c) {
                    is MetaVarConstraint.Concrete -> AnnotationParamStringMatcher(v.paramName, c.value)
                    is MetaVarConstraint.RegExp -> AnnotationParamPatternMatcher(v.paramName, c.regex)
                }
            }
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
    state: State,
    conditions: MutableSet<SerializedCondition>,
    additionalFieldRules: MutableList<SerializedFieldRule>,
) {
    val position = param.position.toSerializedPosition()
    conditions += evaluateParamCondition(position, param.condition, state, additionalFieldRules)
}

private fun findMetaVarPosition(
    param: ParamConstraint,
    varPositions: MutableMap<String, RegisterVarPosition>
) {
    val position = param.position.toSerializedPosition()
    findMetaVarPosition(position, param.condition, varPositions)
}

private fun findMetaVarPosition(
    position: PositionBase,
    condition: ParamCondition.Atom,
    varPositions: MutableMap<String, RegisterVarPosition>
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
    state: State,
    additionalFieldRules: MutableList<SerializedFieldRule>,
): SerializedCondition {
    when (condition) {
        is IsMetavar -> {
            val varValue = state.register.assignedVars[condition.metavar]
            if (varValue == null) {
                val constraints = metaVarInfo.metaVarConstraints[condition.metavar]
                if (constraints != null) {
                    // todo: semantic metavar constraint
                    logger.warn { "Rule $uniqueRuleId: metavar ${condition.metavar} constraint ignored" }
                }
                return SerializedCondition.True
            }

            val mark = markName(condition.metavar, varValue)
            return SerializedCondition.ContainsMark(
                mark, PositionBaseWithModifiers.BaseOnly(position)
            )
        }

        is ParamCondition.TypeIs -> {
            val typeNameMatcher = typeMatcher(condition.typeName)
                ?: return SerializedCondition.True

            return SerializedCondition.IsType(typeNameMatcher, position)
        }

        is ParamCondition.SpecificStaticFieldValue -> {
            val enclosingClassMatcher = typeMatcher(condition.fieldClass) ?: anyName()
            val mark = markName("__STATIC_FIELD_VALUE__${condition.fieldName}", condition.hashCode().absoluteValue)

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

        is ParamCondition.StringValueMetaVar -> {
            val constraints = metaVarInfo.metaVarConstraints[condition.metaVar]?.constraints.orEmpty()
            val conditions = constraints.map { c ->
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

            return SerializedCondition.and(conditions)
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
    Edge.AnalysisEnd -> StateRegister(emptyMap())
}

private fun simplifyEdgeCondition(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    edge: AutomataEdgeType
) = when (edge) {
    is AutomataEdgeType.MethodCall -> simplifyMethodFormula(formulaManager, edge.formula, metaVarInfo).map {
        val (effect, cond) = edgeEffectAndCondition(it, formulaManager)
        Edge.MethodCall(cond, effect)
    }

    is AutomataEdgeType.MethodEnter -> simplifyMethodFormula(formulaManager, edge.formula, metaVarInfo).map {
        val (effect, cond) = edgeEffectAndCondition(it, formulaManager)
        Edge.MethodEnter(cond, effect)
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

    val metaVarWrite = hashMapOf<String, MutableList<MethodPredicate>>()
    val metaVarRead = hashMapOf<String, MutableList<MethodPredicate>>()
    val other = mutableListOf<MethodPredicate>()

    for (mp in predicates) {
        val constraint = mp.predicate.constraint
        val metaVar = ((constraint as? ParamConstraint)?.condition as? IsMetavar)?.metavar

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

    return StateRegister(newStateVars)
}

private fun anyName() = SerializedNameMatcher.Pattern(".*")

private fun SerializedFunctionNameMatcher.matchAnything(): Boolean =
    `class` == anyName() && `package` == anyName() && name == anyName()

private fun SerializedFunctionNameMatcher.isOpentaintReturnValue(): Boolean {
    val name = this.name as? SerializedNameMatcher.Simple ?: return false
    return name.value == opentaintReturnValueMethod
}

val logger = object : KLogging() {}.logger
