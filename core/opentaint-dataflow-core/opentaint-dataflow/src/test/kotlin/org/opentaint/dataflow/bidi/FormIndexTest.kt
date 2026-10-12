package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FormIndexTest {
    private val f = StoreFixtures()
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val z = AccessPathBase.Return

    @Test fun `noncontiguous edges retain relative order and duplicate identity in cached base views`(): Unit {
        val a = MicroEdge.of(starEdge(x, emptyList(), z, emptyList()))
        val b = MicroEdge.of(starEdge(y, emptyList(), x, emptyList()))
        val c = identityEdge(x)
        val d = identityEdge(z)
        val input = arrayListOf(a, b, c, d, a)
        val summary = StatementSummary(setOf(x, y), input, emptyList(), emptyMap())
        input.clear()
        assertEquals(listOf(a, b, c, d, a), summary.edges)
        assertEquals(listOf(a, c, a), summary.edgesOf(x))
        assertEquals(listOf(b), summary.edgesOf(y))
        assertEquals(listOf(d), summary.edgesOf(z))
        assertTrue(summary.edgesOf(AccessPathBase.Exception).isEmpty())
        assertEquals(listOf(z, x), summary.targets.toList())
        for (base in listOf(x, y, z, AccessPathBase.Exception)) {
            val view = summary.edgesOf(base)
            repeat(20) { assertSame(view, summary.edgesOf(base)) }
            if (view.isNotEmpty()) assertFails { (view as MutableList<MicroEdge>).clear() }
        }
        assertSame(a, summary.edgesOf(x)[0])
        assertSame(a, summary.edgesOf(x)[2])
    }

    @Test fun `base index lookup and order cross the small table threshold`(): Unit {
        for (size in listOf(0, 1, 2, 8, 9, 16, 64)) {
            val bases = List(size) { AccessPathBase.Argument(size - it - 1) }
            val edges = bases.map(::identityEdge) + bases.asReversed().map(::identityEdge)
            val summary = StatementSummary(bases.toSet(), edges, emptyList(), emptyMap())
            for (base in bases) {
                assertEquals(edges.filter { it.edge.from.base == base }, summary.edgesOf(base))
                assertSame(summary.edgesOf(base), summary.edgesOf(base))
            }
            assertEquals(bases, summary.targets.toList())
            assertTrue(summary.edgesOf(AccessPathBase.Exception).isEmpty())
        }
    }

    @Test fun `empty and single-base summaries have complete read-only edge views`(): Unit {
        assertTrue(StatementSummary.EMPTY.edgesOf(x).isEmpty())
        for (size in listOf(1, 2, 16)) {
            val edge = identityEdge(x)
            val summary = StatementSummary(setOf(x), List(size) { edge }, emptyList(), emptyMap())
            assertEquals(List(size) { edge }, summary.edgesOf(x))
            assertSame(summary.edges, summary.edgesOf(x))
            assertTrue(summary.edgesOf(y).isEmpty())
        }
        val kill = StatementSummary(setOf(x), emptyList(), emptyList(), emptyMap())
        assertEquals(setOf(x), kill.touched)
        assertTrue(kill.edges.isEmpty())
        assertSame(kill, kill.withIdentities(emptyList()))
    }

    @Test fun `call point directory preserves first point order and noncontiguous duplicate stages`(): Unit {
        val a = CallStage.Edges(CallPoint.ADDED, CallPoint.RETURNED, StageKind.UNRESOLVED, StatementSummary.EMPTY)
        val b = CallStage.Actions(CallPoint.BEFORE, CallPoint.BOUND, emptyList())
        val c = CallStage.Edges(CallPoint.ADDED, CallPoint.REWRITTEN, StageKind.SOURCES, StatementSummary.EMPTY)
        val d = CallStage.Callees(CallPoint.BOUND, CallPoint.RETURNED, listOf(f.method, f.method))
        val stages = arrayListOf<CallStage>(a, b, c, d, a)
        val plan = CallPlan(emptySet(), stages, emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
        stages.clear()
        assertEquals(listOf(CallPoint.ADDED, CallPoint.BEFORE, CallPoint.BOUND), plan.stagesFrom.keys.toList())
        assertEquals(listOf(a, c, a), plan.stagesAt(CallPoint.ADDED))
        assertEquals(listOf(b), plan.stagesAt(CallPoint.BEFORE))
        assertEquals(listOf(d), plan.stagesAt(CallPoint.BOUND))
        assertEquals(listOf(f.method, f.method), d.callees)
        for (point in CallPoint.entries) {
            val view = plan.stagesAt(point)
            repeat(20) { assertSame(view, plan.stagesAt(point)) }
            assertEquals(plan.stagesFrom[point].orEmpty(), view)
            if (view.isNotEmpty()) assertFails { (view as MutableList<CallStage>).clear() }
        }
        assertFails { (plan.stagesFrom as MutableMap<CallPoint, List<CallStage>>).clear() }
        assertSame(a, plan.stagesAt(CallPoint.ADDED)[2])
    }

    @Test fun `call directory handles no stage one stage and every point`(): Unit {
        for (points in listOf(emptyList(), listOf(CallPoint.AFTER), CallPoint.entries.reversed())) {
            val stages = points.map { CallStage.Actions(it, CallPoint.BEFORE, emptyList()) }
            val plan = CallPlan(emptySet(), stages, emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
            assertEquals(points, plan.stagesFrom.keys.toList())
            for (point in CallPoint.entries) assertEquals(stages.filter { it.from == point }, plan.stagesAt(point))
            assertEquals(stages.groupBy { it.from }, plan.stagesFrom)
            assertEquals(stages.groupBy { it.from }.hashCode(), plan.stagesFrom.hashCode())
        }
    }

    @Test fun `owned action lists retain repeated cleaner steps`(): Unit {
        val cleaner = org.opentaint.dataflow.bidi.ap.Cleaner(x, null,
            org.opentaint.dataflow.bidi.ap.CleanReach.EXACT, f.mark("T"))
        val step = CleanStep.Clean(cleaner)
        val input = arrayListOf<CleanStep>(step, step, step)
        val actions = CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, input)
        input.clear()
        assertEquals(3, actions.steps.size)
        assertTrue(actions.steps.all { it === step })
        assertEquals(listOf(step, step, step), actions.steps)
    }

    @Test fun `group key producers execute once per input occurrence`(): Unit {
        for (size in listOf(0, 1, 2, 8, 9, 64)) {
            val input = List(size) { it }
            val visits = IntArray(size)
            val groups = ownedOrdinalGroups(input) { item -> visits[item]++; item % 3 }
            assertEquals(input.groupBy { it % 3 }, groups)
            assertEquals(List(size) { 1 }, visits.toList())
        }
    }
}
