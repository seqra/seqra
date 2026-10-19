package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Structural demand selection reads raw headers before restriction or native read-view conversion. */
class DemandSelectionTest {
    private val manager = testApManager(Cancellation())
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val empty = ExclusionSet.Empty
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun p(path: List<Int>, tail: Tail, mark: MarkSlot = MarkSlot.STAR,
                  exclusion: ExclusionSet = empty, base: AccessPathBase = x) =
        Pattern(PathFact(base, path, tail, mark), exclusion)
    private fun initial(path: List<Int>, tail: Tail, mark: MarkSlot = MarkSlot.STAR,
                        exclusion: ExclusionSet = empty, base: AccessPathBase = x) =
        manager.initial(p(path, tail, mark, exclusion, base))
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
    private fun decode(value: Facts): Set<Conclusion> = buildSet {
        val demand = value.layer == Layer.DEMAND
        fun put(path: List<Int>, tail: Tail, mark: MarkSlot, exclusion: ExclusionSet = empty) {
            add(Conclusion(PathFact(value.base, path, tail, mark), exclusion, demand))
        }
        when (value) {
            is Reach -> put(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
            is FlowTree -> walk(value.root) { path, _ ->
                put(path, if (demand) Tail.ANY else Tail.STAR, MarkSlot.Star(value.markExclusion), value.exclusion)
            }
            is TaintTree -> walk(value.root) { path, leaf ->
                for (id in leaf.exact.ids) put(path, Tail.EXACT, MarkSlot.Concrete(TaintMark(id)))
                for (id in leaf.any.ids) put(path, if (demand) Tail.ANY else Tail.ANY_TAINT,
                    MarkSlot.Concrete(TaintMark(id)), value.exclusion)
            }
        }
    }
    private fun expected(premise: PremiseKey, summary: Facts, direction: Direction): Boolean =
        summary.layer == Layer.DEMAND || premise.size > 1 ||
            (direction == Direction.BACKWARD && premise.isZero)
    private fun check(premise: PremiseKey, summary: Facts, direction: Direction) {
        val selected = expected(premise, summary, direction)
        val actual = ops.demandPart(premise, summary, direction)
        if (selected) assertSame(summary, actual) else assertNull(actual)
        for (leaf in decode(summary)) {
            assertEquals(selected, demandSelected(premise, leaf, direction))
            val eligible = !leaf.demand && premise is InitialAp &&
                !(direction == Direction.BACKWARD && premise.isZero)
            assertEquals(eligible, r1Eligible(premise, leaf, direction))
            assertEquals(!selected, eligible)
        }
    }

    @Test
    fun `raw layer and backward zero select whole summary values`() {
        val initial = manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
            ExclusionSet.Empty, MarkSlot.STAR)
        assertNull(ops.demandPart(initial, Reach.NORMAL, Direction.FORWARD))
        assertSame(Reach.DEMAND, ops.demandPart(initial, Reach.DEMAND, Direction.FORWARD))
        assertNull(ops.demandPart(manager.zero, Reach.NORMAL, Direction.FORWARD))
        assertSame(Reach.NORMAL, ops.demandPart(manager.zero, Reach.NORMAL, Direction.BACKWARD))
    }

    @Test
    fun `all structural cells depend only on raw layer premise count and backward zero status`() {
        val exact = initial(listOf(f), Tail.EXACT, MarkSlot.Concrete(t))
        val must = initial(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(u), ex(g))
        val second = initial(listOf(g), Tail.ANY, MarkSlot.Concrete(u), base = y)
        val premises = listOf<PremiseKey>(manager.zero,
            initial(emptyList(), Tail.STAR), initial(listOf(f), Tail.STAR, exclusion = ex(g)),
            initial(listOf(f), Tail.ANY), exact, must,
            initial(emptyList(), Tail.STAR, MarkSlot.Concrete(t)), second,
            manager.premiseOf(listOf(exact, second)), manager.premiseOf(listOf(exact, must, second)))
        val exactLeaf = manager.taintLeaves(manager.markSetOf(u), MarkSet.EMPTY)
        val mixedLeaf = manager.taintLeaves(manager.markSetOf(t), manager.markSetOf(u))
        val taintRoot = manager.taintAlg.node(mixedLeaf, intArrayOf(f), arrayOf(manager.taintAlg.leafNode(exactLeaf)))
        val flowRoot = manager.flowAlg.node(FlowLeaf.LEAF, intArrayOf(f), arrayOf(FlowAlgebra.LEAF_NODE))
        val summaries = listOf<Facts>(Reach.NORMAL, Reach.DEMAND) + Layer.entries.flatMap { layer ->
            listOf(manager.flowTree(y, layer, ex(g), manager.markSetOf(u), flowRoot),
                manager.taintTree(y, layer, taintRoot, ex(g)),
                manager.taintTree(y, layer, manager.taintAlg.leafNode(exactLeaf)))
        }
        for (premise in premises) for (summary in summaries) for (direction in Direction.entries)
            check(premise, summary, direction)
    }

