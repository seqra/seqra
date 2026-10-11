package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.dataflow.util.SoftReferenceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Shared canonical keys. Each store will own its trie-node cache. */
class ApManager(val cancellation: Cancellation, refManager: RefManager = RefManager()) {
    val softRefs: SoftReferenceManager = refManager.softRefManager("bidi")
    val accessors = AccessorTable()
    val marks = MarkTable()
    val flowAlg: FlowAlgebra get() = FlowAlgebra
    val taintAlg = TaintAlgebra(this)

    private val markSets = ConcurrentHashMap<MarkSet, MarkSet>()
    private val exclusions = ConcurrentHashMap<ExclusionSet, ExclusionSet>()
    private val paths = ConcurrentHashMap<PathKey, PathNode>()
    private val initials = ConcurrentHashMap<InitialKey, InitialAp>()
    private val premiseSets = ConcurrentHashMap<List<InitialAp>, PremiseSet>()
    private val nextId = AtomicInteger()

    /** The suffix is canonical in this manager. Its identity avoids structural-hash clusters. */
    private class PathKey(val accessor: AccessorIdx, val next: PathNode?) {
        private val hash = accessor * 31 + System.identityHashCode(next)
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = other is PathKey && accessor == other.accessor && next === other.next
    }

    private data class InitialKey(
        val base: AccessPathBase,
        val path: PathNode?,
        val tail: Tail,
        val exclusion: ExclusionSet,
        val mark: MarkSlot,
    )

    fun intern(set: MarkSet): MarkSet = if (set.isEmpty) MarkSet.EMPTY else markSets.putIfAbsent(set, set) ?: set
    fun intern(exclusion: ExclusionSet): ExclusionSet =
        if (exclusion == ExclusionSet.Empty) exclusion else exclusions.putIfAbsent(exclusion, exclusion) ?: exclusion
    fun markSetOf(mark: TaintMark): MarkSet = intern(MarkSet(intArrayOf(mark.id)))
    fun path(accessor: AccessorIdx, next: PathNode?): PathNode {
        check(next == null || next.manager === this) { "A path suffix must use this AP manager" }
        return paths.computeIfAbsent(PathKey(accessor, next)) { PathNode(it.accessor, it.next, this) }
    }

    fun path(path: List<AccessorIdx>): PathNode? {
        var node: PathNode? = null
        for (index in path.indices.reversed()) {
            if (index and 255 == 0) cancellation.checkpoint()
            node = path(path[index], node)
        }
        return node
    }

    fun path(path: IntArray): PathNode? {
        var node: PathNode? = null
        for (index in path.indices.reversed()) {
            if (index and 255 == 0) cancellation.checkpoint()
            node = path(path[index], node)
        }
        return node
    }

    fun position(path: IntArray): PathNode = checkNotNull(path(path.copyOf(minOf(2, path.size))))

    fun initial(base: AccessPathBase, path: PathNode?, tail: Tail, exclusion: ExclusionSet, mark: MarkSlot): InitialAp {
        check(path == null || path.manager === this) { "An initial path must use this AP manager" }
        return initials.computeIfAbsent(InitialKey(base, path, tail, intern(exclusion), mark)) { key ->
            InitialAp(key.base, key.path, key.tail, key.exclusion, key.mark, nextId.getAndIncrement(), this)
        }
    }

    fun initial(pattern: Pattern): InitialAp =
        initial(pattern.fact.base, path(pattern.fact.path), pattern.fact.tail, pattern.exclusion, pattern.fact.mark)

    fun premiseOf(members: Collection<InitialAp>): PremiseKey {
        check(members.isNotEmpty()) { "A premise set must be nonempty" }
        check(members.all { it.manager === this }) { "All premises must use this AP manager" }
        val sorted = members.filter { !it.isZero }.distinct().sortedBy { it.id }
        return when (sorted.size) {
            0 -> zero
            1 -> sorted[0]
            else -> premiseSets.computeIfAbsent(sorted) { PremiseSet(it.toTypedArray()) }
        }
    }

    fun union(a: PremiseKey, b: PremiseKey): PremiseKey {
        a.forEachMember { check(it.manager === this) { "All premises must use this AP manager" } }
        b.forEachMember { check(it.manager === this) { "All premises must use this AP manager" } }
        return if (a === b) a else premiseOf(a.members + b.members)
    }

    val zero: InitialAp = initial(AccessPathBase.Zero, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(TaintMark.ZERO))

    fun flowTree(base: AccessPathBase, layer: Layer, exclusion: ExclusionSet, mx: MarkSet, root: FlowNode): FlowTree =
        FlowTree(base, layer, if (layer == Layer.DEMAND) ExclusionSet.Empty else intern(exclusion), intern(mx), root)

    fun taintTree(base: AccessPathBase, layer: Layer, root: TaintNode, exclusion: ExclusionSet = ExclusionSet.Empty): TaintTree =
        TaintTree(base, layer, root, if (layer == Layer.DEMAND || !root.hasAny) ExclusionSet.Empty else intern(exclusion))

    fun taintLeaves(exact: MarkSet, any: MarkSet): TaintLeaves = taintAlg.leaves(exact, any)
    fun newInterners(): StoreInterners = StoreInterners(softRefs, cancellation)

    /** Build one normalized typed leaf. Raw abstract exact values remain in the reference only. */
    fun factsOf(f: PathFact, exclusion: ExclusionSet, layer: Layer): Facts {
        if (f.base == AccessPathBase.Zero) {
            check(f.path.isEmpty() && f.tail == Tail.EXACT && exclusion == ExclusionSet.Empty &&
                f.mark == MarkSlot.Concrete(TaintMark.ZERO)) { "The zero base has only the canonical zero fact" }
            return Reach.of(layer)
        }
        val c = normalize(f, exclusion, layer == Layer.DEMAND)
        val resultLayer = if (c.demand) Layer.DEMAND else Layer.NORMAL
        val resultPath = path(c.fact.path)
        return when (val mark = c.fact.mark) {
            is MarkSlot.Star -> {
                check(c.fact.tail != Tail.EXACT) { "S8: a typed exact leaf needs a concrete mark" }
                flowTree(f.base, resultLayer, c.exclusion, mark.excluded,
                    FlowAlgebra.prepend(this, resultPath, FlowAlgebra.LEAF_NODE))
            }
            is MarkSlot.Concrete -> {
                val payload = if (c.fact.tail == Tail.EXACT) taintLeaves(markSetOf(mark.mark), MarkSet.EMPTY)
                    else taintLeaves(MarkSet.EMPTY, markSetOf(mark.mark))
                taintTree(f.base, resultLayer, taintAlg.prepend(this, resultPath, taintAlg.leafNode(payload)), c.exclusion)
            }
        }
    }

}
