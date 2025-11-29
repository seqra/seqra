package org.opentaint.semgrep.pattern.conversion.automata

class SemgrepRuleAutomata(
    val formulaManager: MethodFormulaManager,
    val initialNodes: Set<AutomataNode>,
    var isDeterministic: Boolean,
    var hasMethodEnter: Boolean,
    var hasEndEdges: Boolean,
) {
    val initialNode: AutomataNode
        get() = initialNodes.single()

    fun deepCopy(): SemgrepRuleAutomata {
        val newRoot = initialNode.deepCopy()
        return SemgrepRuleAutomata(
            formulaManager,
            initialNodes = setOf(newRoot),
            isDeterministic,
            hasMethodEnter,
            hasEndEdges,
        )
    }
}
