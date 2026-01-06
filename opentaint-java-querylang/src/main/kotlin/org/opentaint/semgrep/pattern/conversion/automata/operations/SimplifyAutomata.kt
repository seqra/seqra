package org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.semgrep.pattern.conversion.automata.AutomataBuilderCtx
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.taint.trySimplifyMethodFormula
import java.util.Collections
import java.util.IdentityHashMap

internal fun AutomataBuilderCtx.simplifyAutomata(automata: SemgrepRuleAutomata) {
    val visited = Collections.newSetFromMap<AutomataNode>(IdentityHashMap())

    val unprocessed = mutableListOf<AutomataNode>()
    unprocessed.addAll(automata.initialNodes)

    while (unprocessed.isNotEmpty()) {
        val node = unprocessed.removeLast()
        if (!visited.add(node)) continue

        val iter = node.outEdges.listIterator()
        while (iter.hasNext()) {
            val (edge, nextState) = iter.next()
            unprocessed.add(nextState)

            val simplifiedEdge = simplifyEdge(automata.formulaManager, edge)
            iter.set(simplifiedEdge to nextState)
        }
    }
}

private fun AutomataBuilderCtx.simplifyEdge(
    manager: MethodFormulaManager,
    edge: AutomataEdgeType
): AutomataEdgeType {
    if (edge !is AutomataEdgeType.AutomataEdgeTypeWithFormula) return edge

    val simplifiedFormula = trySimplifyMethodFormula(manager, edge.formula, metaVarInfo, cancelation)

    return when (edge) {
        is AutomataEdgeType.MethodCall -> AutomataEdgeType.MethodCall(simplifiedFormula)
        is AutomataEdgeType.MethodEnter -> AutomataEdgeType.MethodEnter(simplifiedFormula)
    }
}
