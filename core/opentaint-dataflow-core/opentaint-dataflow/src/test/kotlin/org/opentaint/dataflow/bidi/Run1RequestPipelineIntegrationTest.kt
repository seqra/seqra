package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.engine.historical.*
import org.opentaint.dataflow.bidi.engine.historical.Run1Slot
import org.opentaint.dataflow.bidi.engine.historical.RunEvent
import org.opentaint.dataflow.bidi.engine.historical.SummaryStorage
import org.opentaint.dataflow.bidi.engine.historical.RequestStorage
import org.opentaint.dataflow.bidi.engine.historical.RequestRows
import org.opentaint.dataflow.bidi.engine.historical.RequestedRow
import org.opentaint.dataflow.bidi.engine.historical.RequestRowView
import org.opentaint.dataflow.bidi.engine.historical.CalleeSubscriptions
import org.opentaint.dataflow.bidi.fixtures.HistoricalRun1RequestPipelineFixtures as Run1RequestPipelineFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

class Run1RequestPipelineIntegrationTest {
    @Test fun `temporary rows merge every mark before one method barrier and only commit makes R visible`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            val calleeSlot = Run1Slot.Base(AccessPathBase.Argument(0))
            val origin = Run1Slot.Base(AccessPathBase.Argument(1))
            h.submit(f.subscription(f.store.fact(tail = Tail.STAR, mark = MarkSlot.STAR), origin.initial(f.ap)))
            h.drain()
            h.collect(f.callee, calleeSlot, RequestMarkSet.of(t))
            h.collect(f.callee, calleeSlot, RequestMarkSet.fromIds(intArrayOf(u.id, t.id)))
            assertTrue(h.storage().borrowRequest(calleeSlot).isEmpty)
            assertTrue(h.events.isEmpty(), "Temporary collection cannot send an early notification")
            h.commit()
            val changed = assertIs<RunEvent.ChangedRequests>(h.events.single())
            assertEquals(mapOf<Run1Slot, RequestMarkSet>(calleeSlot to RequestMarkSet.fromIds(intArrayOf(t.id, u.id))), changed.rows)
            assertEquals(2, h.storage().borrowRequest(calleeSlot).countMarks {})
            h.drain()
            val batch = h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().single()
            assertEquals(f.caller, batch.method)
            assertEquals(mapOf<Run1Slot, RequestMarkSet>(origin to RequestMarkSet.fromIds(intArrayOf(t.id, u.id))), batch.rows)
            assertTrue(h.storage(f.caller).borrowRequest(origin).isEmpty, "A queued delivery only writes the target temporary store")
            h.commit(f.caller)
            assertEquals(2, h.storage(f.caller).borrowRequest(origin).countMarks {})
            h.collect(f.callee, calleeSlot, RequestMarkSet.of(t)); h.commit(); h.drain()
            assertEquals(1, h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().size)
            assertFails { (changed.rows as MutableMap<Run1Slot, RequestMarkSet>).clear() }
        }
    }

    @Test fun `one packed input visited once across changed slots yields one merged original-slot batch`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val first = Run1Slot.StaticField(cls, f.store.field("f"))
            val second = Run1Slot.StaticField(cls, f.store.field("g"))
            h.declareIdentityStaticDomain(first, second)
            val original = Run1Slot.Base(AccessPathBase.Argument(0))
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            val left = f.store.fact(STATIC, first.pathData.toList(), Tail.STAR, MarkSlot.STAR) as FlowTree
            val right = f.store.fact(STATIC, second.pathData.toList(), Tail.STAR, MarkSlot.STAR) as FlowTree
            val input = left.withRoot(FlowAlgebra.mergeAdd(left.root, right.root))
            h.submit(f.subscription(input, original.initial(f.ap))); h.drain()
            h.collect(f.callee, first, RequestMarkSet.of(t))
            h.collect(f.callee, second, RequestMarkSet.of(u))
            h.commit(); h.drain()
            assertEquals(1L, h.statistics.snapshot().replayCandidates)
            val batch = h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().single()
            assertEquals(setOf<Run1Slot>(original), batch.rows.keys)
            assertEquals(RequestMarkSet.fromIds(intArrayOf(t.id, u.id)), batch.rows.getValue(original))
            assertEquals(setOf<Triple<MethodKey, Run1Slot, Int>>(Triple(f.caller, original, t.id), Triple(f.caller, original, u.id)), h.climbs)
        }
    }

    @Test fun `inherited precomputed segment skips only unchanged queried slot and still routes changed static origin`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val first = Run1Slot.StaticField(cls, f.store.field("f"))
            val second = Run1Slot.StaticField(cls, f.store.field("g"))
            h.declareIdentityStaticDomain(first, second)
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            val frozen: RequestRows = mapOf(first to RequestMarkSet.of(t), second to RequestMarkSet.of(u))
            h.bases[f.callee] = frozen
            h.bases[f.caller] = frozen
            val a = f.store.fact(STATIC, first.pathData.toList(), Tail.STAR, MarkSlot.STAR) as FlowTree
            val b = f.store.fact(STATIC, second.pathData.toList(), Tail.STAR, MarkSlot.STAR) as FlowTree
            val input = a.withRoot(FlowAlgebra.mergeAdd(a.root, b.root))
            h.submit(f.subscription(input, first.initial(f.ap))); h.drain()
            assertEquals(setOf<Triple<MethodKey, Run1Slot, Int>>(Triple(f.caller, first, u.id)), h.climbs,
                "Inherited g/U does not mean that the original f/U interest is admitted")
            assertSame(frozen.getValue(first), h.storage().borrowRequest(first).precomputed)
            assertTrue(h.storage().borrowRequest(first).dynamic.isEmpty)
            assertTrue(h.events.isEmpty(), "B generates no initial ChangedRequests wave")
            h.commit(f.caller)
            assertEquals(RequestMarkSet.of(u), h.storage(f.caller).borrowRequest(first).dynamic)
        }
    }

    @Test fun `late request answers old input then the same admitted request answers later suppliers without a memo`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val slot = Run1Slot.Base(AccessPathBase.Argument(0))
            val t = f.store.mark("T")
            val exact = f.ap.initial(slot.base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))
            val chain = f.ap.initial(slot.base, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.Concrete(t))
            h.submit(f.subscription(f.store.fact(mark = MarkSlot.Concrete(t)))); h.drain()
            assertEquals(setOf(f.callee to slot.initial(f.ap)), h.initials)
            h.publish(slot, t.id); h.drain()
            assertTrue(f.callee to exact in h.initials)
            h.submit(f.subscription(f.store.fact(tail = Tail.ANY, mark = MarkSlot.Concrete(t), layer = Layer.DEMAND), call = 2))
            h.drain()
            assertEquals(setOf(f.callee to slot.initial(f.ap), f.callee to exact, f.callee to chain), h.initials)
            assertTrue(t in h.storage().borrowRequest(slot))
            assertTrue(h.initials.none { it.second.tail == Tail.ANY || it.second.tail == Tail.ANY_TAINT })
        }
    }

    @Test fun `mutually recursive callers close exact merged memberships without endless forwarding`(): Unit {
        for (width in listOf(16, 32)) {
            Run1RequestPipelineFixtures().use { h ->
                val f = h.f
                val slot = Run1Slot.Base(AccessPathBase.Argument(0))
                val input = f.store.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
                h.submit(f.subscription(input, slot.initial(f.ap)))
                h.submit(f.subscription(input, slot.initial(f.ap), caller = f.callee, callee = f.caller, call = 2))
                h.drain()
                val marks = RequestMarkSet.fromIds(IntArray(width) { f.store.mark("T$it").id })
                h.collect(f.caller, slot, marks); h.commit(f.caller); h.drain()
                h.commit(f.callee); h.drain()
                h.commit(f.caller); h.drain()
                assertEquals(width, h.storage(f.caller).borrowRequest(slot).countMarks {})
                assertEquals(width, h.storage(f.callee).borrowRequest(slot).countMarks {})
                assertEquals(1, h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().size,
                    "The edge back to an admitted target needs no second physical batch")
                val count = h.sent.size
                h.collect(f.callee, slot, marks); h.commit(f.callee); h.drain()
                assertEquals(count, h.sent.size)
                assertTrue(h.events.isEmpty())
            }
        }
    }

    @Test fun `a queued answer callback can admit later input after the captured caller visit has ended`(): Unit {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val t = f.store.mark("T")
            val slot = Run1Slot.Base(AccessPathBase.Argument(0))
            val input = f.store.fact(mark = MarkSlot.Concrete(t))
            h.submit(f.subscription(input)); h.drain()
            var delivered = false
            h.onAnswer = {
                if (!delivered) {
                    delivered = true
                    h.submit(f.subscription(input, call = 2))
                }
            }
            h.publish(slot, t.id)
            assertFalse(delivered)
            h.drain()
            assertTrue(delivered)
            assertTrue(h.events.isEmpty())
            assertEquals(setOf(f.callee to slot.initial(f.ap), f.callee to f.ap.initial(slot.base, null,
                Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))), h.initials)
        }
    }
}
