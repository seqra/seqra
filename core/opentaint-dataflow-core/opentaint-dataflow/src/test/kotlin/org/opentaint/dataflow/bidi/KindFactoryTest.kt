package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Decode typed trees here. These tests do not use ApOps, prepend, or merge. */
class KindFactoryTest {
    private val manager = ApManager(Cancellation())
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val cls = manager.accessors.index(ClassStaticAccessor("Box"))
    private val element = manager.accessors.index(ElementAccessor)
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val empty = ExclusionSet.Empty

    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun conclusion(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                           exclusion: ExclusionSet = empty, demand: Boolean = tail == Tail.ANY,
                           base: AccessPathBase = x) = Conclusion(PathFact(base, path, tail, mark), exclusion, demand)

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

    private fun <P : TrieLeaf> walk(root: TrieNode<P>, action: (List<Int>, P) -> Unit) {
        val pending = ArrayDeque<Pair<List<Int>, TrieNode<P>>>()
        pending.addLast(emptyList<Int>() to root)
        while (pending.isNotEmpty()) {
            val (path, node) = pending.removeLast()
            if (!node.leaf.isEmpty) action(path, node.leaf)
            val accessors = node.accessors
            val children = node.children
            if (accessors != null && children != null) {
                for (i in accessors.indices) pending.addLast((path + accessors[i]) to children[i])
            }
        }
    }

