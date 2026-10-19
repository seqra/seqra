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
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The input list is stated independently; cleanRes supplies the expected primitive cleaner rows. */
class CleanerTest {
    private val manager = testApManager(Cancellation())
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val static = AccessPathBase.ClassStatic
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val k = manager.accessors.index(FieldAccessor("Box", "k", "Box"))
    private val c = manager.accessors.index(ClassStaticAccessor("Box"))
    private val element = manager.accessors.index(ElementAccessor)
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val v = manager.marks.mark("V")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val star = MarkSlot.STAR
    private val run1 = ApMode(true, Direction.FORWARD, Int.MAX_VALUE, markMode = MarkMode.FULL)
    private val forward = ApMode(false, Direction.FORWARD, Int.MAX_VALUE)
    private val backward = ApMode(false, Direction.BACKWARD, Int.MAX_VALUE)
    private val modes = listOf(run1, forward, backward)

    private data class Input(val facts: Facts, val leaves: List<Conclusion>)
    private data class Expected(val facts: Set<Conclusion>, val requests: Set<TaintMark>)

    private fun p(vararg accessors: Int): List<Int> = accessors.toList()
    private fun ex(vararg accessors: Int): ExclusionSet = ExclusionSet.of(accessors)
    private fun marks(vararg ids: Int): MarkSet = MarkSet(ids)
    private fun value(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                      demand: Boolean = false, exclusion: ExclusionSet = ExclusionSet.Empty,
                      base: AccessPathBase = x): Conclusion = Conclusion(PathFact(base, path, tail, mark), exclusion, demand)
    private fun cleaner(path: List<Int>, reach: CleanReach, mark: TaintMark? = t,
                        base: AccessPathBase = x): Cleaner = Cleaner(base, manager.path(path), reach, mark)

    private fun pack(leaves: List<Conclusion>): Input {
        require(leaves.isNotEmpty())
        val typed = leaves.map { manager.factsOf(it.fact, it.exclusion, if (it.demand) Layer.DEMAND else Layer.NORMAL) }
        val first = typed.first()
        require(typed.all { it.base == first.base && it.layer == first.layer })
        val facts = when (first) {
            is Reach -> first
            is FlowTree -> {
                require(typed.all { it is FlowTree && it.exclusion == first.exclusion && it.markExclusion == first.markExclusion })
                val root = typed.drop(1).fold(first.root) { root, next -> FlowAlgebra.mergeAdd(root, (next as FlowTree).root) }
                manager.flowTree(first.base, first.layer, first.exclusion, first.markExclusion, root, first.tail)
            }
            is TaintTree -> {
                require(typed.all { it is TaintTree })
                val root = typed.drop(1).fold(first.root) { root, next -> manager.taintAlg.mergeAdd(root, (next as TaintTree).root) }
                val exclusion = leaves.firstOrNull { it.fact.tail == Tail.ANY_TAINT }?.exclusion ?: ExclusionSet.Empty
                require(leaves.filter { it.fact.tail == Tail.ANY_TAINT }.all { it.exclusion == exclusion })
                manager.taintTree(first.base, first.layer, root, exclusion, (typed.firstOrNull { it is TaintTree && it.root.hasAny } as? TaintTree)?.anyTail ?: Tail.ANY_TAINT)
            }
        }
        return Input(facts, leaves)
    }

    private fun flow(paths: List<List<Int>>, demand: Boolean = false, mx: MarkSet = MarkSet.EMPTY,
                     exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x): Input = pack(paths.map {
        value(it, if (demand) Tail.ANY else Tail.STAR, MarkSlot.Star(mx), demand, exclusion, base)
    })

    private fun premise(input: Input): InitialAp = when (input.facts) {
        is Reach -> manager.zero
        is FlowTree -> manager.initial(input.facts.base, null, Tail.STAR, ExclusionSet.Empty, star)
        is TaintTree -> manager.initial(input.facts.base, null, Tail.EXACT, ExclusionSet.Empty, mt)
    }

