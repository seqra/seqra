package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.engine.RequestBatchStatistics
import kotlin.test.*

class RequestBatchStatisticsTest {
    @Test
    fun `accepted batch widths have exact power of two bins and owned numeric snapshots`(): Unit {
        val statistics = RequestBatchStatistics()
        val first = RequestBatchStatistics.Counts()
        for (width in listOf(1, 2, 3, 4, 7, 8, 31, 32)) first.acceptedBatch(width)
        statistics.record(first)
        val before = statistics.snapshot()
        assertEquals(8L, before.outgoingBatches)
        assertEquals(88L, before.outgoingMembers)
        assertEquals(32L, before.maxOutgoingWidth)
        assertEquals(listOf(1L, 2L, 2L, 1L, 1L, 1L) + List(25) { 0L }, before.outgoingWidthHistogram)
        assertFailsWith<UnsupportedOperationException> {
            (before.outgoingWidthHistogram as MutableList<Long>)[0] = 10L
        }
        val second = RequestBatchStatistics.Counts()
        second.acceptedBatch(Int.MAX_VALUE)
        statistics.record(second)
        val after = statistics.snapshot()
        assertEquals(9L, after.outgoingBatches)
        assertEquals(88L + Int.MAX_VALUE, after.outgoingMembers)
        assertEquals(Int.MAX_VALUE.toLong(), after.maxOutgoingWidth)
        assertEquals(1L, after.outgoingWidthHistogram[30])
        assertEquals(0L, before.outgoingWidthHistogram[30], "Earlier snapshots own their numeric values")
        assertFails { RequestBatchStatistics.Counts().acceptedBatch(0) }
    }
}
