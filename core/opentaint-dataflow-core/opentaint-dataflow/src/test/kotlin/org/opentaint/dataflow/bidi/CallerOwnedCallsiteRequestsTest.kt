package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import org.opentaint.dataflow.bidi.fixtures.Run1RequestPipelineFixtures
import org.opentaint.dataflow.bidi.interp.CallPoint
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

/** Two positive frontiers over the *same* caller input relation. */
class CallerOwnedCallsiteRequestsTest {
    @Test fun `pending requests and deferred old input replay never answer a pair twice`() {
        val steps = QueuedProtocolSteps()
        Run1RequestPipelineFixtures(steps).use { h ->
            val f = h.f
            val slot = Run1Slot.Base(AccessPathBase.Argument(0))
            val t = f.store.mark("T")
            val input = f.subscription(f.store.fact(mark = MarkSlot.Concrete(t)))
            h.submit(input); h.drain()
            h.publish(slot, t.id)
            steps.runNotifications()
            val notification = assertIs<RunEvent.ChangedRequests>(h.events.removeFirst())
            h.subscriptions.onRequests(notification.callee, notification.rows)
            val key = input.callSiteKey
            assertEquals(1L, h.subscriptions.requestState(key)?.pendingMembers)
            assertEquals(0L, h.subscriptions.requestState(key)?.answeredMembers)
            // A new full layer/header candidate arrives while the same T is still P.
            h.submit(input.copy(ref = input.ref.copy(callerLayer = Layer.DEMAND),
                linkLayer = Layer.DEMAND, added = input.added.withLayer(Layer.DEMAND)))
            h.drain()
            assertEquals(0L, h.subscriptions.requestState(key)?.pendingMembers)
            assertEquals(1L, h.subscriptions.requestState(key)?.answeredMembers)
            assertEquals(2L, h.statistics.snapshot().replayCandidates)
            val answersBeforeOldReplay = h.sent.filterIsInstance<RunEvent.InitialAnswersIn>().size
            steps.replayAll(); h.drain()
            assertEquals(answersBeforeOldReplay, h.sent.filterIsInstance<RunEvent.InitialAnswersIn>().size,
                "Old input callbacks must use A captured before this P drain")
            assertEquals(2L, h.statistics.snapshot().replayCandidates)
            h.submit(input); h.publish(slot, t.id); steps.runNotifications(); steps.replayAll(); h.drain()
            assertEquals(answersBeforeOldReplay, h.sent.filterIsInstance<RunEvent.InitialAnswersIn>().size)
            assertTrue(h.sent.none { it is RunEvent.LinkIn })
        }
    }

    @Test fun `late actual marks use installed A while absent marks never become initials`() {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val slot = Run1Slot.Base(AccessPathBase.Argument(0))
            val t = f.store.mark("T"); val u = f.store.mark("U")
            val input = f.subscription(f.store.fact(mark = MarkSlot.Concrete(u)))
            h.submit(input); h.publish(slot, t.id); h.drain()
            assertTrue(h.initials.none { it.second.mark == MarkSlot.Concrete(t) })
            h.submit(input.copy(added = f.store.fact(mark = MarkSlot.Concrete(t))))
            h.drain()
            val exact = f.ap.initial(slot.base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertTrue(f.callee to exact in h.initials)
            assertEquals(1L, h.subscriptions.requestState(input.callSiteKey)?.answeredMembers)
            assertTrue(h.initials.none { it.second.mark == MarkSlot.Concrete(u) })
        }
    }

