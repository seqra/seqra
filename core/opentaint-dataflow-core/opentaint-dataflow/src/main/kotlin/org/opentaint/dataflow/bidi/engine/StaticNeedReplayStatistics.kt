package org.opentaint.dataflow.bidi.engine

import java.util.concurrent.atomic.AtomicLong

/** Numeric totals only. These counters retain no keys, batches, waves or routes.
 * JVM-live waves are deliberately not inferred from completed dispatch counts. */
class StaticNeedReplayStatistics {
    private val bindingChanges = AtomicLong()
    private val sourceCommits = AtomicLong()
    private val freshSourceRows = AtomicLong()
    private val freshSourceMembers = AtomicLong()
    private val fullSourceSelections = AtomicLong()
    private val deltaSelections = AtomicLong()
    private val selectedRows = AtomicLong()
    private val selectedMembers = AtomicLong()
    private val markSetGroups = AtomicLong()
    private val markSetComparisons = AtomicLong()
    private val comparedMarkCells = AtomicLong()
    private val groupingNanos = AtomicLong()
    private val maxGroupingNanos = AtomicLong()
    private val maxPendingToFlushNanos = AtomicLong()
    private val waves = AtomicLong()
    private val roots = AtomicLong()
    private val activeWorkWaves = AtomicLong()
    private val maxActiveWorkWaves = AtomicLong()
    private val abandonedActiveWorkWaves = AtomicLong()
    private val unbalancedWaveWorkReleases = AtomicLong()
    private val nodeAttempts = AtomicLong()
    private val nodeEntries = AtomicLong()
    private val subscriberDeliveries = AtomicLong()
    private val ordinaryBindingScans = AtomicLong()
    private val packedBindingScans = AtomicLong()
    private val baseOffers = AtomicLong()
    private val baseOfferedMembers = AtomicLong()
    private val baseDeliveries = AtomicLong()
    private val baseDeliveredMembers = AtomicLong()
    private val allCommittedBaseMembers = AtomicLong()
    private val requestSourceEnvelopes = AtomicLong()
    private val baseEnvelopeRows = AtomicLong()
    private val sourceReentryEnvelopeRows = AtomicLong()

    internal fun bindingChanged() { bindingChanges.incrementAndGet() }
    internal fun sourceCommit(rows: Int, members: Long) {
        sourceCommits.incrementAndGet(); freshSourceRows.addAndGet(rows.toLong()); freshSourceMembers.addAndGet(members)
    }
    internal fun flushed(full: Boolean, latency: Long) {
        if (full) fullSourceSelections.incrementAndGet() else deltaSelections.incrementAndGet()
        maxPendingToFlushNanos.accumulateAndGet(latency, ::maxOf)
    }
    internal fun grouping(rows: Long, members: Long, groups: Int, comparisons: Long, cells: Long, nanos: Long) {
        selectedRows.addAndGet(rows); selectedMembers.addAndGet(members); markSetGroups.addAndGet(groups.toLong())
        markSetComparisons.addAndGet(comparisons); comparedMarkCells.addAndGet(cells)
        groupingNanos.addAndGet(nanos); maxGroupingNanos.accumulateAndGet(nanos, ::maxOf)
    }
    internal fun wave(rootCount: Int) { waves.incrementAndGet(); roots.addAndGet(rootCount.toLong()) }
    internal fun waveWorkStarted() {
        maxActiveWorkWaves.accumulateAndGet(activeWorkWaves.incrementAndGet(), ::maxOf)
    }
    internal fun waveWorkFinished() { activeWorkWaves.decrementAndGet() }
    internal fun unbalancedWaveRelease() { unbalancedWaveWorkReleases.incrementAndGet() }
    /** Called only after all owners join. Remaining work belongs to abandoned
     * queued visits on cancellation/failure, not necessarily JVM-live objects. */
    internal fun ownersJoined() {
        abandonedActiveWorkWaves.addAndGet(activeWorkWaves.getAndSet(0))
    }
    internal fun node(fresh: Boolean) { nodeAttempts.incrementAndGet(); if (fresh) nodeEntries.incrementAndGet() }
    internal fun subscriberDelivery() { subscriberDeliveries.incrementAndGet() }
    internal fun bindingScans(ordinary: Long, packed: Long) {
        ordinaryBindingScans.addAndGet(ordinary); packedBindingScans.addAndGet(packed)
    }
    internal fun baseOffer(members: Int) { baseOffers.incrementAndGet(); baseOfferedMembers.addAndGet(members.toLong()) }
    internal fun baseDelivery(members: Int) { baseDeliveries.incrementAndGet(); baseDeliveredMembers.addAndGet(members.toLong()) }
    internal fun baseCommit(members: Long) { allCommittedBaseMembers.addAndGet(members) }
    internal fun requestEnvelope(baseRows: Int, staticRows: Int) {
        requestSourceEnvelopes.incrementAndGet(); baseEnvelopeRows.addAndGet(baseRows.toLong())
        sourceReentryEnvelopeRows.addAndGet(staticRows.toLong())
    }

    data class Snapshot(
        val bindingChanges: Long, val sourceCommits: Long,
        val freshSourceRows: Long, val freshSourceMembers: Long,
        val fullSourceSelections: Long, val deltaSelections: Long,
        val selectedRows: Long, val selectedMembers: Long, val markSetGroups: Long,
        val markSetComparisons: Long, val comparedMarkCells: Long,
        val groupingNanos: Long, val maxGroupingNanos: Long, val maxPendingToFlushNanos: Long,
        val waves: Long, val roots: Long,
        /** Waves with a root-dispatch owner or accepted queued/active subscriber work. */
        val activeWorkWaves: Long, val maxActiveWorkWaves: Long,
        val abandonedActiveWorkWaves: Long, val unbalancedWaveWorkReleases: Long,
        val nodeAttempts: Long, val nodeEntries: Long,
        val subscriberDeliveries: Long, val ordinaryBindingScans: Long, val packedBindingScans: Long,
        val baseOffers: Long, val baseOfferedMembers: Long, val baseDeliveries: Long, val baseDeliveredMembers: Long,
        /** Includes Base requests from other ordinary producers, not just this router. */
        val allCommittedBaseMembers: Long,
        /** Mixed ordinary Base / static-source reentry envelopes; not Base-only. */
        val requestSourceEnvelopes: Long, val baseEnvelopeRows: Long, val sourceReentryEnvelopeRows: Long,
        /** No GC reachability or per-wave completion history is retained to derive this. */
        val jvmLiveWaveMaximumMeasured: Boolean = false,
    )
    fun snapshot(): Snapshot = Snapshot(bindingChanges.get(), sourceCommits.get(), freshSourceRows.get(),
        freshSourceMembers.get(), fullSourceSelections.get(), deltaSelections.get(), selectedRows.get(),
        selectedMembers.get(), markSetGroups.get(), markSetComparisons.get(), comparedMarkCells.get(),
        groupingNanos.get(), maxGroupingNanos.get(), maxPendingToFlushNanos.get(), waves.get(), roots.get(),
        activeWorkWaves.get(), maxActiveWorkWaves.get(), abandonedActiveWorkWaves.get(),
        unbalancedWaveWorkReleases.get(), nodeAttempts.get(), nodeEntries.get(), subscriberDeliveries.get(), ordinaryBindingScans.get(),
        packedBindingScans.get(), baseOffers.get(), baseOfferedMembers.get(), baseDeliveries.get(),
        baseDeliveredMembers.get(), allCommittedBaseMembers.get(), requestSourceEnvelopes.get(),
        baseEnvelopeRows.get(), sourceReentryEnvelopeRows.get())
}
