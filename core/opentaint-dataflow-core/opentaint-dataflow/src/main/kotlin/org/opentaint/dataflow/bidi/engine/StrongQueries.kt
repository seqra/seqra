package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.store.StaticQueryPolicy

/**
 * Owner-local authorization for actual later-run inputs, independent of old guidance.
 * Only canonical initial identities are retained; caller inputs stay in subscriptions.
 */
internal class StrongQueries(
    private val ops: ApOps,
    private val mode: ApMode,
    private val staticQueryPolicy: StaticQueryPolicy = StaticQueryPolicy.ORDINARY,
) {
    init {
        require(!mode.run1) { "Strong current queries belong to restricted runs" }
        require(staticQueryPolicy == StaticQueryPolicy.ORDINARY ||
            mode.direction == Direction.BACKWARD && mode.fieldLimit == 1) {
            "Run-1 static completion belongs only to the incoming run-2 demand"
        }
    }

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
                    if (staticQueryPolicy == StaticQueryPolicy.COMPLETE_RUN1_STATIC_FIELDS)
                        completeStaticFull(leaf)?.let { projected += ap.initial(it) }
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

    /** Complete one actual FULL field intersection. FLOW never supplies a concrete mark. */
    private fun completeStaticFull(actual: Pattern): Pattern? {
        val ap = ops.manager
        val field = validateRun2StaticFullInput(ap, actual)
        if (!field || actual.fact.tail != Tail.ANY) return null
        val exact = actual.copy(fact = actual.fact.copy(tail = Tail.EXACT))
        // The real AP emitter preserves the actual full identity and spatial intersection.
        return emit(exact, actual)
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

/** Check after the existing field cut, before a producer starts executable run-2 work.
 * Returns true only for a named FULL field; exact class markers need no completion. */
internal fun validateRun2StaticFullInput(ap: ApManager, actual: Pattern): Boolean {
    val field = validateRun2StaticFullAnchor(ap, actual) ?: return false
    check(actual.exclusion == ExclusionSet.Empty &&
        (!field || actual.fact.path.size == 2 &&
            (actual.fact.tail == Tail.EXACT || actual.fact.tail == Tail.ANY))) {
        "Unsupported run-2 concrete static input: $actual. " +
            "A query or seed needs empty field exclusions and a named field with EXACT/ANY tail, " +
            "or an EXACT class marker (run1-static-summary-erasure.md section 3)."
    }
    return field
}

/** A current fact can retain its existing MUST/E header. Only its static anchor
 * is checked here. Deeper field suffixes are lawful before the normal field cut.
 * Returns true for a named field, false for an exact class marker, null otherwise. */
internal fun validateRun2StaticFullAnchor(ap: ApManager, actual: Pattern): Boolean? {
    if (actual.fact.base != STATIC) return null
    val atom = (actual.fact.mark as? MarkSlot.Concrete)?.mark ?: return null
    if (ap.marks.fullId(atom) == null) return null // GROUP guidance is metadata, not an actual FULL mark.
    val path = actual.fact.path
    val field = path.size >= 2 && path[0].isClass() &&
        ap.accessors.accessor(path[1]) is FieldAccessor
    val exactClass = path.size == 1 && path[0].isClass() && actual.fact.tail == Tail.EXACT
    check(field || exactClass) {
        "Unsupported run-2 concrete static input: $actual. " +
            "Expected a named JVM static field anchor or an EXACT class marker; " +
            "broad root/class marks require a separate query adapter (run1-static-summary-erasure.md section 3)."
    }
    return field
}
