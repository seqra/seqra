package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Expected request decisions use stated original and checked paths, not the production gate. */
/** Full-mark primitive/reference regression; selected GROUP behavior has separate differential controls. */
class OrdinaryStaticRequestGateTest {
    private val cancellation = Cancellation()
    private val m = ApManager(cancellation)
    private val ops = ApOps(m)
    private val run1 = ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.FULL)
    private val cls = m.accessors.index(ClassStaticAccessor("C"))
    private val g = m.accessors.index(FieldAccessor("C", "g", "Box"))
    private val f = m.accessors.index(FieldAccessor("C", "f", "Box"))
    private val h = m.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val gp = listOf(cls, g)
    private val fp = listOf(cls, f)
    private val t = m.marks.mark("T")
    private val u = m.marks.mark("U")
    private val empty = ExclusionSet.Empty

    private fun initial(path: List<Int> = gp, base: AccessPathBase = STATIC): InitialAp =
        m.initial(base, m.path(path), Tail.STAR, empty, MarkSlot.STAR)

    private fun flow(vararg paths: List<Int>, base: AccessPathBase = STATIC,
                     layer: Layer = Layer.NORMAL, exclusion: ExclusionSet = empty,
                     mx: MarkSet = MarkSet.EMPTY): FlowTree {
        var root = m.flowAlg.leafNode(FlowLeaf.NONE)
        for (path in paths) root = FlowAlgebra.mergeAdd(root,
            FlowAlgebra.prepend(m, m.path(path), FlowAlgebra.LEAF_NODE), cancellation)
        return m.flowTree(base, layer, exclusion, mx, root)
    }

    private fun literal(path: List<Int> = gp, tail: Tail = Tail.EXACT,
                        base: AccessPathBase = STATIC): Pattern =
        Pattern(PathFact(base, path, tail, MarkSlot.Concrete(t)), empty)

    private fun edge(path: List<Int> = gp, tail: Tail = Tail.EXACT,
                     exclusion: ExclusionSet = empty, base: AccessPathBase = STATIC): PathEdge =
        PathEdge(PathFact(base, path, tail, MarkSlot.Concrete(t)),
            PathFact(AccessPathBase.Return, emptyList(), Tail.EXACT, MarkSlot.Concrete(u)), exclusion)

    private fun apply(input: Facts, origin: InitialAp, rule: PathEdge,
                      enabled: Boolean = true): CollectingOut = CollectingOut().also {
        ops.applyEdge(input, origin, rule, statementEdge = false, run1, it,
            unchangedStaticInitial = origin.takeIf { enabled })
    }

    private fun request(checked: MarkCheck) {
        assertTrue(checked is MarkCheck.Request)
        assertEquals(t, checked.mark)
    }

    @Test
    fun `catalog gate suppresses only the unchanged static check and never supplies a mark`(): Unit {
        val origin = initial()
        val input = ops.startFact(origin)
        assertSame(MarkCheck.None, ops.checkMark(input, literal(), run1, origin))
        request(ops.checkMark(input, literal(), run1)) // No installed catalog: old scalar policy.
        val selected = apply(input, origin, edge())
        assertTrue(selected.markRequests.isEmpty())
        assertTrue(selected.results.isEmpty()) // Different source action target is not a changed input.
        assertEquals(listOf(t), apply(input, origin, edge(), enabled = false).markRequests)
    }

    @Test
    fun `static copies and local checks retain the request on the original initial`(): Unit {
        val origin = initial()
        request(ops.checkMark(flow(fp), literal(fp), run1, origin))
        val local = AccessPathBase.LocalVar(0)
        request(ops.checkMark(flow(emptyList(), base = local), literal(emptyList(), base = local), run1, origin))
        assertEquals(listOf(t), apply(flow(fp), origin, edge(fp)).markRequests)
        assertEquals(listOf(t), apply(flow(emptyList(), base = local), origin,
            edge(emptyList(), base = local)).markRequests)
        val nonstatic = initial(emptyList(), AccessPathBase.Argument(0))
        request(ops.checkMark(ops.startFact(nonstatic),
            literal(emptyList(), base = nonstatic.base), run1, nonstatic))
    }

    @Test
    fun `coverage by the static root and changes to E mx or layer do not count as equality`(): Unit {
        val root = initial(emptyList())
        request(ops.checkMark(flow(gp), literal(), run1, root))
        val origin = initial()
        request(ops.checkMark(flow(gp, exclusion = ExclusionSet.of(h)), literal(), run1, origin))
        request(ops.checkMark(flow(gp, mx = m.markSetOf(u)), literal(), run1, origin))
        request(ops.checkMark(flow(gp, layer = Layer.DEMAND), literal(), run1, origin))
        assertEquals(listOf(t), apply(flow(gp, layer = Layer.DEMAND), origin, edge()).markRequests)
    }

    @Test
    fun `a packed group keeps a request only when a changed leaf is eligible for that literal`(): Unit {
        val origin = initial()
        val siblings = flow(gp, fp)
        assertSame(MarkCheck.None, ops.checkMark(siblings, literal(), run1, origin))
        request(ops.checkMark(siblings, literal(fp), run1, origin))
        request(ops.checkMark(siblings, literal(emptyList(), Tail.ANY), run1, origin))
        val descendants = flow(gp, gp + h)
        assertSame(MarkCheck.None, ops.checkMark(descendants, literal(), run1, origin))
        request(ops.checkMark(descendants, literal(gp, Tail.ANY), run1, origin))
        // One eligible leaf below the literal can itself be the unchanged initial.
        assertSame(MarkCheck.None, ops.checkMark(flow(gp), literal(listOf(cls), Tail.ANY), run1, origin))
    }

    @Test
    fun `geometry and excluded marks are checked before changed input can request`(): Unit {
        val origin = initial()
        assertSame(MarkCheck.None, ops.checkMark(flow(fp), literal(), run1, origin))
        assertSame(MarkCheck.None, ops.checkMark(flow(listOf(cls), exclusion = ExclusionSet.of(g)),
            literal(), run1, origin))
        assertSame(MarkCheck.None, ops.checkMark(flow(fp, mx = m.markSetOf(t)),
            literal(fp), run1, origin))
        request(ops.checkMark(flow(listOf(cls), gp, exclusion = ExclusionSet.of(g)), literal(), run1, origin))
    }

    @Test
    fun `unary source gates use only matched children and do not compare action targets`(): Unit {
        val origin = initial()
        val input = flow(gp, gp + h)
        assertTrue(apply(input, origin, edge()).markRequests.isEmpty()) // EXACT input ignores the child.
        assertEquals(listOf(t), apply(input, origin, edge(tail = Tail.STAR)).markRequests)
        assertTrue(apply(input, origin, edge(tail = Tail.STAR,
            exclusion = ExclusionSet.of(h))).markRequests.isEmpty()) // The edge rejects that child.
        assertTrue(apply(flow(gp), origin, edge(path = listOf(cls), tail = Tail.ANY)).markRequests.isEmpty())
        assertTrue(apply(input, origin, edge()).results.isEmpty())
        assertEquals(listOf(t), apply(flow(gp, mx = m.markSetOf(u)), origin, edge()).markRequests)
    }

    @Test
    fun `static selected cleaners skip disjoint fields even when the mark is already excluded`(): Unit {
        val origin = initial()
        for (input in listOf(ops.startFact(origin), flow(gp, mx = m.markSetOf(t)))) {
            val out = CollectingOut()
            ops.clean(input, origin, Cleaner(STATIC, m.path(fp), CleanReach.EXACT, t), run1, out)
            assertTrue(out.markRequests.isEmpty())
            val result = out.results.single() as FlowTree
            assertEquals((input as FlowTree).markExclusion, result.markExclusion)
            assertEquals(input.root, result.root)
        }
    }

    @Test
    fun `concrete holds and restricted no-request behavior are unchanged`(): Unit {
        val origin = initial()
        val actual = m.factsOf(PathFact(STATIC, gp, Tail.EXACT, MarkSlot.Concrete(t)), empty, Layer.NORMAL)
        assertTrue(ops.checkMark(actual, literal(), run1, origin) is MarkCheck.Holds)
        for (direction in Direction.entries) {
            val restricted = ApMode(false, direction, 1)
            assertSame(MarkCheck.None, ops.checkMark(flow(fp), literal(fp), restricted, origin))
        }
    }

    @Test
    fun `catalog refinement removes the position event but keeps the static parent no-result branch`(): Unit {
        val origin = initial(emptyList())
        val input = ops.startFact(origin)
        val selected = CollectingOut()
        ops.applyEdge(input, origin, edge(), statementEdge = true, run1, selected,
            unchangedStaticInitial = origin)
        assertTrue(selected.markRequests.isEmpty())
        assertTrue(selected.results.isEmpty())
        val scalar = CollectingOut()
        ops.applyEdge(input, origin, edge(), statementEdge = true, run1, scalar)
        val old = concat(Conclusion(origin.toPattern().fact, empty, false), edge(), staticIdentity = true)
        assertTrue(old is EdgeOutcome.PositionRequest)
        assertEquals(gp, old.path) // Retain the historical oracle's precise position obligation.
        assertTrue(scalar.markRequests.isEmpty())
        assertTrue(scalar.results.isEmpty())
    }

    @Test
    fun `deep unchanged scopes are iterative and both consumer paths check cancellation`(): Unit {
        val path = gp + List(4096) { h }
        val origin = initial(path)
        val input = ops.startFact(origin)
        assertSame(MarkCheck.None, ops.checkMark(input, literal(path), run1, origin))
        assertTrue(apply(input, origin, edge(path)).markRequests.isEmpty())
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { ops.checkMark(input, literal(path), run1, origin) }
        assertFailsWith<Cancellation.Cancelled> { apply(input, origin, edge(path)) }
    }
}
