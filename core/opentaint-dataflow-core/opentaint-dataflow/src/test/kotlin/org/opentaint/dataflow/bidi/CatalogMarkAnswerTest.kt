package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.StaticMarkInterest
import org.opentaint.dataflow.bidi.engine.StaticMarkRole
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Catalog matching is separate from the original class/field answer chain. */
class CatalogMarkAnswerTest {
    private val f = StoreFixtures()
    private val m = f.manager
    private val cls = m.accessors.index(ClassStaticAccessor("C"))
    private val g = f.field("g")
    private val h = f.field("h")
    private val k = f.field("k")
    private val gp = listOf(cls, g)
    private val t = f.mark("T")
    private val u = f.mark("U")
    private val empty = ExclusionSet.Empty
    private val origin = m.initial(STATIC, m.path(gp), Tail.STAR, empty, MarkSlot.STAR)
    private val caller = CallerRef(f.key("caller"),
        m.initial(AccessPathBase.Argument(0), null, Tail.STAR, empty, MarkSlot.STAR),
        Layer.NORMAL, f.inst(1))

    private fun query(path: List<Int> = gp, tail: Tail = Tail.EXACT,
                      exclusion: ExclusionSet = empty): Pattern =
        Pattern(PathFact(STATIC, path, tail, MarkSlot.Concrete(t)), exclusion)

    private fun raw(path: List<Int>, tail: Tail = Tail.EXACT, layer: Layer = Layer.NORMAL,
                    exclusion: ExclusionSet = empty, mark: TaintMark = t): Facts =
        m.factsOf(PathFact(STATIC, path, tail, MarkSlot.Concrete(mark)), exclusion, layer)

    private fun expected(path: List<Int>, tail: Tail): InitialAp =
        m.initial(STATIC, m.path(path), tail, empty, MarkSlot.Concrete(t))

    private fun cursor(input: Facts, literal: Pattern, role: StaticMarkRole = StaticMarkRole.SINK): RequestGroupCursor {
        val interest = StaticMarkInterest(literal, role, RequestMarkSet.of(t))
        return RequestGroupCursor(m, origin, interest.marks, input, caller, interest.pattern)
    }

    private data class Actions(val answers: Set<InitialAp>, val climbs: List<TaintMark>)
    private data class StartRow(val tail: Tail, val layer: Layer, val expectedTail: Tail, val startLayer: Layer)
    private fun drain(cursor: RequestGroupCursor): Actions {
        val answers = LinkedHashSet<InitialAp>()
        val climbs = ArrayList<TaintMark>()
        var steps = 0
        while (!cursor.isComplete) {
            assertTrue(++steps < 100000, "The bounded catalog cursor must terminate")
            assertTrue(cursor.step(1, answers::add, climbs::add) in 0..1)
        }
        return Actions(answers, climbs)
    }

    @Test
    fun `declared roles preserve full supplier paths and separate exact may and must starts`(): Unit {
        val path = gp + listOf(h, k)
        for (role in StaticMarkRole.entries) for ((tail, layer, expectedTail, startLayer) in listOf(
            StartRow(Tail.EXACT, Layer.NORMAL, Tail.EXACT, Layer.NORMAL),
            StartRow(Tail.EXACT, Layer.DEMAND, Tail.EXACT, Layer.NORMAL),
            StartRow(Tail.ANY, Layer.DEMAND, Tail.ANY, Layer.DEMAND),
            StartRow(Tail.ANY_TAINT, Layer.NORMAL, Tail.ANY, Layer.DEMAND),
        )) {
            val supplier = raw(path, tail, layer,
                if (tail == Tail.ANY_TAINT) ExclusionSet.of(k) else empty)
            val selected = drain(cursor(supplier, query(path), role))
            val answer = expected(path, expectedTail)
            assertEquals(setOf(answer), selected.answers, "$role $tail $layer")
            assertTrue(selected.climbs.isEmpty())
            assertEquals(startLayer, f.ops.startFact(answer).layer)
            assertEquals(path, answer.path?.toList())
            assertEquals(empty, answer.exclusion)
        }
    }

    @Test
    fun `exact catalog literals do not admit descendant exact suppliers or a different mark`(): Unit {
        assertTrue(drain(cursor(raw(gp + h), query())).answers.isEmpty())
        assertEquals(setOf(expected(gp + h, Tail.EXACT)),
            drain(cursor(raw(gp + h), query(tail = Tail.ANY))).answers)
        assertTrue(drain(cursor(raw(gp, mark = u), query())).answers.isEmpty())
        assertTrue(drain(cursor(raw(listOf(cls)), query())).answers.isEmpty())
    }

