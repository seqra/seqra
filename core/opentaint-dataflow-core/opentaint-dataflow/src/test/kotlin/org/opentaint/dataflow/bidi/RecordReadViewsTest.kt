package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecordReadViewsTest {
    private val manager = ApManager(Cancellation())
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val empty = ExclusionSet.Empty
    private val forward = ApMode(false, Direction.FORWARD, 64)
    private val backward = ApMode(false, Direction.BACKWARD, 64)

    @Test
    fun `F76 exact to must view retains read E and distinct marks while dropping the suffix`() {
        val source = pattern(x, listOf(f), Tail.EXACT, mu)
        val target = conclusion(y, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val view = assertNotNull(reverseRecordLeaf(Direction.FORWARD, source, target))
        assertEquals(pattern(y, listOf(g), Tail.STAR, mt, ex(h)), view.premise)
        assertEquals(conclusion(x, listOf(f), Tail.EXACT, mu), view.result)
        assertNotEquals((view.premise.fact.mark as MarkSlot.Concrete).mark,
            (view.result.fact.mark as MarkSlot.Concrete).mark)
        assertFalse(recordLeafIdentity(view))

        val input = conclusion(y, listOf(g, f, h), Tail.EXACT, mt)
        val selected = assertNotNull(ops.satisfying(typed(input), view.premise, backward, record = true))
        assertEquals(setOf(input), decode(listOf(selected)))
        assertEquals(setOf(conclusion(x, listOf(f), Tail.EXACT, mu)), apply(view, selected, backward))
        assertNull(ops.satisfying(typed(conclusion(y, listOf(g, h, f), Tail.EXACT, mt)),
            view.premise, backward, record = true))
        assertNull(ops.satisfying(typed(input.copy(fact = input.fact.copy(mark = mu))),
            view.premise, backward, record = true))
        assertEquals(ex(h), view.premise.exclusion)
        assertEquals(Tail.STAR, view.premise.fact.tail)
    }

    @Test
    fun `F76 must to must view retains target read E and returns may without old input E`() {
        val source = pattern(x, listOf(f), Tail.ANY_TAINT, mu, ex(g))
        val target = conclusion(y, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val view = assertNotNull(reverseRecordLeaf(Direction.FORWARD, source, target))
        assertEquals(pattern(y, listOf(g), Tail.STAR, mt, ex(h)), view.premise)
        assertEquals(conclusion(x, listOf(f), Tail.ANY, mu), view.result)
        val input = conclusion(y, listOf(g, f), Tail.EXACT, mt)
        val selected = assertNotNull(ops.satisfying(typed(input), view.premise, backward, record = true))
        assertEquals(setOf(conclusion(x, listOf(f), Tail.ANY, mu)), apply(view, selected, backward))
        assertNull(ops.satisfying(typed(conclusion(y, listOf(g, h), Tail.EXACT, mt)),
            view.premise, backward, record = true))
        assertEquals(ex(g), source.exclusion)
        assertEquals(ex(h), target.exclusion)
    }

    @Test
    fun `F76 must to exact view retains an exact reader and returns distinct upstream may mark`() {
        val source = pattern(x, listOf(f), Tail.ANY_TAINT, mu, ex(g))
        val target = conclusion(y, listOf(g), Tail.EXACT, mt)
        val view = assertNotNull(reverseRecordLeaf(Direction.FORWARD, source, target))
        assertEquals(pattern(y, listOf(g), Tail.EXACT, mt), view.premise)
        assertEquals(conclusion(x, listOf(f), Tail.ANY, mu), view.result)
        val selected = assertNotNull(ops.satisfying(typed(target), view.premise, backward, record = true))
        assertEquals(setOf(conclusion(x, listOf(f), Tail.ANY, mu)), apply(view, selected, backward))
        assertNull(ops.satisfying(typed(conclusion(y, listOf(g, f), Tail.EXACT, mt)),
            view.premise, backward, record = true))
    }

    @Test
    fun `native view selection and application agree with applicable or inside and raw concat`() {
        val paths = listOf(emptyList(), listOf(g), listOf(g, f), listOf(g, h), listOf(g, f, h), listOf(f), listOf(h))
        val inputs = buildList {
            for (path in paths) {
                for (mark in listOf(mt, mu)) {
                    add(conclusion(y, path, Tail.EXACT, mark))
                    add(conclusion(y, path, Tail.EXACT, mark, demand = true))
                    add(conclusion(y, path, Tail.ANY, mark))
                    for (e in listOf(empty, ex(f), ex(g), ex(h))) {
                        add(conclusion(y, path, Tail.ANY_TAINT, mark, e))
                    }
                }
                add(conclusion(y, path, Tail.STAR, MarkSlot.STAR, ex(h)))
                add(conclusion(y, path, Tail.STAR, MarkSlot.Star(MarkSet(intArrayOf(t.id)))))
                add(conclusion(y, path, Tail.ANY, MarkSlot.STAR))
            }
            add(conclusion(x, listOf(g), Tail.EXACT, mt))
        }
        for (view in nativeViews()) for (input in inputs) {
            val a = Pattern(input.fact, input.exclusion)
            val expectedSelected = applicable(view.premise, a) || inside(view.premise, a)
            val selected = ops.satisfying(typed(input), view.premise, forward, record = true)
            assertEquals(if (expectedSelected) setOf(input) else emptySet(),
                selected?.let { decode(listOf(it)) } ?: emptySet(), "$view on $input")
            if (selected != null) {
                val expected = directResults(view, listOf(input), forward)
                assertEquals(expected, apply(view, selected, forward), "$view on $input")
            }
        }
    }

    @Test
    fun `mixed concrete STAR E reader keeps admitted leaves and rejects blocked paths and marks`() {
        val view = nativeViews()[0]
        val inputs = listOf(
            conclusion(y, emptyList(), Tail.ANY_TAINT, mt, ex(h)),
            conclusion(y, listOf(g), Tail.ANY_TAINT, mt, ex(h)),
            conclusion(y, listOf(g), Tail.EXACT, mt),
            conclusion(y, listOf(g, f), Tail.EXACT, mt),
            conclusion(y, listOf(g, f, h), Tail.EXACT, mt),
            conclusion(y, listOf(g, h), Tail.EXACT, mt),
            conclusion(y, listOf(g, f), Tail.EXACT, mu),
            conclusion(y, listOf(h), Tail.EXACT, mt),
        )
        val added = taintBundle(inputs, y, Layer.NORMAL, ex(h))
        val selected = assertNotNull(ops.satisfying(added, view.premise, forward, record = true))
        val expected = inputs.filter {
            val a = Pattern(it.fact, it.exclusion)
            applicable(view.premise, a) || inside(view.premise, a)
        }.toSet()
        assertEquals(expected, decode(listOf(selected)))
        assertEquals(directResults(view, expected, forward), apply(view, selected, forward))
        assertEquals(Layer.NORMAL, selected.layer)
        assertEquals(ex(h), (selected as TaintTree).exclusion)
        assertEquals(inputs.toSet(), decode(listOf(added)))
    }

    @Test
    fun `native must split preserves inside and applicable only batch layers and exclusions`() {
        val premise = pattern(x, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val j = manager.initial(premise)
        val inputs = listOf(
            conclusion(x, emptyList(), Tail.ANY_TAINT, mt, ex(h)),
            conclusion(x, listOf(g), Tail.ANY_TAINT, mt, ex(h)),
            conclusion(x, listOf(g, f), Tail.ANY_TAINT, mt, ex(h)),
            conclusion(x, listOf(g, f, h), Tail.ANY_TAINT, mt, ex(h)),
            conclusion(x, listOf(g, h), Tail.ANY_TAINT, mt, ex(h)),
            conclusion(x, listOf(g), Tail.EXACT, mt),
            conclusion(x, listOf(g, f), Tail.EXACT, mt),
            conclusion(x, listOf(g, f), Tail.ANY_TAINT, mu, ex(h)),
        )
        val added = taintBundle(inputs, x, Layer.NORMAL, ex(h))
        val recordPart = assertNotNull(ops.satisfying(added, j, forward, record = true))
        val insidePart = assertNotNull(ops.satisfying(recordPart, j, forward))
        val onlyPart = assertNotNull(ops.without(recordPart, insidePart))
        val expectedRecord = inputs.filter {
            val a = Pattern(it.fact, it.exclusion)
            applicable(premise, a) || inside(premise, a)
        }.toSet()
        val expectedInside = expectedRecord.filter { inside(premise, Pattern(it.fact, it.exclusion)) }.toSet()
        val expectedOnly = expectedRecord - expectedInside
        assertEquals(setOf(inputs[0], inputs[1]), expectedInside)
        assertEquals(setOf(inputs[2], inputs[3]), expectedOnly)
        assertEquals(expectedRecord, decode(listOf(recordPart)))
        assertEquals(expectedInside, decode(listOf(insidePart)))
        assertEquals(expectedOnly, decode(listOf(onlyPart)))
        for (part in listOf(recordPart, insidePart, onlyPart)) {
            assertEquals(Layer.NORMAL, part.layer)
            assertEquals(ex(h), (part as TaintTree).exclusion)
            assertTrue(decode(listOf(part)).all { it.fact.tail == Tail.ANY_TAINT && !it.demand })
        }

        val result = conclusion(y, listOf(f), Tail.ANY_TAINT, mu, ex(g))
        val summary = typed(result)
        val edge = summaryEdge(premise, result)
        for ((part, expectedInput) in listOf(insidePart to expectedInside, onlyPart to expectedOnly)) {
            val out = CollectingOut()
            ops.applySummary(part, j, summary, forward, out)
            assertNoRequests(out)
            val expected = expectedInput.mapNotNull { input ->
                when (val outcome = concat(input, edge, restricted = true)) {
                    EdgeOutcome.None -> null
                    is EdgeOutcome.Fact -> outcome.conclusion
                    else -> error("A satisfied native summary cannot request: $outcome")
                }
            }.toSet()
            assertEquals(setOf(result), expected)
            assertEquals(expected, decode(out.results))
            assertTrue(out.results.all { it.layer == Layer.NORMAL })
            assertTrue(out.results.filterIsInstance<TaintTree>().all { it.exclusion == ex(g) })
        }
        assertEquals(inputs.toSet(), decode(listOf(added)))
    }

    @Test
    fun `must premise mode guards reject run1 and backward selection`() {
        val must = pattern(x, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val input = typed(conclusion(x, listOf(g), Tail.EXACT, mt))
        for (mode in listOf(ApMode(true, Direction.FORWARD, 64), backward)) {
            assertFails { ops.satisfying(input, must, mode, record = true) }
            assertFails { ops.satisfying(input, manager.initial(must), mode) }
        }
        assertNull(reverseRecordLeaf(Direction.BACKWARD, must,
            conclusion(y, listOf(g), Tail.EXACT, mu)))
        assertNull(reverseRecordLeaf(Direction.BACKWARD, pattern(x, listOf(f), Tail.EXACT, mu),
            conclusion(y, listOf(g), Tail.ANY_TAINT, mt)))
    }

    @Test
    fun `malformed native views fail edge shape guards at application`() {
        val exactResult = conclusion(x, listOf(f), Tail.EXACT, mu)
        val malformed = listOf(
            RecordLeafView(pattern(y, listOf(g), Tail.EXACT, mt, ex(h)), exactResult),
            RecordLeafView(pattern(y, listOf(g), Tail.ANY, mt, ex(h)),
                conclusion(x, listOf(f), Tail.ANY, mu)),
            RecordLeafView(pattern(y, listOf(g), Tail.STAR, MarkSlot.Star(MarkSet(intArrayOf(u.id)))),
                conclusion(x, listOf(f), Tail.ANY, MarkSlot.STAR)),
            RecordLeafView(pattern(y, listOf(g), Tail.STAR, MarkSlot.STAR), exactResult),
            RecordLeafView(pattern(y, listOf(g), Tail.EXACT, mt),
                conclusion(x, listOf(f), Tail.STAR, MarkSlot.STAR)),
            RecordLeafView(pattern(y, listOf(g), Tail.ANY_TAINT, MarkSlot.STAR),
                conclusion(x, listOf(f), Tail.ANY, MarkSlot.STAR)),
        )
        val input = typed(conclusion(y, listOf(g), Tail.EXACT, mt))
        for (view in malformed) {
            val out = CollectingOut()
            assertFails(view.toString()) { ops.applyRecordLeaf(input, view, forward, out) }
            assertTrue(out.results.isEmpty())
            assertNoRequests(out)
        }
        val malformedNative = pattern(x, listOf(f), Tail.EXACT, mu, ex(g))
        assertNull(reverseRecordLeaf(Direction.FORWARD, malformedNative, exactResult))
        assertNull(reverseRecordLeaf(Direction.FORWARD, malformedNative,
            conclusion(y, listOf(g), Tail.ANY_TAINT, mt)))
    }

    @Test
    fun `without subtracts canonical exact parts while retaining must E and other group keys`() {
        val must = conclusion(x, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val exact = conclusion(x, listOf(g), Tail.EXACT, mt)
        val input = taintBundle(listOf(must, exact), x, Layer.NORMAL, ex(h))
        val remainder = assertNotNull(ops.without(input, typed(exact)))
        assertEquals(setOf(must), decode(listOf(remainder)))
        assertEquals(ex(h), (remainder as TaintTree).exclusion)
        assertNull(ops.without(input, input))
        for (foreign in listOf(
            typed(must.copy(exclusion = ex(f))),
            typed(exact.copy(demand = true)),
            typed(exact.copy(fact = exact.fact.copy(base = y))),
            typed(conclusion(x, listOf(g), Tail.STAR, MarkSlot.STAR, ex(h))),
        )) assertSame(input, ops.without(input, foreign))

        val flow = typed(conclusion(x, listOf(g), Tail.STAR, MarkSlot.Star(MarkSet(intArrayOf(t.id))), ex(h)))
        assertSame(flow, ops.without(flow, typed(conclusion(x, listOf(g), Tail.STAR, MarkSlot.STAR, ex(h)))))
        assertSame(flow, ops.without(flow, typed(conclusion(x, listOf(g), Tail.STAR,
            MarkSlot.Star(MarkSet(intArrayOf(t.id))), ex(f)))))
    }

    private fun nativeViews(): List<RecordLeafView> {
        val exact = pattern(x, listOf(f), Tail.EXACT, mu)
        val must = pattern(x, listOf(f), Tail.ANY_TAINT, mu, ex(g))
        val targetMust = conclusion(y, listOf(g), Tail.ANY_TAINT, mt, ex(h))
        val targetExact = conclusion(y, listOf(g), Tail.EXACT, mt)
        return listOf(exact to targetMust, must to targetMust, must to targetExact).map { (p, c) ->
            assertNotNull(reverseRecordLeaf(Direction.FORWARD, p, c))
        }
    }

    private fun directResults(view: RecordLeafView, inputs: Collection<Conclusion>, mode: ApMode): Set<Conclusion> =
        inputs.mapNotNull { input ->
            when (val outcome = concat(input, summaryEdge(view.premise, view.result),
                edgeDemand = view.result.demand, restricted = mode.restricted)) {
                EdgeOutcome.None -> null
                is EdgeOutcome.Fact -> outcome.conclusion
                else -> error("A selected record view cannot request: $outcome")
            }
        }.toSet()

    private fun apply(view: RecordLeafView, input: Facts, mode: ApMode): Set<Conclusion> {
        val out = CollectingOut()
        ops.applyRecordLeaf(input, view, mode, out)
        assertNoRequests(out)
        return decode(out.results)
    }

    private fun assertNoRequests(out: CollectingOut) {
        assertTrue(out.markRequests.isEmpty())
    }

    private fun typed(c: Conclusion): Facts =
        manager.factsOf(c.fact, c.exclusion, if (c.demand) Layer.DEMAND else Layer.NORMAL)
    private fun ex(vararg ids: Int): ExclusionSet = ExclusionSet.of(ids)
    private fun pattern(base: AccessPathBase, path: List<Int>, tail: Tail, mark: MarkSlot,
                        exclusion: ExclusionSet = empty) = Pattern(PathFact(base, path, tail, mark), exclusion)
    private fun conclusion(base: AccessPathBase, path: List<Int>, tail: Tail, mark: MarkSlot,
                           exclusion: ExclusionSet = empty, demand: Boolean = tail == Tail.ANY) =
        Conclusion(PathFact(base, path, tail, mark), exclusion, demand)

    private class Prefix {
        val leaves = mutableListOf<Conclusion>()
        val children = sortedMapOf<Int, Prefix>()
    }

    private fun taintBundle(leaves: List<Conclusion>, base: AccessPathBase, layer: Layer,
                            exclusion: ExclusionSet): TaintTree {
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
        return manager.taintTree(base, layer, build(prefix), exclusion)
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
                is Reach -> add(conclusion(AccessPathBase.Zero, emptyList(), Tail.EXACT,
                    MarkSlot.Concrete(TaintMark.ZERO), demand = demand))
                is FlowTree -> walk(value.root) { path, leaf ->
                    if (leaf == FlowLeaf.LEAF) add(conclusion(value.base, path, if (demand) Tail.ANY else Tail.STAR,
                        MarkSlot.Star(value.markExclusion), value.exclusion, demand))
                }
                is TaintTree -> walk(value.root) { path, leaf ->
                    for (id in leaf.exact.ids) add(conclusion(value.base, path, Tail.EXACT,
                        MarkSlot.Concrete(TaintMark(id)), demand = demand))
                    for (id in leaf.any.ids) add(conclusion(value.base, path, if (demand) Tail.ANY else Tail.ANY_TAINT,
                        MarkSlot.Concrete(TaintMark(id)), value.exclusion, demand))
                }
            }
        }
    }
}
