package org.opentaint.semgrep.pattern.conversion.taint

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import org.opentaint.dataflow.util.PersistentBitSet
import org.opentaint.dataflow.util.contains
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction
import org.opentaint.semgrep.pattern.conversion.TypeNamePattern
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodEnclosingClassName
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.StateRegister
import java.util.IdentityHashMap

data class TaintRegisterStateAutomataWithStateVars(
    val automata: TaintRegisterStateAutomata,
    val initialStateVars: Set<MetavarAtom>,
    val acceptStateVars: Set<MetavarAtom>
)

fun RuleConversionCtx.generateTaintAutomataEdges(
    automata: TaintRegisterStateAutomataWithStateVars,
    metaVarInfo: ResolvedMetaVarInfo,
): TaintAutomataEdges {
    val cleanAutomata = generateCleanAutomata(automata, metaVarInfo)
    val generatedEdges = generateTaintEdges(cleanAutomata.automata, metaVarInfo)
    val resultAutomata = cleanupAutomata(generatedEdges)
    return resultAutomata
}

private fun RuleConversionCtx.generateCleanAutomata(
    automata: TaintRegisterStateAutomataWithStateVars,
    metaVarInfo: ResolvedMetaVarInfo,
): TaintRegisterStateAutomataWithStateVars {
    val simulated = simulateAutomata(automata.automata, automata.initialStateVars)
    val meaningFullAutomata = removeMeaningLessEdges(simulated)
    val cleaned = removeUnreachableStates(meaningFullAutomata)
    val rewritten = rewriteEdges(cleaned)
    val liveAutomata = eliminateDeadVariables(rewritten, automata.acceptStateVars)
    val cleanAutomata = cleanupAutomata(liveAutomata, metaVarInfo)

    check(!cleanAutomata.containsStateIdClash()) {
        "Automata state ids clash"
    }

    return automata.copy(automata = cleanAutomata)
}

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

private data class SimulationState(
    val original: State,
    val state: State,
    val originalPath: PersistentMap<State, State>
)

private fun RuleConversionCtx.simulateAutomata(
    automata: TaintRegisterStateAutomata,
    initialStateVars: Set<MetavarAtom>,
): TaintRegisterStateAutomata {
    var nextStateId = 0
    val generatedStateIds = hashMapOf<State, MutableMap<State, Int>>()

    fun stateId(state: State, predecessor: State): Int {
        val predecessors = generatedStateIds.getOrPut(state, ::hashMapOf)

        val stateId = nextStateId
        val prev = predecessors.putIfAbsent(predecessor, stateId)
        if (prev != null) return prev
        nextStateId++
        return stateId
    }

    val initialStateId = stateId(automata.initial, automata.initial)
    val initialStateRegister = StateRegister(initialStateVars.associateWith { initialStateId })
    val initialState = automata.initial.copy(register = initialStateRegister)

    val initialSimulationState = SimulationState(
        original = automata.initial, state = initialState,
        originalPath = persistentHashMapOf(automata.initial to initialState)
    )
    val unprocessed = mutableListOf(initialSimulationState)

    val finalAcceptStates = hashSetOf<State>()
    val finalDeadStates = hashSetOf<State>()
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    val unprocessedLoopBackEdges = mutableListOf<Triple<State, Edge, State>>()

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

        for ((simplifiedEdge, originalDstState) in automata.successors[simulationState.original].orEmpty()) {
            val loopStartState = simulationState.originalPath[originalDstState]
            if (loopStartState != null) {
                if (loopStartState.register == state.register) {
                    // loop has no assignments
                    continue
                }

                unprocessedLoopBackEdges += Triple(state, simplifiedEdge, loopStartState)
                continue
            }

            val dstStateId = stateId(originalDstState, state)
            val updatedEdge = rewriteEdgeWrtComplexMetavars(simplifiedEdge, state.register)
            val dstStateRegister = simulateCondition(updatedEdge, dstStateId, state.register)

            val nextState = State(dstStateId, dstStateRegister)
            successors.getOrPut(state, ::hashSetOf).add(simplifiedEdge to nextState)

            val nextPath = simulationState.originalPath.put(originalDstState, nextState)
            val nextSimulationState = SimulationState(originalDstState, nextState, nextPath)
            unprocessed.add(nextSimulationState)
        }
    }

    val result = TaintRegisterStateAutomata(
        automata.formulaManager, initialState,
        finalAcceptStates, finalDeadStates,
        successors,
    )

    val resultWithLoopsResolved = resolveLoopBackEdges(result, unprocessedLoopBackEdges)
    return resultWithLoopsResolved
}

