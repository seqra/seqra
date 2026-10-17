package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*

/** Actual static T is the evidence for a concrete initial. Neither local nor
 * inherited mark interests gate this projector. E/mx do not filter interest
 * geometry; ordinary transfer still receives its complete input separately. */
internal fun forEachActualStaticInitial(
    ap: ApManager, input: TaintTree, profile: Run1StaticProfile,
    statistics: RequestBatchStatistics.Counts? = null, emit: (InitialAp) -> Unit,
) {
    check(input.base == STATIC)
    val universe = profile.footprint.fieldUniverse
    val stack = ArrayDeque<Pair<TaintNode, IntArray>>()
    stack.addLast(input.root to EMPTY_PATH)
    while (stack.isNotEmpty()) {
        ap.cancellation.checkpoint()
        val (node, path) = stack.removeLast()
        fun branch(marks: MarkSet, exact: Boolean) {
            if (marks.isEmpty || (exact && path.size < 2)) return
            fun at(slot: Run1Slot.StaticField) {
                ap.cancellation.checkpoint()
                if (statistics != null) statistics.groupGeometrySteps++
                val tail = if (exact && path.size == 2) Tail.EXACT else Tail.STAR
                val initialPath = ap.path(slot.pathData)
                for (id in marks.data) {
                    ap.cancellation.checkpoint()
                    if (statistics != null) statistics.memberScans++
                    emit(ap.initial(STATIC, initialPath, tail, ExclusionSet.Empty,
                        MarkSlot.Concrete(TaintMark(id))))
                }
            }
            if (path.size >= 2) {
                val slot = Run1Slot.StaticField(path[0], path[1])
                check(slot in universe) { "Static input field is absent from the frozen prescan universe: $slot" }
                at(slot)
            } else for (slot in universe) {
                ap.cancellation.checkpoint()
                if (path.isEmpty() || path[0] == slot.cls) at(slot)
            }
        }
        branch(node.leaf.exact, exact = true)
        branch(node.leaf.any, exact = false)
        val children = node.childData ?: continue
        val accessors = checkNotNull(node.accessorData)
        for (i in children.indices.reversed()) {
            ap.cancellation.checkpoint()
            stack.addLast(children[i] to (path + accessors[i]))
        }
    }
    // Class points have their own structural inventory, not a field/request key.
    forEachDirectClassInitial(ap, input, profile) { initial ->
        if (statistics != null) statistics.memberScans++
        emit(initial)
    }
}

internal fun isSpecialStaticRoot(initial: InitialAp): Boolean = initial.base == STATIC &&
    initial.path == null && initial.tail == Tail.STAR && initial.mark == MarkSlot.STAR &&
    initial.exclusion == ExclusionSet.Empty

internal fun isSpecialStaticRoot(pattern: Pattern): Boolean = pattern.fact.base == STATIC &&
    pattern.fact.path.isEmpty() && pattern.fact.tail == Tail.STAR && pattern.fact.mark == MarkSlot.STAR &&
    pattern.exclusion == ExclusionSet.Empty

/** Split only the root component; packed field branches keep ordinary rules. */
internal fun staticRootFact(ap: ApManager, input: Facts): Facts? {
    if (input.base != STATIC) return null
    return when (input) {
        is FlowTree -> if (input.root.leaf.isEmpty) null else ap.flowTree(STATIC, input.layer,
            input.exclusion, input.markExclusion, FlowAlgebra.leafNode(input.root.leaf), input.tail)
        is TaintTree -> if (input.root.leaf.isEmpty) null else ap.taintTree(STATIC, input.layer,
            ap.taintAlg.leafNode(input.root.leaf), input.exclusion, input.anyTail)
        is Reach -> null
    }
}

internal fun withoutStaticRootFact(ap: ApManager, input: Facts): Facts? {
    if (input.base != STATIC) return input
    return when (input) {
        is FlowTree -> if (input.root.leaf.isEmpty) input else
            FlowAlgebra.withLeaf(input.root, FlowLeaf.NONE)?.let { input.withRoot(it) }
        is TaintTree -> if (input.root.leaf.isEmpty) input else
            ap.taintAlg.withLeaf(input.root, TaintLeaves.EMPTY)?.let { input.withRoot(it) }
        is Reach -> input
    }
}
