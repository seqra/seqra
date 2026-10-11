package org.opentaint.dataflow.bidi.store

/** The caller stores and deduplicates a side before each event. Near queries must be symmetric. */
class StandingJoin<A, B>(
    private val nearB: (A) -> Sequence<B>,
    private val nearA: (B) -> Sequence<A>,
    private val meet: (A, B) -> Unit,
) {
    fun newA(a: A) { for (b in nearB(a)) meet(a, b) }
    fun newB(b: B) { for (a in nearA(b)) meet(a, b) }
}
