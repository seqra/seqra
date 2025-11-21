package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata

fun acceptIfCurrentAutomataAcceptsPrefix(automata: SemgrepRuleAutomata) {
    val newAcceptNode = AutomataNode().also {
        it.accept = true
        it.outEdges.add(AutomataEdgeType.MethodCall(MethodFormula.True) to it)
    }

    traverse(automata) { node ->
        if (node == newAcceptNode) {
            return@traverse
        }

        val initialOutEdges = node.outEdges.toList()
        initialOutEdges.forEach { edge ->
            val to = edge.second
            if (to.accept) {
                val newEdge = edge.first to newAcceptNode
                node.outEdges.remove(edge)
                node.outEdges.add(newEdge)
            }
        }
    }
}
