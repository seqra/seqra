package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

class Run1GroundingTest {
    @Test fun `transient support is restored between independent operation callbacks`() {
        MethodAnalysisFixtures().use { f ->
            val actor = f.core()
            actor.withGroundingSupport(GroundingSupport.False) {
                assertEquals(GroundingSupport.False, actor.currentGroundingSupport)
                actor.withGroundingSupport(GroundingSupport.TRUE) {
                    assertEquals(GroundingSupport.TRUE, actor.currentGroundingSupport)
                }
                assertEquals(GroundingSupport.False, actor.currentGroundingSupport)
            }
            assertEquals(GroundingSupport.TRUE, actor.currentGroundingSupport)
        }
    }

    @Test fun `an earlier owned unconditional cofactor remains true after a guarded duplicate`() {
        MethodAnalysisFixtures().use { f ->
            val actor = f.core()
            val original = f.manager.initial(f.run1Pattern()); val value = f.run1Fact()
            val emitted = ArrayList<GroundingSupport>()
            actor.edges.addSupported(f.inst(1), original, value, GroundingSupport.TRUE) { _, support -> emitted += support }
            actor.edges.addSupported(f.inst(1), original, value, GroundingSupport.False) { _, support -> emitted += support }
            assertEquals(listOf(GroundingSupport.TRUE), emitted)
            assertEquals(1L, actor.edges.groundingTrueRows)
            assertEquals(0L, actor.edges.groundingGuardedRows)
        }
    }

    @Test fun `support becoming true refreshes an already admitted owned relation`() {
        MethodAnalysisFixtures().use { f ->
            val actor = f.core()
            val original = f.manager.initial(f.run1Pattern()); val value = f.run1Fact()
            val emitted = ArrayList<GroundingSupport>()
            for (support in listOf(GroundingSupport.False, GroundingSupport.False,
                    GroundingSupport.TRUE, GroundingSupport.TRUE))
                actor.edges.addSupported(f.inst(1), original, value, support) { _, part -> emitted += part }
            assertEquals(listOf(GroundingSupport.False, GroundingSupport.TRUE), emitted)
        }
    }

    @Test fun `an actor republishes a genuine unconditional alternative after a guarded summary`() {
        MethodAnalysisFixtures().use { f ->
            val actor = f.core()
            val index = PublicationIndex(f.manager, f.ops)
            val admitted = ArrayList<GroundingSupport>()
            f.onPublish = { _, batch -> admitted += index.addAll(batch).map { it.grounding } }
            val value = f.run1Fact(base = AccessPathBase.Return)
            for (support in listOf(GroundingSupport.False, GroundingSupport.TRUE, GroundingSupport.TRUE)) {
                actor.withGroundingSupport(support) { actor.summaryDelta(f.manager.zero, value) }
                f.drain(actor)
            }
            assertEquals(listOf(GroundingSupport.False, GroundingSupport.TRUE), admitted)
            // The actor forwards current support; only the publication index owns its union.
            assertTrue(f.publications.drop(1).flatten().any { it.grounding == GroundingSupport.TRUE })
        }
        MethodAnalysisFixtures().use { f ->
            val actor = f.core()
            val value = f.run1Fact(base = AccessPathBase.Return)
            repeat(2) {
                actor.summaryDelta(f.manager.zero, value)
                f.drain(actor)
            }
            assertEquals(1, f.publications.flatten().size) // ordinary TRUE-only bulk path
        }
    }

