package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.BoundedDenotation
import org.opentaint.dataflow.bidi.fixtures.TestLocation
import org.opentaint.dataflow.bidi.fixtures.TestLocationPair
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Required incoming subsumption, independently decoded from typed trie payloads. T5 stays disabled. */
class SubsumptionTest {
    private val manager = ApManager(Cancellation())
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val fresh = manager.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val freshMark = manager.marks.mark("fresh-mark")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val empty = ExclusionSet.Empty
    private val paths = listOf(emptyList(), listOf(f), listOf(g), listOf(g, f), listOf(f, g))
    private val exclusions = listOf(empty, ex(f), ex(g), ex(f, g))
    private val markExclusions = listOf(MarkSet.EMPTY, marks(t.id), marks(u.id), marks(t.id, u.id))
    private val oracle = BoundedDenotation(
        listOf(x, y, AccessPathBase.Zero), listOf(f, g, fresh),
        listOf(TaintMark.ZERO, t, u, freshMark), maximumPathLength = 4,
    )
    private data class ObservedLocation(val demand: Boolean, val location: TestLocation)
    private val locationCache = HashMap<Conclusion, Set<ObservedLocation>>()

    @Test
    fun `flow subsumption agrees with direct leaves across path exclusion and mark exclusion cells`() {
        for (layer in Layer.entries) {
            val leaves = buildList {
                for (path in paths) for (mx in markExclusions) {
                    for (e in if (layer == Layer.NORMAL) exclusions else listOf(empty)) {
                        add(conclusion(path, if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                            MarkSlot.Star(mx), e, demand = layer == Layer.DEMAND))
                    }
                }
            }
            checkEveryOrderedPair(leaves)
        }
    }

    @Test
    fun `taint subsumption agrees with direct leaves across exact must may exclusions and marks`() {
        for (layer in Layer.entries) {
            val leaves = buildList {
                for (path in paths) for (mark in listOf(t, u, freshMark)) {
                    add(conclusion(path, Tail.EXACT, MarkSlot.Concrete(mark), demand = layer == Layer.DEMAND))
                    for (e in if (layer == Layer.NORMAL) exclusions else listOf(empty)) {
                        add(conclusion(path, if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY,
                            MarkSlot.Concrete(mark), e, demand = layer == Layer.DEMAND))
                    }
                }
            }
            checkEveryOrderedPair(leaves)
        }
    }

