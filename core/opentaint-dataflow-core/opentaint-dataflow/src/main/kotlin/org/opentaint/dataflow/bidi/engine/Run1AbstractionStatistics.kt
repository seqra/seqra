package org.opentaint.dataflow.bidi.engine

import java.util.concurrent.atomic.AtomicLong

/** Numeric counters do not retain methods, initials, suppliers or profile objects. */
class Run1AbstractionStatistics {
    private val inputArrivals = AtomicLong()
    private val restrictedIntersections = AtomicLong()
    private val initialCandidates = AtomicLong()
    private val newInitials = AtomicLong()
    private val inheritedBaseRowsSkipped = AtomicLong()
    private val structuralCandidates = AtomicLong()
    private val replayVisits = AtomicLong()
    private val replayCandidates = AtomicLong()
    private val replayTests = AtomicLong()
    private val replayAnswers = AtomicLong()
    private val replayClimbs = AtomicLong()
    private val maxReplayVisitNanos = AtomicLong()
    private val maxReplayVisitCandidates = AtomicLong()
    private val requestCommits = AtomicLong()
    private val freshRequestRows = AtomicLong()
    private val freshRequestMembers = AtomicLong()
    private val createdDynamicRows = AtomicLong()
    private val requestMatcherCalls = AtomicLong()
    private val requestDirectLookups = AtomicLong()
    private val requestBroadScans = AtomicLong()
    private val requestScannedKeys = AtomicLong()
    private val requestMatchedRows = AtomicLong()
    private val requestFrozenSlotsExamined = AtomicLong()
    private val requestDynamicKeysExamined = AtomicLong()
    private val temporaryRows = AtomicLong()
    private val temporaryMembers = AtomicLong()
    private val peakTemporaryRows = AtomicLong()
    private val peakTemporaryMembers = AtomicLong()
    private val requestOfferCalls = AtomicLong()
    private val offeredRequestMembers = AtomicLong()
    private val authoritativeDuplicateRequestMembers = AtomicLong()
    private val temporaryDuplicateRequestMembers = AtomicLong()
    private val freshTemporaryRequestMembers = AtomicLong()
    private val freshFlowInputReads = AtomicLong()
    private val growthFlowInputReads = AtomicLong()

    internal fun requestOffer(counts: RequestAdmissionCounts) {
        requestOfferCalls.incrementAndGet(); offeredRequestMembers.addAndGet(counts.offeredMembers)
        authoritativeDuplicateRequestMembers.addAndGet(counts.authoritativeDuplicates)
        temporaryDuplicateRequestMembers.addAndGet(counts.temporaryDuplicates)
        freshTemporaryRequestMembers.addAndGet(counts.freshTemporaryMembers)
    }
    /** An admitted FLOW input's actual current value was read; growth is a subset of these reads. */
    internal fun freshFlowInputVisit(growth: Boolean) {
        freshFlowInputReads.incrementAndGet()
        if (growth) growthFlowInputReads.incrementAndGet()
    }

    internal fun requestCommit(rows: Int, marks: Long, createdRows: Int) {
        requestCommits.incrementAndGet(); freshRequestRows.addAndGet(rows.toLong())
        freshRequestMembers.addAndGet(marks); createdDynamicRows.addAndGet(createdRows.toLong())
    }
    internal fun requestMatch(counts: RequestMatchCounts, returnedRows: Int) {
        requestMatcherCalls.incrementAndGet(); requestDirectLookups.addAndGet(counts.directLookups)
        requestBroadScans.addAndGet(counts.broadScans); requestScannedKeys.addAndGet(counts.scannedKeys)
        requestMatchedRows.addAndGet(returnedRows.toLong())
        requestFrozenSlotsExamined.addAndGet(counts.frozenSlotsExamined)
        requestDynamicKeysExamined.addAndGet(counts.dynamicKeysExamined)
    }
    internal fun temporaryChanged(rows: Long, marks: Long) {
        peakTemporaryRows.accumulateAndGet(temporaryRows.addAndGet(rows), ::maxOf)
        peakTemporaryMembers.accumulateAndGet(temporaryMembers.addAndGet(marks), ::maxOf)
    }

