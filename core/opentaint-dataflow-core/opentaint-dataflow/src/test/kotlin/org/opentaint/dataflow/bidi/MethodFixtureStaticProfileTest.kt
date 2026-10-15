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
    @Test fun `lowered value-free strong write owns its field but leaves its sibling and class point framed`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = Run1Slot.StaticField(cls, f.field("f"))
            val sibling = Run1Slot.StaticField(cls, f.field("g"))
            f.declareStaticDomain(field.pathData.toList(), sibling.pathData.toList())
            f.statement(1, MicroEdgeBuilder().apply {
                write(STATIC, field.pathData.toList(), weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            val footprint = assertNotNull(f.shared.staticFootprint(f.key.method))
            assertTrue(footprint.owns(field.cls, field.field))
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
            f.core()
            val profile = assertNotNull(f.shared.run1StaticProfiles)[f.key.method]
            assertTrue(profile.footprint.owns(from.cls, from.field))
            assertTrue(profile.footprint.owns(to.cls, to.field))
            assertFalse(profile.footprint.owns(untouched.cls, untouched.field))
            assertTrue(untouched in profile.footprint.fieldUniverse)
            assertEquals(RequestMarkSet.of(t), profile.precomputedRequests[from],
                "A generic excluded target requests its actual original static origin")
            val footprint = f.shared.staticFootprint(f.key.method)
            f.shared.releaseRun1StaticProfiles()
            assertNull(f.shared.run1StaticProfiles)
            assertSame(footprint, f.shared.staticFootprint(f.key.method), "Later summary readers keep structural ownership only")
        }
    }

    @Test fun `identity supplier domain creates no owned fields requests or fake mark work`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val slot = Run1Slot.StaticField(cls, f.field("f"))
            f.declareStaticDomain(slot.pathData.toList())
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            val profile = assertNotNull(f.shared.run1StaticProfiles)[f.key.method]
            assertTrue(profile.precomputedRequests.isEmpty())
            assertTrue(profile.footprint.fieldSlots.isEmpty())
            assertEquals(setOf(slot), profile.footprint.fieldUniverse)
            val ref = org.opentaint.dataflow.bidi.store.CallerRef(f.store.key("supplier"), f.manager.zero,
                Layer.NORMAL, f.inst(1))
            f.admit(actor, ref, Layer.NORMAL, f.fact(STATIC, slot.pathData.toList()))
            assertEquals(setOf(f.manager.initial(STATIC, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)),
                actor.initials.all().toSet())
            assertFailsWith<IllegalStateException> { f.declareStaticDomain(slot.pathData.toList()) }
        }
    }
}