private fun RuleConversionCtx.resolveLoopBackEdges(
    automata: TaintRegisterStateAutomata,
    loopBackEdges: List<Triple<State, Edge, State>>
): TaintRegisterStateAutomata {
    if (loopBackEdges.isEmpty()) return automata

    val acceptReachableFromStates = stateReachesAccept(automata)

    val requiredLoops = mutableListOf<Triple<State, Edge, State>>()

    for (loopEdge in loopBackEdges) {
        val (stateFrom, edge, stateTo) = loopEdge

        if (stateTo !in acceptReachableFromStates) continue

        if (stateFrom !in acceptReachableFromStates) {
            requiredLoops.add(loopEdge)
            continue
        }

        val edgeHasEffect = when (edge) {
            is Edge.AnalysisEnd -> true
            is Edge.EdgeWithEffect -> !edge.effect.hasNoEffect()
        }

        if (edgeHasEffect) {
            requiredLoops.add(loopEdge)
            continue
        }

        if (automata.anyEdgeCanChangeMetaVarPosition(stateTo, stateFrom)) {
            requiredLoops.add(loopEdge)
            continue
        }

        // loop has no effect
    }

    if (requiredLoops.isEmpty()) return automata

    trace.error("Loop var assign", Reason.ERROR)
    return automata
}

private fun TaintRegisterStateAutomata.anyEdgeCanChangeMetaVarPosition(
    startSate: State,
    dstSate: State
): Boolean {
    val allPathsOnLoop = findAllPathsBetweenStates(startSate, dstSate)
    for (path in allPathsOnLoop) {
        // todo: handle complex loops
        if (path.size > 1) {
            return true
        }

        val edge = path.firstOrNull()?.second ?: continue

        val edgeCond = when (edge) {
            is Edge.AnalysisEnd -> continue
            is Edge.EdgeWithCondition -> edge.condition
        }

        val edgeEffect = when (edge) {
            is Edge.EdgeWithEffect -> edge.effect
        }

        for ((metaVar, reads) in edgeCond.readMetaVar) {
            val writes = edgeEffect.assignMetaVar[metaVar] ?: continue

            val readPos = hashSetOf<Position>()
            val writePos = hashSetOf<Position>()

            reads.collectMetaVarPositions(readPos)
            writes.collectMetaVarPositions(writePos)

            if (writePos.size > 1) return true

            val singleWritePos = writePos.firstOrNull() ?: continue
            if (singleWritePos !in readPos) return true
        }
    }
    return false
}

private fun List<MethodPredicate>.collectMetaVarPositions(dst: MutableSet<Position>) =
    forEach { it.collectMetaVarPositions(dst) }

private fun MethodPredicate.collectMetaVarPositions(dst: MutableSet<Position>) {
    val c = predicate.constraint as? ParamConstraint ?: return
    dst.add(c.position)
}

private fun TaintRegisterStateAutomata.findAllPathsBetweenStates(
    startSate: State,
    dstSate: State
): List<List<Triple<State, Edge, State>>> {
    val predecessors = automataPredecessors(this)

    data class SearchState(
        val current: State,
        val path: PersistentList<Triple<State, Edge, State>>
    )

    val result = mutableListOf<List<Triple<State, Edge, State>>>()

    val initial = SearchState(dstSate, persistentListOf())
    val unprocessed = mutableListOf(initial)
    val visited = hashSetOf<SearchState>()

    while (unprocessed.isNotEmpty()) {
        val searchState = unprocessed.removeLast()
        if (!visited.add(searchState)) continue

        if (searchState.current == startSate) {
            result.add(searchState.path)
            continue
        }

        for ((edge, preState) in predecessors[searchState.current].orEmpty()) {
            val next = SearchState(preState, searchState.path.add(Triple(preState, edge, searchState.current)))
            unprocessed.add(next)
        }
    }

    return result
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
        newSuccessors,
    )
}

