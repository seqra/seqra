package org.opentaint.dataflow.bidi.store

import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/** Optional numeric aggregates. Totals span stores/runs; live census is retired at store release.
 *  Array cells cover the owned N1 arrays, not JVM headers, path child maps or byte sizes. */
class N1Statistics {
    private val createdStores = AtomicLong()
    private val liveStores = AtomicLong()
    private val insertions = AtomicLong()
    private val duplicates = AtomicLong()
    private val markRequests = AtomicLong()
    private val positionRequests = AtomicLong()
    private val liveMarkRequests = AtomicLong()
    private val livePositionRequests = AtomicLong()
    private val markRows = AtomicLong()
    private val positionRows = AtomicLong()
    private val liveMarkRows = AtomicLong()
    private val livePositionRows = AtomicLong()
    private val liveOrigins = AtomicLong()
    private val liveIndexNodes = AtomicLong()
    private val liveIndexBuckets = AtomicLong()
    private val livePrimitiveCells = AtomicLong()
    private val liveReferenceCells = AtomicLong()
    private val allocatedPrimitiveCells = AtomicLong()
    private val allocatedReferenceCells = AtomicLong()
    private val rowSizes = Array(31) { AtomicLong() }
    private val releasedRowSizes = Array(31) { AtomicLong() }
    private val coarseCandidates = AtomicLong()
    private val selectedCandidates = AtomicLong()
    private val overlappingCandidates = AtomicLong()
    private val existingLinkCandidates = AtomicLong()
    private val selectedExistingLinks = AtomicLong()
    private val answers = AtomicLong()
    private val climbs = AtomicLong()
    private val noActions = AtomicLong()

    internal fun newCensus(): Census {
        createdStores.incrementAndGet()
        liveStores.incrementAndGet()
        return Census()
    }

    internal fun duplicate() { duplicates.incrementAndGet() }
    internal class Candidates {
        var coarse = 0L
        var selected = 0L
        var overlapping = 0L
    }
    internal class Actions {
        var existingLinks = 0L
        var selectedLinks = 0L
        var answers = 0L
        var climbs = 0L
        var none = 0L
    }
    internal fun candidates(counts: Candidates) {
        coarseCandidates.addAndGet(counts.coarse)
        selectedCandidates.addAndGet(counts.selected)
        overlappingCandidates.addAndGet(counts.overlapping)
    }
    internal fun actions(counts: Actions) {
        existingLinkCandidates.addAndGet(counts.existingLinks)
        selectedExistingLinks.addAndGet(counts.selectedLinks)
        answers.addAndGet(counts.answers); climbs.addAndGet(counts.climbs); noActions.addAndGet(counts.none)
    }

