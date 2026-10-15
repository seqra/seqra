package org.opentaint.dataflow.bidi.engine

import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApMode
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.MethodKey
import java.util.concurrent.ConcurrentLinkedQueue
import org.opentaint.dataflow.bidi.store.RequestMarkSetBuilder

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
    private var questions: QuestionView = persistentHashMapOf()

    /** The returned persistent root is immutable and may be borrowed by a replay handler. */
    fun questions(): QuestionView = synchronized(lock) { questions }

    /** Install before notify. Registration before the corresponding replay read closes the other race. */
    fun publishQuestions(delta: QuestionDelta) {
        ap.cancellation.checkpoint()
        if (delta.marks.isEmpty) return
        val fresh = synchronized(lock) {
            val old = questions[delta.key]
            val added = RequestMarkSetBuilder()
            delta.marks.forEachMark { mark ->
                ap.cancellation.checkpoint()
                if (old == null || mark !in old) added.add(mark)
            }
            if (added.isEmpty) null else {
                val marks = added.takeSnapshot()
                val all = if (old == null) marks else RequestMarkSetBuilder().apply {
                    old.forEachMark { ap.cancellation.checkpoint(); add(it) }
                    marks.forEachMark { ap.cancellation.checkpoint(); add(it) }
                }.takeSnapshot()
                questions = questions.put(delta.key, all)
                QuestionDelta(delta.key, marks)
            }
        } ?: return
        steps.notify {
            for (subscriber in subscribers) {
                ap.cancellation.checkpoint()
                subscriber.notifyQuestions(method, fresh)
            }
        }
    }

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
