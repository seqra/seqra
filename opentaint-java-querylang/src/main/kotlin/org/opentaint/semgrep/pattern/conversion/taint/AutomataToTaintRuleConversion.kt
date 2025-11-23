package org.opentaint.org.opentaint.semgrep.pattern.conversion.taint

import mu.KLogging
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantCmpType
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantType
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.ConstantValue
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule.SinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSignatureMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.util.PersistentBitSet
import org.opentaint.dataflow.util.PersistentBitSet.Companion.emptyPersistentBitSet
import org.opentaint.dataflow.util.contains
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.org.opentaint.semgrep.pattern.RuleWithFocusMetaVars
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SpecificBoolValue
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SpecificStringValue
import org.opentaint.org.opentaint.semgrep.pattern.conversion.TypeNamePattern
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.Cube
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaCubeCompact
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodName
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.PredicateId
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.StateRegister
import org.opentaint.org.opentaint.semgrep.pattern.conversion.opentaintAnyValueGeneratorMethodName
import java.util.IdentityHashMap

fun convertToTaintRules(
    rule: SemgrepRule<RuleWithFocusMetaVars<SemgrepRuleAutomata>>,
    ruleId: String,
    meta: SinkMetaData,
): TaintRuleFromSemgrep = when (rule) {
    is SemgrepMatchingRule -> convertMatchingRuleToTaintRules(rule, ruleId, meta)
    is SemgrepTaintRule -> convertTaintRuleToTaintRules(rule, ruleId, meta)
}

private fun convertMatchingRuleToTaintRules(
    rule: SemgrepMatchingRule<RuleWithFocusMetaVars<SemgrepRuleAutomata>>,
    ruleId: String,
    meta: SinkMetaData,
): TaintRuleFromSemgrep {
    val ruleGroups = rule.rules.mapIndexed { idx, r ->
        val automataId = "$ruleId#$idx"
        val rules = convertAutomataToTaintRules(r.rule, automataId, ruleId, meta)
        TaintRuleFromSemgrep.TaintRuleGroup(rules)
    }
    return TaintRuleFromSemgrep(ruleId, ruleGroups)
}

private fun convertTaintRuleToTaintRules(
    rule: SemgrepTaintRule<RuleWithFocusMetaVars<SemgrepRuleAutomata>>,
    ruleId: String,
    meta: SinkMetaData,
): TaintRuleFromSemgrep {
    val taintMarkName = "$ruleId#taint"
    val generatedRules = mutableListOf<SerializedRule>()

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
    rule: RuleWithFocusMetaVars<SemgrepRuleAutomata>,
    fromMetaVar: String,
    toMetaVar: String
): Pair<TaintRuleGenerationCtx, Int> {
    val automata = rule.rule
    check(automata.isDeterministic) { "NFA not supported" }

    val taintAutomata = createAutomata(automata.formulaManager, automata.initialNode)

    val initialStateId = taintAutomata.stateId(taintAutomata.initial)
    val initialRegister = StateRegister(mapOf(fromMetaVar to initialStateId))
    val newInitial = State(taintAutomata.initial.node, initialRegister)
    val taintAutomataWithState = taintAutomata.replaceInitialState(newInitial)

    val taintEdges = generateAutomataWithTaintEdges(
        taintAutomataWithState, automataId = "$ruleId#pass_$passIdx", acceptStateVars = setOf(toMetaVar)
    )

    return taintEdges to initialStateId
}

private fun convertTaintSinkRule(
    ruleId: String,
    sinkIdx: Int,
    rule: RuleWithFocusMetaVars<SemgrepRuleAutomata>
): Triple<TaintRuleGenerationCtx, Set<String>, Int> {
    val automata = rule.rule
    check(automata.isDeterministic) { "NFA not supported" }

    val taintAutomata = createAutomata(automata.formulaManager, automata.initialNode)
    val (sinkAutomata, stateMetaVars) = ensureSinkStateVars(taintAutomata, rule.focusMetaVars)

    val initialStateId = sinkAutomata.stateId(sinkAutomata.initial)
    val initialRegister = StateRegister(stateMetaVars.associateWith { initialStateId })
    val newInitial = State(sinkAutomata.initial.node, initialRegister)
    val sinkAutomataWithState = sinkAutomata.replaceInitialState(newInitial)

    val taintEdges = generateAutomataWithTaintEdges(
        sinkAutomataWithState, automataId = "$ruleId#sink_$sinkIdx", acceptStateVars = emptySet()
    )

    return Triple(taintEdges, stateMetaVars, initialStateId)
}

