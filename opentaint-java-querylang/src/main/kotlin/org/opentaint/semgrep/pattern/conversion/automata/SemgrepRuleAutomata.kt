package org.opentaint.semgrep.pattern.conversion.automata

class SemgrepRuleAutomata(
    val formulaManager: MethodFormulaManager,
    val initialNodes: Set<AutomataNode>,
    var params: Params,
    var deadNode: AutomataNode = createDeadNode()
) {
    data class Params(
        val isDeterministic: Boolean,
        val hasMethodEnter: Boolean,
        val hasMethodExit: Boolean,
        val hasEndEdges: Boolean,
    )

    val initialNode: AutomataNode
        get() = initialNodes.single()

    fun deepCopy(): SemgrepRuleAutomata {
        val (newRoot, newNodes) = initialNode.deepCopy()
        val newDeadNode = newNodes[deadNode] ?: createDeadNode()

        return SemgrepRuleAutomata(
            formulaManager,
            initialNodes = setOf(newRoot),
            params = params,
            deadNode = newDeadNode
        )
    }

    companion object {
        fun createDeadNode(): AutomataNode = AutomataNode().also {
            it.outEdges.add(AutomataEdgeType.MethodCall(MethodFormula.True) to it)
            it.outEdges.add(AutomataEdgeType.MethodExit(MethodFormula.True) to it)
        }
    }
}
