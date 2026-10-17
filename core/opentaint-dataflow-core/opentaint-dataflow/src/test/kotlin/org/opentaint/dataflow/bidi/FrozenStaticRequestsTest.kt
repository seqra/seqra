package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.ownedMap
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class FrozenStaticRequestsTest {
    private fun ids(marks: RequestMarkSelection): List<Int> = buildList {
        val cursor = marks.cursor()
        while (!cursor.isComplete) cursor.step(1) { add(it.id) }
    }

    @Test fun `sparse descriptor freeze equals independent set expansion and prunes only complete overlays`() {
        val f = StoreFixtures(); val m = f.manager
        val c = m.accessors.index(ClassStaticAccessor("C")); val d = m.accessors.index(ClassStaticAccessor("D"))
        val selected = (0 until 8).map { Run1Slot.StaticField(if (it%2==0) c else d,f.field("f$it")) }.toCollection(LinkedHashSet())
        val rootIds = setOf(0,2)
        val classIds = mapOf(c to setOf(0,2,3)) // the already-unioned commonByClass
        val raw = linkedMapOf(selected.elementAt(0) to setOf(0,2,3), // fully redundant
            selected.elementAt(1) to setOf(2,4), selected.elementAt(2) to setOf(3,5))
        val expected = selected.associateWith { slot -> (classIds[slot.cls] ?: rootIds) + raw[slot].orEmpty() }
        val common = ownedMap(classIds.mapValues { RequestMarkSet.fromIds(it.value.toIntArray()) })
        val input = raw.mapValuesTo(LinkedHashMap()) { RequestMarkSet.fromIds(it.value.toIntArray()) }
        val footprint = Run1StaticFootprint(selected)
        val base = FrozenStaticRequests.freeze(footprint,RequestMarkSet.fromIds(rootIds.toIntArray()),common,input,{})
        input.clear()
        assertEquals(expected.size.toLong(),base.logicalRows)
        assertEquals(expected.values.sumOf { it.size.toLong() },base.logicalMembers)
        val rows = ArrayList<Run1Slot.StaticField>();base.forEachStaticRow({}) { rows+=it }
        assertEquals(selected.toList(),rows)
        for ((slot, marks) in expected) assertEquals(marks.sorted(),ids(base.borrow(slot)))
        val outside = Run1Slot.StaticField(c,f.field("outside"))
        assertFalse(base.hasRow(outside));assertTrue(base.borrow(outside).isEmpty)
        val factored = assertIs<FrozenStaticRequests>(base)
        assertSame(footprint.fieldUniverse,factored.domainSlots)
        assertSame(common,factored.commonByClass)
        assertEquals(raw.keys.drop(1).toSet(),factored.byField.keys)
        assertFalse(factored.byField === input)
    }

    @Test fun `empty B selected slot with later D remains visible and snapshots keep captured values`() {
        val f = StoreFixtures();val c=f.manager.accessors.index(ClassStaticAccessor("C"))
        val empty=Run1Slot.StaticField(c,f.field("empty"));val full=Run1Slot.StaticField(c,f.field("full"))
        val t=f.mark("T");val u=f.mark("U");val v=f.mark("V")
        val base=FrozenStaticRequests.freeze(Run1StaticFootprint(linkedSetOf(empty,full)),RequestMarkSet.EMPTY,
            emptyMap(),mapOf(full to RequestMarkSet.of(t)),{})
        val a=RequestStorage(base);val b=RequestStorage(base)
        assertFalse(base.hasRow(empty));a.addAll(empty,RequestMarkSet.of(u))
        val broad=f.fact(STATIC,tail=Tail.ANY)
        val counts=RequestMatchCounts();val before=a.borrowMatchingRows(broad,{},counts)
        assertEquals(listOf(full,empty),before.map { it.slot })
        assertEquals(2L,counts.frozenSlotsExamined);assertEquals(1L,counts.dynamicKeysExamined)
        assertEquals(3L,counts.scannedKeys)
        a.addAll(empty,RequestMarkSet.of(v))
        assertEquals(listOf(u.id),ids(before.last().marks))
        assertEquals(setOf(u.id,v.id),ids(a.borrow(empty)).toSet())
        assertTrue(b.borrow(empty).isEmpty)
        assertSame(base.borrow(full),a.borrow(full).precomputed)
    }

    @Test fun `mixed class and explicit field scans preserve map-control order and geometry without field mark copies`() {
        val f=StoreFixtures();val m=f.manager
        val c=m.accessors.index(ClassStaticAccessor("C"));val d=m.accessors.index(ClassStaticAccessor("D"))
        val slots=(0 until 6).map { Run1Slot.StaticField(if(it%2==0)c else d,f.field("v$it")) }
        val t=f.mark("T");val u=f.mark("U")
        val base=FrozenStaticRequests.freeze(Run1StaticFootprint(slots.toCollection(LinkedHashSet())),RequestMarkSet.of(t),
            emptyMap(),mapOf(slots[2] to RequestMarkSet.of(u)),{})
        // Independent expanded map uses ordinary sets, not production union/borrow.
        val map=MapFrozenRequestBase(slots.associateWith { slot ->
            RequestMarkSet.fromIds((setOf(t.id)+(if(slot==slots[2])setOf(u.id) else emptySet())).toIntArray()) })
        val actual=RequestStorage(base);val oracle=RequestStorage(map)
        val outside=Run1Slot.StaticField(c,f.field("late"))
        actual.addAll(outside,RequestMarkSet.of(u));oracle.addAll(outside,RequestMarkSet.of(u))
        val broad=f.fact(STATIC,listOf(c),Tail.ANY,MarkSlot.Concrete(t)) as TaintTree
        val exact=f.fact(STATIC,slots[1].pathData.toList(),Tail.EXACT,MarkSlot.Concrete(t)) as TaintTree
        val packed=m.taintTree(STATIC,Layer.NORMAL,m.taintAlg.mergeAdd(broad.root,exact.root),ExclusionSet.of(c))
        val a=actual.borrowMatchingRows(packed,{});val b=oracle.borrowMatchingRows(packed,{})
        assertEquals(b.map { it.slot },a.map { it.slot })
        assertEquals(listOf(slots[1],slots[0],slots[2],slots[4],outside),a.map { it.slot })
        assertEquals(b.map { ids(it.marks) },a.map { ids(it.marks) })
    }

    @Test fun `logical counters use common multiplication and sparse differences while the base has no materialized field rows`() {
        val f=StoreFixtures();val m=f.manager;val c=m.accessors.index(ClassStaticAccessor("C"))
        val slots=(0 until 256).map { listOf(c,f.field("s$it")) }
        val t=f.mark("T");val u=f.mark("U")
        val interest=StaticMarkInterest(f.pattern(STATIC,slots[0],Tail.EXACT,MarkSlot.Concrete(u)),
            StaticMarkRole.SINK,RequestMarkSet.fromIds(intArrayOf(t.id,u.id)))
        val profiles=Run1StaticProfiles.build(m,emptyMap(),mapOf(f.commonMethod to Run1StaticLocalProfile(
            abstractions=slots,marks=listOf(interest),cleanerMarks=RequestMarkSet.of(t))))
        val stats=profiles.statistics
        assertEquals(0L,stats.physicalPrecomputedRows)
        assertEquals(256L,stats.uniqueBaseLogicalRows)
        assertEquals(257L,stats.uniqueBaseLogicalMembers)
        assertEquals(1L,stats.sparseFieldRows)
        assertEquals(256L,stats.selectedFieldSlots)
        assertEquals(3L,stats.ownedMarkPrimitiveCells) // common{T} + retained partial overlay{T,U}
        assertEquals(256L,profiles[f.commonMethod].precomputedRequests.logicalRows)
    }
    @Test fun `same method distinct full keys borrow B while cancellation and emitter release leave other owners intact`() {
        val f=StoreFixtures();val c=f.manager.accessors.index(ClassStaticAccessor("C"))
        val slot=Run1Slot.StaticField(c,f.field("f"));val t=f.mark("T");val u=f.mark("U")
        val profile=Run1StaticProfiles.build(f.manager,emptyMap(),mapOf(f.commonMethod to Run1StaticLocalProfile(
            abstractions=listOf(slot.pathData.toList()),cleanerMarks=RequestMarkSet.of(t))))[f.commonMethod]
        val first=f.key("first",0);val second=f.key("second",2)
        assertSame(first.method,second.method);assertNotEquals(first,second)
        val a=SummaryStorage(first,f.manager,f.ops,ProtocolSteps.Inline,profile.precomputedRequests)
        val b=SummaryStorage(second,f.manager,f.ops,ProtocolSteps.Inline,profile.precomputedRequests)
        val pa=RequestStorage();val pb=RequestStorage()
        val aa=ArrayList<InitialAp>();val bb=ArrayList<InitialAp>()
        fun emitter(storage:SummaryStorage,p:RequestStorage,out:MutableList<InitialAp>) = Run1Abstraction(
            f.ops,profile,{out+=it},storage::borrowMatchingRequests,
            {key,marks->p.addAll(key,marks.minus(storage.borrowRequest(key)))})
        val ea=emitter(a,pa,aa);val eb=emitter(b,pb,bb)
        val old=a.borrowRequest(slot)
        assertSame(old.precomputed,b.borrowRequest(slot).precomputed)
        assertFailsWith<org.opentaint.dataflow.util.Cancellation.Cancelled> {
            pa.addAll(slot,RequestMarkSet.of(u)) { throw org.opentaint.dataflow.util.Cancellation.Cancelled() }
        }
        assertTrue(pa.isEmpty);assertTrue(b.borrowRequest(slot).dynamic.isEmpty)
        pa.addAll(slot,RequestMarkSet.of(u));a.commit(pa)
        assertEquals(RequestMarkSet.of(u),a.borrowRequest(slot).dynamic)
        assertTrue(b.borrowRequest(slot).dynamic.isEmpty)
        ea.release()
        val input=f.fact(STATIC,slot.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(t))
        val ref=CallerRef(f.key("upstream"),f.manager.zero,Layer.NORMAL,f.inst(1))
        assertFailsWith<IllegalStateException> { ea.emit(ref,input) }
        eb.emit(ref,input)
        assertTrue(bb.any { it.mark==MarkSlot.Concrete(t) && it.pathData.toList()==slot.pathData.toList() })
        assertEquals(listOf(t.id),ids(old))
        assertEquals(listOf(t.id),ids(b.borrowRequest(slot)))
        eb.release()
    }

}
