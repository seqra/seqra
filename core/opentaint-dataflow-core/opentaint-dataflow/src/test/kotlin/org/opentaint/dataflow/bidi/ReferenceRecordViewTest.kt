package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceRecordViewTest {
    private val manager = testApManager(Cancellation())
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val empty = ExclusionSet.Empty

    private fun ex(vararg values: Int) = ExclusionSet.of(values)
    private fun p(base: AccessPathBase, path: List<Int>, tail: Tail, mark: MarkSlot,
                  exclusion: ExclusionSet = empty) = Pattern(PathFact(base, path, tail, mark), exclusion)
    private fun c(base: AccessPathBase, path: List<Int>, tail: Tail, mark: MarkSlot,
                  exclusion: ExclusionSet = empty, demand: Boolean = tail == Tail.ANY) =
        Conclusion(PathFact(base, path, tail, mark), exclusion, demand)
    private fun apply(view: RecordLeafView, input: Conclusion): EdgeOutcome =
        concat(input, summaryEdge(view.premise, view.result), edgeDemand = view.result.demand, restricted = true)

    @Test
    fun `generic native may premise reversal gives demand and retains both side marks`(): Unit {
        val source = p(x, listOf(f), Tail.ANY, mu)
        val target = c(y, listOf(g), Tail.EXACT, mt)
        for (direction in Direction.entries) {
            val view = assertNotNull(reverseRecordLeaf(direction, source, target))
            assertEquals(RecordLeafView(p(y, listOf(g), Tail.EXACT, mt),
                c(x, listOf(f), Tail.ANY, mu, demand = true)), view)
            val outcome = apply(view, target)
            assertEquals(EdgeOutcome.Fact(c(x, listOf(f), Tail.ANY, mu, demand = true)), outcome)
            assertFalse(recordLeafIdentity(view))
        }
    }

    @Test
    fun `AP 9_1 generic reversal tail table keeps all admitted cells`() {
        val table = listOf(
            (Tail.STAR to Tail.STAR) to (Tail.STAR to Tail.STAR),
            (Tail.STAR to Tail.EXACT) to (Tail.EXACT to Tail.ANY),
            (Tail.STAR to Tail.ANY) to (Tail.ANY to Tail.ANY),
            (Tail.ANY to Tail.STAR) to (Tail.STAR to Tail.STAR),
            (Tail.ANY to Tail.EXACT) to (Tail.EXACT to Tail.ANY),
            (Tail.ANY to Tail.ANY) to (Tail.ANY to Tail.ANY),
            (Tail.ANY to Tail.ANY_TAINT) to (Tail.ANY to Tail.ANY),
            (Tail.EXACT to Tail.STAR) to (Tail.EXACT to Tail.EXACT),
            (Tail.EXACT to Tail.EXACT) to (Tail.EXACT to Tail.EXACT),
            (Tail.EXACT to Tail.ANY) to (Tail.ANY to Tail.EXACT),
            (Tail.EXACT to Tail.ANY_TAINT) to (Tail.ANY to Tail.EXACT),
        )
        for ((forward, reversed) in table) {
            assertEquals(reversed, revTails(forward.first, forward.second), "reverse $forward")
        }
        assertFails { revTails(Tail.STAR, Tail.ANY_TAINT) }
        for (tail in Tail.entries) assertFails { revTails(Tail.ANY_TAINT, tail) }
    }

    @Test
    fun `AP 9_1 generic reversal retains separate concrete side marks`() {
        val source = p(x, listOf(f), Tail.EXACT, mu)
        val target = c(y, listOf(g), Tail.EXACT, mt)
        val edge = summaryEdge(source, target)
        val reversed = assertNotNull(revEdge(edge))
        assertEquals(PathEdge(target.fact, source.fact, empty), reversed)
        assertEquals(edge, revEdge(reversed))
        val mustSource = p(x, listOf(f), Tail.ANY_TAINT, mu, ex(g))
        assertNull(revEdge(summaryEdge(mustSource, target)))
        assertNull(revEdge(summaryEdge(source, c(y, listOf(g), Tail.ANY_TAINT, mt, ex(h)))))
        val mustTarget = c(y, listOf(g), Tail.ANY_TAINT, mt)
        assertEquals(PathEdge(PathFact(y, listOf(g), Tail.ANY, mt), source.fact, empty),
            revEdge(summaryEdge(source, mustTarget)))
    }

    @Test
    fun `AP 9_1 generic abstract reversal keeps excluded output marks off its premise`() {
        val outputMark = MarkSlot.Star(MarkSet(intArrayOf(t.id)))
        val source = p(x, listOf(f), Tail.STAR, MarkSlot.STAR)
        val target = c(y, listOf(g), Tail.STAR, outputMark, ex(h))
        val reversed = assertNotNull(revEdge(summaryEdge(source, target)))
        assertEquals(PathFact(y, listOf(g), Tail.STAR, MarkSlot.STAR), reversed.from)
        assertEquals(PathFact(x, listOf(f), Tail.STAR, outputMark), reversed.to)
        assertEquals(ex(h), reversed.exclusion)
        assertEquals(empty, reversed.fromExclusion)
        assertEquals(empty, reversed.toExclusion)
    }

    @Test
    fun `F76 exact to must view retains target E and returns distinct upstream mark`() {
        val source = p(x, listOf(f), Tail.EXACT, mu)
        val target = c(y, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val view = assertNotNull(reverseRecordLeaf(Direction.FORWARD, source, target))
        assertEquals(p(y, listOf(g), Tail.STAR, mt, ex(h)), view.premise)
        assertEquals(c(x, listOf(f), Tail.EXACT, mu), view.result)
        assertFalse(recordLeafIdentity(view))
        // The view's STAR has a concrete mark and full E. It is not started.
        val incoming = c(y, listOf(g, f), Tail.EXACT, mt)
        assertEquals(EdgeOutcome.Fact(c(x, listOf(f), Tail.EXACT, mu)), apply(view, incoming))
        assertEquals(EdgeOutcome.None, apply(view, c(y, listOf(g, h), Tail.EXACT, mt)))
        assertEquals(EdgeOutcome.None, apply(view, c(y, listOf(g, f), Tail.EXACT, mu)))
        assertEquals(EdgeOutcome.None, apply(view, c(y, listOf(g, f), Tail.EXACT, MarkSlot.STAR)))
        assertEquals(EdgeOutcome.Fact(c(x, listOf(f), Tail.EXACT, mu, demand = true)),
            apply(view, c(y, listOf(g, f), Tail.EXACT, mt, demand = true)))
    }

    @Test
    fun `F76 must to must view forgets old input E and always returns DEMAND`() {
        val source = p(x, listOf(f), Tail.ANY_TAINT, mu, ex(g))
        val target = c(y, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val view = assertNotNull(reverseRecordLeaf(Direction.FORWARD, source, target))
        assertEquals(p(y, listOf(g), Tail.STAR, mt, ex(h)), view.premise)
        assertEquals(c(x, listOf(f), Tail.ANY, mu), view.result)
        assertEquals(EdgeOutcome.Fact(c(x, listOf(f), Tail.ANY, mu)),
            apply(view, c(y, listOf(g, f), Tail.EXACT, mt)))
        assertEquals(EdgeOutcome.None, apply(view, c(y, listOf(g, h), Tail.EXACT, mt)))
        assertFalse(recordLeafIdentity(view))
    }

    @Test
    fun `F76 must to exact view keeps exact read premise and may upstream result`() {
        val source = p(x, listOf(f), Tail.ANY_TAINT, mu, ex(g))
        val target = c(y, listOf(g), Tail.EXACT, mt)
        val view = assertNotNull(reverseRecordLeaf(Direction.FORWARD, source, target))
        assertEquals(p(y, listOf(g), Tail.EXACT, mt), view.premise)
        assertEquals(c(x, listOf(f), Tail.ANY, mu), view.result)
        assertEquals(EdgeOutcome.Fact(c(x, listOf(f), Tail.ANY, mu)), apply(view, target))
        assertEquals(EdgeOutcome.None, apply(view, c(y, listOf(g, f), Tail.EXACT, mt)))
        assertFalse(recordLeafIdentity(view))
    }

    @Test
    fun `F76 malformed native shapes and backward must fallback are rejected`() {
        val exact = p(x, listOf(f), Tail.EXACT, mu)
        val must = c(y, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val sourceMust = p(x, listOf(f), Tail.ANY_TAINT, mu, ex(g))
        assertNull(reverseRecordLeaf(Direction.FORWARD, exact.copy(exclusion = ex(g)), must))
        assertNull(reverseRecordLeaf(Direction.FORWARD, exact.copy(fact = exact.fact.copy(mark = MarkSlot.STAR)), must))
        assertNull(reverseRecordLeaf(Direction.FORWARD, p(x, listOf(f), Tail.STAR, mu), must))
        assertNull(reverseRecordLeaf(Direction.FORWARD, sourceMust,
            c(y, listOf(g), Tail.STAR, MarkSlot.STAR, ex(h))))
        assertNull(reverseRecordLeaf(Direction.FORWARD, sourceMust, c(y, listOf(g), Tail.ANY, mt)))
        assertNull(reverseRecordLeaf(Direction.BACKWARD, exact, must))
        assertNull(reverseRecordLeaf(Direction.BACKWARD, sourceMust, c(y, listOf(g), Tail.EXACT, mt)))
        assertFails { c(y, listOf(g), Tail.EXACT, mt, ex(h)) }
        assertNull(reverseRecordLeaf(Direction.FORWARD, exact,
            c(y, listOf(g), Tail.ANY_TAINT, mt, ex(h), demand = true)))
    }

    @Test
    fun `AP 8_7 native exact premise with field exclusion cannot use exact fallback`() {
        val malformed = p(x, listOf(f), Tail.EXACT, mu, ex(g))
        assertNull(reverseRecordLeaf(Direction.FORWARD, malformed, c(y, listOf(g), Tail.EXACT, mt)))
    }

    @Test
    fun `F76 native exact premise with field exclusion cannot use empty-E must fallback`() {
        val malformed = p(x, listOf(f), Tail.EXACT, mu, ex(g))
        assertNull(reverseRecordLeaf(Direction.FORWARD, malformed, c(y, listOf(g), Tail.ANY_TAINT, mt)))
    }

    @Test
    fun `W8 backward native record cannot carry empty-E must conclusion`() {
        val exact = p(x, listOf(f), Tail.EXACT, mu)
        assertNull(reverseRecordLeaf(Direction.BACKWARD, exact, c(y, listOf(g), Tail.ANY_TAINT, mt)))
    }

    @Test
    fun `AP 8_7 generic native fallback handles exact and FLOW records separately`() {
        val exact = p(x, listOf(f), Tail.EXACT, mt)
        val exactResult = c(x, listOf(f), Tail.EXACT, mt)
        val identity = assertNotNull(reverseRecordLeaf(Direction.FORWARD, exact, exactResult))
        assertTrue(recordLeafIdentity(identity))
        val flow = p(x, listOf(f), Tail.STAR, MarkSlot.STAR)
        val flowResult = c(x, listOf(f), Tail.STAR, MarkSlot.STAR)
        assertTrue(recordLeafIdentity(assertNotNull(reverseRecordLeaf(Direction.BACKWARD, flow, flowResult))))
        assertNull(reverseRecordLeaf(Direction.FORWARD, exact,
            c(x, listOf(f), Tail.EXACT, mt, demand = true)))
        val zero = manager.zero.toPattern()
        assertFalse(recordLeafIdentity(RecordLeafView(zero, Conclusion(zero.fact, empty, false))))
    }

    @Test
    fun `R1 and F77 route raw summaries by separate structural headers`() {
        val one = manager.initial(p(x, listOf(f), Tail.EXACT, mt))
        val second = manager.initial(p(x, listOf(g), Tail.EXACT, mu))
        val multiple = manager.union(one, second)
        val normal = c(y, listOf(h), Tail.EXACT, mt)
        val demand = c(y, listOf(h), Tail.EXACT, mt, demand = true)
        for (direction in Direction.entries) {
            assertTrue(r1Eligible(one, normal, direction))
            assertFalse(demandSelected(one, normal, direction))
            assertFalse(r1Eligible(one, demand, direction))
            assertTrue(demandSelected(one, demand, direction))
            assertFalse(r1Eligible(multiple, normal, direction))
            assertTrue(demandSelected(multiple, normal, direction))
        }
        assertTrue(r1Eligible(manager.zero, normal, Direction.FORWARD))
        assertFalse(demandSelected(manager.zero, normal, Direction.FORWARD))
        assertFalse(r1Eligible(manager.zero, normal, Direction.BACKWARD))
        assertTrue(demandSelected(manager.zero, normal, Direction.BACKWARD))
    }

    @Test
    fun `F77 native must record is persisted without callee demand despite its DEMAND view`() {
        val source = p(x, listOf(f), Tail.ANY_TAINT, mu, ex(g))
        val native = c(y, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val key = manager.initial(source)
        val view = assertNotNull(reverseRecordLeaf(Direction.FORWARD, source, native))
        assertTrue(view.result.demand)
        assertTrue(r1Eligible(key, native, Direction.FORWARD))
        assertFalse(demandSelected(key, native, Direction.FORWARD))
        // A resulting caller summary uses the view result's own layer.
        assertTrue(demandSelected(manager.initial(p(y, listOf(g), Tail.EXACT, mt)),
            view.result, Direction.BACKWARD))
        val narrowed = restrict(source, native, DemandPattern(p(x, listOf(f), Tail.ANY, mu),
            p(y, listOf(g), Tail.EXACT, mt)))
        assertEquals(c(y, listOf(g), Tail.EXACT, mt), narrowed)
        assertFalse(demandSelected(key, assertNotNull(narrowed), Direction.FORWARD))
        assertEquals(ex(h), native.exclusion)
    }
}
