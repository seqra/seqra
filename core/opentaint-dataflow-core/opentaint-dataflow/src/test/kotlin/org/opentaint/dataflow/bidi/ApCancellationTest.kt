package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApCancellationTest {
    private val fixture = StoreFixtures()
    private val v = StoreValues(fixture)
    private val m = fixture.manager
    private val ops = fixture.ops
    private val mode = ApMode(false, Direction.FORWARD, 16)

    private data class Viewed(val facts: Facts, val patterns: Set<Pattern>)

    private fun views(): List<Viewed> = with(v) {
        buildList {
            for (layer in Layer.entries) {
                add(Viewed(Reach.of(layer), setOf(m.zero.toPattern())))
                val e = if (layer == Layer.NORMAL) ex(g) else ExclusionSet.Empty
                val flow = listOf(flow(p(), layer, e, marks(u.id)), flow(p(f), layer, e, marks(u.id)))
                add(Viewed(pack(*flow.toTypedArray()), flow.map { Pattern(it.fact, it.exclusion) }.toSet()))
                val any = if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY
                val taint = listOf(leaf(p(), mark = mt, layer = layer), leaf(p(), mark = mu, layer = layer),
                    leaf(p(), any, mt, layer, e), leaf(p(), any, mu, layer, e), leaf(p(f), mark = mt, layer = layer))
                add(Viewed(pack(*taint.toTypedArray()), taint.map { Pattern(it.fact, it.exclusion) }.toSet()))
            }
        }
    }

    @Test
    fun `leaf sequences stop after creation is followed by cancellation and remain reusable after activation`(): Unit {
        for ((facts, expected) in views()) {
            val values = ops.leaves(facts)
            fixture.cancellation.cancel()
            try {
                assertFailsWith<Cancellation.Cancelled> { values.iterator().next() }
            } finally { fixture.cancellation.activate() }
            assertEquals(expected, values.toSet(), facts.groupKey.toString())
        }
    }

    @Test
    fun `nearby leaf sequences stop after creation is followed by cancellation for every nonempty kind`(): Unit = with(v) {
        for ((facts, patterns) in views()) for (path in listOf(p(), p(f), p(f, f), p(g))) {
            val query = Pattern(PathFact(facts.base, path, Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            val expected = patterns.filter { overlap(it, query) }.toSet()
            val values = ops.leavesNear(facts, path.toIntArray())
            if (expected.isNotEmpty()) {
                fixture.cancellation.cancel()
                try {
                    assertFailsWith<Cancellation.Cancelled> { values.iterator().next() }
                } finally { fixture.cancellation.activate() }
            }
            assertEquals(expected, values.toSet(), "${facts.groupKey} $path")
        }
    }

    @Test
    fun `multi-leaf and multi-mark sequences check cancellation between values`(): Unit = with(v) {
        for ((facts, patterns) in views()) for (near in listOf(false, true)) for (path in listOf(p(), p(f))) {
            if (!near && path.isNotEmpty()) continue
            val query = Pattern(PathFact(facts.base, path, Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            val expected = if (near) patterns.filter { overlap(it, query) }.toSet() else patterns
            if (expected.size < 2) continue
            val values = if (near) ops.leavesNear(facts, path.toIntArray()) else ops.leaves(facts)
            val iterator = values.iterator()
            assertTrue(iterator.next() in expected)
            fixture.cancellation.cancel()
            try {
                assertFailsWith<Cancellation.Cancelled> { iterator.next() }
            } finally { fixture.cancellation.activate() }
            assertEquals(expected, values.toSet())
        }
    }

    @Test
    fun `both satisfying overloads stop before selection and preserve selected full leaves after activation`(): Unit = with(v) {
        for (layer in Layer.entries) {
            val literal = premise()
            val exact = leaf(p(), layer = layer)
            val input = pack(exact, leaf(p(), mark = mu, layer = layer), leaf(p(f), layer = layer))
            val flowLeaves = listOf(flow(p(), layer, if (layer == Layer.NORMAL) ex(g) else ExclusionSet.Empty),
                flow(p(f), layer, if (layer == Layer.NORMAL) ex(g) else ExclusionSet.Empty))
            val flowInitial = premise(tail = Tail.STAR, mark = MarkSlot.STAR)
            val cases = listOf(Triple(input, literal, setOf(exact)),
                Triple(pack(*flowLeaves.toTypedArray()), flowInitial, flowLeaves.toSet()),
                Triple(Reach.of(layer) as Facts, m.zero, setOf(leaf(p(), mark = MarkSlot.Concrete(TaintMark.ZERO), layer = layer,
                    base = org.opentaint.dataflow.ap.ifds.AccessPathBase.Zero))))
            for ((facts, initial, expected) in cases) for (record in listOf(false, true)) {
                val pattern = initial.toPattern()
                fixture.cancellation.cancel()
                try {
                    assertFailsWith<Cancellation.Cancelled> { ops.satisfying(facts, initial, mode, record) }
                    assertFailsWith<Cancellation.Cancelled> { ops.satisfying(facts, pattern, mode, record) }
                } finally { fixture.cancellation.activate() }
                assertEquals(expected, decoded(assertNotNull(ops.satisfying(facts, initial, mode, record))))
                assertEquals(expected, decoded(assertNotNull(ops.satisfying(facts, pattern, mode, record))))
            }
        }
    }

    private fun cancelledOutput(expected: Set<Conclusion>, apply: (CollectingOut) -> Unit) {
        val out = CollectingOut()
        fixture.cancellation.cancel()
        try {
            assertFailsWith<Cancellation.Cancelled> { apply(out) }
            assertTrue(out.results.isEmpty())
            assertTrue(out.markRequests.isEmpty())
        } finally { fixture.cancellation.activate() }
        apply(out)
        assertEquals(expected, out.results.flatMap { v.decoded(it) }.toSet())
        assertTrue(out.markRequests.isEmpty())
    }

    @Test
    fun `summary application stops before output and activation restores distinct target marks and must exclusions`(): Unit = with(v) {
        val initial = premise()
        val input = pack(leaf(p()))
        val targetLeaves = listOf(leaf(p(f), mark = mu, base = y), leaf(p(g), Tail.ANY_TAINT, e = ex(h), base = y))
        val summary = pack(*targetLeaves.toTypedArray())
        cancelledOutput(targetLeaves.toSet()) { out -> ops.applySummary(input, initial, summary, mode, out) }
    }

    @Test
    fun `native record application stops before output and activation restores exclusion-aware suffix dropping`(): Unit = with(v) {
        val source = Pattern(PathFact(x, p(f), Tail.EXACT, mu), ExclusionSet.Empty)
        val target = leaf(p(g), Tail.ANY_TAINT, mt, e = ex(h), base = y)
        val view = assertNotNull(reverseRecordLeaf(Direction.FORWARD, source, target))
        val backward = ApMode(false, Direction.BACKWARD, 16)
        for (layer in Layer.entries) {
            val input = pack(leaf(p(g, f, f), mark = mt, layer = layer, base = y))
            val selected = assertNotNull(ops.satisfying(input, view.premise, backward, record = true))
            cancelledOutput(setOf(leaf(p(f), mark = mu, layer = layer))) { out ->
                ops.applyRecordLeaf(selected, view, backward, out)
            }
        }
    }

    @Test
    fun `combination application stops before output and activation restores joined layers and normalized E`(): Unit = with(v) {
        val initial = listOf(premise(p(f)), m.initial(y, m.path(p(g)), Tail.EXACT, ExclusionSet.Empty, mu))
        val target = pack(leaf(p(h), Tail.ANY_TAINT, e = ex(f), base = y)) as TaintTree
        for (left in Layer.entries) for (right in Layer.entries) {
            val parts = listOf(pack(leaf(p(f), layer = left)) to initial[0],
                pack(leaf(p(g), mark = mu, layer = right, base = y)) to initial[1])
            val demand = left == Layer.DEMAND || right == Layer.DEMAND
            val expected = leaf(p(h), if (demand) Tail.ANY else Tail.ANY_TAINT,
                layer = if (demand) Layer.DEMAND else Layer.NORMAL,
                e = if (demand) ExclusionSet.Empty else ex(f), base = y)
            cancelledOutput(setOf(expected)) { out -> ops.applyCombination(parts, target, mode, out) }
        }
    }

    @Test
    fun `without stops before subtraction and activation restores the annotated remainder`(): Unit = with(v) {
        val exact = leaf(p(f), mark = mu)
        val must = leaf(p(), Tail.ANY_TAINT, e = ex(g))
        val input = pack(exact, must)
        val part = pack(exact)
        fixture.cancellation.cancel()
        try {
            assertFailsWith<Cancellation.Cancelled> { ops.without(input, part) }
        } finally { fixture.cancellation.activate() }
        assertEquals(setOf(must), decoded(assertNotNull(ops.without(input, part))))
        assertEquals(setOf(exact, must), decoded(input))
    }
}
