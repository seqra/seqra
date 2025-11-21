package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.simplifyMethodFormulaAnd

fun intersection(a1: SemgrepRuleAutomata, a2: SemgrepRuleAutomata): SemgrepRuleAutomata {
    val manager = a1.formulaManager
    check(a1.formulaManager === a2.formulaManager)

    val root = createNewNode(a1.initialNode, a2.initialNode)

    val newNodes = mutableMapOf<Pair<AutomataNode, AutomataNode>, AutomataNode>()
    newNodes[a1.initialNode to a2.initialNode] = root

    val queue = mutableListOf(a1.initialNode to a2.initialNode)

    while (queue.isNotEmpty()) {
        val (n1, n2) = queue.removeFirst()
        val node = newNodes.getOrPut(n1 to n2) {
            createNewNode(n1, n2)
        }

        n1.outEdges.forEach { (outType1, to1) ->
            n2.outEdges.forEach inner@{ (outType2, to2) ->
                val to = newNodes.getOrPut(to1 to to2) {
                    queue.add(to1 to to2)
                    createNewNode(to1, to2)
                }

                val edgeType = if (outType1 == outType2 && outType1 !is AutomataEdgeType.AutomataEdgeTypeWithFormula) {
                    outType1

                } else if (outType1 is AutomataEdgeType.MethodCall && outType2 is AutomataEdgeType.MethodCall) {
                    val formula = intersectMethodFormula(manager, outType1.formula, outType2.formula)
                        ?: return@inner

                    AutomataEdgeType.MethodCall(formula)

                } else if (outType1 is AutomataEdgeType.MethodEnter && outType2 is AutomataEdgeType.MethodEnter) {
                    val formula = intersectMethodFormula(manager, outType1.formula, outType2.formula)
                        ?: return@inner

                    AutomataEdgeType.MethodEnter(formula)

                } else {
                    return@inner
                }

                node.outEdges.add(edgeType to to)
            }
        }
    }

    return SemgrepRuleAutomata(
        a1.formulaManager,
        setOf(root),
        isDeterministic = a1.isDeterministic && a2.isDeterministic,
        hasMethodEnter = a1.hasMethodEnter && a2.hasMethodEnter,
        hasEndEdges = a1.hasEndEdges && a2.hasEndEdges,
    ).also {
        removeDeadNodes(it)
    }
}

private fun createNewNode(n1: AutomataNode, n2: AutomataNode): AutomataNode =
    AutomataNode().also {
        it.accept = n1.accept && n2.accept
    }

private fun intersectMethodFormula(
    formulaManager: MethodFormulaManager,
    f1: MethodFormula, f2: MethodFormula
): MethodFormula? {
    val result = simplifyMethodFormulaAnd(formulaManager, listOf(f1, f2))

    if (result == MethodFormula.False) {
        return null
    }

    return result
}
