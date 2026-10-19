package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.FlowLeaf
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.TaintLeaves
import org.opentaint.dataflow.bidi.ap.TrieInterner
import org.opentaint.dataflow.bidi.ap.TrieLeaf
import org.opentaint.dataflow.bidi.ap.TrieNode
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InternerTest {
    private val manager = testApManager(Cancellation())
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val branchAccessors = intArrayOf(f, g).sortedArray()

    @Test
    fun `bottom up interning shares equal subtrees within one flow store`() {
        val interner = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val raw = flowTree()
        assertNotSame(raw.child(f), raw.child(g))

        val result = interner.intern(raw)
        assertTrue(result.interned)
        assertFalse(raw.interned)
        assertNotSame(raw, result)
        assertSame(result.child(f), result.child(g))
        assertSame(result, interner.intern(flowTree()))
        assertValuePreserved(raw, result)

        val shared = requireNotNull(result.child(f))
        assertTrue(shared.interned)
        assertEquals(FlowLeaf.LEAF, shared.leaf)
        assertTrue(requireNotNull(shared.child(g)).interned)
        assertEquals(FlowLeaf.LEAF, shared.child(g)?.leaf)
    }

    @Test
    fun `taint interning compares exact and any mark sets by value`() {
        val interner = TrieInterner<TaintLeaves>(manager.softRefs, manager.cancellation)
        val t = manager.marks.mark("T")
        val u = manager.marks.mark("U")
        fun leaf() = TaintLeaves(MarkSet(intArrayOf(t.id, u.id)), MarkSet(intArrayOf(u.id)))
        fun tree(): TrieNode<TaintLeaves> = TrieNode(
            TaintLeaves.EMPTY,
            branchAccessors,
            arrayOf(TrieNode(leaf(), null, null, false), TrieNode(leaf(), null, null, false)),
            false,
        )

        val raw = tree()
        val result = interner.intern(raw)
        assertSame(result.child(f), result.child(g))
        assertSame(result, interner.intern(tree()))
        assertValuePreserved(raw, result)
        assertTrue(result.hasAny)
        assertEquals(MarkSet(intArrayOf(t.id, u.id)), result.child(f)?.leaf?.exact)
        assertEquals(MarkSet(intArrayOf(u.id)), result.child(f)?.leaf?.any)

        val exactOnly = interner.intern(TrieNode(TaintLeaves(leaf().exact, MarkSet.EMPTY), null, null, false))
        val anyOnly = interner.intern(TrieNode(TaintLeaves(MarkSet.EMPTY, leaf().exact), null, null, false))
        assertNotSame(exactOnly, anyOnly)
        assertNotEquals(exactOnly, anyOnly)
        assertFalse(exactOnly.hasAny)
        assertTrue(anyOnly.hasAny)
    }

    @Test
    fun `canonical nodes remain unchanged across stores and cancelled calls`() {
        val first = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val second = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val canonical = first.intern(flowTree())
        assertSame(canonical, first.intern(canonical))
        assertSame(canonical, second.intern(canonical))

        manager.cancellation.cancel()
        assertSame(canonical, first.intern(canonical))
        assertSame(canonical, second.intern(canonical))
    }

    @Test
    fun `independent store tables retain structural equality and hashes`() {
        val first = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val second = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val a = first.intern(flowTree())
        val b = second.intern(flowTree())
        assertNotSame(a, b)
        assertNotSame(a.child(f), b.child(f))
        assertValuePreserved(a, b)
        assertSame(a, first.intern(flowTree()))
        assertSame(b, second.intern(flowTree()))
        assertEquals(1, hashSetOf(a, b, flowTree()).size)
    }

    @Test
    fun `clearing a managed cache changes sharing but preserves values`() {
        val interner = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val old = interner.intern(flowTree())
        assertSame(old, interner.intern(flowTree()))

        assertTrue(manager.softRefs.cleanup() >= 0)
        manager.softRefs.enable()
        val fresh = interner.intern(flowTree())
        assertNotSame(old, fresh)
        assertValuePreserved(old, fresh)
        assertSame(fresh, interner.intern(flowTree()))
        assertSame(old, interner.intern(old))
        assertEquals(1, hashSetOf(old, fresh).size)
    }

    @Test
    fun `disabled managed caches share within a call but retain no table across calls`() {
        val interner = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        interner.intern(flowTree())
        assertTrue(manager.softRefs.cleanup() >= 0)

        val a = interner.intern(flowTree())
        val b = interner.intern(flowTree())
        assertSame(a.child(f), a.child(g))
        assertSame(b.child(f), b.child(g))
        assertNotSame(a, b)
        assertValuePreserved(a, b)

        manager.softRefs.enable()
        val retained = interner.intern(flowTree())
        assertSame(retained, interner.intern(flowTree()))
        assertValuePreserved(a, retained)
    }

    @Test
    fun `intern policy admits minimum size only at each rate tick`() {
        val interner = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val raw = flowChain(IntArray(TrieInterner.MIN_SIZE_TO_INTERN.toInt() - 1) { f })
        assertEquals(TrieInterner.MIN_SIZE_TO_INTERN, raw.size)

        repeat(TrieInterner.INTERN_RATE - 1) { assertSame(raw, interner.internIfRequired(raw)) }
        val canonical = interner.internIfRequired(raw)
        assertTrue(canonical.interned)
        assertValuePreserved(raw, canonical)
        repeat(TrieInterner.INTERN_RATE - 1) { assertSame(raw, interner.internIfRequired(raw)) }
        assertSame(canonical, interner.internIfRequired(raw))
    }

    @Test
    fun `small inputs pass rate ticks unchanged until an eligible input arrives`() {
        val interner = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val small = flowChain(IntArray(TrieInterner.MIN_SIZE_TO_INTERN.toInt() - 2) { f })
        assertEquals(TrieInterner.MIN_SIZE_TO_INTERN - 1, small.size)
        repeat(TrieInterner.INTERN_RATE + 1) {
            assertSame(small, interner.internIfRequired(small))
            assertFalse(small.interned)
        }

        val eligible = flowChain(IntArray(TrieInterner.MIN_SIZE_TO_INTERN.toInt() - 1) { f })
        val canonical = interner.internIfRequired(eligible)
        assertTrue(canonical.interned)
        assertValuePreserved(eligible, canonical)
    }

    @Test
    fun `large shared subtrees force interning before a rate tick`() {
        val interner = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val raw = sharedFlowTree(17)
        assertEquals(262143L, raw.size)
        assertTrue(raw.size >= TrieInterner.SIZE_TO_FORCE_INTERN)

        val result = interner.internIfRequired(raw)
        assertTrue(result.interned)
        assertValuePreserved(raw, result)
        var node = result
        repeat(17) {
            assertTrue(node.interned)
            assertSame(node.child(f), node.child(g))
            node = requireNotNull(node.child(f))
        }
        assertTrue(node.interned)
        assertEquals(FlowLeaf.LEAF, node.leaf)

        val medium = flowChain(IntArray(TrieInterner.MIN_SIZE_TO_INTERN.toInt() - 1) { f })
        repeat(TrieInterner.INTERN_RATE - 1) { assertSame(medium, interner.internIfRequired(medium)) }
        assertTrue(interner.internIfRequired(medium).interned)
    }

    @Test
    fun `cancellation stops raw walks and a later active call can complete`() {
        val interner = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val raw = flowChain(IntArray(128) { f })
        val forced = sharedFlowTree(17)
        manager.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { interner.intern(raw) }
        assertFailsWith<Cancellation.Cancelled> { interner.internIfRequired(forced) }
        assertFalse(raw.interned)
        assertFalse(forced.interned)

        manager.cancellation.activate()
        val completed = interner.intern(raw)
        assertTrue(completed.interned)
        assertValuePreserved(raw, completed)
        assertSame(completed, interner.intern(raw))
    }

    @Test
    fun `interning and structural equality support a path with 32768 accessors`() {
        val path = IntArray(32768) { f }
        val raw = flowChain(path)
        val independentlyBuilt = flowChain(path)
        assertNotSame(raw, independentlyBuilt)
        assertEquals(32768, raw.boundedDepth)
        assertEquals(32769L, raw.size)
        assertValuePreserved(raw, independentlyBuilt)

        val first = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val second = TrieInterner<FlowLeaf>(manager.softRefs, manager.cancellation)
        val a = first.intern(raw)
        val b = second.intern(independentlyBuilt)
        assertNotSame(a, b)
        assertValuePreserved(raw, a)
        assertValuePreserved(a, b)
        assertSame(a, first.intern(flowChain(path)))

        var node = a
        for (accessor in path) {
            assertTrue(node.interned)
            assertEquals(FlowLeaf.NONE, node.leaf)
            assertContentEquals(intArrayOf(accessor), node.accessors)
            node = requireNotNull(node.child(accessor))
        }
        assertTrue(node.interned)
        assertEquals(FlowLeaf.LEAF, node.leaf)
        assertEquals(null, node.accessors)
        assertEquals(null, node.children)
    }

    private fun flowTree(): TrieNode<FlowLeaf> {
        fun subtree() = TrieNode(
            FlowLeaf.LEAF,
            intArrayOf(g),
            arrayOf(TrieNode(FlowLeaf.LEAF, null, null, false)),
            false,
        )
        return TrieNode(FlowLeaf.NONE, branchAccessors, arrayOf(subtree(), subtree()), false)
    }

    private fun flowChain(path: IntArray): TrieNode<FlowLeaf> {
        var node = TrieNode(FlowLeaf.LEAF, null, null, false)
        for (index in path.indices.reversed()) {
            node = TrieNode(FlowLeaf.NONE, intArrayOf(path[index]), arrayOf(node), false)
        }
        return node
    }

    private fun sharedFlowTree(depth: Int): TrieNode<FlowLeaf> {
        var node = TrieNode(FlowLeaf.LEAF, null, null, false)
        repeat(depth) {
            node = TrieNode(FlowLeaf.NONE, branchAccessors, arrayOf(node, node), false)
        }
        return node
    }

    private fun <P : TrieLeaf> assertValuePreserved(expected: TrieNode<P>, actual: TrieNode<P>) {
        assertEquals(expected, actual)
        assertEquals(expected.hash, actual.hash)
        assertEquals(expected.hashCode(), actual.hashCode())
        assertEquals(expected.boundedDepth, actual.boundedDepth)
        assertEquals(expected.size, actual.size)
        assertEquals(expected.hasAny, actual.hasAny)
    }
}
