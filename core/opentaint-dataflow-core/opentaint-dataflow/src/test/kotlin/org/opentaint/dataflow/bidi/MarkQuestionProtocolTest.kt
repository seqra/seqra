package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import org.opentaint.dataflow.bidi.fixtures.Run1RequestPipelineFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

/** F78 controls. The former full-query, role and exclusion policy is not the request oracle. */
class MarkQuestionProtocolTest {
    @Test fun `both input request orders and queued replay notify orders retain exact supplied answers`(): Unit {
        for (requestFirst in listOf(false, true)) for (replayFirst in listOf(false, true)) {
            val steps = QueuedProtocolSteps()
            Run1RequestPipelineFixtures(steps).use { h ->
                val f = h.f
                val t = f.store.mark("T")
                val slot = Run1Slot.Base(AccessPathBase.Argument(0))
                val input = f.store.fact(mark = MarkSlot.Concrete(t))
                if (requestFirst) h.publish(slot, t.id)
                h.submit(f.subscription(input))
                if (!requestFirst) h.publish(slot, t.id)
                if (replayFirst) { steps.replayAll(); steps.runNotifications() }
                else { steps.runNotifications(); h.drain(); steps.replayAll() }
                h.drain()
                val exact = f.ap.initial(slot.base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))
                assertEquals(setOf(f.callee to slot.initial(f.ap), f.callee to exact), h.initials)
                assertTrue(h.climbs.isEmpty())
                assertTrue(t in h.storage().borrowRequest(slot), "An answered request stays active")
                assertTrue(h.events.isEmpty())
            }
        }
    }

    @Test fun `fresh FLOW and late requests recover the full original caller slot in both orders`(): Unit {
        for (requestFirst in listOf(false, true)) {
            Run1RequestPipelineFixtures().use { h ->
                val f = h.f
                val t = f.store.mark("T")
                val slot = Run1Slot.Base(AccessPathBase.Argument(0))
                val original = Run1Slot.Base(AccessPathBase.Argument(1))
                val input = f.store.fact(tail = Tail.STAR, mark = MarkSlot.Star(MarkSet(intArrayOf(t.id))))
                if (requestFirst) h.publish(slot, t.id)
                h.submit(f.subscription(input, original.initial(f.ap), callerLayer = Layer.DEMAND))
                if (!requestFirst) h.publish(slot, t.id)
                h.drain()
                assertEquals(setOf<Triple<MethodKey, Run1Slot, Int>>(Triple(f.caller, original, t.id)), h.climbs,
                    "Incoming FLOW mx does not filter requests or change the original slot")
                assertTrue(h.initials.all { it.second.mark == MarkSlot.STAR })
                val event = h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().single()
                assertEquals(f.caller, event.method)
                assertEquals(setOf<Run1Slot>(original), event.rows.keys)
            }
        }
    }

    @Test fun `static incoming E is ignored and nonexact concrete answers use STAR not ANY`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val field = f.store.field("f")
            val slot = Run1Slot.StaticField(cls, field)
            h.declareIdentityStaticDomain(slot)
            val t = f.store.mark("T")
            val input = f.store.fact(STATIC, listOf(cls), Tail.ANY, MarkSlot.Concrete(t),
                ExclusionSet.of(field), Layer.NORMAL)
            h.submit(f.subscription(input)); h.drain()
            h.publish(slot, t.id); h.drain()
            val expected = f.ap.initial(STATIC, f.ap.path(listOf(cls, field)), Tail.STAR,
                ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertTrue(f.callee to expected in h.initials)
            assertTrue(h.initials.all { it.second.tail != Tail.ANY && it.second.tail != Tail.ANY_TAINT })
            assertEquals(ExclusionSet.Empty, expected.exclusion)
        }
    }

    @Test fun `a matching request cannot create a concrete answer for an absent supplied mark`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val slot = Run1Slot.Base(AccessPathBase.Argument(0))
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            h.submit(f.subscription(f.store.fact(mark = MarkSlot.Concrete(u))))
            h.drain(); h.publish(slot, t.id); h.drain()
            assertEquals(setOf(f.callee to slot.initial(f.ap)), h.initials)
            assertTrue(t in h.storage().borrowRequest(slot))
        }
    }

    @Test fun `a live current-value request visit rejects mutating reentry and releases its guard`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val input = f.store.fact()
            val directory = CalleeSubscriptions(f.ops)
            directory.add(f.subscription(input))
            val row = RequestedRow(Run1Slot.Base(input.base),
                RequestRowView(RequestMarkSet.EMPTY, RequestMarkSet.of(f.store.mark("T"))))
            assertFailsWith<IllegalStateException> {
                directory.visitRequestBatch(listOf(row)) { directory.add(f.subscription(input, call = 2)) }
            }
            assertNotNull(directory.add(f.subscription(input, call = 2)))
            var visited = 0
            directory.visitRequestBatch(listOf(row)) { visited++ }
            assertEquals(2, visited)
            directory.clear()
        }
    }

    @Test fun `target authoritative R suppresses admitted marks while unknown marks remain queued`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            val original = Run1Slot.Base(AccessPathBase.Argument(1))
            h.publish(original, t.id, method = f.caller)
            val input = f.store.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
            h.submit(f.subscription(input, original.initial(f.ap)))
            h.publish(Run1Slot.Base(input.base), t.id, u.id); h.drain()
            assertEquals(setOf<Triple<MethodKey, Run1Slot, Int>>(Triple(f.caller, original, u.id)), h.climbs)
            assertEquals(RequestMarkSet.of(t), h.storage(f.caller).borrowRequest(original).dynamic)
        }
    }

    @Test fun `rejected queued answer without cancellation fails instead of silently completing`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val t = f.store.mark("T")
            h.submit(f.subscription(f.store.fact(mark = MarkSlot.Concrete(t)))); h.drain()
            h.publish(Run1Slot.Base(AccessPathBase.Argument(0)), t.id)
            h.rejectAnswerSends = true
            assertFailsWith<IllegalStateException> { h.drain() }
            assertEquals(1L, h.statistics.snapshot().replayAnswers)
            assertTrue(h.initials.none { it.second.mark == MarkSlot.Concrete(t) })
        }
    }

    @Test fun `cancellation after queued result aborts and does not certify completion`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val t = f.store.mark("T")
            h.submit(f.subscription(f.store.fact(mark = MarkSlot.Concrete(t)))); h.drain()
            h.publish(Run1Slot.Base(AccessPathBase.Argument(0)), t.id)
            h.cancelOnAnswerSend = true
            assertFailsWith<Cancellation.Cancelled> { h.drain() }
            assertTrue(h.initials.none { it.second.mark == MarkSlot.Concrete(t) })
            assertTrue(h.events.isNotEmpty(), "An accepted queued answer is not an applied answer")
            f.ap.cancellation.activate()
        }
    }
}
