package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import java.util.ArrayDeque

/** A path filter runs before the concrete mark policy; the input annotations belong to the whole tails. */
internal fun filterFacts(m: ApManager, c: Facts, f: TypeFilter): Facts? {
    m.cancellation.checkpoint()
    return when (c) {
        is Reach -> c
        is FlowTree -> FlowAlgebra.filterPath(m, c.root, f.may)
            ?.let { if (it === c.root) c else c.withRoot(it) }
        is TaintTree -> {
            var result = m.taintAlg.filterPath(m, c.root, f.may)
            val policy = f.markPolicy
            if (result != null && policy != null) result = markPolicyOnElements(m, result, policy)
            result?.let { if (it === c.root) c else m.taintTree(c.base, c.layer, it, c.exclusion, c.anyTail) }
        }
    }
}

private class FilterState<P : TrieLeaf>(val node: TrieNode<P>, val filter: FactTypeChecker.FactApFilter) {
    private val hash = System.identityHashCode(node) * 31 + System.identityHashCode(filter)
    override fun hashCode(): Int = hash
    override fun equals(other: Any?): Boolean = other is FilterState<*> &&
        node === other.node && filter === other.filter
}

private class FilterFrame<P : TrieLeaf>(val state: FilterState<P>) {
    var next = 0
    var waiting: FilterState<P>? = null
    var changed = false
    val accessors = ArrayList<Int>()
    val children = ArrayList<TrieNode<P>>()

    fun add(index: Int, child: TrieNode<P>?) {
        val node = state.node
        if (child !== node.childData!![index]) changed = true
        if (child != null) {
            accessors += node.accessorData!![index]
            children += child
        }
    }
}

/** Evaluate each edge once per node/filter identity pair, caching removed subtrees as null. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.filterPath(
    m: ApManager, n: TrieNode<P>, may: FactTypeChecker.FactApFilter,
): TrieNode<P>? {
    m.cancellation.checkpoint()
    val initial = FilterState(n, may)
    val memo = HashMap<FilterState<P>, TrieNode<P>?>()
    val pending = ArrayDeque<FilterFrame<P>>()
    pending.addLast(FilterFrame(initial))
    while (pending.isNotEmpty()) {
        m.cancellation.checkpoint()
        val frame = pending.peekLast()
        val waiting = frame.waiting
        if (waiting != null) {
            check(memo.containsKey(waiting))
            frame.add(frame.next - 1, memo[waiting])
            frame.waiting = null
            continue
        }
        val current = frame.state.node
        val accessors = current.accessorData
        if (accessors != null && frame.next < accessors.size) {
            val index = frame.next++
            val child = current.childData!![index]
            val result = frame.state.filter.check(m.accessors.accessor(accessors[index]))
            m.cancellation.checkpoint()
            when (result) {
                FactTypeChecker.FilterResult.Accept -> frame.add(index, child)
                FactTypeChecker.FilterResult.Reject -> frame.add(index, null)
                is FactTypeChecker.FilterResult.FilterNext -> {
                    val state = FilterState(child, result.filter)
                    if (memo.containsKey(state)) frame.add(index, memo[state])
                    else {
                        frame.waiting = state
                        pending.addLast(FilterFrame(state))
                    }
                }
            }
            continue
        }
        memo[frame.state] = if (!frame.changed) current.takeIf { !it.isEmpty }
            else node(current.leaf, frame.accessors.toIntArray(), frame.children.toTypedArray()).takeIf { !it.isEmpty }
        pending.removeLast()
    }
    return memo[initial]
}

private class PolicyFrame(val node: TaintNode, val kept: TaintLeaves)

/** Only the root and its uninterrupted element chain have their payload marks filtered. */
private fun markPolicyOnElements(m: ApManager, n: TaintNode, policy: MarkPolicy): TaintNode? {
    val spine = ArrayList<PolicyFrame>()
    var current = n
    var level = 0
    while (true) {
        m.cancellation.checkpoint()
        val kept = m.taintLeaves(keepPolicyMarks(m, current.leaf.exact, policy, level),
            keepPolicyMarks(m, current.leaf.any, policy, level))
        spine += PolicyFrame(current, kept)
        val child = current.child(ELEMENT_ACCESSOR_IDX) ?: break
        current = child
        level = Math.addExact(level, 1)
    }
    var below: TaintNode? = null
    for (index in spine.indices.reversed()) {
        m.cancellation.checkpoint()
        val frame = spine[index]
        val oldChild = frame.node.child(ELEMENT_ACCESSOR_IDX)
        below = when {
            frame.kept === frame.node.leaf && below === oldChild -> frame.node
            oldChild == null -> m.taintAlg.withLeaf(frame.node, frame.kept)
            else -> m.taintAlg.replaceChild(frame.node, frame.kept, ELEMENT_ACCESSOR_IDX, below)
        }
    }
    return below
}

private fun keepPolicyMarks(m: ApManager, marks: MarkSet, policy: MarkPolicy, level: Int): MarkSet {
    if (marks.isEmpty) return marks
    val kept = ArrayList<Int>()
    for (id in marks.data) {
        m.cancellation.checkpoint()
        val atom = TaintMark(id)
        val keep = policy.keeps(atom, level)
        m.cancellation.checkpoint()
        if (keep) kept += id
    }
    return if (kept.size == marks.data.size) marks else m.intern(MarkSet(kept.toIntArray()))
}
