package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Expected facts below are literal current AP table rows, independent of trie operations. */
class ReferenceOperationsTest {
    private val manager = ApManager(Cancellation())
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val cls = manager.accessors.index(ClassStaticAccessor("Box"))
    private val element = manager.accessors.index(ElementAccessor)
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val empty = ExclusionSet.Empty

    private fun marks(vararg values: Int) = MarkSet(values)
    private fun ex(vararg values: Int) = ExclusionSet.of(values)
    private fun p(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                  exclusion: ExclusionSet = empty, base: AccessPathBase = x) =
        Pattern(PathFact(base, path, tail, mark), exclusion)
    private fun c(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                  exclusion: ExclusionSet = empty, demand: Boolean = tail == Tail.ANY,
                  base: AccessPathBase = x) = Conclusion(PathFact(base, path, tail, mark), exclusion, demand)
    private fun cleaner(path: List<Int>, reach: CleanReach, mark: TaintMark? = t,
                        base: AccessPathBase = x) = Cleaner(base, manager.path(path), reach, mark)
    private fun assertClean(expected: List<Conclusion>, request: TaintMark?, cl: Cleaner, fact: Conclusion) {
        val result = cleanRes(cl, fact)
        assertEquals(expected, result.facts)
        assertEquals(request, result.request)
    }

    @Test
    fun `AP 4_7 cleaner spatial table includes exclusions and all tails`() {
        data class Row(val fact: Pattern, val exact: Pos, val below: Pos, val atAndBelow: Pos)
        val rows = listOf(
            Row(p(listOf(g), Tail.EXACT), Pos.DISJOINT, Pos.DISJOINT, Pos.DISJOINT),
            Row(p(listOf(f, g), Tail.EXACT), Pos.DISJOINT, Pos.INSIDE, Pos.INSIDE),
            Row(p(listOf(f, g), Tail.ANY), Pos.DISJOINT, Pos.INSIDE, Pos.INSIDE),
            Row(p(listOf(f), Tail.EXACT), Pos.INSIDE, Pos.DISJOINT, Pos.INSIDE),
            Row(p(listOf(f), Tail.STAR, MarkSlot.STAR, ex(g)), Pos.PART, Pos.PART, Pos.INSIDE),
            Row(p(listOf(f), Tail.ANY), Pos.PART, Pos.PART, Pos.INSIDE),
            Row(p(listOf(f), Tail.ANY_TAINT, exclusion = ex(g)), Pos.PART, Pos.PART, Pos.INSIDE),
            Row(p(emptyList(), Tail.EXACT), Pos.DISJOINT, Pos.DISJOINT, Pos.DISJOINT),
            Row(p(emptyList(), Tail.STAR, MarkSlot.STAR, ex(g)), Pos.PART, Pos.PART, Pos.PART),
            Row(p(emptyList(), Tail.STAR, MarkSlot.STAR, ex(f)), Pos.DISJOINT, Pos.DISJOINT, Pos.DISJOINT),
            Row(p(emptyList(), Tail.ANY_TAINT, exclusion = ex(g)), Pos.PART, Pos.PART, Pos.PART),
            Row(p(emptyList(), Tail.ANY_TAINT, exclusion = ex(f)), Pos.DISJOINT, Pos.DISJOINT, Pos.DISJOINT),
            Row(p(emptyList(), Tail.ANY), Pos.PART, Pos.PART, Pos.PART),
        )
        for (row in rows) {
            for ((reach, expected) in listOf(CleanReach.EXACT to row.exact,
                CleanReach.BELOW to row.below, CleanReach.AT_AND_BELOW to row.atAndBelow)) {
                assertEquals(expected, cleanPos(cleaner(listOf(f), reach), row.fact.fact, row.fact.exclusion),
                    "$reach on ${row.fact}")
            }
        }
        for (reach in CleanReach.entries) {
            val fact = p(listOf(f), Tail.EXACT, base = y)
            assertEquals(Pos.DISJOINT, cleanPos(cleaner(listOf(f), reach), fact.fact, fact.exclusion))
        }
    }

