package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.Run1RequestPipelineFixtures
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

/** Caller-owned static view integration; the class name keeps test selectors stable. */
class StaticNeedRouterTest {
    @Test fun `bootstrap reaches the actual parameter writer in both arrival orders`() {
        for (needFirst in listOf(false,true)) Run1RequestPipelineFixtures().use { h ->
            val f=h.f; val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val field=Run1Slot.StaticField(c,f.store.field("f")); val t=f.store.mark("T")
            val p=Run1Slot.Base(AccessPathBase.Argument(0))
            h.declareIdentityStaticDomain(field)
            if (needFirst) { h.publish(field,t.id); h.drain() }
            val written=f.store.fact(STATIC,field.pathData.toList(),Tail.STAR,MarkSlot.STAR)
            h.submit(f.subscription(written,p.initial(f.ap))); h.drain()
            if (!needFirst) { h.publish(field,t.id); h.drain() }
            assertEquals(setOf(Triple(f.caller,p as Run1Slot,t.id)),h.climbs)
            assertEquals(RequestMarkSet.of(t),h.needs(f.callee,field))
            assertEquals(RequestMarkSet.of(t),h.storage().borrowRequest(field).dynamic)
            assertTrue(h.storage(f.caller).borrowRequest(p).isEmpty,"Base endpoint waits for its ordinary barrier")
            h.commit(f.caller)
            assertEquals(RequestMarkSet.of(t),h.storage(f.caller).borrowRequest(p).dynamic)
            assertEquals(1,h.staticRequests.snapshot().sources)
            val before=h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().size
            h.submit(f.subscription(written,p.initial(f.ap),call=2));h.drain()
            assertEquals(before,h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().size,
                "A new callsite must not resend an admitted destination membership")
            assertTrue(h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().all { it.rows.keys.all { key -> key is Run1Slot.Base } })
        }
    }

    @Test fun `changed static origins retain productive requests and close a cycle`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f; val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val g=Run1Slot.StaticField(c,f.store.field("g")); val field=Run1Slot.StaticField(c,f.store.field("f"))
            val t=f.store.mark("T"); val u=f.store.mark("U"); val p=Run1Slot.Base(AccessPathBase.Argument(1))
            h.declareIdentityStaticDomain(field,g)
            val atF=f.store.fact(STATIC,field.pathData.toList(),Tail.STAR,MarkSlot.STAR)
            val atG=f.store.fact(STATIC,g.pathData.toList(),Tail.STAR,MarkSlot.STAR)
            h.submit(f.subscription(atF,g.initial(f.ap)))
            h.submit(f.subscription(atG,field.initial(f.ap),caller=f.callee,callee=f.caller,call=2))
            val upstream=f.store.key("parameter owner")
            h.submit(f.subscription(atG,p.initial(f.ap),caller=upstream,callee=f.caller,call=2))
            h.drain(); h.publish(field,t.id,u.id); h.drain()
            h.commit(f.caller); h.drain()
            assertEquals(setOf(Triple(f.caller,g as Run1Slot,t.id),Triple(f.caller,g as Run1Slot,u.id),
                Triple(upstream,p as Run1Slot,t.id),Triple(upstream,p as Run1Slot,u.id)),h.climbs)
            val both=RequestMarkSet.fromIds(intArrayOf(t.id,u.id))
            assertEquals(mapOf(StaticNeedKey(f.callee,field) to both,StaticNeedKey(f.caller,g) to both),h.sourceNeeds())
            assertEquals(both,h.needs(f.caller,g),"A changed field is a productive request boundary")
            val sent=h.sent.size
            h.publish(field,u.id); h.drain()
            assertEquals(sent,h.sent.size,"An unchanged authoritative source creates no notification")
        }
    }

    @Test fun `base request replay reenters static views using original field rather than current base`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f; val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val g=Run1Slot.StaticField(c,f.store.field("g")); val t=f.store.mark("T")
            val p=Run1Slot.Base(AccessPathBase.Argument(0)); h.declareIdentityStaticDomain(g)
            h.submit(f.subscription(f.store.fact(tail=Tail.STAR,mark=MarkSlot.STAR),g.initial(f.ap)))
            h.drain();h.publish(p,t.id);h.drain();h.commit(f.caller);h.drain()
            assertEquals(RequestMarkSet.of(t),h.needs(f.caller,g))
            assertEquals(RequestMarkSet.of(t),h.storage(f.caller).borrowRequest(g).dynamic)
        }
    }

    @Test fun `routing ignores E and mx but skips disconnected methods and invents no concrete input`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f; val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val field=Run1Slot.StaticField(c,f.store.field("f")); val t=f.store.mark("T")
            val p=Run1Slot.Base(AccessPathBase.Argument(0)); h.declareIdentityStaticDomain(field)
            val input=f.store.fact(STATIC,field.pathData.toList(),Tail.STAR,
                MarkSlot.Star(f.ap.markSetOf(t)),ExclusionSet.of(c))
            h.submit(f.subscription(input,p.initial(f.ap)))
            val other=StoreFixtures().method // a distinct CommonMethod, not just another context
            h.publish(field,t.id,method=other);h.drain()
            assertTrue(h.climbs.isEmpty())
            h.publish(field,t.id);h.drain()
            assertEquals(setOf(Triple(f.caller,p as Run1Slot,t.id)),h.climbs)
            val sources=h.sourceNeeds()
            assertEquals(2,sources.size)
            assertTrue(h.initials.none { it.second.mark is MarkSlot.Concrete && !it.second.isZero },"Need routing cannot invent T")
        }
    }
    @Test fun `concrete and zero caller inputs cannot become fast FLOW routes`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f;val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val field=Run1Slot.StaticField(c,f.store.field("f"));val t=f.store.mark("T")
            h.declareIdentityStaticDomain(field)
            val actual=f.store.fact(STATIC,field.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(t))
            val original=f.ap.initial(STATIC,f.ap.path(field.pathData),Tail.EXACT,ExclusionSet.Empty,MarkSlot.Concrete(t))
            h.submit(f.subscription(actual,original));h.submit(f.subscription(Reach.NORMAL,f.ap.zero,zeroOnly=true))
            h.drain();h.publish(field,t.id);h.drain()
            assertTrue(h.climbs.isEmpty())
            assertEquals(mapOf(StaticNeedKey(f.callee,field) to RequestMarkSet.of(t)),h.sourceNeeds())
            assertTrue(h.storage(f.caller).borrowRequest(field).isEmpty)
        }
    }

}
