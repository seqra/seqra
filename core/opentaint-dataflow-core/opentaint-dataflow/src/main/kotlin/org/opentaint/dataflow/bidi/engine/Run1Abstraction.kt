package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.*

/** Run-1 input emission borrows the authoritative request rows. It retains no
 * supplier, question mirror, deferred answer, or completed-answer history. */
internal class Run1Abstraction(
    private val ops: ApOps,
    private val answer: (InitialAp) -> Unit,
    private val borrowRequests: (Facts) -> List<RequestedRow>,
    private val collectRequests: (Run1Slot, RequestMarkSelection) -> Unit,
    private val statistics: RequestBatchStatistics? = null,
    private val policyStatistics: Run1AbstractionStatistics? = null,
    private val owner: MethodKey? = null,
) {
    private var released = false

    // Pending ownership is exclusively the method actor's temporary RequestStorage.
    val hasPending: Boolean get() = false
    internal val pendingAnswerShapeCount: Long get() = 0
    internal val pendingAnswerMarkCount: Long get() = 0

    fun emit(ref: CallerRef, input: Facts) {
        check(!released)
        val ap = ops.manager
        ap.cancellation.checkpoint()
        validateRun1Supplier(ap, ref.premise, input)
        validateRun1Input(ap, input)
        if (input.base == STATIC) return // static reads/refinements are operation-local, never callee entry seeds
        answer(ops.policy(input))
        if (input is Reach) return
        if (input is TaintTree) {
            // Read after any synchronous abstraction starts; later P admission is
            // covered by registered caller-input replay, never by an emitter memo.
            val rows = borrowRequests(input)
            val counts = statistics?.let { RequestBatchStatistics.Counts() }
            forEachConcreteAnswer(ap, input, rows, counts) { initial ->
                if (counts != null) counts.answers++
                answer(initial)
            }
            if (counts != null) statistics?.record(counts)
        }
    }

    fun requireMark(initial: InitialAp, mark: TaintMark) = requireMarks(initial, RequestMarkSet.of(mark))

    fun requireMarks(initial: InitialAp, marks: RequestMarkSet) {
        check(!released)
        ops.manager.cancellation.checkpoint()
        val slot = normalRun1Slot(initial) ?: return // concrete/zero/special-root origins never request
        check(slot is Run1Slot.Base) { "Static interests do not enter ordinary P/R" }
        collectRequests(slot, marks)
    }

    /** Compatibility for local-service callers; only the actor can commit P. */
    fun flush(quantum: Int): Boolean { require(quantum > 0); check(!released); return true }

    fun release() { released = true }
}
