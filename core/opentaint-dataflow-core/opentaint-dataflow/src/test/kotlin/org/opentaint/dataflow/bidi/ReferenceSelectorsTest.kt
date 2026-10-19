package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.applicable
import org.opentaint.dataflow.bidi.ap.covers
import org.opentaint.dataflow.bidi.ap.inside
import org.opentaint.dataflow.bidi.ap.overlap
import org.opentaint.dataflow.bidi.ap.satisfies
import org.opentaint.dataflow.bidi.fixtures.BoundedDenotation
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReferenceSelectorsTest {
    private val manager = testApManager(Cancellation())
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val freshField = manager.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val element = manager.accessors.index(ElementAccessor)
    private val classAccessor = manager.accessors.index(ClassStaticAccessor("Box"))
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val freshMark = manager.marks.mark("fresh")
    // Pattern depth is at most one. A second level exposes every possible first suffix accessor.
    private val universe = BoundedDenotation(listOf(x, y), listOf(f, g, freshField, element, classAccessor),
        listOf(t, u, freshMark), 2)

    private fun p(
        path: List<Int> = emptyList(),
        tail: Tail = Tail.STAR,
        mark: MarkSlot = MarkSlot.STAR,
        exclusion: ExclusionSet = ExclusionSet.Empty,
        base: AccessPathBase = x,
    ) = Pattern(PathFact(base, path, tail, mark), exclusion)

    private fun patterns(): List<Pattern> {
        val concrete = listOf(MarkSlot.Concrete(t), MarkSlot.Concrete(u))
        val slots = concrete + listOf(MarkSlot.STAR,
            MarkSlot.Star(MarkSet(intArrayOf(t.id))),
            MarkSlot.Star(MarkSet(intArrayOf(u.id))),
            MarkSlot.Star(MarkSet(intArrayOf(t.id, u.id))))
        val exclusions = listOf(ExclusionSet.Empty, ExclusionSet.of(f), ExclusionSet.of(g), ExclusionSet.of(intArrayOf(f, g)))
        return buildList {
            for (base in listOf(x, y)) for (path in listOf(emptyList(), listOf(f), listOf(g))) {
                for (tail in Tail.entries) {
                    val marks = if (tail == Tail.ANY_TAINT) concrete else slots
                    val fields = if (tail == Tail.STAR || tail == Tail.ANY_TAINT) exclusions else listOf(ExclusionSet.Empty)
                    for (mark in marks) for (exclusion in fields) add(p(path, tail, mark, exclusion, base))
                }
            }
        }
    }

    @Test
    fun `covers overlap applicable and inside equal independently enumerated location tests`() {
        val patterns = patterns()
        val spatial = patterns.associateWith(universe::spatial)
        val marked = patterns.associateWith(universe::marked)
        val marks = patterns.map { it.fact.mark }.distinct().associateWith(universe::markedValues)
        for (j in patterns) for (a in patterns) {
            val expectedCover = marked.getValue(j).containsAll(marked.getValue(a))
            val expectedOverlap = spatial.getValue(j).any { it in spatial.getValue(a) }
            val expectedApplicable = expectedCover &&
                (j.fact.tail !in setOf(Tail.ANY, Tail.ANY_TAINT) || a.fact.tail in setOf(Tail.ANY, Tail.ANY_TAINT))
            val expectedInside = spatial.getValue(a).containsAll(spatial.getValue(j)) &&
                marks.getValue(j.fact.mark).containsAll(marks.getValue(a.fact.mark))
            val label = "j=$j a=$a"
            assertEquals(expectedCover, covers(j, a), "covers: $label")
            assertEquals(expectedOverlap, overlap(j, a), "overlap: $label")
            assertEquals(expectedApplicable, applicable(j, a), "applicable: $label")
            assertEquals(expectedInside, inside(j, a), "inside: $label")
        }
    }

    @Test
    fun `satisfaction in both modes follows full spatial and mark guards`() {
        val patterns = patterns()
        val spatial = patterns.associateWith(universe::spatial)
        val marked = patterns.associateWith(universe::marked)
        val marks = patterns.map { it.fact.mark }.distinct().associateWith(universe::markedValues)
        for (j in patterns) for (a in patterns) {
            val expectedApplicable = marked.getValue(j).containsAll(marked.getValue(a)) &&
                (j.fact.tail !in setOf(Tail.ANY, Tail.ANY_TAINT) || a.fact.tail in setOf(Tail.ANY, Tail.ANY_TAINT))
            val expectedInside = spatial.getValue(a).containsAll(spatial.getValue(j)) &&
                marks.getValue(j.fact.mark).containsAll(marks.getValue(a.fact.mark))
            val canonicalFlow = j.fact.tail == Tail.STAR && j.exclusion == ExclusionSet.Empty && j.fact.mark == MarkSlot.STAR
            val expectedRestricted = if (canonicalFlow) spatial.getValue(j).any { it in spatial.getValue(a) }
                else expectedInside || (j.fact.mark == MarkSlot.STAR && expectedApplicable)
            assertEquals(expectedApplicable || expectedInside, satisfies(j, a, restricted = false), "run1: j=$j a=$a")
            assertEquals(expectedRestricted, satisfies(j, a, restricted = true), "restricted: j=$j a=$a")
        }
    }

    @Test
    fun `overlap ignores marks but respects must exclusions and different bases`() {
        val must = p(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(f))
        val excluded = p(listOf(f), Tail.EXACT, MarkSlot.Concrete(u))
        val admitted = p(listOf(g), Tail.EXACT, MarkSlot.Concrete(u))
        assertFalse(overlap(must, excluded))
        assertTrue(overlap(must, admitted))
        assertFalse(covers(must, admitted))
        assertFalse(overlap(must, admitted.copy(fact = admitted.fact.copy(base = y))))
        assertTrue(overlap(p(tail = Tail.EXACT, mark = MarkSlot.Concrete(t)),
            p(tail = Tail.EXACT, mark = MarkSlot.Concrete(u))))
    }

    @Test
    fun `canonical flow is annotated overlap even for excluded abstract added marks`() {
        val flow = p(listOf(g))
        for (a in patterns()) {
            val expected = universe.spatial(flow).any { it in universe.spatial(a) }
            assertEquals(expected, satisfies(flow, a, restricted = true), "added=$a")
        }
        val admitted = p(listOf(g), Tail.ANY, MarkSlot.Star(MarkSet(intArrayOf(t.id))))
        assertTrue(satisfies(flow, admitted, restricted = true))
        val blockedMust = p(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(g))
        assertFalse(satisfies(flow, blockedMust, restricted = true))
    }

    @Test
    fun `noncanonical flow exclusions retain both full guards instead of overlap shortcut`() {
        val j = p(exclusion = ExclusionSet.of(f))
        val a = p(exclusion = ExclusionSet.of(g))
        assertTrue(overlap(j, a))
        assertFalse(inside(j, a))
        assertFalse(applicable(j, a))
        assertFalse(satisfies(j, a, restricted = true))
        val narrowerAdded = p(exclusion = ExclusionSet.of(intArrayOf(f, g)))
        assertTrue(applicable(j, narrowerAdded))
        assertTrue(satisfies(j, narrowerAdded, restricted = true))
        assertTrue(inside(j, p()))
        assertTrue(satisfies(j, p(), restricted = true))
    }

    @Test
    fun `concrete premise never accepts abstract added marks in restricted mode`() {
        val j = p(tail = Tail.EXACT, mark = MarkSlot.Concrete(t))
        for (a in listOf(p(), p(mark = MarkSlot.Star(MarkSet(intArrayOf(u.id)))),
            p(tail = Tail.ANY, mark = MarkSlot.STAR))) {
            assertTrue(overlap(j, a))
            assertFalse(satisfies(j, a, restricted = true))
        }
    }

    @Test
    fun `concrete STAR E native reader matches admitted exact or may requirements with full guards`() {
        // F76 read premise data, not generic reversed-record or callee-demand selection data.
        val reader = p(listOf(f), Tail.STAR, MarkSlot.Concrete(t), ExclusionSet.of(g))
        val exact = p(listOf(f, freshField), Tail.EXACT, MarkSlot.Concrete(t))
        val may = p(listOf(f, freshField), Tail.ANY, MarkSlot.Concrete(t))
        val excluded = p(listOf(f, g), Tail.EXACT, MarkSlot.Concrete(t))
        assertTrue(applicable(reader, exact))
        assertTrue(applicable(reader, may))
        assertFalse(inside(reader, exact))
        assertFalse(satisfies(reader, exact, restricted = true))
        assertFalse(applicable(reader, excluded))
        assertFalse(overlap(reader, excluded))
        assertFalse(applicable(reader, exact.copy(fact = exact.fact.copy(mark = MarkSlot.Concrete(u)))))
        // Native reader admission uses inside OR applicable, not concrete restricted-summary satisfaction.
        assertTrue(inside(reader, exact) || applicable(reader, exact))
        assertTrue(inside(reader, may) || applicable(reader, may))
    }

    @Test
    fun `must premise needs every admitted location and exact added input is insufficient`() {
        val j = p(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(f))
        val exact = p(tail = Tail.EXACT, mark = MarkSlot.Concrete(t))
        val tooNarrow = p(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(intArrayOf(f, g)))
        val coveringMust = p(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t))
        val coveringMay = p(tail = Tail.ANY, mark = MarkSlot.Concrete(t))
        assertFalse(satisfies(j, exact, restricted = true))
        assertFalse(satisfies(j, tooNarrow, restricted = true))
        assertTrue(satisfies(j, coveringMust, restricted = true))
        assertTrue(satisfies(j, coveringMay, restricted = true))
    }

    @Test
    fun `an any premise cannot apply to an exact or correlated leaf despite location coverage`() {
        val j = p(tail = Tail.ANY, mark = MarkSlot.STAR)
        val exact = p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t))
        val correlated = p(listOf(f))
        assertTrue(covers(j, exact))
        assertTrue(covers(j, correlated))
        assertFalse(applicable(j, exact))
        assertFalse(applicable(j, correlated))
        assertTrue(applicable(j, p(listOf(f), Tail.ANY_TAINT, MarkSlot.Concrete(t))))
    }

    @Test
    fun `nested selector spines test the first relative accessor and ignore later excluded accessors`() {
        // The longest pattern has depth three. Depth four retains witnesses below every pattern.
        val deepUniverse = BoundedDenotation(listOf(x, y), listOf(f, g, freshField), listOf(t, u, freshMark), 4)

        fun check(j: Pattern, a: Pattern) {
            val jSpatial = deepUniverse.spatial(j)
            val aSpatial = deepUniverse.spatial(a)
            val expectedCover = deepUniverse.marked(j).containsAll(deepUniverse.marked(a))
            val expectedOverlap = jSpatial.any { it in aSpatial }
            val expectedApplicable = expectedCover &&
                (j.fact.tail !in setOf(Tail.ANY, Tail.ANY_TAINT) || a.fact.tail in setOf(Tail.ANY, Tail.ANY_TAINT))
            val expectedInside = aSpatial.containsAll(jSpatial) &&
                deepUniverse.markedValues(j.fact.mark).containsAll(deepUniverse.markedValues(a.fact.mark))
            val canonicalFlow = j.fact.tail == Tail.STAR && j.exclusion == ExclusionSet.Empty && j.fact.mark == MarkSlot.STAR
            val expectedRestricted = if (canonicalFlow) expectedOverlap
                else expectedInside || (j.fact.mark == MarkSlot.STAR && expectedApplicable)
            val label = "nested j=$j a=$a"
            assertEquals(expectedCover, covers(j, a), "covers: $label")
            assertEquals(expectedOverlap, overlap(j, a), "overlap: $label")
            assertEquals(expectedApplicable, applicable(j, a), "applicable: $label")
            assertEquals(expectedInside, inside(j, a), "inside: $label")
            assertEquals(expectedApplicable || expectedInside, satisfies(j, a, restricted = false), "run1: $label")
            assertEquals(expectedRestricted, satisfies(j, a, restricted = true), "restricted: $label")
        }

        val admittedSpine = listOf(f, g, f)
        val excludedSpine = listOf(f, f, g)
        for (tail in listOf(Tail.STAR, Tail.ANY_TAINT)) {
            val mark = if (tail == Tail.STAR) MarkSlot.STAR else MarkSlot.Concrete(t)
            val excludesF = p(listOf(f), tail, mark, ExclusionSet.of(f))
            val excludesG = p(listOf(f), tail, mark, ExclusionSet.of(g))
            for (addedTail in listOf(Tail.EXACT, Tail.ANY, Tail.ANY_TAINT)) {
                val admitted = p(admittedSpine, addedTail, MarkSlot.Concrete(t))
                val excluded = p(excludedSpine, addedTail, MarkSlot.Concrete(t))
                check(excludesF, admitted)
                check(excludesF, excluded)
                check(excludesG, admitted)
                assertTrue(covers(excludesF, admitted))
                assertTrue(overlap(excludesF, admitted))
                assertFalse(covers(excludesF, excluded))
                assertFalse(overlap(excludesF, excluded))
                assertFalse(overlap(excludesG, admitted))
            }
        }

        val insideRequirement = p(admittedSpine, Tail.EXACT, MarkSlot.Concrete(t))
        val coveringAdded = p(listOf(f), Tail.ANY_TAINT, MarkSlot.Concrete(t), ExclusionSet.of(f))
        val blockedAdded = coveringAdded.copy(exclusion = ExclusionSet.of(g))
        check(insideRequirement, coveringAdded)
        check(insideRequirement, blockedAdded)
        assertTrue(inside(insideRequirement, coveringAdded))
        assertTrue(satisfies(insideRequirement, coveringAdded, restricted = true))
        assertFalse(inside(insideRequirement, blockedAdded))
        assertFalse(satisfies(insideRequirement, blockedAdded, restricted = true))
    }
}