    private fun decoded(results: List<Facts>): Set<Conclusion> = results.flatMap { facts ->
        ops.leaves(facts).map { Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND) }.toList()
    }.toSet()

    private fun policy(value: Conclusion, mode: ApMode): Conclusion =
        if (mode.run1 && value.exclusion != ExclusionSet.Empty) value.copy(exclusion = ExclusionSet.Empty, demand = true) else value

    private fun expected(input: Input, cl: Cleaner, mode: ApMode): Expected {
        val results = input.leaves.map { cleanRes(cl, it, mode.run1) }
        return Expected(results.flatMap { it.facts }.toSet(),
            if (mode.run1) results.mapNotNull { it.request }.toSet() else emptySet())
    }

    private fun check(input: Input, cl: Cleaner, mode: ApMode = forward, name: String = ""): CollectingOut {
        val expected = expected(input, cl, mode)
        val out = CollectingOut()
        ops.clean(input.facts, premise(input), cl, mode, out)
        assertEquals(expected.facts, decoded(out.results), "$name facts")
        assertEquals(expected.requests, out.markRequests.toSet(), "$name mark requests")
        assertEquals(out.markRequests.toSet().size, out.markRequests.size, "$name duplicate requests")
        return out
    }

    @Test
    fun `primitive cleaner matches every legal tail layer spatial mark and reach row`() {
        val paths = listOf(p(), p(f), p(f, g), p(f, g, h), p(f, h), p(k))
        for (path in paths) {
            val inputs = listOf(
                pack(listOf(value(path, Tail.EXACT))), pack(listOf(value(path, Tail.EXACT, demand = true))),
                pack(listOf(value(path, Tail.EXACT, mu))),
                pack(listOf(value(path, Tail.ANY_TAINT))),
                pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(g)))),
                pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(h)))),
                pack(listOf(value(path, Tail.ANY, demand = true))),
                flow(listOf(path), mx = marks(u.id), exclusion = ex(g)),
                flow(listOf(path), demand = true, mx = marks(u.id)),
            )
            for (reach in CleanReach.entries) for (mark in listOf(t, u, null)) for (mode in modes) {
                val cl = cleaner(p(f, g), reach, mark)
                for ((index, input) in inputs.withIndex())
                    check(input, cl, mode, "$path input $index $reach mark=$mark run1=${mode.run1} ${mode.direction}")
            }
        }
    }

    @Test
    fun `selected mark abstract branch precedes path reach and already excluded tests`() {
        val paths = listOf(p(), p(f), p(f, g), p(f, h), p(k, element))
        for (demand in listOf(false, true)) for (mx in listOf(marks(u.id), marks(t.id, u.id))) {
            val input = flow(paths, demand, mx, if (demand) ExclusionSet.Empty else ex(g))
            for (clPath in listOf(p(), p(f, g), p(h, k))) for (reach in CleanReach.entries) for (mode in modes) {
                val out = check(input, cleaner(clPath, reach), mode)
                val result = assertIs<FlowTree>(out.results.single())
                val original = assertIs<FlowTree>(input.facts)
                assertSame(original.root, result.root)
                assertEquals(if (mode.run1 && original.exclusion != ExclusionSet.Empty) Layer.DEMAND else original.layer, result.layer)
                assertEquals(if (mode.run1) ExclusionSet.Empty else original.exclusion, result.exclusion)
                assertEquals(MarkExclusions.fromMarks(marks(t.id, u.id), manager.marks), result.markExclusion)
                if (t in original.markExclusion) assertSame(original.markExclusion, result.markExclusion)
                assertEquals(if (mode.run1) listOf(t) else emptyList(), out.markRequests)
            }
        }
    }

    @Test
    fun `must one-step partial cleaners retain normal exclusion and below adds an exact location`() {
        for (exclusion in listOf(ExclusionSet.Empty, ex(h))) {
            val original = value(p(f), Tail.ANY_TAINT, exclusion = exclusion)
            val input = pack(listOf(original))
            val kept = original.copy(exclusion = exclusion.union(ex(g)))
            val endpoint = value(p(f, g), Tail.EXACT)
            for (mark in listOf(t, null)) for (mode in modes) {
                assertEquals(setOf(policy(kept, mode)), decoded(check(input, cleaner(p(f, g), CleanReach.AT_AND_BELOW, mark), mode).results))
                assertEquals(setOf(policy(kept, mode), endpoint), decoded(check(input, cleaner(p(f, g), CleanReach.BELOW, mark), mode).results))
                val demoted = policy(original.copy(demand = true), mode)
                assertEquals(setOf(demoted), decoded(check(input, cleaner(p(f, g), CleanReach.EXACT, mark), mode).results))
                assertEquals(setOf(demoted), decoded(check(input, cleaner(p(f, g, h), CleanReach.AT_AND_BELOW, mark), mode).results))
                assertEquals(setOf(demoted), decoded(check(input, cleaner(p(f, g, h), CleanReach.BELOW, mark), mode).results))
            }
            for (reach in CleanReach.entries) assertEquals(setOf(original),
                decoded(check(input, cleaner(p(f, g), reach, u)).results))
        }
    }

    @Test
    fun `must at below becomes exact while other partial rows demote with Empty E`() {
        val original = value(p(f, g), Tail.ANY_TAINT, exclusion = ex(h))
        val input = pack(listOf(original))
        for (mode in modes) for (mark in listOf(t, null)) {
            assertEquals(setOf(value(p(f, g), Tail.EXACT)), decoded(check(input, cleaner(p(f, g), CleanReach.BELOW, mark), mode).results))
            assertEquals(setOf(policy(original.copy(demand = true), mode)), decoded(check(input, cleaner(p(f, g), CleanReach.EXACT, mark), mode).results))
            assertTrue(check(input, cleaner(p(f, g), CleanReach.AT_AND_BELOW, mark), mode).results.isEmpty())
        }
        val demand = pack(listOf(value(p(f, g), Tail.ANY, demand = true)))
        assertEquals(setOf(value(p(f, g), Tail.EXACT, demand = true)),
            decoded(check(demand, cleaner(p(f, g), CleanReach.BELOW)).results))
    }

    @Test
    fun `first accessor alone decides whether an above must fact is disjoint`() {
        val original = value(p(f), Tail.ANY_TAINT, exclusion = ex(h))
        val input = pack(listOf(original))
        for (reach in CleanReach.entries) for (mark in listOf(t, null)) for (mode in modes) {
            assertEquals(setOf(policy(original, mode)), decoded(check(input, cleaner(p(f, h, g), reach, mark), mode).results))
            assertEquals(setOf(policy(original.copy(demand = true), mode)),
                decoded(check(input, cleaner(p(f, g, h), reach, mark), mode).results))
        }
    }

    @Test
    fun `multi-leaf cleaner keeps unselected marks and off-spine subtrees while packing partial results`() {
        val normal = pack(buildList {
            for (path in listOf(p(), p(f), p(f, g), p(f, g, k), p(f, h), p(k, element))) {
                add(value(path, Tail.EXACT))
                add(value(path, Tail.EXACT, mu))
                add(value(path, Tail.ANY_TAINT, exclusion = ex(h)))
                add(value(path, Tail.ANY_TAINT, mu, exclusion = ex(h)))
            }
            add(value(p(k, element), Tail.EXACT, MarkSlot.Concrete(v)))
        })
        val demand = pack(normal.leaves.map { leaf ->
            value(leaf.fact.path, if (leaf.fact.tail == Tail.ANY_TAINT) Tail.ANY else Tail.EXACT,
                leaf.fact.mark, demand = true)
        })
        for (input in listOf(normal, demand)) for (reach in CleanReach.entries) for (mark in listOf(t, null)) for (mode in modes)
            check(input, cleaner(p(f, g), reach, mark), mode)
        val original = assertIs<TaintTree>(normal.facts)
        val out = check(normal, cleaner(p(f, g), CleanReach.AT_AND_BELOW))
        val unchangedGroup = out.results.filterIsInstance<TaintTree>().single {
            it.layer == Layer.NORMAL && it.exclusion == original.exclusion
        }
        assertSame(original.root.child(k), unchangedGroup.root.child(k))
        assertSame(original.root.child(f)?.child(h), unchangedGroup.root.child(f)?.child(h))
        for (demandLayer in listOf(false, true)) {
            val abstract = flow(listOf(p(), p(f), p(f, g), p(f, g, k), p(f, h), p(k)), demandLayer,
                marks(u.id), if (demandLayer) ExclusionSet.Empty else ex(g))
            for (reach in CleanReach.entries) for (mode in modes) check(abstract, cleaner(p(f, g), reach, null), mode)
        }
    }

    @Test
    fun `different base zero and static roots follow primitive dispatch without position requests`() {
        val inputs = listOf(flow(listOf(p(), p(f, g)), mx = marks(u.id)),
            flow(listOf(p(), p(f, g)), demand = true), pack(listOf(value(p(f), Tail.ANY_TAINT, exclusion = ex(g)))),
            pack(listOf(value(p(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero))),
            pack(listOf(value(p(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), demand = true, base = AccessPathBase.Zero))))
        for (input in inputs) for (reach in CleanReach.entries) for (mark in listOf(t, null)) for (mode in modes) {
            val out = check(input, cleaner(p(), reach, mark, y), mode)
            if (!mode.run1 || input.leaves.all { it.exclusion == ExclusionSet.Empty }) assertSame(input.facts, out.results.single())
            else assertEquals(input.leaves.map { policy(it, mode) }.toSet(), decoded(out.results))
        }
        for (path in listOf(p(), p(c), p(c, f))) {
            val abstract = flow(listOf(path), mx = marks(u.id), exclusion = ex(g), base = static)
            val concrete = pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(g), base = static)))
            for (input in listOf(abstract, concrete)) for (reach in CleanReach.entries) for (mark in listOf(t, null)) for (mode in modes)
                check(input, cleaner(p(c, f), reach, mark, static), mode)
        }
        assertFails { cleaner(p(), CleanReach.EXACT, base = AccessPathBase.Zero) }
    }

    @Test
    fun `selected abstract cleaning shares a huge shared DAG without enumerating its leaves`() {
        var root = FlowAlgebra.LEAF_NODE
        repeat(62) { root = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, g), arrayOf(root, root)) }
        assertEquals(Long.MAX_VALUE, root.size)
        for (layer in Layer.entries) for (mode in modes) {
            val facts = manager.flowTree(x, layer, if (layer == Layer.NORMAL) ex(h) else ExclusionSet.Empty, marks(u.id), root)
            val initial = manager.initial(x, null, Tail.STAR, ExclusionSet.Empty, star)
            val out = CollectingOut()
            ops.clean(facts, initial, cleaner(p(k, h), CleanReach.EXACT), mode, out)
            val result = assertIs<FlowTree>(out.results.single())
            assertSame(root, result.root)
            assertEquals(if (mode.run1 && facts.exclusion != ExclusionSet.Empty) Layer.DEMAND else facts.layer, result.layer)
            assertEquals(if (mode.run1) ExclusionSet.Empty else facts.exclusion, result.exclusion)
            assertEquals(MarkExclusions.fromMarks(marks(t.id, u.id), manager.marks), result.markExclusion)
            assertEquals(if (mode.run1) listOf(t) else emptyList(), out.markRequests)
        }
    }

    @Test
    fun `32768 depth cleaner spines and selected branches remain iterative`() {
        val path = List(32768) { f }
        val inputs = listOf(pack(listOf(value(path, Tail.EXACT))),
            pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(g)))),
            pack(listOf(value(path, Tail.ANY, demand = true))),
            flow(listOf(path), mx = marks(u.id), exclusion = ex(g)))
        for (input in inputs) for (reach in CleanReach.entries) for (mark in listOf(t, null)) {
            check(input, cleaner(path, reach, mark))
            check(input, cleaner(path + h, reach, mark))
        }
        val offSpine = pack(listOf(value(path, Tail.EXACT), value(path.dropLast(1) + h, Tail.EXACT)))
        check(offSpine, cleaner(path, CleanReach.AT_AND_BELOW))
        check(pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(g)))),
            cleaner(path + h + g, CleanReach.AT_AND_BELOW))
    }

    @Test
    fun `cleanSpine keeps the positional callback API and checks cancellation on empty prefixes`() {
        val path = List(32768) { f }.toIntArray()
        val root = FlowAlgebra.prepend(manager, manager.path(path), FlowAlgebra.LEAF_NODE)
        val at: (Int, FlowLeaf) -> FlowLeaf = { _, leaf -> leaf }
        val inside: (FlowNode) -> FlowNode? = { it }
        assertSame(root, FlowAlgebra.cleanSpine(root, path, CleanReach.EXACT, 0, at, inside))
        var visited = 0
        try {
            assertFailsWith<Cancellation.Cancelled> {
                FlowAlgebra.cleanSpine(root, path, CleanReach.EXACT, 0, manager.cancellation, { _, leaf ->
                    visited++
                    if (visited == 3) manager.cancellation.cancel()
                    leaf
                }, inside)
            }
            assertEquals(3, visited)
        } finally {
            manager.cancellation.activate()
        }
    }

    @Test
    fun `cancelled cleaning stops deep concrete and selected abstract operations`() {
        val path = List(32768) { f }
        val inputs = listOf(pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(g)))),
            flow(listOf(path), mx = marks(u.id)))
        val cl = cleaner(path, CleanReach.AT_AND_BELOW)
        manager.cancellation.cancel()
        try {
            for (input in inputs) {
                val out = CollectingOut()
                assertFailsWith<Cancellation.Cancelled> { ops.clean(input.facts, premise(input), cl, run1, out) }
                assertTrue(out.results.isEmpty())
                assertTrue(out.markRequests.isEmpty())
            }
        } finally {
            manager.cancellation.activate()
        }
    }
}
