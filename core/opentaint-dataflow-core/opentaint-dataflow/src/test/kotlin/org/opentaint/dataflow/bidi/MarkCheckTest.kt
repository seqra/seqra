package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Concrete exact/any literals are checked against a local decoder and the per-path reference. */
class MarkCheckTest {
    private val cancellation = Cancellation()
    private val m = testApManager(cancellation)
    private val ops = ApOps(m)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = m.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = m.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val freshAccessor = m.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val element = m.accessors.index(ElementAccessor)
    private val t = m.marks.mark("T")
    private val u = m.marks.mark("U")
    private val freshMark = m.marks.mark("Fresh")
    private val empty = ExclusionSet.Empty
    private val run1 = ApMode(true, Direction.FORWARD, 4, markMode = MarkMode.FULL)
    private val forward = ApMode(false, Direction.FORWARD, 4)
    private val backward = ApMode(false, Direction.BACKWARD, 4)
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun p(path: List<Int>, tail: Tail, mark: TaintMark = t, base: AccessPathBase = x) =
        Pattern(PathFact(base, path, tail, MarkSlot.Concrete(mark)), empty)
    private fun c(path: List<Int>, tail: Tail, mark: TaintMark = t) =
        Conclusion(PathFact(x, path, tail, MarkSlot.Concrete(mark)), empty, false)
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
    private fun flow(vararg paths: List<Int>, exclusion: ExclusionSet = empty, mx: MarkSet = MarkSet.EMPTY,
                     layer: Layer = Layer.NORMAL): FlowTree {
        val root = Prefix()
        for (path in paths) at(root, path).flag = true
        return m.flowTree(x, layer, exclusion, mx,
            build(root, m.flowAlg) { if (it.flag) FlowLeaf.LEAF else FlowLeaf.NONE })
    }
    private fun taint(vararg leaves: Conclusion, exclusion: ExclusionSet = empty,
                      layer: Layer = Layer.NORMAL): TaintTree {
        val root = Prefix()
        for (leaf in leaves) {
            val prefix = at(root, leaf.fact.path)
            val id = (leaf.fact.mark as MarkSlot.Concrete).mark.id
            if (leaf.fact.tail == Tail.EXACT) prefix.exact += id else prefix.any += id
        }
        return m.taintTree(x, layer, build(root, m.taintAlg) {
            m.taintLeaves(MarkSet(it.exact.toIntArray()), MarkSet(it.any.toIntArray()))
        }, exclusion)
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
    private fun decode(value: Facts?): Set<Conclusion> = buildSet {
        if (value == null) return@buildSet
        val demand = value.layer == Layer.DEMAND
        fun put(path: List<Int>, tail: Tail, mark: MarkSlot, exclusion: ExclusionSet = empty) {
            add(Conclusion(PathFact(value.base, path, tail, mark), exclusion, demand))
        }
        when (value) {
            is Reach -> put(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
            is FlowTree -> walk(value.root) { path, _ ->
                put(path, value.tail, MarkSlot.Star(value.markExclusion), value.exclusion)
            }
            is TaintTree -> walk(value.root) { path, leaf ->
                for (id in leaf.exact.ids) put(path, Tail.EXACT, MarkSlot.Concrete(TaintMark(id)))
                for (id in leaf.any.ids) put(path, value.anyTail,
                    MarkSlot.Concrete(TaintMark(id)), value.exclusion)
            }
        }
    }
    private fun checkReference(input: Facts, literal: Pattern, mode: ApMode) {
        val premise = if (input is Reach) m.zero.toPattern()
            else Pattern(PathFact(input.base, emptyList(), Tail.STAR, MarkSlot.STAR), empty)
        val held = LinkedHashSet<Conclusion>()
        val requested = LinkedHashSet<TaintMark>()
        for (leaf in decode(input)) when (val answer = markCheck(premise, leaf, literal, mode.restricted)) {
            CheckResult.Holds -> {
                assertEquals(leaf.demand, conjDemand(leaf, literal))
                held += leaf
            }
            is CheckResult.Request -> requested += answer.mark
            CheckResult.None -> Unit
        }
        val actual = ops.checkMark(input, literal, mode)
        when {
            held.isNotEmpty() -> {
                assertTrue(actual is MarkCheck.Holds)
                assertEquals(held, decode(actual.facts), "$literal on ${input.groupKey}")
                assertEquals(input.layer, actual.facts.layer)
                assertEquals(input.base, actual.facts.base)
                assertEquals(input.groupKey.kind, actual.facts.groupKey.kind)
                assertTrue(requested.isEmpty())
            }
            requested.isNotEmpty() -> {
                assertTrue(actual is MarkCheck.Request)
                assertEquals(requested.single(), actual.mark)
            }
            else -> assertSame(MarkCheck.None, actual)
        }
    }

    @Test
    fun `the zero literal holds in both layers and zero targets are canonical`() {
        for (layer in Layer.entries) {
            val zero = Reach.of(layer)
            val hit = ops.checkMark(zero, m.zero.toPattern(), ApMode(true, Direction.FORWARD, 3, markMode = MarkMode.FULL))
            assertTrue(hit is MarkCheck.Holds)
            assertSame(zero, hit.facts)
            assertSame(zero, ops.targetTree(m.zero.toPattern().fact, layer))
            assertSame(zero, ops.withoutMarks(zero, m.markSetOf(TaintMark.ZERO)))
        }
    }

    @Test
    fun `literal checks match full annotated reference leaves in every kind layer and direction`() {
        val paths = listOf(emptyList(), listOf(f), listOf(f, g), listOf(g), listOf(cls, f), listOf(element))
        val raw = paths.flatMap { listOf(c(it, Tail.EXACT), c(it, Tail.EXACT, u),
            c(it, Tail.ANY_TAINT), c(it, Tail.ANY_TAINT, u)) } + c(listOf(freshAccessor), Tail.EXACT, freshMark)
        val inputs = listOf<Facts>(Reach.NORMAL, Reach.DEMAND) + Layer.entries.flatMap { layer ->
            listOf(flow(*paths.toTypedArray(), exclusion = ex(g), mx = marks(u.id), layer = layer),
                taint(*raw.toTypedArray(), exclusion = ex(g), layer = layer))
        }
        val queries = listOf(emptyList(), listOf(f), listOf(f, g), listOf(f, g, g), listOf(g),
            listOf(freshAccessor), listOf(cls), listOf(cls, f), listOf(element))
        val literals = queries.flatMap { path -> listOf(Tail.EXACT, Tail.ANY).flatMap { tail ->
            listOf(t, u, freshMark).map { mark -> p(path, tail, mark) }
        } } + listOf(p(listOf(f), Tail.EXACT, base = y), m.zero.toPattern())
        for (input in inputs) for (literal in literals) for (mode in listOf(run1, forward, backward))
            checkReference(input, literal, mode)
    }

    @Test
    fun `input exclusions and mark exclusions are checked before any flow request`() {
        val input = flow(emptyList(), exclusion = ex(f))
        assertSame(MarkCheck.None, ops.checkMark(input, p(listOf(f), Tail.EXACT), run1))
        assertSame(MarkCheck.None, ops.checkMark(input, p(listOf(f, g), Tail.ANY), run1))
        val requested = ops.checkMark(input, p(listOf(g), Tail.EXACT), run1)
        assertTrue(requested is MarkCheck.Request)
        assertEquals(t, requested.mark)
        val excluded = flow(emptyList(), exclusion = ex(f), mx = marks(t.id))
        assertSame(MarkCheck.None, ops.checkMark(excluded, p(listOf(g), Tail.ANY), run1))
        for (mode in listOf(forward, backward))
            assertSame(MarkCheck.None, ops.checkMark(input, p(listOf(g), Tail.EXACT), mode))
        val explicit = flow(emptyList(), listOf(f), exclusion = ex(f))
        assertTrue(ops.checkMark(explicit, p(listOf(f), Tail.EXACT), run1) is MarkCheck.Request)
        val demand = flow(emptyList(), exclusion = ex(f), layer = Layer.DEMAND)
        assertTrue(ops.checkMark(demand, p(listOf(f), Tail.EXACT), run1) is MarkCheck.Request)
    }

    @Test
    fun `normal must literals read E while explicit exact descendants keep their own marks`() {
        val input = taint(c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.EXACT, u), exclusion = ex(f))
        for (mode in listOf(run1, forward, backward)) {
            assertSame(MarkCheck.None, ops.checkMark(input, p(listOf(f), Tail.EXACT), mode))
            val hit = ops.checkMark(input, p(listOf(f), Tail.EXACT, u), mode)
            assertTrue(hit is MarkCheck.Holds)
            assertEquals(setOf(c(listOf(f), Tail.EXACT, u)), decode(hit.facts))
            assertEquals(Layer.NORMAL, hit.facts.layer)
            assertEquals(empty, (hit.facts as TaintTree).exclusion)
            val any = ops.checkMark(input, p(listOf(g), Tail.ANY), mode)
            assertTrue(any is MarkCheck.Holds)
            assertEquals(setOf(Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t)), ex(f), false)),
                decode(any.facts))
        }
    }

    @Test
    fun `withoutMarks removes both payload tails and clears E only when the last must mark goes`() {
        for (layer in Layer.entries) {
            val input = taint(c(emptyList(), Tail.EXACT), c(emptyList(), Tail.EXACT, u),
                c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.EXACT, u),
                c(listOf(f, g), Tail.ANY_TAINT), c(listOf(cls), Tail.EXACT, freshMark),
                c(listOf(element), Tail.EXACT), exclusion = ex(g), layer = layer)
            val removed = requireNotNull(ops.withoutMarks(input, marks(t.id)))
            assertEquals(decode(input).filter { it.fact.mark != MarkSlot.Concrete(t) }.toSet(), decode(removed))
            assertEquals(layer, removed.layer)
            assertFalse((removed as TaintTree).root.hasAny)
            assertEquals(empty, removed.exclusion)
            assertSame(input, ops.withoutMarks(input, MarkSet.EMPTY))
            assertSame(input, ops.withoutMarks(input, marks(m.marks.mark("Absent").id)))
            assertNull(ops.withoutMarks(input, marks(t.id, u.id, freshMark.id)))
        }
        val mixed = taint(c(emptyList(), Tail.ANY_TAINT), c(emptyList(), Tail.ANY_TAINT, u),
            c(listOf(f), Tail.EXACT), exclusion = ex(g))
        val result = ops.withoutMarks(mixed, marks(t.id)) as TaintTree
        assertEquals(ex(g), result.exclusion)
        assertEquals(setOf(Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(u)), ex(g), false)),
            decode(result))
        val flags = flow(emptyList(), listOf(f), exclusion = ex(g), mx = marks(u.id))
        assertSame(flags, ops.withoutMarks(flags, marks(t.id, u.id)))
    }

    @Test
    fun `concrete targets normalize must may and exact tails in every requested layer`() {
        val paths = listOf(emptyList(), listOf(f, g), listOf(cls, f), listOf(element, element))
        for (path in paths) for (tail in listOf(Tail.EXACT, Tail.ANY_TAINT, Tail.ANY)) for (layer in Layer.entries) {
            val target = PathFact(y, path, tail, MarkSlot.Concrete(u))
            val actual = ops.targetTree(target, layer)
            assertEquals(setOf(normalize(target, empty, layer == Layer.DEMAND)), decode(actual))
            assertTrue(actual is TaintTree)
            assertEquals(y, actual.base)
        }
        for (tail in Tail.entries) {
            assertFails { ops.targetTree(PathFact(x, listOf(f), tail, MarkSlot.STAR), Layer.NORMAL) }
        }
        assertFails { ops.targetTree(PathFact(x, emptyList(), Tail.STAR, MarkSlot.Concrete(t)), Layer.NORMAL) }
        for (bad in listOf(
            PathFact(AccessPathBase.Zero, listOf(f), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO)),
            PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT, MarkSlot.Concrete(t)),
            PathFact(AccessPathBase.Zero, emptyList(), Tail.ANY, MarkSlot.Concrete(TaintMark.ZERO)),
            PathFact(AccessPathBase.Zero, emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(TaintMark.ZERO))))
            assertFails { ops.targetTree(bad, Layer.NORMAL) }
    }

    @Test
    fun `mark selection walks deep prefixes and observes cancellation`() {
        val depth = 32768
        val exact = m.taintLeaves(m.markSetOf(u), MarkSet.EMPTY)
        var chain = m.taintAlg.leafNode(exact)
        repeat(depth) { chain = m.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(f), arrayOf(chain)) }
        chain = m.taintAlg.node(m.taintLeaves(MarkSet.EMPTY, m.markSetOf(t)),
            chain.accessors, chain.children)
        val input = m.taintTree(x, Layer.NORMAL, chain, ex(g))
        val literal = p(List(depth) { f }, Tail.EXACT)
        val result = ops.checkMark(input, literal, forward)
        assertTrue(result is MarkCheck.Holds)
        val selected = result.facts as TaintTree
        assertEquals(m.markSetOf(t), selected.root.leaf.any)
        assertNull(selected.root.accessors)
        assertEquals(ex(g), selected.exclusion)
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { ops.checkMark(input, literal, forward) }
        assertFailsWith<Cancellation.Cancelled> { ops.withoutMarks(input, marks(t.id)) }
    }
}
