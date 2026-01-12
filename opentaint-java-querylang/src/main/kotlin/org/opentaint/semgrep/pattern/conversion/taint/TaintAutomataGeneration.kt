package org.opentaint.semgrep.pattern.conversion.taint

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
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
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.StateRegister
import java.util.IdentityHashMap

fun RuleConversionCtx.generateAutomataWithTaintEdges(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
    automataId: String,
    acceptStateVars: Set<MetavarAtom>
): TaintRuleGenerationCtx {
    val simulated = simulateAutomata(automata)
    val meaningFullAutomata = removeMeaningLessEdges(simulated)
    val cleaned = removeUnreachableStates(meaningFullAutomata)
    val rewritten = rewriteEdges(cleaned)
    val liveAutomata = eliminateDeadVariables(rewritten, acceptStateVars)
    val cleanAutomata = cleanupAutomata(liveAutomata, metaVarInfo)
    val generatedEdges =  generateTaintEdges(cleanAutomata, metaVarInfo, automataId)
    val resultAutomata = cleanupAutomata(generatedEdges)
    return resultAutomata
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
        newSuccessors, automata.nodeIndex
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

    val newCondition =
        EdgeCondition(newReadMetavar.mapValues { it.value.toList() }, newOther)
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

private fun removeUnreachableStates(
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
    val cleanerState =
        State(AutomataNode(), StateRegister(emptyMap()))
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

private fun TaintRegisterStateAutomata.allStates(): Set<State> {
    val states = hashSetOf<State>()
    states += initial
    states += finalAcceptStates
    states += finalDeadStates
    states += successors.keys
    return states
}
