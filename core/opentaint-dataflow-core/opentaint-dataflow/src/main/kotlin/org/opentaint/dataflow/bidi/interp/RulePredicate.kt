package org.opentaint.dataflow.bidi.interp

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.ap.MarkTable
import org.opentaint.dataflow.util.Cancellation

/** An owned positive NNF DAG. Node numbers are deterministic postorder ordinals. */
class RulePredicate private constructor(
    val root: Int,
    private val tags: ByteArray,
    private val offsets: IntArray,
    private val children: IntArray,
    private val leafAt: IntArray,
    val leaves: List<Pattern>,
    private val leafNodes: IntArray,
    private val parentOffsets: IntArray,
    private val parents: IntArray,
    private val unary: BooleanArray,
    private val multi: BooleanArray,
    cancellation: Cancellation,
) {
    enum class Kind { FALSE, TRUE, LEAF, AND, OR }

    val nodeCount: Int get() = tags.size
    val childCount: Int get() = children.size
    val isTrue: Boolean get() = kind(root) == Kind.TRUE
    val isFalse: Boolean get() = kind(root) == Kind.FALSE
    fun kind(node: Int): Kind = Kind.entries[tags[node].toInt()]
    fun leafOrdinal(node: Int): Int = leafAt[node]
    fun leafNode(leaf: Int): Int = leafNodes[leaf]
    fun isUnary(leaf: Int): Boolean = unary[leaf]
    fun isMulti(leaf: Int): Boolean = multi[leaf]
    fun childrenOf(node: Int, visit: (Int) -> Unit) {
        for (i in offsets[node] until offsets[node + 1]) visit(children[i])
    }
    fun parentsOf(node: Int, visit: (Int) -> Unit) {
        for (i in parentOffsets[node] until parentOffsets[node + 1]) visit(parents[i])
    }

    /** All ordinal vectors are private, detached primitive arrays. Mark runs use binary search. */
    private class BaseLeaves(val all: IntArray, val marks: IntArray, val offsets: IntArray, val marked: IntArray)
    private class IndexBuilder {
        val all = IntArrayList()
        val marks = Int2ObjectOpenHashMap<IntArrayList>()
    }
    private val byBase: Map<AccessPathBase, BaseLeaves> = buildMap {
        val scratch = LinkedHashMap<AccessPathBase, IndexBuilder>()
        for (ordinal in leaves.indices) {
            cancellation.checkpoint()
            val fact = leaves[ordinal].fact
            val builder = scratch.getOrPut(fact.base) { IndexBuilder() }
            builder.all.add(ordinal)
            val mark = (fact.mark as MarkSlot.Concrete).mark.id
            builder.marks.getOrPut(mark) { IntArrayList() }.add(ordinal)
        }
        for ((base, builder) in scratch) {
            cancellation.checkpoint()
            val marks = builder.marks.keys.toIntArray().also { it.sort() }
            val offsets = IntArray(marks.size + 1)
            val marked = IntArray(builder.all.size)
            var cursor = 0
            for (index in marks.indices) {
                cancellation.checkpoint()
                offsets[index] = cursor
                val ordinals = checkNotNull(builder.marks[marks[index]])
                for (position in 0 until ordinals.size) {
                    cancellation.checkpoint()
                    marked[cursor++] = ordinals.getInt(position)
                }
            }
            offsets[marks.size] = cursor
            put(base, BaseLeaves(builder.all.toIntArray(), marks, offsets, marked))
        }
    }

    /** FLOW has no mark and must visit every read on its base to produce requests. */
    fun matchingLeaves(base: AccessPathBase, mark: TaintMark?, visit: (Int, Pattern) -> Unit) {
        val index = byBase[base] ?: return
        if (mark == null) {
            for (ordinal in index.all) visit(ordinal, leaves[ordinal])
            return
        }
        val run = index.marks.binarySearch(mark.id)
        if (run < 0) return
        for (position in index.offsets[run] until index.offsets[run + 1]) {
            val ordinal = index.marked[position]
            visit(ordinal, leaves[ordinal])
        }
    }

    /** Compiler scratch is discarded after build. No branch or action product is built. */
    @Suppress("UNUSED_PARAMETER")
    class Builder(private val cancellation: Cancellation, catalog: MarkTable? = null) {
        private data class Node(val kind: Kind, val children: List<Int>, val leaf: Pattern? = null)
        private val nodes = ArrayList<Node>()
        private val canonical = HashMap<Node, Int>()
        val falseNode = intern(Node(Kind.FALSE, emptyList()))
        val trueNode = intern(Node(Kind.TRUE, emptyList()))

        private fun intern(node: Node): Int {
            cancellation.checkpoint()
            return canonical.getOrPut(node) { nodes.size.also { nodes += node } }
        }

        fun leaf(pattern: Pattern): Int {
            require(pattern.fact.mark is MarkSlot.Concrete)
            val owned = pattern.copy(fact = pattern.fact.copy(path = ownedList(pattern.fact.path)))
            return intern(Node(Kind.LEAF, emptyList(), owned))
        }

        fun and(children: List<Int>): Int = junction(Kind.AND, children)
        fun or(children: List<Int>): Int = junction(Kind.OR, children)

        private fun junction(kind: Kind, children: List<Int>): Int {
            cancellation.checkpoint()
            require(children.all { it in nodes.indices })
            val identity = if (kind == Kind.AND) trueNode else falseNode
            val absorbing = if (kind == Kind.AND) falseNode else trueNode
            if (absorbing in children) return absorbing
            val live = children.filter { it != identity }.distinct()
            return when (live.size) {
                0 -> identity
                1 -> live[0]
                else -> intern(Node(kind, ownedList(live)))
            }
        }

        fun build(root: Int): RulePredicate {
            require(root in nodes.indices)
            val active = BooleanArray(nodes.size)
            val work = ArrayDeque<Int>()
            work.add(root)
            while (work.isNotEmpty()) {
                cancellation.checkpoint()
                val node = work.removeLast()
                if (active[node]) continue
                active[node] = true
                nodes[node].children.forEach(work::addLast)
            }
            val remap = IntArray(nodes.size) { -1 }
            var count = 0
            for (i in nodes.indices) if (active[i]) remap[i] = count++
            val tags = ByteArray(count)
            val offsets = IntArray(count + 1)
            val leafAt = IntArray(count) { -1 }
            val children = ArrayList<Int>()
            val leaves = ArrayList<Pattern>()
            val leafNodes = ArrayList<Int>()
            val atoms = arrayOfNulls<Set<Int>>(count)
            val singletons = arrayOfNulls<Set<Int>>(count)
            val multi = HashSet<Int>()
            for (old in nodes.indices) {
                if (!active[old]) continue
                cancellation.checkpoint()
                val id = remap[old]
                val node = nodes[old]
                tags[id] = node.kind.ordinal.toByte()
                offsets[id] = children.size
                val childIds = node.children.map { remap[it] }
                children.addAll(childIds)
                when (node.kind) {
                    Kind.FALSE, Kind.TRUE -> {
                        atoms[id] = emptySet(); singletons[id] = emptySet()
                    }
                    Kind.LEAF -> {
                        val ordinal = leaves.size
                        leafAt[id] = ordinal
                        leaves += checkNotNull(node.leaf)
                        leafNodes += id
                        atoms[id] = setOf(ordinal); singletons[id] = setOf(ordinal)
                    }
                    Kind.AND, Kind.OR -> {
                        val all = childIds.flatMapTo(LinkedHashSet()) { checkNotNull(atoms[it]) }
                        atoms[id] = all
                        singletons[id] = if (node.kind == Kind.OR)
                            childIds.flatMapTo(LinkedHashSet()) { checkNotNull(singletons[it]) }
                        else childIds.map { checkNotNull(singletons[it]) }.reduce { a, b -> a.intersect(b) }
                        // With positive live children, all atoms of an AND can take part in a
                        // proof with another distinct atom. Repeated canonical leaves count once.
                        if (node.kind == Kind.AND && all.size >= 2) multi.addAll(all)
                    }
                }
            }
            offsets[count] = children.size
            val parentsCount = IntArray(count)
            for (child in children) parentsCount[child]++
            val parentOffsets = IntArray(count + 1)
            for (i in 0 until count) parentOffsets[i + 1] = parentOffsets[i] + parentsCount[i]
            val cursors = parentOffsets.copyOf()
            val parents = IntArray(children.size)
            for (node in 0 until count) for (i in offsets[node] until offsets[node + 1]) {
                cancellation.checkpoint()
                parents[cursors[children[i]]++] = node
            }
            val unaryFlags = BooleanArray(leaves.size)
            for (leaf in checkNotNull(singletons[remap[root]])) unaryFlags[leaf] = true
            val multiFlags = BooleanArray(leaves.size)
            for (leaf in multi) multiFlags[leaf] = true
            return RulePredicate(remap[root], tags, offsets, children.toIntArray(), leafAt,
                ownedList(leaves), leafNodes.toIntArray(), parentOffsets, parents,
                unaryFlags, multiFlags, cancellation)
        }
    }
}
