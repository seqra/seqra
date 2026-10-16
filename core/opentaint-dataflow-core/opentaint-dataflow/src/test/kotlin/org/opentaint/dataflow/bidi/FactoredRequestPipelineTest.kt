package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.Run1RequestPipelineFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

class FactoredRequestPipelineTest {
    private fun bind(h: Run1RequestPipelineFixtures, slots: List<Run1Slot.StaticField>,
                     common: TaintMark, extraSlot: Run1Slot.StaticField? = null,
                     extra: TaintMark? = null): Run1StaticProfile {
        val f=h.f
        val interests=if(extraSlot==null)emptyList() else {
            val mark=checkNotNull(extra)
            listOf(StaticMarkInterest(f.store.pattern(STATIC,extraSlot.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(mark)),
                StaticMarkRole.INPUT_GATE,RequestMarkSet.of(mark)))
        }
        val profile=Run1StaticProfiles.build(f.ap,emptyMap(),mapOf(f.callee.method to Run1StaticLocalProfile(
            abstractions=slots.map { it.pathData.toList() },marks=interests,cleanerMarks=RequestMarkSet.of(common))))[f.callee.method]
        for(key in listOf(f.callee,f.caller)) {
            h.profiles[key]=profile
            h.frozenBases[key]=profile.precomputedRequests
        }
        return profile
    }

    @Test fun `factored inherited rows skip only unchanged field and queue changed origin once without startup wave`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f;val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val first=Run1Slot.StaticField(c,f.store.field("f"));val second=Run1Slot.StaticField(c,f.store.field("g"))
            val t=f.store.mark("T");val u=f.store.mark("U")
            val profile=bind(h,listOf(first,second),t,second,u)
            assertIs<FrozenStaticRequests>(profile.precomputedRequests)
            val a=f.store.fact(STATIC,first.pathData.toList(),Tail.STAR,MarkSlot.STAR) as FlowTree
            val b=f.store.fact(STATIC,second.pathData.toList(),Tail.STAR,MarkSlot.STAR) as FlowTree
            h.storage();h.storage(f.caller)
            assertTrue(h.events.isEmpty(),"Binding B does not publish requests")
            h.submit(f.subscription(a.withRoot(FlowAlgebra.mergeAdd(a.root,b.root)),first.initial(f.ap)))
            h.drain()
            assertEquals(setOf<Triple<MethodKey,Run1Slot,Int>>(Triple(f.caller,first,u.id)),h.climbs)
            assertEquals(1,h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().size)
            assertTrue(h.events.isEmpty())
            h.commit(f.caller);h.drain()
            assertEquals(RequestMarkSet.of(u),h.storage(f.caller).borrowRequest(first).dynamic)
            assertTrue(h.storage().borrowRequest(first).dynamic.isEmpty,"The shared B has per-full-key D")
            assertEquals(3L,profile.precomputedRequests.logicalMembers)
        }
    }

    @Test fun `factored B and later D answer concrete suppliers in either arrival order without a memo`() {
        for(requestFirst in listOf(false,true)) Run1RequestPipelineFixtures().use { h ->
            val f=h.f;val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val slot=Run1Slot.StaticField(c,f.store.field("f"));val t=f.store.mark("T");val u=f.store.mark("U")
            bind(h,listOf(slot),t)
            val a=f.store.fact(STATIC,slot.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(t)) as TaintTree
            val b=f.store.fact(STATIC,slot.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(u)) as TaintTree
            val input=a.withRoot(f.ap.taintAlg.mergeAdd(a.root,b.root))
            if(requestFirst)h.publish(slot,u.id)
            h.submit(f.subscription(input));h.drain()
            val initialT=f.ap.initial(STATIC,f.ap.path(slot.pathData),Tail.EXACT,ExclusionSet.Empty,MarkSlot.Concrete(t))
            val initialU=f.ap.initial(STATIC,f.ap.path(slot.pathData),Tail.EXACT,ExclusionSet.Empty,MarkSlot.Concrete(u))
            assertTrue(f.callee to initialT in h.initials)
            if(!requestFirst) {
                assertFalse(f.callee to initialU in h.initials)
                h.publish(slot,u.id);h.drain()
            }
            assertTrue(f.callee to initialU in h.initials)
            h.submit(f.subscription(input,call=2));h.drain()
            assertEquals(setOf(f.callee to slot.initial(f.ap),f.callee to initialT,f.callee to initialU),h.initials)
            assertTrue(h.initials.none { it.second.tail==Tail.ANY })
            assertEquals(RequestMarkSet.of(u),h.storage().borrowRequest(slot).dynamic)
        }
    }
    @Test fun `outside S wide activation writes P then exact D delta and answers registered supplier`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f;val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val first=Run1Slot.StaticField(c,f.store.field("f"));val late=Run1Slot.StaticField(c,f.store.field("g"))
            val t=f.store.mark("T")
            val profile=Run1StaticProfiles.build(f.ap,emptyMap(),mapOf(f.callee.method to Run1StaticLocalProfile(
                abstractions=listOf(first.pathData.toList()),cleanerMarks=RequestMarkSet.of(t))),
                declaredFieldUniverse=setOf(late))[f.callee.method]
            h.profiles[f.callee]=profile;h.frozenBases[f.callee]=profile.precomputedRequests
            assertIs<FrozenStaticRequests>(profile.precomputedRequests)
            assertFalse(profile.precomputedRequests.hasRow(late))
            val input=f.store.fact(STATIC,late.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(t))
            h.submit(f.subscription(input));h.drain()
            assertTrue(h.storage().borrowRequest(late).isEmpty)
            assertTrue(h.events.isEmpty(),"Emitter P offers cannot notify before commit")
            val concrete=f.ap.initial(STATIC,f.ap.path(late.pathData),Tail.EXACT,ExclusionSet.Empty,MarkSlot.Concrete(t))
            assertFalse(f.callee to concrete in h.initials)
            h.commit()
            val delta=assertIs<RunEvent.ChangedRequests>(h.events.single())
            assertEquals(mapOf<Run1Slot,RequestMarkSet>(late to RequestMarkSet.of(t)),delta.rows)
            assertEquals(RequestMarkSet.of(t),h.storage().borrowRequest(late).dynamic)
            assertTrue(h.storage().borrowRequest(late).precomputed.isEmpty)
            h.drain()
            assertTrue(f.callee to concrete in h.initials)
            assertFalse(profile.precomputedRequests.hasRow(late),"Activation never enlarges immutable S or B")
            h.submit(f.subscription(input,call=2));h.drain();h.commit()
            assertTrue(h.events.isEmpty(),"Repeated wide activation is filtered by R")
        }
    }

}