    internal fun arrival() { inputArrivals.incrementAndGet() }
    internal fun intersection() { restrictedIntersections.incrementAndGet() }
    internal fun initial(fresh: Boolean) {
        initialCandidates.incrementAndGet()
        if (fresh) newInitials.incrementAndGet()
    }
    internal fun inheritedBaseRowSkipped() { inheritedBaseRowsSkipped.incrementAndGet() }
    internal fun structural() { structuralCandidates.incrementAndGet() }
    /** One finite request-batch/current-input visit, including work interrupted by cancellation. */
    internal fun replayVisit(candidates: Long, tests: Long, answers: Long, climbs: Long, nanos: Long) {
        replayVisits.incrementAndGet()
        replayCandidates.addAndGet(candidates)
        replayTests.addAndGet(tests)
        replayAnswers.addAndGet(answers)
        replayClimbs.addAndGet(climbs)
        maxReplayVisitNanos.accumulateAndGet(nanos, ::maxOf)
        maxReplayVisitCandidates.accumulateAndGet(candidates, ::maxOf)
    }
    data class Snapshot(
        val inputArrivals: Long, val restrictedIntersections: Long,
        val initialCandidates: Long, val newInitials: Long,
        /** Matched inherited B rows skipped as a whole; no mark enumeration is counted here. */
        val inheritedBaseRowsSkipped: Long, val structuralCandidates: Long,
        val replayVisits: Long, val replayCandidates: Long, val replayTests: Long,
        val replayAnswers: Long, val replayClimbs: Long,
        val maxReplayVisitNanos: Long, val maxReplayVisitCandidates: Long,
        val requestCommits: Long, val freshRequestRows: Long, val freshRequestMembers: Long,
        val createdDynamicRequestRows: Long, val requestMatcherCalls: Long, val requestDirectLookups: Long,
        val requestBroadScans: Long, val requestScannedKeys: Long, val requestMatchedRows: Long,
        val currentTemporaryRequestRows: Long, val currentTemporaryRequestMembers: Long,
        val peakTemporaryRequestRows: Long, val peakTemporaryRequestMembers: Long,
        /** Actor collectRequests calls, including offers wholly rejected by effective R. */
        val requestOfferCalls: Long,
        /** Mark IDs examined by those calls before effective R filtering. */
        val offeredRequestMembers: Long,
        val authoritativeDuplicateRequestMembers: Long, val temporaryDuplicateRequestMembers: Long,
        /** Accepted P memberships; overlaps eventual D admission counts and is not a fact count. */
        val freshTemporaryRequestMembers: Long,
        /** Actual current-value reads for newly admitted FLOW inputs, not logical facts or marks. */
        val freshFlowInputReads: Long, val growthFlowInputReads: Long,
        /** Stored frozen S slots and dynamic keys examined; sum equals requestScannedKeys. */
        val requestFrozenSlotsExamined: Long, val requestDynamicKeysExamined: Long,
    )

    fun snapshot(): Snapshot = Snapshot(inputArrivals.get(), restrictedIntersections.get(),
        initialCandidates.get(), newInitials.get(), inheritedBaseRowsSkipped.get(), structuralCandidates.get(),
        replayVisits.get(), replayCandidates.get(), replayTests.get(), replayAnswers.get(), replayClimbs.get(),
        maxReplayVisitNanos.get(), maxReplayVisitCandidates.get(), requestCommits.get(), freshRequestRows.get(),
        freshRequestMembers.get(), createdDynamicRows.get(), requestMatcherCalls.get(), requestDirectLookups.get(),
        requestBroadScans.get(), requestScannedKeys.get(), requestMatchedRows.get(), temporaryRows.get(), temporaryMembers.get(),
        peakTemporaryRows.get(), peakTemporaryMembers.get(), requestOfferCalls.get(), offeredRequestMembers.get(),
        authoritativeDuplicateRequestMembers.get(), temporaryDuplicateRequestMembers.get(), freshTemporaryRequestMembers.get(),
        freshFlowInputReads.get(), growthFlowInputReads.get(), requestFrozenSlotsExamined.get(), requestDynamicKeysExamined.get())
}
