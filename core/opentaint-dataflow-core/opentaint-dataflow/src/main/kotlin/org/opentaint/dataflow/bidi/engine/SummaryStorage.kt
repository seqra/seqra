package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApMode
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.MethodKey
import java.util.concurrent.ConcurrentLinkedQueue

/** A run owns this callee's published deltas and its subscribed caller runners. */
class SummaryStorage(
    val method: MethodKey,
    private val ap: ApManager,
    ops: ApOps,
    private val steps: ProtocolSteps,
) {
    private val lock = Any()
    private val published = PublicationIndex(ap, ops)
    private val subscribers = ConcurrentLinkedQueue<SubscriptionManager>()

    fun publish(pubs: List<Publication>) {
        ap.cancellation.checkpoint()
        val delta = synchronized(lock) { published.addAll(pubs) }
        if (delta.isEmpty()) return
        steps.notify {
            for (subscriber in subscribers) {
                ap.cancellation.checkpoint()
                subscriber.notify(method, delta)
            }
        }
    }

    fun addSubscriber(subscriber: SubscriptionManager) {
        ap.cancellation.checkpoint()
        subscribers.add(subscriber)
    }

    fun candidates(part: Subscription, mode: ApMode): List<Pair<Publication, Int>> =
        synchronized(lock) { published.candidates(part, mode) }
}
