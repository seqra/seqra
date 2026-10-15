package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class Run1RequestStorageTest {
    private fun ids(row: RequestRowView): Set<Int> = buildSet { row.forEachMark { add(it.id) } }

    @Test fun `one borrowed base and disjoint additions preserve mark zero and old borrowed values`() {
        val f = StoreFixtures(); val t = f.mark("T"); val u = f.mark("U"); val v = f.mark("V")
        val slot = Run1Slot.Base(AccessPathBase.Argument(0))
        val frozen = RequestMarkSet.fromIds(intArrayOf(0, t.id))
        val base = freezeRequestRows(mapOf(slot to frozen))
        val a = RequestStorage(base); val b = RequestStorage(base)
        assertSame(frozen, a.borrow(slot).precomputed)
        assertSame(frozen, b.borrow(slot).precomputed)
        assertEquals(RequestMarkSet.of(u), a.addAll(slot, RequestMarkSet.fromIds(intArrayOf(t.id, u.id, 0))))
        val before = a.borrow(slot)
        assertEquals(setOf(0, t.id, u.id), ids(before))
        assertEquals(setOf(u.id), before.dynamic.let { marks -> buildSet { marks.forEachId { add(it) } } })
        assertEquals(RequestMarkSet.of(v), a.addAll(slot, RequestMarkSet.fromIds(intArrayOf(v.id, u.id))))
        assertEquals(setOf(0, t.id, u.id), ids(before), "A borrowed row retains immutable segments only")
        assertEquals(setOf(0, t.id), ids(b.borrow(slot)), "D belongs to one authoritative storage")
        assertEquals(1, a.dynamicRows); assertEquals(2L, a.dynamicMembers)
        assertFailsWith<IllegalStateException> { a.pendingRows() }
    }

    @Test fun `same class temporary storage merges all marks but does not read or copy frozen rows`() {
        val f = StoreFixtures(); val t = f.mark("T"); val u = f.mark("U")
        val slot = Run1Slot.Base(AccessPathBase.Argument(0))
        val authority = RequestStorage(freezeRequestRows(mapOf(slot to RequestMarkSet.of(t))))
        val temporary = RequestStorage()
        temporary.addAll(slot, RequestMarkSet.fromIds(intArrayOf(t.id, u.id)).minus(authority.borrow(slot)))
        temporary.addAll(slot, RequestMarkSet.of(u).minus(authority.borrow(slot)))
        assertEquals(mapOf<Run1Slot, RequestMarkSet>(slot to RequestMarkSet.of(u)), temporary.pendingRows())
        assertEquals(1L, temporary.dynamicMembers)
        assertTrue(authority.borrow(slot).dynamic.isEmpty)
        for ((key, marks) in temporary.pendingRows()) authority.addAll(key, marks)
        assertEquals(setOf(t.id, u.id), ids(authority.borrow(slot)))
        assertEquals(RequestMarkSet.of(u), authority.borrow(slot).dynamic)
        assertFails { (temporary.pendingRows() as MutableMap<Run1Slot, RequestMarkSet>).clear() }
    }

    @Test fun `packed narrow and broad classes return each B plus D row once without unrelated classes`() {
        val f = StoreFixtures(); val m = f.manager
        val c = m.accessors.index(ClassStaticAccessor("C")); val d = m.accessors.index(ClassStaticAccessor("D"))
        val other = m.accessors.index(ClassStaticAccessor("Other"))
        val x = f.field("x"); val y = f.field("y"); val z = f.field("z")
        val t = f.mark("T"); val u = f.mark("U")
        val cx = Run1Slot.StaticField(c,x); val cy = Run1Slot.StaticField(c,y)
        val dz = Run1Slot.StaticField(d,z); val ox = Run1Slot.StaticField(other,x)
        val storage = RequestStorage(freezeRequestRows(mapOf(cx to RequestMarkSet.of(t), cy to RequestMarkSet.of(t),
            dz to RequestMarkSet.of(t), ox to RequestMarkSet.of(t))))
        storage.addAll(cx, RequestMarkSet.of(u)); storage.addAll(cy, RequestMarkSet.of(u))
        val broadC = f.fact(STATIC,listOf(c),Tail.ANY,MarkSlot.Concrete(t)) as TaintTree
        val narrowD = f.fact(STATIC,listOf(d,z),Tail.EXACT,MarkSlot.Concrete(t)) as TaintTree
        val packed = m.taintTree(STATIC,Layer.NORMAL,m.taintAlg.mergeAdd(broadC.root,narrowD.root),ExclusionSet.of(c))
        val counts = RequestMatchCounts()
        val rows = storage.borrowMatchingRows(packed,{},counts)
        assertEquals(setOf(cx,cy,dz),rows.map { it.slot }.toSet())
        assertEquals(3,rows.size, "B and D contribute two segments to one descriptor, never duplicate slots")
        assertEquals(setOf(t.id,u.id),ids(rows.single { it.slot == cx }.marks))
        assertEquals(1L,counts.directLookups); assertEquals(1L,counts.broadScans)
        assertEquals(6L,counts.scannedKeys) // four B keys and two overlapping D keys
    }

    @Test fun `request matching ignores field and mark exclusions but exact ancestor is not a supplier`() {
        val f = StoreFixtures(); val m = f.manager
        val c = m.accessors.index(ClassStaticAccessor("C")); val x = f.field("x"); val t = f.mark("T")
        val slot = Run1Slot.StaticField(c,x)
        val storage = RequestStorage(freezeRequestRows(mapOf(slot to RequestMarkSet.of(t))))
        val excludedFlow = f.fact(STATIC,emptyList(),Tail.STAR,MarkSlot.Star(m.markSetOf(t)),ExclusionSet.of(c))
        assertEquals(listOf(slot),storage.borrowMatchingRows(excludedFlow,{}).map { it.slot })
        val excludedMay = f.fact(STATIC,listOf(c),Tail.ANY,MarkSlot.Concrete(t),ExclusionSet.of(x))
        assertEquals(listOf(slot),storage.borrowMatchingRows(excludedMay,{}).map { it.slot })
        val exactAncestor = f.fact(STATIC,listOf(c),Tail.EXACT,MarkSlot.Concrete(t))
        assertTrue(storage.borrowMatchingRows(exactAncestor,{}).isEmpty())
        val explicitChild = f.fact(STATIC,slot.pathData.toList(),Tail.EXACT,MarkSlot.Concrete(t)) as TaintTree
        val exact = exactAncestor as TaintTree
        val withChild = m.taintTree(STATIC,Layer.NORMAL,m.taintAlg.mergeAdd(exact.root,explicitChild.root))
        assertEquals(listOf(slot),storage.borrowMatchingRows(withChild,{}).map { it.slot })
    }

    @Test fun `ordinary base lookup stays direct and matching checks cancellation for wide scans`() {
        val f = StoreFixtures(); val m = f.manager; val t = f.mark("T")
        val base = Run1Slot.Base(AccessPathBase.Argument(0))
        val c = m.accessors.index(ClassStaticAccessor("C"))
        val fields = (0 until 64).map { Run1Slot.StaticField(c,f.field("f$it")) }
        val storage = RequestStorage(freezeRequestRows((fields + base).associateWith { RequestMarkSet.of(t) }))
        val counts = RequestMatchCounts()
        assertEquals(listOf(base),storage.borrowMatchingRows(f.fact(),{},counts).map { it.slot })
        assertEquals(1L,counts.directLookups); assertEquals(0L,counts.scannedKeys)
        val broad = f.fact(STATIC,tail=Tail.ANY)
        var visits = 0
        assertFailsWith<Cancellation.Cancelled> {
            storage.borrowMatchingRows(broad, { if (++visits == 7) f.cancellation.cancel(); f.cancellation.checkpoint() })
        }
        assertEquals(7,visits)
    }

    @Test fun `only actual normal initial slots can become request keys`() {
        val f = StoreFixtures(); val m = f.manager
        val c = m.accessors.index(ClassStaticAccessor("C")); val x = f.field("x")
        val slot = Run1Slot.StaticField(c,x)
        assertEquals(slot,Run1Slot.fromInitial(slot.initial(m)))
        assertNull(Run1Slot.fromInitial(m.initial(STATIC,m.path(listOf(c)),Tail.STAR,ExclusionSet.Empty,MarkSlot.STAR)))
        assertNull(Run1Slot.fromInitial(m.initial(STATIC,m.path(slot.pathData),Tail.EXACT,ExclusionSet.Empty,MarkSlot.Concrete(TaintMark.ZERO))))
        assertFailsWith<IllegalArgumentException> { Run1Slot.StaticField(c,m.accessors.index(ElementAccessor)) }
    }
}
