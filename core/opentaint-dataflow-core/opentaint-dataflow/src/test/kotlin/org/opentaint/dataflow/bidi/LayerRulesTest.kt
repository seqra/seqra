package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Focused operation sequences keep normal and demand facts distinct, with explicit expected leaves. */
class LayerRulesTest {
    private val manager = ApManager(Cancellation())
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val c = manager.accessors.index(ClassStaticAccessor("Box"))
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val v = manager.marks.mark("V")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val run1 = ApMode(true, Direction.FORWARD, Int.MAX_VALUE)
    private val restricted = listOf(ApMode(false, Direction.FORWARD, Int.MAX_VALUE),
        ApMode(false, Direction.BACKWARD, Int.MAX_VALUE))
    private val acceptAll = TypeFilter(FactTypeChecker.AlwaysAcceptFilter)

    private fun p(vararg accessors: Int): List<Int> = accessors.toList()
    private fun ex(vararg accessors: Int): ExclusionSet = ExclusionSet.of(accessors)
    private fun value(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                      demand: Boolean = false, exclusion: ExclusionSet = ExclusionSet.Empty): Conclusion =
        Conclusion(PathFact(x, path, tail, mark), exclusion, demand)
    private fun facts(value: Conclusion): Facts =
        manager.factsOf(value.fact, value.exclusion, if (value.demand) Layer.DEMAND else Layer.NORMAL)
    private fun decoded(results: List<Facts>): Set<Conclusion> = results.flatMap { result ->
        ops.leaves(result).map { Conclusion(it.fact, it.exclusion, result.layer == Layer.DEMAND) }.toList()
    }.toSet()
    private fun literal(path: List<Int>, mark: MarkSlot = mt, tail: Tail = Tail.EXACT): Pattern =
        Pattern(PathFact(x, path, tail, mark), ExclusionSet.Empty)
    private fun initial(mark: MarkSlot = mt): InitialAp = manager.initial(x, null,
        if (mark is MarkSlot.Star) Tail.STAR else Tail.EXACT, ExclusionSet.Empty, mark)
    private fun cleaned(input: Facts, cl: Cleaner, mode: ApMode = run1): CollectingOut = CollectingOut().also {
        ops.clean(input, initial(if (input is FlowTree) MarkSlot.STAR else mt), cl, mode, it)
    }

    @Test
    fun `selected abstract cleaning preserves normal E until a counted field cut demotes it`() {
        val original = value(p(c, f, g), Tail.STAR, MarkSlot.Star(MarkSet(intArrayOf(u.id))), exclusion = ex(h))
        val input = assertIs<FlowTree>(facts(original))
        val cl = Cleaner(x, manager.path(p(h)), CleanReach.EXACT, t)
        val clean = cleaned(input, cl)
        val expectedClean = cleanRes(cl, original).facts.single()
        assertEquals(setOf(expectedClean), decoded(clean.results))
        assertEquals(listOf(t), clean.markRequests)
        val normal = assertIs<FlowTree>(clean.results.single())
        assertSame(input.root, normal.root)
        assertEquals(Layer.NORMAL, normal.layer)
        assertEquals(ex(h), normal.exclusion)
        val cut = CollectingOut()
        ops.limit(normal, 1, cut)
        assertEquals(setOf(limit(expectedClean, 1)), decoded(cut.results))
        val demand = assertIs<FlowTree>(cut.results.single())
        assertEquals(Layer.DEMAND, demand.layer)
        assertEquals(ExclusionSet.Empty, demand.exclusion)
        assertEquals(normal.markExclusion, demand.markExclusion)
        assertSame(demand, ops.filter(demand, acceptAll))
        assertIs<MarkCheck.None>(ops.checkMark(demand, literal(p(c, f)), run1))
        assertIs<MarkCheck.None>(ops.checkMark(demand, literal(p(c, f), mu), run1))
        val uncleaned = literal(p(c, f), MarkSlot.Concrete(v))
        assertEquals(v, assertIs<MarkCheck.Request>(ops.checkMark(demand, uncleaned, run1)).mark)
        for (mode in restricted) assertIs<MarkCheck.None>(ops.checkMark(demand, uncleaned, mode))
        assertEquals(setOf(original), decoded(listOf(input)))
    }

    @Test
    fun `one-step must cleaner produces normal facts and each accepted literal keeps that layer`() {
        val original = value(p(), Tail.ANY_TAINT, exclusion = ex(h))
        val cl = Cleaner(x, manager.path(p(f)), CleanReach.BELOW, t)
        val clean = cleaned(facts(original), cl)
        assertEquals(cleanRes(cl, original).facts.toSet(), decoded(clean.results))
        assertEquals(setOf(value(p(), Tail.ANY_TAINT, exclusion = ex(f, h)), value(p(f), Tail.EXACT)), decoded(clean.results))
        for (result in clean.results) {
            assertEquals(Layer.NORMAL, result.layer)
            assertSame(result, ops.filter(result, acceptAll))
            val query = if ((result as TaintTree).root.hasAny) literal(p(g)) else literal(p(f))
            val holds = assertIs<MarkCheck.Holds>(ops.checkMark(result, query, run1))
            assertEquals(Layer.NORMAL, holds.facts.layer)
            assertEquals(decoded(listOf(result)), decoded(listOf(holds.facts)))
        }
        assertTrue(clean.markRequests.isEmpty())
    }

