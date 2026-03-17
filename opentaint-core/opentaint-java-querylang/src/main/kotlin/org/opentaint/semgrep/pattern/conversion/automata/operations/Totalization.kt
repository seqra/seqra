package org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.semgrep.pattern.conversion.automata.AutomataBuilderCtx
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType.AutomataEdgeTypeWithFormula
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaSat

fun AutomataBuilderCtx.totalizeAutomata(automata: SemgrepRuleAutomata, keepTrivialEdges: Boolean = false) {
    totalizeMethodEnters(automata, keepTrivialEdges)
    totalizeMethodCalls(automata, keepTrivialEdges)
    totalizeMethodExits(automata, keepTrivialEdges)
}

private fun AutomataBuilderCtx.totalizeMethodCalls(automata: SemgrepRuleAutomata, keepTrivialEdges: Boolean) {
    totalize(automata) { node ->
        methodCallEdgeToDeadNode(automata, node, keepTrivialEdges)
    }
}

fun AutomataBuilderCtx.methodCallEdgeToDeadNode(
    automata: SemgrepRuleAutomata,
    node: AutomataNode,
    keepTrivialEdges: Boolean
): AutomataEdgeType? = totalizeNode(automata, node, keepTrivialEdges, AutomataEdgeType::MethodCall)

private fun AutomataBuilderCtx.totalizeMethodEnters(automata: SemgrepRuleAutomata, keepTrivialEdges: Boolean) {
    totalize(automata) { node ->
       methodEnterEdgeToDeadNode(automata, node, keepTrivialEdges)
    }
}

fun AutomataBuilderCtx.methodEnterEdgeToDeadNode(
    automata: SemgrepRuleAutomata,
    node: AutomataNode,
    keepTrivialEdges: Boolean
): AutomataEdgeType? = totalizeNode(automata, node, keepTrivialEdges, AutomataEdgeType::MethodEnter)

private fun AutomataBuilderCtx.totalizeMethodExits(automata: SemgrepRuleAutomata, keepTrivialEdges: Boolean) {
    totalize(automata) { node ->
        methodExitEdgeToDeadNode(automata, node, keepTrivialEdges)
    }
}

fun AutomataBuilderCtx.methodExitEdgeToDeadNode(
    automata: SemgrepRuleAutomata,
    node: AutomataNode,
    keepTrivialEdges: Boolean
): AutomataEdgeType? = totalizeNode(automata, node, keepTrivialEdges, AutomataEdgeType::MethodExit)

private fun totalize(
    automata: SemgrepRuleAutomata,
    edgeToDeadNode: (AutomataNode) -> AutomataEdgeType?,
) {

    traverse(automata) { node ->
        if (node == automata.deadNode) {
            return@traverse
        }

        val newEdge = edgeToDeadNode(node)
            ?: return@traverse

        node.outEdges.add(newEdge to automata.deadNode)
    }
}

private inline fun <reified EdgeType : AutomataEdgeTypeWithFormula> AutomataBuilderCtx.totalizeNode(
    automata: SemgrepRuleAutomata,
    node: AutomataNode,
    keepTrivialEdges: Boolean,
    mkEdge: (MethodFormula) -> EdgeType,
): EdgeType? {
    cancelation.check()

    val relevantEdges = node.outEdges.filter { it.first is EdgeType }
    if (relevantEdges.isEmpty() && !keepTrivialEdges) return null

    if (relevantEdges.any { it.second == automata.deadNode }) {
        // Edge to dead node already exists
        return null
    }

    val negationFormula = getNodeNegation<EdgeType>(node)
        ?: return null

    return mkEdge(negationFormula)
}

private inline fun <reified EdgeType : AutomataEdgeTypeWithFormula> AutomataBuilderCtx.getNodeNegation(
    node: AutomataNode,
): MethodFormula? {
    val formulas = node.outEdges.mapNotNull { (it.first as? EdgeType)?.formula?.complement() }

    val result = formulaManager.mkAnd(formulas)
    if (!methodFormulaSat(formulaManager, result, metaVarInfo, cancelation)) {
        return null
    }

    return result
}