    private fun decode(facts: Facts): Set<Conclusion> {
        val result = mutableSetOf<Conclusion>()
        val demand = facts.layer == Layer.DEMAND
        when (facts) {
            is Reach -> result += conclusion(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO),
                demand = demand, base = AccessPathBase.Zero)
            is FlowTree -> walk(facts.root) { path, leaf ->
                if (leaf == FlowLeaf.LEAF) result += conclusion(path, if (demand) Tail.ANY else Tail.STAR,
                    MarkSlot.Star(facts.markExclusion), facts.exclusion, demand, facts.base)
            }
            is TaintTree -> walk(facts.root) { path, leaf ->
                for (id in leaf.exact.ids) result += conclusion(path, Tail.EXACT, MarkSlot.Concrete(TaintMark(id)),
                    demand = demand, base = facts.base)
                for (id in leaf.any.ids) result += conclusion(path, if (demand) Tail.ANY else Tail.ANY_TAINT,
                    MarkSlot.Concrete(TaintMark(id)), facts.exclusion, demand, facts.base)
            }
        }
        return result
    }

    private class DecodedPrefix {
        val leaves = mutableListOf<Conclusion>()
        val children = sortedMapOf<Int, DecodedPrefix>()
    }

    private fun prefixMap(leaves: Collection<Conclusion>): DecodedPrefix {
        val root = DecodedPrefix()
        for (leaf in leaves) {
            var prefix = root
            for (accessor in leaf.fact.path) prefix = prefix.children.getOrPut(accessor) { DecodedPrefix() }
            prefix.leaves += leaf
        }
        return root
    }

    private fun <P : TrieLeaf> build(prefix: DecodedPrefix, alg: LeafAlgebra<P>,
                                     payload: (List<Conclusion>) -> P): TrieNode<P> {
        val children = prefix.children.values.map { build(it, alg, payload) }.toTypedArray()
        return alg.node(payload(prefix.leaves), prefix.children.keys.toIntArray(), children)
    }

    private fun rebuildFlow(leaves: Collection<Conclusion>): FlowNode =
        build(prefixMap(leaves), manager.flowAlg) { at ->
            assertTrue(at.all { it.fact.tail == Tail.STAR || it.fact.tail == Tail.ANY })
            assertTrue(at.all { it.fact.mark is MarkSlot.Star })
            if (at.isEmpty()) FlowLeaf.NONE else FlowLeaf.LEAF
        }

    private fun rebuildTaint(leaves: Collection<Conclusion>): TaintNode =
        build(prefixMap(leaves), manager.taintAlg) { at ->
            assertTrue(at.all { it.fact.tail == Tail.EXACT || it.fact.tail == Tail.ANY || it.fact.tail == Tail.ANY_TAINT })
            val exact = at.filter { it.fact.tail == Tail.EXACT }.map { (it.fact.mark as MarkSlot.Concrete).mark.id }
            val any = at.filter { it.fact.tail == Tail.ANY || it.fact.tail == Tail.ANY_TAINT }
                .map { (it.fact.mark as MarkSlot.Concrete).mark.id }
            manager.taintLeaves(MarkSet(exact.toIntArray()), MarkSet(any.toIntArray()))
        }

    @Test
    fun `manager exposes shared flow algebra and canonical concrete payloads`() {
        assertSame(FlowAlgebra, manager.flowAlg)
        assertSame(manager.taintAlg, manager.taintAlg)
        assertSame(TaintLeaves.EMPTY, manager.taintLeaves(marks(), marks()))
        val first = manager.taintLeaves(marks(u.id, t.id, t.id), marks(u.id))
        val second = manager.taintAlg.leaves(marks(t.id, u.id), marks(u.id, u.id))
        assertSame(first, second)
        assertSame(manager.intern(marks(t.id, u.id)), first.exact)
        assertSame(manager.markSetOf(u), first.any)
        assertSame(manager.taintAlg.leafNode(first), manager.taintAlg.leafNode(second))
        assertFalse(first.isEmpty)
        assertTrue(first.hasAny)
    }

    @Test
    fun `sparse flow flags decode as star in normal and may in demand`() {
        val root = branch(manager.flowAlg, FlowLeaf.NONE,
            cls to chain(manager.flowAlg, listOf(f, element), FlowLeaf.LEAF),
            element to manager.flowAlg.LEAF_NODE,
            f to chain(manager.flowAlg, listOf(g), FlowLeaf.LEAF))
        val mx = marks(u.id)
        val normal = manager.flowTree(x, Layer.NORMAL, ex(g), mx, root)
        assertEquals(setOf(
            conclusion(listOf(cls, f, element), Tail.STAR, MarkSlot.Star(mx), ex(g)),
            conclusion(listOf(element), Tail.STAR, MarkSlot.Star(mx), ex(g)),
            conclusion(listOf(f, g), Tail.STAR, MarkSlot.Star(mx), ex(g)),
        ), decode(normal))
        val demand = manager.flowTree(x, Layer.DEMAND, ex(g), mx, root)
        assertEquals(setOf(
            conclusion(listOf(cls, f, element), Tail.ANY, MarkSlot.Star(mx)),
            conclusion(listOf(element), Tail.ANY, MarkSlot.Star(mx)),
            conclusion(listOf(f, g), Tail.ANY, MarkSlot.Star(mx)),
        ), decode(demand))
        assertEquals(empty, demand.exclusion)
        assertSame(root, normal.root)
        assertSame(root, demand.root)
        assertEquals(FlowTree(x, Layer.NORMAL, ex(g), mx, root), normal)
        assertEquals(FlowTree(x, Layer.DEMAND, empty, mx, root), demand)
    }

    @Test
    fun `mixed taint marks decode with must E only on normal any leaves`() {
        val root = branch(manager.taintAlg, manager.taintLeaves(marks(u.id), marks(t.id)),
            cls to chain(manager.taintAlg, listOf(f, element), manager.taintLeaves(marks(t.id), marks(u.id))),
            element to manager.taintAlg.leafNode(manager.taintLeaves(marks(t.id, u.id), marks())))
        val normal = manager.taintTree(x, Layer.NORMAL, root, ex(f))
        assertEquals(setOf(
            conclusion(emptyList(), Tail.EXACT, mu),
            conclusion(emptyList(), Tail.ANY_TAINT, mt, ex(f)),
            conclusion(listOf(cls, f, element), Tail.EXACT, mt),
            conclusion(listOf(cls, f, element), Tail.ANY_TAINT, mu, ex(f)),
            conclusion(listOf(element), Tail.EXACT, mt),
            conclusion(listOf(element), Tail.EXACT, mu),
        ), decode(normal))
        val demand = manager.taintTree(x, Layer.DEMAND, root, ex(f))
        assertEquals(setOf(
            conclusion(emptyList(), Tail.EXACT, mu, demand = true),
            conclusion(emptyList(), Tail.ANY, mt),
            conclusion(listOf(cls, f, element), Tail.EXACT, mt, demand = true),
            conclusion(listOf(cls, f, element), Tail.ANY, mu),
            conclusion(listOf(element), Tail.EXACT, mt, demand = true),
            conclusion(listOf(element), Tail.EXACT, mu, demand = true),
        ), decode(demand))
        assertEquals(ex(f), normal.exclusion)
        assertEquals(empty, demand.exclusion)
        assertSame(root, normal.root)
        assertSame(root, demand.root)
        assertEquals(TaintTree(x, Layer.NORMAL, root, ex(f)), normal)
        assertEquals(TaintTree(x, Layer.DEMAND, root, empty), demand)
    }

    @Test
    fun `sparse flow roots rebuild from decoded leaf paths in both layers`() {
        val root = branch(manager.flowAlg, FlowLeaf.LEAF,
            cls to branch(manager.flowAlg, FlowLeaf.LEAF, f to manager.flowAlg.LEAF_NODE),
            f to chain(manager.flowAlg, listOf(g), FlowLeaf.LEAF),
            element to manager.flowAlg.LEAF_NODE)
        for (layer in Layer.entries) {
            val original = manager.flowTree(x, layer, ex(f, g), marks(u.id), root)
            val leaves = decode(original).toList()
            assertEquals(setOf(emptyList(), listOf(cls), listOf(cls, f), listOf(f, g), listOf(element)),
                leaves.map { it.fact.path }.toSet())
            val rebuilt = manager.flowTree(original.base, original.layer, original.exclusion,
                original.markExclusion, rebuildFlow(leaves))
            assertNotSame(original.root, rebuilt.root)
            assertEquals(original, rebuilt)
            assertEquals(original.hashCode(), rebuilt.hashCode())
            assertEquals(leaves.toSet(), decode(rebuilt))
            assertSame(original.exclusion, rebuilt.exclusion)
            assertSame(original.markExclusion, rebuilt.markExclusion)
        }
    }

    @Test
    fun `mixed sparse taint roots rebuild marks and tails without applying E to exact leaves`() {
        val root = branch(manager.taintAlg, manager.taintLeaves(marks(t.id), marks(u.id)),
            cls to branch(manager.taintAlg, manager.taintLeaves(marks(u.id), marks()),
                f to manager.taintAlg.leafNode(manager.taintLeaves(marks(), marks(t.id))),
                element to manager.taintAlg.leafNode(manager.taintLeaves(marks(t.id), marks(u.id)))),
            element to manager.taintAlg.leafNode(manager.taintLeaves(marks(t.id, u.id), marks())))
        for (layer in Layer.entries) {
            val original = manager.taintTree(x, layer, root, ex(f, g))
            val leaves = decode(original).toList()
            val exact = leaves.filter { it.fact.tail == Tail.EXACT }
            val any = leaves.filter { it.fact.tail != Tail.EXACT }
            assertTrue(exact.isNotEmpty())
            assertTrue(any.isNotEmpty())
            assertTrue(exact.all { it.exclusion == empty })
            assertTrue(any.all { it.exclusion == original.exclusion })
            val rebuilt = manager.taintTree(original.base, original.layer, rebuildTaint(leaves), original.exclusion)
            assertNotSame(original.root, rebuilt.root)
            assertEquals(original, rebuilt)
            assertEquals(original.hashCode(), rebuilt.hashCode())
            assertEquals(leaves.toSet(), decode(rebuilt))
            assertSame(original.exclusion, rebuilt.exclusion)
            assertEquals(if (layer == Layer.NORMAL) ex(f, g) else empty, rebuilt.exclusion)
            // The exact part still has the same facts when the original must E is supplied.
            val exactOnly = manager.taintTree(original.base, original.layer, rebuildTaint(exact), original.exclusion)
            assertFalse(exactOnly.root.hasAny)
            assertEquals(empty, exactOnly.exclusion)
            assertEquals(exact.toSet(), decode(exactOnly))
        }
    }

    @Test
    fun `an exact-only root clears E even when its marks occur under excluded accessors`() {
        val root = chain(manager.taintAlg, listOf(cls, f, element), manager.taintLeaves(marks(t.id, u.id), marks()))
        assertFalse(root.hasAny)
        for (layer in Layer.entries) {
            val facts = manager.taintTree(x, layer, root, ex(f, element))
            assertSame(root, facts.root)
            assertEquals(empty, facts.exclusion)
            assertEquals(setOf(
                conclusion(listOf(cls, f, element), Tail.EXACT, mt, demand = layer == Layer.DEMAND),
                conclusion(listOf(cls, f, element), Tail.EXACT, mu, demand = layer == Layer.DEMAND),
            ), decode(facts))
            assertEquals(GroupKey(FactsKind.TAINT, layer, x), facts.groupKey)
        }
    }

    @Test
    fun `direct typed constructors reject nonempty E in demand and E without must leaves`() {
        val anyRoot = manager.taintAlg.leafNode(manager.taintLeaves(marks(), marks(t.id)))
        val exactRoot = manager.taintAlg.leafNode(manager.taintLeaves(marks(t.id), marks()))
        assertFails { FlowTree(x, Layer.DEMAND, ex(f), marks(), manager.flowAlg.LEAF_NODE) }
        assertFails { TaintTree(x, Layer.DEMAND, anyRoot, ex(f)) }
        assertFails { TaintTree(x, Layer.NORMAL, exactRoot, ex(f)) }
    }

    @Test
    fun `one-path factories match literal normalized reference facts for every supported shape`() {
        data class Row(val tail: Tail, val mark: MarkSlot, val e: ExclusionSet, val layer: Layer,
                       val expectedTail: Tail, val expectedE: ExclusionSet, val demand: Boolean)
        val abstract = MarkSlot.Star(marks(u.id))
        val rows = listOf(
            Row(Tail.EXACT, mt, empty, Layer.NORMAL, Tail.EXACT, empty, false),
            Row(Tail.EXACT, mt, empty, Layer.DEMAND, Tail.EXACT, empty, true),
            Row(Tail.ANY, mt, ex(g), Layer.NORMAL, Tail.ANY, empty, true),
            Row(Tail.ANY, mt, ex(g), Layer.DEMAND, Tail.ANY, empty, true),
            Row(Tail.STAR, mt, ex(g), Layer.NORMAL, Tail.ANY, empty, true),
            Row(Tail.STAR, mt, ex(g), Layer.DEMAND, Tail.ANY, empty, true),
            Row(Tail.ANY_TAINT, mt, ex(g), Layer.NORMAL, Tail.ANY_TAINT, ex(g), false),
            Row(Tail.ANY_TAINT, mt, ex(g), Layer.DEMAND, Tail.ANY, empty, true),
            Row(Tail.STAR, abstract, ex(g), Layer.NORMAL, Tail.STAR, ex(g), false),
            Row(Tail.STAR, abstract, ex(g), Layer.DEMAND, Tail.ANY, empty, true),
            Row(Tail.ANY, abstract, ex(g), Layer.NORMAL, Tail.ANY, empty, true),
            Row(Tail.ANY, abstract, ex(g), Layer.DEMAND, Tail.ANY, empty, true),
        )
        for (path in listOf(emptyList(), listOf(f), listOf(element), listOf(cls), listOf(cls, f, element, g))) {
            for (row in rows) {
                val actual = manager.factsOf(PathFact(x, path, row.tail, row.mark), row.e, row.layer)
                val expected = conclusion(path, row.expectedTail, row.mark, row.expectedE, row.demand)
                assertEquals(setOf(expected), decode(actual), "$path on $row")
                assertEquals(if (row.demand) Layer.DEMAND else Layer.NORMAL, actual.layer)
                val kind = if (row.mark is MarkSlot.Star) FactsKind.FLOW else FactsKind.TAINT
                val mx = if (row.mark is MarkSlot.Star) row.mark.excluded else MarkSet.EMPTY
                assertEquals(GroupKey(kind, actual.layer, x, row.expectedE, mx), actual.groupKey)
            }
        }
    }

    @Test
    fun `typed factories reject abstract exact and abstract must without widening`() {
        for (tail in listOf(Tail.EXACT, Tail.ANY_TAINT)) {
            for (mark in listOf(MarkSlot.STAR, MarkSlot.Star(marks(t.id)))) {
                for (layer in Layer.entries) {
                    for (path in listOf(emptyList(), listOf(cls, f, element))) {
                        assertFails { manager.factsOf(PathFact(x, path, tail, mark), empty, layer) }
                    }
                }
            }
        }
    }

    @Test
    fun `zero facts use exactly two canonical reach values`() {
        val zero = PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
        assertSame(Reach.NORMAL, Reach.of(Layer.NORMAL))
        assertSame(Reach.DEMAND, Reach.of(Layer.DEMAND))
        val normal = manager.factsOf(zero, empty, Layer.NORMAL)
        val demand = manager.factsOf(zero, empty, Layer.DEMAND)
        assertSame(Reach.NORMAL, normal)
        assertSame(Reach.DEMAND, demand)
        assertNotSame(normal, demand)
        assertNotEquals(normal, demand)
        assertEquals(2, setOf(normal, demand, Reach.NORMAL, Reach.DEMAND).size)
        for (facts in listOf(normal, demand)) {
            assertEquals(AccessPathBase.Zero, facts.base)
            assertEquals(GroupKey(FactsKind.REACH, facts.layer, AccessPathBase.Zero), facts.groupKey)
            assertEquals(setOf(conclusion(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO),
                demand = facts.layer == Layer.DEMAND, base = AccessPathBase.Zero)), decode(facts))
        }
    }

    @Test
    fun `flow and taint constructors and factories reject the zero base`() {
        val flowRoot = manager.flowAlg.LEAF_NODE
        val taintRoot = manager.taintAlg.leafNode(manager.taintLeaves(marks(TaintMark.ZERO.id), marks()))
        for (layer in Layer.entries) {
            assertFails { manager.flowTree(AccessPathBase.Zero, layer, empty, marks(), flowRoot) }
            assertFails { manager.taintTree(AccessPathBase.Zero, layer, taintRoot) }
            assertFails { FlowTree(AccessPathBase.Zero, layer, empty, marks(), flowRoot) }
            assertFails { TaintTree(AccessPathBase.Zero, layer, taintRoot) }
        }
    }

    @Test
    fun `raw zero factories reject malformed geometry exclusions and marks`() {
        val zero = PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
        val malformed = listOf(
            zero.copy(path = listOf(f)) to empty,
            zero.copy(path = listOf(cls)) to empty,
            zero.copy(tail = Tail.ANY) to empty,
            zero.copy(tail = Tail.ANY_TAINT) to empty,
            zero.copy(tail = Tail.STAR) to empty,
            zero.copy(mark = mt) to empty,
            zero.copy(mark = MarkSlot.STAR) to empty,
            zero.copy(tail = Tail.STAR, mark = MarkSlot.STAR) to empty,
            zero.copy(tail = Tail.ANY, mark = MarkSlot.STAR) to empty,
            zero to ex(f),
        )
        for (layer in Layer.entries) {
            for ((fact, exclusion) in malformed) {
                assertFails("$layer on $fact / $exclusion") { manager.factsOf(fact, exclusion, layer) }
            }
        }
    }

    @Test
    fun `fact factories reject roots with no leaves`() {
        val emptyFlow = manager.flowAlg.leafNode(FlowLeaf.NONE)
        val emptyTaint = manager.taintAlg.leafNode(TaintLeaves.EMPTY)
        assertTrue(emptyFlow.isEmpty)
        assertTrue(emptyTaint.isEmpty)
        for (layer in Layer.entries) {
            assertFails { manager.flowTree(x, layer, empty, marks(), emptyFlow) }
            assertFails { manager.taintTree(x, layer, emptyTaint) }
        }
    }

    @Test
    fun `group keys retain kind layer base and the applicable exclusions but omit root content`() {
        val fRoot = chain(manager.flowAlg, listOf(f), FlowLeaf.LEAF)
        val gRoot = chain(manager.flowAlg, listOf(g), FlowLeaf.LEAF)
        val flow = manager.flowTree(x, Layer.NORMAL, ex(f), marks(u.id), fRoot)
        assertEquals(GroupKey(FactsKind.FLOW, Layer.NORMAL, x, ex(f), marks(u.id)), flow.groupKey)
        assertEquals(flow.groupKey, manager.flowTree(x, Layer.NORMAL, ex(f), marks(u.id), gRoot).groupKey)
        assertNotEquals(flow.groupKey, manager.flowTree(y, Layer.NORMAL, ex(f), marks(u.id), fRoot).groupKey)
        assertNotEquals(flow.groupKey, manager.flowTree(x, Layer.NORMAL, ex(g), marks(u.id), fRoot).groupKey)
        assertNotEquals(flow.groupKey, manager.flowTree(x, Layer.NORMAL, ex(f), marks(t.id), fRoot).groupKey)
        assertNotEquals(flow.groupKey, manager.flowTree(x, Layer.DEMAND, ex(f), marks(u.id), fRoot).groupKey)
        val anyT = manager.taintAlg.leafNode(manager.taintLeaves(marks(), marks(t.id)))
        val anyU = manager.taintAlg.leafNode(manager.taintLeaves(marks(), marks(u.id)))
        val taint = manager.taintTree(x, Layer.NORMAL, anyT, ex(f))
        assertEquals(GroupKey(FactsKind.TAINT, Layer.NORMAL, x, ex(f)), taint.groupKey)
        assertEquals(taint.groupKey, manager.taintTree(x, Layer.NORMAL, anyU, ex(f)).groupKey)
        assertNotEquals(taint.groupKey, manager.taintTree(x, Layer.NORMAL, anyT, ex(g)).groupKey)
        assertNotEquals(taint.groupKey, manager.taintTree(y, Layer.NORMAL, anyT, ex(f)).groupKey)
        assertNotEquals(taint.groupKey, manager.taintTree(x, Layer.DEMAND, anyT, ex(f)).groupKey)
        assertNotEquals(taint.groupKey, manager.flowTree(x, Layer.NORMAL, ex(f), marks(), fRoot).groupKey)
    }

    @Test
    fun `facts equality compares separate sparse roots by value and preserves all annotations`() {
        val firstRoot = chain(manager.flowAlg, listOf(cls, f, element), FlowLeaf.LEAF)
        val secondRoot = chain(manager.flowAlg, listOf(cls, f, element), FlowLeaf.LEAF)
        assertNotSame(firstRoot, secondRoot)
        val first = manager.flowTree(x, Layer.NORMAL, ex(g), marks(u.id), firstRoot)
        val second = manager.flowTree(x, Layer.NORMAL, ex(g), marks(u.id), secondRoot)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, manager.flowTree(y, Layer.NORMAL, ex(g), marks(u.id), secondRoot))
        assertNotEquals(first, manager.flowTree(x, Layer.NORMAL, ex(f), marks(u.id), secondRoot))
        assertNotEquals(first, manager.flowTree(x, Layer.NORMAL, ex(g), marks(t.id), secondRoot))
        assertNotEquals(first, manager.flowTree(x, Layer.DEMAND, empty, marks(u.id), secondRoot))
        assertNotEquals(first, manager.flowTree(x, Layer.NORMAL, ex(g), marks(u.id), manager.flowAlg.LEAF_NODE))
        val firstTaint = chain(manager.taintAlg, listOf(cls, f, element), manager.taintLeaves(marks(t.id), marks(u.id)))
        val secondTaint = chain(manager.taintAlg, listOf(cls, f, element), manager.taintLeaves(marks(t.id), marks(u.id)))
        assertNotSame(firstTaint, secondTaint)
        val taint = manager.taintTree(x, Layer.NORMAL, firstTaint, ex(g))
        val sameTaint = manager.taintTree(x, Layer.NORMAL, secondTaint, ex(g))
        assertEquals(taint, sameTaint)
        assertEquals(taint.hashCode(), sameTaint.hashCode())
        assertNotEquals(taint, manager.taintTree(y, Layer.NORMAL, secondTaint, ex(g)))
        assertNotEquals(taint, manager.taintTree(x, Layer.NORMAL, secondTaint, ex(f)))
        assertNotEquals(taint, manager.taintTree(x, Layer.DEMAND, secondTaint))
        assertNotEquals(taint, manager.taintTree(x, Layer.NORMAL,
            chain(manager.taintAlg, listOf(cls, f, element), manager.taintLeaves(marks(u.id), marks(t.id))), ex(g)))
        assertNotEquals(taint, manager.taintTree(x, Layer.NORMAL,
            chain(manager.taintAlg, listOf(f), manager.taintLeaves(marks(t.id), marks(u.id))), ex(g)))
    }

    private data class FactorySample(
        val leaf: TaintLeaves,
        val leafNode: TaintNode,
        val normalFlow: FlowTree,
        val demandFlow: FlowTree,
        val normalTaint: TaintTree,
        val demandTaint: TaintTree,
        val exact: TaintTree,
        val normalReach: Facts,
        val demandReach: Facts,
    )

    @Test
    fun `concurrent manager factories retain canonical leaves roots and annotations`() {
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val jobs = (0 until 8).map {
                pool.submit(Callable {
                    start.await()
                    List(20) {
                        val leaf = manager.taintLeaves(marks(t.id, t.id), marks(u.id))
                        val leafNode = manager.taintAlg.leafNode(leaf)
                        val mx = marks(u.id, t.id, u.id)
                        val e = ex(g, f, g)
                        val zero = PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
                        FactorySample(leaf, leafNode,
                            manager.flowTree(x, Layer.NORMAL, e, mx, manager.flowAlg.LEAF_NODE),
                            manager.factsOf(PathFact(x, emptyList(), Tail.STAR, MarkSlot.Star(mx)), e, Layer.DEMAND) as FlowTree,
                            manager.taintTree(x, Layer.NORMAL, leafNode, e),
                            manager.taintTree(x, Layer.DEMAND, leafNode, e),
                            manager.factsOf(PathFact(x, emptyList(), Tail.EXACT, mt), empty, Layer.NORMAL) as TaintTree,
                            manager.factsOf(zero, empty, Layer.NORMAL),
                            manager.factsOf(zero, empty, Layer.DEMAND))
                    }
                })
            }
            start.countDown()
            val samples = jobs.flatMap { it.get(30, TimeUnit.SECONDS) }
            val first = samples.first()
            for (sample in samples) {
                assertSame(first.leaf, sample.leaf)
                assertSame(first.leafNode, sample.leafNode)
                assertSame(first.leaf.exact, sample.leaf.exact)
                assertSame(first.leaf.any, sample.leaf.any)
                assertSame(first.normalFlow.root, sample.normalFlow.root)
                assertSame(first.demandFlow.root, sample.demandFlow.root)
                assertSame(first.normalFlow.exclusion, sample.normalFlow.exclusion)
                assertSame(first.normalFlow.markExclusion, sample.normalFlow.markExclusion)
                assertSame(first.demandFlow.markExclusion, sample.demandFlow.markExclusion)
                assertSame(empty, sample.demandFlow.exclusion)
                assertSame(first.normalTaint.root, sample.normalTaint.root)
                assertSame(first.demandTaint.root, sample.demandTaint.root)
                assertSame(first.normalTaint.exclusion, sample.normalTaint.exclusion)
                assertSame(empty, sample.demandTaint.exclusion)
                assertSame(first.exact.root, sample.exact.root)
                assertSame(Reach.NORMAL, sample.normalReach)
                assertSame(Reach.DEMAND, sample.demandReach)
                assertEquals(first.normalFlow, sample.normalFlow)
                assertEquals(first.demandFlow, sample.demandFlow)
                assertEquals(first.normalTaint, sample.normalTaint)
                assertEquals(first.demandTaint, sample.demandTaint)
                assertEquals(first.exact, sample.exact)
            }
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }
}
