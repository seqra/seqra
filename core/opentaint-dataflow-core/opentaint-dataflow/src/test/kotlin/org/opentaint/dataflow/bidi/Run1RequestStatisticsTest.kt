package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.fixtures.Run1RequestPipelineFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class Run1RequestStatisticsTest {
    @Test fun `uncovered static relays use shared views and authoritative rows without inherited base`() {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val slot = Run1Slot.StaticField(cls,f.store.field("f"))
            val t = f.store.mark("T")
            h.declareIdentityStaticDomain(slot)
            val input = f.store.fact(STATIC,slot.pathData.toList(),Tail.STAR,MarkSlot.STAR)
            h.submit(f.subscription(input,slot.initial(f.ap)))
            h.drain()
            assertTrue(h.storage().borrowRequest(slot).precomputed.isEmpty)
            h.publish(slot,t.id)
            h.drain()
            assertEquals(setOf(Triple(f.caller,slot as Run1Slot,t.id)),h.climbs,
                "Without a prescan coverage certificate the ordinary same-field join remains")
            assertEquals(RequestMarkSet.of(t),h.needs(f.callee,slot))
            assertEquals(1,h.staticRequests.snapshot().sources)
            val totals = h.statistics.snapshot()
            assertEquals(1L,totals.replayClimbs)
            assertEquals(0L,totals.inheritedBaseRowsSkipped,
                "A D-only notification has no B row to skip even when queried slot equals original slot")
        }
    }

    @Test fun `request only actor measures P wait and retires its exact temporary gauges at commit`() {
        val statistics = Run1AbstractionStatistics()
        val batches = RequestBatchStatistics()
        MethodAnalysisFixtures(abstractionStatistics=statistics,requestBatchStatistics=batches).use { f ->
            val actor = f.core()
            val slot = Run1Slot.Base(AccessPathBase.Argument(0))
            val t = f.mark("T"); val u = f.mark("U")
            actor.collectRequests(slot,RequestMarkSet.fromIds(intArrayOf(t.id,u.id)))
            actor.collectRequests(slot,RequestMarkSet.of(u))
            assertTrue(f.enqueued.isNotEmpty(),"A request-only actor reserves ordinary Work")
            assertEquals(2L,statistics.snapshot().currentTemporaryRequestMembers)
            assertEquals(0L,batches.snapshot().maxServiceDelayNanos)
            f.drain(actor,quantum=1)
            val committed = statistics.snapshot()
            assertEquals(0L,committed.currentTemporaryRequestRows)
            assertEquals(0L,committed.currentTemporaryRequestMembers)
            assertEquals(1L,committed.temporaryDuplicateRequestMembers)
            val delay = batches.snapshot().maxServiceDelayNanos
            assertTrue(delay > 0,"The actual method-empty submission records the fresh P wait")
            actor.collectRequests(slot,RequestMarkSet.of(t))
            val rejected = statistics.snapshot()
            assertEquals(3L,rejected.requestOfferCalls)
            assertEquals(4L,rejected.offeredRequestMembers)
            assertEquals(1L,rejected.authoritativeDuplicateRequestMembers)
            assertEquals(2L,rejected.freshTemporaryRequestMembers)
            assertEquals(0L,rejected.currentTemporaryRequestMembers)
            assertEquals(delay,batches.snapshot().maxServiceDelayNanos,"An R duplicate creates no new pending clock")
        }
    }

    @Test fun `admission counters distinguish authoritative duplicates temporary duplicates and new members`() {
        val slot = Run1Slot.Base(AccessPathBase.Argument(0))
        val authority = RequestStorage(freezeRequestRows(mapOf(slot to RequestMarkSet.of(TaintMark.ZERO))))
        val temporary = RequestStorage()
        val statistics = Run1AbstractionStatistics()
        fun collect(vararg ids: Int) {
            val counts = RequestAdmissionCounts()
            val beforeRows = temporary.dynamicRows
            val beforeMembers = temporary.dynamicMembers
            val fresh = RequestMarkSet.fromIds(ids).minus(authority.borrow(slot), counts, {})
            temporary.addAll(slot,fresh,counts,{})
            counts.freshTemporaryMembers = temporary.dynamicMembers - beforeMembers
            statistics.requestOffer(counts)
            statistics.temporaryChanged((temporary.dynamicRows - beforeRows).toLong(),counts.freshTemporaryMembers)
        }
        collect(0,1)
        collect(0,1,2)
        val beforeCommit = statistics.snapshot()
        assertEquals(2L,beforeCommit.requestOfferCalls)
        assertEquals(5L,beforeCommit.offeredRequestMembers)
        assertEquals(2L,beforeCommit.authoritativeDuplicateRequestMembers)
        assertEquals(1L,beforeCommit.temporaryDuplicateRequestMembers)
        assertEquals(2L,beforeCommit.freshTemporaryRequestMembers)
        assertEquals(beforeCommit.offeredRequestMembers,beforeCommit.authoritativeDuplicateRequestMembers +
            beforeCommit.temporaryDuplicateRequestMembers + beforeCommit.freshTemporaryRequestMembers)
        assertEquals(1L,beforeCommit.currentTemporaryRequestRows)
        assertEquals(2L,beforeCommit.currentTemporaryRequestMembers)
        for ((key,marks) in temporary.pendingRows()) authority.addAll(key,marks)
        statistics.requestCommit(1,2,1)
        statistics.temporaryChanged(-temporary.dynamicRows.toLong(),-temporary.dynamicMembers)
        val afterCommit = statistics.snapshot()
        assertEquals(0L,afterCommit.currentTemporaryRequestRows)
        assertEquals(0L,afterCommit.currentTemporaryRequestMembers)
        assertEquals(2L,afterCommit.peakTemporaryRequestMembers)
        assertEquals(2L,afterCommit.freshRequestMembers)
        assertEquals(2L,beforeCommit.currentTemporaryRequestMembers,"Snapshots own scalar values")
    }

    @Test fun `cancelled admission counts only the examined prefix and does not create a P membership`() {
        val slot = Run1Slot.Base(AccessPathBase.Argument(0))
        val authority = RequestStorage(freezeRequestRows(mapOf(slot to RequestMarkSet.of(TaintMark.ZERO))))
        val counts = RequestAdmissionCounts()
        val cancellation = Cancellation()
        var checks = 0
        assertFailsWith<Cancellation.Cancelled> {
            RequestMarkSet.fromIds(intArrayOf(0,1,2)).minus(authority.borrow(slot),counts) {
                if (++checks == 2) cancellation.cancel()
                cancellation.checkpoint()
            }
        }
        assertEquals(1L,counts.offeredMembers)
        assertEquals(1L,counts.authoritativeDuplicates)
        assertEquals(0L,counts.temporaryDuplicates)
        assertEquals(0L,counts.freshTemporaryMembers)
    }

    @Test fun `current FLOW reads and inherited skips use input and row units independently of mark work`() {
        val statistics = Run1AbstractionStatistics()
        statistics.freshFlowInputVisit(growth=false)
        statistics.freshFlowInputVisit(growth=true)
        statistics.inheritedBaseRowSkipped()
        val snapshot = statistics.snapshot()
        assertEquals(2L,snapshot.freshFlowInputReads)
        assertEquals(1L,snapshot.growthFlowInputReads)
        assertEquals(1L,snapshot.inheritedBaseRowsSkipped)
        assertEquals(0L,snapshot.offeredRequestMembers)
        assertEquals(0L,snapshot.replayClimbs)
    }
}
