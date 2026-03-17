package org.opentaint.semgrep.pattern.conversion.automata.operations

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata

fun traverse(automata: SemgrepRuleAutomata, action: (AutomataNode) -> Unit) {
    val visited = hashSetOf<AutomataNode>()
    automata.initialNodes.forEach {
        traverse(it, visited, action)
    }
}

private fun traverse(node: AutomataNode, visited: MutableSet<AutomataNode>, action: (AutomataNode) -> Unit) {
    if (!visited.add(node)) return
    action(node)
    node.outEdges.forEach { (_, to) ->
        traverse(to, visited, action)
    }
}

fun SemgrepRuleAutomata.containsAcceptState(): Boolean {
    var result = false
    traverse(this) { if (it.accept) result = true }
    return result
}

fun countStates(automata: SemgrepRuleAutomata): Int {
    var states = 0
    traverse(automata) { states++ }
    return states
}

private data class AutomataPath(
    val nodes: PersistentSet<AutomataNode>,
    val edges: PersistentList<AutomataEdgeType>,
) {
    fun add(node: AutomataNode, edge: AutomataEdgeType) = AutomataPath(nodes.add(node), edges.add(edge))
}

fun collectSimplePathToAccept(automata: SemgrepRuleAutomata): List<List<AutomataEdgeType>> {
    val result = mutableListOf<List<AutomataEdgeType>>()

    val unprocessed = mutableListOf<Pair<AutomataNode, AutomataPath>>()
    automata.initialNodes.forEach {
        unprocessed.add(it to AutomataPath(persistentHashSetOf(it), persistentListOf()))
    }

    while (unprocessed.isNotEmpty()) {
        val (node, path) = unprocessed.removeLast()

        if (node.accept) {
            result.add(path.edges)
        }

        for ((edge, nextNode) in node.outEdges) {
            if (nextNode in path.nodes) continue
            unprocessed.add(nextNode to path.add(nextNode, edge))
        }
    }

    return result
}
