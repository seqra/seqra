package org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.semgrep.pattern.conversion.automata.operations.unifyMetavars
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata

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

private fun reverse(automata: SemgrepRuleAutomata): SemgrepRuleAutomata {
    val allNodes = mutableListOf<AutomataNode>()
    traverse(automata) {
        allNodes.add(it)
    }
    val initialNodes = mutableSetOf<AutomataNode>()
    val newEdges = mutableListOf<Pair<AutomataEdgeType, Pair<AutomataNode, AutomataNode>>>()
    allNodes.forEach {
        if (it.accept) {
            initialNodes.add(it)
        }
        it.accept = it in automata.initialNodes
        it.outEdges.forEach { edge ->
            val (type, to) = edge
            newEdges.add(type to (to to it))
        }
        it.outEdges.clear()
    }
    newEdges.forEach { (type, edge) ->
        val (from, to) = edge
        from.outEdges.add(type to to)
    }

    return SemgrepRuleAutomata(
        automata.formulaManager,
        initialNodes,
        isDeterministic = false,
        hasMethodEnter = automata.hasMethodEnter,
        hasEndEdges = automata.hasEndEdges,
    )
}

fun brzozowskiAlgorithm(metaVarInfo: ResolvedMetaVarInfo, automata: SemgrepRuleAutomata): SemgrepRuleAutomata {
    if (automata.isDeterministic) {
        return automata
    }

    val reversedNfa = reverse(automata)
    val reversedDfa = determinize(reversedNfa, metaVarInfo)
    val newNfa = reverse(reversedDfa)
    val result = determinize(newNfa, metaVarInfo, simplifyAutomata = true)
    return unifyMetavars(result, metaVarInfo)
}
