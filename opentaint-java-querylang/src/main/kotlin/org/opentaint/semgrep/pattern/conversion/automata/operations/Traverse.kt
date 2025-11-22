package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata

fun traverse(automata: SemgrepRuleAutomata, action: (AutomataNode) -> Unit) {
    val visited = hashSetOf<AutomataNode>()
    automata.initialNodes.forEach {
        traverse(it, visited, action)
    }
}

private fun traverse(node: AutomataNode, visited: MutableSet<AutomataNode>, action: (AutomataNode) -> Unit) {
    visited.add(node)
    action(node)
    node.outEdges.forEach { (_, to) ->
        if (to !in visited) {
            traverse(to, visited, action)
        }
    }
}
