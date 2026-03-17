package org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.dataflow.util.forEach
import org.opentaint.semgrep.pattern.conversion.automata.AutomataBuilderCtx
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import java.util.BitSet

fun removeDeadNodes(automata: SemgrepRuleAutomata) {
    removeDeadNodes(automata.initialNode, automata.deadNode, mutableSetOf())
}

// TODO: linear time?
private fun removeDeadNodes(
    node: AutomataNode,
    mainDeadNode: AutomataNode,
    visited: MutableSet<AutomataNode>,
) {
    visited.add(node)

    val initialOutEdges = node.outEdges.toList()
    initialOutEdges.forEach { elem ->
        val to = elem.second

        if (to in visited || to == mainDeadNode) {
            return@forEach
        }

        if (!acceptIsReachable(to, mutableSetOf())) {
            node.outEdges.remove(elem)
        } else {
            removeDeadNodes(to, mainDeadNode, visited)
        }
    }
}

private fun acceptIsReachable(
    node: AutomataNode,
    visited: MutableSet<AutomataNode>,
): Boolean {
    visited.add(node)
    if (node.accept) {
        return true
    }

    var acceptIsReachable = false

    val initialOutEdges = node.outEdges.toList()
    initialOutEdges.forEach { elem ->
        val to = elem.second

        if (to in visited) {
            return@forEach
        }

        acceptIsReachable = acceptIsReachable || acceptIsReachable(to, visited)
    }

    return acceptIsReachable
}

private data class EdgeInfo(
    val type: AutomataEdgeType,
    val startNode: AutomataNode,
    val endNode: AutomataNode,
)

private fun reverse(automata: SemgrepRuleAutomata): SemgrepRuleAutomata {
    val allNodes = mutableListOf<AutomataNode>()
    traverse(automata) {
        allNodes.add(it)
    }
    val initialNodes = mutableSetOf<AutomataNode>()
    val newEdges = mutableListOf<EdgeInfo>()
    allNodes.forEach {
        if (it.accept) {
            initialNodes.add(it)
        }
        it.accept = it in automata.initialNodes
        it.outEdges.forEach { edge ->
            val (type, to) = edge
            newEdges.add(EdgeInfo(type, startNode = to, endNode = it))
        }
        it.outEdges.clear()
    }
    newEdges.forEach {
        it.startNode.outEdges.add(it.type to it.endNode)
    }

    val params = automata.params.copy(isDeterministic = false)
    return SemgrepRuleAutomata(
        automata.formulaManager,
        initialNodes,
        params
    )
}

private class EdgeBuilder(private val formulaManager: MethodFormulaManager) {
    private var hasEnd = false
    private var hasPatternStart = false
    private var hasPatternEnd = false

    private val methodCallFormulas = mutableListOf<MethodFormula>()
    private val methodEnterFormulas = mutableListOf<MethodFormula>()
    private val methodExitFormulas = mutableListOf<MethodFormula>()

    fun addEdge(edge: AutomataEdgeType) {
        when (edge) {
            AutomataEdgeType.End -> hasEnd = true
            AutomataEdgeType.PatternEnd -> hasPatternEnd = true
            AutomataEdgeType.PatternStart -> hasPatternStart = true
            is AutomataEdgeType.MethodCall -> methodCallFormulas.add(edge.formula)
            is AutomataEdgeType.MethodEnter -> methodEnterFormulas.add(edge.formula)
            is AutomataEdgeType.MethodExit -> methodExitFormulas.add(edge.formula)
        }
    }

    fun build(): List<AutomataEdgeType> = buildList {
        if (hasEnd) {
            add(AutomataEdgeType.End)
        }
        if (hasPatternStart) {
            add(AutomataEdgeType.PatternStart)
        }
        if (hasPatternEnd) {
            add(AutomataEdgeType.PatternEnd)
        }
        if (methodCallFormulas.isNotEmpty()) {
            add(AutomataEdgeType.MethodCall(formulaManager.mkOr(methodCallFormulas)))
        }
        if (methodEnterFormulas.isNotEmpty()) {
            add(AutomataEdgeType.MethodEnter(formulaManager.mkOr(methodEnterFormulas)))
        }
        if (methodExitFormulas.isNotEmpty()) {
            add(AutomataEdgeType.MethodExit(formulaManager.mkOr(methodExitFormulas)))
        }
    }
}

