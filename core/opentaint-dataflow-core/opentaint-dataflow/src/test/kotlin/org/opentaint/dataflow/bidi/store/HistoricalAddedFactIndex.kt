package org.opentaint.dataflow.bidi.store

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.PathNode
import org.opentaint.dataflow.util.Cancellation

/** Test-only historical raw-link positional directory. Append-only lanes make paused cursors safe through rehashes. */
internal class AddedFactIndex(private val cancellation: Cancellation) {
    class Node {
        val seen = IntOpenHashSet()
        val values = LinkHandleLane()
        val children = Int2ObjectOpenHashMap<Node>()
        val childOrder = ArrayList<Node>()
        fun child(accessor: Int): Node? = children[accessor]
        fun childOrAdd(accessor: Int): Node = children.getOrPut(accessor) {
            Node().also(childOrder::add)
        }
    }
    private val roots = HashMap<AccessPathBase, Node>()

    fun add(base: AccessPathBase, path: IntArray, handle: Int) {
        cancellation.checkpoint()
        var node = roots.getOrPut(base) { Node() }
        for (accessor in path) { cancellation.checkpoint(); node = node.childOrAdd(accessor) }
        if (node.seen.add(handle)) node.values.add(handle)
    }

    fun around(base: AccessPathBase, path: PathNode?): Cursor = Cursor(roots[base], path, cancellation)

    internal class Cursor(root: Node?, path: PathNode?, private val cancellation: Cancellation) {
        private class Frame(val node: Node) {
            var value = 0
            val valueBound = node.values.size
            var child = 0
            val childBound = node.childOrder.size
        }
        private var prefixNode = root
        private var path = path
        private var prefixValue = 0
        private var prefixBound = root?.values?.size ?: 0
        private var prefixes = true
        private val stack = ArrayDeque<Frame>()
        val isComplete: Boolean get() = !prefixes && stack.isEmpty()

        fun step(budget: Int, visit: (Int) -> Unit): Int {
            require(budget > 0)
            var used = 0
            while (used < budget && !isComplete) {
                cancellation.checkpoint()
                if (prefixes) {
                    val node = prefixNode
                    if (node == null) { prefixes = false; continue }
                    if (prefixValue < prefixBound) { visit(node.values[prefixValue++]); used++ }
                    else {
                        val next = path
                        if (next == null) {
                            // The tip's values were visited above. Only descend from it now.
                            stack.addLast(Frame(node).also { it.value = it.valueBound })
                            prefixes = false
                        } else {
                            prefixNode = node.child(next.accessor)
                            path = next.next
                            prefixValue = 0
                            prefixBound = prefixNode?.values?.size ?: 0
                            used++
                        }
                    }
                } else {
                    val frame = stack.last()
                    if (frame.value < frame.valueBound) { visit(frame.node.values[frame.value++]); used++ }
                    else if (frame.child < frame.childBound) {
                        stack.addLast(Frame(frame.node.childOrder[frame.child++]))
                        used++
                    } else { stack.removeLast(); used++ }
                }
            }
            return used
        }
    }
}

/** Existing raw-link handles keep their append-only lane after request indexing is removed. */
internal class LinkHandleLane {
    private var data = IntArray(0)
    var size = 0
        private set
    operator fun get(index: Int): Int = data[index]
    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(
            if (data.isEmpty()) 2 else Math.addExact(data.size, maxOf(1, data.size / 2)))
        data[size++] = value
    }
}
