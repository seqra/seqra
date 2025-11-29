package org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaSat

/**
 * Return dead node
 * */
fun totalizeMethodCalls(
    metaVarInfo: ResolvedMetaVarInfo,
    automata: SemgrepRuleAutomata,
    oldDeadNode: AutomataNode? = null
): AutomataNode {
    return totalize(automata, oldDeadNode) { node ->
        val negationFormula = getNodeNegation<AutomataEdgeType.MethodCall>(automata.formulaManager, metaVarInfo, node)
            ?: return@totalize null

        AutomataEdgeType.MethodCall(negationFormula)
    }
}

/**
 * Return dead node
 * */
fun totalizeMethodEnters(
    metaVarInfo: ResolvedMetaVarInfo,
    automata: SemgrepRuleAutomata,
    oldDeadNode: AutomataNode? = null
): AutomataNode {
    automata.hasMethodEnter = true
    return totalize(automata, oldDeadNode) { node ->
        if (node != automata.initialNode) {
            check(node.outEdges.none { it.first is AutomataEdgeType.MethodEnter }) {
                "Unexpected MethodEnter edge in non-root node"
            }
            return@totalize null
        }

        val negationFormula = getNodeNegation<AutomataEdgeType.MethodEnter>(automata.formulaManager, metaVarInfo, node)
            ?: return@totalize null

        AutomataEdgeType.MethodEnter(negationFormula)
    }
}

private fun totalize(
    automata: SemgrepRuleAutomata,
    oldDeadNode: AutomataNode?,
    edgeToDeadNode: (AutomataNode) -> AutomataEdgeType?,
): AutomataNode {
    check(automata.isDeterministic) {
        "Cannot totalize NFA"
    }

    val deadNode = AutomataNode().also {
        it.outEdges.add(AutomataEdgeType.MethodCall(MethodFormula.True) to it)
    }

    traverse(automata) { node ->
        if (node == oldDeadNode) {
            return@traverse
        }

        val edgesToOldDeadNodes = node.outEdges.filter { it.second == oldDeadNode }.map { it.first }
        node.outEdges.removeIf { it.second == oldDeadNode }
        node.outEdges.addAll(edgesToOldDeadNodes.map { it to deadNode })

        val newEdge = edgeToDeadNode(node)
            ?: return@traverse

        node.outEdges.add(newEdge to deadNode)
    }

    return deadNode
}

private inline fun <reified EdgeType : AutomataEdgeType.AutomataEdgeTypeWithFormula> getNodeNegation(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    node: AutomataNode,
): MethodFormula? {
    val formulas = node.outEdges.mapNotNull { (it.first as? EdgeType)?.formula?.complement() }

    val result = formulaManager.mkAnd(formulas)
    if (!methodFormulaSat(formulaManager, result, metaVarInfo)) {
        return null
    }

    return result
}
