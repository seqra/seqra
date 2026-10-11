package org.opentaint.dataflow.bidi.ap

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.SoftReferenceManager
import java.lang.ref.Reference
import java.util.ArrayDeque
import java.util.IdentityHashMap

/** One owner per store. Soft cache loss changes sharing only. */
class TrieInterner<P : TrieLeaf>(private val refs: SoftReferenceManager, private val cancellation: Cancellation) {
    private class Table<P : TrieLeaf> {
        private val buckets = Long2ObjectOpenHashMap<Object2ObjectOpenCustomHashMap<TrieNode<P>, TrieNode<P>>>()
        fun intern(node: TrieNode<P>): TrieNode<P> = buckets.computeIfAbsent(node.hash) {
            Object2ObjectOpenCustomHashMap<TrieNode<P>, TrieNode<P>>(InternStrategy)
        }.putIfAbsent(node, node) ?: node
    }

    private class Frame<P : TrieLeaf>(val node: TrieNode<P>, var next: Int = 0)
    private var cache: Reference<Table<P>>? = null
    private var operationsBeforeIntern = INTERN_RATE
    private fun table(): Table<P> = cache?.get() ?: Table<P>().also { cache = refs.createRef(it) }

    fun intern(node: TrieNode<P>): TrieNode<P> {
        if (node.interned) return node
        val table = table()
        val memo = IdentityHashMap<TrieNode<P>, TrieNode<P>>()
        val stack = ArrayDeque<Frame<P>>()
        stack.addLast(Frame(node))
        while (stack.isNotEmpty()) {
            cancellation.checkpoint()
            val frame = stack.peekLast()
            val kids = frame.node.childData
            if (kids != null && frame.next < kids.size) {
                val child = kids[frame.next++]
                if (!child.interned && !memo.containsKey(child)) stack.addLast(Frame(child))
                continue
            }
            var out = kids
            if (kids != null) for (i in kids.indices) {
                val child = memo[kids[i]] ?: kids[i]
                if (child !== kids[i]) {
                    if (out === kids) out = kids.copyOf()
                    out!![i] = child
                }
            }
            memo[frame.node] = table.intern(TrieNode(frame.node.leaf, frame.node.accessorData, out, true))
            stack.removeLast()
        }
        return checkNotNull(memo[node])
    }

    fun internIfRequired(node: TrieNode<P>): TrieNode<P> = when {
        node.size >= SIZE_TO_FORCE_INTERN -> intern(node)
        --operationsBeforeIntern > 0 || node.size < MIN_SIZE_TO_INTERN -> node
        else -> { operationsBeforeIntern = INTERN_RATE; intern(node) }
    }

    companion object {
        const val MIN_SIZE_TO_INTERN = 100L
        const val SIZE_TO_FORCE_INTERN = 100_000L
        const val INTERN_RATE = 100
    }
}

/** Child identity is sound only after bottom-up interning in this table. */
private object InternStrategy : Hash.Strategy<TrieNode<*>> {
    override fun hashCode(node: TrieNode<*>?): Int {
        if (node == null) return 0
        var hash = node.hashCode()
        node.childData?.forEach { hash = hash * 31 + System.identityHashCode(it) }
        return hash
    }

    override fun equals(a: TrieNode<*>?, b: TrieNode<*>?): Boolean {
        if (a === b) return true
        if (a == null || b == null || a.hash != b.hash || a.leaf != b.leaf ||
            !a.accessorData.contentEquals(b.accessorData)) return false
        val x = a.childData ?: return b.childData == null
        val y = b.childData ?: return false
        return x.size == y.size && x.indices.all { x[it] === y[it] }
    }
}