    @Test
    fun `duplicates return no delta and leave the stored object unchanged`() {
        val leaves = listOf(
            conclusion(emptyList(), Tail.STAR, MarkSlot.STAR, ex(f)),
            conclusion(listOf(g), Tail.ANY, MarkSlot.Star(marks(u.id))),
            conclusion(emptyList(), Tail.ANY_TAINT, mt, ex(f)),
            conclusion(listOf(g), Tail.EXACT, mt),
            conclusion(emptyList(), Tail.ANY, mt),
            conclusion(listOf(g), Tail.EXACT, mt, demand = true),
            conclusion(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero),
            conclusion(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), demand = true,
                base = AccessPathBase.Zero),
        )
        for (leaf in leaves) {
            val group = group()
            val original = typed(leaf)
            assertSame(original, group.add(original))
            val stored = group.all().single()
            assertNull(group.add(original))
            assertNull(group.add(typed(leaf)))
            assertSame(stored, group.all().single())
            assertEquals(setOf(leaf), decode(group.all().toList()))
        }
    }

    @Test
    fun `conclusion groups retain different layers kinds and bases independently`() {
        val leaves = buildList {
            for (base in listOf(x, y)) for (layer in Layer.entries) {
                val demand = layer == Layer.DEMAND
                add(conclusion(emptyList(), if (demand) Tail.ANY else Tail.STAR, MarkSlot.STAR,
                    demand = demand, base = base))
                add(conclusion(emptyList(), Tail.EXACT, mt, demand = demand, base = base))
            }
            for (demand in listOf(false, true)) {
                add(conclusion(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), demand = demand,
                    base = AccessPathBase.Zero))
            }
        }
        for (order in listOf(leaves, leaves.reversed())) {
            val group = group()
            for (leaf in order) assertNotNull(group.add(typed(leaf)), leaf.toString())
            assertEquals(leaves.toSet(), decode(group.all().toList()))
            assertEquals(observed(leaves), observed(decode(group.all().toList())))
            for (leaf in order) assertNull(group.add(typed(leaf)))
        }
    }

    @Test
    fun `normal flow star covers its node and does not cover correlated descendants`() {
        val ancestor = conclusion(emptyList(), Tail.STAR, MarkSlot.STAR)
        val descendant = conclusion(listOf(g), Tail.STAR, MarkSlot.STAR)
        assertFalse(subsumes(ancestor, descendant))
        for (order in listOf(listOf(ancestor, descendant), listOf(descendant, ancestor))) {
            val group = group()
            order.forEach { assertNotNull(group.add(typed(it))) }
            assertEquals(order.toSet(), decode(group.all().toList()))
        }
    }

    @Test
    fun `must exclusion filters only the first step below a nested stored leaf`() {
        val stored = conclusion(listOf(g), Tail.ANY_TAINT, mt, ex(f))
        val admitted = conclusion(listOf(g, g, f), Tail.ANY_TAINT, mt, ex(g))
        val blocked = conclusion(listOf(g, f, g), Tail.ANY_TAINT, mt)
        assertTrue(subsumes(stored, admitted))
        assertFalse(subsumes(stored, blocked))
        val group = group()
        assertNotNull(group.add(typed(stored)))
        assertNull(group.add(typed(admitted)))
        assertNotNull(group.add(typed(blocked)))
        assertEquals(setOf(stored, blocked), decode(group.all().toList()))
        assertEquals(observed(listOf(stored, admitted, blocked)), observed(decode(group.all().toList())))
    }

    @Test
    fun `shared incoming subtree is subtracted separately for different inherited cover states`() {
        val alg = manager.taintAlg
        val mustT = manager.taintLeaves(MarkSet.EMPTY, manager.markSetOf(t))
        val shared = alg.leafNode(mustT)
        val input = branch(alg, TaintLeaves.EMPTY, f to shared, g to shared)
        val stored = alg.leafNode(mustT)
        val storedExclusion = ex(f)
        assertSame(input.child(f), input.child(g))

        // Both child states reach this same node with no stored child. Only [g] inherits the root cover.
        val rest = assertNotNull(alg.subtract(input, stored, TaintLeaves.EMPTY,
            at = { it }, below = alg::belowNormal, admits = storedExclusion::admits,
            cancellation = manager.cancellation))
        assertSame(shared, rest.child(f))
        assertNull(rest.child(g))
        assertSame(shared, input.child(f))
        assertSame(shared, input.child(g))

        val storedLeaf = conclusion(emptyList(), Tail.ANY_TAINT, mt, storedExclusion)
        val retainedLeaf = conclusion(listOf(f), Tail.ANY_TAINT, mt)
        val coveredLeaf = conclusion(listOf(g), Tail.ANY_TAINT, mt)
        val delta = manager.taintTree(x, Layer.NORMAL, rest)
        val storedValue = manager.taintTree(x, Layer.NORMAL, stored, storedExclusion)
        assertEquals(setOf(retainedLeaf), decode(listOf(delta)))
        val storedUnion = decode(listOf(storedValue, delta))
        assertEquals(setOf(storedLeaf, retainedLeaf), storedUnion)
        assertEquals(observed(listOf(storedLeaf, retainedLeaf, coveredLeaf)), observed(storedUnion))
    }

    @Test
    fun `normal must removes covered must leaves and preserves exact leaves in the incoming delta`() {
        val stored = conclusion(emptyList(), Tail.ANY_TAINT, mt, ex(f))
        val covered = conclusion(emptyList(), Tail.ANY_TAINT, mt, ex(f, g))
        val exact = conclusion(listOf(g), Tail.EXACT, mt)
        val input = taintBundle(listOf(covered, exact), ex(f, g), Layer.NORMAL)
        val group = group()
        assertNotNull(group.add(typed(stored)))
        val delta = assertNotNull(group.add(input))
        assertEquals(setOf(exact), decode(listOf(delta)))
        assertEquals(empty, (delta as TaintTree).exclusion)
        assertEquals(setOf(stored, exact), decode(group.all().toList()))
        assertEquals(observed(listOf(stored, covered, exact)), observed(decode(group.all().toList())))
    }

    @Test
    fun `demand any removes matching exact and any marks while retaining other marks`() {
        val stored = conclusion(emptyList(), Tail.ANY, mt)
        val incoming = listOf(
            conclusion(listOf(g), Tail.EXACT, mt, demand = true),
            conclusion(listOf(g), Tail.ANY, mt),
            conclusion(listOf(g), Tail.EXACT, mu, demand = true),
            conclusion(listOf(g), Tail.ANY, mu),
        )
        val group = group()
        group.add(typed(stored))
        val delta = assertNotNull(group.add(taintBundle(incoming, empty, Layer.DEMAND)))
        val referenceDelta = incoming.filterNot { subsumes(stored, it) }.toSet()
        assertEquals(referenceDelta, decode(listOf(delta)))
        assertEquals(setOf(incoming[2], incoming[3]), referenceDelta)
        assertEquals(observed(listOf(stored) + incoming), observed(decode(group.all().toList())))
    }

    @Test
    fun `disabled T5 keeps old demand descendants after a new ancestor and rejects covered incoming leaves`() {
        for (mark in listOf(MarkSlot.STAR, mt)) {
            val descendant = conclusion(listOf(g), Tail.ANY, mark)
            val ancestor = conclusion(emptyList(), Tail.ANY, mark)
            val later = conclusion(listOf(g, f), Tail.ANY, mark)
            val group = group()
            assertNotNull(group.add(typed(descendant)))
            assertNotNull(group.add(typed(ancestor)))
            assertEquals(setOf(descendant, ancestor), decode(group.all().toList()))
            val stored = group.all().toList()
            assertNull(group.add(typed(later)))
            assertEquals(stored.size, group.all().count())
            stored.zip(group.all().toList()).forEach { (before, after) -> assertSame(before, after) }
            assertEquals(observed(listOf(descendant, ancestor, later)), observed(decode(group.all().toList())))
        }
    }

    @Test
    fun `a normal must ancestor preserves old exact descendants and cannot suppress later exact input`() {
        val ancestor = conclusion(emptyList(), Tail.ANY_TAINT, mt)
        val exact = conclusion(listOf(g), Tail.EXACT, mt)
        val later = conclusion(listOf(g, f), Tail.EXACT, mt)
        for (order in listOf(listOf(ancestor, exact), listOf(exact, ancestor))) {
            val group = group()
            order.forEach { assertNotNull(group.add(typed(it))) }
            assertEquals(order.toSet(), decode(group.all().toList()))
            assertNotNull(group.add(typed(later)))
            assertEquals((order + later).toSet(), decode(group.all().toList()))
            assertNull(group.add(typed(conclusion(listOf(g, f), Tail.ANY_TAINT, mt))))
        }
    }

    @Test
    fun `group unions preserve bounded input output relations in both insertion orders`() {
        val relationOracle = BoundedDenotation(listOf(x), listOf(f, g, fresh), listOf(t, u, freshMark), 3)
        val premise = Pattern(PathFact(x, emptyList(), Tail.STAR, MarkSlot.STAR), empty)
        val relationCache = HashMap<Conclusion, Set<Pair<Boolean, TestLocationPair>>>()
        fun relation(leaves: Collection<Conclusion>): Set<Pair<Boolean, TestLocationPair>> = leaves.flatMap { leaf ->
            relationCache.getOrPut(leaf) { relationOracle.pairs(premise, leaf).map { leaf.demand to it }.toSet() }
        }.toSet()
        val cases = listOf(
            conclusion(emptyList(), Tail.STAR, MarkSlot.STAR) to
                conclusion(listOf(g), Tail.STAR, MarkSlot.STAR),
            conclusion(emptyList(), Tail.STAR, MarkSlot.Star(marks(u.id))) to
                conclusion(emptyList(), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(f)),
            conclusion(emptyList(), Tail.ANY, MarkSlot.Star(marks(u.id))) to
                conclusion(listOf(g), Tail.ANY, MarkSlot.Star(marks(u.id))),
            conclusion(emptyList(), Tail.ANY_TAINT, mt, ex(f)) to
                conclusion(listOf(g), Tail.EXACT, mt),
            conclusion(emptyList(), Tail.ANY_TAINT, mt, ex(f)) to
                conclusion(listOf(g, f), Tail.ANY_TAINT, mt, ex(g)),
            conclusion(emptyList(), Tail.ANY_TAINT, mt, ex(f)) to
                conclusion(listOf(f, g), Tail.ANY_TAINT, mt),
            conclusion(emptyList(), Tail.ANY, mt) to
                conclusion(listOf(g), Tail.EXACT, mt, demand = true),
            conclusion(emptyList(), Tail.ANY, mt) to
                conclusion(listOf(g), Tail.ANY, mu),
        )
        for ((a, b) in cases) for (order in listOf(listOf(a, b), listOf(b, a))) {
            val group = group()
            order.forEach { group.add(typed(it)) }
            val actual = decode(group.all().toList())
            assertEquals(relation(order), relation(actual), order.toString())
            assertEquals(observed(order), observed(actual), order.toString())
        }
    }

    @Test
    fun `generic subtraction handles a shared 32768 depth tree and shares untouched input`() {
        val path = List(32767) { f }
        val shared = chain(FlowAlgebra, path, FlowLeaf.LEAF)
        val storedShared = chain(FlowAlgebra, path, FlowLeaf.LEAF)
        val input = branch(FlowAlgebra, FlowLeaf.NONE, f to shared, g to shared)
        val stored = branch(FlowAlgebra, FlowLeaf.NONE, f to storedShared, g to storedShared)
        assertEquals(32768, input.boundedDepth)
        assertNull(FlowAlgebra.subtract(input, stored, FlowLeaf.NONE,
            at = { it }, below = { FlowLeaf.NONE }, cancellation = manager.cancellation))

        val partialStored = branch(FlowAlgebra, FlowLeaf.NONE, f to storedShared)
        val rest = assertNotNull(FlowAlgebra.subtract(input, partialStored, FlowLeaf.NONE,
            at = { it }, below = { FlowLeaf.NONE }, cancellation = manager.cancellation))
        assertNull(rest.child(f))
        assertSame(shared, rest.child(g))
        assertSame(shared, input.child(f))
        assertSame(shared, input.child(g))
        assertSame(input, FlowAlgebra.subtract(input, null, FlowLeaf.NONE,
            at = { it }, below = { it }, cancellation = manager.cancellation))
    }

    @Test
    fun `generic subtraction observes its supplied cancellation`() {
        val input = chain(FlowAlgebra, List(128) { f }, FlowLeaf.LEAF)
        val stored = chain(FlowAlgebra, List(128) { f }, FlowLeaf.LEAF)
        manager.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> {
            FlowAlgebra.subtract(input, stored, FlowLeaf.NONE,
                at = { it }, below = { FlowLeaf.NONE }, cancellation = manager.cancellation)
        }
        manager.cancellation.activate()
        assertNull(FlowAlgebra.subtract(input, stored, FlowLeaf.NONE,
            at = { it }, below = { FlowLeaf.NONE }, cancellation = manager.cancellation))
    }

    private fun checkEveryOrderedPair(leaves: List<Conclusion>) {
        for (stored in leaves) for (incoming in leaves) {
            val label = "$stored then $incoming"
            val group = group()
            val first = typed(stored)
            assertSame(first, group.add(first), label)
            val delta = group.add(typed(incoming))
            assertEquals(subsumes(stored, incoming), delta == null, label)
            assertEquals(observed(listOf(stored, incoming)), observed(decode(group.all().toList())), label)
        }
    }

    private fun group() = ConclusionGroup(manager, manager.newInterners())
    private fun typed(c: Conclusion): Facts =
        manager.factsOf(c.fact, c.exclusion, if (c.demand) Layer.DEMAND else Layer.NORMAL)
    private fun ex(vararg ids: Int): ExclusionSet = ExclusionSet.of(ids)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun conclusion(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                           exclusion: ExclusionSet = empty, demand: Boolean = tail == Tail.ANY,
                           base: AccessPathBase = x) = Conclusion(PathFact(base, path, tail, mark), exclusion, demand)

    private fun observed(leaves: Collection<Conclusion>): Set<ObservedLocation> = leaves.flatMap { c ->
        locationCache.getOrPut(c) { oracle.marked(Pattern(c.fact, c.exclusion)).map { ObservedLocation(c.demand, it) }.toSet() }
    }.toSet()

    private fun <P : TrieLeaf> chain(alg: LeafAlgebra<P>, path: List<Int>, leaf: P): TrieNode<P> {
        var node = alg.leafNode(leaf)
        for (i in path.indices.reversed()) node = alg.node(alg.empty, intArrayOf(path[i]), arrayOf(node))
        return node
    }

    private fun <P : TrieLeaf> branch(alg: LeafAlgebra<P>, leaf: P,
                                     vararg children: Pair<Int, TrieNode<P>>): TrieNode<P> {
        val sorted = children.sortedBy { it.first }
        return alg.node(leaf, sorted.map { it.first }.toIntArray(), sorted.map { it.second }.toTypedArray())
    }

    private class Prefix {
        val leaves = mutableListOf<Conclusion>()
        val children = sortedMapOf<Int, Prefix>()
    }

    private fun taintBundle(leaves: List<Conclusion>, exclusion: ExclusionSet, layer: Layer): TaintTree {
        val prefix = Prefix()
        for (leaf in leaves) {
            var node = prefix
            for (accessor in leaf.fact.path) node = node.children.getOrPut(accessor) { Prefix() }
            node.leaves += leaf
        }
        fun build(node: Prefix): TaintNode {
            val exact = node.leaves.filter { it.fact.tail == Tail.EXACT }
                .map { (it.fact.mark as MarkSlot.Concrete).mark.id }
            val any = node.leaves.filter { it.fact.tail != Tail.EXACT }
                .map { (it.fact.mark as MarkSlot.Concrete).mark.id }
            return manager.taintAlg.node(manager.taintLeaves(MarkSet(exact.toIntArray()), MarkSet(any.toIntArray())),
                node.children.keys.toIntArray(), node.children.values.map(::build).toTypedArray())
        }
        return manager.taintTree(x, layer, build(prefix), exclusion)
    }

    private fun <P : TrieLeaf> walk(root: TrieNode<P>, action: (List<Int>, P) -> Unit) {
        val pending = ArrayDeque<Pair<List<Int>, TrieNode<P>>>()
        pending.addLast(emptyList<Int>() to root)
        while (pending.isNotEmpty()) {
            val (path, node) = pending.removeLast()
            if (!node.leaf.isEmpty) action(path, node.leaf)
            val accessors = node.accessors
            val children = node.children
            if (accessors != null && children != null) for (i in accessors.indices) {
                pending.addLast((path + accessors[i]) to children[i])
            }
        }
    }

    private fun decode(facts: Collection<Facts>): Set<Conclusion> = buildSet {
        for (value in facts) {
            val demand = value.layer == Layer.DEMAND
            when (value) {
                is Reach -> add(conclusion(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO),
                    demand = demand, base = AccessPathBase.Zero))
                is FlowTree -> walk(value.root) { path, leaf ->
                    if (leaf == FlowLeaf.LEAF) add(conclusion(path, value.tail,
                        MarkSlot.Star(value.markExclusion), value.exclusion, demand, value.base))
                }
                is TaintTree -> walk(value.root) { path, leaf ->
                    for (id in leaf.exact.ids) add(conclusion(path, Tail.EXACT, MarkSlot.Concrete(TaintMark(id)),
                        demand = demand, base = value.base))
                    for (id in leaf.any.ids) add(conclusion(path, value.anyTail,
                        MarkSlot.Concrete(TaintMark(id)), value.exclusion, demand, value.base))
                }
            }
        }
    }
}
