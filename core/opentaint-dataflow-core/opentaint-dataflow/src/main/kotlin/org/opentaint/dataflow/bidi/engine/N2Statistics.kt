package org.opentaint.dataflow.bidi.engine

import java.util.concurrent.atomic.AtomicLong

/** Optional aggregate census. Current counts cover subscription directories, not live graph bytes. */
class N2Statistics {
    private val acceptedDeltas = AtomicLong()
    private val cumulativeGroups = AtomicLong()
    private val zeroOnlyEntries = AtomicLong()
    private val indexPostings = AtomicLong()
    private val createdGroups = AtomicLong()
    private val createdZeroOnlyEntries = AtomicLong()
    private val createdIndexPostings = AtomicLong()
    private val queuedReplays = AtomicLong()
    private val peakQueuedReplays = AtomicLong()
    private val e6Bindings = AtomicLong()
    private val e6AvoidedBindings = AtomicLong()
    private val e6Combinations = AtomicLong()

    data class Snapshot(
        val acceptedDeltas: Long,
        val cumulativeGroups: Long,
        val zeroOnlyEntries: Long,
        val indexPostings: Long,
        val createdGroups: Long,
        val createdZeroOnlyEntries: Long,
        val createdIndexPostings: Long,
        val queuedReplays: Long,
        val peakQueuedReplays: Long,
        val e6Bindings: Long,
        val e6AvoidedBindings: Long,
        val e6Combinations: Long,
    )

    internal fun accepted(newGroup: Boolean = false, zeroOnly: Boolean = false) {
        acceptedDeltas.incrementAndGet()
        if (newGroup) {
            cumulativeGroups.incrementAndGet()
            createdGroups.incrementAndGet()
        }
        if (zeroOnly) {
            zeroOnlyEntries.incrementAndGet()
            createdZeroOnlyEntries.incrementAndGet()
        }
    }

    internal fun posted() {
        indexPostings.incrementAndGet()
        createdIndexPostings.incrementAndGet()
    }

    internal fun releaseDirectory(groups: Int, zeroEntries: Int, postings: Long) {
        cumulativeGroups.addAndGet(-groups.toLong())
        zeroOnlyEntries.addAndGet(-zeroEntries.toLong())
        indexPostings.addAndGet(-postings)
    }

    internal fun replayQueued() {
        // Pending callbacks include an executing Inline callback, not only a physical queue.
        val count = queuedReplays.incrementAndGet()
        peakQueuedReplays.accumulateAndGet(count, ::maxOf)
    }

    internal fun replayFinished() { queuedReplays.decrementAndGet() }

    /** E6 totals are cumulative insertions and avoided duplicate member bindings. */
    fun recordE6(newBindings: Int, avoidedBindings: Int, newCombinations: Int) {
        require(newBindings >= 0 && avoidedBindings >= 0 && newCombinations >= 0)
        e6Bindings.addAndGet(newBindings.toLong())
        e6AvoidedBindings.addAndGet(avoidedBindings.toLong())
        e6Combinations.addAndGet(newCombinations.toLong())
    }

    /** Read after joining for a consistent aggregate; reads during work are a bounded live census. */
    fun snapshot(): Snapshot = Snapshot(acceptedDeltas.get(), cumulativeGroups.get(), zeroOnlyEntries.get(),
        indexPostings.get(), createdGroups.get(), createdZeroOnlyEntries.get(), createdIndexPostings.get(),
        queuedReplays.get(), peakQueuedReplays.get(), e6Bindings.get(),
        e6AvoidedBindings.get(), e6Combinations.get())
}