    @Test
    fun `F75 selected abstract cleaning precedes path tests and repeats its request candidate`() {
        for (reach in CleanReach.entries) {
            for (excluded in listOf(marks(), marks(u.id), marks(t.id), marks(t.id, u.id))) {
                val fact = c(listOf(g), Tail.STAR, MarkSlot.Star(excluded), ex(h))
                val expected = c(listOf(g), Tail.STAR, MarkSlot.Star(excluded + t), ex(h))
                // Paths f and g are apart. The direct helper supplies a request candidate;
                // the mode-aware caller stores it only in run 1.
                assertClean(listOf(expected), t, cleaner(listOf(f), reach), fact)
                val demand = c(listOf(g), Tail.ANY, MarkSlot.Star(excluded))
                assertClean(listOf(c(listOf(g), Tail.ANY, MarkSlot.Star(excluded + t))), t,
                    cleaner(listOf(f), reach), demand)
            }
        }
    }

    @Test
    fun `AP 4_7 different bases and unselected concrete marks pass`() {
        val abstract = c(listOf(f), Tail.STAR, MarkSlot.STAR, ex(g))
        assertClean(listOf(abstract), null, cleaner(listOf(f), CleanReach.AT_AND_BELOW, base = y), abstract)
        val otherMark = c(listOf(f), Tail.EXACT, mu)
        for (reach in CleanReach.entries) {
            assertClean(listOf(otherMark), null, cleaner(listOf(f), reach), otherMark)
        }
        val exact = c(listOf(f), Tail.EXACT)
        assertClean(emptyList(), null, cleaner(listOf(f), CleanReach.EXACT), exact)
        assertClean(listOf(exact), null, cleaner(listOf(f), CleanReach.BELOW), exact)
    }

    @Test
    fun `AP 4_7 one-accessor must cleaner retains normal exclusion and exact split`() {
        val must = c(emptyList(), Tail.ANY_TAINT, exclusion = ex(g))
        val kept = c(emptyList(), Tail.ANY_TAINT, exclusion = ex(f, g))
        assertClean(listOf(kept), null, cleaner(listOf(f), CleanReach.AT_AND_BELOW), must)
        assertClean(listOf(kept, c(listOf(f), Tail.EXACT)), null,
            cleaner(listOf(f), CleanReach.BELOW), must)
        assertClean(listOf(c(emptyList(), Tail.ANY)), null, cleaner(listOf(f), CleanReach.EXACT), must)
        val excluded = c(emptyList(), Tail.ANY_TAINT, exclusion = ex(f))
        for (reach in CleanReach.entries) {
            assertClean(listOf(excluded), null, cleaner(listOf(f), reach), excluded)
            assertClean(listOf(c(emptyList(), Tail.ANY)), null, cleaner(listOf(f, g), reach), must)
        }
    }

    @Test
    fun `AP 4_7 below at an any root retains exact root in its input layer`() {
        assertClean(listOf(c(emptyList(), Tail.EXACT)), null,
            cleaner(emptyList(), CleanReach.BELOW), c(emptyList(), Tail.ANY_TAINT, exclusion = ex(g)))
        assertClean(listOf(c(emptyList(), Tail.EXACT, demand = true)), null,
            cleaner(emptyList(), CleanReach.BELOW), c(emptyList(), Tail.ANY))
        assertClean(listOf(c(emptyList(), Tail.ANY)), null,
            cleaner(emptyList(), CleanReach.EXACT), c(emptyList(), Tail.ANY_TAINT, exclusion = ex(g)))
    }

    @Test
    fun `AP 4_7 all-mark cleaner drops inside passes disjoint and normalizes partial abstract`() {
        val flow = c(emptyList(), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(g))
        assertClean(emptyList(), null, cleaner(emptyList(), CleanReach.AT_AND_BELOW, null), flow)
        assertClean(listOf(c(emptyList(), Tail.ANY, MarkSlot.Star(marks(u.id)))), null,
            cleaner(listOf(f), CleanReach.EXACT, null), flow)
        val disjoint = c(listOf(g), Tail.STAR, MarkSlot.STAR, ex(h))
        assertClean(listOf(disjoint), null, cleaner(listOf(f), CleanReach.AT_AND_BELOW, null), disjoint)
        assertFails { Cleaner(AccessPathBase.Zero, null, CleanReach.EXACT, t) }
    }