private fun rewriteEdgeWrtComplexMetavars(edge: Edge, register: StateRegister): Edge {
    return when (edge) {
        is Edge.AnalysisEnd -> edge
        is Edge.MethodCall -> rewriteEdgeWrtComplexMetavars(
            edge.effect,
            edge.condition,
            register
        ) { effect, condition ->
            Edge.MethodCall(condition, effect)
        }
        is Edge.MethodEnter -> rewriteEdgeWrtComplexMetavars(
            edge.effect,
            edge.condition,
            register
        ) { effect, condition ->
            Edge.MethodEnter(condition, effect)
        }

        is Edge.MethodExit -> rewriteEdgeWrtComplexMetavars(
            edge.effect,
            edge.condition,
            register
        ) { effect, condition ->
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
        is ParamCondition.StringValueMetaVar -> ParamCondition.StringValueMetaVar(
            replace(condition.metaVar) ?: return null
        )
        else -> return this
    }

    return ParamConstraint(position, newCondition)
}

private fun RuleConversionCtx.removeMeaningLessEdges(
    automata: TaintRegisterStateAutomata
): TaintRegisterStateAutomata {
    val removedEdgesToAccept = mutableListOf<Edge>()
    val removedEdgesToUnreachable = mutableListOf<Edge>()
    val reachableStates = stateReachesAccept(automata)

    val successors = automata.successors.mapValues { (srcState, edges) ->
        val resultEdges = hashSetOf<Pair<Edge, State>>()
        for ((edge, dstState) in edges) {
            val removedEdges = if (dstState in reachableStates) removedEdgesToAccept else removedEdgesToUnreachable
            val positiveEdge = edge.ensurePositiveCondition(removedEdges)
            if (positiveEdge == null) {
                check(srcState.register == dstState.register) { "State register modified with non-positive edge" }
                continue
            }

            resultEdges.add(positiveEdge to dstState)
        }
        resultEdges
    }

    if (removedEdgesToAccept.isNotEmpty()) {
        trace.error("Edges without positive predicate: ${removedEdgesToAccept.size}", Reason.WARNING)
    }

    return automata.copy(successors = successors)
}

private fun Edge.ensurePositiveCondition(removedEdges: MutableList<Edge>): Edge? = when (this) {
    is Edge.AnalysisEnd -> this
    is Edge.MethodCall -> condition.ensurePositiveCondition(removedEdges, this)?.let { copy(condition = it) }
    is Edge.MethodEnter -> condition.ensurePositiveCondition(removedEdges, this)?.let { copy(condition = it) }
    is Edge.MethodExit -> condition.ensurePositiveCondition(removedEdges, this)?.let { copy(condition = it) }
}

private fun EdgeCondition.ensurePositiveCondition(removedEdges: MutableList<Edge>, edge: Edge): EdgeCondition? {
    if (containsPositivePredicate()) return this

    if (edge is Edge.MethodEnter) {
        // todo: remove this tricky hack
        val positivePredicate = Predicate(anyMethodSignature(), constraint = null)
        val otherPredicates = other + MethodPredicate(positivePredicate, negated = false)
        return copy(other = otherPredicates)
    }

    removedEdges.add(edge)

    return null
}

private fun removeUnreachableStates(
    automata: TaintRegisterStateAutomata
): TaintRegisterStateAutomata {
    val reachableStates = stateReachesAccept(automata)

    check(automata.initial in reachableStates) {
        "Initial state is unreachable"
    }

    var cleanerStateReachable = false
    val maxStateId = automata.allStates().maxOfOrNull { it.id } ?: 0
    val cleanerState = State(maxStateId + 1, StateRegister(emptyMap()))
    val reachableSuccessors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()

    val unprocessed = mutableListOf<State>()
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
            reachableSuccessors,
        )
    }

    val finalDeadNodes = automata.finalDeadStates + cleanerState
    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial,
        automata.finalAcceptStates, finalDeadNodes,
        reachableSuccessors,
    )
}

private fun stateReachesAccept(automata: TaintRegisterStateAutomata): Set<State> {
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
    return reachableStates
}

