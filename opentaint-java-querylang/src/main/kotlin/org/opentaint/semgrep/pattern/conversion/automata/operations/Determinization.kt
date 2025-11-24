package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.methodFormulaSat
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.trySimplifyMethodFormula
import java.util.Collections
import java.util.IdentityHashMap

fun determinize(
    automata: SemgrepRuleAutomata,
    metaVarInfo: ResolvedMetaVarInfo,
): SemgrepRuleAutomata {
    if (automata.isDeterministic) {
        return automata
    }

    simplifyAutomata(automata, metaVarInfo)

    val formulaManager = automata.formulaManager

    val root = createNewNode(automata.initialNodes)
    var hasMethodEnter = false
    var hasEndEdges = false

    val newNodes = mutableMapOf<Set<AutomataNode>, AutomataNode>()
    newNodes[automata.initialNodes] = root

    val queue = mutableListOf(automata.initialNodes)
    while (queue.isNotEmpty()) {
        val s = queue.removeFirst()
        val newNode = newNodes.getOrPut(s) { createNewNode(s) }

        val initialEdges = s.flatMap { it.outEdges }

        for (type in listOf(AutomataEdgeType.End, AutomataEdgeType.PatternStart, AutomataEdgeType.PatternEnd)) {
            val edgesWithoutFormula = initialEdges.filter { it.first == type }
            if (edgesWithoutFormula.isNotEmpty()) {
                hasEndEdges = type == AutomataEdgeType.End
                val nodes = edgesWithoutFormula.map { it.second }.toSet()
                val toNode = newNodes.getOrPut(nodes) {
                    queue.add(nodes)
                    createNewNode(nodes)
                }
                newNode.outEdges.add(type to toNode)
            }
        }

        determinizeSpecificEdgeType<AutomataEdgeType.MethodCall>(
            formulaManager, metaVarInfo, initialEdges, newNodes, queue, newNode
        ) {
            AutomataEdgeType.MethodCall(it)
        }

        determinizeSpecificEdgeType<AutomataEdgeType.MethodEnter>(
            formulaManager, metaVarInfo, initialEdges, newNodes, queue, newNode
        ) {
            hasMethodEnter = true
            AutomataEdgeType.MethodEnter(it)
        }
    }

    return SemgrepRuleAutomata(
        formulaManager,
        initialNodes = setOf(root),
        isDeterministic = true,
        hasMethodEnter = hasMethodEnter,
        hasEndEdges = hasEndEdges,
    ).also {
        removeDeadNodes(it)
    }
}

private fun createNewNode(nodes: Set<AutomataNode>): AutomataNode {
    return AutomataNode().also {
        it.accept = nodes.any { node -> node.accept }
    }
}

private inline fun <reified Type : AutomataEdgeType.AutomataEdgeTypeWithFormula> determinizeSpecificEdgeType(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialEdges: List<Pair<AutomataEdgeType, AutomataNode>>,
    newNodes: MutableMap<Set<AutomataNode>, AutomataNode>,
    queue: MutableList<Set<AutomataNode>>,
    newNode: AutomataNode,
    createEdge: (MethodFormula) -> Type,
) {
    val edgesOfThisType = initialEdges.mapNotNull { (type, to) ->
        (type as? Type)?.formula?.let { it to to }
    }.groupBy { it.first }
    if (edgesOfThisType.isNotEmpty()) {
        val n = edgesOfThisType.size
        val out = mutableMapOf<Set<AutomataNode>, MutableList<Int>>()

        check(n < Int.SIZE_BITS) {
            "Determinization failed: too many formulas $n"
        }

        for (i in 1..<(1 shl n)) {
            val toSet = edgesOfThisType.entries.flatMapIndexed { index, (_, to) ->
                val take = (i and (1 shl index)) != 0
                if (!take) emptyList() else to.map { it.second }
            }.toSet()
            val maskList = out.getOrPut(toSet) { mutableListOf() }
            maskList.add(i)
        }

        out.entries.forEach { (toSet, masks) ->
            val formulas = mutableListOf<MethodFormula>()
            masks.forEach inner@{ mask ->
                val formulaLits = edgesOfThisType.keys.mapIndexed { index, formula ->
                    val takeNegation = (mask and (1 shl index)) == 0
                    if (takeNegation) {
                        formula.complement()
                    } else {
                        formula
                    }
                }

                val formula = formulaManager.mkAnd(formulaLits)

                if (!methodFormulaSat(formulaManager, formula, metaVarInfo)) {
                    return@inner
                }

                formulas.add(formula)
            }

            if (formulas.isEmpty()) {
                return@forEach
            }

            val singleFormula = formulaManager.mkOr(formulas)

            val toNode = newNodes.getOrPut(toSet) {
                queue.add(toSet)
                createNewNode(toSet)
            }
            newNode.outEdges.add(createEdge(singleFormula) to toNode)
        }
    }
}

private fun simplifyAutomata(automata: SemgrepRuleAutomata, metaVarInfo: ResolvedMetaVarInfo) {
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

            val simplifiedEdge = simplifyEdge(automata.formulaManager, edge, metaVarInfo)
            iter.set(simplifiedEdge to nextState)
        }
    }
}

private fun simplifyEdge(
    manager: MethodFormulaManager,
    edge: AutomataEdgeType,
    metaVarInfo: ResolvedMetaVarInfo
): AutomataEdgeType {
    if (edge !is AutomataEdgeType.AutomataEdgeTypeWithFormula) return edge

    val simplifiedFormula = trySimplifyMethodFormula(manager, edge.formula, metaVarInfo)

    return when (edge) {
        is AutomataEdgeType.MethodCall -> AutomataEdgeType.MethodCall(simplifiedFormula)
        is AutomataEdgeType.MethodEnter -> AutomataEdgeType.MethodEnter(simplifiedFormula)
    }
}
