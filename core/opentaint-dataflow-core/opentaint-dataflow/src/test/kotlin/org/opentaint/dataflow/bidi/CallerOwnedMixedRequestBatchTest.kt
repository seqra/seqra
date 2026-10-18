package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.Run1RequestPipelineFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

/** A method-empty site batch can contain both ordinary Base and shared static interests. */
class CallerOwnedMixedRequestBatchTest {
    @Test fun `mixed request drain answers only actual Base marks and still forwards static FLOW`() {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val field = Run1Slot.StaticField(f.ap.accessors.index(ClassStaticAccessor("C")), f.store.field("f"))
            h.declareIdentityStaticDomain(field)
            val base = Run1Slot.Base(AccessPathBase.Argument(0))
            val staticProducer = Run1Slot.Base(AccessPathBase.Argument(2))
            val t = f.store.mark("base-requested")
            val u = f.store.mark("static-requested-only")
            val absent = f.store.mark("requested-but-absent")

            // The two concrete deltas share one retained current input. U is
            // actual on this nonstatic input, but only the static slot requests U.
            h.submit(f.subscription(f.store.fact(mark = MarkSlot.Concrete(t))))
            h.submit(f.subscription(f.store.fact(mark = MarkSlot.Concrete(u))))
            h.submit(f.subscription(f.store.fact(STATIC, field.pathData.toList(), Tail.STAR,
                MarkSlot.STAR, layer = Layer.DEMAND), staticProducer.initial(f.ap)))
            h.drain()
            assertTrue(h.initials.none { it.second.mark is MarkSlot.Concrete })

            // One source commit queues both exact Base and shared-view static
            // deliveries before the same caller's method-empty request drain.
            h.collect(f.callee, base, RequestMarkSet.fromIds(intArrayOf(t.id, absent.id)))
            h.collect(f.callee, field, RequestMarkSet.of(u))
            h.commit()
            h.drain()

            val exact = f.ap.initial(base.base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertEquals(setOf(f.callee to exact), h.initials.filter { it.second.mark is MarkSlot.Concrete }.toSet())
            assertEquals(listOf(exact), h.sent.filterIsInstance<RunEvent.InitialAnswersIn>().flatMap { it.answers })
            assertEquals(setOf<Triple<MethodKey, Run1Slot, Int>>(Triple(f.caller, staticProducer, u.id)), h.climbs,
                "The static row still joins its productive FLOW supplier, without answering U on the unrelated Base input")
            val site = f.subscription(f.store.fact(mark = MarkSlot.Concrete(t))).callSiteKey
            val state = assertNotNull(h.subscriptions.requestState(site))
            assertEquals(0L, state.pendingMembers)
            assertEquals(2, state.answeredRows)
            assertEquals(3L, state.answeredMembers)
            assertTrue(h.storage(f.caller).borrowRequest(staticProducer).isEmpty,
                "The productive static need remains pending at its actual Base owner until that owner commits")
            h.commit(f.caller)
            assertEquals(RequestMarkSet.of(u), h.storage(f.caller).borrowRequest(staticProducer).dynamic)
            assertTrue(h.sent.none { it is RunEvent.LinkIn })
        }
    }

    @Test fun `installed mixed interests answer later Base input without inventing static or absent marks`() {
        Run1RequestPipelineFixtures().use { h ->
            val f = h.f
            val field = Run1Slot.StaticField(f.ap.accessors.index(ClassStaticAccessor("C")), f.store.field("f"))
            h.declareIdentityStaticDomain(field)
            val base = Run1Slot.Base(AccessPathBase.Argument(0))
            val baseProducer = Run1Slot.Base(AccessPathBase.Argument(1))
            val staticProducer = Run1Slot.Base(AccessPathBase.Argument(2))
            val t = f.store.mark("base-requested")
            val u = f.store.mark("static-requested-only")
            val absent = f.store.mark("requested-but-absent")

            // Abstract inputs register both streams and admit the mixed A
            // frontier before any actual concrete supplier exists at this site.
            h.submit(f.subscription(f.store.fact(tail = Tail.STAR, mark = MarkSlot.STAR), baseProducer.initial(f.ap)))
            h.submit(f.subscription(f.store.fact(STATIC, field.pathData.toList(), Tail.STAR,
                MarkSlot.STAR, layer = Layer.DEMAND), staticProducer.initial(f.ap)))
            h.collect(f.callee, base, RequestMarkSet.fromIds(intArrayOf(t.id, absent.id)))
            h.collect(f.callee, field, RequestMarkSet.of(u))
            h.commit()
            h.drain()
            assertTrue(h.initials.none { it.second.mark is MarkSlot.Concrete })
            assertTrue(h.sent.none { it is RunEvent.InitialAnswersIn })
            val site = f.subscription(f.store.fact(tail = Tail.STAR, mark = MarkSlot.STAR), baseProducer.initial(f.ap)).callSiteKey
            assertEquals(3L, h.subscriptions.requestState(site)?.answeredMembers)
            assertEquals(setOf<Triple<MethodKey, Run1Slot, Int>>(
                Triple(f.caller, baseProducer, t.id), Triple(f.caller, baseProducer, absent.id),
                Triple(f.caller, staticProducer, u.id)), h.climbs)

            h.submit(f.subscription(f.store.fact(mark = MarkSlot.Concrete(t))))
            h.submit(f.subscription(f.store.fact(mark = MarkSlot.Concrete(u))))
            h.drain()
            val exact = f.ap.initial(base.base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertEquals(setOf(f.callee to exact), h.initials.filter { it.second.mark is MarkSlot.Concrete }.toSet())
            assertEquals(listOf(exact), h.sent.filterIsInstance<RunEvent.InitialAnswersIn>().flatMap { it.answers })
            assertEquals(3L, h.subscriptions.requestState(site)?.answeredMembers)
            assertEquals(0L, h.subscriptions.requestState(site)?.pendingMembers)
            assertTrue(h.sent.none { it is RunEvent.LinkIn })
        }
    }
}
