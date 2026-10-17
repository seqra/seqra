package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.store.RequestMarkSet

/** One runner owns this temporary turn accumulator. Sources remain authoritative
 * in the router; taking a batch does not release its executing parent event. */
class PendingNeedReplay(
    private val ap: ApManager,
    private val statistics: StaticNeedReplayStatistics? = null,
) {
    private var graphGrew = false
    private var sourceDeltas = LinkedHashMap<StaticNeedKey, RequestMarkSet>()
    private var firstPendingNanos = 0L

    fun isEmpty(): Boolean = !graphGrew && sourceDeltas.isEmpty()

    private fun pending() {
        if (statistics != null && firstPendingNanos == 0L) firstPendingNanos = System.nanoTime()
    }

    /** Called only after a fresh relevant binding component is registered. */
    fun graphChanged() {
        pending()
        graphGrew = true
        statistics?.bindingChanged()
    }

    /** Source union and its diagnostic hooks have completed before this call. */
    fun sourcesCommitted(rows: Map<StaticNeedKey, RequestMarkSet>) {
        for ((source, delta) in rows) {
            ap.cancellation.checkpoint()
            if (delta.isEmpty) continue
            pending()
            sourceDeltas[source] = unionRequestMarks(sourceDeltas[source] ?: RequestMarkSet.EMPTY,
                delta, ap.cancellation::checkpoint)
        }
    }

    /** Runs before either releasing the event or requeuing incomplete Work.
     * Replay only queues children; it must not call back into this accumulator. */
    fun flush(router: StaticNeedRouter) {
        if (isEmpty()) return
        ap.cancellation.checkpoint()
        val full = graphGrew
        val deltas = sourceDeltas
        val since = firstPendingNanos
        graphGrew = false
        sourceDeltas = LinkedHashMap()
        firstPendingNanos = 0L
        statistics?.flushed(full, if (since == 0L) 0L else System.nanoTime() - since)
        val selected = if (full) router.sourcesSnapshot() else deltas
        router.replayEqualMarkGroups(selected)
        check(isEmpty()) { "Static replay must queue children without reentering its owner turn" }
    }

    /** Release only after owners have joined. Cancellation does not flush. */
    fun clear() {
        graphGrew = false
        sourceDeltas.clear()
        firstPendingNanos = 0L
    }
}