    @Test
    fun `AP 6_5 start table preserves must exclusion and demotes concrete star`() {
        val rows = listOf(
            p(listOf(f), Tail.STAR, MarkSlot.STAR, ex(g)) to c(listOf(f), Tail.STAR, MarkSlot.STAR, ex(g)),
            p(listOf(f), Tail.STAR, mt, ex(g)) to c(listOf(f), Tail.ANY),
            p(listOf(f), Tail.ANY) to c(listOf(f), Tail.ANY),
            p(listOf(f), Tail.ANY, MarkSlot.STAR) to c(listOf(f), Tail.ANY, MarkSlot.STAR),
            p(listOf(f), Tail.ANY_TAINT, exclusion = ex(g)) to c(listOf(f), Tail.ANY_TAINT, exclusion = ex(g)),
            p(listOf(f), Tail.EXACT) to c(listOf(f), Tail.EXACT),
        )
        for ((initial, expected) in rows) assertEquals(expected, startFact(initial))
        assertEquals(Conclusion(manager.zero.toPattern().fact, empty, false), startFact(manager.zero.toPattern()))
    }

    @Test
    fun `AP 4_4 counted cut retains class prefixes and mark exclusions`() {
        val must = c(listOf(cls, f, element, g), Tail.ANY_TAINT, exclusion = ex(h))
        assertSame(must, limit(must, 3))
        assertEquals(c(listOf(cls, f), Tail.ANY), limit(must, 1))
        assertEquals(c(listOf(f), Tail.ANY), limit(c(listOf(f, g), Tail.ANY_TAINT, exclusion = ex(h)), 1))
        val flow = c(listOf(cls, f, cls, g), Tail.STAR, MarkSlot.Star(marks(t.id, u.id)), ex(h))
        assertEquals(c(listOf(cls, f, cls), Tail.ANY, MarkSlot.Star(marks(t.id, u.id))), limit(flow, 1))
        val classesOnly = c(listOf(cls, cls), Tail.EXACT)
        assertSame(classesOnly, limit(classesOnly, 1))
    }

    @Test
    fun `AP 4_9 mark checks use geometry exclusions and restricted mode`() {
        val initial = p(emptyList(), Tail.STAR, MarkSlot.STAR)
        val literal = p(listOf(f), Tail.EXACT)
        assertEquals(CheckResult.Holds, markCheck(initial, c(emptyList(), Tail.ANY_TAINT), literal))
        assertEquals(CheckResult.None, markCheck(initial, c(emptyList(), Tail.ANY_TAINT, exclusion = ex(f)), literal))
        assertEquals(CheckResult.None, markCheck(initial, c(listOf(f), Tail.EXACT, mu), literal))
        assertEquals(CheckResult.None, markCheck(initial, c(listOf(g), Tail.STAR, MarkSlot.STAR), literal))
        val flow = c(emptyList(), Tail.STAR, MarkSlot.Star(marks(u.id)))
        assertEquals(CheckResult.Request(t), markCheck(initial, flow, literal))
        assertEquals(CheckResult.None, markCheck(initial, flow, literal, restricted = true))
        assertEquals(CheckResult.None,
            markCheck(initial, c(emptyList(), Tail.STAR, MarkSlot.Star(marks(t.id))), literal))
        assertFails { markCheck(p(emptyList(), Tail.EXACT), flow, literal) }
    }

    @Test
    fun `AP 4_6 matched taint literal retains its input layer`() {
        val literal = p(listOf(f), Tail.EXACT)
        assertFalse(conjDemand(c(listOf(f), Tail.EXACT), literal))
        assertFalse(conjDemand(c(emptyList(), Tail.ANY_TAINT, exclusion = ex(g)), literal))
        assertTrue(conjDemand(c(listOf(f), Tail.EXACT, demand = true), literal))
        assertTrue(conjDemand(c(emptyList(), Tail.ANY), literal))
    }

