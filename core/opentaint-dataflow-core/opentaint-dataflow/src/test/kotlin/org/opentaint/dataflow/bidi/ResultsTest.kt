package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Result grouping from ap-impl.md §5.2; expected roots use sparse constructors only. */
class ResultsTest {
    private val m = ApManager(Cancellation())
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
        repeat(2) { out.result(Reach.NORMAL); out.markRequest(t); out.positionRequest(position) }
        assertEquals(listOf<Facts>(Reach.NORMAL, Reach.NORMAL), out.results)
        assertEquals(listOf(t, t), out.markRequests)
        assertEquals(listOf(position, position), out.positionRequests)
    }
}
