package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReversalTest {
    private val manager = ApManager(Cancellation())
    private val ops = ApOps(manager)
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

    @Test
    fun `generic reverse follows every AP 9_1 admitted tail row`() {
        val rows = listOf(
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
        for ((forward, reversed) in rows) {
            val edge = PathEdge(PathFact(x, listOf(f), forward.first, mu),
                PathFact(y, listOf(g), forward.second, mt), empty)
            val expected = PathEdge(PathFact(y, listOf(g), reversed.first, mt),
                PathFact(x, listOf(f), reversed.second, mu), empty)
            assertEquals(expected, ops.reverse(edge), forward.toString())
        }
    }

    @Test
    fun `reverse agrees with raw reference for all tail mark and side exclusion cells`() {
        val targets = listOf(MarkSlot.STAR, MarkSlot.Star(MarkSet(intArrayOf(t.id))), mt, mu)
        for (fromTail in Tail.entries) for (toTail in Tail.entries) {
            for (fromMark in listOf(MarkSlot.STAR, mt, mu)) for (toMark in targets) {
                for (shared in listOf(empty, ex(f))) for (fromE in listOf(empty, ex(g))) {
                    for (toE in listOf(empty, ex(h))) {
                        val edge = PathEdge(PathFact(x, listOf(f), fromTail, fromMark),
                            PathFact(y, listOf(g), toTail, toMark), shared, fromE, toE)
                        val expected = runCatching { revEdge(edge) }
                        val actual = runCatching { ops.reverse(edge) }
                        assertEquals(expected.isSuccess, actual.isSuccess, edge.toString())
                        if (expected.isSuccess) assertEquals(expected.getOrNull(), actual.getOrThrow(), edge.toString())
                        else assertEquals(expected.exceptionOrNull()!!::class, actual.exceptionOrNull()!!::class,
                            edge.toString())
                    }
                }
            }
        }
    }

    @Test
    fun `excluded abstract output marks stay on reversed output with retained STAR exclusion`() {
        val excluded = MarkSlot.Star(MarkSet(intArrayOf(t.id)))
        val edge = PathEdge(PathFact(x, listOf(f), Tail.STAR, MarkSlot.STAR),
            PathFact(y, listOf(g), Tail.STAR, excluded), ex(h))
        val reversed = assertNotNull(ops.reverse(edge))
        assertEquals(PathFact(y, listOf(g), Tail.STAR, MarkSlot.STAR), reversed.from)
        assertEquals(PathFact(x, listOf(f), Tail.STAR, excluded), reversed.to)
        assertEquals(ex(h), reversed.exclusion)
        assertEquals(empty, reversed.fromExclusion)
        assertEquals(empty, reversed.toExclusion)
    }

    @Test
    fun `generic must reversal remains separate from F76 native readers`() {
        val exact = Pattern(PathFact(x, listOf(f), Tail.EXACT, mu), empty)
        val must = Conclusion(PathFact(y, listOf(g), Tail.ANY_TAINT, mt), ex(h), false)
        assertNull(ops.reverse(summaryEdge(exact, must)))
        assertNotNull(reverseRecordLeaf(Direction.FORWARD, exact, must))
        val mustPremise = Pattern(exact.fact.copy(tail = Tail.ANY_TAINT), ex(g))
        assertNull(ops.reverse(summaryEdge(mustPremise, must)))
        assertNotNull(reverseRecordLeaf(Direction.FORWARD, mustPremise, must))

        val emptyMust = must.copy(exclusion = empty)
        assertEquals(PathEdge(emptyMust.fact.copy(tail = Tail.ANY), exact.fact, empty),
            ops.reverse(summaryEdge(exact, emptyMust)))
        assertNull(reverseRecordLeaf(Direction.BACKWARD, exact, emptyMust))
    }

    @Test
    fun `concrete to concrete reversal swaps distinct marks without changing exact geometry`() {
        val edge = PathEdge(PathFact(x, listOf(f), Tail.EXACT, mu),
            PathFact(y, listOf(g), Tail.EXACT, mt), empty)
        val reversed = assertNotNull(ops.reverse(edge))
        assertEquals(mt, reversed.from.mark)
        assertEquals(mu, reversed.to.mark)
        assertEquals(edge, ops.reverse(reversed))
        val unsupported = edge.copy(from = edge.from.copy(tail = Tail.STAR, mark = MarkSlot.STAR))
        assertNull(ops.reverse(unsupported))
    }

    private fun ex(vararg ids: Int): ExclusionSet = ExclusionSet.of(ids)
}
