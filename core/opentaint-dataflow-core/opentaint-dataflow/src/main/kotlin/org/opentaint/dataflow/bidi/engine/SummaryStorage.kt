package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApMode
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.Facts

/** A run owns this callee's published deltas and its subscribed caller runners. */
class SummaryStorage(
    val method: MethodKey,
    private val ap: ApManager,
    ops: ApOps,
    private val steps: ProtocolSteps,
    private val statistics: Run1AbstractionStatistics? = null,
) {
    private val lock = Any()
    private val published = PublicationIndex(ap, ops)
    private val subscribers = LinkedHashSet<SubscriptionManager>()
    private val requests = RequestStorage()

    fun borrowRequest(slot: Run1Slot): RequestRowView = synchronized(lock) { requests.borrow(slot) }

    fun borrowMatchingRequests(input: Facts): List<RequestedRow> = synchronized(lock) {
        val counts = statistics?.let { RequestMatchCounts() }
        requests.borrowMatchingRows(input, ap.cancellation::checkpoint, counts).also { rows ->
            if (counts != null) statistics?.requestMatch(counts, rows.size)
        }
    }

    /** Commit merged rows synchronously, then queue new marks after releasing the storage lock. */
    fun commit(temporary: RequestStorage) {
        ap.cancellation.checkpoint()
        val (fresh, recipients) = synchronized(lock) {
            val oldRows = requests.dynamicRows
            val rows = LinkedHashMap<Run1Slot, org.opentaint.dataflow.bidi.store.RequestMarkSet>()
            for ((slot, marks) in temporary.pendingRows()) {
                ap.cancellation.checkpoint()
                val delta = requests.addAll(slot, marks, ap.cancellation::checkpoint)
                if (!delta.isEmpty) rows[slot] = delta
            }
            statistics?.requestCommit(rows.size, rows.values.sumOf { it.size.toLong() }, requests.dynamicRows - oldRows)
            freezeRequestRows(rows) to if (rows.isEmpty()) emptyList() else subscribers.toList()
        }
        if (fresh.isEmpty()) return
        steps.notify {
            for (subscriber in recipients) {
                ap.cancellation.checkpoint()
                if (!subscriber.notifyRequests(method, fresh)) {
                    ap.cancellation.checkpoint()
                    error("An active run rejected a required request delivery")
                }
            }
        }
    }

    fun publish(pubs: List<Publication>) {
        ap.cancellation.checkpoint()
        val (delta, recipients) = synchronized(lock) { published.addAll(pubs) to subscribers.toList() }
        if (delta.isEmpty()) return
        steps.notify {
            for (subscriber in recipients) {
                ap.cancellation.checkpoint()
                subscriber.notify(method, delta)
            }
        }
    }

    fun addSubscriber(subscriber: SubscriptionManager) {
        ap.cancellation.checkpoint()
        synchronized(lock) { subscribers.add(subscriber) }
    }

    fun candidates(part: Subscription, mode: ApMode): List<Pair<Publication, Int>> =
        synchronized(lock) { published.candidates(part, mode) }

    fun staticCandidates(): List<Pair<Publication, Int>> = synchronized(lock) { published.staticCandidates() }
}