fun AutomataBuilderCtx.hopcroftAlgorithhm(automata: SemgrepRuleAutomata): SemgrepRuleAutomata {
    totalizeAutomata(automata)

    val nodes = mutableListOf<AutomataNode>()
    val node2class = mutableMapOf<AutomataNode, Int>()
    val acceptMask = BitSet()
    val nonAcceptMask = BitSet()
    traverse(automata) { node ->
        val id = nodes.size
        nodes.add(node)

        if (node.accept) {
            acceptMask.set(id)
            node2class[node] = 0
        } else {
            nonAcceptMask.set(id)
            node2class[node] = 1
        }
    }
    var classCnt = 2

    val eqClasses = mutableSetOf(acceptMask, nonAcceptMask)
    var newClassesFound = true
    while (newClassesFound) {
        newClassesFound = false
        for (eqClass in eqClasses) {
            val edges = mutableListOf<AutomataEdgeType>()

            eqClass.forEach { nodeIdx ->
                edges.addAll(nodes[nodeIdx].outEdges.map { it.first })
            }

            for (edge in edges) {
                val dstClasses2NewClassNum = mutableMapOf<BitSet, Int>()
                val newClassNum = mutableMapOf<Int, Int>()
                val newClasses = mutableMapOf<Int, BitSet>()

                eqClass.forEach { nodeIdx ->
                    val reachable = BitSet()
                    nodes[nodeIdx].outEdges.forEach { curEdge ->
                        if (intersectEdges(edge, curEdge.first) != null) {
                            reachable.set(node2class[curEdge.second]!!)
                        }
                    }
                    newClassNum[nodeIdx] = dstClasses2NewClassNum.getOrPut(reachable) {
                        if (dstClasses2NewClassNum.isEmpty()) {
                            node2class[nodes[nodeIdx]]!!
                        } else {
                            classCnt++
                        }
                    }
                    newClasses.getOrPut(newClassNum[nodeIdx]!!) {
                        BitSet()
                    }.set(nodeIdx)
                }

                if (dstClasses2NewClassNum.size > 1) {
                    eqClass.forEach { nodeIdx ->
                        node2class[nodes[nodeIdx]] = newClassNum[nodeIdx]!!
                    }
                    eqClasses.remove(eqClass)
                    newClasses.values.forEach(eqClasses::add)
                    newClassesFound = true
                    break
                }
            }
            if (newClassesFound) {
                break
            }
        }
    }

    val eqClassesList = eqClasses.filter { it.cardinality() > 0 }.toList()
    val resultNodes = eqClassesList.map { eqClass ->
        AutomataNode().also {
            it.accept = nodes[eqClass.nextSetBit(0)].accept
        }
    }

    val resultEdges = mutableMapOf<Pair<Int, Int>, EdgeBuilder>()

    eqClassesList.forEach { eqClass ->
        eqClass.forEach { idx ->
            nodes[idx].outEdges.forEach { (edge, dst) ->
                resultEdges.getOrPut(node2class[nodes[idx]]!! to node2class[dst]!!) {
                    EdgeBuilder(formulaManager)
                }.addEdge(edge)
            }
        }
    }

    eqClassesList.forEachIndexed { idx1, bitset1 ->
        val classNum1 = node2class[nodes[bitset1.nextSetBit(0)]]!!
        eqClassesList.forEachIndexed inner@{ idx2, bitset2 ->
            val classNum2 = node2class[nodes[bitset2.nextSetBit(0)]]!!
            val edges = resultEdges[classNum1 to classNum2]?.build() ?: return@inner

            resultNodes[idx1].outEdges.addAll(edges.map { it to resultNodes[idx2] })
        }
    }

    val initialNodeIdx = nodes.indexOf(automata.initialNode)
    val newInitialNode = resultNodes[eqClassesList.indexOfFirst { it.get(initialNodeIdx) }]
    return SemgrepRuleAutomata(
        formulaManager = formulaManager,
        initialNodes = setOf(newInitialNode),
        automata.params,
    ).also {
        simplifyAutomata(it)
        removeDeadNodes(it)
    }
}

fun AutomataBuilderCtx.brzozowskiAlgorithm(startAutomata: SemgrepRuleAutomata): SemgrepRuleAutomata {
    if (startAutomata.params.isDeterministic) {
        return startAutomata
    }

    val automata = startAutomata.deepCopy()
    val reversedNfa = reverse(automata)
    val reversedDfa = determinize(reversedNfa)
    val newNfa = reverse(reversedDfa)
    val result = determinize(newNfa, simplifyAutomata = true)
    return unifyMetavars(result)
}
