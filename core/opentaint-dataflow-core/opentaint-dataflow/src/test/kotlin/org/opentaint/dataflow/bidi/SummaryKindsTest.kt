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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Summary outputs are the union of direct concat over every locally decoded leaf pair. */
class SummaryKindsTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val z = AccessPathBase.Argument(1)
    private val f = m.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = m.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = m.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val element = m.accessors.index(ElementAccessor)
    private val t = m.marks.mark("T")
    private val u = m.marks.mark("U")
    private val v = m.marks.mark("Fresh")
    private val empty = ExclusionSet.Empty
    private val run1 = ApMode(true, Direction.FORWARD, 6)
    private val forward = ApMode(false, Direction.FORWARD, 6)
    private val backward = ApMode(false, Direction.BACKWARD, 6)
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun p(path: List<Int>, tail: Tail, mark: MarkSlot = MarkSlot.STAR,
                  e: ExclusionSet = empty, base: AccessPathBase = x) = Pattern(PathFact(base, path, tail, mark), e)
    private fun c(path: List<Int>, tail: Tail, mark: TaintMark = t, e: ExclusionSet = empty,
                  layer: Layer = Layer.NORMAL, base: AccessPathBase = x) = Conclusion(
        PathFact(base, path, tail, MarkSlot.Concrete(mark)), e, layer == Layer.DEMAND)
    private class Prefix {
        var flag = false
        val exact = sortedSetOf<Int>()
        val any = sortedSetOf<Int>()
        val children = sortedMapOf<Int, Prefix>()
    }
    private fun at(root: Prefix, path: List<Int>): Prefix {
        var result = root
        for (accessor in path) result = result.children.getOrPut(accessor) { Prefix() }
        return result
    }
    private fun <P : TrieLeaf> build(root: Prefix, alg: LeafAlgebra<P>, leaf: (Prefix) -> P): TrieNode<P> =
        alg.node(leaf(root), root.children.keys.toIntArray(), root.children.values.map { build(it, alg, leaf) }.toTypedArray())
    private fun flow(vararg paths: List<Int>, e: ExclusionSet = empty, mx: MarkSet = MarkSet.EMPTY,
                     layer: Layer = Layer.NORMAL, base: AccessPathBase = x): FlowTree {
        val root = Prefix()
        for (path in paths) at(root, path).flag = true
        return m.flowTree(base, layer, e, mx, build(root, m.flowAlg) { if (it.flag) FlowLeaf.LEAF else FlowLeaf.NONE })
    }
    private fun taint(vararg leaves: Conclusion, e: ExclusionSet = empty,
                      layer: Layer = Layer.NORMAL, base: AccessPathBase = x): TaintTree {
        val root = Prefix()
        for (leaf in leaves) {
            val prefix = at(root, leaf.fact.path)
            val id = (leaf.fact.mark as MarkSlot.Concrete).mark.id
            if (leaf.fact.tail == Tail.EXACT) prefix.exact += id else prefix.any += id
        }
        return m.taintTree(base, layer, build(root, m.taintAlg) {
            m.taintLeaves(MarkSet(it.exact.toIntArray()), MarkSet(it.any.toIntArray()))
        }, e)
    }
    private fun <P : TrieLeaf> walk(root: TrieNode<P>, action: (List<Int>, P) -> Unit) {
        val pending = ArrayDeque<Pair<List<Int>, TrieNode<P>>>()
        pending.addLast(emptyList<Int>() to root)
        while (pending.isNotEmpty()) {
            val (path, node) = pending.removeLast()
            if (!node.leaf.isEmpty) action(path, node.leaf)
            val accessors = node.accessors
            val children = node.children
            if (accessors != null && children != null) for (i in accessors.indices)
                pending.addLast((path + accessors[i]) to children[i])
        }
    }
    private fun decode(facts: Facts?): Set<Conclusion> = buildSet {
        if (facts == null) return@buildSet
        val demand = facts.layer == Layer.DEMAND
        fun put(path: List<Int>, tail: Tail, mark: MarkSlot, e: ExclusionSet = empty) {
            add(Conclusion(PathFact(facts.base, path, tail, mark), e, demand))
        }
        when (facts) {
            is Reach -> put(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
            is FlowTree -> walk(facts.root) { path, _ ->
                put(path, if (demand) Tail.ANY else Tail.STAR, MarkSlot.Star(facts.markExclusion), facts.exclusion)
            }
            is TaintTree -> walk(facts.root) { path, leaf ->
                for (id in leaf.exact.ids) put(path, Tail.EXACT, MarkSlot.Concrete(TaintMark(id)))
                for (id in leaf.any.ids) put(path, if (demand) Tail.ANY else Tail.ANY_TAINT,
                    MarkSlot.Concrete(TaintMark(id)), facts.exclusion)
            }
        }
    }
    private fun direct(input: Facts, premise: InitialAp, summary: Facts, mode: ApMode): Set<Conclusion> = buildSet {
        for (leaf in decode(input)) for (target in decode(summary)) {
            when (val result = concat(leaf, summaryEdge(premise.toPattern(), target),
                edgeDemand = summary.layer == Layer.DEMAND, restricted = mode.restricted)) {
                is EdgeOutcome.Fact -> add(result.conclusion)
                EdgeOutcome.None -> Unit
                else -> error("A selected summary input must not produce a request: $result")
            }
        }
    }
    private fun apply(input: Facts, pattern: Pattern, summary: Facts, mode: ApMode): CollectingOut {
        val premise = m.initial(pattern)
        val expectedSelection = decode(input).filter { satisfies(pattern, Pattern(it.fact, it.exclusion), mode.restricted) }.toSet()
        val selected = ops.satisfying(input, premise, mode)
        assertEquals(expectedSelection, decode(selected))
        val out = CollectingOut()
        if (selected != null) {
            val expected = direct(selected, premise, summary, mode)
            ops.applySummary(selected, premise, summary, mode, out)
            assertEquals(expected, out.results.flatMap { decode(it) }.toSet())
        } else assertNull(selected)
        val expectedCreators = if (mode.run1 && selected is FlowTree && summary is FlowTree)
            summary.markExclusion.ids.map(::TaintMark).toSet() else emptySet()
        assertEquals(expectedCreators,out.markRequests.toSet(),
            "F78 creator interest is additive; the ordinary fact/layer/E oracle remains unchanged")
        return out
    }

    @Test
    fun `reach input applies reach and concrete source summaries in both layers`() {
        for (inputLayer in Layer.entries) for (summaryLayer in Layer.entries) for (mode in listOf(run1, forward)) {
            val input = Reach.of(inputLayer)
            val reachOut = apply(input, m.zero.toPattern(), Reach.of(summaryLayer), mode)
            assertSame(Reach.of(if (inputLayer == Layer.DEMAND || summaryLayer == Layer.DEMAND) Layer.DEMAND else Layer.NORMAL),
                reachOut.results.single())
            val source = taint(c(emptyList(), Tail.EXACT, u), c(emptyList(), Tail.ANY_TAINT, v),
                c(listOf(g), Tail.EXACT, v), c(listOf(g, f), Tail.ANY_TAINT, u),
                e = ex(h), layer = summaryLayer, base = y)
            val out = apply(input, m.zero.toPattern(), source, mode)
            assertTrue(out.results.all { it is TaintTree && it.base == y })
        }
    }

    @Test
    fun `flow input applies flow summary with complete mark exclusions paths E and layer`() {
        for (inputLayer in Layer.entries) for (summaryLayer in Layer.entries) for (mode in listOf(run1, forward)) {
            val input = flow(emptyList(), listOf(f), listOf(f, g), listOf(g), listOf(element), listOf(cls, f),
                e = ex(g), mx = marks(u.id), layer = inputLayer)
            val summary = flow(emptyList(), listOf(f), listOf(f, g), listOf(element),
                e = ex(f), mx = marks(v.id), layer = summaryLayer, base = y)
            val out = apply(input, p(emptyList(), Tail.STAR), summary, mode)
            assertTrue(out.results.all { it is FlowTree && it.base == y })
            for (result in out.results) assertEquals(marks(u.id, v.id), (result as FlowTree).markExclusion)
        }
    }

    @Test
    fun `taint input applies flow transfer summaries after selection and output mark filtering`() {
        for (inputLayer in Layer.entries) for (summaryLayer in Layer.entries) for (mode in listOf(run1, forward)) {
            val input = taint(c(emptyList(), Tail.ANY_TAINT), c(emptyList(), Tail.ANY_TAINT, u),
                c(listOf(f), Tail.EXACT), c(listOf(f), Tail.EXACT, u), c(listOf(f, g), Tail.ANY_TAINT),
                c(listOf(g), Tail.EXACT, v), e = ex(g), layer = inputLayer)
            val summary = flow(emptyList(), listOf(g), listOf(g, f), e = ex(h), mx = marks(u.id),
                layer = summaryLayer, base = y)
            val out = apply(input, p(listOf(f), Tail.STAR), summary, mode)
            assertTrue(out.results.all { it is TaintTree && it.base == y })
            assertTrue(out.results.flatMap { decode(it) }.none { it.fact.mark == MarkSlot.Concrete(u) })
        }
    }

    @Test
    fun `taint summaries preserve distinct target marks and must E with exact and must premises`() {
        for (inputLayer in Layer.entries) for (summaryLayer in Layer.entries) {
            val input = taint(c(emptyList(), Tail.ANY_TAINT), c(emptyList(), Tail.ANY_TAINT, u),
                c(listOf(f), Tail.EXACT), c(listOf(f, g), Tail.ANY_TAINT), e = ex(g), layer = inputLayer)
            val summary = taint(c(emptyList(), Tail.EXACT, v), c(emptyList(), Tail.ANY_TAINT, u),
                c(listOf(g), Tail.EXACT, u), c(listOf(g, f), Tail.ANY_TAINT, v),
                e = ex(h), layer = summaryLayer, base = y)
            for (mode in listOf(run1, forward)) {
                val out = apply(input, p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t)), summary, mode)
                assertTrue(out.results.all { it is TaintTree && it.base == y })
            }
            apply(input, p(listOf(f), Tail.ANY_TAINT, MarkSlot.Concrete(t), ex(g)), summary, forward)
        }
    }

    @Test
    fun `a backward concrete requirement applies reach summaries without requests`() {
        for (inputLayer in Layer.entries) for (summaryLayer in Layer.entries) {
            val input = if (inputLayer == Layer.NORMAL) taint(c(listOf(f), Tail.EXACT))
                else taint(c(emptyList(), Tail.ANY, layer = Layer.DEMAND), c(listOf(f), Tail.EXACT, layer = Layer.DEMAND),
                    layer = Layer.DEMAND)
            val out = apply(input, p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t)), Reach.of(summaryLayer), backward)
            assertTrue(out.results.isNotEmpty())
            assertTrue(out.results.all { it is Reach })
        }
    }

    @Test
    fun `root and nested summary occurrences graft every input suffix and exact targets drop it`() {
        val input = taint(c(listOf(f), Tail.EXACT))
        val transfer = flow(emptyList(), listOf(f), base = y)
        val flowOut = apply(input, p(emptyList(), Tail.STAR), transfer, run1)
        assertEquals(setOf(c(listOf(f), Tail.EXACT, base = y), c(listOf(f, f), Tail.EXACT, base = y)),
            flowOut.results.flatMap { decode(it) }.toSet())
        val nestedInput = taint(c(listOf(f, g), Tail.EXACT))
        val exact = taint(c(listOf(h), Tail.EXACT, u), c(listOf(h, g), Tail.EXACT, u), base = y)
        val exactOut = apply(nestedInput, p(emptyList(), Tail.STAR, MarkSlot.Concrete(t)), exact, run1)
        assertEquals(setOf(c(listOf(h), Tail.EXACT, u, base = y), c(listOf(h, g), Tail.EXACT, u, base = y)),
            exactOut.results.flatMap { decode(it) }.toSet())
    }

    @Test
    fun `an abstract input cannot apply concrete or reach summary kinds`() {
        val input = flow(emptyList())
        val initial = m.initial(p(emptyList(), Tail.STAR))
        val selected = requireNotNull(ops.satisfying(input, initial, run1))
        assertFails { ops.applySummary(selected, initial, taint(c(emptyList(), Tail.EXACT)), run1, CollectingOut()) }
        assertFails { ops.applySummary(selected, initial, Reach.NORMAL, run1, CollectingOut()) }
    }

    private fun checkCombination(parts: List<Pair<Facts, InitialAp>>, target: TaintTree, mode: ApMode) {
        val joinedDemand = target.layer == Layer.DEMAND || parts.any { it.first.layer == Layer.DEMAND }
        val expected = decode(target).map { normalize(it.fact, it.exclusion, joinedDemand) }.toSet()
        for ((part, initial) in parts) {
            val member = direct(part, initial, target, mode).map { normalize(it.fact, it.exclusion, joinedDemand) }.toSet()
            assertEquals(expected, member, "Every matched member produces the same uncorrelated target")
        }
        val out = CollectingOut()
        ops.applyCombination(parts, target, mode, out)
        assertEquals(expected, out.results.flatMap { decode(it) }.toSet())
        assertEquals(1, out.results.size)
        assertSame(target.root, (out.results.single() as TaintTree).root)
        assertTrue(out.markRequests.isEmpty())
    }

    @Test
    fun `complete concrete combinations join all input layers and normalize target E`() {
        for (mask in 0 until 8) for (targetLayer in Layer.entries) for (mode in listOf(run1, forward)) {
            val members = listOf(Triple(x, f, t), Triple(y, g, u), Triple(z, element, v))
            val parts = members.mapIndexed { index, (base, accessor, mark) ->
                val layer = if (mask and (1 shl index) == 0) Layer.NORMAL else Layer.DEMAND
                val initial = m.initial(p(listOf(accessor), Tail.EXACT, MarkSlot.Concrete(mark), base = base))
                val input = taint(c(listOf(accessor), Tail.EXACT, mark, layer = layer, base = base), layer = layer, base = base)
                requireNotNull(ops.satisfying(input, initial, mode)) to initial
            }
            val target = taint(c(emptyList(), Tail.EXACT, v), c(emptyList(), Tail.ANY_TAINT, u),
                c(listOf(g), Tail.EXACT), c(listOf(g, f), Tail.ANY_TAINT, v), e = ex(h), layer = targetLayer, base = y)
            checkCombination(parts, target, mode)
        }
    }

    @Test
    fun `matched concrete star must and may premises admit the same combination target`() {
        val first = m.initial(p(emptyList(), Tail.STAR, MarkSlot.Concrete(t)))
        val second = m.initial(p(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(u), ex(g), y))
        val third = m.initial(p(emptyList(), Tail.ANY, MarkSlot.Concrete(v), base = z))
        val firstInput = taint(c(emptyList(), Tail.ANY_TAINT))
        val secondInput = taint(c(emptyList(), Tail.ANY_TAINT, u), e = ex(g), base = y)
        val thirdInput = taint(c(emptyList(), Tail.ANY, v, layer = Layer.DEMAND), layer = Layer.DEMAND, base = z)
        val parts = listOf(requireNotNull(ops.satisfying(firstInput, first, forward)) to first,
            requireNotNull(ops.satisfying(secondInput, second, forward)) to second,
            requireNotNull(ops.satisfying(thirdInput, third, forward)) to third)
        val target = taint(c(emptyList(), Tail.EXACT, v), c(listOf(f), Tail.ANY_TAINT, u), e = ex(h), base = y)
        checkCombination(parts, target, forward)
    }

    @Test
    fun `combination guards require at least two concrete taint parts`() {
        val initial = m.initial(p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t)))
        val part: Pair<Facts, InitialAp> = taint(c(listOf(f), Tail.EXACT)) to initial
        val target = taint(c(emptyList(), Tail.EXACT, u), base = y)
        assertFails { ops.applyCombination(emptyList(), target, forward, CollectingOut()) }
        assertFails { ops.applyCombination(listOf(part), target, forward, CollectingOut()) }
        val reach: Pair<Facts, InitialAp> = Reach.NORMAL to m.zero
        val flags: Pair<Facts, InitialAp> = flow(emptyList()) to m.initial(p(emptyList(), Tail.STAR))
        assertFails { ops.applyCombination(listOf(part, reach), target, forward, CollectingOut()) }
        assertFails { ops.applyCombination(listOf(flags, part), target, forward, CollectingOut()) }
    }
}
