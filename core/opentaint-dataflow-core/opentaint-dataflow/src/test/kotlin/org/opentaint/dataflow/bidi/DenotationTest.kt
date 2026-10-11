package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.Conclusion
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.markSub
import org.opentaint.dataflow.bidi.ap.tailAdmits
import org.opentaint.dataflow.bidi.fixtures.BoundedDenotation
import org.opentaint.dataflow.bidi.fixtures.TestLocation
import org.opentaint.dataflow.bidi.fixtures.TestLocationPair
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DenotationTest {
    private val manager = ApManager(Cancellation())
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val freshField = manager.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val freshMark = manager.marks.mark("fresh")
    private val universe = BoundedDenotation(listOf(x, y), listOf(f, g, freshField), listOf(t, u, freshMark), 2)

    private fun pattern(
        base: AccessPathBase = x,
        path: List<Int> = emptyList(),
        tail: Tail = Tail.STAR,
        mark: MarkSlot = MarkSlot.STAR,
        exclusion: ExclusionSet = ExclusionSet.Empty,
    ) = Pattern(PathFact(base, path, tail, mark), exclusion)

    private fun conclusion(p: Pattern, demand: Boolean = p.fact.tail == Tail.ANY) = Conclusion(p.fact, p.exclusion, demand)

    @Test
    fun `admitted continuations follow all four tail rows and only the first excluded accessor`() {
        for (tail in Tail.entries) for (exclusion in listOf(ExclusionSet.Empty, ExclusionSet.of(f))) {
            for (suffix in universe.paths) {
                assertEquals(universe.admitsContinuation(tail, exclusion, suffix), tailAdmits(tail, exclusion, suffix),
                    "tail=$tail exclusion=$exclusion suffix=$suffix")
            }
        }
        assertTrue(tailAdmits(Tail.STAR, ExclusionSet.of(f), emptyList()))
        assertTrue(tailAdmits(Tail.STAR, ExclusionSet.of(f), listOf(g, f)))
        assertFalse(tailAdmits(Tail.STAR, ExclusionSet.of(f), listOf(f, g)))
    }

    @Test
    fun `mark subset compares admitted concrete values including a fresh mark`() {
        val slots = listOf(MarkSlot.Concrete(t), MarkSlot.Concrete(u), MarkSlot.STAR,
            MarkSlot.Star(MarkSet(intArrayOf(t.id))), MarkSlot.Star(MarkSet(intArrayOf(t.id, u.id))))
        for (i in slots) for (c in slots) {
            assertEquals(universe.markedValues(i).containsAll(universe.markedValues(c)), markSub(i, c), "i=$i c=$c")
        }
        assertTrue(freshMark in universe.markedValues(slots.last()))
    }

    @Test
    fun `star conclusion correlates the full continuation and preserves incoming marks`() {
        val premise = pattern()
        val target = conclusion(pattern(y, listOf(g)))
        val sameSuffix = TestLocationPair(TestLocation(x, listOf(f), t), TestLocation(y, listOf(g, f), t))
        assertTrue(universe.containsPair(premise, target, sameSuffix))
        assertFalse(universe.containsPair(premise, target, sameSuffix.copy(exit = TestLocation(y, listOf(g, freshField), t))))
        assertFalse(universe.containsPair(premise, target, sameSuffix.copy(exit = TestLocation(y, listOf(g, f), u))))
        assertTrue(universe.pairs(premise, target).all { it.entry.path == it.exit.path.drop(1) && it.entry.mark == it.exit.mark })
    }

    @Test
    fun `exact conclusion drops an admitted premise continuation and changes the output mark`() {
        val premise = pattern(mark = MarkSlot.Concrete(t))
        val target = conclusion(pattern(y, listOf(g), Tail.EXACT, MarkSlot.Concrete(u)))
        val pair = TestLocationPair(TestLocation(x, listOf(f, freshField), t), TestLocation(y, listOf(g), u))
        assertTrue(universe.containsPair(premise, target, pair))
        assertFalse(universe.containsPair(premise, target, pair.copy(exit = TestLocation(y, listOf(g, f), u))))
        assertFalse(universe.containsPair(premise, target, pair.copy(entry = TestLocation(x, listOf(f), u))))
    }

    @Test
    fun `may conclusions allow independent input and output continuations`() {
        val premise = pattern(mark = MarkSlot.Concrete(t))
        val target = conclusion(pattern(y, tail = Tail.ANY, mark = MarkSlot.Concrete(u)))
        for (sigma in universe.paths) for (tau in universe.paths) {
            assertTrue(universe.containsPair(premise, target,
                TestLocationPair(TestLocation(x, sigma, t), TestLocation(y, tau, u))))
        }
    }

    @Test
    fun `must exclusions are independent on the input and output sides`() {
        val premise = pattern(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(f))
        val target = conclusion(pattern(y, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(u), exclusion = ExclusionSet.of(g)))
        val independent = TestLocationPair(TestLocation(x, listOf(g, f), t), TestLocation(y, listOf(f, g), u))
        assertTrue(universe.containsPair(premise, target, independent))
        assertFalse(universe.containsPair(premise, target, independent.copy(entry = TestLocation(x, listOf(f, g), t))))
        assertFalse(universe.containsPair(premise, target, independent.copy(exit = TestLocation(y, listOf(g, f), u))))
        assertTrue(universe.containsPair(premise, target,
            TestLocationPair(TestLocation(x, emptyList(), t), TestLocation(y, emptyList(), u))))
    }

    @Test
    fun `abstract target exclusions test the incoming mark without changing it`() {
        val premise = pattern()
        val target = conclusion(pattern(y, mark = MarkSlot.Star(MarkSet(intArrayOf(t.id)))))
        assertFalse(universe.containsPair(premise, target,
            TestLocationPair(TestLocation(x, listOf(f), t), TestLocation(y, listOf(f), t))))
        assertTrue(universe.containsPair(premise, target,
            TestLocationPair(TestLocation(x, listOf(f), freshMark), TestLocation(y, listOf(f), freshMark))))
    }

    @Test
    fun `exact premise has one path and a zero source has the single zero entry`() {
        val exact = pattern(path = listOf(f), tail = Tail.EXACT, mark = MarkSlot.Concrete(t))
        assertEquals(setOf(TestLocation(x, listOf(f), t)), universe.marked(exact))
        val zero = manager.zero.toPattern()
        val zeroUniverse = BoundedDenotation(listOf(AccessPathBase.Zero, y), listOf(f),
            listOf(org.opentaint.dataflow.bidi.ap.TaintMark.ZERO, t), 1)
        assertEquals(setOf(TestLocation(AccessPathBase.Zero, emptyList(), org.opentaint.dataflow.bidi.ap.TaintMark.ZERO)),
            zeroUniverse.marked(zero))
        val source = conclusion(pattern(y, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t)))
        assertEquals(2, zeroUniverse.pairs(zero, source).size)
    }

    @Test
    fun `must empty exclusion and may have the same locations and uncorrelated pairs`() {
        val must = pattern(y, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t))
        val may = must.copy(fact = must.fact.copy(tail = Tail.ANY))
        val premise = pattern(mark = MarkSlot.Concrete(u))
        assertEquals(universe.marked(must), universe.marked(may))
        assertEquals(universe.pairs(premise, conclusion(must)), universe.pairs(premise, conclusion(may)))
    }

    @Test
    fun `current proposal T2 witness requires union equality rather than mutual leaf coverage`() {
        // CurrentProposal.T2Oracle is current local data; it does not prove a general store theorem.
        val premise = pattern(mark = MarkSlot.Concrete(u))
        val left = pattern(y, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(f))
        val right = left.copy(exclusion = ExclusionSet.of(g))
        val merged = left.copy(exclusion = ExclusionSet.Empty)
        val referenceLocations = universe.marked(left) + universe.marked(right)
        val actualLocations = universe.marked(merged)
        assertTrue(actualLocations.containsAll(referenceLocations))
        assertTrue(referenceLocations.containsAll(actualLocations))
        val referencePairs = universe.pairs(premise, conclusion(left)) + universe.pairs(premise, conclusion(right))
        val actualPairs = universe.pairs(premise, conclusion(merged))
        assertTrue(actualPairs.containsAll(referencePairs))
        assertTrue(referencePairs.containsAll(actualPairs))
        assertFalse(universe.marked(left).containsAll(actualLocations))
        assertFalse(universe.marked(right).containsAll(actualLocations))
        assertTrue(TestLocation(y, listOf(f), t) in actualLocations)
        assertTrue(TestLocation(y, listOf(g), t) in actualLocations)
        assertTrue(TestLocation(y, listOf(freshField), t) in actualLocations)
        assertFalse(TestLocation(y, listOf(freshField), freshMark) in actualLocations)
    }
}
