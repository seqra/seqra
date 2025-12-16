package org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.dataflow.util.any
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.semgrep.pattern.conversion.automata.AutomataBuilderCtx
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaSat
import org.opentaint.semgrep.pattern.conversion.taint.trySimplifyMethodFormula
import java.util.BitSet
import java.util.Collections
import java.util.IdentityHashMap

fun AutomataBuilderCtx.determinize(
    automata: SemgrepRuleAutomata,
    simplifyAutomata: Boolean = false
): SemgrepRuleAutomata {
    if (automata.isDeterministic) {
        return automata
    }

    if (simplifyAutomata) {
        simplifyAutomata(automata)
    }

    val nodeIndex = IdentityHashMap<AutomataNode, Int>()
    val nodeByIdx = arrayListOf<AutomataNode>()

    fun Int.node(): AutomataNode = nodeByIdx[this]
    fun AutomataNode.nodeId(): Int = nodeIndex.getOrPut(this) {
        nodeByIdx.add(this)
        nodeIndex.size
    }

    var hasMethodEnter = false
    var hasEndEdges = false

    val queue = mutableListOf<BitSet>()

    val newNodes = hashMapOf<BitSet, AutomataNode>()
    fun getOrCreateNewNode(nodeIds: BitSet): AutomataNode =
        newNodes.getOrPut(nodeIds) {
            queue.add(nodeIds)
            val accept = nodeIds.any { it.node().accept }
            AutomataNode().also { it.accept = accept }
        }

    val initialNodeSet = automata.initialNodes.toBitSet { it.nodeId() }
    val root = getOrCreateNewNode(initialNodeSet)

    while (queue.isNotEmpty()) {
        cancelation.check()

        val s = queue.removeFirst()
        val newNode = getOrCreateNewNode(s)

        val initialEdges = mutableListOf<Pair<AutomataEdgeType, AutomataNode>>()
        s.forEach { initialEdges.addAll(it.node().outEdges) }

        for (type in listOf(AutomataEdgeType.End, AutomataEdgeType.PatternStart, AutomataEdgeType.PatternEnd)) {
            val edgesWithoutFormula = initialEdges.filter { it.first == type }
            if (edgesWithoutFormula.isNotEmpty()) {
                hasEndEdges = type == AutomataEdgeType.End
                val nodes = edgesWithoutFormula.toBitSet { it.second.nodeId() }
                val toNode = getOrCreateNewNode(nodes)
                newNode.outEdges.add(type to toNode)
            }
        }

        determinizeSpecificEdgeType<AutomataEdgeType.MethodCall>(
            initialEdges, newNode, AutomataNode::nodeId, ::getOrCreateNewNode
        ) {
            AutomataEdgeType.MethodCall(it)
        }

        determinizeSpecificEdgeType<AutomataEdgeType.MethodEnter>(
            initialEdges, newNode, AutomataNode::nodeId, ::getOrCreateNewNode
        ) {
            hasMethodEnter = true
            AutomataEdgeType.MethodEnter(it)
        }
    }

    return SemgrepRuleAutomata(
        automata.formulaManager,
        initialNodes = setOf(root),
        isDeterministic = true,
        hasMethodEnter = hasMethodEnter,
        hasEndEdges = hasEndEdges,
    ).also {
        removeDeadNodes(it)
    }
}

private inline fun <reified Type : AutomataEdgeType.AutomataEdgeTypeWithFormula> AutomataBuilderCtx.determinizeSpecificEdgeType(
    initialEdges: List<Pair<AutomataEdgeType, AutomataNode>>,
    newNode: AutomataNode,
    nodeId: AutomataNode.() -> Int,
    createNewNode: (BitSet) -> AutomataNode,
    createEdge: (MethodFormula) -> Type,
) {
    val edgesOfThisType = initialEdges.mapNotNull { (type, to) ->
        (type as? Type)?.formula?.let { it to to }
    }.groupBy { it.first }.entries.toList()

    if (edgesOfThisType.isEmpty()) return

    val n = edgesOfThisType.size
    val out = hashMapOf<BitSet, MutableList<Int>>()

    if (n > 16) {
        TODO("Determinization failure: state explosion")
    }

    check(n < Int.SIZE_BITS) {
        "Determinization failed: too many formulas $n"
    }

    val edgeNodeSets = edgesOfThisType.map { (_, edges) ->
        edges.toBitSet { it.second.nodeId() }
    }

    for (i in 1..<(1 shl n)) {
        val toSet = BitSet()
        edgeNodeSets.forEachIndexed { index, nodeSet ->
            val take = (i and (1 shl index)) != 0
            if (!take) return@forEachIndexed
            toSet.or(nodeSet)
        }
        out.getOrPut(toSet, ::mutableListOf).add(i)
    }

    out.entries.forEach { (toSet, masks) ->
        val formulas = mutableListOf<MethodFormula>()
        masks.forEach inner@{ mask ->
            cancelation.check()

            val formulaLits = edgesOfThisType.mapIndexed { index, (formula, _) ->
                val takeNegation = (mask and (1 shl index)) == 0
                if (takeNegation) {
                    formula.complement()
                } else {
                    formula
                }
            }

            val formula = formulaManager.mkAnd(formulaLits)

            if (!methodFormulaSat(formulaManager, formula, metaVarInfo, cancelation)) {
                return@inner
            }

            formulas.add(formula)
        }

        if (formulas.isEmpty()) {
            return@forEach
        }

        val singleFormula = formulaManager.mkOr(formulas)

        val toNode = createNewNode(toSet)
        newNode.outEdges.add(createEdge(singleFormula) to toNode)
    }
}

private fun AutomataBuilderCtx.simplifyAutomata(automata: SemgrepRuleAutomata) {
    val visited = Collections.newSetFromMap<AutomataNode>(IdentityHashMap())

    val unprocessed = mutableListOf<AutomataNode>()
    unprocessed.addAll(automata.initialNodes)

    while (unprocessed.isNotEmpty()) {
        val node = unprocessed.removeLast()
        if (!visited.add(node)) continue

        val iter = node.outEdges.listIterator()
        while (iter.hasNext()) {
            val (edge, nextState) = iter.next()
            unprocessed.add(nextState)

            val simplifiedEdge = simplifyEdge(automata.formulaManager, edge)
            iter.set(simplifiedEdge to nextState)
        }
    }
}

private fun AutomataBuilderCtx.simplifyEdge(
    manager: MethodFormulaManager,
    edge: AutomataEdgeType
): AutomataEdgeType {
    if (edge !is AutomataEdgeType.AutomataEdgeTypeWithFormula) return edge

    val simplifiedFormula = trySimplifyMethodFormula(manager, edge.formula, metaVarInfo, cancelation)

    return when (edge) {
        is AutomataEdgeType.MethodCall -> AutomataEdgeType.MethodCall(simplifiedFormula)
        is AutomataEdgeType.MethodEnter -> AutomataEdgeType.MethodEnter(simplifiedFormula)
    }
}