private fun eliminateDeadVariables(
    automata: TaintRegisterStateAutomata,
    acceptStateLiveVars: Set<MetavarAtom>
): TaintRegisterStateAutomata {
    // TODO: do we need to specially handle complex variables here?
    val predecessors = automataPredecessors(automata)

    val variableIdx = hashMapOf<MetavarAtom, Int>()
    val stateLiveVars = IdentityHashMap<State, PersistentBitSet>()

    val unprocessed = mutableListOf<Pair<State, PersistentBitSet>>()

    for (state in automata.finalDeadStates) {
        unprocessed.add(state to PersistentBitSet.emptyPersistentBitSet())
    }

    for (state in automata.finalAcceptStates) {
        val liveVarIndices = acceptStateLiveVars.toBitSet {
            variableIdx.getOrPut(it) { variableIdx.size }
        }
        val liveVarSet = PersistentBitSet.emptyPersistentBitSet().persistentAddAll(liveVarIndices)
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
    )
}

private fun cleanupAutomata(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
): TaintRegisterStateAutomata {
    val withoutResultReassign = removeResultReassign(automata)
    val withoutRedundantEnd = removeEndEdge(withoutResultReassign)
    val withAcceptFixed = removeTransitiveAccept(withoutRedundantEnd)
    val withoutDummyEntry = tryRemoveDummyMethodEntry(withAcceptFixed, metaVarInfo)
    val withoutDummyCleaners = removeDummyCleaner(withoutDummyEntry)
    val withEnterExitFix = fixEntryExitSequence(withoutDummyCleaners)
    return withEnterExitFix
}

private fun removeResultReassign(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val newSuccessors = automata.successors.mapValues { (_, edges) ->
        edges.mapTo(mutableSetOf()) {
            val newEdge = removeResultReassign(it.first)
            newEdge to it.second
        }
    }
    return automata.copy(successors = newSuccessors)
}

private fun removeResultReassign(edge: Edge): Edge {
    if (edge !is Edge.EdgeWithCondition) return edge

    val reassingedMetaVars = edge.condition.readMetaVar.filter { (_, preds) ->
        preds.size > 1 && preds.any { it.findParamConstraint()?.position is Position.Result }
    }

    if (reassingedMetaVars.isEmpty()) return edge

    val fixedMetaVarCondition = edge.condition.readMetaVar.toMutableMap()
    for ((mv, preds) in reassingedMetaVars) {
        fixedMetaVarCondition[mv] = preds.filter { it.findParamConstraint()?.position !is Position.Result }
    }

    val fixedCondition = edge.condition.copy(readMetaVar = fixedMetaVarCondition)
    return when (edge) {
        is Edge.MethodCall -> edge.copy(condition = fixedCondition)
        is Edge.MethodEnter -> edge.copy(condition = fixedCondition)
        is Edge.MethodExit -> edge.copy(condition = fixedCondition)
    }
}

private fun fixEntryExitSequence(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val replacement = mutableListOf<Pair<Pair<Edge, State>, List<Pair<Edge, State>>>>()

    outer@ for ((enterEdge, initialSucc) in automata.successors[automata.initial].orEmpty()) {
        if (enterEdge !is Edge.MethodEnter) continue

        if (initialSucc.register != automata.initial.register) continue
        if (enterEdge.condition.readMetaVar.isNotEmpty()) continue
        if (!enterEdge.effect.hasNoEffect()) continue

        if (initialSucc in automata.finalAcceptStates || initialSucc in automata.finalDeadStates) {
            continue
        }

        val nextSuccessors = automata.successors[initialSucc] ?: continue

        val exits = nextSuccessors.map { it.first }.filterIsInstance<Edge.MethodExit>()
        if (exits.isEmpty() || exits.size != nextSuccessors.size) continue

        val enterEdgeReplacement = nextSuccessors.map { (edge, dst) ->
            edge as Edge.MethodExit

            val positiveSig = enterEdge.condition.other.firstOrNull { !it.negated }?.predicate?.signature
            val cond = edge.condition.combineWitEnterCondition(enterEdge.condition, positiveSig)
            val effect = edge.effect.combineWitEnterCondition(positiveSig)
            val modifiedExit = Edge.MethodExit(cond, effect)

            modifiedExit to dst
        }

        replacement.add((enterEdge to initialSucc) to enterEdgeReplacement)
    }

    if (replacement.isEmpty()) return automata

    val successors = automata.successors.toMutableMap()
    val mutableInitial = successors[automata.initial].orEmpty().toMutableSet()
    replacement.forEach {
        mutableInitial.remove(it.first)
        successors.remove(it.first.second)
        mutableInitial.addAll(it.second)
    }
    successors[automata.initial] = mutableInitial

    return automata.copy(successors = successors)
}

