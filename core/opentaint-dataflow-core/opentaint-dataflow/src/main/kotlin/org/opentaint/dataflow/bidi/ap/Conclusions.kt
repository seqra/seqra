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
) : Facts {
    init {
        check(base != AccessPathBase.Zero) { "Only REACH represents zero" }
        check(!root.isEmpty) { "A conclusion tree must be nonempty" }
        check(layer == Layer.NORMAL || exclusion == ExclusionSet.Empty) { "W1: a demand flag has an ANY tail" }
    }
    internal fun withRoot(root: FlowNode) = FlowTree(base, layer, exclusion, markExclusion, root)
    override fun equals(other: Any?): Boolean = this === other || (other is FlowTree && layer == other.layer &&
        base == other.base && exclusion == other.exclusion && markExclusion == other.markExclusion && root == other.root)
    override fun hashCode(): Int = ((root.hashCode() * 31 + base.hashCode()) * 31 + exclusion.hashCode()) * 31 +
        markExclusion.hashCode() + layer.ordinal
}

/** Normal any leaves are must/E; demand any leaves are may with empty E. */
class TaintTree internal constructor(
    override val base: AccessPathBase,
    override val layer: Layer,
    val root: TaintNode,
    val exclusion: ExclusionSet = ExclusionSet.Empty,
) : Facts {
    init {
        check(base != AccessPathBase.Zero) { "Only REACH represents zero" }
        check(!root.isEmpty) { "A conclusion tree must be nonempty" }
        check(exclusion == ExclusionSet.Empty || (layer == Layer.NORMAL && root.hasAny)) {
            "W8: only normal must leaves have an exclusion"
        }
    }
    internal fun withRoot(root: TaintNode) = TaintTree(base, layer, root, if (root.hasAny) exclusion else ExclusionSet.Empty)
    override fun equals(other: Any?): Boolean = this === other || (other is TaintTree && layer == other.layer &&
        base == other.base && exclusion == other.exclusion && root == other.root)
    override fun hashCode(): Int = ((root.hashCode() * 31 + base.hashCode()) * 31 + exclusion.hashCode()) * 2 + layer.ordinal
}

enum class FactsKind { REACH, FLOW, TAINT }
data class GroupKey(
    val kind: FactsKind,
    val layer: Layer,
    val base: AccessPathBase,
    val exclusion: ExclusionSet = ExclusionSet.Empty,
    val markExclusion: MarkSet = MarkSet.EMPTY,
)

val Facts.groupKey: GroupKey get() = when (this) {
    is Reach -> GroupKey(FactsKind.REACH, layer, base)
    is FlowTree -> GroupKey(FactsKind.FLOW, layer, base, exclusion, markExclusion)
    is TaintTree -> GroupKey(FactsKind.TAINT, layer, base, exclusion)
}
