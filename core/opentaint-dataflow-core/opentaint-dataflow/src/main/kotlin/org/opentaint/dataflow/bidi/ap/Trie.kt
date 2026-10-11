package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

interface TrieLeaf {
    val isEmpty: Boolean
    val hasAny: Boolean
}

enum class FlowLeaf : TrieLeaf {
    NONE, LEAF;
    override val isEmpty: Boolean get() = this == NONE
    override val hasAny: Boolean get() = false
}

class TaintLeaves internal constructor(val exact: MarkSet, val any: MarkSet) : TrieLeaf {
    private val hash = exact.hashCode() * 31 + any.hashCode()
    override val isEmpty: Boolean get() = exact.isEmpty && any.isEmpty
    override val hasAny: Boolean get() = !any.isEmpty
    override fun equals(other: Any?): Boolean = this === other ||
        (other is TaintLeaves && hash == other.hash && exact == other.exact && any == other.any)
    override fun hashCode(): Int = hash
    companion object { @JvmField val EMPTY = TaintLeaves(MarkSet.EMPTY, MarkSet.EMPTY) }
}

/** Immutable sorted children. Public arrays are snapshots of the owned content. */
class TrieNode<P : TrieLeaf> internal constructor(
    @JvmField val leaf: P,
    accessors: IntArray?,
    children: Array<TrieNode<P>>?,
    @JvmField val interned: Boolean,
) {
    internal val accessorData: IntArray? = accessors?.takeIf { it.isNotEmpty() }?.copyOf()
    internal val childData: Array<TrieNode<P>>? = children?.takeIf { it.isNotEmpty() }?.copyOf()
    val accessors: IntArray? get() = accessorData?.copyOf()
    val children: Array<TrieNode<P>>? get() = childData?.copyOf()
    @JvmField val hash: Long
    @JvmField val boundedDepth: Int
    @JvmField val hasAny: Boolean
    @JvmField val size: Long
    @JvmField internal var allLeaves: P? = null

    init {
        check((accessorData == null) == (childData == null)) { "Child arrays must be aligned" }
        check(accessorData == null || accessorData.size == childData!!.size) { "Child arrays must have equal sizes" }
        var h = leaf.hashCode().toLong()
        var depth = 0
        var any = leaf.hasAny
        var count = 1L
        if (accessorData != null) for (i in accessorData.indices) {
            val accessor = accessorData[i]
            check(accessor.isPathAccessor()) { "W5: a trie edge needs a path accessor" }
            check(i == 0 || accessorData[i - 1] < accessor) { "Trie accessors must be sorted and unique" }
            val child = childData!![i]
            check(!child.isEmpty) { "A child subtree must be nonempty" }
            count = Math.addExact(count, child.size)
            h += (child.hash * 31 + accessor) shl 5
            depth = maxOf(depth, Math.addExact(child.boundedDepth, if (accessor.isCounted()) 1 else 0))
            any = any || child.hasAny
        }
        hash = h
        boundedDepth = depth
        hasAny = any
        size = count
    }

    val isEmpty: Boolean get() = leaf.isEmpty && accessorData == null
    fun child(accessor: AccessorIdx): TrieNode<P>? {
        val index = accessorData?.binarySearch(accessor) ?: return null
        return if (index < 0) null else childData!![index]
    }

    override fun hashCode(): Int = hash.toInt()

    /** Shared DAGs and deep paths compare by value without recursive calls. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrieNode<*>) return false
        val pending = ArrayDeque<Pair<TrieNode<*>, TrieNode<*>>>()
        val seen = IdentityHashMap<TrieNode<*>, IdentityHashMap<TrieNode<*>, Boolean>>()
        pending.addLast(this to other)
        while (pending.isNotEmpty()) {
            val (a, b) = pending.removeLast()
            if (a === b) continue
            if (a.hash != b.hash || a.size != b.size || a.boundedDepth != b.boundedDepth ||
                a.hasAny != b.hasAny || a.leaf != b.leaf || !a.accessorData.contentEquals(b.accessorData)) return false
            if (seen.getOrPut(a) { IdentityHashMap() }.put(b, true) != null) continue
            val x = a.childData
            val y = b.childData
            if (x == null || y == null) {
                if (x !== y) return false
                continue
            }
            if (x.size != y.size) return false
            for (i in x.indices) pending.addLast(x[i] to y[i])
        }
        return true
    }
}

typealias FlowNode = TrieNode<FlowLeaf>
typealias TaintNode = TrieNode<TaintLeaves>

abstract class LeafAlgebra<P : TrieLeaf> {
    abstract val empty: P
    abstract fun union(a: P, b: P): P
    abstract fun minus(a: P, b: P): P
    abstract fun below(p: P): P
    abstract fun asAny(p: P): P
    abstract fun intern(p: P): P
    private val leafNodes = ConcurrentHashMap<P, TrieNode<P>>()

    fun leafNode(p: P): TrieNode<P> = leafNodes.computeIfAbsent(intern(p)) { TrieNode(it, null, null, true) }

    fun node(p: P, accessors: IntArray?, children: Array<TrieNode<P>>?): TrieNode<P> {
        val a = accessors?.takeIf { it.isNotEmpty() }
        val c = children?.takeIf { it.isNotEmpty() }
        check((a == null) == (c == null)) { "Child arrays must be aligned" }
        return if (a == null) leafNode(p) else TrieNode(intern(p), a, c, false)
    }
}

object FlowAlgebra : LeafAlgebra<FlowLeaf>() {
    override val empty: FlowLeaf get() = FlowLeaf.NONE
    override fun union(a: FlowLeaf, b: FlowLeaf) = if (!a.isEmpty || !b.isEmpty) FlowLeaf.LEAF else FlowLeaf.NONE
    override fun minus(a: FlowLeaf, b: FlowLeaf) = if (!a.isEmpty && b.isEmpty) FlowLeaf.LEAF else FlowLeaf.NONE
    override fun below(p: FlowLeaf) = p
    override fun asAny(p: FlowLeaf) = p
    override fun intern(p: FlowLeaf) = p
    val LEAF_NODE: FlowNode get() = leafNode(FlowLeaf.LEAF)
}

class TaintAlgebra(private val m: ApManager) : LeafAlgebra<TaintLeaves>() {
    private val table = ConcurrentHashMap<TaintLeaves, TaintLeaves>()
    override val empty: TaintLeaves get() = TaintLeaves.EMPTY
    fun leaves(exact: MarkSet, any: MarkSet): TaintLeaves = intern(TaintLeaves(m.intern(exact), m.intern(any)))
    override fun intern(p: TaintLeaves): TaintLeaves =
        if (p.isEmpty) TaintLeaves.EMPTY else table.putIfAbsent(p, p) ?: p
    override fun union(a: TaintLeaves, b: TaintLeaves): TaintLeaves =
        if (a === b) a else leaves(a.exact + b.exact, a.any + b.any)
    override fun minus(a: TaintLeaves, b: TaintLeaves) = leaves(a.exact - b.exact, a.any - b.any)
    override fun below(p: TaintLeaves) = leaves(p.any, p.any)
    fun belowNormal(p: TaintLeaves) = leaves(MarkSet.EMPTY, p.any)
    fun belowIn(layer: Layer): (TaintLeaves) -> TaintLeaves = if (layer == Layer.NORMAL) this::belowNormal else this::below
    override fun asAny(p: TaintLeaves) = leaves(MarkSet.EMPTY, p.exact + p.any)
}