    @Test
    fun `AP 6_2 policy forgets location and marks except for zero`() {
        val expected = p(emptyList(), Tail.STAR, MarkSlot.STAR)
        assertEquals(expected, policy(p(listOf(f), Tail.EXACT)))
        assertEquals(expected, policy(p(listOf(f), Tail.ANY_TAINT, exclusion = ex(g))))
        assertEquals(expected, policy(p(listOf(f), Tail.ANY, MarkSlot.Star(marks(t.id)))))
        val zero = manager.zero.toPattern()
        assertSame(zero, policy(zero))
    }

    @Test
    fun `AP 4_5 chain answers use the request path and exact equal-path exception`() {
        val initial = p(listOf(f), Tail.STAR, MarkSlot.STAR, ex(h))
        assertEquals(p(listOf(f), Tail.EXACT), answer(initial, p(listOf(f), Tail.EXACT), t))
        assertEquals(p(listOf(f), Tail.STAR, mt, ex(h)), answer(initial, p(listOf(f, g), Tail.EXACT), t))
        assertEquals(p(listOf(f), Tail.STAR, mu, ex(h)), answer(initial, p(emptyList(), Tail.ANY, mu), u))
        val staticInitial = p(listOf(cls), Tail.STAR, MarkSlot.STAR, base = STATIC)
        assertEquals(p(listOf(cls, f), Tail.EXACT, base = STATIC),
            answer(staticInitial, p(listOf(cls, f), Tail.EXACT, base = STATIC), t))
        assertEquals(p(listOf(cls, f), Tail.ANY, base = STATIC),
            answer(staticInitial, p(listOf(cls, f), Tail.ANY_TAINT, exclusion = ex(g), base = STATIC), t))
        assertEquals(p(listOf(cls), Tail.STAR, mt, base = STATIC),
            answer(staticInitial, p(emptyList(), Tail.ANY, base = STATIC), t))
    }

    @Test
    fun `AP 6_3 abstract emission shares FLOW across concrete and abstract added marks`() {
        val demand = p(listOf(f), Tail.ANY, MarkSlot.STAR)
        val expected = p(listOf(f), Tail.STAR, MarkSlot.STAR)
        for (mark in listOf(mt, mu, MarkSlot.STAR, MarkSlot.Star(marks(t.id)))) {
            assertEquals(expected, emit(demand, p(listOf(f, g), Tail.EXACT, mark)))
        }
        assertNull(emit(demand, p(listOf(g), Tail.EXACT)))
        assertNull(emit(demand, p(listOf(f), Tail.EXACT, base = y)))
        val excluded = p(emptyList(), Tail.STAR, MarkSlot.STAR, ex(f))
        assertNull(emit(excluded, p(listOf(f), Tail.EXACT)))
        assertEquals(excluded, emit(excluded, p(listOf(g), Tail.EXACT)))
    }

    @Test
    fun `AP 6_3 concrete emission checks marks before the annotated tail meet`() {
        val demand = p(listOf(f), Tail.ANY)
        assertNull(emit(demand, p(listOf(f), Tail.EXACT, mu)))
        assertNull(emit(demand, p(listOf(f), Tail.ANY, MarkSlot.STAR)))
        assertNull(emit(demand, p(listOf(f), Tail.ANY, MarkSlot.Star(marks(u.id)))))
        val rows = listOf(
            Triple(Tail.EXACT, Tail.EXACT, Tail.EXACT), Triple(Tail.EXACT, Tail.ANY, Tail.EXACT),
            Triple(Tail.EXACT, Tail.STAR, Tail.EXACT), Triple(Tail.ANY, Tail.EXACT, Tail.EXACT),
            Triple(Tail.ANY, Tail.ANY, Tail.ANY), Triple(Tail.ANY, Tail.STAR, Tail.STAR),
            Triple(Tail.ANY_TAINT, Tail.EXACT, Tail.EXACT), Triple(Tail.ANY_TAINT, Tail.ANY, Tail.ANY_TAINT),
        )
        for ((addedTail, demandTail, outputTail) in rows) {
            val addedE = if (addedTail == Tail.ANY_TAINT) ex(g) else empty
            val demandE = if (demandTail == Tail.STAR) ex(h) else empty
            val outputE = when (outputTail) { Tail.ANY_TAINT -> ex(g); Tail.STAR -> ex(h); else -> empty }
            assertEquals(p(listOf(f), outputTail, exclusion = outputE),
                emit(p(listOf(f), demandTail, exclusion = demandE), p(listOf(f), addedTail, exclusion = addedE)))
        }
        assertFails { emit(p(listOf(f), Tail.STAR, exclusion = ex(h)),
            p(listOf(f), Tail.ANY_TAINT, exclusion = ex(g))) }
    }

