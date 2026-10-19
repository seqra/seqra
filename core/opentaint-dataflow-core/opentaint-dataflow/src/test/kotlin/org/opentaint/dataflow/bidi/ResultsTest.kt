package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Result grouping from ap-impl.md §5.2; expected roots use sparse constructors only. */
class ResultsTest {
    private val m = testApManager(Cancellation())
    private val base = AccessPathBase.Argument(0)
    private val f = m.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = m.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val t = m.marks.mark("T")
    private val e = ExclusionSet.of(f)

    @Test
    fun `flow results union equal keys and keep layers separate`() {
        val results = Results(m, base)
        val fNode = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f), arrayOf(FlowAlgebra.LEAF_NODE))
        val gNode = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(g), arrayOf(FlowAlgebra.LEAF_NODE))
        results.flow(Layer.NORMAL, e, MarkSet.EMPTY, fNode)
        results.flow(Layer.NORMAL, e, MarkSet.EMPTY, gNode)
        results.flow(Layer.DEMAND, e, MarkSet.EMPTY, fNode)
        val out = CollectingOut()
        results.flush(out)
        val expectedRoot = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, g),
            arrayOf(FlowAlgebra.LEAF_NODE, FlowAlgebra.LEAF_NODE))
        assertEquals(setOf(m.flowTree(base, Layer.NORMAL, e, MarkSet.EMPTY, expectedRoot),
            m.flowTree(base, Layer.DEMAND, ExclusionSet.Empty, MarkSet.EMPTY, fNode)), out.results.toSet())
        val second = CollectingOut()
        results.flush(second)
        assertTrue(second.results.isEmpty())
    }

    @Test
    fun `taint results canonicalize exact only and demand exclusions`() {
        val results = Results(m, base)
        val exact = m.taintAlg.leafNode(m.taintLeaves(m.markSetOf(t), MarkSet.EMPTY))
        val any = m.taintAlg.leafNode(m.taintLeaves(MarkSet.EMPTY, m.markSetOf(t)))
        results.taint(Layer.NORMAL, exact, e)
        results.taint(Layer.NORMAL, any, e)
        results.taint(Layer.DEMAND, any, e)
        val out = CollectingOut()
        results.flush(out)
        assertEquals(setOf(m.taintTree(base, Layer.NORMAL, exact),
            m.taintTree(base, Layer.NORMAL, any, e),
            m.taintTree(base, Layer.DEMAND, any)), out.results.toSet())
    }

    @Test
    fun `reach result bits deduplicate separately per layer`() {
        val results = Results(m, AccessPathBase.Zero)
        repeat(2) { results.reach(Layer.NORMAL); results.reach(Layer.DEMAND) }
        val out = CollectingOut()
        results.flush(out)
        assertEquals(listOf<Facts>(Reach.NORMAL, Reach.DEMAND), out.results)
        assertSame(Reach.NORMAL, out.results[0])
        assertSame(Reach.DEMAND, out.results[1])
    }

    @Test
    fun `collecting output retains each request and full result`() {
        val out = CollectingOut()
        val position = checkNotNull(m.path(intArrayOf(f)))
        repeat(2) { out.result(Reach.NORMAL); out.markRequest(t) }
        assertEquals(listOf<Facts>(Reach.NORMAL, Reach.NORMAL), out.results)
        assertEquals(listOf(t, t), out.markRequests)
    }

    @Test
    fun `equal-content normal flow results retain separate field and mark exclusions without T2 in both orders`(): Unit {
        val u = m.marks.mark("U")
        val keys = listOf(
            e to MarkSet.EMPTY,
            ExclusionSet.of(g) to MarkSet.EMPTY,
            e to MarkSet(intArrayOf(t.id)),
            e to MarkSet(intArrayOf(u.id)),
        )
        val at = FlowAlgebra.LEAF_NODE
        val below = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(g), arrayOf(at))
        val union = FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(g), arrayOf(at))
        for (order in listOf(keys, keys.reversed())) {
            val results = Results(m, base)
            for ((exclusion, marks) in order) results.flow(Layer.NORMAL, exclusion, marks, at)
            val first = CollectingOut()
            results.flush(first)
            assertEquals(keys.map { (exclusion, marks) -> m.flowTree(base, Layer.NORMAL, exclusion, marks, at) }.toSet(),
                first.results.toSet())
            assertEquals(keys.size, first.results.size)

            for ((exclusion, marks) in order) results.flow(Layer.NORMAL, exclusion, marks, below)
            for ((exclusion, marks) in order.reversed()) {
                results.flow(Layer.NORMAL, exclusion, marks, at)
                results.flow(Layer.NORMAL, exclusion, marks, below)
            }
            val merged = CollectingOut()
            results.flush(merged)
            assertEquals(keys.map { (exclusion, marks) -> m.flowTree(base, Layer.NORMAL, exclusion, marks, union) }.toSet(),
                merged.results.toSet())
            assertEquals(keys.size, merged.results.size)
            assertTrue(merged.markRequests.isEmpty())
        }
    }

    @Test
    fun `equal-content normal must results retain every exclusion and union same-key leaves without T2 in both orders`(): Unit {
        val exclusions = listOf(ExclusionSet.Empty, e, ExclusionSet.of(g))
        val payload = m.taintLeaves(MarkSet.EMPTY, m.markSetOf(t))
        val at = m.taintAlg.leafNode(payload)
        val below = m.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(g), arrayOf(at))
        val union = m.taintAlg.node(payload, intArrayOf(g), arrayOf(at))
        for (order in listOf(exclusions, exclusions.reversed())) {
            val results = Results(m, base)
            for (exclusion in order) results.taint(Layer.NORMAL, at, exclusion)
            val first = CollectingOut()
            results.flush(first)
            assertEquals(exclusions.map { m.taintTree(base, Layer.NORMAL, at, it) }.toSet(), first.results.toSet())
            assertEquals(exclusions.size, first.results.size)

            for (exclusion in order) results.taint(Layer.NORMAL, below, exclusion)
            for (exclusion in order.reversed()) {
                results.taint(Layer.NORMAL, at, exclusion)
                results.taint(Layer.NORMAL, below, exclusion)
            }
            val merged = CollectingOut()
            results.flush(merged)
            assertEquals(exclusions.map { m.taintTree(base, Layer.NORMAL, union, it) }.toSet(), merged.results.toSet())
            assertEquals(exclusions.size, merged.results.size)
            assertTrue(merged.markRequests.isEmpty())
        }
    }

    @Test
    fun `reach result flushing observes cancellation before each callback including the first`(): Unit {
        val results = Results(m, AccessPathBase.Zero)
        results.reach(Layer.NORMAL)
        results.reach(Layer.DEMAND)
        val observed = ArrayList<Facts>()
        val out = object : ApOut {
            override fun result(f: Facts) {
                observed += f
                m.cancellation.cancel()
            }
            override fun markRequest(mark: TaintMark) = error("No mark request is expected")
        }
        try {
            assertFailsWith<Cancellation.Cancelled> { results.flush(out) }
        } finally { m.cancellation.activate() }
        assertEquals(listOf<Facts>(Reach.NORMAL), observed)

        val beforeFirst = Results(m, AccessPathBase.Zero)
        beforeFirst.reach(Layer.NORMAL)
        val untouched = CollectingOut()
        m.cancellation.cancel()
        try {
            assertFailsWith<Cancellation.Cancelled> { beforeFirst.flush(untouched) }
        } finally { m.cancellation.activate() }
        assertTrue(untouched.results.isEmpty())
    }
}