private fun convertTaintSourceRule(
    ruleId: String,
    sourceIdx: Int,
    rule: RuleWithFocusMetaVars<SemgrepRuleAutomata>
): Pair<TaintRuleGenerationCtx, Set<String>> {
    val automata = rule.rule
    check(automata.isDeterministic) { "NFA not supported" }

    val taintAutomata = createAutomata(automata.formulaManager, automata.initialNode)
    val (sourceAutomata, stateMetaVars) = ensureSourceStateVars(taintAutomata, rule.focusMetaVars)

    val taintEdges = generateAutomataWithTaintEdges(
        sourceAutomata, automataId = "$ruleId#source_$sourceIdx", acceptStateVars = stateMetaVars
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
                val condition = ParamConstraint(Position.Argument(index = null), IsMetavar(freshVar))
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
    automata: SemgrepRuleAutomata,
    automataId: String,
    id: String, meta: SinkMetaData,
): List<SerializedRule> {
    check(automata.isDeterministic) { "NFA not supported" }

    val taintAutomata = createAutomata(automata.formulaManager, automata.initialNode)
    val ctx = generateAutomataWithTaintEdges(taintAutomata, automataId, acceptStateVars = emptySet())
    return ctx.generateTaintSinkRules(id, meta)
}

private fun generateAutomataWithTaintEdges(
    automata: TaintRegisterStateAutomata,
    automataId: String,
    acceptStateVars: Set<String>
): TaintRuleGenerationCtx {
    val automataWithoutValueGen = eliminateAnyValueGenerator(automata)
    val simulated = simulateAutomata(automataWithoutValueGen)
    val cleaned = removeUnreachabeStates(simulated)
    val liveAutomata = eliminateDeadVariables(cleaned, acceptStateVars)
    val automataWithoutEnd = tryRemoveEndEdge(liveAutomata)
    return generateTaintEdges(automataWithoutEnd, automataId)
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

open class TaintRuleGenerationCtx(
    val uniqueRuleId: String,
    val automata: TaintRegisterStateAutomata,
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
    globalStateAssignStates: Set<State>,
    edges: List<TaintRuleEdge>,
    finalEdges: List<TaintRuleEdge>
) : TaintRuleGenerationCtx(uniqueRuleId, automata, globalStateAssignStates, edges, finalEdges) {
    constructor(
        initialStateVars: Set<String>, initialVarValue: Int, taintMarkName: String,
        ctx: TaintRuleGenerationCtx
    ) : this(
        initialStateVars, initialVarValue, taintMarkName,
        ctx.uniqueRuleId, ctx.automata, ctx.globalStateAssignStates, ctx.edges, ctx.finalEdges
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

private fun createAutomata(formulaManager: MethodFormulaManager, initialNode: AutomataNode): TaintRegisterStateAutomata {
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
            for (simplifiedEdge in simplifyEdgeCondition(formulaManager, edgeCondition)) {
                val nextState = State(dstNode, emptyRegister)
                successors.getOrPut(state, ::hashSetOf).add(simplifiedEdge to nextState)
                unprocessed.add(nextState)
            }
        }
    }

    check(finalStates.isNotEmpty()) { "Automata has no accept state" }

    return TaintRegisterStateAutomata(formulaManager, initialState, finalStates, successors, nodeIds)
}

private fun simulateAutomata(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val processedStates = hashSetOf<State>()
    val unprocessed = mutableListOf(automata.initial to automata.initial)

    val finalStates = hashSetOf<State>()
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    while (unprocessed.isNotEmpty()) {
        val (state, originalState) = unprocessed.removeLast()
        if (!processedStates.add(state)) continue

        if (originalState in automata.final) {
            finalStates.add(state)
            continue
        }

        for ((simplifiedEdge, dstState) in automata.successors[originalState].orEmpty()) {
            // todo: hack to avoid positive back edges
            if (dstState == originalState) continue

            val dstStateId = automata.stateId(dstState)
            val dstStateRegister = simulateCondition(simplifiedEdge, dstStateId, state.register)

            val nextState = dstState.copy(register = dstStateRegister)
            successors.getOrPut(state, ::hashSetOf).add(simplifiedEdge to nextState)
            unprocessed.add(nextState to dstState)
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

    check(automata.initial in reachableStates) { "Initial state is unreachable" }

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

    val unprocessed = mutableListOf(StateWithValueGeneratorContext(automata.initial, ValueGeneratorCtx(emptyMap())))
    val visited = hashSetOf<StateWithValueGeneratorContext>()
    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!visited.add(state)) continue

        val stateSuccessors = successors.getOrPut(state.state, ::hashSetOf)
        eliminateAnyValueGeneratorEdges(state, automata.successors, stateSuccessors, unprocessed)
    }

    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial, automata.final, successors, automata.nodeIndex
    )
}

private fun eliminateAnyValueGeneratorEdges(
    state: StateWithValueGeneratorContext,
    successors: Map<State, Set<Pair<Edge, State>>>,
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
                if (nextState == state.state) continue

                eliminateAnyValueGeneratorEdges(
                    StateWithValueGeneratorContext(nextState, elimResult.ctx),
                    successors, resultStateSuccessors, unprocessed
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
            if (pred.predicate.signature.methodName.name == opentaintAnyValueGeneratorMethodName) {
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
    predicate.signature.methodName.name == opentaintAnyValueGeneratorMethodName

private fun generateTaintEdges(
    automata: TaintRegisterStateAutomata,
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

    return TaintRuleGenerationCtx(uniqueRuleId, automata, globalStateAssignStates, taintRuleEdges, finalEdges)
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
    val signature: SerializedSignatureMatcher?,
    val condition: SerializedCondition,
)

private data class EvaluatedEdgeCondition(
    val ruleCondition: RuleCondition,
    val accessedVarPosition: Map<String, RegisterVarPosition>
)

private fun TaintRuleGenerationCtx.generateTaintSinkRules(id: String, meta: SinkMetaData): List<SerializedRule> =
    generateTaintRules { ruleEdge, _, function, sig, cond ->
        if (function.matchAnything() && cond is SerializedCondition.True) {
            logger.warn { "Rule $id match anything" }
            return@generateTaintRules emptyList()
        }

        val rule = when (ruleEdge.edge) {
            is Edge.MethodEnter -> SerializedRule.MethodEntrySink(
                function, sig, overrides = false, cond, id, meta = meta
            )

            is Edge.MethodCall -> SerializedRule.Sink(
                function, sig, overrides = false, cond, id, meta = meta
            )

            Edge.AnalysisEnd -> SerializedRule.MethodExitSink(
                function, sig, overrides = false, cond, id, meta = meta
            )
        }
        listOf(rule)
    }

private fun TaintRuleGenerationCtx.generateTaintSourceRules(
    stateVars: Set<String>, taintMarkName: String
): List<SerializedRule> = generateTaintRules { ruleEdge, condition, function, sig, cond ->
    val actions = stateVars.flatMapTo(mutableListOf()) { varName ->
        val varPosition = condition.accessedVarPosition[varName] ?: return@flatMapTo emptyList()
        varPosition.positions.map {
            SerializedTaintAssignAction(taintMarkName, pos = PositionBaseWithModifiers.BaseOnly(it))
        }
    }

    if (actions.isEmpty()) return@generateTaintRules emptyList()

    val rule = when (ruleEdge.edge) {
        is Edge.MethodCall -> SerializedRule.Source(
            function, sig, overrides = false, cond, actions
        )

        is Edge.MethodEnter -> SerializedRule.EntryPoint(
            function, sig, overrides = false, cond, actions
        )

        Edge.AnalysisEnd -> TODO()
    }

    listOf(rule)
}

private fun SinkRuleGenerationCtx.generateTaintPassRules(
    fromVar: String, toVar: String,
    taintMarkName: String
): List<SerializedRule> {
    // todo: generate taint pass when possible
    return generateTaintSourceRules(setOf(toVar), taintMarkName)
}

private fun TaintRuleGenerationCtx.generateTaintRules(
    generateAcceptStateRules: (
        TaintRuleEdge,
        EvaluatedEdgeCondition,
        SerializedFunctionNameMatcher,
        SerializedSignatureMatcher?,
        SerializedCondition
    ) -> List<SerializedRule>
): List<SerializedRule> {
    val rules = mutableListOf<SerializedRule>()

    val evaluatedConditions = hashMapOf<State, MutableMap<Edge, EvaluatedEdgeCondition>>()

    fun evaluate(edge: Edge, state: State): EvaluatedEdgeCondition =
        evaluatedConditions
            .getOrPut(state, ::hashMapOf)
            .getOrPut(edge) { evaluateEdgeCondition(edge, state) }

    for (ruleEdge in finalEdges) {
        val edge = ruleEdge.edge
        val state = ruleEdge.stateFrom

        val condition = evaluate(edge, state).addGlobalStateCheck(this, ruleEdge.checkGlobalState, state)

        if (ruleEdge.stateTo.node.accept) {
            rules += generateRules(condition.ruleCondition) { function, sig, cond ->
                generateAcceptStateRules(ruleEdge, condition, function, sig, cond)
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

            rules += generateRules(condition.ruleCondition) { function, sig, cond ->
                SerializedRule.Cleaner(function, sig, overrides = false, cond, actions)
            }
        }
    }

    for (ruleEdge in edges) {
        val edge = ruleEdge.edge
        val state = ruleEdge.stateFrom

        val condition = evaluate(edge, state).addGlobalStateCheck(this, ruleEdge.checkGlobalState, state)

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
            rules += generateRules(condition.ruleCondition) { function, sig, cond ->
                when (edge) {
                    is Edge.MethodCall -> SerializedRule.Source(
                        function, sig, overrides = false, cond, actions
                    )

                    is Edge.MethodEnter -> SerializedRule.EntryPoint(
                        function, sig, overrides = false, cond, actions
                    )

                    Edge.AnalysisEnd -> TODO()
                }
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
    body: (SerializedFunctionNameMatcher, SerializedSignatureMatcher?, SerializedCondition) -> T
): T {
    val functionMatcher = SerializedFunctionNameMatcher.Complex(
        condition.enclosingClassPackage,
        condition.enclosingClassName,
        condition.name
    )

    return body(functionMatcher, condition.signature, condition.condition)
}

private fun TaintRuleGenerationCtx.evaluateEdgeCondition(
    edge: Edge,
    state: State
): EvaluatedEdgeCondition = when (edge) {
    is Edge.MethodCall -> evaluateMethodConditionAndEffect(edge.condition, edge.effect, state)
    is Edge.MethodEnter -> evaluateMethodConditionAndEffect(edge.condition, edge.effect, state)
    Edge.AnalysisEnd -> EvaluatedEdgeCondition(RuleConditionBuilder().build(), emptyMap())
}

private class RuleConditionBuilder {
    var enclosingClassPackage: SerializedNameMatcher? = null
    var enclosingClassName: SerializedNameMatcher? = null
    var methodName: SerializedNameMatcher? = null

    var signature: SerializedSignatureMatcher? = null
    val conditions = hashSetOf<SerializedCondition>()

    fun build(): RuleCondition = RuleCondition(
        enclosingClassPackage ?: anyName(),
        enclosingClassName ?: anyName(),
        methodName ?: anyName(),
        signature,
        SerializedCondition.and(conditions.toList())
    )
}

private fun TaintRuleGenerationCtx.evaluateMethodConditionAndEffect(
    condition: EdgeCondition,
    effect: EdgeEffect,
    state: State
): EvaluatedEdgeCondition {
    val ruleBuilder = RuleConditionBuilder()

    evaluateConditionAndEffectSignatures(effect, condition, ruleBuilder)

    condition.readMetaVar.values.flatten().forEach {
        evaluateEdgePredicateConstraint(it.predicate.constraint, it.negated, state, ruleBuilder)
    }

    condition.other.forEach {
        evaluateEdgePredicateConstraint(it.predicate.constraint, it.negated, state, ruleBuilder)
    }

    val varPositions = hashMapOf<String, RegisterVarPosition>()
    effect.assignMetaVar.values.flatten().forEach {
        findMetaVarPosition(it.predicate.constraint, varPositions)
    }

    return EvaluatedEdgeCondition(ruleBuilder.build(), varPositions)
}

private fun evaluateConditionAndEffectSignatures(
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

private fun evaluateFormulaSignature(
    signatures: List<MethodSignature>,
    negatedSignatures: List<MethodSignature>,
    builder: RuleConditionBuilder
) {
    val signature = signatures.first()

    if (signatures.any { it != signature }) {
        TODO("Signature mismatch")
    }

    if (negatedSignatures.any { it != signature }) {
        TODO("Negative signature mismatch")
    }

    if (signature.methodName.name == opentaintAnyValueGeneratorMethodName) {
        // note: propagate all constraints to the next variable usage
        TODO("Eliminate opentaint any value generator")
    }

    with(builder) {
        methodName = signature.methodName.name
            ?.let { SerializedNameMatcher.Simple(it) } ?: anyName()

        when (val name = signature.enclosingClassName.name) {
            null -> {
                // ignore, any name by default
            }

            is TypeNamePattern.ClassName -> {
                enclosingClassName = SerializedNameMatcher.Simple(name.name)
            }

            is TypeNamePattern.FullyQualified -> {
                val parts = name.name.split(".")
                val packageName = parts.dropLast(1).joinToString(separator = ".")
                enclosingClassPackage = SerializedNameMatcher.Simple(packageName)
                enclosingClassName = SerializedNameMatcher.Simple(parts.last())
            }
        }
    }
}

private fun TaintRuleGenerationCtx.evaluateEdgePredicateConstraint(
    constraint: MethodConstraint?,
    negated: Boolean,
    state: State,
    builder: RuleConditionBuilder
) {
    if (!negated) {
        evaluateMethodConstraints(constraint, state, builder.conditions)
    } else {
        val negatedConditions = hashSetOf<SerializedCondition>()
        evaluateMethodConstraints(constraint, state, negatedConditions)
        builder.conditions += SerializedCondition.not(SerializedCondition.and(negatedConditions.toList()))
    }
}

private fun TaintRuleGenerationCtx.evaluateMethodConstraints(
    constraint: MethodConstraint?,
    state: State,
    conditions: MutableSet<SerializedCondition>
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
        is ParamConstraint -> evaluateParamConstraints(constraint, state, conditions)
    }
}

private fun findMetaVarPosition(
    constraint: MethodConstraint?,
    varPositions: MutableMap<String, RegisterVarPosition>
) {
    if (constraint !is ParamConstraint) return
    findMetaVarPosition(constraint, varPositions)
}

private fun signatureModifierConstraint(
    modifier: SemgrepPatternAction.SignatureModifier
): SerializedCondition.AnnotationConstraint {
    val type = when (modifier.type) {
        is TypeNamePattern.ClassName -> SerializedNameMatcher.ClassPattern(
            `package` = anyName(),
            `class` = SerializedNameMatcher.Simple(modifier.type.name)
        )

        is TypeNamePattern.FullyQualified -> {
            SerializedNameMatcher.Simple(modifier.type.name)
        }
    }

    val params = when (modifier.value) {
        SemgrepPatternAction.SignatureModifierValue.AnyValue -> null
        SemgrepPatternAction.SignatureModifierValue.NoValue -> emptyList()
        is SemgrepPatternAction.SignatureModifierValue.StringValue -> listOf(
            SerializedCondition.AnnotationParamStringMatcher(modifier.value.paramName, modifier.value.value)
        )

        is SemgrepPatternAction.SignatureModifierValue.StringPattern -> listOf(
            SerializedCondition.AnnotationParamPatternMatcher(modifier.value.paramName, modifier.value.pattern)
        )

        // todo: annotation metavars ignored
        is SemgrepPatternAction.SignatureModifierValue.MetaVar -> null
    }

    return SerializedCondition.AnnotationConstraint(type, params)
}

private fun TaintRuleGenerationCtx.evaluateParamConstraints(
    param: ParamConstraint,
    state: State,
    conditions: MutableSet<SerializedCondition>
) {
    val position = when (val pos = param.position) {
        is Position.Argument -> PositionBase.Argument(pos.index)
        is Position.Object -> PositionBase.This
        is Position.Result -> PositionBase.Result
    }

    conditions += evaluateParamCondition(position, param.condition, state)
}

private fun findMetaVarPosition(
    param: ParamConstraint,
    varPositions: MutableMap<String, RegisterVarPosition>
) {
    val position = when (val pos = param.position) {
        is Position.Argument -> PositionBase.Argument(pos.index)
        is Position.Object -> PositionBase.This
        is Position.Result -> PositionBase.Result
    }

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
    state: State
): SerializedCondition {
    when (condition) {
        is IsMetavar -> {
            val varValue = state.register.assignedVars[condition.metavar]
                ?: return SerializedCondition.True

            val mark = markName(condition.metavar, varValue)
            return SerializedCondition.ContainsMark(
                mark, PositionBaseWithModifiers.BaseOnly(position)
            )
        }

        is ParamCondition.TypeIs -> {
            val typeNameMatcher = when (val tn = condition.typeName) {
                is TypeNamePattern.ClassName -> SerializedNameMatcher.ClassPattern(
                    `package` = anyName(),
                    `class` = SerializedNameMatcher.Simple(tn.name)
                )

                is TypeNamePattern.FullyQualified -> SerializedNameMatcher.Simple(tn.name)
            }

            return SerializedCondition.IsType(typeNameMatcher, position)
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

        is ParamCondition.StringMatches -> {
            return SerializedCondition.ConstantMatches(condition.pattern, position)
        }

        is ParamCondition.ParamModifier -> {
            val annotation = signatureModifierConstraint(condition.modifier)
            return SerializedCondition.ParamAnnotated(position, annotation)
        }

        is ParamCondition.TypeMetaVar -> {
            // todo: for now we ignore type meta vars
            return SerializedCondition.True
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

private fun simplifyEdgeCondition(formulaManager: MethodFormulaManager, edge: AutomataEdgeType) = when (edge) {
    is AutomataEdgeType.MethodCall -> simplifyMethodFormula(formulaManager, edge.formula).map {
        val (effect, cond) = edgeEffectAndCondition(it, formulaManager)
        Edge.MethodCall(cond, effect)
    }

    is AutomataEdgeType.MethodEnter -> {
        // todo: method name is metavar
        val (formula, methodNames) = removeMethodNameAtoms(formulaManager, edge.formula)
        simplifyMethodFormula(formulaManager, formula).map {
            val (effect, cond) = edgeEffectAndCondition(it, formulaManager)
            Edge.MethodEnter(cond, effect)
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

private fun removeMethodNameAtoms(
    formulaManager: MethodFormulaManager,
    initialFormula: MethodFormula
): Pair<MethodFormula, Set<MethodName>> {
    val methodNames = hashSetOf<MethodName>()

    fun removeFromPredicate(predicateId: PredicateId): PredicateId {
        val predicate = formulaManager.predicate(predicateId)
        predicate.signature.methodName.let { methodNames += it }
        val signature = predicate.signature.copy(methodName = MethodName.anyMethodName)
        val resultPredicate = predicate.copy(signature = signature)
        return formulaManager.predicateId(resultPredicate)
    }

    fun removeFromFormula(formula: MethodFormula): MethodFormula = when (formula) {
        is MethodFormula.Or -> formulaManager.mkOr(formula.any.map { removeFromFormula(it) })
        is MethodFormula.And -> formulaManager.mkAnd(formula.all.map { removeFromFormula(it) })
        is Cube -> {
            val cube = MethodFormulaCubeCompact()
            formula.cube.positiveLiterals.forEach {
                cube.positiveLiterals.set(removeFromPredicate(it))
            }
            formula.cube.negativeLiterals.forEach {
                cube.negativeLiterals.set(removeFromPredicate(it))
            }

            Cube(cube, formula.negated)
        }

        MethodFormula.True -> formula
        MethodFormula.False -> formula
        is MethodFormula.Literal -> MethodFormula.Literal(removeFromPredicate(formula.predicate), formula.negated)
    }

    val result = removeFromFormula(initialFormula)
    return result to methodNames
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

private val logger = object : KLogging() {}.logger
