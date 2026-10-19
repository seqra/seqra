package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Raw input leaves and local typed-tree decoding make the per-leaf reference independent of trie cutting. */
class FieldLimitTest {
    private val manager = testApManager(Cancellation())
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val c = manager.accessors.index(ClassStaticAccessor("Box"))
    private val otherClass = manager.accessors.index(ClassStaticAccessor("Other"))
    private val element = manager.accessors.index(ElementAccessor)
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private fun p(vararg a: Int) = a.toList()
    private fun ex(vararg a: Int) = ExclusionSet.of(a)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private data class Input(val facts: Facts, val leaves: List<Conclusion>)

    @Test
    fun `every public kind rejects zero and negative field limits before emitting results`() {
        val inputs = listOf(
            Reach.NORMAL, Reach.DEMAND,
            pack(listOf(value(p(f), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(g)))).facts,
            pack(listOf(value(p(f), Tail.ANY, MarkSlot.STAR))).facts,
            pack(listOf(value(p(f), Tail.EXACT))).facts,
            pack(listOf(value(p(f), Tail.ANY_TAINT, exclusion = ex(g)))).facts,
            pack(listOf(value(p(f), Tail.EXACT, demand = true), value(p(g), Tail.ANY, mu))).facts,
        )
        for (input in inputs) for (invalid in listOf(0, -1, Int.MIN_VALUE)) {
            val out = CollectingOut()
            assertFailsWith<IllegalArgumentException>("$input, limit $invalid") { ops.limit(input, invalid, out) }
            assertEmptyRequests(out)
            assertTrue(out.results.isEmpty())
        }
    }

    @Test
    fun `reach is unchanged at every positive public limit and layer`() {
        for (input in listOf(Reach.NORMAL, Reach.DEMAND)) for (budget in listOf(1, 2, Int.MAX_VALUE)) {
            val out = CollectingOut()
            ops.limit(input, budget, out)
            assertSame(input, out.results.single())
            assertEmptyRequests(out)
        }
    }

    @Test
    fun `positive limit one cuts before the second field keeps marks and drops must exclusions`() {
        val leaves = listOf(
            value(p(f, g), Tail.EXACT),
            value(p(f, g), Tail.ANY_TAINT, mu, ex(h)),
            value(p(f, g), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(h)),
        )
        for (leaf in leaves) {
            val out = check(pack(listOf(leaf)), 1)
            assertEquals(setOf(value(p(f), Tail.ANY, leaf.fact.mark)), decode(out.results))
            assertEquals(Layer.DEMAND, out.results.single().layer)
        }
    }

    @Test
    fun `retained mixed and fully cut trees match per-leaf limit across kinds layers marks and exclusions`() {
        val paths = listOf(p(), p(f), p(f, g), p(f, g, h), p(f, f, f), p(element, element),
            p(c), p(c, f), p(f, c, otherClass, g), p(c, element, g, element))
        val inputs = buildList {
            for (layer in Layer.entries) {
                val demand = layer == Layer.DEMAND
                for (e in if (demand) listOf(ExclusionSet.Empty) else listOf(ExclusionSet.Empty, ex(g, h))) {
                    for (mx in listOf(MarkSet.EMPTY, marks(t.id, u.id))) {
                        add(pack(paths.map { value(it, if (demand) Tail.ANY else Tail.STAR, MarkSlot.Star(mx), e, demand) }))
                    }
                    add(pack(paths.flatMap { path -> listOf(
                        value(path, Tail.EXACT, mt, demand = demand),
                        value(path, Tail.EXACT, mu, demand = demand),
                        value(path, if (demand) Tail.ANY else Tail.ANY_TAINT, mt, e, demand),
                        value(path, if (demand) Tail.ANY else Tail.ANY_TAINT, mu, e, demand),
                    ) }))
                    add(pack(paths.map { value(it, if (demand) Tail.ANY else Tail.ANY_TAINT, mu, e, demand) }))
                }
                add(pack(paths.map { value(it, Tail.EXACT, mt, demand = demand) }))
            }
        }
        for (input in inputs) for (budget in listOf(1, 2, 3, 4, Int.MAX_VALUE)) check(input, budget)
    }

