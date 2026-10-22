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
    private val staticGroupCallOffers = AtomicLong()
    private val staticGroupDirectCalls = AtomicLong()
    private val staticGroupFallbackCallsAdmitted = AtomicLong()
    private val staticGroupScalarApplications = AtomicLong()
    private val staticGroupSuffixEntries = AtomicLong()
    private val staticGroupDirectSupplyLeaves = AtomicLong()
    private val staticGroupFallbackJoinLeaves = AtomicLong()

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
        val staticGroupCallOffers: Long = 0,
        val staticGroupDirectCalls: Long = 0,
        val staticGroupFallbackCallsAdmitted: Long = 0,
        val staticGroupScalarApplications: Long = 0,
        val staticGroupSuffixEntries: Long = 0,
        val staticGroupDirectSupplyLeaves: Long = 0,
        val staticGroupFallbackJoinLeaves: Long = 0,
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

    /** Scalar offers include repeats rejected by a pending fallback key. */
    internal fun staticGroupCallOffered() { staticGroupCallOffers.incrementAndGet() }
    internal fun staticGroupCallDirect() { staticGroupDirectCalls.incrementAndGet() }
    internal fun staticGroupCallFallbackAdmitted() { staticGroupFallbackCallsAdmitted.incrementAndGet() }
    /** Count matched scalar reducer entries and nonempty suffix batches separately. */
    internal fun staticGroupScalarApplied() { staticGroupScalarApplications.incrementAndGet() }
    internal fun staticGroupSuffixEntered() { staticGroupSuffixEntries.incrementAndGet() }
    internal fun staticGroupSupplyDirect() { staticGroupDirectSupplyLeaves.incrementAndGet() }
    internal fun staticGroupJoinFallback() { staticGroupFallbackJoinLeaves.incrementAndGet() }

    /** Read after joining for a consistent aggregate; reads during work are a bounded live census. */
    fun snapshot(): Snapshot = Snapshot(acceptedDeltas.get(), cumulativeGroups.get(), zeroOnlyEntries.get(),
        indexPostings.get(), createdGroups.get(), createdZeroOnlyEntries.get(), createdIndexPostings.get(),
        queuedReplays.get(), peakQueuedReplays.get(), e6Bindings.get(),
        e6AvoidedBindings.get(), e6Combinations.get(), staticGroupCallOffers.get(), staticGroupDirectCalls.get(),
        staticGroupFallbackCallsAdmitted.get(), staticGroupScalarApplications.get(), staticGroupSuffixEntries.get(),
        staticGroupDirectSupplyLeaves.get(), staticGroupFallbackJoinLeaves.get())
}
