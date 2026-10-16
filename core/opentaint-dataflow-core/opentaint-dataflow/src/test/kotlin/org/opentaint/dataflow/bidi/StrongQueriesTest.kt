package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.StrongQueries
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class StrongQueriesTest {
    @Test fun `actual exact queries admit every base in both strong directions without an old guide`() {
        for (direction in Direction.entries) {
            val f = StoreFixtures()
            val queries = StrongQueries(f.ops, ApMode(false, direction, 2))
            val t = f.mark("T")
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = f.field("f")
            for (base in listOf(AccessPathBase.Argument(0), AccessPathBase.This,
                AccessPathBase.Return, AccessPathBase.Exception, AccessPathBase.LocalVar(0), STATIC)) {
                val path = if (base == STATIC) listOf(cls, field) else listOf(field)
                val actual = f.fact(base, path, Tail.EXACT, MarkSlot.Concrete(t))
                val expected = f.manager.initial(f.pattern(base, path, Tail.EXACT, MarkSlot.Concrete(t)))
                assertEquals(listOf(expected), queries.admit(actual), "$direction $base")
                assertTrue(queries.permits(expected))
                assertTrue(queries.admit(actual).isEmpty(), "Same current query does not restart work")
            }
        }
    }

    @Test fun `FLOW supplies only abstract queries and never fabricates an excluded concrete mark`() {
        val f = StoreFixtures()
        val queries = StrongQueries(f.ops, ApMode(false, Direction.BACKWARD, 1))
        val t = f.mark("T")
        val field = f.field("f")
        val input = f.fact(tail = Tail.STAR, mark = MarkSlot.Star(f.manager.markSetOf(t)),
            exclusion = ExclusionSet.of(field))
        assertFalse(queries.permits(f.manager.zero))
        val initial = queries.admit(input).single()
        assertEquals(Tail.STAR, initial.tail)
        assertEquals(MarkSlot.STAR, initial.mark)
        assertTrue(queries.permits(f.manager.zero), "Genuine computed GEN outcomes may publish")
        val concrete = f.manager.initial(f.pattern(mark = MarkSlot.Concrete(t)))
        assertFalse(queries.permits(concrete))
    }

    @Test fun `field limit cuts current inputs while class accessors do not consume the budget`() {
        val f = StoreFixtures()
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val field = f.field("f")
        val nested = f.field("g")
        val t = f.mark("T")
        val queries = StrongQueries(f.ops, ApMode(false, Direction.BACKWARD, 1))
        val actual = f.fact(STATIC, listOf(cls, field, nested), Tail.EXACT, MarkSlot.Concrete(t))
        val initial = queries.admit(actual).single()
        assertEquals(listOf(cls, field), initial.toPattern().fact.path)
        assertEquals(Tail.ANY, initial.tail)
        assertEquals(MarkSlot.Concrete(t), initial.mark)
        assertEquals(ExclusionSet.Empty, initial.exclusion)
        val root = f.fact(STATIC, tail = Tail.STAR, mark = MarkSlot.STAR)
        val rootInitial = queries.admit(root).single()
        assertEquals(emptyList(), rootInitial.toPattern().fact.path)
        assertEquals(Tail.STAR, rootInitial.tail, "Only F1 forbids a static-root initial")
    }

    @Test fun `forward must input keeps actual E and backward invalid must is rejected before admission`() {
        val f = StoreFixtures()
        val field = f.field("f")
        val t = f.mark("T")
        val e = ExclusionSet.of(field)
        val input = f.fact(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = e)
        val forward = StrongQueries(f.ops, ApMode(false, Direction.FORWARD, 1))
        val initial = forward.admit(input).single()
        assertEquals(Tail.ANY_TAINT, initial.tail)
        assertEquals(e, initial.exclusion)
        val backward = StrongQueries(f.ops, ApMode(false, Direction.BACKWARD, 1))
        assertFailsWith<IllegalStateException> { backward.admit(input) }
        assertFalse(backward.permits(initial))
        assertFalse(backward.permits(f.manager.zero))
    }

    @Test fun `full conjunctive premise requires every actual query and independent owners do not share authorization`() {
        val f = StoreFixtures()
        val mode = ApMode(false, Direction.FORWARD, 1)
        val first = StrongQueries(f.ops, mode)
        val second = StrongQueries(f.ops, mode)
        val t = f.mark("T")
        val leftFact = f.fact(AccessPathBase.Argument(0), mark = MarkSlot.Concrete(t))
        val rightFact = f.fact(AccessPathBase.Argument(1), mark = MarkSlot.Concrete(t))
        val left = first.admit(leftFact).single()
        val right = second.admit(rightFact).single()
        val both = f.manager.premiseOf(listOf(left, right))
        assertTrue(first.permits(left))
        assertFalse(first.permits(both))
        assertFalse(second.permits(both))
        assertEquals(listOf(right), first.admit(rightFact))
        assertTrue(first.permits(both))
        first.clear()
        assertFalse(first.permits(left))
        assertFalse(first.permits(both))
        assertTrue(second.permits(right))
    }

    @Test fun `cancellation does not install a query or source permission`() {
        val f = StoreFixtures()
        val queries = StrongQueries(f.ops, ApMode(false, Direction.FORWARD, 1))
        val input = f.fact()
        f.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { queries.admit(input) }
        f.cancellation.activate()
        assertFalse(queries.permits(f.manager.zero))
        assertEquals(1, queries.admit(input).size)
    }

    @Test fun `concrete guides refine a broad actual input while abstract guides never start FLOW`() {
        val f = StoreFixtures()
        val t = f.mark("T")
        val u = f.mark("U")
        val g = f.field("g")
        val h = f.field("h")
        val actual = f.fact(tail = Tail.ANY, mark = MarkSlot.Concrete(t), layer = Layer.DEMAND)
        val narrow = f.pattern(path = listOf(g, h), mark = MarkSlot.Concrete(t))
        val wrongMark = narrow.copy(fact = narrow.fact.copy(mark = MarkSlot.Concrete(u)))
        val abstractRoot = f.pattern(tail = Tail.STAR, mark = MarkSlot.STAR)
        val queries = StrongQueries(f.ops, ApMode(false, Direction.FORWARD, 2))
        val admitted = queries.admit(actual, sequenceOf(DemandPattern(narrow, null),
            DemandPattern(wrongMark, null), DemandPattern(abstractRoot, null)))
        assertEquals(setOf(f.manager.initial(f.pattern(tail = Tail.ANY, mark = MarkSlot.Concrete(t))),
            f.manager.initial(narrow)), admitted.toSet())
        assertTrue(admitted.all { it.mark == MarkSlot.Concrete(t) })
        assertTrue(admitted.all(queries::permits), "The whole additive batch authorizes computed responses")
        assertTrue(queries.admit(actual, sequenceOf(DemandPattern(narrow, null))).isEmpty())

        val blocked = StrongQueries(f.ops, ApMode(false, Direction.FORWARD, 2))
        val excluded = f.fact(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(g))
        assertFalse(f.manager.initial(narrow) in blocked.admit(excluded, sequenceOf(DemandPattern(narrow, null))),
            "A guide cannot cross an actual input exclusion")
        val flow = f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
        val external = StrongQueries(f.ops, ApMode(false, Direction.FORWARD, 2))
        assertTrue(external.admit(flow, sequenceOf(DemandPattern(narrow, null))).all { it.mark == MarkSlot.STAR },
            "An actual external FLOW can only supply its abstract query, never guide-selected T")
    }

    @Test fun `guide refinement obeys the current limit and ignores an unmatched old exit hint`() {
        val f = StoreFixtures()
        val t = f.mark("T")
        val g = f.field("g")
        val h = f.field("h")
        val actual = f.fact(tail = Tail.ANY, mark = MarkSlot.Concrete(t), layer = Layer.DEMAND)
        val guide = DemandPattern(f.pattern(path = listOf(g, h), mark = MarkSlot.Concrete(t)),
            f.pattern(AccessPathBase.Return, mark = MarkSlot.Concrete(f.mark("unrelated exit"))))
        val queries = StrongQueries(f.ops, ApMode(false, Direction.FORWARD, 1))
        val expected = f.manager.initial(f.pattern(path = listOf(g), tail = Tail.ANY, mark = MarkSlot.Concrete(t)))
        assertContains(queries.admit(actual, sequenceOf(guide)), expected)
        assertTrue(queries.permits(expected), "Current response permission does not reuse the old exit filter")
    }
}
