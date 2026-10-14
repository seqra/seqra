package org.opentaint.dataflow.bidi.engine

import java.util.concurrent.atomic.AtomicLong

/** Numeric counters do not retain methods, initials, suppliers or profile objects. */
class Run1AbstractionStatistics {
    private val policies = AtomicLong()
    private val members = AtomicLong()
    private val peakPolicies = AtomicLong()
    private val peakMembers = AtomicLong()
    private val deliveries = AtomicLong()
    private val duplicateMarks = AtomicLong()
    private val coveredMarks = AtomicLong()
    private val structuralCandidates = AtomicLong()

    internal fun admission(newPolicy: Boolean, fresh: Int, duplicates: Int) {
        deliveries.incrementAndGet()
        duplicateMarks.addAndGet(duplicates.toLong())
        if (newPolicy) peakPolicies.accumulateAndGet(policies.incrementAndGet(), ::maxOf)
        peakMembers.accumulateAndGet(members.addAndGet(fresh.toLong()), ::maxOf)
    }

    internal fun covered() { coveredMarks.incrementAndGet() }
    internal fun structural() { structuralCandidates.incrementAndGet() }
    internal fun release(policyCount: Long, memberCount: Long) {
        policies.addAndGet(-policyCount)
        members.addAndGet(-memberCount)
    }

    data class Snapshot(
        val currentPolicies: Long, val currentPolicyMarkCells: Long,
        val peakPolicies: Long, val peakPolicyMarkCells: Long,
        val deliveries: Long, val duplicateMarks: Long,
        val coveredMarks: Long, val structuralCandidates: Long,
    )

    fun snapshot(): Snapshot = Snapshot(policies.get(), members.get(), peakPolicies.get(), peakMembers.get(),
        deliveries.get(), duplicateMarks.get(), coveredMarks.get(), structuralCandidates.get())
}
