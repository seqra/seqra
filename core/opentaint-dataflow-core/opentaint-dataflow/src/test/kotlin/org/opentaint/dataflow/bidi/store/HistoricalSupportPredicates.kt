package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.bidi.ap.*

// Historical same-call support predicates. No runtime driver consumes retained emitter inputs.
/** Admissible members of the jointly supported premise set (ap.md §4.9). */
fun confirmableMember(j: InitialAp, mode: ApMode): Boolean =
    j.isZero || (j.mark is MarkSlot.Concrete && (j.tail == Tail.EXACT ||
        (j.tail == Tail.ANY_TAINT && mode.restricted && mode.direction == Direction.FORWARD)))

/** The driver separately checks same-call joint support and the caller edge's normal layer. */
fun supplies(jm: InitialAp, link: Link, mode: ApMode): Boolean {
    if (link.linkLayer != Layer.NORMAL) return false
    val a = link.addedFact
    val j = jm.toPattern()
    if (a == j) return true
    return mode.restricted && a.fact.tail == Tail.ANY_TAINT && j.fact.mark == a.fact.mark &&
        (j.fact.tail == Tail.EXACT || j.fact.tail == Tail.ANY_TAINT) && inside(j, a)
}