    @Test
    fun `AP 6_3 concrete emission above and below reads first-accessor exclusions`() {
        val demand = p(listOf(f), Tail.ANY)
        assertEquals(p(listOf(f), Tail.ANY_TAINT),
            emit(demand, p(emptyList(), Tail.ANY_TAINT, exclusion = ex(g))))
        assertNull(emit(demand, p(emptyList(), Tail.ANY_TAINT, exclusion = ex(f))))
        val below = p(listOf(f, g), Tail.ANY_TAINT, exclusion = ex(h))
        assertSame(below, emit(demand, below))
        assertNull(emit(p(listOf(f), Tail.STAR, exclusion = ex(g)), below))
        assertNull(emit(p(listOf(f), Tail.EXACT), below))
    }

    @Test
    fun `AP 6_4 restriction checks whole premise and both mark gates`() {
        val premise = p(emptyList(), Tail.ANY)
        val result = c(listOf(f), Tail.EXACT, base = y)
        assertNull(restrict(premise, result, DemandPattern(premise, null)))
        assertNull(restrict(premise, result, DemandPattern(p(listOf(f), Tail.EXACT),
            p(listOf(f), Tail.EXACT, base = y))))
        assertNull(restrict(premise, result, DemandPattern(p(emptyList(), Tail.ANY, mu),
            p(listOf(f), Tail.EXACT, base = y))))
        assertNull(restrict(premise, result, DemandPattern(premise, p(listOf(f), Tail.EXACT, mu, base = y))))
        assertNull(restrict(premise, result, DemandPattern(premise, p(listOf(f), Tail.EXACT, base = x))))
        assertEquals(result, restrict(premise, result, DemandPattern(premise,
            p(listOf(f), Tail.EXACT, base = y))))
    }

    @Test
    fun `AP 6_4 same-path reduction implements every tail cell and preserves layer`() {
        val premise = p(emptyList(), Tail.EXACT)
        val rows = listOf(
            c(listOf(f), Tail.EXACT, base = y),
            c(listOf(f), Tail.ANY, base = y),
            c(listOf(f), Tail.ANY_TAINT, exclusion = ex(g), base = y),
            c(listOf(f), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(g), base = y),
        )
        for (result in rows) {
            for (demandTail in listOf(Tail.EXACT, Tail.STAR, Tail.ANY)) {
                val demandE = if (demandTail == Tail.STAR) ex(h) else empty
                val output = when {
                    result.fact.tail == Tail.STAR -> result
                    result.fact.tail == Tail.EXACT -> result
                    demandTail == Tail.EXACT -> c(listOf(f), Tail.EXACT, result.fact.mark,
                        demand = result.demand, base = y)
                    result.fact.tail == Tail.ANY_TAINT && demandTail == Tail.STAR ->
                        c(listOf(f), Tail.ANY_TAINT, exclusion = ex(g, h), base = y)
                    else -> result
                }
                assertEquals(output, restrict(premise, result, DemandPattern(premise,
                    p(listOf(f), demandTail, exclusion = demandE, base = y))))
            }
        }
    }