    @Test
    fun `repeated fields and every element accessor consume the budget independently`() {
        val paths = listOf(p(f, f, f), p(element, element, element), p(f, element, f, element))
        for (path in paths) {
            val input = pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(h))))
            for (budget in 1 until path.size) {
                val out = check(input, budget)
                assertEquals(setOf(value(path.take(budget), Tail.ANY)), decode(out.results))
            }
            assertSame(input.facts, check(input, path.size).results.single())
        }
    }

    @Test
    fun `class prefixes remain after the last counted accessor and before the next cut`() {
        val paths = listOf(p(c, otherClass, f, c, otherClass, g), p(f, c, otherClass, g), p(c, f, c, otherClass))
        for (path in paths) {
            val input = pack(listOf(value(path, Tail.EXACT)))
            val out = check(input, 1)
            val firstOverflow = path.indexOfLast { it == g }
            if (firstOverflow >= 0) {
                assertEquals(setOf(value(path.take(firstOverflow), Tail.ANY)), decode(out.results))
            } else {
                assertSame(input.facts, out.results.single())
            }
        }
        val input = pack(listOf(value(p(f, c, otherClass), Tail.EXACT),
            value(p(f, c, otherClass, g), Tail.ANY_TAINT, mu, ex(h))))
        assertEquals(setOf(value(p(f, c, otherClass), Tail.EXACT), value(p(f, c, otherClass), Tail.ANY, mu)),
            decode(check(input, 1).results))
    }

    @Test
    fun `internal zero remaining budget retains class edges and folds at the first counted edge`() {
        val input = pack(listOf(value(p(c), Tail.EXACT, mu), value(p(c, otherClass, f, g), Tail.ANY_TAINT, mt, ex(h))))
        val root = (input.facts as TaintTree).root
        val (kept, cut) = FieldLimitCut(manager.taintAlg, manager).keep(root, 0)
        assertEquals(setOf(value(p(c), Tail.EXACT, mu)),
            decode(listOf(manager.taintTree(x, Layer.NORMAL, assertNotNull(kept)))))
        assertEquals(setOf(value(p(c, otherClass), Tail.ANY, mt)),
            decode(listOf(manager.taintTree(x, Layer.DEMAND, assertNotNull(cut)))))
        assertEquals(0, kept.boundedDepth)
        assertEquals(0, cut.boundedDepth)
    }

    @Test
    fun `fully fitting metadata boundary returns the exact original facts and mixed cuts share untouched children`() {
        val inputs = listOf(
            pack(listOf(value(p(f, c, g), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(h)),
                value(p(h), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(h)))),
            pack(listOf(value(p(f, c, g), Tail.ANY_TAINT, mt, ex(h)), value(p(h), Tail.EXACT, mu))),
            pack(listOf(value(p(f, c, g), Tail.ANY, mt), value(p(h), Tail.EXACT, mu, demand = true))),
        )
        for (input in inputs) {
            val root = when (val facts = input.facts) { is FlowTree -> facts.root; is TaintTree -> facts.root; else -> error("tree") }
            assertEquals(2, root.boundedDepth)
            assertSame(input.facts, check(input, 2).results.single())
            val out = check(input, 1)
            val kept = out.results.first { decode(listOf(it)).any { leaf -> leaf.fact.path == p(h) } }
            val keptRoot = when (kept) { is FlowTree -> kept.root; is TaintTree -> kept.root; else -> error("tree") }
            assertSame(root.child(h), keptRoot.child(h))
            assertEquals(1, keptRoot.boundedDepth)
        }
    }

    @Test
    fun `keeping only exact leaves clears the old must exclusion while retained must leaves keep it`() {
        val exactOnlyKept = pack(listOf(value(p(f), Tail.EXACT), value(p(f, g), Tail.ANY_TAINT, mu, ex(h))))
        val out = check(exactOnlyKept, 1)
        val kept = out.results.filterIsInstance<TaintTree>().single { it.layer == Layer.NORMAL }
        assertEquals(ExclusionSet.Empty, kept.exclusion)
        assertEquals(setOf(value(p(f), Tail.EXACT)), decode(listOf(kept)))
        val mustKept = pack(exactOnlyKept.leaves + value(p(h), Tail.ANY_TAINT, mt, ex(h)))
        val retained = check(mustKept, 1).results.filterIsInstance<TaintTree>().single { it.layer == Layer.NORMAL }
        assertEquals(ex(h), retained.exclusion)
    }

    @Test
    fun `shared subtrees are cut separately for each remaining budget`() {
        val alg = manager.taintAlg
        val exactT = alg.leafNode(manager.taintLeaves(manager.markSetOf(t), MarkSet.EMPTY))
        val exactU = manager.taintLeaves(manager.markSetOf(u), MarkSet.EMPTY)
        val shared = branch(alg, exactU, g to branch(alg, TaintLeaves.EMPTY, h to exactT))
        val root = branch(alg, TaintLeaves.EMPTY, c to shared, f to shared)
        assertSame(root.child(c), root.child(f))
        val input = Input(manager.taintTree(x, Layer.NORMAL, root), listOf(
            value(p(c), Tail.EXACT, mu), value(p(c, g, h), Tail.EXACT),
            value(p(f), Tail.EXACT, mu), value(p(f, g, h), Tail.EXACT),
        ))
        val out = check(input, 1)
        assertEquals(setOf(value(p(c), Tail.EXACT, mu), value(p(f), Tail.EXACT, mu),
            value(p(c, g), Tail.ANY), value(p(f), Tail.ANY)), decode(out.results))
        assertSame(shared, root.child(c))
        assertSame(shared, root.child(f))

        val flowShared = branch(FlowAlgebra, FlowLeaf.LEAF,
            g to branch(FlowAlgebra, FlowLeaf.NONE, h to FlowAlgebra.LEAF_NODE))
        val flowRoot = branch(FlowAlgebra, FlowLeaf.NONE, c to flowShared, f to flowShared)
        check(Input(manager.flowTree(x, Layer.NORMAL, ex(h), marks(u.id), flowRoot), input.leaves.map {
            value(it.fact.path, Tail.STAR, MarkSlot.Star(marks(u.id)), ex(h))
        }), 1)
    }

    @Test
    fun `32768 depth paths use the all-fit identity shortcut and cut without recursion`() {
        val depth = 32768
        val exact = manager.taintLeaves(manager.markSetOf(t), MarkSet.EMPTY)
        val flowRoot = spine(FlowAlgebra, FlowLeaf.LEAF, f, depth)
        val taintRoot = spine(manager.taintAlg, exact, f, depth)
        val inputs = listOf(
            manager.flowTree(x, Layer.NORMAL, ex(h), marks(u.id), flowRoot),
            manager.flowTree(x, Layer.DEMAND, ExclusionSet.Empty, marks(u.id), flowRoot),
            manager.taintTree(x, Layer.NORMAL, taintRoot),
            manager.taintTree(x, Layer.DEMAND, taintRoot),
        )
        for (input in inputs) {
            val fit = CollectingOut()
            ops.limit(input, depth, fit)
            assertSame(input, fit.results.single())
            assertEmptyRequests(fit)
            val cut = CollectingOut()
            ops.limit(input, 1, cut)
            val mark = if (input is FlowTree) MarkSlot.Star(marks(u.id)) else mt
            assertEquals(setOf(value(p(f), Tail.ANY, mark)), decode(cut.results))
            assertEmptyRequests(cut)

            // The cut walker must descend through the full counted spine, rather than fold near its root.
            val nearBoundary = CollectingOut()
            ops.limit(input, depth - 1, nearBoundary)
            val result = nearBoundary.results.single()
            assertEquals(Layer.DEMAND, result.layer)
            assertEquals(x, result.base)
            when (result) {
                is FlowTree -> {
                    assertEquals(ExclusionSet.Empty, result.exclusion)
                    assertSame((input as FlowTree).markExclusion, result.markExclusion)
                    assertEquals(depth - 1, result.root.boundedDepth)
                    var at = result.root
                    repeat(depth - 1) {
                        assertEquals(FlowLeaf.NONE, at.leaf)
                        assertEquals(1, at.accessors!!.size)
                        at = assertNotNull(at.child(f))
                    }
                    assertEquals(FlowLeaf.LEAF, at.leaf)
                    assertNull(at.accessors)
                }
                is TaintTree -> {
                    assertEquals(ExclusionSet.Empty, result.exclusion)
                    assertEquals(depth - 1, result.root.boundedDepth)
                    var at = result.root
                    repeat(depth - 1) {
                        assertTrue(at.leaf.isEmpty)
                        assertEquals(1, at.accessors!!.size)
                        at = assertNotNull(at.child(f))
                    }
                    assertEquals(MarkSet.EMPTY, at.leaf.exact)
                    assertSame(manager.markSetOf(t), at.leaf.any)
                    assertNull(at.accessors)
                }
                is Reach -> error("A field cut keeps the nonzero input base")
            }
            assertEmptyRequests(nearBoundary)
        }
    }

    @Test
    fun `32768 uncounted class edges after budget zero are retained before a counted cut`() {
        val depth = 32768
        var root = branch(FlowAlgebra, FlowLeaf.NONE, g to FlowAlgebra.LEAF_NODE)
        repeat(depth) { root = branch(FlowAlgebra, FlowLeaf.NONE, c to root) }
        root = branch(FlowAlgebra, FlowLeaf.NONE, f to root)
        val input = manager.flowTree(x, Layer.NORMAL, ex(h), marks(u.id), root)
        assertEquals(2, root.boundedDepth)
        val out = CollectingOut()
        ops.limit(input, 1, out)
        val result = out.results.single() as FlowTree
        assertEquals(Layer.DEMAND, result.layer)
        assertEquals(ExclusionSet.Empty, result.exclusion)
        var at = assertNotNull(result.root.child(f))
        repeat(depth) { at = assertNotNull(at.child(c)) }
        assertEquals(FlowLeaf.LEAF, at.leaf)
        assertNull(at.child(g))
        assertEquals(1, result.root.boundedDepth)
        assertEmptyRequests(out)
    }

    @Test
    fun `cancelled limit and internal cut stop before publishing a result even on fitting inputs`() {
        val input = pack(listOf(value(p(f, g), Tail.EXACT)))
        manager.cancellation.cancel()
        for (budget in listOf(1, 2)) {
            val out = CollectingOut()
            assertFailsWith<Cancellation.Cancelled> { ops.limit(input.facts, budget, out) }
            assertTrue(out.results.isEmpty())
            assertEmptyRequests(out)
            assertFailsWith<Cancellation.Cancelled> {
                FieldLimitCut(manager.taintAlg, manager).keep((input.facts as TaintTree).root, budget)
            }
        }
        assertFailsWith<Cancellation.Cancelled> { ops.limit(Reach.NORMAL, 1, CollectingOut()) }
    }

    private fun <P : TrieLeaf> branch(alg: LeafAlgebra<P>, leaf: P,
                                     vararg children: Pair<Int, TrieNode<P>>): TrieNode<P> {
        val sorted = children.sortedBy { it.first }
        return alg.node(leaf, sorted.map { it.first }.toIntArray(), sorted.map { it.second }.toTypedArray())
    }

    private fun <P : TrieLeaf> spine(alg: LeafAlgebra<P>, leaf: P, accessor: Int, depth: Int): TrieNode<P> {
        var root = alg.leafNode(leaf)
        repeat(depth) { root = branch(alg, alg.empty, accessor to root) }
        return root
    }

    private fun value(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                      exclusion: ExclusionSet = ExclusionSet.Empty, demand: Boolean = tail == Tail.ANY): Conclusion =
        Conclusion(PathFact(x, path, tail, mark), exclusion, demand)

    private class Draft<P : TrieLeaf>(leaf: P) {
        val children = TreeMap<Int, Draft<P>>()
        var payload: P = leaf
    }

    /** Only assembles explicitly stated paths; no production merge, limit, or traversal computes the fixture. */
    private fun <P : TrieLeaf> tree(alg: LeafAlgebra<P>, entries: Map<List<Int>, P>): TrieNode<P> {
        val root = Draft(alg.empty)
        for ((path, payload) in entries) {
            var at = root
            for (accessor in path) at = at.children.getOrPut(accessor) { Draft(alg.empty) }
            at.payload = payload
        }
        val pending = ArrayDeque<Pair<Draft<P>, Boolean>>()
        val built = IdentityHashMap<Draft<P>, TrieNode<P>>()
        pending.addLast(root to false)
        while (pending.isNotEmpty()) {
            val (at, finish) = pending.removeLast()
            if (!finish) {
                pending.addLast(at to true)
                at.children.values.forEach { pending.addLast(it to false) }
            } else {
                built[at] = alg.node(at.payload, at.children.keys.toIntArray(),
                    at.children.values.map { built.getValue(it) }.toTypedArray())
            }
        }
        return built.getValue(root)
    }

    private fun pack(leaves: List<Conclusion>): Input {
        require(leaves.isNotEmpty())
        val first = leaves.first()
        require(leaves.all { it.fact.base == x && it.demand == first.demand })
        val layer = if (first.demand) Layer.DEMAND else Layer.NORMAL
        val facts = when (val mark = first.fact.mark) {
            is MarkSlot.Star -> {
                require(leaves.all { it.fact.mark == mark && it.exclusion == first.exclusion })
                manager.flowTree(x, layer, first.exclusion, mark.excluded,
                    tree(FlowAlgebra, leaves.associate { it.fact.path to FlowLeaf.LEAF }))
            }
            is MarkSlot.Concrete -> {
                val grouped = leaves.groupBy { it.fact.path }.mapValues { (_, values) ->
                    val exact = values.filter { it.fact.tail == Tail.EXACT }.map { (it.fact.mark as MarkSlot.Concrete).mark.id }
                    val any = values.filter { it.fact.tail != Tail.EXACT }.map { (it.fact.mark as MarkSlot.Concrete).mark.id }
                    manager.taintLeaves(MarkSet(exact.toIntArray()), MarkSet(any.toIntArray()))
                }
                val exclusion = leaves.firstOrNull { it.fact.tail == Tail.ANY_TAINT }?.exclusion ?: ExclusionSet.Empty
                require(leaves.filter { it.fact.tail == Tail.ANY_TAINT }.all { it.exclusion == exclusion })
                manager.taintTree(x, layer, tree(manager.taintAlg, grouped), exclusion)
            }
        }
        return Input(facts, leaves)
    }

    private fun <P : TrieLeaf> visit(root: TrieNode<P>, action: (List<Int>, P) -> Unit) {
        val pending = ArrayDeque<Pair<TrieNode<P>, List<Int>>>()
        pending.addLast(root to emptyList())
        while (pending.isNotEmpty()) {
            val (node, path) = pending.removeLast()
            if (!node.leaf.isEmpty) action(path, node.leaf)
            val accessors = node.accessors ?: continue
            val children = node.children!!
            for (i in accessors.indices) pending.addLast(children[i] to (path + accessors[i]))
        }
    }

    private fun decode(facts: List<Facts>): Set<Conclusion> = buildSet {
        for (fact in facts) when (fact) {
            is Reach -> add(Conclusion(PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT,
                MarkSlot.Concrete(TaintMark.ZERO)), ExclusionSet.Empty, fact.layer == Layer.DEMAND))
            is FlowTree -> visit(fact.root) { path, _ ->
                add(Conclusion(PathFact(fact.base, path, if (fact.layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                    MarkSlot.Star(fact.markExclusion)), fact.exclusion, fact.layer == Layer.DEMAND))
            }
            is TaintTree -> visit(fact.root) { path, payload ->
                for (id in payload.exact.ids) add(Conclusion(PathFact(fact.base, path, Tail.EXACT,
                    MarkSlot.Concrete(TaintMark(id))), ExclusionSet.Empty, fact.layer == Layer.DEMAND))
                for (id in payload.any.ids) add(Conclusion(PathFact(fact.base, path,
                    if (fact.layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY, MarkSlot.Concrete(TaintMark(id))),
                    fact.exclusion, fact.layer == Layer.DEMAND))
            }
        }
    }

    private fun check(input: Input, budget: Int): CollectingOut {
        val out = CollectingOut()
        ops.limit(input.facts, budget, out)
        assertEquals(input.leaves.map { limit(it, budget) }.toSet(), decode(out.results), "limit $budget")
        assertEmptyRequests(out)
        return out
    }

    private fun assertEmptyRequests(out: CollectingOut) {
        assertTrue(out.markRequests.isEmpty())
    }
}
