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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Reduction uses a local raw decoder; packing preserves every complete annotation key. */
class RestrictionTest {
    private val cancellation = Cancellation()
    private val manager = ApManager(cancellation)
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val z = AccessPathBase.Argument(1)
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val cls = manager.accessors.index(ClassStaticAccessor("Box"))
    private val element = manager.accessors.index(ElementAccessor)
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val fresh = manager.marks.mark("Fresh")
    private val empty = ExclusionSet.Empty
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun p(path: List<Int>, tail: Tail, mark: MarkSlot = MarkSlot.STAR,
                  exclusion: ExclusionSet = empty, base: AccessPathBase = x) =
        Pattern(PathFact(base, path, tail, mark), exclusion)
    private fun c(path: List<Int>, tail: Tail, mark: TaintMark = t,
                  exclusion: ExclusionSet = empty, layer: Layer = Layer.NORMAL, base: AccessPathBase = y) =
        Conclusion(PathFact(base, path, tail, MarkSlot.Concrete(mark)), exclusion, layer == Layer.DEMAND)
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
        return manager.flowTree(y, layer, exclusion, mx,
            build(root, manager.flowAlg) { if (it.flag) FlowLeaf.LEAF else FlowLeaf.NONE })
    }
    private fun taint(vararg leaves: Conclusion, exclusion: ExclusionSet = empty,
                      layer: Layer = Layer.NORMAL): TaintTree {
        val root = Prefix()
        for (leaf in leaves) {
            val prefix = at(root, leaf.fact.path)
            val id = (leaf.fact.mark as MarkSlot.Concrete).mark.id
            if (leaf.fact.tail == Tail.EXACT) prefix.exact += id else prefix.any += id
        }
        return manager.taintTree(y, layer, build(root, manager.taintAlg) {
            manager.taintLeaves(MarkSet(it.exact.toIntArray()), MarkSet(it.any.toIntArray()))
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
    private fun decode(value: Facts): Set<Conclusion> = buildSet {
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
    private fun key(c: Conclusion): GroupKey {
        val layer = if (c.demand) Layer.DEMAND else Layer.NORMAL
        return when {
            c.fact.base == AccessPathBase.Zero -> GroupKey(FactsKind.REACH, layer, c.fact.base)
            c.fact.mark is MarkSlot.Star -> GroupKey(FactsKind.FLOW, layer, c.fact.base, c.exclusion,
                (c.fact.mark as MarkSlot.Star).excluded, c.fact.tail)
            else -> GroupKey(FactsKind.TAINT, layer, c.fact.base, c.exclusion,
                tail = if (c.fact.tail == Tail.EXACT) Tail.ANY_TAINT else c.fact.tail)
        }
    }
    private fun representable(c: Conclusion): Boolean = c.fact.mark !is MarkSlot.Star ||
        (c.fact.tail == Tail.STAR) || (c.fact.tail == Tail.ANY && c.demand)
    private fun check(initial: InitialAp, summary: Facts, demand: DemandPattern): List<Conclusion> {
        val expected = decode(summary).mapNotNull { restrict(initial.toPattern(), it, demand) }.toSet()
        val reference = ops.restrictReference(initial, summary, demand)
        assertEquals(expected, reference.toSet())
        assertEquals(expected.size, reference.size)
        assertTrue(reference.all { it.demand == (summary.layer == Layer.DEMAND) })
        if (expected.any { !representable(it) }) {
            assertFails { ops.restrict(initial, summary, demand) }
        } else {
            val packed = ops.restrict(initial, summary, demand)
            assertEquals(expected, packed.flatMap { decode(it) }.toSet())
            val groups = packed.associateBy { it.groupKey }
            assertEquals(packed.size, groups.size)
            assertEquals(expected.groupBy(::key).mapValues { it.value.toSet() }, groups.mapValues { decode(it.value) })
            assertTrue(packed.all { it.layer == summary.layer })
        }
        return reference
    }

    @Test
    fun `zero restriction preserves the raw layer and canonical reach packing`() {
        val zero = manager.zero.toPattern()
        val demand = DemandPattern(zero, zero)
        for (layer in Layer.entries) {
            val reach = Reach.of(layer)
            assertEquals(listOf(Conclusion(zero.fact, ExclusionSet.Empty, layer == Layer.DEMAND)),
                ops.restrictReference(manager.zero, reach, demand))
            assertSame(reach, ops.restrict(manager.zero, reach, demand).single())
        }
    }

    @Test
    fun `tree reduction matches the annotated reference for entry exit marks tails exclusions and layers`() {
        val paths = listOf(emptyList(), listOf(f), listOf(f, g), listOf(g), listOf(cls, f), listOf(element))
        val leaves = paths.flatMap { listOf(c(it, Tail.EXACT), c(it, Tail.EXACT, u),
            c(it, Tail.ANY_TAINT), c(it, Tail.ANY_TAINT, u)) } + c(listOf(h), Tail.EXACT, fresh)
        val summaries = listOf<Facts>(Reach.NORMAL, Reach.DEMAND) + Layer.entries.flatMap { layer ->
            listOf(flow(*paths.toTypedArray(), exclusion = ex(g), mx = marks(u.id), layer = layer),
                taint(*leaves.toTypedArray(), exclusion = ex(g), layer = layer))
        }
        val initials = listOf(p(emptyList(), Tail.STAR), p(listOf(f), Tail.STAR, exclusion = ex(g)),
            p(listOf(f), Tail.ANY), p(emptyList(), Tail.STAR, MarkSlot.Concrete(t), ex(g)),
            p(emptyList(), Tail.EXACT, MarkSlot.Concrete(t)), p(listOf(f), Tail.EXACT, MarkSlot.Concrete(u)),
            p(emptyList(), Tail.ANY, MarkSlot.Concrete(t)),
            p(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t), ex(g)),
            p(listOf(f), Tail.ANY_TAINT, MarkSlot.Concrete(u)),
            p(emptyList(), Tail.STAR, base = z), manager.zero.toPattern()).map(manager::initial)
        val entries = listOf(p(emptyList(), Tail.ANY), p(emptyList(), Tail.STAR),
            p(emptyList(), Tail.STAR, exclusion = ex(f)), p(listOf(f), Tail.STAR),
            p(emptyList(), Tail.EXACT, MarkSlot.Concrete(t)), p(emptyList(), Tail.ANY, MarkSlot.Concrete(t)),
            p(emptyList(), Tail.ANY, MarkSlot.Star(marks(u.id))),
            p(emptyList(), Tail.ANY, base = z), manager.zero.toPattern())
        val exits = listOf<Pattern?>(null, p(emptyList(), Tail.ANY, base = y), p(listOf(f), Tail.ANY, base = y),
            p(listOf(f), Tail.STAR, exclusion = ex(g), base = y),
            p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t), base = y),
            p(listOf(f), Tail.EXACT, MarkSlot.Concrete(u), base = y),
            p(listOf(f, g), Tail.EXACT, MarkSlot.Concrete(t), base = y),
            p(listOf(g), Tail.ANY, MarkSlot.Concrete(t), base = y),
            p(emptyList(), Tail.ANY, MarkSlot.Star(marks(t.id)), base = y),
            p(listOf(cls), Tail.STAR, base = y), p(listOf(element), Tail.ANY, base = y),
            p(emptyList(), Tail.ANY, base = z), manager.zero.toPattern())
        for (initial in initials) for (summary in summaries) for (entry in entries) for (exit in exits)
            check(initial, summary, DemandPattern(entry, exit))
    }

    @Test
    fun `whole premise containment rejects overlap and reads must exclusions and entry marks`() {
        val conclusion = taint(c(listOf(f), Tail.EXACT, u))
        val exit = p(listOf(f), Tail.EXACT, MarkSlot.Concrete(u), base = y)
        val root = manager.initial(p(emptyList(), Tail.STAR))
        val overlapping = p(listOf(f), Tail.STAR)
        assertTrue(overlap(root.toPattern(), overlapping))
        assertTrue(check(root, conclusion, DemandPattern(overlapping, exit)).isEmpty())
        val must = manager.initial(p(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t), ex(g)))
        for (entry in listOf(p(emptyList(), Tail.STAR, exclusion = ex(f)),
            p(emptyList(), Tail.STAR, exclusion = ex(f, g)),
            p(emptyList(), Tail.ANY, MarkSlot.Concrete(u)),
            p(emptyList(), Tail.ANY, MarkSlot.Star(marks(t.id)))))
            assertTrue(check(must, conclusion, DemandPattern(entry, exit)).isEmpty())
        for (entry in listOf(p(emptyList(), Tail.STAR), p(emptyList(), Tail.STAR, exclusion = ex(g)),
            p(emptyList(), Tail.ANY, MarkSlot.Concrete(t)),
            p(emptyList(), Tail.ANY, MarkSlot.Star(marks(u.id)))))
            assertEquals(decode(conclusion), check(must, conclusion, DemandPattern(entry, exit)).toSet())
        val moreExcluded = manager.initial(p(emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t), ex(f, g)))
        assertEquals(decode(conclusion),
            check(moreExcluded, conclusion, DemandPattern(p(emptyList(), Tail.STAR, exclusion = ex(g)), exit)).toSet())
        assertTrue(check(root, conclusion, DemandPattern(p(emptyList(), Tail.ANY, MarkSlot.Concrete(t)), exit)).isEmpty())
        val underField = manager.initial(p(listOf(f), Tail.ANY_TAINT, MarkSlot.Concrete(t)))
        assertTrue(check(underField, conclusion, DemandPattern(p(emptyList(), Tail.STAR, exclusion = ex(f)), exit)).isEmpty())
    }

    @Test
    fun `must exits distinguish at above below and keep raw normal E until the specified meet`() {
        val initial = manager.initial(p(emptyList(), Tail.EXACT, MarkSlot.Concrete(t)))
        val entry = p(emptyList(), Tail.ANY)
        val input = taint(c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.ANY_TAINT),
            c(listOf(f, g), Tail.ANY_TAINT), c(listOf(g), Tail.ANY_TAINT), exclusion = ex(g))
        val star = DemandPattern(entry, p(listOf(f), Tail.STAR, exclusion = ex(h), base = y))
        assertEquals(setOf(c(listOf(f), Tail.ANY_TAINT, exclusion = ex(h)),
            c(listOf(f), Tail.ANY_TAINT, exclusion = ex(g, h)),
            c(listOf(f, g), Tail.ANY_TAINT, exclusion = ex(g))), check(initial, input, star).toSet())
        val exact = DemandPattern(entry, p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t), base = y))
        assertEquals(listOf(c(listOf(f), Tail.EXACT)), check(initial, input, exact))
        val any = DemandPattern(entry, p(listOf(f), Tail.ANY, base = y))
        assertEquals(setOf(c(listOf(f), Tail.ANY_TAINT), c(listOf(f), Tail.ANY_TAINT, exclusion = ex(g)),
            c(listOf(f, g), Tail.ANY_TAINT, exclusion = ex(g))), check(initial, input, any).toSet())
        val blocked = DemandPattern(entry, p(listOf(g), Tail.EXACT, MarkSlot.Concrete(t), base = y))
        assertEquals(listOf(c(listOf(g), Tail.EXACT)), check(initial, input, blocked))
    }

    @Test
    fun `retained correlated tails and abstract marks stay whole instead of narrowing to a concrete demand`() {
        val initial = manager.initial(p(emptyList(), Tail.STAR))
        val input = flow(emptyList(), listOf(f), listOf(f, g), exclusion = ex(g), mx = marks(u.id))
        val demand = DemandPattern(p(emptyList(), Tail.ANY), p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t), base = y))
        val raw = check(initial, input, demand)
        assertEquals(setOf(emptyList(), listOf(f)), raw.map { it.fact.path }.toSet())
        assertTrue(raw.all { it.fact.tail == Tail.STAR && it.fact.mark == MarkSlot.Star(marks(u.id)) && it.exclusion == ex(g) })
        assertTrue(check(initial, input, demand.copy(exit = p(listOf(f), Tail.EXACT, MarkSlot.Concrete(u), base = y))).isEmpty())
        val may = flow(emptyList(), listOf(f), listOf(f, g), mx = marks(u.id), layer = Layer.DEMAND)
        val star = demand.copy(exit = p(listOf(f), Tail.STAR, MarkSlot.Concrete(t), ex(g), y))
        assertEquals(listOf(Conclusion(PathFact(y, listOf(f), Tail.ANY, MarkSlot.Star(marks(u.id))), empty, true)),
            check(initial, may, star))
    }

    @Test
    fun `generic abstract exact reduction is retained and typed packing rejects it`() {
        val initial = manager.initial(p(emptyList(), Tail.STAR))
        val may = flow(emptyList(), listOf(f), mx = marks(u.id), layer = Layer.DEMAND)
        val demand = DemandPattern(p(emptyList(), Tail.ANY), p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t), base = y))
        val expected = Conclusion(PathFact(y, listOf(f), Tail.EXACT, MarkSlot.Star(marks(u.id))), empty, true)
        assertEquals(listOf(expected), ops.restrictReference(initial, may, demand))
        assertFails { ops.restrict(initial, may, demand) }
    }

    @Test
    fun `packing groups complete annotation keys without cross E intersection or subsumption`() {
        val initial = manager.initial(p(emptyList(), Tail.EXACT, MarkSlot.Concrete(t)))
        val input = taint(c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.ANY_TAINT),
            c(listOf(f), Tail.EXACT, u), exclusion = ex(g))
        val demand = DemandPattern(p(emptyList(), Tail.ANY), p(listOf(f), Tail.STAR, exclusion = ex(h), base = y))
        val raw = check(initial, input, demand)
        val packed = ops.restrict(initial, input, demand)
        assertEquals(3, raw.size)
        assertEquals(3, packed.size)
        assertEquals(setOf(empty, ex(h), ex(g, h)), packed.map { (it as TaintTree).exclusion }.toSet())
        val transfer = flow(emptyList(), listOf(f), exclusion = ex(g), mx = marks(u.id))
        val transferInitial = manager.initial(p(emptyList(), Tail.STAR))
        val exactExit = demand.copy(exit = p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t), base = y))
        val kept = ops.restrict(transferInitial, transfer, exactExit).single() as FlowTree
        assertEquals(setOf(emptyList(), listOf(f)), decode(kept).map { it.fact.path }.toSet())
        assertEquals(transfer.groupKey, kept.groupKey)
    }

    @Test
    fun `duplicate moved and at-path conclusions are packed once with exact E cleared`() {
        val initial = manager.initial(p(emptyList(), Tail.EXACT, MarkSlot.Concrete(t)))
        val input = taint(c(emptyList(), Tail.ANY_TAINT), c(listOf(f), Tail.ANY_TAINT), exclusion = ex(g))
        val demand = DemandPattern(p(emptyList(), Tail.ANY), p(listOf(f), Tail.EXACT, MarkSlot.Concrete(t), base = y))
        assertEquals(listOf(c(listOf(f), Tail.EXACT)), ops.restrictReference(initial, input, demand))
        val packed = ops.restrict(initial, input, demand).single() as TaintTree
        assertEquals(empty, packed.exclusion)
        assertFalse(packed.root.hasAny)
        assertEquals(setOf(c(listOf(f), Tail.EXACT)), decode(packed))
    }

    @Test
    fun `deep sparse restriction preserves moved exact results and cancellation`() {
        val depth = 32768
        val path = List(depth) { f }
        val exact = manager.taintLeaves(manager.markSetOf(u), MarkSet.EMPTY)
        var root = manager.taintAlg.leafNode(exact)
        repeat(depth) { root = manager.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(f), arrayOf(root)) }
        root = manager.taintAlg.node(manager.taintLeaves(MarkSet.EMPTY, manager.markSetOf(t)), root.accessors, root.children)
        val input = manager.taintTree(y, Layer.NORMAL, root, ex(g))
        val initial = manager.initial(p(emptyList(), Tail.EXACT, MarkSlot.Concrete(t)))
        val demand = DemandPattern(p(emptyList(), Tail.ANY), p(path, Tail.EXACT, MarkSlot.Concrete(t), base = y))
        assertEquals(listOf(c(path, Tail.EXACT)), ops.restrictReference(initial, input, demand))
        val packed = ops.restrict(initial, input, demand).single() as TaintTree
        assertEquals(Layer.NORMAL, packed.layer)
        assertEquals(empty, packed.exclusion)
        val tip = requireNotNull(packed.root.walk(path.toIntArray()))
        assertEquals(manager.markSetOf(t), tip.leaf.exact)
        assertTrue(tip.leaf.any.isEmpty)
        assertNull(tip.accessors)
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { ops.restrictReference(initial, input, demand) }
        assertFailsWith<Cancellation.Cancelled> { ops.restrict(initial, input, demand) }
    }
}
