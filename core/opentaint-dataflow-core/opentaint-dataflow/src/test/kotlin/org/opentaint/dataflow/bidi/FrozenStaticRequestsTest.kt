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

    @Test fun `historical static frozen adapter stays explicit while current runtime profiles use no base`() {
        val f=StoreFixtures();val c=f.manager.accessors.index(ClassStaticAccessor("C"))
        val slot=Run1Slot.StaticField(c,f.field("f"));val t=f.mark("T")
        val base=FrozenStaticRequests.freeze(Run1StaticFootprint(linkedSetOf(slot)),RequestMarkSet.of(t),
            emptyMap(),emptyMap(),{})
        assertEquals(listOf(t.id),ids(base.borrow(slot)))
        val storage = RequestStorage(base)
        assertEquals(listOf(t.id),ids(storage.borrow(slot)))
        assertTrue(storage.borrow(slot).dynamic.isEmpty, "Compatibility borrowing cannot copy the frozen row")
        assertFailsWith<IllegalStateException> { storage.pendingRows() }
        assertSame(EmptyFrozenRequestBase,Run1StaticProfiles.build(f.manager,emptyMap(),
            mapOf(f.commonMethod to Run1StaticLocalProfile(abstractions=listOf(slot.pathData.toList()))))[f.commonMethod].precomputedRequests)
    }

    @Test fun `current profiles retain geometry and local readers but no inherited frozen request payload`() {
        val f=StoreFixtures();val c=f.manager.accessors.index(ClassStaticAccessor("C"))
        val slots=(0 until 256).map { listOf(c,f.field("s$it")) }
        val t=f.mark("T");val u=f.mark("U")
        val interest=StaticMarkInterest(f.pattern(STATIC,slots[0],Tail.EXACT,MarkSlot.Concrete(u)),
            StaticMarkRole.SINK,RequestMarkSet.of(u))
        val profiles=Run1StaticProfiles.build(f.manager,emptyMap(),mapOf(f.commonMethod to Run1StaticLocalProfile(
            abstractions=slots,marks=listOf(interest),cleanerMarks=RequestMarkSet.of(t))))
        val profile=profiles[f.commonMethod]
        assertSame(EmptyFrozenRequestBase,profile.precomputedRequests)
        assertEquals(0L,profile.precomputedRequests.logicalRows)
        assertEquals(0L,profile.precomputedRequests.logicalMembers)
        assertEquals(slots.map { Run1Slot.StaticField(it[0],it[1]) }.toSet(),profile.footprint.fieldUniverse)
        assertEquals(256,profiles.localReads(f.commonMethod).size)
        assertEquals(0L,profiles.statistics.ownedMarkPrimitiveCells)
    }
}
