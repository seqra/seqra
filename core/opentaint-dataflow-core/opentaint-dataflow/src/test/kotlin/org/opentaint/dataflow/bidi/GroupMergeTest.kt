package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.BoundedDenotation
import org.opentaint.dataflow.bidi.fixtures.TestLocation
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Groups are checked by literal leaves and bounded unions, without production decoding. */
class GroupMergeTest {
    private val m = ApManager(Cancellation())
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = m.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = m.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val fresh = m.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val element = m.accessors.index(ElementAccessor)
    private val t = m.marks.mark("T")
    private val u = m.marks.mark("U")
    private val v = m.marks.mark("Fresh")
    private val empty = ExclusionSet.Empty
    private val universe = BoundedDenotation(listOf(x, y, AccessPathBase.This, AccessPathBase.Zero),
        listOf(f, g, fresh, cls, element), listOf(t, u, v, TaintMark.ZERO), 2)
    private val premise = Pattern(PathFact(AccessPathBase.This, emptyList(), Tail.STAR, MarkSlot.STAR), empty)

    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private data class Atom(val path: List<Int>, val tail: Tail, val mark: TaintMark)
    private fun a(path: List<Int>, tail: Tail, mark: TaintMark = t) = Atom(path, tail, mark)
    private class Prefix {
        var flag = false
        val exact = sortedSetOf<Int>()
        val any = sortedSetOf<Int>()
        val children = sortedMapOf<Int, Prefix>()
    }
    private fun at(root: Prefix, path: List<Int>): Prefix {
        var node = root
        for (accessor in path) node = node.children.getOrPut(accessor) { Prefix() }
        return node
    }
    private fun <P : TrieLeaf> build(prefix: Prefix, alg: LeafAlgebra<P>, leaf: (Prefix) -> P): TrieNode<P> =
        alg.node(leaf(prefix), prefix.children.keys.toIntArray(),
            prefix.children.values.map { build(it, alg, leaf) }.toTypedArray())
    private fun flow(vararg paths: List<Int>, e: ExclusionSet = empty, mx: MarkSet = MarkSet.EMPTY,
                     layer: Layer = Layer.NORMAL, base: AccessPathBase = x): FlowTree {
        val root = Prefix()
        for (path in paths) at(root, path).flag = true
        return m.flowTree(base, layer, e, mx, build(root, m.flowAlg) { if (it.flag) FlowLeaf.LEAF else FlowLeaf.NONE })
    }
    private fun taint(vararg atoms: Atom, e: ExclusionSet = empty,
                      layer: Layer = Layer.NORMAL, base: AccessPathBase = x): TaintTree {
        val root = Prefix()
        for (atom in atoms) {
            val node = at(root, atom.path)
            if (atom.tail == Tail.EXACT) node.exact += atom.mark.id else node.any += atom.mark.id
        }
        return m.taintTree(base, layer, build(root, m.taintAlg) {
            m.taintLeaves(MarkSet(it.exact.toIntArray()), MarkSet(it.any.toIntArray()))
        }, e)
    }
    private fun <P : TrieLeaf> walk(root: TrieNode<P>, visit: (List<Int>, P) -> Unit) {
        val queue = ArrayDeque<Pair<List<Int>, TrieNode<P>>>()
        queue.addLast(emptyList<Int>() to root)
        while (queue.isNotEmpty()) {
            val (path, node) = queue.removeLast()
            if (!node.leaf.isEmpty) visit(path, node.leaf)
            val accessors = node.accessors
            val children = node.children
            if (accessors != null && children != null) {
                for (i in accessors.indices) queue.addLast((path + accessors[i]) to children[i])
            }
        }
    }
    private fun decode(facts: Facts): Set<Conclusion> = buildSet {
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
    private data class Observation(val kind: FactsKind, val layer: Layer, val base: AccessPathBase)
    private fun denotation(facts: List<Facts>): Map<Observation, Set<TestLocation>> = facts
        .groupBy { Observation(it.groupKey.kind, it.layer, it.base) }
        .mapValues { (_, group) -> group.flatMap { decode(it) }
            .flatMap { universe.marked(Pattern(it.fact, it.exclusion)) }.toSet() }
    private fun assertUnion(expected: List<Facts>, actual: List<Facts>) {
        assertEquals(denotation(expected), denotation(actual))
    }
    private fun assertPairsUnion(expected: List<Facts>, actual: List<Facts>) {
        val before = expected.flatMap { decode(it) }.flatMap { universe.pairs(premise, it) }.toSet()
        val after = actual.flatMap { decode(it) }.flatMap { universe.pairs(premise, it) }.toSet()
        assertTrue(before.containsAll(after), "Stored relation must not add pairs")
        assertTrue(after.containsAll(before), "Stored relation must keep every input pair")
        assertEquals(before, after)
    }

    @Test
    fun `T1 flow union returns only new flags and exact duplicates return no delta`() {
        val left = flow(listOf(f))
        val right = flow(listOf(f), listOf(g))
        for (order in listOf(listOf(left, right), listOf(right, left))) {
            val group = FlowGroup(m, m.newInterners().flow)
            assertEquals(decode(order[0]), decode(requireNotNull(group.add(order[0]))))
            val delta = group.add(order[1])
            val required = decode(order[1]) - decode(order[0])
            if (required.isEmpty()) assertNull(delta) else assertEquals(required, decode(requireNotNull(delta)))
            assertEquals(1, group.all().count())
            assertEquals(decode(right), decode(group.all().single()))
            assertUnion(order, group.all().toList())
            assertNull(group.add(right))
        }
    }

    @Test
    fun `T1 taint union merges independent exact and any mark columns with a literal delta`() {
        val left = taint(a(listOf(f), Tail.EXACT), a(listOf(g), Tail.ANY_TAINT, u), e = ex(fresh))
        val right = taint(a(listOf(f), Tail.EXACT), a(listOf(f), Tail.EXACT, u),
            a(listOf(g), Tail.ANY_TAINT), e = ex(fresh))
        for (order in listOf(listOf(left, right), listOf(right, left))) {
            val group = TaintGroup(m, m.newInterners().taint)
            group.add(order[0])
            val delta = requireNotNull(group.add(order[1]))
            assertEquals(decode(order[1]) - decode(order[0]), decode(delta))
            assertEquals(1, group.all().count())
            assertEquals(decode(left) + decode(right), decode(group.all().single()))
            assertUnion(order, group.all().toList())
            assertNull(group.add(left))
            assertNull(group.add(right))
        }
    }

    @Test
    fun `T2 equal flow content intersects E and returns the whole merged tree`() {
        val left = flow(emptyList(), listOf(f), e = ex(f, fresh), mx = marks(u.id))
        val right = flow(emptyList(), listOf(f), e = ex(g, fresh), mx = marks(u.id))
        val merged = flow(emptyList(), listOf(f), e = ex(fresh), mx = marks(u.id))
        for (order in listOf(listOf(left, right), listOf(right, left))) {
            val group = FlowGroup(m, m.newInterners().flow)
            group.add(order[0])
            val delta = requireNotNull(group.add(order[1]))
            assertEquals(decode(merged), decode(delta))
            assertEquals(listOf(merged), group.all().toList())
            assertUnion(order, group.all().toList())
            assertPairsUnion(order, group.all().toList())
            assertNull(group.add(order[1]))
        }
    }

    @Test
    fun `T2 prime equal flow content intersects mark exclusions without changing E`() {
        val left = flow(emptyList(), e = ex(f), mx = marks(t.id))
        val right = flow(emptyList(), e = ex(f), mx = marks(u.id))
        val merged = flow(emptyList(), e = ex(f))
        for (order in listOf(listOf(left, right), listOf(right, left))) {
            val group = FlowGroup(m, m.newInterners().flow)
            group.add(order[0])
            assertEquals(decode(merged), decode(requireNotNull(group.add(order[1]))))
            assertEquals(listOf(merged), group.all().toList())
            assertUnion(order, group.all().toList())
            assertPairsUnion(order, group.all().toList())
            assertNull(group.add(left))
            assertNull(group.add(right))
        }
    }

    @Test
    fun `T2 taint union witness uses the whole location union instead of pairwise leaf coverage`() {
        val left = taint(a(emptyList(), Tail.ANY_TAINT), e = ex(f))
        val right = taint(a(emptyList(), Tail.ANY_TAINT), e = ex(g))
        val merged = taint(a(emptyList(), Tail.ANY_TAINT))
        val mergedLocations = universe.marked(Pattern(decode(merged).single().fact, empty))
        for (original in listOf(left, right)) {
            val leaf = decode(original).single()
            assertFalse(universe.marked(Pattern(leaf.fact, leaf.exclusion)).containsAll(mergedLocations))
        }
        for (order in listOf(listOf(left, right), listOf(right, left))) {
            val group = TaintGroup(m, m.newInterners().taint)
            group.add(order[0])
            assertEquals(decode(merged), decode(requireNotNull(group.add(order[1]))))
            assertEquals(listOf(merged), group.all().toList())
            assertUnion(order, group.all().toList())
            assertPairsUnion(order, group.all().toList())
        }
    }

    @Test
    fun `T4 changing must E returns old exact leaves as part of the whole merged delta`() {
        val left = taint(a(emptyList(), Tail.ANY_TAINT), a(listOf(fresh), Tail.EXACT, u), e = ex(f))
        val right = taint(a(emptyList(), Tail.ANY_TAINT), a(listOf(fresh), Tail.EXACT, u), e = ex(g))
        val merged = taint(a(emptyList(), Tail.ANY_TAINT), a(listOf(fresh), Tail.EXACT, u))
        for (order in listOf(listOf(left, right), listOf(right, left))) {
            val group = TaintGroup(m, m.newInterners().taint)
            group.add(order[0])
            val delta = requireNotNull(group.add(order[1]))
            assertEquals(decode(merged), decode(delta))
            assertTrue(decode(delta).any { it.fact.tail == Tail.EXACT && it.fact.mark == MarkSlot.Concrete(u) })
            assertUnion(order, group.all().toList())
            assertNull(group.add(merged))
        }
    }

    @Test
    fun `different contents cannot intersect field or mark exclusions`() {
        val cases = listOf(
            listOf(flow(listOf(f), e = ex(f)), flow(listOf(g), e = ex(g))),
            listOf(flow(listOf(f), mx = marks(t.id)), flow(listOf(g), mx = marks(u.id))),
        )
        for (pair in cases) for (order in listOf(pair, pair.reversed())) {
            val group = FlowGroup(m, m.newInterners().flow)
            for (input in order) assertEquals(decode(input), decode(requireNotNull(group.add(input))))
            assertEquals(pair.toSet(), group.all().toSet())
            assertUnion(order, group.all().toList())
            assertPairsUnion(order, group.all().toList())
        }
        val taints = listOf(taint(a(listOf(f), Tail.ANY_TAINT), e = ex(f)),
            taint(a(listOf(g), Tail.ANY_TAINT), e = ex(g)))
        for (order in listOf(taints, taints.reversed())) {
            val group = TaintGroup(m, m.newInterners().taint)
            for (input in order) assertEquals(decode(input), decode(requireNotNull(group.add(input))))
            assertEquals(taints.toSet(), group.all().toSet())
            assertUnion(order, group.all().toList())
        }
    }

    @Test
    fun `equal flow content cannot intersect E and mark exclusion together`() {
        val left = flow(emptyList(), e = ex(f), mx = marks(t.id))
        val right = flow(emptyList(), e = ex(g), mx = marks(u.id))
        val forbidden = flow(emptyList())
        assertNotEquals(denotation(listOf(left, right)), denotation(listOf(forbidden)))
        for (order in listOf(listOf(left, right), listOf(right, left))) {
            val group = FlowGroup(m, m.newInterners().flow)
            for (input in order) assertEquals(decode(input), decode(requireNotNull(group.add(input))))
            assertEquals(setOf(left, right), group.all().toSet())
            assertUnion(order, group.all().toList())
            assertPairsUnion(order, group.all().toList())
        }
    }

    @Test
    fun `T3 conclusion groups preserve base kind and layer and deduplicate reach`() {
        val inputs = listOf<Facts>(Reach.NORMAL, Reach.DEMAND,
            flow(listOf(f)), flow(listOf(f), base = y), flow(listOf(f), layer = Layer.DEMAND),
            taint(a(listOf(f), Tail.EXACT)), taint(a(listOf(f), Tail.EXACT), base = y),
            taint(a(listOf(f), Tail.EXACT), layer = Layer.DEMAND))
        for (order in listOf(inputs, inputs.reversed())) {
            val group = ConclusionGroup(m, m.newInterners())
            for (input in order) assertEquals(decode(input), decode(requireNotNull(group.add(input))))
            assertEquals(inputs.toSet(), group.all().toSet())
            assertUnion(order, group.all().toList())
            assertEquals(8, group.all().count())
            for (input in inputs) assertNull(group.add(input))
        }
    }

    @Test
    fun `store interner factories keep per-store caches and preserve every typed annotation`() {
        val first = m.newInterners()
        val second = StoreInterners(m.softRefs, m.cancellation)
        assertNotSame(first, second)
        assertNotSame(first.flow, second.flow)
        assertNotSame(first.taint, second.taint)
        assertSame(Reach.NORMAL, first.internIfRequired(Reach.NORMAL))
        assertSame(Reach.DEMAND, first.internIfRequired(Reach.DEMAND))
        var flowRoot = m.flowAlg.LEAF_NODE
        var taintRoot = m.taintAlg.leafNode(m.taintLeaves(marks(t.id), marks(u.id)))
        val edges = intArrayOf(f, g).sortedArray()
        repeat(16) {
            flowRoot = m.flowAlg.node(FlowLeaf.NONE, edges, arrayOf(flowRoot, flowRoot))
            taintRoot = m.taintAlg.node(TaintLeaves.EMPTY, edges, arrayOf(taintRoot, taintRoot))
        }
        assertTrue(flowRoot.size >= TrieInterner.SIZE_TO_FORCE_INTERN)
        assertTrue(taintRoot.size >= TrieInterner.SIZE_TO_FORCE_INTERN)
        val rawFlow = m.flowTree(x, Layer.NORMAL, ex(fresh), marks(v.id), flowRoot)
        val rawTaint = m.taintTree(y, Layer.NORMAL, taintRoot, ex(fresh))
        val storedFlow = first.internIfRequired(rawFlow) as FlowTree
        val storedTaint = first.internIfRequired(rawTaint) as TaintTree
        assertTrue(storedFlow.root.interned)
        assertTrue(storedTaint.root.interned)
        assertEquals(rawFlow, storedFlow)
        assertEquals(rawTaint, storedTaint)
        assertEquals(rawFlow.groupKey, storedFlow.groupKey)
        assertEquals(rawTaint.groupKey, storedTaint.groupKey)
        assertSame(storedFlow, first.internIfRequired(storedFlow))
        assertSame(storedTaint, first.internIfRequired(storedTaint))
    }

    @Test
    fun `Results groups canonical keys and exact leaves ignore the supplied E`() {
        val results = Results(m, x)
        val out = CollectingOut()
        results.reach(Layer.NORMAL)
        results.reach(Layer.NORMAL)
        results.reach(Layer.DEMAND)
        results.flow(Layer.NORMAL, ex(f), marks(u.id), flow(listOf(f)).root)
        results.flow(Layer.NORMAL, ex(f, f), marks(u.id, u.id), flow(listOf(g)).root)
        results.flow(Layer.NORMAL, ex(g), marks(u.id), flow(listOf(f)).root)
        results.flow(Layer.NORMAL, ex(f), marks(t.id), flow(listOf(g)).root)
        results.flow(Layer.DEMAND, ex(f), marks(u.id), flow(listOf(f)).root)
        results.flow(Layer.DEMAND, ex(g), marks(u.id), flow(listOf(g)).root)
        results.taint(Layer.NORMAL, taint(a(emptyList(), Tail.ANY_TAINT)).root, ex(f))
        results.taint(Layer.NORMAL, taint(a(listOf(f), Tail.ANY_TAINT, u)).root, ex(g))
        results.taint(Layer.NORMAL, taint(a(listOf(f), Tail.EXACT)).root, ex(f))
        results.taint(Layer.NORMAL, taint(a(listOf(g), Tail.EXACT, u)).root, ex(g))
        results.taint(Layer.DEMAND, taint(a(emptyList(), Tail.ANY_TAINT)).root, ex(f))
        results.taint(Layer.DEMAND, taint(a(listOf(g), Tail.EXACT, u)).root, ex(g))
        results.flush(out)
        val expected = listOf<Facts>(Reach.NORMAL, Reach.DEMAND,
            flow(listOf(f), listOf(g), e = ex(f), mx = marks(u.id)),
            flow(listOf(f), e = ex(g), mx = marks(u.id)),
            flow(listOf(g), e = ex(f), mx = marks(t.id)),
            flow(listOf(f), listOf(g), mx = marks(u.id), layer = Layer.DEMAND),
            taint(a(emptyList(), Tail.ANY_TAINT), e = ex(f)),
            taint(a(listOf(f), Tail.ANY_TAINT, u), e = ex(g)),
            taint(a(listOf(f), Tail.EXACT), a(listOf(g), Tail.EXACT, u)),
            taint(a(emptyList(), Tail.ANY), a(listOf(g), Tail.EXACT, u), layer = Layer.DEMAND))
        assertEquals(expected.toSet(), out.results.toSet())
        assertEquals(expected.size, out.results.size)
        assertEquals(out.results.size, out.results.map { it.groupKey }.toSet().size)
        assertUnion(expected, out.results)
        for (fact in out.results) when (fact) {
            is Reach -> assertSame(Reach.of(fact.layer), fact)
            is FlowTree -> {
                assertSame(m.intern(fact.exclusion), fact.exclusion)
                assertSame(m.intern(fact.markExclusion), fact.markExclusion)
            }
            is TaintTree -> assertSame(m.intern(fact.exclusion), fact.exclusion)
        }
        results.flush(out)
        assertEquals(expected.size, out.results.size)
        results.reach(Layer.NORMAL)
        results.taint(Layer.NORMAL, taint(a(listOf(fresh), Tail.EXACT, v)).root, ex(f))
        val next = CollectingOut()
        results.flush(next)
        assertEquals(setOf<Facts>(Reach.NORMAL, taint(a(listOf(fresh), Tail.EXACT, v))), next.results.toSet())
    }

    @Test
    fun `CollectingOut retains repeated facts and requests without hidden grouping`() {
        val out = CollectingOut()
        val position = requireNotNull(m.path(listOf(cls, f)))
        repeat(2) {
            out.result(Reach.NORMAL)
            out.markRequest(t)
            out.positionRequest(position)
        }
        assertEquals(listOf<Facts>(Reach.NORMAL, Reach.NORMAL), out.results)
        assertEquals(listOf(t, t), out.markRequests)
        assertEquals(listOf(position, position), out.positionRequests)
    }
}