    @Test
    fun `other partial must cleaning loses E once and accepted demand literal stays demand`() {
        val original = value(p(f), Tail.ANY_TAINT, exclusion = ex(h))
        val cl = Cleaner(x, manager.path(p(f, g)), CleanReach.EXACT, t)
        val clean = cleaned(facts(original), cl)
        assertEquals(cleanRes(cl, original).facts.toSet(), decoded(clean.results))
        assertEquals(setOf(value(p(f), Tail.ANY, demand = true)), decoded(clean.results))
        val demand = assertIs<TaintTree>(clean.results.single())
        assertEquals(ExclusionSet.Empty, demand.exclusion)
        for (mode in listOf(run1) + restricted) {
            val holds = assertIs<MarkCheck.Holds>(ops.checkMark(demand, literal(p(f, h)), mode))
            assertEquals(Layer.DEMAND, holds.facts.layer)
            assertEquals(ExclusionSet.Empty, assertIs<TaintTree>(holds.facts).exclusion)
            assertEquals(decoded(listOf(demand)), decoded(listOf(holds.facts)))
        }
        assertSame(demand, ops.filter(demand, acceptAll))
    }

    @Test
    fun `below cleaner at an any leaf preserves its input layer on the remaining exact location`() {
        for (demand in listOf(false, true)) {
            val original = value(p(f), if (demand) Tail.ANY else Tail.ANY_TAINT, demand = demand,
                exclusion = if (demand) ExclusionSet.Empty else ex(h))
            val cl = Cleaner(x, manager.path(p(f)), CleanReach.BELOW, t)
            val clean = cleaned(facts(original), cl)
            val expected = value(p(f), Tail.EXACT, demand = demand)
            assertEquals(setOf(expected), decoded(clean.results))
            assertEquals(cleanRes(cl, original).facts.toSet(), decoded(clean.results))
            val result = clean.results.single()
            val holds = assertIs<MarkCheck.Holds>(ops.checkMark(result, literal(p(f)), run1))
            assertEquals(if (demand) Layer.DEMAND else Layer.NORMAL, holds.facts.layer)
            assertEquals(setOf(expected), decoded(listOf(holds.facts)))
        }
        val original = value(p(f), Tail.STAR, MarkSlot.STAR, exclusion = ex(h))
        val cl = Cleaner(x, manager.path(p(f)), CleanReach.BELOW, null)
        val clean = cleaned(facts(original), cl)
        assertEquals(setOf(value(p(f), Tail.ANY, MarkSlot.STAR, demand = true)), decoded(clean.results))
    }

    @Test
    fun `mark removal preserves the layer and normal E only while any leaves remain`() {
        for (layer in Layer.entries) {
            val anyTail = if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY
            val exclusion = if (layer == Layer.NORMAL) ex(h) else ExclusionSet.Empty
            val exact = facts(value(p(f), Tail.EXACT, mu, layer == Layer.DEMAND)) as TaintTree
            val any = facts(value(p(g), anyTail, mt, layer == Layer.DEMAND, exclusion)) as TaintTree
            val root = manager.taintAlg.mergeAdd(exact.root, any.root)
            val input = manager.taintTree(x, layer, root, exclusion)
            val kept = assertIs<TaintTree>(assertNotNull(ops.withoutMarks(input, MarkSet(intArrayOf(t.id)))))
            assertEquals(layer, kept.layer)
            assertEquals(ExclusionSet.Empty, kept.exclusion)
            assertEquals(setOf(value(p(f), Tail.EXACT, mu, layer == Layer.DEMAND)), decoded(listOf(kept)))
            val onlyAny = assertIs<TaintTree>(assertNotNull(ops.withoutMarks(input, MarkSet(intArrayOf(u.id)))))
            assertEquals(layer, onlyAny.layer)
            assertEquals(exclusion, onlyAny.exclusion)
            assertEquals(setOf(value(p(g), anyTail, mt, layer == Layer.DEMAND, exclusion)), decoded(listOf(onlyAny)))
        }
    }

    @Test
    fun `targets and cuts normalize may must exact and zero without changing accepted literal layers`() {
        for (layer in Layer.entries) for (tail in listOf(Tail.EXACT, Tail.ANY, Tail.ANY_TAINT)) {
            val target = PathFact(x, p(c, f, g), tail, mt)
            val result = ops.targetTree(target, layer)
            val expected = normalize(target, ExclusionSet.Empty, layer == Layer.DEMAND)
            assertEquals(setOf(expected), decoded(listOf(result)))
            val cut = CollectingOut()
            ops.limit(result, 1, cut)
            assertEquals(setOf(limit(expected, 1)), decoded(cut.results))
            val holds = assertIs<MarkCheck.Holds>(ops.checkMark(cut.results.single(), literal(p(c, f, h)), run1))
            assertEquals(Layer.DEMAND, holds.facts.layer)
            assertEquals(ExclusionSet.Empty, assertIs<TaintTree>(holds.facts).exclusion)
        }
        for (layer in Layer.entries) {
            val zero = ops.targetTree(PathFact(AccessPathBase.Zero, p(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO)), layer)
            assertEquals(layer, assertIs<Reach>(zero).layer)
            val cut = CollectingOut()
            ops.limit(zero, 1, cut)
            assertSame(zero, cut.results.single())
        }
    }
}
