package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.org.opentaint.semgrep.pattern.Mark
import org.opentaint.org.opentaint.semgrep.pattern.UserRuleFromSemgrepInfo
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State

class TaintRuleGenerationCtx(
    private val uniqueRuleId: String,
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
        fun stateClean(state: State, varName: MetavarAtom?, pos: PositionBaseWithModifiers?): List<SerializedTaintCleanAction>? = null
        fun stateAccessedMarks(state: State, varName: MetavarAtom): Set<String>? = null
    }

    fun globalStateMarkName(state: State): String {
        val stateId = automata.stateId(state)
        return "${uniqueRuleId}${Mark.ArtificialStateName}$stateId"
    }

    fun artificialMarkName(name: String): String {
        return "${uniqueRuleId}${Mark.ArtificialMetavarName}$name"
    }

    val stateVarPosition by lazy {
        PositionBase.ClassStatic("${uniqueRuleId}${Mark.ArtificialStateName}").base()
    }

    fun stateAssignMark(
        varName: MetavarAtom,
        state: State,
        position: PositionBaseWithModifiers
    ): List<SerializedTaintAssignAction> {
        compositionStrategy?.stateAssign(state, varName, position)?.let { return it }

        val markName = stateMarkName(varName, state)
            ?: return emptyList()

        return listOf(SerializedTaintAssignAction(markName, pos = position))
    }

    fun stateCleanMark(
        varName: MetavarAtom?,
        state: State,
        position: PositionBaseWithModifiers?
    ): List<SerializedTaintCleanAction> {
        compositionStrategy?.stateClean(state, varName, position)?.let { return it }

        if (varName == null || position == null) return emptyList()

        val markName = stateMarkName(varName, state)
            ?: return emptyList()

        return listOf(SerializedTaintCleanAction(markName, pos = position))
    }

    fun containsStateMark(
        varName: MetavarAtom,
        state: State,
        position: PositionBaseWithModifiers
    ): SerializedCondition {
        compositionStrategy?.stateContains(state, varName, position)?.let { return it }

        val markName = stateMarkName(varName, state)
            ?: return SerializedCondition.mkFalse()

        return SerializedCondition.ContainsMark(markName, position)
    }

    private fun usedTaintMarks(state: State): Set<String> =
        state.register.assignedVars.keys.flatMapTo(hashSetOf()) { mv ->
            compositionStrategy?.stateAccessedMarks(state, mv)?.let { return@flatMapTo it }
            setOfNotNull(stateMarkName(mv, state))
        }

    fun edgeRuleInfo(edge: TaintRuleEdge): UserRuleFromSemgrepInfo {
        val relevantTaintMarks = hashSetOf<String>()
        relevantTaintMarks += usedTaintMarks(edge.stateFrom)
        relevantTaintMarks += usedTaintMarks(edge.stateTo)
        if (edge.checkGlobalState || edge.stateTo in globalStateAssignStates) {
            relevantTaintMarks += globalStateMarkName(edge.stateTo)
        }

        return UserRuleFromSemgrepInfo(uniqueRuleId, relevantTaintMarks)
    }

    private fun allStates(): List<State> {
        val result = mutableListOf<State>()
        edges.flatMapTo(result) { listOf(it.stateFrom, it.stateTo) }
        edgesToFinalAccept.flatMapTo(result) { listOf(it.stateFrom, it.stateTo) }
        edgesToFinalDead.flatMapTo(result) { listOf(it.stateFrom, it.stateTo) }
        return result
    }

    private val metaVarStates by lazy {
        val result = hashMapOf<MetavarAtom, MutableSet<State>>()
        allStates().forEach { state ->
            state.register.assignedVars.keys.forEach { mv ->
                result.getOrPut(mv, ::hashSetOf).add(state)
            }
        }
        result
    }

    fun containsMarkWithAnyState(
        varName: MetavarAtom,
        position: PositionBaseWithModifiers
    ): SerializedCondition {
        val varStates = metaVarStates[varName] ?: error("MetaVar is not assigned")
        val conditions = varStates.map { containsStateMark(varName, it, position) }
        return serializedConditionOr(conditions)
    }

    private fun stateMarkName(varName: MetavarAtom, state: State): String? =
        state.register.assignedVars[varName]?.let { stateMarkName(varName, it) }

    private fun stateMarkName(varName: MetavarAtom, varValue: Int): String {
        return "${uniqueRuleId}${Mark.MarkSeparator}${varName}${Mark.MarkSeparator}$varValue"
    }
}
