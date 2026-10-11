package org.opentaint.dataflow.bidi.ap

import java.util.ArrayDeque
import java.util.IdentityHashMap

/** Cut before the next counted accessor after the positive public budget is exhausted. */
internal fun limitFacts(m: ApManager, c: Facts, fieldLimit: Int, out: ApOut) {
    require(fieldLimit >= 1) { "The field limit must be positive" }
    m.cancellation.checkpoint()
    when (c) {
        is Reach -> out.result(c)
        is FlowTree -> limitTrie(m, FlowAlgebra, c.root, fieldLimit,
            { out.result(if (it === c.root) c else c.withRoot(it)) },
            { out.result(m.flowTree(c.base, Layer.DEMAND, ExclusionSet.Empty, c.markExclusion, it)) })
        is TaintTree -> limitTrie(m, m.taintAlg, c.root, fieldLimit,
            { out.result(if (it === c.root) c else c.withRoot(it)) },
            { out.result(m.taintTree(c.base, Layer.DEMAND, it)) })
    }
}

private inline fun <P : TrieLeaf> limitTrie(
    m: ApManager, alg: LeafAlgebra<P>, root: TrieNode<P>, budget: Int,
    kept: (TrieNode<P>) -> Unit, cut: (TrieNode<P>) -> Unit,
) {
    if (root.boundedDepth <= budget) { kept(root); return }
    val (within, overflow) = FieldLimitCut(alg, m).keep(root, budget)
    m.cancellation.checkpoint()
    within?.let(kept)
    overflow?.let(cut)
}

/** A subtree can occur at different counted depths, so sharing is memoized by node identity and remaining budget. */
internal class FieldLimitCut<P : TrieLeaf>(private val alg: LeafAlgebra<P>, private val m: ApManager) {
    private val memo = HashMap<Int, IdentityHashMap<TrieNode<P>, Pair<TrieNode<P>?, TrieNode<P>?>>>()

    private class Frame<P : TrieLeaf>(val node: TrieNode<P>, val budget: Int, empty: P) {
        var next = 0
        var cutHere = empty
        val keptAccessors = ArrayList<Int>()
        val keptChildren = ArrayList<TrieNode<P>>()
        val cutAccessors = ArrayList<Int>()
        val cutChildren = ArrayList<TrieNode<P>>()
    }

    /** Both results are rooted at n. Zero is a valid internal remaining budget; class edges do not consume it. */
    fun keep(n: TrieNode<P>, budget: Int): Pair<TrieNode<P>?, TrieNode<P>?> {
        require(budget >= 0) { "The remaining field budget must be nonnegative" }
        m.cancellation.checkpoint()
        if (n.boundedDepth <= budget) return n to null
        memo[budget]?.get(n)?.let { return it }
        val stack = ArrayDeque<Frame<P>>()
        stack.addLast(Frame(n, budget, alg.empty))
        while (stack.isNotEmpty()) {
            m.cancellation.checkpoint()
            val frame = stack.peekLast()
            val accessors = frame.node.accessorData
            if (accessors != null && frame.next < accessors.size) {
                val accessor = accessors[frame.next]
                val child = frame.node.childData!![frame.next]
                val counted = accessor.isCounted()
                if (counted && frame.budget == 0) {
                    frame.cutHere = alg.union(frame.cutHere, alg.asAny(alg.foldAll(child, m.cancellation)))
                    frame.next++
                    continue
                }
                val childBudget = frame.budget - if (counted) 1 else 0
                val result = if (child.boundedDepth <= childBudget) child to null else memo[childBudget]?.get(child)
                if (result == null) {
                    stack.addLast(Frame(child, childBudget, alg.empty))
                    continue
                }
                result.first?.let {
                    frame.keptAccessors.add(accessor)
                    frame.keptChildren.add(it)
                }
                result.second?.let {
                    frame.cutAccessors.add(accessor)
                    frame.cutChildren.add(it)
                }
                frame.next++
                continue
            }
            val kept = alg.node(frame.node.leaf, frame.keptAccessors.toIntArray(), frame.keptChildren.toTypedArray())
                .takeIf { !it.isEmpty }
            val cut = alg.node(frame.cutHere, frame.cutAccessors.toIntArray(), frame.cutChildren.toTypedArray())
                .takeIf { !it.isEmpty }
            memo.getOrPut(frame.budget) { IdentityHashMap() }[frame.node] = kept to cut
            stack.removeLast()
        }
        return memo.getValue(budget).getValue(n)
    }
}
