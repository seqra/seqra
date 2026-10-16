package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApMode
import org.opentaint.dataflow.bidi.ap.Conclusion
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FactShapeTest {
    private val manager = ApManager(Cancellation())
    private val base = AccessPathBase.Argument(0)
    private val mark = MarkSlot.Concrete(manager.marks.mark("T"))
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))

    private fun pattern(tail: Tail, slot: MarkSlot = mark, exclusion: ExclusionSet = ExclusionSet.Empty) =
        Pattern(PathFact(base, listOf(f), tail, slot), exclusion)

    @Test
    fun `initial rejects abstract exact and abstract must shapes`() {
        for (tail in listOf(Tail.EXACT, Tail.ANY_TAINT)) {
            assertFails { manager.initial(pattern(tail, MarkSlot.STAR)) }
            assertFails { manager.initial(base, manager.path(listOf(f)), tail, ExclusionSet.Empty, MarkSlot.STAR) }
        }
    }

    @Test
    fun `initial rejects excluded abstract marks and exact or may field exclusions`() {
        val excludedMark = MarkSlot.Star(manager.markSetOf(manager.marks.mark("T")))
        assertFails { manager.initial(pattern(Tail.STAR, excludedMark)) }
        assertFails { manager.initial(pattern(Tail.ANY, excludedMark)) }
        for (tail in listOf(Tail.EXACT, Tail.ANY)) {
            assertFails { manager.initial(pattern(tail, exclusion = ExclusionSet.of(g))) }
        }
    }

    @Test
    fun `valid initial shapes have canonical complete keys`() {
        val cases = listOf(
            pattern(Tail.EXACT),
            pattern(Tail.ANY),
            pattern(Tail.STAR, MarkSlot.STAR),
            pattern(Tail.STAR, MarkSlot.STAR, ExclusionSet.of(g)),
            pattern(Tail.STAR, mark, ExclusionSet.of(g)),
            pattern(Tail.ANY_TAINT, mark, ExclusionSet.of(g)),
        )
        for (p in cases) {
            val first = manager.initial(p)
            assertSame(first, manager.initial(p))
            assertEquals(p, first.toPattern())
            assertSame(first, manager.initial(base, manager.path(listOf(f)), p.fact.tail, p.exclusion, p.fact.mark))
        }
    }

    @Test
    fun `zero is the canonical exact zero marked empty path premise`() {
        val zero = manager.zero
        assertSame(zero, manager.initial(AccessPathBase.Zero, null, Tail.EXACT, ExclusionSet.Empty,
            MarkSlot.Concrete(TaintMark.ZERO)))
        assertEquals(AccessPathBase.Zero, zero.base)
        assertEquals(Tail.EXACT, zero.tail)
        assertEquals(MarkSlot.Concrete(TaintMark.ZERO), zero.mark)
        assertEquals(ExclusionSet.Empty, zero.exclusion)
        assertTrue(zero.pathArray.isEmpty())
        assertTrue(zero.isZero)
        assertEquals(1, zero.size)
        assertEquals(0, zero.nonZeroCount)
    }

    @Test
    fun `zero initial rejects a path nonexact tail exclusion or nonzero mark`() {
        val zeroMark = MarkSlot.Concrete(TaintMark.ZERO)
        assertFails { manager.initial(AccessPathBase.Zero, manager.path(listOf(f)), Tail.EXACT, ExclusionSet.Empty, zeroMark) }
        assertFails { manager.initial(AccessPathBase.Zero, null, Tail.ANY, ExclusionSet.Empty, zeroMark) }
        assertFails { manager.initial(AccessPathBase.Zero, null, Tail.STAR, ExclusionSet.of(f), zeroMark) }
        assertFails { manager.initial(AccessPathBase.Zero, null, Tail.EXACT, ExclusionSet.Empty, mark) }
    }

    @Test
    fun `must may and distinct must exclusions have separate premise identities`() {
        val may = manager.initial(pattern(Tail.ANY))
        val must = manager.initial(pattern(Tail.ANY_TAINT))
        val mustF = manager.initial(pattern(Tail.ANY_TAINT, exclusion = ExclusionSet.of(f)))
        val mustG = manager.initial(pattern(Tail.ANY_TAINT, exclusion = ExclusionSet.of(g)))
        val keys = listOf(may, must, mustF, mustG)
        assertEquals(4, keys.toSet().size)
        assertEquals(4, keys.map { it.id }.toSet().size)
        assertNotSame(mustF, mustG)
    }

    @Test
    fun `raw generic abstract exact remains available without becoming an initial`() {
        val p = pattern(Tail.EXACT, MarkSlot.STAR)
        val raw = Conclusion(p.fact, p.exclusion, false)
        assertEquals(Tail.EXACT, raw.fact.tail)
        assertEquals(MarkSlot.STAR, raw.fact.mark)
        assertFails { manager.initial(p) }
    }

    @Test
    fun `raw conclusion preserves a demand star tail and field exclusions`() {
        val p = pattern(Tail.STAR, MarkSlot.STAR, ExclusionSet.of(g))
        assertEquals(p.fact, Conclusion(p.fact, p.exclusion, false).fact)
        assertEquals(Conclusion(p.fact, p.exclusion, false).copy(demand = true),
            Conclusion(p.fact, p.exclusion, true))
    }

    @Test
    fun `initial path snapshots cannot change an interned premise`() {
        val input = intArrayOf(f, g)
        val initial = manager.initial(base, manager.path(input), Tail.EXACT, ExclusionSet.Empty, mark)
        val hash = initial.hashCode()
        input[0] = g
        initial.pathArray[0] = g
        assertEquals(listOf(f, g), initial.pathArray.asList())
        assertEquals(listOf(f, g), initial.toPattern().fact.path)
        assertEquals(hash, initial.hashCode())
        assertSame(initial, manager.initial(base, manager.path(intArrayOf(f, g)), Tail.EXACT, ExclusionSet.Empty, mark))
    }

    @Test
    fun `must initial construction does not impose a run mode`() {
        // A07/E02 own mode rejection. InitialAp and ApManager.initial have no mode argument.
        val initial = manager.initial(pattern(Tail.ANY_TAINT, exclusion = ExclusionSet.of(g)))
        assertEquals(Tail.ANY_TAINT, initial.tail)
        assertEquals(ExclusionSet.of(g), initial.exclusion)
    }

    @Test
    fun `positive limits support both restricted directions and forward run one`() {
        for (fieldLimit in listOf(1, 3, Int.MAX_VALUE)) {
            assertFalse(ApMode(true, Direction.FORWARD, fieldLimit).restricted)
            assertTrue(ApMode(false, Direction.BACKWARD, fieldLimit).restricted)
            assertTrue(ApMode(false, Direction.FORWARD, fieldLimit).restricted)
            assertFails { ApMode(true, Direction.BACKWARD, fieldLimit) }
        }
    }

    @Test
    fun `all directions and run flags reject nonpositive limits`() {
        for (run1 in listOf(false, true)) {
            for (direction in Direction.entries) {
                for (fieldLimit in listOf(0, -1, Int.MIN_VALUE)) {
                    assertFails("run1=$run1 direction=$direction fieldLimit=$fieldLimit") {
                        ApMode(run1, direction, fieldLimit)
                    }
                }
            }
        }
    }
}