    @Test
    fun `native must records stay normal singleton records even when a transient view returns demand`() {
        val exact = p(listOf(f), Tail.EXACT, MarkSlot.Concrete(u))
        val must = p(listOf(f), Tail.ANY_TAINT, MarkSlot.Concrete(u), ex(g))
        val targetMust = Conclusion(PathFact(y, listOf(g), Tail.ANY_TAINT, MarkSlot.Concrete(t)), ex(h), false)
        val targetExact = Conclusion(PathFact(y, listOf(g), Tail.EXACT, MarkSlot.Concrete(t)), empty, false)
        for ((source, target) in listOf(exact to targetMust, must to targetMust, must to targetExact)) {
            val premise = manager.initial(source)
            val native = manager.factsOf(target.fact, target.exclusion, Layer.NORMAL)
            val view = requireNotNull(reverseRecordLeaf(Direction.FORWARD, source, target))
            assertTrue(r1Eligible(premise, target, Direction.FORWARD))
            assertFalse(demandSelected(premise, target, Direction.FORWARD))
            assertNull(ops.demandPart(premise, native, Direction.FORWARD))
            if (source.fact.tail == Tail.ANY_TAINT) assertTrue(view.result.demand)
            else assertFalse(view.result.demand)
            assertEquals(Layer.NORMAL, native.layer)
            assertEquals(target.exclusion, (native as TaintTree).exclusion)
        }
    }

    @Test
    fun `restriction can change result paths and E without changing raw structural selection`() {
        val single = initial(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t), ex(g))
        val root = manager.taintAlg.node(manager.taintLeaves(MarkSet.EMPTY, manager.markSetOf(t)), intArrayOf(f),
            arrayOf(manager.taintAlg.leafNode(manager.taintLeaves(MarkSet.EMPTY, manager.markSetOf(t)))))
        val normal = manager.taintTree(y, Layer.NORMAL, root, ex(g))
        val demand = DemandPattern(p(emptyList(), Tail.ANY), p(listOf(f), Tail.STAR, exclusion = ex(h), base = y))
        assertNull(ops.demandPart(single, normal, Direction.FORWARD))
        val narrowed = ops.restrict(single, normal, demand)
        assertEquals(setOf(ex(h), ex(g, h)), narrowed.map { (it as TaintTree).exclusion }.toSet())
        for (piece in narrowed) {
            assertEquals(Layer.NORMAL, piece.layer)
            assertNull(ops.demandPart(single, piece, Direction.FORWARD))
        }
        val second = initial(listOf(g), Tail.EXACT, MarkSlot.Concrete(u), base = y)
        val several = manager.premiseOf(listOf(single, second))
        assertSame(normal, ops.demandPart(several, normal, Direction.FORWARD))
        for (piece in narrowed) assertSame(piece, ops.demandPart(several, piece, Direction.FORWARD))
    }

    @Test
    fun `selection returns or rejects a huge shared DAG without reading or folding its leaves`() {
        var flowRoot = FlowAlgebra.LEAF_NODE
        val leaves = manager.taintLeaves(manager.markSetOf(t), manager.markSetOf(u))
        var taintRoot = manager.taintAlg.leafNode(leaves)
        repeat(62) {
            flowRoot = manager.flowAlg.node(FlowLeaf.NONE, intArrayOf(f, g), arrayOf(flowRoot, flowRoot))
            taintRoot = manager.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(f, g), arrayOf(taintRoot, taintRoot))
        }
        assertEquals(Long.MAX_VALUE, flowRoot.size)
        assertEquals(Long.MAX_VALUE, taintRoot.size)
        val flowPremise = initial(emptyList(), Tail.STAR)
        val taintPremise = initial(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t), ex(g))
        val second = initial(listOf(g), Tail.EXACT, MarkSlot.Concrete(u), base = y)
        val several = manager.premiseOf(listOf(taintPremise, second))
        for (direction in Direction.entries) for (layer in Layer.entries) {
            val flag = manager.flowTree(y, layer, ex(g), manager.markSetOf(u), flowRoot)
            val taint = manager.taintTree(y, layer, taintRoot, ex(g))
            if (layer == Layer.DEMAND) {
                assertSame(flag, ops.demandPart(flowPremise, flag, direction))
                assertSame(taint, ops.demandPart(taintPremise, taint, direction))
            } else {
                assertNull(ops.demandPart(flowPremise, flag, direction))
                assertNull(ops.demandPart(taintPremise, taint, direction))
            }
            assertSame(taint, ops.demandPart(several, taint, direction))
            assertNull(flowRoot.allLeaves)
            assertNull(taintRoot.allLeaves)
        }
        val seed = manager.taintTree(y, Layer.NORMAL, taintRoot, ex(g))
        assertSame(seed, ops.demandPart(manager.zero, seed, Direction.BACKWARD))
        assertNull(ops.demandPart(manager.zero, seed, Direction.FORWARD))
        assertNull(taintRoot.allLeaves)
    }

    @Test
    fun `deep sparse summaries keep their original layer annotations and root identity`() {
        val depth = 32768
        val leaves = manager.taintLeaves(manager.markSetOf(t), manager.markSetOf(u))
        var root = manager.taintAlg.leafNode(leaves)
        repeat(depth) { root = manager.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(f), arrayOf(root)) }
        val single = initial(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t), ex(g))
        val second = initial(listOf(f), Tail.EXACT, MarkSlot.Concrete(u), base = y)
        val several = manager.premiseOf(listOf(single, second))
        val normal = manager.taintTree(y, Layer.NORMAL, root, ex(g))
        val demand = manager.taintTree(y, Layer.DEMAND, root)
        assertNull(ops.demandPart(single, normal, Direction.FORWARD))
        val selectedNormal = requireNotNull(ops.demandPart(several, normal, Direction.FORWARD)) as TaintTree
        assertSame(normal, selectedNormal)
        assertSame(root, selectedNormal.root)
        assertEquals(ex(g), selectedNormal.exclusion)
        assertEquals(Layer.NORMAL, selectedNormal.layer)
        assertSame(demand, ops.demandPart(single, demand, Direction.FORWARD))
        assertEquals(empty, demand.exclusion)
        assertNull(root.allLeaves)
    }
}
