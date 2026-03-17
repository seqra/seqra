package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State

class TaintRegisterStateAutomataBuilder(val startStateId: Int) {
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    val acceptStates = hashSetOf<State>()
    val deadStates = hashSetOf<State>()
    private val nodeIndex = hashMapOf<AutomataNode, Int>()

    fun newState(): State = newState(AutomataNode())

    fun newState(node: AutomataNode): State {
        val id = stateId(node)
        return State(id, emptyRegister)
    }

    fun stateId(node: AutomataNode): Int {
        val id = nodeIndex.size + startStateId
        val curId = nodeIndex.putIfAbsent(node, id)
        return curId ?: id
    }

    fun build(manager: MethodFormulaManager, initial: State) =
        TaintRegisterStateAutomata(manager, initial, acceptStates, deadStates, successors)

    companion object {
        private val emptyRegister = TaintRegisterStateAutomata.StateRegister(emptyMap())
    }
}
