package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata

fun acceptIfCurrentAutomataAcceptsSuffix(automata: SemgrepRuleAutomata) {
    check(!automata.hasMethodEnter)

    automata.initialNode.outEdges.add(AutomataEdgeType.MethodCall(MethodFormula.True) to automata.initialNode)
    automata.isDeterministic = false
}
