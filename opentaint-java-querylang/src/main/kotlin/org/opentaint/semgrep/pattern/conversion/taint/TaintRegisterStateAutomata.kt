package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State

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

fun automataPredecessors(automata: TaintRegisterStateAutomata): Map<State, Set<Pair<Edge, State>>> {
    val predecessors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    for ((state, edges) in automata.successors) {
        for ((edge, edgeDst) in edges) {
            predecessors.getOrPut(edgeDst, ::hashSetOf).add(edge to state)
        }
    }
    return predecessors
}