    @Test
    fun `AP 6_4 above reduction retains must annotations and representability exceptions`() {
        val premise = p(emptyList(), Tail.EXACT)
        fun reduced(result: Conclusion, exit: Pattern) = restrict(premise, result, DemandPattern(premise, exit))
        val must = c(emptyList(), Tail.ANY_TAINT, exclusion = ex(g), base = y)
        assertEquals(c(listOf(f), Tail.EXACT, base = y), reduced(must, p(listOf(f), Tail.EXACT, base = y)))
        assertEquals(c(listOf(f), Tail.ANY_TAINT, exclusion = ex(h), base = y),
            reduced(must, p(listOf(f), Tail.STAR, exclusion = ex(h), base = y)))
        assertEquals(c(listOf(f), Tail.ANY_TAINT, base = y), reduced(must, p(listOf(f), Tail.ANY, base = y)))
        assertNull(reduced(must, p(listOf(g), Tail.EXACT, base = y)))
        val may = c(emptyList(), Tail.ANY, base = y)
        assertEquals(c(listOf(f), Tail.ANY, base = y),
            reduced(may, p(listOf(f), Tail.STAR, exclusion = ex(g), base = y)))
        val flow = c(emptyList(), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(g), base = y)
        assertSame(flow, reduced(flow, p(listOf(f), Tail.EXACT, base = y)))
        assertNull(reduced(flow, p(listOf(g), Tail.EXACT, base = y)))
        assertNull(reduced(c(emptyList(), Tail.EXACT, base = y), p(listOf(f), Tail.EXACT, base = y)))
    }

    @Test
    fun `AP 4_4 limit rejects nonpositive values before reading any path shape`() {
        val facts = listOf(emptyList(), listOf(cls, cls), listOf(f), listOf(cls, f, element, g)).flatMap { path ->
            listOf(
                c(path, Tail.EXACT),
                c(path, Tail.ANY_TAINT, exclusion = ex(h)),
                c(path, Tail.STAR, MarkSlot.Star(marks(u.id)), ex(h)),
                c(path, Tail.ANY),
            )
        } + Conclusion(manager.zero.toPattern().fact, empty, false)
        for (fieldLimit in listOf(0, -1, Int.MIN_VALUE)) {
            for (fact in facts) {
                assertFails("fieldLimit=$fieldLimit on $fact") { limit(fact, fieldLimit) }
            }
        }
    }

    @Test
    fun `AP 6_4 below-exit restriction tests the demanded first accessor and keeps full result`() {
        val premise = p(emptyList(), Tail.EXACT)
        val results = listOf(
            c(listOf(f, g), Tail.EXACT, base = y),
            c(listOf(f, g), Tail.EXACT, demand = true, base = y),
            c(listOf(f, g), Tail.ANY, base = y),
            c(listOf(f, g), Tail.ANY_TAINT, exclusion = ex(h), base = y),
            c(listOf(f, g), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(h), base = y),
        )
        for (result in results) {
            assertEquals(result, restrict(premise, result, DemandPattern(premise,
                p(listOf(f), Tail.STAR, exclusion = ex(h), base = y))))
            assertEquals(result, restrict(premise, result, DemandPattern(premise,
                p(listOf(f), Tail.ANY, base = y))))
            assertNull(restrict(premise, result, DemandPattern(premise,
                p(listOf(f), Tail.STAR, exclusion = ex(g), base = y))))
            assertNull(restrict(premise, result, DemandPattern(premise,
                p(listOf(f), Tail.EXACT, base = y))))
        }
    }

    @Test
    fun `AP 6_4 whole must-premise containment reads inclusion of excluded accessors`() {
        val narrowPremise = p(listOf(f), Tail.ANY_TAINT, exclusion = ex(g, h))
        val widerPremise = p(listOf(f), Tail.ANY_TAINT, exclusion = ex(g))
        val result = c(emptyList(), Tail.EXACT, base = y)
        val exit = p(emptyList(), Tail.EXACT, base = y)
        assertEquals(result, restrict(narrowPremise, result,
            DemandPattern(p(listOf(f), Tail.STAR, exclusion = ex(g)), exit)))
        assertNull(restrict(widerPremise, result,
            DemandPattern(p(listOf(f), Tail.STAR, exclusion = ex(g, h)), exit)))
        // The facts overlap at f itself. Neither exclusion contains the other,
        // so that overlap is not whole-premise containment.
        assertNull(restrict(widerPremise, result,
            DemandPattern(p(listOf(f), Tail.STAR, exclusion = ex(h)), exit)))
        assertEquals(result, restrict(widerPremise, result,
            DemandPattern(p(listOf(f), Tail.ANY), exit)))
        assertNull(restrict(widerPremise, result,
            DemandPattern(p(listOf(f), Tail.EXACT), exit)))
        assertNull(restrict(widerPremise, result,
            DemandPattern(p(emptyList(), Tail.STAR, exclusion = ex(f)), exit)))
    }

