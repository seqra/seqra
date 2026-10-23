package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import kotlin.test.*

/** Ordinary bounded AP semantics replace the removed exact-input validator. */
class Run1GroundingTest {
    @Test fun `canonical group STAR permits a sibling-field DEMAND check`() {
        MethodAnalysisFixtures().use { f ->
            val original = f.manager.initial(f.run1Pattern(tail = Tail.STAR))
            val literal = f.pattern(path = listOf(f.field("right")))
            val mode = ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.GROUP)
            val current = f.ops.startFact(original, mode)
            val result = assertIs<MarkCheck.Holds>(f.ops.checkMark(current, literal, mode))
            assertEquals(Layer.DEMAND, result.facts.layer)
            assertEquals(f.ops.leaves(current).toSet(), f.ops.leaves(result.facts).toSet(),
                "A successful mark check retains the original admitted current image")
        }
    }

    @Test fun `an actual disjoint represented path still fails the ordinary AP match`() {
        MethodAnalysisFixtures().use { f ->
            val left = f.run1Fact(path = listOf(f.field("left")))
            val right = f.pattern(path = listOf(f.field("right")))
            val mode = ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.GROUP)
            assertSame(MarkCheck.None, f.ops.checkMark(left, right, mode))
            assertIs<MarkCheck.Holds>(f.ops.checkMark(left,
                f.pattern(path = listOf(f.field("left"))), mode))
        }
    }

    @Test fun `full refinement keeps distinct actual field paths`() {
        MethodAnalysisFixtures().use { f ->
            val full = f.mark("T")
            val left = f.fact(path = listOf(f.field("left")), mark = MarkSlot.Concrete(full))
            val right = f.pattern(path = listOf(f.field("right")), mark = MarkSlot.Concrete(full))
            val mode = ApMode(false, Direction.FORWARD, 2, MarkMode.FULL)
            assertSame(MarkCheck.None, f.ops.checkMark(left, right, mode))
            val held = assertIs<MarkCheck.Holds>(f.ops.checkMark(left,
                f.pattern(path = listOf(f.field("left")), mark = MarkSlot.Concrete(full)), mode))
            assertEquals(MarkSlot.Concrete(full), f.ops.leaves(held.facts).single().fact.mark)
        }
    }

    @Test fun `a selected cleaner does not remove its group DEMAND complement`() {
        MethodAnalysisFixtures().use { f ->
            val full = f.mark("T")
            val input = f.run1Fact(AccessPathBase.Argument(0))
            val out = CollectingOut()
            f.ops.clean(input, f.manager.zero,
                Cleaner(input.base, null, CleanReach.AT_AND_BELOW, full),
                ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.GROUP), out)
            assertEquals(listOf(input), out.results)
            assertTrue(out.results.all { it.layer == Layer.DEMAND })
        }
    }
}