    @Test
    fun `wide ancestors use raw E but answer against the original slot rather than the catalog leaf`(): Unit {
        val wideAtSlot = raw(gp, Tail.ANY_TAINT, exclusion = ExclusionSet.of(k))
        assertEquals(setOf(expected(gp, Tail.ANY)),
            drain(cursor(wideAtSlot, query(gp + h))).answers)
        val ancestor = raw(listOf(cls), Tail.ANY_TAINT, exclusion = ExclusionSet.of(k))
        val chain = expected(gp, Tail.STAR)
        assertEquals(setOf(chain), drain(cursor(ancestor, query(gp + h))).answers)
        assertEquals(Layer.DEMAND, f.ops.startFact(chain).layer)
        assertTrue(drain(cursor(raw(gp, Tail.ANY_TAINT, exclusion = ExclusionSet.of(h)),
            query(gp + h))).answers.isEmpty())
        // An explicit child remains eligible even when the parent's must leaf excludes it.
        val must = raw(gp, Tail.ANY_TAINT, exclusion = ExclusionSet.of(h)) as TaintTree
        val child = raw(gp + h) as TaintTree
        val mixed = m.taintTree(STATIC, Layer.NORMAL,
            m.taintAlg.mergeAdd(must.root, child.root, f.cancellation), must.exclusion)
        assertEquals(setOf(expected(gp + h, Tail.EXACT)), drain(cursor(mixed, query(gp + h))).answers)
    }

    @Test
    fun `query E applies once to STAR and must descendants while ANY ignores it`(): Unit {
        for (tail in listOf(Tail.STAR, Tail.ANY_TAINT)) {
            assertTrue(drain(cursor(raw(gp + h), query(tail = tail,
                exclusion = ExclusionSet.of(h)))).answers.isEmpty())
            val admitted = gp + listOf(k, h)
            assertEquals(setOf(expected(admitted, Tail.EXACT)),
                drain(cursor(raw(admitted), query(tail = tail, exclusion = ExclusionSet.of(h)))).answers)
        }
        assertEquals(setOf(expected(gp + h, Tail.EXACT)),
            drain(cursor(raw(gp + h), query(tail = Tail.ANY, exclusion = ExclusionSet.of(h)))).answers)
        assertEquals(setOf(expected(gp, Tail.EXACT)),
            drain(cursor(raw(gp), query(exclusion = ExclusionSet.of(h)))).answers)
    }

    @Test
    fun `FLOW catalogs preserve raw exclusions layers and mark exclusions before climbing`(): Unit {
        fun flow(path: List<Int>, layer: Layer = Layer.NORMAL, exclusion: ExclusionSet = empty,
                 mx: MarkSet = MarkSet.EMPTY) = m.flowTree(STATIC, layer, exclusion, mx,
            FlowAlgebra.prepend(m, m.path(path), FlowAlgebra.LEAF_NODE))
        assertTrue(drain(cursor(flow(gp + h), query())).climbs.isEmpty())
        assertEquals(listOf(t), drain(cursor(flow(gp + h), query(tail = Tail.ANY))).climbs)
        assertTrue(drain(cursor(flow(gp, exclusion = ExclusionSet.of(h)), query(gp + h))).climbs.isEmpty())
        assertEquals(listOf(t), drain(cursor(flow(gp, layer = Layer.DEMAND), query(gp + h))).climbs)
        assertTrue(drain(cursor(flow(gp, mx = m.markSetOf(t)), query())).climbs.isEmpty())
        assertEquals(listOf(t), drain(cursor(flow(gp, mx = m.markSetOf(u)), query())).climbs)
        assertTrue(drain(cursor(flow(gp + h), query(tail = Tail.STAR,
            exclusion = ExclusionSet.of(h)))).climbs.isEmpty())
        assertEquals(listOf(t), drain(cursor(flow(gp + listOf(k, h)), query(tail = Tail.STAR,
            exclusion = ExclusionSet.of(h)))).climbs)
    }

    @Test
    fun `excluded FLOW child candidates consume the quantum before the first allowed route`(): Unit {
        val excluded = List(128) { f.field("excluded$it") }.sorted()
        val allowed = f.field("allowed-last")
        var root = m.flowAlg.leafNode(FlowLeaf.NONE)
        for (accessor in excluded + allowed) root = FlowAlgebra.mergeAdd(root,
            FlowAlgebra.prepend(m, m.path(gp + accessor), FlowAlgebra.LEAF_NODE), f.cancellation)
        val input = m.flowTree(STATIC, Layer.NORMAL, empty, MarkSet.EMPTY, root)
        val current = cursor(input, query(tail = Tail.STAR, exclusion = ExclusionSet.of(excluded.toIntArray())))
        val climbs = ArrayList<TaintMark>()
        repeat(gp.size + excluded.size) {
            assertEquals(1, current.step(1, { error("FLOW cannot answer a mark") }, climbs::add))
            assertTrue(climbs.isEmpty())
        }
        val remaining = drain(current)
        assertEquals(listOf(t), remaining.climbs)
        assertTrue(remaining.answers.isEmpty())
        assertTrue(current.geometryTests >= excluded.size + gp.size)
    }
}
