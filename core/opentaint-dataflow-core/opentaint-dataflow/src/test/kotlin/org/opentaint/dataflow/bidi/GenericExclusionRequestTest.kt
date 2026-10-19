package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Creator obligations are independent of subsequent ordinary positional admission. */
class GenericExclusionRequestTest {
    private val ap = testApManager(Cancellation())
    private val ops = ApOps(ap)
    private val base = AccessPathBase.Argument(0)
    private val other = AccessPathBase.Argument(1)
    private val f = ap.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = ap.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val t = ap.marks.mark("T")
    private val u = ap.marks.mark("U")
    private val excluded = MarkSet(intArrayOf(t.id, u.id))
    private val origin = ap.initial(base, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
    private val edge = PathEdge(
        PathFact(base, listOf(f), Tail.STAR, MarkSlot.STAR),
        PathFact(other, emptyList(), Tail.STAR, MarkSlot.Star(excluded)),
        ExclusionSet.Empty,
    )

    @Test
    fun `generic excluded targets collect each mark even for disjoint or already excluded FLOW`() {
        for (mx in listOf(MarkSet.EMPTY, excluded)) {
            val input = ap.factsOf(PathFact(base, listOf(g), Tail.STAR, MarkSlot.Star(mx)),
                ExclusionSet.of(f), Layer.NORMAL)
            val out = CollectingOut()
            ops.applyEdge(input, origin, edge, false, ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.FULL), out)
            assertEquals(setOf(t, u), out.markRequests.toSet())
            assertTrue(out.results.isEmpty()) // The side effect does not make the edge applicable.
        }
    }

    @Test
    fun `creator runs before the excluded output and preserves the ordinary mark union`() {
        val input = ap.factsOf(PathFact(base, listOf(f), Tail.STAR, MarkSlot.STAR),
            ExclusionSet.Empty, Layer.NORMAL)
        val order = ArrayList<String>()
        val out = object : ApOut {
            override fun markRequest(mark: TaintMark) { order += "request:${mark.id}" }
            override fun result(f: Facts) {
                assertEquals(MarkExclusions.fromMarks(excluded, ap.marks), (f as FlowTree).markExclusion)
                order += "result"
            }
        }
        ops.applyEdge(input, origin, edge, false, ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.FULL), out)
        assertEquals(listOf("request:${t.id}", "request:${u.id}", "result"), order)
    }

    @Test
    fun `later runs and concrete facts create no generic exclusion requests`() {
        val flow = ap.factsOf(PathFact(base, listOf(f), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty, Layer.NORMAL)
        val concrete = ap.factsOf(PathFact(base, listOf(f), Tail.EXACT, MarkSlot.Concrete(t)),
            ExclusionSet.Empty, Layer.NORMAL)
        val concreteOrigin = ap.initial(base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))
        for ((input, premise, mode) in listOf(
            Triple(flow, origin, ApMode(false, Direction.FORWARD, 1)),
            Triple(concrete, concreteOrigin, ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.FULL)),
        )) {
            val out = CollectingOut()
            ops.applyEdge(input, premise, edge, false, mode, out)
            assertTrue(out.markRequests.isEmpty())
        }
    }

    @Test
    fun `excluded summary targets preserve creator requests on the caller output`() {
        val supplied = ap.factsOf(PathFact(base, emptyList(), Tail.STAR, MarkSlot.STAR),
            ExclusionSet.Empty, Layer.NORMAL)
        val summary = ap.factsOf(PathFact(other, emptyList(), Tail.STAR, MarkSlot.Star(excluded)),
            ExclusionSet.Empty, Layer.NORMAL)
        val out = CollectingOut()
        ops.applySummary(supplied, origin, summary, ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.FULL), out)
        assertEquals(setOf(t, u), out.markRequests.toSet())
        assertEquals(MarkExclusions.fromMarks(excluded, ap.marks), (out.results.single() as FlowTree).markExclusion)
    }
}
