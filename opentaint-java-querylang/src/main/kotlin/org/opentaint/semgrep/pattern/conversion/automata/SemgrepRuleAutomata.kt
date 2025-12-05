package org.opentaint.semgrep.pattern.conversion.automata

class SemgrepRuleAutomata(
    val formulaManager: MethodFormulaManager,
    val initialNodes: Set<AutomataNode>,
    var isDeterministic: Boolean,
    var hasMethodEnter: Boolean,
    var hasEndEdges: Boolean,
    var deadNode: AutomataNode = createDeadNode()
) {
    val initialNode: AutomataNode
        get() = initialNodes.single()

    fun deepCopy(): SemgrepRuleAutomata {
        val (newRoot, newNodes) = initialNode.deepCopy()
        val newDeadNode = newNodes[deadNode] ?: createDeadNode()

        return SemgrepRuleAutomata(
            formulaManager,
            initialNodes = setOf(newRoot),
            isDeterministic,
            hasMethodEnter,
            hasEndEdges,
            deadNode = newDeadNode
        )
    }

    companion object {
        fun createDeadNode(): AutomataNode = AutomataNode().also {
            it.outEdges.add(AutomataEdgeType.MethodCall(MethodFormula.True) to it)
        }
    }
}