    @Test fun `call stages partition inputs while mixed member layers retain all full ND rows`() {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f; val t = f.store.mark("T"); val u = f.store.mark("U")
            val tInitial = f.ap.initial(AccessPathBase.Argument(0), null, Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(t))
            val uInitial = f.ap.initial(AccessPathBase.Argument(0), null, Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(u))
            val first = f.subscription(f.store.fact(mark = MarkSlot.Concrete(t)), tInitial,
                callerLayer = Layer.NORMAL)
            val second = f.subscription(f.store.fact(mark = MarkSlot.Concrete(u), layer = Layer.DEMAND),
                uInitial, callerLayer = Layer.DEMAND)
            val otherStage = first.copy(stageFrom = CallPoint.BOUND, stageTo = CallPoint.ADDED)
            h.submit(first); h.submit(second); h.submit(otherStage); h.drain()
            assertEquals(2, h.subscriptions.requestSnapshot().callsites,
                "Caller layer is a row contribution, call stage is structural scope")
            val premise = f.ap.union(tInitial, uInitial)
            val out = f.store.fact(mark = MarkSlot.Concrete(t), layer = Layer.DEMAND)
            h.storage().publish(listOf(Publication(premise, out))); h.drain()
            val regular = f.summaries.filter { it.part.stageFrom == first.stageFrom && it.part.stageTo == first.stageTo }
            assertEquals(setOf(0,1), regular.map { it.member }.toSet())
            assertEquals(setOf(Layer.NORMAL, Layer.DEMAND), regular.map { it.part.linkLayer }.toSet())
            assertEquals(setOf(tInitial,uInitial), regular.map { it.part.ref.premise }.toSet())
            assertEquals(1, f.summaries.count { it.part.stageFrom == CallPoint.BOUND && it.part.stageTo == CallPoint.ADDED })
        }
    }

    @Test fun `packed request matching keeps slot association and ignores incoming E and mx`() {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f; val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val left = Run1Slot.StaticField(cls, f.store.field("f"))
            val right = Run1Slot.StaticField(cls, f.store.field("g"))
            val t = f.store.mark("T"); val u = f.store.mark("U")
            val requests = RequestStorage()
            requests.addAll(left, RequestMarkSet.of(t)); requests.addAll(right, RequestMarkSet.of(u))
            val a = f.store.fact(STATIC, left.pathData.toList(), Tail.STAR,
                MarkSlot.Star(MarkSet(intArrayOf(t.id))), ExclusionSet.of(right.field)) as FlowTree
            val b = f.store.fact(STATIC, right.pathData.toList(), Tail.STAR,
                MarkSlot.Star(MarkSet(intArrayOf(t.id))), ExclusionSet.of(right.field)) as FlowTree
            val packed = a.withRoot(FlowAlgebra.mergeAdd(a.root,b.root))
            assertNull(directRun1RequestSlot(packed))
            val counts = RequestMatchCounts()
            val rows = requests.borrowMatchingRows(packed, {}, counts)
            assertEquals(setOf(left,right), rows.map { it.slot }.toSet())
            assertEquals(2L, counts.scannedKeys)
            assertEquals(RequestMarkSet.of(t), rows.single { it.slot == left }.marks.dynamic)
            assertEquals(RequestMarkSet.of(u), rows.single { it.slot == right }.marks.dynamic)
            assertEquals(left, directRun1RequestSlot(a))
        }
    }

    @Test fun `covered same-field component is skipped without suppressing changed-field need`() {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f; val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val left = Run1Slot.StaticField(cls, f.store.field("f"))
            val right = Run1Slot.StaticField(cls, f.store.field("g"))
            h.declareIdentityStaticDomain(left,right,prescanCall = true)
            val t = f.store.mark("T"); val u = f.store.mark("U")
            val a = f.store.fact(STATIC,left.pathData.toList(),Tail.STAR,MarkSlot.STAR) as FlowTree
            val b = f.store.fact(STATIC,right.pathData.toList(),Tail.STAR,MarkSlot.STAR) as FlowTree
            val input = f.subscription(a.withRoot(FlowAlgebra.mergeAdd(a.root,b.root)),left.initial(f.ap))
                .copy(coveredStaticRelay = true)
            h.submit(input); h.publish(left,t.id); h.publish(right,u.id); h.drain()
            assertEquals(setOf<Triple<MethodKey,Run1Slot,Int>>(Triple(f.caller,left,u.id)),h.climbs,
                "Covered f is omitted per slot; productive f→g must still map U to original f")
            assertTrue(t !in h.needs(f.caller,left))
            h.commit(f.caller)
            assertEquals(RequestMarkSet.of(u),h.needs(f.caller,left))
        }
    }

    @Test fun `unknown call boundary uses shared callee view for both source input orders`() {
        for (sourceFirst in listOf(false,true)) Run1RequestPipelineFixtures().use { h ->
            val f = h.f; val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val field = Run1Slot.StaticField(cls,f.store.field("f"))
            h.declareIdentityStaticDomain(field)
            val origin = Run1Slot.Base(AccessPathBase.Argument(1)); val t = f.store.mark("T")
            assertFalse(h.staticRequests.containsPrescanEdge(f.caller.method,f.callee.method))
            if (sourceFirst) h.publish(field,t.id)
            h.submit(f.subscription(f.store.fact(STATIC,field.pathData.toList(),Tail.STAR,MarkSlot.STAR),origin.initial(f.ap)))
            if (!sourceFirst) h.publish(field,t.id)
            h.drain()
            assertEquals(setOf<Triple<MethodKey,Run1Slot,Int>>(Triple(f.caller,origin,t.id)),h.climbs)
            assertTrue(h.sent.none { it is RunEvent.StaticNeed || it is RunEvent.LinkIn })
        }
    }
}
