package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.util.forEach
import org.opentaint.semgrep.pattern.Mark
import org.opentaint.semgrep.pattern.Mark.RuleUniqueMarkPrefix
import org.opentaint.semgrep.pattern.UserRuleFromSemgrepInfo
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import java.util.BitSet

class TaintRuleGenerationCtx(
    val prefix: RuleUniqueMarkPrefix,
    private val automataEdges: TaintAutomataEdges,
    private val compositionStrategy: CompositionStrategy?
) {
    val automata: TaintRegisterStateAutomata get() = automataEdges.automata
    val metaVarInfo: TaintRuleGenerationMetaVarInfo get() = automataEdges.metaVarInfo
    val globalStateAssignStates: Set<State> get() = automataEdges.globalStateAssignStates
    val edges: List<TaintRuleEdge> get() = automataEdges.edges
    val edgesToFinalAccept: List<TaintRuleEdge> get() = automataEdges.edgesToFinalAccept
    val edgesToFinalDead: List<TaintRuleEdge> get() = automataEdges.edgesToFinalDead

    interface CompositionStrategy {
        fun stateContains(state: State, varName: MetavarAtom, pos: PositionBaseWithModifiers): SerializedCondition? = null
        fun stateAssign(state: State, varName: MetavarAtom, pos: PositionBaseWithModifiers): List<SerializedTaintAssignAction>? = null
        fun stateClean(state: State, stateBefore: State, varName: MetavarAtom?, pos: PositionBaseWithModifiers?): List<SerializedTaintCleanAction>? = null
        fun stateAccessedMarks(state: State, varName: MetavarAtom): Set<Mark.GeneratedMark>? = null
    }

    fun globalStateMarkName(state: State): Mark.GeneratedMark {
        val stateId = automata.stateId(state)
        return prefix.artificialState("$stateId")
    }

    val stateVarPosition by lazy {
        PositionBase.ClassStatic(prefix.artificialState("pos").taintMarkStr()).base()
    }

    fun stateAssignMark(
        varName: MetavarAtom,
        state: State,
        position: PositionBaseWithModifiers
    ): List<SerializedTaintAssignAction> {
        compositionStrategy?.stateAssign(state, varName, position)?.let { return it }

        val markName = stateMarkName(varName, state)
            ?: return emptyList()

        return listOf(markName.mkAssignMark(position))
    }

    fun stateCleanMark(
        varName: MetavarAtom?,
        state: State,
        stateBefore: State,
        position: PositionBaseWithModifiers?
    ): List<SerializedTaintCleanAction> {
        compositionStrategy?.stateClean(state, stateBefore, varName, position)?.let { return it }

        if (varName == null || position == null) return emptyList()

        val markName = stateMarkName(varName, stateBefore)
            ?: return emptyList()

        return listOf(markName.mkCleanMark(position))
    }

    fun containsStateMark(
        varName: MetavarAtom,
        state: State,
        position: PositionBaseWithModifiers
    ): SerializedCondition {
        compositionStrategy?.stateContains(state, varName, position)?.let { return it }

        val markName = stateMarkName(varName, state)
            ?: return SerializedCondition.mkFalse()

        return markName.mkContainsMark(position)
    }

    private fun usedTaintMarks(state: State): Set<Mark.GeneratedMark> =
        state.register.assignedVars.keys.flatMapTo(hashSetOf()) { mv ->
            compositionStrategy?.stateAccessedMarks(state, mv)?.let { return@flatMapTo it }
            setOfNotNull(stateMarkName(mv, state))
        }

    fun edgeRuleInfo(edge: TaintRuleEdge): UserRuleFromSemgrepInfo {
        val relevantTaintMarks = hashSetOf<Mark.GeneratedMark>()
        relevantTaintMarks += usedTaintMarks(edge.stateFrom)
        relevantTaintMarks += usedTaintMarks(edge.stateTo)
        if (edge.checkGlobalState || edge.stateTo in globalStateAssignStates) {
            relevantTaintMarks += globalStateMarkName(edge.stateTo)
        }

        val taintMarkNames = relevantTaintMarks.mapTo(hashSetOf()) { it.taintMarkStr() }
        return UserRuleFromSemgrepInfo(prefix.ruleId, taintMarkNames)
    }

    private fun allStates(): List<State> {
        val result = hashSetOf<State>()
        edges.flatMapTo(result) { listOf(it.stateFrom, it.stateTo) }
        edgesToFinalAccept.flatMapTo(result) { listOf(it.stateFrom, it.stateTo) }
        edgesToFinalDead.flatMapTo(result) { listOf(it.stateFrom, it.stateTo) }
        return result.toList()
    }

    private val allStates by lazy { allStates() }
    private val stateIndex by lazy { allStates.mapIndexed { i, s -> s to i }.toMap() }

    private fun statePredecessors(): List<BitSet> {
        val predecessors = List(allStates.size) { BitSet() }
        (edges + edgesToFinalAccept + edgesToFinalDead).forEach {
            val toIdx = stateIndex.getValue(it.stateTo)
            val fromIdx = stateIndex.getValue(it.stateFrom)
            predecessors[toIdx].set(fromIdx)
        }
        return predecessors
    }

    private fun stateTransitivePredecessors(): List<BitSet> {
        val predecessors = statePredecessors()

        do {
            var hasChanges = false
            for (states in predecessors) {
                val initialSize = states.cardinality()
                val allPredecessors = BitSet()
                states.forEach { allPredecessors.or(predecessors[it]) }
                states.or(allPredecessors)
                hasChanges = hasChanges || initialSize != states.cardinality()
            }
        } while (hasChanges)

        predecessors.forEachIndexed { index, set ->
            set.set(index)
        }

        return predecessors
    }

    private fun metaVarStates(): List<Map<MetavarAtom, BitSet>> {
        val transitivePredecessors = stateTransitivePredecessors()
        return transitivePredecessors.map { states ->
            val result = hashMapOf<MetavarAtom, BitSet>()
            states.forEach { stateId ->
                val state = allStates[stateId]
                state.register.assignedVars.keys.forEach { mv ->
                    result.getOrPut(mv, ::BitSet).set(stateId)
                }
            }
            result
        }
    }

    private val metaVarStates by lazy { metaVarStates() }

    private fun metaVarRelevantStates(state: State, varName: MetavarAtom): List<State> {
        val relevantMetaVarStates = metaVarStates[stateIndex.getValue(state)]
        val varStatesIndices = relevantMetaVarStates[varName] ?: error("MetaVar is not assigned")
        val varStates = mutableListOf<State>()
        varStatesIndices.forEach { varStates += allStates[it] }
        return varStates
    }

    fun containsMarkWithAnyStateBefore(
        state: State,
        varName: MetavarAtom,
        position: PositionBaseWithModifiers
    ): SerializedCondition {
        val varStates = metaVarRelevantStates(state, varName)
        val conditions = varStates.map { containsStateMark(varName, it, position) }
        return serializedConditionOr(conditions)
    }

    private fun stateMarkName(varName: MetavarAtom, state: State): Mark.GeneratedMark? =
        state.register.assignedVars[varName]?.let { stateMarkName(varName, it, prefix) }
}

fun State.stateMarkName(varName: MetavarAtom, prefix: RuleUniqueMarkPrefix): Mark.GeneratedMark? =
    register.assignedVars[varName]?.let { stateMarkName(varName, it, prefix) }

fun stateMarkName(varName: MetavarAtom, varValue: Int, prefix: RuleUniqueMarkPrefix): Mark.GeneratedMark =
    prefix.metaVarState(varName, varValue)