    /** Fixed-size, store-local census; it retains no relation keys or values. */
    internal inner class Census {
        private var marks = 0L
        private var positions = 0L
        private var markRowCount = 0L
        private var positionRowCount = 0L
        private var origins = 0L
        private var nodes = 0L
        private var buckets = 0L
        private var primitiveCells = 0L
        private var referenceCells = 0L
        private val sizes = LongArray(31)

        fun origin() { origins++; liveOrigins.incrementAndGet() }
        fun node() { nodes++; liveIndexNodes.incrementAndGet() }
        fun bucket() { buckets++; liveIndexBuckets.incrementAndGet() }
        fun row(mark: Boolean) {
            if (mark) {
                markRowCount++; markRows.incrementAndGet(); liveMarkRows.incrementAndGet()
            } else {
                positionRowCount++; positionRows.incrementAndGet(); livePositionRows.incrementAndGet()
            }
        }
        fun member(mark: Boolean, previousSize: Int) {
            insertions.incrementAndGet()
            if (mark) {
                marks++; markRequests.incrementAndGet(); liveMarkRequests.incrementAndGet()
            } else {
                positions++; positionRequests.incrementAndGet(); livePositionRequests.incrementAndGet()
            }
            if (previousSize != 0) {
                val oldBin = 31 - Integer.numberOfLeadingZeros(previousSize)
                sizes[oldBin]--; rowSizes[oldBin].decrementAndGet()
            }
            val bin = 31 - Integer.numberOfLeadingZeros(Math.addExact(previousSize, 1))
            sizes[bin]++; rowSizes[bin].incrementAndGet()
        }
        fun primitiveArray(oldSize: Int, newSize: Int) {
            val difference = newSize.toLong() - oldSize
            primitiveCells += difference
            livePrimitiveCells.addAndGet(difference)
            allocatedPrimitiveCells.addAndGet(newSize.toLong())
        }
        fun referenceArray(oldSize: Int, newSize: Int) {
            val difference = newSize.toLong() - oldSize
            referenceCells += difference
            liveReferenceCells.addAndGet(difference)
            allocatedReferenceCells.addAndGet(newSize.toLong())
        }
        fun release() {
            liveStores.decrementAndGet()
            liveMarkRequests.addAndGet(-marks); livePositionRequests.addAndGet(-positions)
            liveMarkRows.addAndGet(-markRowCount); livePositionRows.addAndGet(-positionRowCount)
            liveOrigins.addAndGet(-origins); liveIndexNodes.addAndGet(-nodes); liveIndexBuckets.addAndGet(-buckets)
            livePrimitiveCells.addAndGet(-primitiveCells); liveReferenceCells.addAndGet(-referenceCells)
            for (index in sizes.indices) {
                rowSizes[index].addAndGet(-sizes[index])
                releasedRowSizes[index].addAndGet(sizes[index])
            }
        }
    }

    data class Snapshot(
        val createdStores: Long, val liveStores: Long,
        val insertions: Long, val duplicates: Long, val markRequests: Long, val positionRequests: Long,
        val liveMarkRequests: Long, val livePositionRequests: Long,
        val markRows: Long, val positionRows: Long, val liveMarkRows: Long, val livePositionRows: Long,
        val liveOrigins: Long, val liveIndexNodes: Long, val liveIndexBuckets: Long,
        val livePrimitiveCells: Long, val liveReferenceCells: Long,
        val allocatedPrimitiveCells: Long, val allocatedReferenceCells: Long,
        /** Bin k contains live rows with 2^k through 2^(k+1)-1 members. */
        val liveRowSizeHistogram: List<Long>,
        /** Final row sizes accumulated at explicit release, once per store. Not a simultaneous census. */
        val releasedRowSizeHistogram: List<Long>,
        /** These three totals cover new-link visitor queries only. */
        val coarseCandidates: Long, val selectedCandidates: Long, val overlappingCandidates: Long,
        /** Existing-link totals cover new-request queries. Action totals cover both arrival orders. */
        val existingLinkCandidates: Long, val selectedExistingLinks: Long,
        val answers: Long, val climbs: Long, val noActions: Long,
    )

    fun snapshot(): Snapshot = Snapshot(createdStores.get(), liveStores.get(), insertions.get(), duplicates.get(),
        markRequests.get(), positionRequests.get(), liveMarkRequests.get(), livePositionRequests.get(),
        markRows.get(), positionRows.get(), liveMarkRows.get(), livePositionRows.get(), liveOrigins.get(),
        liveIndexNodes.get(), liveIndexBuckets.get(), livePrimitiveCells.get(), liveReferenceCells.get(),
        allocatedPrimitiveCells.get(), allocatedReferenceCells.get(),
        Collections.unmodifiableList(rowSizes.map { it.get() }),
        Collections.unmodifiableList(releasedRowSizes.map { it.get() }), coarseCandidates.get(), selectedCandidates.get(),
        overlappingCandidates.get(), existingLinkCandidates.get(), selectedExistingLinks.get(),
        answers.get(), climbs.get(), noActions.get())

    companion object {
        const val PROPERTY = "org.opentaint.bidi.optimizationStatistics"
        fun ifEnabled(): N1Statistics? = if (java.lang.Boolean.getBoolean(PROPERTY)) N1Statistics() else null
    }
}
