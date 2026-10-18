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

    @Test fun `profiles install no frozen mark interests and actual static suppliers need no request wave`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f;val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val first=Run1Slot.StaticField(c,f.store.field("f"));val second=Run1Slot.StaticField(c,f.store.field("g"))
            val t=f.store.mark("T");val u=f.store.mark("U")
            val profile=bind(h,listOf(first,second),t,second,u)
            assertSame(EmptyFrozenRequestBase,profile.precomputedRequests)
            h.storage();h.storage(f.caller)
            assertTrue(h.events.isEmpty())
            val actual=f.store.fact(STATIC,second.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(u))
            h.submit(f.subscription(actual));h.drain()
            val initial=f.ap.initial(STATIC,f.ap.path(second.pathData),Tail.EXACT,ExclusionSet.Empty,MarkSlot.Concrete(u))
            assertTrue(f.callee to initial in h.initials)
            assertTrue(h.sourceNeeds().isEmpty(),"Actual-T admission does not fabricate an interest")
            assertTrue(h.storage().borrowRequest(second).isEmpty)
            assertTrue(h.sent.filterIsInstance<RunEvent.MarkRequestsIn>().isEmpty())
        }
    }

    @Test fun `actual static marks are admitted independently of source needs in both arrival orders`() {
        for(needFirst in listOf(false,true)) Run1RequestPipelineFixtures().use { h ->
            val f=h.f;val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val slot=Run1Slot.StaticField(c,f.store.field("f"));val t=f.store.mark("T");val u=f.store.mark("U")
            bind(h,listOf(slot),t)
            val a=f.store.fact(STATIC,slot.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(t)) as TaintTree
            val b=f.store.fact(STATIC,slot.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(u)) as TaintTree
            val input=a.withRoot(f.ap.taintAlg.mergeAdd(a.root,b.root))
            if(needFirst) { h.publish(slot,u.id);h.drain() }
            h.submit(f.subscription(input));h.drain()
            val initialT=f.ap.initial(STATIC,f.ap.path(slot.pathData),Tail.EXACT,ExclusionSet.Empty,MarkSlot.Concrete(t))
            val initialU=f.ap.initial(STATIC,f.ap.path(slot.pathData),Tail.EXACT,ExclusionSet.Empty,MarkSlot.Concrete(u))
            assertEquals(setOf(f.callee to slot.initial(f.ap),f.callee to initialT,f.callee to initialU),h.initials)
            if(!needFirst) { h.publish(slot,u.id);h.drain() }
            h.submit(f.subscription(input,call=2));h.drain()
            assertEquals(RequestMarkSet.of(u),h.needs(f.callee,slot))
            assertEquals(RequestMarkSet.of(u), h.storage().borrowRequest(slot).dynamic)
            assertTrue(h.initials.none { it.second.tail==Tail.ANY })
        }
    }

    @Test fun `wide metadata does not install runtime requests but all actual marks on a legal nonreader start`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f;val c=f.ap.accessors.index(ClassStaticAccessor("C"))
            val first=Run1Slot.StaticField(c,f.store.field("f"));val late=Run1Slot.StaticField(c,f.store.field("g"))
            val t=f.store.mark("T");val u=f.store.mark("U")
            val profile=Run1StaticProfiles.build(f.ap,emptyMap(),mapOf(f.callee.method to Run1StaticLocalProfile(
                abstractions=listOf(first.pathData.toList()),cleanerMarks=RequestMarkSet.of(t))),
                declaredFieldUniverse=setOf(late))[f.callee.method]
            h.profiles[f.callee]=profile
            assertSame(EmptyFrozenRequestBase,profile.precomputedRequests)
            val input=f.store.fact(STATIC,late.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(u))
            h.submit(f.subscription(input));h.drain();h.commit()
            val concrete=f.ap.initial(STATIC,f.ap.path(late.pathData),Tail.EXACT,ExclusionSet.Empty,MarkSlot.Concrete(u))
            assertEquals(setOf(f.callee to late.initial(f.ap),f.callee to concrete),h.initials)
            assertTrue(h.sourceNeeds().isEmpty())
            assertTrue(h.storage().borrowRequest(late).isEmpty)
            assertTrue(h.events.isEmpty())
        }
    }
}
