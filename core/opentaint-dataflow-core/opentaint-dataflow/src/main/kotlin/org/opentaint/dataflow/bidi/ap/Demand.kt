package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestKind

/** The caller enforces the run's must-premise mode before starting an initial. */
internal fun initialFacts(m: ApManager, i: InitialAp): Facts {
    m.cancellation.checkpoint()
    if (i.isZero) return Reach.NORMAL
    return when (val mark = i.mark) {
        is MarkSlot.Star -> {
            val layer = when (i.tail) {
                Tail.STAR -> Layer.NORMAL
                Tail.ANY -> Layer.DEMAND
                Tail.EXACT -> error("S8: an exact premise needs a concrete mark")
                Tail.ANY_TAINT -> error("W8: a must premise needs a concrete mark")
            }
            m.flowTree(i.base, layer, i.exclusion, MarkSet.EMPTY,
                FlowAlgebra.prepend(m, i.path, FlowAlgebra.LEAF_NODE))
        }
        is MarkSlot.Concrete -> {
            val marks = m.markSetOf(mark.mark)
            val exact = i.tail == Tail.EXACT
            val leaf = if (exact) m.taintLeaves(marks, MarkSet.EMPTY) else m.taintLeaves(MarkSet.EMPTY, marks)
            val layer = if (exact || i.tail == Tail.ANY_TAINT) Layer.NORMAL else Layer.DEMAND
            m.taintTree(i.base, layer, m.taintAlg.prepend(m, i.path, m.taintAlg.leafNode(leaf)),
                if (i.tail == Tail.ANY_TAINT) i.exclusion else ExclusionSet.Empty)
        }
    }
}

internal fun policyInitial(m: ApManager, added: Facts): InitialAp {
    m.cancellation.checkpoint()
    return if (added is Reach) m.zero
        else m.initial(added.base, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
}

/** An abstract entry shares one FLOW initial across matching concrete marks and exit patterns. */
internal fun emitInitials(ops: ApOps, d: DemandPattern, added: Facts): List<InitialAp> {
    val m = ops.manager
    m.cancellation.checkpoint()
    check(d.entry.fact.tail != Tail.ANY_TAINT) { "W8 (d): a demand entry cannot have a must tail" }
    val result = LinkedHashSet<InitialAp>()
    for (a in ops.leaves(added)) {
        m.cancellation.checkpoint()
        val j = emit(d.entry, a) ?: continue
        result += m.initial(j)
    }
    return result.toList()
}

/** Preserve the reference result, including shapes outside typed Facts. */
internal fun restrictRaw(ops: ApOps, j: InitialAp, g: Facts, d: DemandPattern): List<Conclusion> {
    val m = ops.manager
    m.cancellation.checkpoint()
    val initial = j.toPattern()
    val result = LinkedHashSet<Conclusion>()
    for (a in ops.leaves(g)) {
        m.cancellation.checkpoint()
        restrict(initial, Conclusion(a.fact, a.exclusion, g.layer == Layer.DEMAND), d)?.let { result += it }
    }
    return result.toList()
}

/** Exact-key packing preserves every reference leaf and its raw layer. */
internal fun restrictFacts(ops: ApOps, j: InitialAp, g: Facts, d: DemandPattern): List<Facts> {
    val m = ops.manager
    val values = LinkedHashMap<GroupKey, Facts>()
    for (c in restrictRaw(ops, j, g, d)) {
        m.cancellation.checkpoint()
        check(c.fact.mark !is MarkSlot.Star ||
            (c.fact.tail == Tail.STAR && !c.demand) || (c.fact.tail == Tail.ANY && c.demand)) {
            "The reference result needs a FLOW tail that Facts cannot encode; validate the canonical paired guard"
        }
        val f = m.factsOf(c.fact, c.exclusion, if (c.demand) Layer.DEMAND else Layer.NORMAL)
        val old = values[f.groupKey]
        values[f.groupKey] = if (old == null) f else m.mergeAddDelta(old, f).first
    }
    return values.values.toList()
}

/** Selection reads raw headers only. A transient native read view does not change them. */
internal fun selectDemand(m: ApManager, premise: PremiseKey, g: Facts, direction: Direction): Facts? {
    m.cancellation.checkpoint()
    if (g.layer == Layer.DEMAND) return g
    val j = premise as? InitialAp ?: return g
    if (direction == Direction.BACKWARD && j.isZero) return g
    return null
}

/** One standing request and an overlapping added leaf, supplied by the caller's join. */
internal fun actOnRequest(m: ApManager, i: InitialAp, kind: RequestKind, a: Pattern, caller: CallerRef): RequestAction {
    m.cancellation.checkpoint()
    check(i.tail == Tail.STAR && i.exclusion == ExclusionSet.Empty && i.mark == MarkSlot.STAR) {
        "A request needs a policy initial or position answer"
    }
    return when (kind) {
        is RequestKind.Mark -> when (val mark = a.fact.mark) {
            is MarkSlot.Concrete -> if (mark.mark == kind.mark)
                RequestAction.Answer(m.initial(answer(i.toPattern(), a, kind.mark))) else RequestAction.None
            is MarkSlot.Star -> if (kind.mark in mark.excluded) RequestAction.None
                else RequestAction.Climb(caller.premise as InitialAp, kind)
        }
        is RequestKind.Position -> when {
            a.fact.path.startsWith(kind.path.toList()) ->
                RequestAction.Answer(m.initial(STATIC, kind.path, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR))
            (caller.premise as? InitialAp)?.base == STATIC -> RequestAction.Climb(caller.premise as InitialAp, kind)
            else -> RequestAction.None
        }
    }
}
