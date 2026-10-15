package org.opentaint.dataflow.bidi.store

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.walkPath
import org.opentaint.dataflow.util.Cancellation

/** Single-writer positional index. A value at two positions remains two entries. */
class PathTrie<V : Any>(private val cancellation: Cancellation? = null) {
    private class Node<V : Any> {
        val values = LinkedHashSet<V>(2)
        var children: Int2ObjectOpenHashMap<Node<V>>? = null
        fun child(accessor: Int): Node<V>? = children?.get(accessor)
        fun getOrCreate(accessor: Int): Node<V> {
            val map = children ?: Int2ObjectOpenHashMap<Node<V>>().also { children = it }
            return map.getOrPut(accessor) { Node() }
        }
    }
    private val roots = HashMap<AccessPathBase, Node<V>>(4)

    fun add(base: AccessPathBase, path: IntArray, value: V): Boolean {
        cancellation?.checkpoint()
        var node = roots.getOrPut(base) { Node() }
        for (accessor in path) {
            cancellation?.checkpoint()
            node = node.getOrCreate(accessor)
        }
        return node.values.add(value)
    }

    fun lookupPrefixes(base: AccessPathBase, path: IntArray): MutableList<V> {
        cancellation?.checkpoint()
        val out = ArrayList<V>()
        val root = roots[base] ?: return out
        walkPath(root, path, Node<V>::child) { _, node ->
            cancellation?.checkpoint()
            out += node.values
        }?.let { out += it.values }
        return out
    }

    fun lookupExtensions(base: AccessPathBase, path: IntArray): List<V> {
        cancellation?.checkpoint()
        val out = ArrayList<V>()
        find(base, path)?.let { collect(it, out, self = true) }
        return out
    }

    fun around(base: AccessPathBase, path: IntArray): List<V> =
        lookupPrefixes(base, path).also { out -> find(base, path)?.let { collect(it, out, self = false) } }

    /** Finite owner-local visit; the callback must not mutate this index or its owning directory. */
    fun visitAround(base: AccessPathBase, path: IntArray, visit: (V) -> Unit) {
        cancellation?.checkpoint()
        var node = roots[base] ?: return
        fun values(at: Node<V>) {
            for (value in at.values) {
                cancellation?.checkpoint()
                visit(value)
            }
        }
        for (accessor in path) {
            cancellation?.checkpoint()
            values(node)
            node = node.child(accessor) ?: return
        }
        val stack = ArrayDeque<Node<V>>()
        stack.addLast(node)
        while (stack.isNotEmpty()) {
            cancellation?.checkpoint()
            val at = stack.removeLast()
            values(at)
            at.children?.values?.forEach {
                cancellation?.checkpoint()
                stack.addLast(it)
            }
        }
    }

    fun all(): Sequence<V> = sequence {
        cancellation?.checkpoint()
        for (root in roots.values) {
            val out = ArrayList<V>()
            collect(root, out, self = true)
            for (value in out) {
                cancellation?.checkpoint()
                yield(value)
            }
        }
    }

    private fun find(base: AccessPathBase, path: IntArray): Node<V>? {
        cancellation?.checkpoint()
        return roots[base]?.let { root ->
            walkPath(root, path, Node<V>::child) { _, _ -> cancellation?.checkpoint() }
        }
    }

    private fun collect(node: Node<V>, out: MutableList<V>, self: Boolean) {
        cancellation?.checkpoint()
        val stack = ArrayDeque<Node<V>>()
        if (self) out += node.values
        node.children?.values?.let(stack::addAll)
        while (stack.isNotEmpty()) {
            cancellation?.checkpoint()
            val next = stack.removeLast()
            out += next.values
            next.children?.values?.let(stack::addAll)
        }
    }
}