private fun EdgeCondition.combineWitEnterCondition(enterCond: EdgeCondition, positiveSig: MethodSignature?): EdgeCondition {
    check(enterCond.readMetaVar.isEmpty())

    if (positiveSig == null) return copy(other = this.other + enterCond.other)

    val rmv = readMetaVar.mapValues { (_, values) ->
        values.map { it.replaceSignatureIfAny(positiveSig) }
    }

    val other = this.other.map { it.replaceSignatureIfAny(positiveSig) }

    return EdgeCondition(rmv, other = other + enterCond.other)
}

private fun EdgeEffect.combineWitEnterCondition(positiveSig: MethodSignature?): EdgeEffect {
    if (positiveSig == null) return this

    val amv = assignMetaVar.mapValues { (_, values) ->
        values.map { it.replaceSignatureIfAny(positiveSig) }
    }
    return EdgeEffect(amv)
}

private fun MethodPredicate.replaceSignatureIfAny(newSig: MethodSignature): MethodPredicate {
    val thisSig = predicate.signature
    if (thisSig.methodName.name !is SemgrepPatternAction.SignatureName.AnyName) return this
    if (thisSig.enclosingClassName != MethodEnclosingClassName.anyClassName) return this

    val pred = predicate.copy(signature = newSig)
    return copy(predicate = pred)
}

private fun removeTransitiveAccept(automata: TaintRegisterStateAutomata): TaintRegisterStateAutomata {
    val predecessors = automataPredecessors(automata)

    val fixedAccept = hashSetOf<State>()
    for (state in automata.finalAcceptStates) {
        val statePredecessors = predecessors[state]
        if (statePredecessors.isNullOrEmpty()) {
            fixedAccept.add(state)
            continue
        }

        if (statePredecessors.any { it.second !in automata.finalAcceptStates }) {
            fixedAccept.add(state)
            continue
        }
    }

    return automata.copy(finalAcceptStates = fixedAccept)
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
        mutableSuccessors,
    )
}

private fun EdgeCondition.isDummyCondition(metaVarInfo: ResolvedMetaVarInfo): Boolean {
    for (cond in other) {
        if (cond.predicate.constraint != null) return false
        val sig = cond.predicate.signature

        when (val mn = sig.methodName.name) {
            is SemgrepPatternAction.SignatureName.Concrete -> return false
            SemgrepPatternAction.SignatureName.AnyName -> {}
            is SemgrepPatternAction.SignatureName.MetaVar -> {
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
            is TypeNamePattern.ArrayType,
            is TypeNamePattern.ClassName,
            is TypeNamePattern.FullyQualified,
            is TypeNamePattern.PrimitiveName -> return false
        }
    }

    return true
}

private fun cleanupAutomata(automata: TaintAutomataEdges): TaintAutomataEdges {
    return dropUnassignedMarkChecks(automata)
}

private fun dropUnassignedMarkChecks(automata: TaintAutomataEdges): TaintAutomataEdges {
    val edges = automata.edges.map { it.copy(edgeCondition = it.edgeCondition.dropUnassignedMarkChecks(it.stateFrom)) }
    val edgesToFinalAccept = automata.edgesToFinalAccept.map { it.copy(edgeCondition = it.edgeCondition.dropUnassignedMarkChecks(it.stateFrom)) }
    val edgesToFinalDead = automata.edgesToFinalDead.map { it.copy(edgeCondition = it.edgeCondition.dropUnassignedMarkChecks(it.stateFrom)) }
    return TaintAutomataEdges(
        automata.automata, automata.metaVarInfo, automata.globalStateAssignStates,
        edges, edgesToFinalAccept, edgesToFinalDead
    )
}

private fun EdgeCondition.dropUnassignedMarkChecks(state: State): EdgeCondition {
    val readMetaVar = readMetaVar.filterKeys { it in state.register.assignedVars }
    return EdgeCondition(readMetaVar, other)
}
