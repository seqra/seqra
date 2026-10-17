package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.MicroEdge
import org.opentaint.dataflow.bidi.interp.MicroEdgeBuilder
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

class MethodFixtureStaticProfileTest {
    @Test fun `lowered value-free write sets the effect bit without requesting a field read`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = Run1Slot.StaticField(cls, f.field("f"))
            val sibling = Run1Slot.StaticField(cls, f.field("g"))
            f.declareStaticDomain(field.pathData.toList(), sibling.pathData.toList())
            f.statement(1, MicroEdgeBuilder().apply {
                write(STATIC, field.pathData.toList(), weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            val footprint = assertNotNull(f.shared.staticFootprint(f.key.method))
            assertFalse(footprint.owns(field.cls, field.field))
            assertTrue(assertNotNull(f.shared.run1StaticProfiles)[f.key.method].staticMayWrite)
            assertFalse(footprint.owns(sibling.cls, sibling.field))
            assertFalse(footprint.ownsPoint(cls))
            assertFalse(footprint.allStatic)
        }
    }

    @Test fun `profile freezes actual configured code and exclusion producer while declared domain stays unowned`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val from = Run1Slot.StaticField(cls, f.field("g"))
            val to = Run1Slot.StaticField(cls, f.field("f"))
            val untouched = Run1Slot.StaticField(cls, f.field("h"))
            val t = f.mark("T")
            f.declareStaticDomain(from.pathData.toList(), to.pathData.toList(), untouched.pathData.toList())
            val edge = PathEdge(PathFact(STATIC, from.pathData.toList(), Tail.STAR, MarkSlot.STAR),
                PathFact(STATIC, to.pathData.toList(), Tail.STAR, MarkSlot.Star(f.manager.markSetOf(t))),
                ExclusionSet.Empty)
            f.statement(0, StatementSummary(setOf(STATIC), listOf(MicroEdge.of(edge)), emptyList(), emptyMap()))
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            val profile = assertNotNull(f.shared.run1StaticProfiles)[f.key.method]
            assertContains(assertNotNull(f.shared.run1StaticProfiles).localReads(f.key.method), from)
            assertFalse(profile.footprint.owns(from.cls, from.field))
            assertFalse(profile.footprint.owns(to.cls, to.field))
            assertTrue(profile.staticMayWrite)
            assertFalse(profile.footprint.owns(untouched.cls, untouched.field))
            assertTrue(untouched in profile.footprint.fieldUniverse)
            assertTrue(profile.precomputedRequests.isEmpty, "Prescan does not precompute static mark requests")
            val origin = from.initial(f.manager)
            val supplier = org.opentaint.dataflow.bidi.store.CallerRef(f.store.key("supplier"), origin,
                Layer.DEMAND, f.inst(1))
            f.admit(actor, supplier, Layer.DEMAND,
                f.fact(STATIC, from.pathData.toList(), Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND))
            f.drain(actor)
            assertEquals(RequestMarkSet.of(t), f.requestRow(actor, from).toRequestMarkSet {},
                "The reached generic exclusion creator records its actual original source need")
            val footprint = f.shared.staticFootprint(f.key.method)
            f.shared.releaseRun1StaticProfiles()
            assertNull(f.shared.run1StaticProfiles)
            assertSame(footprint, f.shared.staticFootprint(f.key.method), "The diagnostic/legal vocabulary can outlive the request plans")
        }
    }

    @Test fun `identity supplier domain creates no owned fields requests or fake mark work`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val slot = Run1Slot.StaticField(cls, f.field("f"))
            f.declareStaticDomain(slot.pathData.toList())
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            val profile = assertNotNull(f.shared.run1StaticProfiles)[f.key.method]
            assertTrue(profile.precomputedRequests.isEmpty)
            assertTrue(profile.footprint.fieldSlots.isEmpty())
            assertEquals(setOf(slot), profile.footprint.fieldUniverse)
            val ref = org.opentaint.dataflow.bidi.store.CallerRef(f.store.key("supplier"), f.manager.zero,
                Layer.NORMAL, f.inst(1))
            f.admit(actor, ref, Layer.NORMAL, f.fact(STATIC, slot.pathData.toList()))
            assertNotNull(actor.staticEntryFacts(), "The actual supplied field has a symbolic entry independent of local reads")
            assertNotNull(actor.concreteStaticEntryFacts(), "The actual supplied mark also starts without a request")
            assertTrue(actor.initials.all().isEmpty(), "No scalar field reader is started in an identity-only method")
            assertFailsWith<IllegalStateException> { f.declareStaticDomain(slot.pathData.toList()) }
        }
    }
}
