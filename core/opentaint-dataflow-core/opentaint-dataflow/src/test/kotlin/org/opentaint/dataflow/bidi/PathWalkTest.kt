package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.FlowAlgebra
import org.opentaint.dataflow.bidi.ap.FlowLeaf
import org.opentaint.dataflow.bidi.ap.FlowNode
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.TaintAlgebra
import org.opentaint.dataflow.bidi.ap.TaintNode
import org.opentaint.dataflow.bidi.ap.walk
import org.opentaint.dataflow.bidi.ap.walkPath
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Proper-prefix callbacks from ap-impl.md 4.6, including the node where a child is missing. */
class PathWalkTest {
    private val manager = ApManager(Cancellation())
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val element = manager.accessors.index(ElementAccessor)

    private class Node(val name: String, val children: Map<Int, Node> = emptyMap())

    @Test
    fun `generic complete walk calls each proper prefix before its child lookup`() {
        val tip = Node("tip")
        val middle = Node("middle", mapOf(g to tip))
        val root = Node("root", mapOf(f to middle))
        val events = arrayListOf<String>()
        val path = intArrayOf(f, g)
        val result = walkPath(root, path, { n, a ->
            events += "child ${n.name} $a"
            n.children[a]
        }) { d, n -> events += "prefix $d ${n.name}" }
        assertSame(tip, result)
        assertEquals(listOf("prefix 0 root", "child root $f", "prefix 1 middle", "child middle $g"), events)
        assertContentEquals(intArrayOf(f, g), path)
    }

    @Test
    fun `generic missing walk includes the failed prefix and stops there`() {
        val middle = Node("middle")
        val root = Node("root", mapOf(f to middle))
        val events = arrayListOf<String>()
        val result = walkPath(root, intArrayOf(f, g, h), { n, a ->
            events += "child ${n.name} $a"
            n.children[a]
        }) { d, n -> events += "prefix $d ${n.name}" }
        assertNull(result)
        assertEquals(listOf("prefix 0 root", "child root $f", "prefix 1 middle", "child middle $g"), events)
        events.clear()
        assertNull(walkPath(root, intArrayOf(h, f), { n, a ->
            events += "child ${n.name} $a"
            n.children[a]
        }) { d, n -> events += "prefix $d ${n.name}" })
        assertEquals(listOf("prefix 0 root", "child root $h"), events)
    }

    @Test
    fun `generic empty path returns the root without callbacks or child lookups`() {
        val root = Node("root")
        var prefixCalls = 0
        var childCalls = 0
        assertSame(root, walkPath(root, intArrayOf(), { _, _ ->
            childCalls++
            null
        }) { _, _ -> prefixCalls++ })
        assertEquals(0, prefixCalls)
        assertEquals(0, childCalls)
    }

    @Test
    fun `flow trie walk returns the exact node and proper prefixes for complete missing and empty paths`() {
        val tip = FlowAlgebra.LEAF_NODE
        val middle = FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(element), arrayOf(tip))
        val root = FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(f), arrayOf(middle))
        val prefixes = arrayListOf<Pair<Int, FlowNode>>()
        assertSame(tip, root.walk(intArrayOf(f, element)) { d, n -> prefixes += d to n })
        assertEquals(listOf(0 to root, 1 to middle), prefixes)
        prefixes.clear()
        assertSame(middle, root.walk(intArrayOf(f)) { d, n -> prefixes += d to n })
        assertEquals(listOf(0 to root), prefixes)
        prefixes.clear()
        assertNull(root.walk(intArrayOf(f, g, h)) { d, n -> prefixes += d to n })
        assertEquals(listOf(0 to root, 1 to middle), prefixes)
        prefixes.clear()
        assertNull(root.walk(intArrayOf(g)) { d, n -> prefixes += d to n })
        assertEquals(listOf(0 to root), prefixes)
        prefixes.clear()
        assertSame(root, root.walk(intArrayOf()) { d, n -> prefixes += d to n })
        assertEquals(emptyList(), prefixes)
        assertSame(tip, root.walk(intArrayOf(f, element)))
    }

    @Test
    fun `taint trie walk uses the same complete and failed prefix contract`() {
        val alg = TaintAlgebra(manager)
        val t = manager.marks.mark("T")
        val tip = alg.leafNode(alg.leaves(MarkSet(intArrayOf(t.id)), MarkSet.EMPTY))
        val middle = alg.node(alg.empty, intArrayOf(g), arrayOf(tip))
        val root = alg.node(alg.empty, intArrayOf(f), arrayOf(middle))
        val prefixes = arrayListOf<Pair<Int, TaintNode>>()
        assertSame(tip, root.walk(intArrayOf(f, g)) { d, n -> prefixes += d to n })
        assertEquals(listOf(0 to root, 1 to middle), prefixes)
        prefixes.clear()
        assertNull(root.walk(intArrayOf(f, h)) { d, n -> prefixes += d to n })
        assertEquals(listOf(0 to root, 1 to middle), prefixes)
        prefixes.clear()
        assertSame(root, root.walk(intArrayOf()) { d, n -> prefixes += d to n })
        assertEquals(emptyList(), prefixes)
    }

    @Test
    fun `32768-edge trie walk is iterative for complete and failed paths`() {
        val path = IntArray(32_768) { f }
        val tip = FlowAlgebra.LEAF_NODE
        var root = tip
        for (accessor in path) root = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(accessor), arrayOf(root))
        var prefixCount = 0
        assertSame(tip, root.walk(path) { d, n ->
            assertEquals(prefixCount, d)
            assertEquals(32_768 - d, n.boundedDepth)
            prefixCount++
        })
        assertEquals(32_768, prefixCount)
        val missing = path.copyOf()
        missing[missing.lastIndex] = g
        prefixCount = 0
        assertNull(root.walk(missing) { d, n ->
            assertEquals(prefixCount, d)
            assertEquals(32_768 - d, n.boundedDepth)
            prefixCount++
        })
        assertEquals(32_768, prefixCount)
        assertContentEquals(IntArray(32_768) { f }, path)
    }
}
