package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*

/**
 * Owner-local authorization for actual later-run inputs, independent of old guidance.
 * Only canonical initial identities are retained; caller inputs stay in subscriptions.
 */
internal class StrongQueries(private val ops: ApOps, private val mode: ApMode) {
    init { require(!mode.run1) { "Strong current queries belong to restricted runs" } }

    private val admitted = LinkedHashSet<InitialAp>()

    /** Install the whole batch before a caller starts any returned initial. */
    fun admit(input: Facts, guides: Sequence<DemandPattern> = emptySequence()): List<InitialAp> {
        val ap = ops.manager
        ap.cancellation.checkpoint()
        val projected = LinkedHashSet<InitialAp>()
        val concreteGuides = guides.filter { it.entry.fact.mark is MarkSlot.Concrete }.toList()
        ops.limit(input, mode.fieldLimit, object : ApOut {
            override fun markRequest(mark: TaintMark) = error("A field cut cannot request a mark")
            override fun result(f: Facts) {
                if (f is Reach) {
                    projected += ap.zero
                    return
                }
                for (leaf in ops.leaves(f)) {
                    ap.cancellation.checkpoint()
                    // Use the ordinary restricted emitter. Abstract and must current
                    // inputs supply a canonical ANY query, not a concrete mark that
                    // was absent from the actual input. Matching still reads f's E/mx.
                    val query = Pattern(leaf.fact.copy(
                        tail = if (leaf.fact.tail == Tail.STAR || leaf.fact.tail == Tail.ANY_TAINT)
                            Tail.ANY else leaf.fact.tail,
                        mark = if (leaf.fact.mark is MarkSlot.Star) MarkSlot.STAR else leaf.fact.mark,
                    ), if (leaf.fact.tail == Tail.EXACT) leaf.exclusion else ExclusionSet.Empty)
                    // The same scalar primitive used by ops.emit, once per leaf;
                    // do not rescan the whole packed input for every current leaf.
                    emit(query, leaf)?.let { projected += ap.initial(it) }
                }
                // Guidance refines actual concrete inputs by intersection; it cannot
                // manufacture a concrete mark from FLOW or reintroduce an abstract
                // run-1 root analysis. The actual-input query remains additive.
                for (guide in concreteGuides) {
                    ap.cancellation.checkpoint()
                    for (initial in ops.emit(guide, f)) {
                        check(initial.mark is MarkSlot.Concrete)
                        val bounded = limit(Conclusion(initial.toPattern().fact, initial.exclusion,
                            initial.tail == Tail.ANY), mode.fieldLimit)
                        projected += ap.initial(Pattern(bounded.fact, bounded.exclusion))
                    }
                }
            }
        })
        ap.cancellation.checkpoint()
        for (initial in projected) {
            ap.cancellation.checkpoint()
            check(mode.direction == Direction.FORWARD || initial.tail != Tail.ANY_TAINT) {
                "A backward current query cannot supply a must initial"
            }
        }
        return projected.filter { initial ->
            ap.cancellation.checkpoint()
            admitted.add(initial)
        }
    }

    /** Authorize the whole premise, never one independently matching ND member. */
    fun permits(premise: PremiseKey): Boolean {
        ops.manager.cancellation.checkpoint()
        // A computed local GEN/source outcome has no nonzero input dependency.
        // This grants publication only; it does not start ZERO or invent an answer.
        if (premise.isZero) return admitted.isNotEmpty()
        for (member in 0 until premise.size) {
            ops.manager.cancellation.checkpoint()
            if (premise.member(member) !in admitted) return false
        }
        return true
    }

    fun clear() = admitted.clear()
}
