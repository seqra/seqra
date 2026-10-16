package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase

sealed interface Facts {
    val base: AccessPathBase
    val layer: Layer
}

class Reach private constructor(override val layer: Layer) : Facts {
    override val base: AccessPathBase get() = AccessPathBase.Zero
    companion object {
        @JvmField val NORMAL = Reach(Layer.NORMAL)
        @JvmField val DEMAND = Reach(Layer.DEMAND)
        fun of(layer: Layer): Reach = if (layer == Layer.NORMAL) NORMAL else DEMAND
    }
}

class FlowTree internal constructor(
    override val base: AccessPathBase,
    override val layer: Layer,
    val exclusion: ExclusionSet,
    val markExclusion: MarkSet,
    val root: FlowNode,
    val tail: Tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
) : Facts {
    init {
        check(base != AccessPathBase.Zero) { "Only REACH represents zero" }
        check(!root.isEmpty) { "A conclusion tree must be nonempty" }
        check(tail == Tail.STAR || tail == Tail.ANY) { "A FLOW leaf is STAR or ANY" }
        check(tail != Tail.ANY || exclusion == ExclusionSet.Empty) { "An ANY leaf has Empty E" }
    }
    internal fun withRoot(root: FlowNode) = FlowTree(base, layer, exclusion, markExclusion, root, tail)
    override fun equals(other: Any?): Boolean = this === other || (other is FlowTree && layer == other.layer &&
        base == other.base && tail == other.tail && exclusion == other.exclusion && markExclusion == other.markExclusion && root == other.root)
    override fun hashCode(): Int = ((root.hashCode() * 31 + base.hashCode()) * 31 + exclusion.hashCode()) * 31 +
        markExclusion.hashCode() + layer.ordinal + tail.ordinal * 17
}

/** The any-leaf geometry is independent of the edge layer. */
class TaintTree internal constructor(
    override val base: AccessPathBase,
    override val layer: Layer,
    val root: TaintNode,
    val exclusion: ExclusionSet = ExclusionSet.Empty,
    val anyTail: Tail = if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY,
) : Facts {
    init {
        check(base != AccessPathBase.Zero) { "Only REACH represents zero" }
        check(!root.isEmpty) { "A conclusion tree must be nonempty" }
        check(anyTail == Tail.ANY || anyTail == Tail.ANY_TAINT)
        check(exclusion == ExclusionSet.Empty || (anyTail == Tail.ANY_TAINT && root.hasAny)) {
            "Only must leaves have an exclusion"
        }
    }
    internal fun withRoot(root: TaintNode) = TaintTree(base, layer, root, if (root.hasAny) exclusion else ExclusionSet.Empty,
        if (root.hasAny) anyTail else Tail.ANY_TAINT)
    override fun equals(other: Any?): Boolean = this === other || (other is TaintTree && layer == other.layer &&
        base == other.base && anyTail == other.anyTail && exclusion == other.exclusion && root == other.root)
    override fun hashCode(): Int = ((root.hashCode() * 31 + base.hashCode()) * 31 + exclusion.hashCode()) * 2 + layer.ordinal + anyTail.ordinal * 17
}

enum class FactsKind { REACH, FLOW, TAINT }
data class GroupKey(
    val kind: FactsKind,
    val layer: Layer,
    val base: AccessPathBase,
    val exclusion: ExclusionSet = ExclusionSet.Empty,
    val markExclusion: MarkSet = MarkSet.EMPTY,
    val tail: Tail = when (kind) {
        FactsKind.REACH -> Tail.EXACT
        FactsKind.FLOW -> if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY
        FactsKind.TAINT -> if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY
    },
)

val Facts.groupKey: GroupKey get() = when (this) {
    is Reach -> GroupKey(FactsKind.REACH, layer, base)
    is FlowTree -> GroupKey(FactsKind.FLOW, layer, base, exclusion, markExclusion, tail)
    is TaintTree -> GroupKey(FactsKind.TAINT, layer, base, exclusion, tail = anyTail)
}

/** Layer changes never change known geometry or either exclusion annotation. */
fun Facts.withLayer(layer: Layer): Facts = if (this.layer == layer) this else when (this) {
    is Reach -> Reach.of(layer)
    is FlowTree -> FlowTree(base, layer, exclusion, markExclusion, root, tail)
    is TaintTree -> TaintTree(base, layer, root, exclusion, anyTail)
}

/** Exact TAINT leaves do not carry E and must not inherit a sibling's loss. */
internal fun run1CoverFacts(m: ApManager, f: Facts, emit: (Facts) -> Unit) {
    m.cancellation.checkpoint()
    when (f) {
        is Reach -> emit(f)
        is FlowTree -> emit(if (f.exclusion == ExclusionSet.Empty) f
            else FlowTree(f.base, Layer.DEMAND, ExclusionSet.Empty, f.markExclusion, f.root, f.tail))
        is TaintTree -> if (f.exclusion == ExclusionSet.Empty) emit(f) else {
            m.taintAlg.mapLeaves(f.root, m.cancellation) { m.taintLeaves(it.exact, MarkSet.EMPTY) }
                ?.let { emit(m.taintTree(f.base, f.layer, it)) }
            m.taintAlg.mapLeaves(f.root, m.cancellation) { m.taintLeaves(MarkSet.EMPTY, it.any) }
                ?.let { emit(m.taintTree(f.base, Layer.DEMAND, it, anyTail = f.anyTail)) }
        }
    }
}
