package org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata

fun addEndEdges(automata: SemgrepRuleAutomata) {
    if (automata.hasEndEdges) {
        return
    }

    automata.hasEndEdges = true

    val newAcceptNode = AutomataNode().also {
        it.accept = true
        it.outEdges.add(AutomataEdgeType.End to it)
    }

    val newRejectNode = AutomataNode().also {
        it.outEdges.add(AutomataEdgeType.End to it)
    }

    traverse(automata) { node ->
        if (node == newAcceptNode || node == newRejectNode) {
            return@traverse
        }

        if (node.accept) {
            node.accept = false
            node.outEdges.add(AutomataEdgeType.End to newAcceptNode)
        } else {
            node.outEdges.add(AutomataEdgeType.End to newRejectNode)
        }
    }
}

fun addDummyMethodEnter(automata: SemgrepRuleAutomata): SemgrepRuleAutomata {
    check(!automata.hasMethodEnter) {
        "Shouldn't add method enter if it is already present"
    }

    val newRoot = AutomataNode()
    newRoot.outEdges.add(AutomataEdgeType.MethodEnter(MethodFormula.True) to automata.initialNode)

    return SemgrepRuleAutomata(
        automata.formulaManager,
        initialNodes = setOf(newRoot),
        hasMethodEnter = true,
        isDeterministic = automata.isDeterministic,
        hasEndEdges = automata.hasEndEdges,
    )
}

fun addPatternStartAndEnd(automata: SemgrepRuleAutomata, metaVarInfo: ResolvedMetaVarInfo): SemgrepRuleAutomata {
    check(!automata.hasMethodEnter && !automata.hasEndEdges)

    automata.initialNode.outEdges.add(AutomataEdgeType.PatternStart to automata.initialNode)
    traverse(automata) {
        if (it.accept) {
            it.outEdges.add(AutomataEdgeType.PatternEnd to it)
        }
    }

    return org.opentaint.semgrep.pattern.conversion.automata.operations.intersection(
        automata,
        patternBordersAutomata(automata.formulaManager),
        metaVarInfo
    )
}

fun addPatternStartAndEndOnEveryNode(automata: SemgrepRuleAutomata) {
    traverse(automata) {
        it.outEdges.add(AutomataEdgeType.PatternStart to it)
        it.outEdges.add(AutomataEdgeType.PatternEnd to it)
    }
}

private fun patternBordersAutomata(formulaManager: MethodFormulaManager): SemgrepRuleAutomata {
    val root = AutomataNode()
    val middleNode = AutomataNode()
    val terminalNode = AutomataNode()

    root.outEdges.add(AutomataEdgeType.PatternStart to middleNode)
    middleNode.outEdges.add(AutomataEdgeType.MethodCall(MethodFormula.True) to middleNode)
    middleNode.outEdges.add(AutomataEdgeType.PatternEnd to terminalNode)
    terminalNode.accept = true

    return SemgrepRuleAutomata(
        formulaManager,
        initialNodes = setOf(root),
        isDeterministic = true,
        hasMethodEnter = false,
        hasEndEdges = false,
    )
}

// TODO: optimize?
fun removePatternStartAndEnd(automata: SemgrepRuleAutomata) {
    var redirectedSomething = false
    traverse(automata) { node ->
        if (redirectedSomething) {
            return@traverse
        }

        val edgeToRedirect = node.outEdges.firstOrNull {
            it.first in setOf(AutomataEdgeType.PatternStart, AutomataEdgeType.PatternEnd)
        } ?: return@traverse

        redirectedSomething = true
        node.outEdges.remove(edgeToRedirect)

        edgeToRedirect.second.outEdges.forEach { edge ->
            node.outEdges.add(edge)
        }
    }

    // try to do this one more time
    if (redirectedSomething) {
        automata.isDeterministic = false
        removePatternStartAndEnd(automata)
        return
    }

    removeDeadNodes(automata)
}
