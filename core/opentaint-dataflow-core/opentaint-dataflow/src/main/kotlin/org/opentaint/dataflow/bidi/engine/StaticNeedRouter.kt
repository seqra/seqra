package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import java.util.concurrent.atomic.AtomicInteger

/** One authoritative interest source; intermediate visits retain no request row. */
data class StaticNeedKey(val method: MethodKey, val slot: Run1Slot.StaticField)

internal class StaticNeedWave(val router: StaticNeedRouter, val marks: RequestMarkSet) {
    private val visited = HashSet<StaticNeedKey>()
    // Telemetry only: one root-dispatch owner plus accepted subscriber deliveries.
    // This counter never decides queue admission, graph visits or completion.
    private val workOwners = router.statistics?.let { statistics ->
        statistics.waveWorkStarted()
        AtomicInteger(1)
    }
    internal fun queuedWork() {
        val owners = workOwners ?: return
        if (owners.getAndIncrement() == 0) router.statistics?.waveWorkStarted()
    }
    internal fun finishedWork() {
        val owners = workOwners ?: return
        while (true) {
            val old = owners.get()
            if (old <= 0) {
                router.statistics?.unbalancedWaveRelease()
                return
            }
            if (owners.compareAndSet(old, old - 1)) {
                if (old == 1) router.statistics?.waveWorkFinished()
                return
            }
        }
    }
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
    internal val statistics: StaticNeedReplayStatistics? = null,
) {
    private val lock = Any()
    private val sources = LinkedHashMap<StaticNeedKey, RequestMarkSet>()

    /** Commit at method-empty, but leave dispatch to the explicit runner owner.
     * Hooks retain source identity and run once for each actual fresh commit. */
    fun commitSourceUnions(method: MethodKey,
        rows: Map<Run1Slot.StaticField, RequestMarkSet>): Map<StaticNeedKey, RequestMarkSet> {
        val fresh = LinkedHashMap<StaticNeedKey, RequestMarkSet>()
        synchronized(lock) {
            for ((slot, offered) in rows) {
                ap.cancellation.checkpoint()
                val key = StaticNeedKey(method, slot)
                val old = sources[key] ?: RequestMarkSet.EMPTY
                val delta = offered.minus(old, ap.cancellation::checkpoint)
                if (delta.isEmpty) continue
                sources[key] = unionRequestMarks(old, delta, ap.cancellation::checkpoint)
                fresh[key] = delta
            }
        }
        statistics?.sourceCommit(fresh.size, fresh.values.sumOf { it.size.toLong() })
        for ((key, marks) in fresh) {
            ap.cancellation.checkpoint()
            onSourceChanged?.invoke(key, marks)
        }
        return fresh
    }

    /** Ephemeral equality keys keep cancellation bounded inside content checks.
     * Hash collisions never combine unequal contents; no key outlives this flush. */
    private inner class MarkKey(val marks: RequestMarkSet, val counts: GroupCounts?) {
        override fun hashCode(): Int = marks.hashCode()
        override fun equals(other: Any?): Boolean {
            if (other !is StaticNeedRouter.MarkKey) return false
            if (counts != null) counts.comparisons++
            ap.cancellation.checkpoint()
            if (marks === other.marks) return true
            if (marks.hashCode() != other.marks.hashCode() || marks.size != other.marks.size) return false
            for (index in 0 until marks.size) {
                ap.cancellation.checkpoint()
                if (counts != null) counts.comparedCells++
                if (marks.idAt(index) != other.marks.idAt(index)) return false
            }
            return true
        }
    }
    private class GroupCounts {
        var comparisons = 0L
        var comparedCells = 0L
    }

    /** One fresh wave per exact nonempty selected mark set, with all its roots.
     * This snapshots no binding graph; visits read each recipient's live routes. */
    fun replayEqualMarkGroups(starts: Map<StaticNeedKey, RequestMarkSet>) {
        val groups = LinkedHashMap<MarkKey, MutableList<StaticNeedKey>>()
        val counts = statistics?.let { GroupCounts() }
        val started = if (statistics == null) 0L else System.nanoTime()
        var rows = 0L
        var members = 0L
        try {
            for ((source, marks) in starts) {
                ap.cancellation.checkpoint()
                if (marks.isEmpty) continue
                rows++
                members += marks.size
                groups.getOrPut(MarkKey(marks, counts), ::ArrayList).add(source)
            }
        } finally {
            if (counts != null) statistics?.grouping(rows, members, groups.size,
                counts.comparisons, counts.comparedCells, System.nanoTime() - started)
        }
        for ((key, roots) in groups) {
            ap.cancellation.checkpoint()
            val wave = StaticNeedWave(this, key.marks)
            statistics?.wave(roots.size)
            try {
                for (root in roots) {
                    ap.cancellation.checkpoint()
                    forward(wave, root)
                }
            } finally {
                wave.finishedWork()
            }
        }
    }

    fun forward(visit: StaticNeedVisit, method: MethodKey, slot: Run1Slot.StaticField) {
        check(visit.wave.router === this) { "A static need belongs to one run" }
        forward(visit.wave, StaticNeedKey(method, slot))
    }

    private fun forward(wave: StaticNeedWave, key: StaticNeedKey) {
        ap.cancellation.checkpoint()
        val fresh = wave.enter(key)
        statistics?.node(fresh)
        if (fresh) dispatch(StaticNeedVisit(key, wave))
    }

    /** Immutable values, captured under the source lock; no callback holds it. */
    fun sourcesSnapshot(): Map<StaticNeedKey, RequestMarkSet> = synchronized(lock) {
        val snapshot = LinkedHashMap<StaticNeedKey, RequestMarkSet>()
        for ((key, marks) in sources) {
            ap.cancellation.checkpoint()
            snapshot[key] = marks
        }
        snapshot
    }

    /** The joined run has no remaining publisher/owner messages. */
    fun clear() = synchronized(lock) {
        sources.clear()
        statistics?.ownersJoined()
    }
}
