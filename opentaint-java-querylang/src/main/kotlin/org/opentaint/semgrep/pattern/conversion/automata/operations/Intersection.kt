package org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.semgrep.pattern.conversion.automata.AutomataBuilderCtx
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaSat

fun AutomataBuilderCtx.intersection(
    a1: SemgrepRuleAutomata,
    a2: SemgrepRuleAutomata
): SemgrepRuleAutomata {
    check(a1.formulaManager === a2.formulaManager)

    val root = createNewNode(a1.initialNode, a2.initialNode)

    val newNodes = mutableMapOf<Pair<AutomataNode, AutomataNode>, AutomataNode>()
    newNodes[a1.initialNode to a2.initialNode] = root

    val queue = ArrayDeque<Pair<AutomataNode, AutomataNode>>()
    queue.add(a1.initialNode to a2.initialNode)

    while (queue.isNotEmpty()) {
        val (n1, n2) = queue.removeFirst()
        val node = newNodes.getOrPut(n1 to n2) {
            createNewNode(n1, n2)
        }

        for ((outType1, to1) in n1.outEdges) {
            for ((outType2, to2) in n2.outEdges) {
                val to = newNodes.getOrPut(to1 to to2) {
                    queue.add(to1 to to2)
                    createNewNode(to1, to2)
                }

                val edgeType = intersectEdges(outType1, outType2)
                    ?: continue

                node.outEdges.add(edgeType to to)
            }
        }
    }

    val intersection = SemgrepRuleAutomata(
        a1.formulaManager,
        setOf(root),
        isDeterministic = a1.isDeterministic && a2.isDeterministic,
        hasMethodEnter = a1.hasMethodEnter && a2.hasMethodEnter,
        hasEndEdges = a1.hasEndEdges && a2.hasEndEdges,
    )

    val unified = unifyMetavars(intersection)
    return unified.also {
        removeDeadNodes(it)
    }
}

internal fun AutomataBuilderCtx.intersectEdges(
    outType1: AutomataEdgeType,
    outType2: AutomataEdgeType,
): AutomataEdgeType? {
    if (outType1 !is AutomataEdgeType.AutomataEdgeTypeWithFormula) {
        if (outType1 == outType2) return outType1

        return null
    }

    return when (outType2) {
        AutomataEdgeType.End,
        AutomataEdgeType.PatternEnd,
        AutomataEdgeType.PatternStart,
            -> return null

        is AutomataEdgeType.MethodCall -> when (outType1) {
            is AutomataEdgeType.MethodCall -> {
                val formula = intersectMethodFormula(outType1.formula, outType2.formula)
                    ?: return null

                AutomataEdgeType.MethodCall(formula)
            }

            is AutomataEdgeType.MethodEnter -> {
                return null
            }

            is AutomataEdgeType.InitialLoopMethodCall -> {
                val formula = intersectMethodFormula(outType1.formula, outType2.formula)
                    ?: return null

                AutomataEdgeType.MethodCall(formula)
            }
        }

        is AutomataEdgeType.MethodEnter -> when (outType1) {
            is AutomataEdgeType.MethodEnter -> {
                val formula = intersectMethodFormula(outType1.formula, outType2.formula)
                    ?: return null

                AutomataEdgeType.MethodEnter(formula)
            }

            is AutomataEdgeType.MethodCall -> {
                return null
            }

            is AutomataEdgeType.InitialLoopMethodCall -> {
                return outType2
            }
        }

        is AutomataEdgeType.InitialLoopMethodCall -> when (outType1) {
            is AutomataEdgeType.MethodCall -> return intersectEdges(outType2, outType1)
            is AutomataEdgeType.MethodEnter -> return intersectEdges(outType2, outType1)
            is AutomataEdgeType.InitialLoopMethodCall -> {
                val formula = intersectMethodFormula(outType1.formula, outType2.formula)
                    ?: return null

                return AutomataEdgeType.InitialLoopMethodCall(formula)
            }
        }
    }
}

private fun createNewNode(n1: AutomataNode, n2: AutomataNode): AutomataNode =
    AutomataNode().also {
        it.accept = n1.accept && n2.accept
    }

private fun AutomataBuilderCtx.intersectMethodFormula(
    f1: MethodFormula,
    f2: MethodFormula
): MethodFormula? {
    val result = formulaManager.mkAnd(listOf(f1, f2))
    if (!methodFormulaSat(formulaManager, result, metaVarInfo, cancelation)) {
        return null
    }

    return result
}
