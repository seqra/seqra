package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import kotlin.test.*

class N2StatisticsTest {
    @Test fun `aggregate counts queued fresh replays and current directory without retaining payloads`(): Unit {
        val steps = QueuedProtocolSteps()
        PipelineFixtures(protocol = steps).use { f ->
            val statistics = N2Statistics()
            val port = object : SubscriptionPort by f.port { override val statistics = statistics }
            val manager = SubscriptionManager(port)
            try {
                val a = f.subscription(f.store.fact(path = listOf(f.store.field("a"))))
                val b = a.copy(added = f.store.fact(path = listOf(f.store.field("b"))))
                val samePosition = a.copy(added = f.store.fact(path = listOf(f.store.field("a")),
                    mark = MarkSlot.Concrete(f.store.mark("U"))))
                manager.subscribe(a)
                manager.subscribe(b)
                manager.subscribe(samePosition)
                manager.subscribe(a)
                manager.subscribe(f.subscription(Reach.NORMAL, zeroOnly = true))
                manager.subscribe(f.subscription(Reach.DEMAND, zeroOnly = true))
                val queued = statistics.snapshot()
                assertEquals(4L, queued.acceptedDeltas)
                assertEquals(1L, queued.cumulativeGroups)
                assertEquals(1L, queued.zeroOnlyEntries)
                assertEquals(2L, queued.indexPostings, "Another mark at one position adds no posting")
                assertEquals(1L, queued.createdGroups)
                assertEquals(1L, queued.createdZeroOnlyEntries)
                assertEquals(2L, queued.createdIndexPostings)
                assertEquals(4L, queued.queuedReplays)
                assertEquals(4L, queued.peakQueuedReplays)
                steps.replayAll()
                assertEquals(0L, statistics.snapshot().queuedReplays)
                val pub = Publication(f.ap.initial(f.store.pattern(tail = Tail.STAR, mark = MarkSlot.STAR)),
                    f.store.fact(AccessPathBase.Return, tail = Tail.STAR, mark = MarkSlot.STAR))
                manager.onDelivery(f.callee, listOf(pub))
                assertEquals(1, f.summaries.size)
                assertEquals(queued.acceptedDeltas, statistics.snapshot().acceptedDeltas)
            } finally { manager.freeze() }
            val released = statistics.snapshot()
            assertEquals(0L, released.cumulativeGroups)
            assertEquals(0L, released.zeroOnlyEntries)
            assertEquals(0L, released.indexPostings)
            assertEquals(4L, released.acceptedDeltas)
            assertEquals(1L, released.createdGroups)
            assertEquals(1L, released.createdZeroOnlyEntries)
            assertEquals(2L, released.createdIndexPostings, "Per-run creation deltas remain available after release")
            assertTrue(N2Statistics::class.java.declaredFields.all {
                java.util.concurrent.atomic.AtomicLong::class.java.isAssignableFrom(it.type)
            }, "The aggregate holds counters, without facts, forms, keys or store references")
        }
    }

    @Test fun `cleanup counters separate admitted slots from completed singleton FLOW blocks`() {
        val statistics = N2Statistics()
        statistics.fullCleanupBlock(suffix = false, steps = 5, inputs = 4)
        statistics.fullCleanupBlock(suffix = true, steps = 3, inputs = 1)
        statistics.fullCleanupSingletonFlow(steps = 3)
        statistics.fullCleanupFallback()
        statistics.fullCleanupRequested(2)
        val result = statistics.snapshot()
        assertEquals(2L, result.fullCleanupBlocks)
        assertEquals(1L, result.fullCleanupSuffixEntries)
        assertEquals(23L, result.fullCleanupSelectedSteps)
        assertEquals(5L, result.fullCleanupInputs)
        assertEquals(1L, result.fullCleanupSingletonFlowInputs)
        assertEquals(3L, result.fullCleanupSingletonFlowSteps)
        assertEquals(1L, result.fullCleanupFallbacks)
        assertEquals(2L, result.fullCleanupRequestMembers)
    }

    @Test fun `failed replay releases its pending count and E6 census remains numeric`(): Unit {
        val steps = QueuedProtocolSteps()
        PipelineFixtures(protocol = steps).use { f ->
            val statistics = N2Statistics()
            val manager = SubscriptionManager(object : SubscriptionPort by f.port { override val statistics = statistics })
            manager.subscribe(f.subscription())
            assertEquals(1L, statistics.snapshot().queuedReplays)
            f.store.cancellation.cancel()
            assertFails { steps.replayAll() }
            assertEquals(0L, statistics.snapshot().queuedReplays)
            f.store.cancellation.activate()
            statistics.recordE6(newBindings = 2, avoidedBindings = 3, newCombinations = 4)
            assertEquals(2L, statistics.snapshot().e6Bindings)
            assertEquals(3L, statistics.snapshot().e6AvoidedBindings)
            assertEquals(4L, statistics.snapshot().e6Combinations)
            manager.freeze()
        }
    }
}
