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

/** Select full annotated leaves, using local decoding and the A02 per-path predicates. */
class SummarySelectorTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = m.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = m.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = m.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val element = m.accessors.index(ElementAccessor)
    private val t = m.marks.mark("T")
    private val u = m.marks.mark("U")
    private val empty = ExclusionSet.Empty
    private val forward = ApMode(false, Direction.FORWARD, 4)
    private val modes = listOf(ApMode(true, Direction.FORWARD, 4), forward, ApMode(false, Direction.BACKWARD, 4))
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun p(path: List<Int>, tail: Tail, mark: MarkSlot = MarkSlot.STAR,
                  e: ExclusionSet = empty, base: AccessPathBase = x) = Pattern(PathFact(base, path, tail, mark), e)
    private fun c(path: List<Int>, tail: Tail, mark: TaintMark = t, e: ExclusionSet = empty,
                  layer: Layer = Layer.NORMAL) = Conclusion(PathFact(x, path, tail, MarkSlot.Concrete(mark)), e,
        layer == Layer.DEMAND)
    private class Prefix {
        var flow = false
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
        for (path in paths) at(root, path).flow = true
        return m.flowTree(base, layer, e, mx, build(root, m.flowAlg) { if (it.flow) FlowLeaf.LEAF else FlowLeaf.NONE })
    }
    private fun taint(vararg leaves: Conclusion, e: ExclusionSet = empty,
                      layer: Layer = Layer.NORMAL, base: AccessPathBase = x): TaintTree {
        val root = Prefix()
        for (leaf in leaves) {
            val prefix = at(root, leaf.fact.path)
            val mark = (leaf.fact.mark as MarkSlot.Concrete).mark.id
            if (leaf.fact.tail == Tail.EXACT) prefix.exact += mark else prefix.any += mark
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
    private fun inputs(): List<Facts> = listOf(Reach.NORMAL, Reach.DEMAND) + Layer.entries.flatMap { layer ->
        listOf(flow(emptyList(), listOf(f), listOf(f, g), listOf(g), listOf(cls, f), listOf(element),
            e = ex(g), mx = marks(u.id), layer = layer),
            taint(c(emptyList(), Tail.EXACT), c(emptyList(), Tail.EXACT, u), c(emptyList(), Tail.ANY_TAINT),
                c(listOf(f), Tail.EXACT), c(listOf(f), Tail.ANY_TAINT, u),
                c(listOf(f, g), Tail.EXACT, u), c(listOf(f, g), Tail.ANY_TAINT),
                c(listOf(g), Tail.EXACT, u), c(listOf(g), Tail.ANY_TAINT),
                c(listOf(cls, f), Tail.EXACT), c(listOf(element), Tail.ANY_TAINT, u), e = ex(g), layer = layer))
    }

    @Test
    fun `satisfying pattern and initial overloads select exactly the per-path reference leaves`() {
        val patterns = listOf(p(emptyList(), Tail.STAR), p(listOf(f), Tail.STAR), p(listOf(f, g), Tail.STAR),
            p(listOf(h), Tail.STAR), p(emptyList(), Tail.STAR, e = ex(f)),
            p(emptyList(), Tail.STAR, e = ex(f, g)),
            p(emptyList(), Tail.STAR, MarkSlot.Concrete(t)),
            p(listOf(f), Tail.STAR, MarkSlot.Concrete(t), ex(g)),
            p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t)), p(listOf(f), Tail.EXACT, MarkSlot.Concrete(u)),
            p(listOf(f), Tail.ANY, MarkSlot.Concrete(t)), p(listOf(f), Tail.ANY),
            p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t), base = y), m.zero.toPattern())
        for (input in inputs()) for (pattern in patterns) for (mode in modes) for (record in listOf(false, true)) {
            val expected = decode(input).filter { leaf ->
                val raw = Pattern(leaf.fact, leaf.exclusion)
                if (record) applicable(pattern, raw) || inside(pattern, raw) else satisfies(pattern, raw, mode.restricted)
            }.toSet()
            val selected = ops.satisfying(input, pattern, mode, record)
            assertEquals(expected, decode(selected), "$pattern on ${input.groupKey}, ${mode.direction}, run1=${mode.run1}, record=$record")
            assertEquals(expected, decode(ops.satisfying(input, m.initial(pattern), mode, record)))
            if (expected.isEmpty()) assertNull(selected) else {
                assertEquals(input.layer, requireNotNull(selected).layer)
                assertEquals(input.base, selected.base)
                assertEquals(input.groupKey.kind, selected.groupKey.kind)
            }
        }
    }

    @Test
    fun `canonical flow overlap keeps admitted added E and noncanonical E reads both containment guards`() {
        val input = flow(emptyList(), e = ex(g), mx = marks(u.id))
        assertEquals(decode(input), decode(ops.satisfying(input, p(listOf(f), Tail.STAR), forward)))
        assertNull(ops.satisfying(input, p(listOf(g), Tail.STAR), forward))
        val incomparable = p(emptyList(), Tail.STAR, e = ex(f))
        assertTrue(overlap(incomparable, Pattern(decode(input).single().fact, input.exclusion)))
        assertNull(ops.satisfying(input, incomparable, forward))
        assertNull(ops.satisfying(input, p(emptyList(), Tail.STAR, MarkSlot.Concrete(t)), forward))
        assertNull(ops.satisfying(input, p(emptyList(), Tail.STAR, MarkSlot.Concrete(t)), modes.first(), record = true))
    }

    @Test
    fun `concrete must selection reads input E mark and layer without demotion`() {
        val input = taint(c(emptyList(), Tail.ANY_TAINT), c(emptyList(), Tail.ANY_TAINT, u),
            c(listOf(f), Tail.ANY_TAINT), c(listOf(g), Tail.ANY_TAINT), e = ex(g))
        for (pattern in listOf(p(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t), ex(g)),
            p(listOf(f), Tail.ANY_TAINT, MarkSlot.Concrete(t)),
            p(listOf(g), Tail.ANY_TAINT, MarkSlot.Concrete(t)))) {
            for (record in listOf(false, true)) {
                val expected = decode(input).filter {
                    val raw = Pattern(it.fact, it.exclusion)
                    if (record) applicable(pattern, raw) || inside(pattern, raw) else satisfies(pattern, raw, true)
                }.toSet()
                assertEquals(expected, decode(ops.satisfying(input, m.initial(pattern), forward, record)))
            }
        }
        for (mode in listOf(modes.first(), modes.last())) for (record in listOf(false, true)) {
            assertFails { ops.satisfying(input, p(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t)), mode, record) }
        }
    }

    @Test
    fun `leaves and leavesNear match a local decoder and raw overlap on every kind and layer`() {
        val paths = listOf(emptyList(), listOf(f), listOf(f, g), listOf(f, g, g), listOf(g), listOf(h), listOf(cls), listOf(element))
        for (input in inputs()) {
            val raw = decode(input).map { Pattern(it.fact, it.exclusion) }.toSet()
            assertEquals(raw, ops.leaves(input).toSet())
            assertEquals(raw.size, ops.leaves(input).count())
            for (path in paths) {
                val query = p(path, Tail.STAR, base = input.base)
                val expected = raw.filter { overlap(it, query) }.toSet()
                assertEquals(expected, ops.leavesNear(input, path.toIntArray()).toSet(), "$path on ${input.groupKey}")
            }
        }
    }

    @Test
    fun `must record split subtracts applicable-only leaves while preserving raw normal E`() {
        val input = taint(c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.ANY_TAINT),
            c(listOf(g), Tail.ANY_TAINT), c(listOf(f), Tail.EXACT), e = ex(g))
        val premise = m.initial(p(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t), ex(g)))
        val selected = requireNotNull(ops.satisfying(input, premise, forward, record = true))
        val inside = requireNotNull(ops.satisfying(selected, premise, forward))
        val only = requireNotNull(ops.without(selected, inside))
        assertEquals(setOf(c(emptyList(), Tail.ANY_TAINT, e = ex(g))), decode(inside))
        assertEquals(setOf(c(listOf(f), Tail.ANY_TAINT, e = ex(g))), decode(only))
        assertEquals(decode(selected), decode(inside) + decode(only))
        assertEquals(Layer.NORMAL, only.layer)
        assertEquals(ex(g), (only as TaintTree).exclusion)
        assertNull(ops.without(selected, selected))
    }

    @Test
    fun `without removes exact leaves independent of must E and clears E after the last must leaf`() {
        val input = taint(c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.EXACT), c(listOf(g), Tail.EXACT, u), e = ex(g))
        val exactPart = taint(c(listOf(f), Tail.EXACT))
        val anyPart = taint(c(emptyList(), Tail.ANY_TAINT), e = ex(g))
        assertEquals(setOf(c(emptyList(), Tail.ANY_TAINT, e = ex(g)), c(listOf(g), Tail.EXACT, u)),
            decode(ops.without(input, exactPart)))
        val exactOnly = ops.without(input, anyPart) as TaintTree
        assertFalse(exactOnly.root.hasAny)
        assertEquals(empty, exactOnly.exclusion)
        assertEquals(setOf(c(listOf(f), Tail.EXACT), c(listOf(g), Tail.EXACT, u)), decode(exactOnly))
    }

    @Test
    fun `without retains values of another base layer kind or applicable annotation key`() {
        val input = taint(c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.EXACT), e = ex(g))
        for (part in listOf<Facts>(Reach.NORMAL, flow(emptyList()),
            taint(c(listOf(f), Tail.EXACT), base = y),
            taint(c(listOf(f), Tail.EXACT), layer = Layer.DEMAND),
            taint(c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.EXACT), e = ex(f)))) {
            assertSame(input, ops.without(input, part))
        }
        val flags = flow(emptyList(), listOf(f), e = ex(g), mx = marks(u.id))
        assertSame(flags, ops.without(flags, flow(listOf(f), e = ex(f), mx = marks(u.id))))
        assertSame(flags, ops.without(flags, flow(listOf(f), e = ex(g), mx = marks(t.id))))
        assertEquals(decode(flow(emptyList(), e = ex(g), mx = marks(u.id))),
            decode(ops.without(flags, flow(listOf(f), e = ex(g), mx = marks(u.id)))))
        assertNull(ops.without(Reach.NORMAL, Reach.NORMAL))
        assertSame(Reach.NORMAL, ops.without(Reach.NORMAL, Reach.DEMAND))
    }
}
