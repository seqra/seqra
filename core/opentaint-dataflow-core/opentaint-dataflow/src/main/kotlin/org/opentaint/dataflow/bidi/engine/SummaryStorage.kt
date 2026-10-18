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
    precomputedRequests: FrozenRequestBase = EmptyFrozenRequestBase,
    private val statistics: Run1AbstractionStatistics? = null,
    private val staticRequests: CallerOwnedStaticRequests? = null,
) {
    constructor(method: MethodKey, ap: ApManager, ops: ApOps, steps: ProtocolSteps,
                precomputedRequests: RequestRows, statistics: Run1AbstractionStatistics? = null) :
        this(method, ap, ops, steps, MapFrozenRequestBase(precomputedRequests), statistics)

    private val lock = Any()
    private val published = PublicationIndex(ap, ops)
    private val subscribers = LinkedHashSet<SubscriptionManager>()
    private val requestSubscribers = LinkedHashMap<Run1Slot, LinkedHashSet<SubscriptionManager>>()
    private val requests = RequestStorage(precomputedRequests)

    fun borrowRequest(slot: Run1Slot): RequestRowView = synchronized(lock) { requests.borrow(slot) }

    fun borrowMatchingRequests(input: Facts): List<RequestedRow> = synchronized(lock) {
        val counts = statistics?.let { RequestMatchCounts() }
        requests.borrowMatchingRows(input, ap.cancellation::checkpoint, counts).also { rows ->
            if (counts != null) statistics?.requestMatch(counts, rows.size)
        }
    }

    /** Commit synchronously; keep B shared and queue fresh D after releasing the storage lock. */
    fun commit(temporary: RequestStorage): RequestRows {
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
            val notifications = LinkedHashMap<SubscriptionManager, MutableMap<Run1Slot, org.opentaint.dataflow.bidi.store.RequestMarkSet>>()
            for ((slot, marks) in rows) for (subscriber in requestSubscribers[slot].orEmpty()) {
                ap.cancellation.checkpoint()
                notifications.getOrPut(subscriber, ::LinkedHashMap)[slot] = marks
            }
            freezeRequestRows(rows) to notifications.map { (subscriber, selected) ->
                subscriber to freezeRequestRows(selected)
            }
        }
        if (fresh.isEmpty()) return fresh
        // The source owns the cumulative marks. The directory retains only a
        // getter/reference, installed before either snapshot or delta notification.
        val service = staticRequests
        if (service != null) for ((slot, marks) in fresh) if (slot is Run1Slot.StaticField) {
            val key = StaticNeedKey(method, slot)
            service.registerSource(key) {
                synchronized(lock) {
                    val row = requests.borrow(slot)
                    if (row.precomputed.isEmpty) row.dynamic
                    else row.toRequestMarkSet(ap.cancellation::checkpoint)
                }
            }
            service.publishSourceDelta(key, marks)
        }
        steps.notify {
            for ((subscriber, selected) in recipients) {
                ap.cancellation.checkpoint()
                if (!subscriber.notifyRequests(method, selected)) {
                    ap.cancellation.checkpoint()
                    error("An active run rejected a required request delivery")
                }
            }
        }
        return fresh
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

    /** Exact Base/fallback stream; registration and commit use the same lock.
     * A caller registers before reading the cumulative row. */
    fun addRequestSubscriber(subscriber: SubscriptionManager, slot: Run1Slot) {
        ap.cancellation.checkpoint()
        synchronized(lock) { requestSubscribers.getOrPut(slot, ::LinkedHashSet).add(subscriber) }
    }

    /** Shared published routing metadata; publisher never reads caller state. */
    fun notifyStaticNeed(visit: StaticNeedVisit) {
        check(visit.key.method == method)
        val recipients = synchronized(lock) { subscribers.toList() }
        // Deferred test adapters own this notification until their callback ends;
        // subscriber posts acquire their own owners before this one is released.
        visit.wave.queuedWork()
        var entered = false
        try {
            steps.notify {
                entered = true
                try {
                    for (subscriber in recipients) {
                        ap.cancellation.checkpoint()
                        subscriber.notifyStaticNeed(visit)
                    }
                } finally {
                    visit.wave.finishedWork()
                }
            }
        } catch (error: Throwable) {
            if (!entered) visit.wave.finishedWork()
            throw error
        }
    }

    fun candidates(part: Subscription, mode: ApMode): List<Pair<Publication, Int>> =
        synchronized(lock) { published.candidates(part, mode) }
}
