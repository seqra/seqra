package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

class Run1PreparedInitialsTest {
    @Test fun `nonstatic actual marks wait for the caller request join`() {
        val f = StoreFixtures()
        val mark = MarkSlot.Concrete(f.mark("T"))
        val ref = CallerRef(f.method, f.manager.zero, Layer.NORMAL, f.inst(1))
        for (tail in listOf(Tail.EXACT, Tail.ANY)) {
            val input = f.fact(tail = tail, mark = mark)
            val prepared = prepareRun1Initials(f.ops, null, ref, input)
            assertEquals(listOf(f.ops.policy(input)), prepared.initials)
            assertEquals(MarkSlot.STAR, prepared.initials.single().mark)
            assertNull(prepared.staticEntries)
        }
    }

    @Test fun `static ANY becomes a STAR descriptor and starts as ANY demand`() {
        val f = StoreFixtures()
        val ap = f.manager
        val cls = ap.accessors.index(ClassStaticAccessor("Box"))
        val field = f.field("f")
        val slot = Run1Slot.StaticField(cls, field)
        val mark = MarkSlot.Concrete(f.mark("T"))
        val profile = Run1StaticProfiles.build(ap, mapOf(f.method.method to emptyList()),
            mapOf(f.method.method to Run1StaticLocalProfile.EMPTY), setOf(slot), setOf(cls))[f.method.method]
        val ref = CallerRef(f.method, ap.zero, Layer.NORMAL, f.inst(1))
        val prepared = prepareRun1Initials(f.ops, profile, ref,
            f.fact(STATIC, slot.pathData.toList(), Tail.ANY, mark, layer = Layer.DEMAND))
        val initial = prepared.initials.single()
        assertEquals(slot.pathData.toList(), initial.pathData.toList())
        assertEquals(Tail.STAR, initial.tail)
        assertEquals(mark, initial.mark)
        validateRun1Initial(initial, 1)
        val current = f.ops.startFact(initial)
        assertEquals(Layer.DEMAND, current.layer)
        assertTrue((current as TaintTree).root.hasAny)
        assertEquals(listOf(slot.initial(ap)), StaticIdentityEntries(f.ops)
            .originals(assertNotNull(prepared.staticEntries)).toList())
        assertTrue(prepared.demandPresence)
    }

    @Test fun `exact class entry retains its own coordinate and exact tag`() {
        val f = StoreFixtures()
        val ap = f.manager
        val cls = ap.accessors.index(ClassStaticAccessor("Box"))
        val mark = MarkSlot.Concrete(f.mark("T"))
        val profile = Run1StaticProfiles.build(ap, mapOf(f.method.method to emptyList()),
            mapOf(f.method.method to Run1StaticLocalProfile(classPoints = setOf(cls))),
            declaredClassUniverse = setOf(cls))[f.method.method]
        val prepared = prepareRun1Initials(f.ops, profile,
            CallerRef(f.method, ap.zero, Layer.NORMAL, f.inst(1)),
            f.fact(STATIC, listOf(cls), Tail.EXACT, mark))
        val initial = prepared.initials.single()
        assertEquals(listOf(cls), initial.pathData.toList())
        assertEquals(Tail.EXACT, initial.tail)
        assertEquals(mark, initial.mark)
        assertNull(prepared.staticEntries)
    }

    @Test fun `presence survives a real input with no legal initial`() {
        val f = StoreFixtures()
        val ap = f.manager
        val profile = Run1StaticProfiles.build(ap, mapOf(f.method.method to emptyList()),
            mapOf(f.method.method to Run1StaticLocalProfile.EMPTY))[f.method.method]
        val original = ap.initial(AccessPathBase.Argument(0), null, Tail.STAR,
            ExclusionSet.Empty, MarkSlot.STAR)
        val prepared = prepareRun1Initials(f.ops, profile,
            CallerRef(f.method, original, Layer.DEMAND, f.inst(1)),
            f.fact(STATIC, emptyList(), Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND))
        assertTrue(prepared.initials.isEmpty())
        assertNull(prepared.staticEntries)
        assertTrue(prepared.demandPresence)
    }

    @Test fun `presence alone initializes the actor default and retains demand input`() {
        MethodAnalysisFixtures().use { f ->
            val actor = f.core()
            actor.addPreparedInitials(Run1PreparedInitials(emptyList(), null, true))
            f.drain(actor)
            assertTrue(f.initials(actor).isEmpty(), "Presence cannot invent an initial")
            assertFalse(actor.nonZeroInitial)
            val default = f.publications.flatten().single()
            assertTrue(isSpecialStaticRoot(assertIs<InitialAp>(default.premise)))
            assertEquals(Layer.DEMAND, default.conclusion.layer)
            assertEquals(2L, actor.demandLayerEdges, "Stored default plus one demand-input flag")
            actor.addPreparedInitials(Run1PreparedInitials(emptyList(), null, true))
            f.drain(actor)
            assertEquals(2L, actor.demandLayerEdges)
            assertEquals(1, f.publications.flatten().size)
        }
    }
}
