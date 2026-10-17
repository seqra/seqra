package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.store.RequestMarkSet

/** One authoritative interest source; intermediate visits retain no request row. */
data class StaticNeedKey(val method: MethodKey, val slot: Run1Slot.StaticField)

internal class StaticNeedWave(val router: StaticNeedRouter, val marks: RequestMarkSet) {
    private val visited = HashSet<StaticNeedKey>()
    fun enter(key: StaticNeedKey): Boolean = synchronized(visited) { visited.add(key) }
}

/** The same immutable mark batch is shared by every node in one finite traversal. */
class StaticNeedVisit internal constructor(
    val key: StaticNeedKey,
    internal val wave: StaticNeedWave,
) {
    val marks: RequestMarkSet get() = wave.marks
}

/** Run-scoped source union and traversal deduplication. Only dispatch may cross
 * units, through SummaryStorage's subscriber snapshots and counted owner posts.
 * Actual FLOW edges remain in caller-owned subscription directories. */
class StaticNeedRouter(
    private val ap: ApManager,
    private val dispatch: (StaticNeedVisit) -> Unit,
    private val onSourceChanged: ((StaticNeedKey, RequestMarkSet) -> Unit)? = null,
) {
    private val lock = Any()
    private val sources = LinkedHashMap<StaticNeedKey, RequestMarkSet>()

    /** Called at the source actor's method-empty barrier, before its work ends. */
    fun commit(method: MethodKey, rows: Map<Run1Slot.StaticField, RequestMarkSet>) {
        val fresh = ArrayList<Pair<StaticNeedKey, RequestMarkSet>>()
        synchronized(lock) {
            for ((slot, offered) in rows) {
                ap.cancellation.checkpoint()
                val key = StaticNeedKey(method, slot)
                val old = sources[key] ?: RequestMarkSet.EMPTY
                val delta = offered.minus(old, ap.cancellation::checkpoint)
                if (delta.isEmpty) continue
                sources[key] = unionRequestMarks(old, delta, ap.cancellation::checkpoint)
                fresh += key to delta
            }
        }
        for ((key, marks) in fresh) {
            ap.cancellation.checkpoint()
            onSourceChanged?.invoke(key, marks)
            forward(StaticNeedWave(this, marks), key)
        }
    }

    /** Register the actual input first. Fresh traversal replays cumulative sources,
     * so a previous visit cannot hide a newly added edge or a late mark. */
    fun graphChanged() {
        val current = synchronized(lock) { sources.entries.map { it.key to it.value } }
        for ((key, marks) in current) {
            ap.cancellation.checkpoint()
            forward(StaticNeedWave(this, marks), key)
        }
    }

    fun forward(visit: StaticNeedVisit, method: MethodKey, slot: Run1Slot.StaticField) {
        check(visit.wave.router === this) { "A static need belongs to one run" }
        forward(visit.wave, StaticNeedKey(method, slot))
    }

    private fun forward(wave: StaticNeedWave, key: StaticNeedKey) {
        ap.cancellation.checkpoint()
        if (wave.enter(key)) dispatch(StaticNeedVisit(key, wave))
    }

    /** Test/diagnostic view of sources only, never intermediate visit history. */
    fun sourcesSnapshot(): Map<StaticNeedKey, RequestMarkSet> = synchronized(lock) { sources.toMap() }

    /** The joined run has no remaining publisher/owner messages. */
    fun clear() = synchronized(lock) { sources.clear() }
}
