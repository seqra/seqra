package org.opentaint.dataflow.bidi.engine

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.Collections

/** Optional numeric request-join measurements. No key, fact, event or trace is retained. */
class RequestBatchStatistics {
    private val phases = AtomicLong()
    private val continuations = AtomicLong()
    private val groupGeometryTests = AtomicLong()
    private val groupGeometrySteps = AtomicLong()
    private val memberScans = AtomicLong()
    private val answers = AtomicLong()
    private val climbs = AtomicLong()
    private val outgoingBatches = AtomicLong()
    private val outgoingMembers = AtomicLong()
    private val outgoingWidthHistogram = AtomicLongArray(31)
    private val maxOutgoingWidth = AtomicLong()
    private val duplicateTargetUnions = AtomicLong()
    private val peakPendingMembers = AtomicLong()
    private val peakPendingPrimitiveCapacity = AtomicLong()
    private val peakPendingLinkGroups = AtomicLong()
    private val peakPendingDeltaRoots = AtomicLong()
    private val peakCapturedLinkRoots = AtomicLong()
    private val maxServiceDelayNanos = AtomicLong()

    /** One flush accumulates in ordinary fields, then adds to the shared numeric totals. */
    internal class Counts {
        var phases = 0L
        var continuations = 0L
        var groupGeometryTests = 0L
        var groupGeometrySteps = 0L
        var memberScans = 0L
        var answers = 0L
        var climbs = 0L
        var outgoingBatches = 0L
        var outgoingMembers = 0L
        val outgoingWidthHistogram = LongArray(31)
        var maxOutgoingWidth = 0
        var duplicateTargetUnions = 0L

        fun acceptedBatch(width: Int) {
            require(width > 0) { "An outgoing request batch must be nonempty" }
            outgoingBatches++
            outgoingMembers += width
            outgoingWidthHistogram[31 - Integer.numberOfLeadingZeros(width)]++
            if (width > maxOutgoingWidth) maxOutgoingWidth = width
        }
    }

    internal fun record(counts: Counts) {
        phases.addAndGet(counts.phases)
        continuations.addAndGet(counts.continuations)
        groupGeometryTests.addAndGet(counts.groupGeometryTests)
        groupGeometrySteps.addAndGet(counts.groupGeometrySteps)
        memberScans.addAndGet(counts.memberScans)
        answers.addAndGet(counts.answers)
        climbs.addAndGet(counts.climbs)
        outgoingBatches.addAndGet(counts.outgoingBatches)
        outgoingMembers.addAndGet(counts.outgoingMembers)
        for (bin in counts.outgoingWidthHistogram.indices) {
            val count = counts.outgoingWidthHistogram[bin]
            if (count != 0L) outgoingWidthHistogram.addAndGet(bin, count)
        }
        maxOutgoingWidth.keepMaximum(counts.maxOutgoingWidth.toLong())
        duplicateTargetUnions.addAndGet(counts.duplicateTargetUnions)
    }

    internal fun pending(members: Long, primitiveCapacity: Long, linkGroups: Long, deltaRoots: Long, capturedRoots: Long) {
        peakPendingMembers.keepMaximum(members)
        peakPendingPrimitiveCapacity.keepMaximum(primitiveCapacity)
        peakPendingLinkGroups.keepMaximum(linkGroups)
        peakPendingDeltaRoots.keepMaximum(deltaRoots)
        peakCapturedLinkRoots.keepMaximum(capturedRoots)
    }

    internal fun serviceDelay(nanos: Long) { maxServiceDelayNanos.keepMaximum(nanos.coerceAtLeast(0L)) }

    data class Snapshot(
        val phases: Long,
        val continuations: Long,
        val groupGeometryTests: Long,
        /** Geometry-node inspections inside those group/row queries. */
        val groupGeometrySteps: Long,
        /** FLOW selection cells or TAINT concrete leaf ID probes; includes empty hash cells. */
        val memberScans: Long,
        val answers: Long,
        val climbs: Long,
        val outgoingBatches: Long,
        val outgoingMembers: Long,
        /** Accepted batches: bin k contains widths 2^k through 2^(k+1)-1, for k = 0..30. */
        val outgoingWidthHistogram: List<Long>,
        val maxOutgoingWidth: Long,
        val duplicateTargetUnions: Long,
        /** Maximum observed in one actor, not a simultaneous global live total. */
        val peakPendingMembers: Long,
        /** Owned request-builder/set int cells, not JVM bytes or hash-map capacity. */
        val peakPendingPrimitiveCapacity: Long,
        val peakPendingLinkGroups: Long,
        /** Pending AP delta wrappers/roots. This does not count their shared descendants or bytes. */
        val peakPendingDeltaRoots: Long,
        /** Old immutable group roots retained by one active phase view; overlaps other AP owners. */
        val peakCapturedLinkRoots: Long,
        /** Oldest pending work's observed wait to a flush service opportunity. */
        val maxServiceDelayNanos: Long,
    )

    fun snapshot(): Snapshot = Snapshot(phases.get(), continuations.get(), groupGeometryTests.get(), groupGeometrySteps.get(),
        memberScans.get(), answers.get(), climbs.get(), outgoingBatches.get(), outgoingMembers.get(),
        Collections.unmodifiableList(List(31) { outgoingWidthHistogram.get(it) }), maxOutgoingWidth.get(),
        duplicateTargetUnions.get(), peakPendingMembers.get(), peakPendingPrimitiveCapacity.get(),
        peakPendingLinkGroups.get(), peakPendingDeltaRoots.get(), peakCapturedLinkRoots.get(), maxServiceDelayNanos.get())

    companion object {
        fun ifEnabled(): RequestBatchStatistics? =
            if (java.lang.Boolean.getBoolean("org.opentaint.bidi.optimizationStatistics")) RequestBatchStatistics() else null
    }
}

private fun AtomicLong.keepMaximum(value: Long) {
    var old = get()
    while (value > old && !compareAndSet(old, value)) old = get()
}
