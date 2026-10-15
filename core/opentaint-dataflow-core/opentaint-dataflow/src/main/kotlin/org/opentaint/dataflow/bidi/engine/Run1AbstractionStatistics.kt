package org.opentaint.dataflow.bidi.engine

import java.util.concurrent.atomic.AtomicLong

/** Numeric counters do not retain methods, initials, suppliers or profile objects. */
class Run1AbstractionStatistics {
    private val inputArrivals = AtomicLong()
    private val restrictedIntersections = AtomicLong()
    private val initialCandidates = AtomicLong()
    private val newInitials = AtomicLong()
    private val pendingShapes = AtomicLong()
    private val pendingMarks = AtomicLong()
    private val peakPendingShapes = AtomicLong()
    private val peakPendingMarks = AtomicLong()
    private val consumedPendingMarks = AtomicLong()
    private val policies = AtomicLong()
    private val members = AtomicLong()
    private val peakPolicies = AtomicLong()
    private val peakMembers = AtomicLong()
    private val deliveries = AtomicLong()
    private val duplicateMarks = AtomicLong()
    private val coveredMarks = AtomicLong()
    private val structuralCandidates = AtomicLong()
    private val replayVisits = AtomicLong()
    private val replayCandidates = AtomicLong()
    private val replayTests = AtomicLong()
    private val replayAnswers = AtomicLong()
    private val replayClimbs = AtomicLong()
    private val maxReplayVisitNanos = AtomicLong()
    private val maxReplayVisitCandidates = AtomicLong()

    internal fun admission(newPolicy: Boolean, fresh: Int, duplicates: Int) {
        deliveries.incrementAndGet()
        duplicateMarks.addAndGet(duplicates.toLong())
        if (newPolicy) peakPolicies.accumulateAndGet(policies.incrementAndGet(), ::maxOf)
        peakMembers.accumulateAndGet(members.addAndGet(fresh.toLong()), ::maxOf)
    }

    internal fun arrival() { inputArrivals.incrementAndGet() }
    internal fun intersection() { restrictedIntersections.incrementAndGet() }
    internal fun initial(fresh: Boolean) {
        initialCandidates.incrementAndGet()
        if (fresh) newInitials.incrementAndGet()
    }
    internal fun memoChange(shapes: Long, marks: Long, consumed: Long = 0) {
        peakPendingShapes.accumulateAndGet(pendingShapes.addAndGet(shapes), ::maxOf)
        peakPendingMarks.accumulateAndGet(pendingMarks.addAndGet(marks), ::maxOf)
        consumedPendingMarks.addAndGet(consumed)
    }
    internal fun covered() { coveredMarks.incrementAndGet() }
    internal fun structural() { structuralCandidates.incrementAndGet() }
    /** One question/current-input visit, including partial work interrupted by cancellation. */
    internal fun replayVisit(candidates: Long, tests: Long, answers: Long, climbs: Long, nanos: Long) {
        replayVisits.incrementAndGet()
        replayCandidates.addAndGet(candidates)
        replayTests.addAndGet(tests)
        replayAnswers.addAndGet(answers)
        replayClimbs.addAndGet(climbs)
        maxReplayVisitNanos.accumulateAndGet(nanos, ::maxOf)
        maxReplayVisitCandidates.accumulateAndGet(candidates, ::maxOf)
    }
    internal fun release(policyCount: Long, memberCount: Long) {
        policies.addAndGet(-policyCount)
        members.addAndGet(-memberCount)
    }

    data class Snapshot(
        val inputArrivals: Long, val restrictedIntersections: Long,
        val initialCandidates: Long, val newInitials: Long,
        val currentPendingShapes: Long, val currentPendingMarks: Long,
        val peakPendingShapes: Long, val peakPendingMarks: Long, val consumedPendingMarks: Long,
        val currentPolicies: Long, val currentPolicyMarkCells: Long,
        val peakPolicies: Long, val peakPolicyMarkCells: Long,
        val deliveries: Long, val duplicateMarks: Long,
        val coveredMarks: Long, val structuralCandidates: Long,
        val replayVisits: Long, val replayCandidates: Long, val replayTests: Long,
        val replayAnswers: Long, val replayClimbs: Long,
        val maxReplayVisitNanos: Long, val maxReplayVisitCandidates: Long,
    )

    fun snapshot(): Snapshot = Snapshot(inputArrivals.get(), restrictedIntersections.get(),
        initialCandidates.get(), newInitials.get(), pendingShapes.get(), pendingMarks.get(),
        peakPendingShapes.get(), peakPendingMarks.get(), consumedPendingMarks.get(), policies.get(), members.get(), peakPolicies.get(), peakMembers.get(),
        deliveries.get(), duplicateMarks.get(), coveredMarks.get(), structuralCandidates.get(),
        replayVisits.get(), replayCandidates.get(), replayTests.get(), replayAnswers.get(), replayClimbs.get(),
        maxReplayVisitNanos.get(), maxReplayVisitCandidates.get())
}