    private fun spatialGuard(actualField: String, flowFirst: Boolean, excluded: Boolean = false,
                             rebind: Boolean = false): Boolean {
        MethodAnalysisFixtures().use { f ->
            val actor = f.core(); val grounding = Run1Grounding(actor)
            val full = f.mark("condition T")
            val group = f.manager.marks.groupOf(full)
            val atom = f.manager.marks.group(group)
            val base = AccessPathBase.Argument(0)
            val original = f.manager.initial(base, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.Concrete(atom))
            val abstract = f.manager.initial(base, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            val site = ResumeSite.StatementInput(actor.operation(f.inst(1)))
            val hypothetical = actor.ops.startFact(original, actor.mode)
            val flow = actor.ops.startFact(abstract, actor.mode) as FlowTree
            val geometry = if (!excluded) flow else f.manager.flowTree(base, Layer.NORMAL, ExclusionSet.Empty,
                MarkExclusions.of(f.manager.marks, atom), flow.root, flow.tail)
            val at = OperationInput(site, original, hypothetical)
            val literal = f.pattern(base, listOf(f.field("f")), mark = MarkSlot.Concrete(full))
            val checked = assertIs<MarkCheck.Holds>(actor.ops.checkMark(hypothetical, literal, actor.mode))
            if (!flowFirst) assertEquals(GroundingSupport.False, grounding.requirement(at, checked.facts, literal))
            grounding.observe(OperationInput(site, abstract, geometry))
            val support = grounding.requirement(at, checked.facts, literal)
            val actual = RegionGroups(f.pattern(base, listOf(f.field(actualField)), tail = Tail.EXACT,
                mark = MarkSlot.STAR), GroupSet.of(group))
            if (!rebind) return grounding.permits(original, hypothetical, listOf(actual), support)
            val ref = CallerRef(actor.key, f.manager.zero, Layer.NORMAL, f.inst(1))
            val bound = grounding.rebind(support, listOf(GroundingArgument(original, ref, actor.staticFacts(actual))))
            return grounding.permits(f.manager.zero, hypothetical, emptyList(), bound)
        }
    }

    @Test fun `FLOW input geometry rejects a sibling that a group STAR entry would hide`() {
        assertFalse(spatialGuard("h", flowFirst = true))
        assertTrue(spatialGuard("f", flowFirst = true))
    }

    @Test fun `a late FLOW input wakes the same positive condition without inventing support`() {
        assertFalse(spatialGuard("h", flowFirst = false))
        assertTrue(spatialGuard("f", flowFirst = false))
    }

    @Test fun `guard geometry composes through an actual zero caller binding`() {
        assertFalse(spatialGuard("h", flowFirst = true, rebind = true))
        assertTrue(spatialGuard("f", flowFirst = true, rebind = true))
    }

    @Test fun `a DEMAND group complement is not rejected by FLOW mark exclusions`() {
        assertTrue(spatialGuard("f", flowFirst = true, excluded = true))
        assertTrue(spatialGuard("f", flowFirst = true, excluded = true, rebind = true))
    }

    @Test fun `a nonempty inherited guard does not certify a missing FLOW geometry`() {
        MethodAnalysisFixtures().use { f ->
            val actor = f.core(); val grounding = Run1Grounding(actor)
            val original = f.manager.initial(f.run1Pattern(tail = Tail.STAR))
            val hypothetical = actor.ops.startFact(original, actor.mode)
            val literal = f.pattern(path = listOf(f.field("f")))
            val checked = assertIs<MarkCheck.Holds>(actor.ops.checkMark(hypothetical, literal, actor.mode))
            val inherited = GroundingSupport.require(listOf(GroundingRequirement(original, hypothetical, f.pattern())))
            val at = OperationInput(ResumeSite.StatementInput(actor.operation(f.inst(1))), original, hypothetical,
                support = inherited)
            assertEquals(GroundingSupport.False, grounding.requirement(at, checked.facts, literal))
            val certified = grounding.fixed(at) // explicit actual fixed-target producer certificate
            assertNotEquals(GroundingSupport.False,
                grounding.requirement(at.copy(support = certified), checked.facts, literal))
        }
    }

    @Test fun `an uncertified TRUE alternative does not erase a fixed image's input guard`() {
        MethodAnalysisFixtures().use { f ->
            val actor = f.core(); val grounding = Run1Grounding(actor)
            val full = f.mark("T"); val group = f.manager.marks.groupOf(full)
            val original = f.manager.initial(f.run1Pattern(tail = Tail.STAR))
            val hypothetical = actor.ops.startFact(original, actor.mode)
            val site = ResumeSite.StatementInput(actor.operation(f.inst(1)))
            val abstract = f.manager.initial(original.base, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            val guard = GroundingSupport.require(listOf(GroundingRequirement(original,
                actor.ops.startFact(abstract, actor.mode), f.pattern(path = listOf(f.field("f"))),
                relationInitial = abstract)))
            val certified = GroundingSupport.withFixed(guard, guard).or(GroundingSupport.TRUE)
            assertEquals(GroundingSupport.TRUE, certified.logical)
            val at = OperationInput(site, original, hypothetical, support = certified)
            val literal = f.pattern(path = listOf(f.field("g")))
            val checked = assertIs<MarkCheck.Holds>(actor.ops.checkMark(hypothetical, literal, actor.mode))
            val selected = grounding.requirement(at, checked.facts, literal)
            val actual = RegionGroups(f.pattern(path = listOf(f.field("h")), mark = MarkSlot.STAR), GroupSet.of(group))
            assertFalse(grounding.permits(original, hypothetical, listOf(actual), selected))
        }
    }
}