    @Test
    fun `AP 6_4 exit-mark table includes concrete abstract and excluded marks`() {
        val premise = p(emptyList(), Tail.EXACT)
        val concreteT = c(listOf(f), Tail.EXACT, mt, base = y)
        val concreteU = c(listOf(f), Tail.EXACT, mu, base = y)
        val abstract = c(listOf(f), Tail.STAR, MarkSlot.STAR, ex(g), base = y)
        val excludedT = c(listOf(f), Tail.STAR, MarkSlot.Star(marks(t.id)), ex(g), base = y)
        val excludedU = c(listOf(f), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(g), base = y)
        data class Row(val exitMark: MarkSlot, val accepted: List<Conclusion>, val rejected: List<Conclusion>)
        val rows = listOf(
            Row(mt, listOf(concreteT, abstract, excludedU), listOf(concreteU, excludedT)),
            Row(mu, listOf(concreteU, abstract, excludedT), listOf(concreteT, excludedU)),
            Row(MarkSlot.STAR, listOf(concreteT, concreteU, abstract, excludedT, excludedU), emptyList()),
            Row(MarkSlot.Star(marks(t.id)), listOf(concreteU, abstract, excludedT, excludedU), listOf(concreteT)),
            Row(MarkSlot.Star(marks(t.id, u.id)), listOf(abstract, excludedT, excludedU), listOf(concreteT, concreteU)),
        )
        for (row in rows) {
            val demand = DemandPattern(premise, p(listOf(f), Tail.ANY, row.exitMark, base = y))
            for (result in row.accepted) assertEquals(result, restrict(premise, result, demand))
            for (result in row.rejected) assertNull(restrict(premise, result, demand))
        }
    }

    @Test
    fun `AP 4_7 MAY root cleaners keep EXACT coarse and remove at-and-below`() {
        val may = c(emptyList(), Tail.ANY)
        val abstractMay = c(emptyList(), Tail.ANY, MarkSlot.Star(marks(u.id)))
        assertClean(listOf(may), null, cleaner(emptyList(), CleanReach.EXACT), may)
        assertClean(emptyList(), null, cleaner(emptyList(), CleanReach.AT_AND_BELOW), may)
        assertClean(listOf(abstractMay), null, cleaner(emptyList(), CleanReach.EXACT, null), abstractMay)
        assertClean(emptyList(), null, cleaner(emptyList(), CleanReach.AT_AND_BELOW, null), abstractMay)
    }

    @Test
    fun `AP 4_7 partial all-mark cleaning of abstract ANY keeps its mark and DEMAND layer`() {
        val may = c(emptyList(), Tail.ANY, MarkSlot.Star(marks(t.id, u.id)))
        for (reach in CleanReach.entries) {
            assertClean(listOf(may), null, cleaner(listOf(f), reach, null), may)
        }
        assertClean(listOf(may), null,
            cleaner(emptyList(), CleanReach.BELOW, null), may)
    }

    @Test
    fun `AP 6_4 exit marks retain abstract pass-through without narrowing`() {
        val premise = p(emptyList(), Tail.STAR, MarkSlot.STAR)
        val flow = c(listOf(f), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(g), base = y)
        assertSame(flow, restrict(premise, flow, DemandPattern(premise, p(listOf(f), Tail.EXACT, base = y))))
        assertNull(restrict(premise, flow, DemandPattern(premise, p(listOf(f), Tail.EXACT, mu, base = y))))
        assertNull(restrict(premise, flow, DemandPattern(p(emptyList(), Tail.ANY),
            p(listOf(f), Tail.EXACT, base = y))))
        val concrete = c(listOf(f), Tail.EXACT, base = y)
        assertNull(restrict(p(emptyList(), Tail.EXACT), concrete,
            DemandPattern(p(emptyList(), Tail.EXACT), p(listOf(f), Tail.ANY, MarkSlot.Star(marks(t.id)), base = y))))
    }
}
