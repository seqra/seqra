package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.FlowAlgebra
import org.opentaint.dataflow.bidi.ap.FlowLeaf
import org.opentaint.dataflow.bidi.ap.FlowNode
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.LeafAlgebra
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.TaintAlgebra
import org.opentaint.dataflow.bidi.ap.TaintLeaves
import org.opentaint.dataflow.bidi.ap.TrieLeaf
import org.opentaint.dataflow.bidi.ap.TrieNode
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Node and leaf contracts from ap-impl.md 4.1 and 4.5. These tests use no TrieOps. */
class TrieTest {
    private val manager = testApManager(Cancellation())
    private val taintAlg = TaintAlgebra(manager)
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val k = manager.accessors.index(FieldAccessor("Box", "k", "Box"))
    private val classC = manager.accessors.index(ClassStaticAccessor("Box"))
    private val element = manager.accessors.index(ElementAccessor)
    private val t = manager.marks.mark("T").id
    private val u = manager.marks.mark("U").id
    private val v = manager.marks.mark("V").id

    private fun marks(vararg ids: Int): MarkSet = MarkSet(ids)

    private fun assertLeaves(exact: IntArray, any: IntArray, actual: TaintLeaves) {
        assertContentEquals(exact, actual.exact.ids, "exact marks")
        assertContentEquals(any, actual.any.ids, "any marks")
        assertEquals(exact.isEmpty() && any.isEmpty(), actual.isEmpty)
        assertEquals(any.isNotEmpty(), actual.hasAny)
    }

    // Build the stated path bottom-up through the A03 constructor, without A04 prepend.
    private fun <P : TrieLeaf> chain(alg: LeafAlgebra<P>, path: IntArray, tip: P): TrieNode<P> {
        var node = alg.leafNode(tip)
        for (index in path.indices.reversed()) {
            node = alg.node(alg.empty, intArrayOf(path[index]), arrayOf(node))
        }
        return node
    }

    @Test
    fun `flow leaf operations follow the flag truth table`() {
        data class Row(val a: FlowLeaf, val b: FlowLeaf, val union: FlowLeaf, val minus: FlowLeaf)
        val rows = listOf(
            Row(FlowLeaf.NONE, FlowLeaf.NONE, FlowLeaf.NONE, FlowLeaf.NONE),
            Row(FlowLeaf.NONE, FlowLeaf.LEAF, FlowLeaf.LEAF, FlowLeaf.NONE),
            Row(FlowLeaf.LEAF, FlowLeaf.NONE, FlowLeaf.LEAF, FlowLeaf.LEAF),
            Row(FlowLeaf.LEAF, FlowLeaf.LEAF, FlowLeaf.LEAF, FlowLeaf.NONE),
        )
        assertSame(FlowLeaf.NONE, FlowAlgebra.empty)
        for (row in rows) {
            assertSame(row.union, FlowAlgebra.union(row.a, row.b), "union ${row.a}, ${row.b}")
            assertSame(row.minus, FlowAlgebra.minus(row.a, row.b), "minus ${row.a}, ${row.b}")
        }
        for (leaf in FlowLeaf.entries) {
            assertSame(leaf, FlowAlgebra.below(leaf))
            assertSame(leaf, FlowAlgebra.asAny(leaf))
            assertSame(leaf, FlowAlgebra.intern(leaf))
            assertEquals(leaf == FlowLeaf.NONE, leaf.isEmpty)
            assertFalse(leaf.hasAny)
        }
    }

    @Test
    fun `taint leaf construction interns equal payloads and keeps exact separate from any`() {
        assertSame(TaintLeaves.EMPTY, taintAlg.empty)
        assertSame(TaintLeaves.EMPTY, taintAlg.leaves(MarkSet.EMPTY, MarkSet.EMPTY))
        val leaf = taintAlg.leaves(marks(u, t, u), marks(v, u, v))
        assertLeaves(intArrayOf(t, u), intArrayOf(u, v), leaf)
        assertSame(leaf, taintAlg.leaves(marks(t, u), marks(u, v)))
        assertSame(leaf, taintAlg.intern(leaf))
        assertSame(taintAlg.leafNode(leaf), taintAlg.leafNode(taintAlg.leaves(marks(u, t), marks(v, u))))
        assertNotEquals(taintAlg.leaves(marks(t), MarkSet.EMPTY), taintAlg.leaves(MarkSet.EMPTY, marks(t)))
    }

    @Test
    fun `taint union and minus use each mark column independently`() {
        val a = taintAlg.leaves(marks(t, u), marks(u, v))
        val b = taintAlg.leaves(marks(u, v), marks(t, v))
        assertLeaves(intArrayOf(t, u, v), intArrayOf(t, u, v), taintAlg.union(a, b))
        assertLeaves(intArrayOf(t), intArrayOf(u), taintAlg.minus(a, b))
        assertLeaves(intArrayOf(v), intArrayOf(t), taintAlg.minus(b, a))
        assertSame(a, taintAlg.union(a, a))
        assertSame(TaintLeaves.EMPTY, taintAlg.minus(a, a))
        assertSame(a, taintAlg.union(a, taintAlg.empty))
        assertSame(a, taintAlg.union(taintAlg.empty, a))
        assertLeaves(intArrayOf(t, u), intArrayOf(u, v), taintAlg.minus(a, taintAlg.empty))
        assertSame(TaintLeaves.EMPTY, taintAlg.minus(taintAlg.empty, a))
        val exactT = taintAlg.leaves(marks(t), MarkSet.EMPTY)
        val anyT = taintAlg.leaves(MarkSet.EMPTY, marks(t))
        assertLeaves(intArrayOf(t), intArrayOf(), taintAlg.minus(exactT, anyT))
        assertLeaves(intArrayOf(), intArrayOf(t), taintAlg.minus(anyT, exactT))
    }

    @Test
    fun `taint descendant cover and cut preserve the specified marks`() {
        val mixed = taintAlg.leaves(marks(t, u), marks(u, v))
        assertLeaves(intArrayOf(u, v), intArrayOf(u, v), taintAlg.below(mixed))
        assertLeaves(intArrayOf(), intArrayOf(u, v), taintAlg.belowNormal(mixed))
        assertLeaves(intArrayOf(), intArrayOf(t, u, v), taintAlg.asAny(mixed))
        assertLeaves(intArrayOf(), intArrayOf(u, v), taintAlg.belowIn(Layer.NORMAL)(mixed))
        assertLeaves(intArrayOf(u, v), intArrayOf(u, v), taintAlg.belowIn(Layer.DEMAND)(mixed))
        val exact = taintAlg.leaves(marks(t), MarkSet.EMPTY)
        assertSame(TaintLeaves.EMPTY, taintAlg.below(exact))
        assertSame(TaintLeaves.EMPTY, taintAlg.belowNormal(exact))
        assertLeaves(intArrayOf(), intArrayOf(t), taintAlg.asAny(exact))
        assertSame(TaintLeaves.EMPTY, taintAlg.below(taintAlg.empty))
        assertSame(TaintLeaves.EMPTY, taintAlg.belowNormal(taintAlg.empty))
        assertSame(TaintLeaves.EMPTY, taintAlg.asAny(taintAlg.empty))
    }

    @Test
    fun `taint payload equality and hash use values across separate algebra tables`() {
        val otherAlg = TaintAlgebra(manager)
        val first = taintAlg.leaves(marks(t, u), marks(v))
        val second = otherAlg.leaves(marks(u, t), marks(v))
        assertNotSame(first, second)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, otherAlg.leaves(marks(t), marks(u, v)))
        assertFalse(first.equals(FlowLeaf.LEAF))
    }

    @Test
    fun `leaf-only factories normalize empty arrays and keep one node per payload`() {
        for (leaf in FlowLeaf.entries) {
            val node = FlowAlgebra.node(leaf, null, null)
            assertSame(node, FlowAlgebra.leafNode(leaf))
            assertSame(node, FlowAlgebra.node(leaf, intArrayOf(), emptyArray()))
            assertNull(node.accessors)
            assertNull(node.children)
            assertEquals(leaf.isEmpty, node.isEmpty)
            assertEquals(0, node.boundedDepth)
            assertEquals(1L, node.size)
            assertFalse(node.hasAny)
            assertTrue(node.interned)
        }
        val leaf = taintAlg.leaves(marks(t), marks(u))
        val node = taintAlg.node(leaf, intArrayOf(), emptyArray())
        assertSame(node, taintAlg.leafNode(leaf))
        assertEquals(0, node.boundedDepth)
        assertEquals(1L, node.size)
        assertTrue(node.hasAny)
        assertFalse(node.isEmpty)
        assertTrue(taintAlg.leafNode(taintAlg.empty).isEmpty)
    }

    @Test
    fun `sparse sorted children retain their accessor association`() {
        val leaf = FlowAlgebra.LEAF_NODE
        val branch = chain(FlowAlgebra, intArrayOf(g), FlowLeaf.LEAF)
        val accessors = intArrayOf(f, classC, h, element).sortedArray()
        val children = accessors.map { if (it == classC || it == h) branch else leaf }.toTypedArray()
        val root = FlowAlgebra.node(FlowLeaf.NONE, accessors, children)
        assertContentEquals(accessors, root.accessors)
        assertSame(leaf, root.child(f))
        assertSame(branch, root.child(classC))
        assertSame(branch, root.child(h))
        assertSame(leaf, root.child(element))
        assertNull(root.child(g))
        assertNull(root.child(k))
        assertNull(root.child(-1))
        assertFalse(root.isEmpty)
        assertFalse(root.interned)
        assertNull(leaf.child(f))
    }

    @Test
    fun `node factories reject mismatched arrays order and duplicate accessors`() {
        val leaf = FlowAlgebra.LEAF_NODE
        val empty = FlowAlgebra.leafNode(FlowLeaf.NONE)
        assertFails { FlowAlgebra.node(FlowLeaf.NONE, null, arrayOf(leaf)) }
        assertFails { FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f), null) }
        assertFails { FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f), emptyArray()) }
        assertFails { FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, g), arrayOf(leaf)) }
        assertFails { FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f), arrayOf(leaf, leaf)) }
        assertFails { FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(), arrayOf(leaf)) }
        assertFails { FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(g, f), arrayOf(leaf, leaf)) }
        assertFails { FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, f), arrayOf(leaf, leaf)) }
        assertFails { FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f), arrayOf(empty)) }
        assertFails { FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(f), arrayOf(empty)) }
        val taintLeaf = taintAlg.leafNode(taintAlg.leaves(marks(t), MarkSet.EMPTY))
        val taintEmpty = taintAlg.leafNode(taintAlg.empty)
        assertFails { taintAlg.node(taintAlg.empty, null, arrayOf(taintLeaf)) }
        assertFails { taintAlg.node(taintAlg.empty, intArrayOf(f), null) }
        assertFails { taintAlg.node(taintAlg.empty, intArrayOf(f), emptyArray()) }
        assertFails { taintAlg.node(taintAlg.empty, intArrayOf(f, g), arrayOf(taintLeaf)) }
        assertFails { taintAlg.node(taintAlg.empty, intArrayOf(g, f), arrayOf(taintLeaf, taintLeaf)) }
        assertFails { taintAlg.node(taintAlg.empty, intArrayOf(f, f), arrayOf(taintLeaf, taintLeaf)) }
        assertFails { taintAlg.node(taintAlg.empty, intArrayOf(f), arrayOf(taintEmpty)) }
    }

    @Test
    fun `node factories reject tail mark value type and negative accessor IDs`() {
        val invalid = intArrayOf(
            -1, Int.MIN_VALUE,
            AccessorInterner.FINAL_ACCESSOR_IDX,
            AccessorInterner.VALUE_ACCESSOR_IDX,
            AccessorInterner.ANY_ACCESSOR_IDX,
            AccessorInterner.TYPE_INFO_GROUP_ACCESSOR_IDX,
            AccessorInterner.TAINT_KIND,
            AccessorInterner.TYPES_KIND,
        )
        val taintLeaf = taintAlg.leafNode(taintAlg.leaves(marks(t), MarkSet.EMPTY))
        for (accessor in invalid) {
            assertFails("FLOW path accessor $accessor") {
                FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(accessor), arrayOf(FlowAlgebra.LEAF_NODE))
            }
            assertFails("TAINT path accessor $accessor") {
                taintAlg.node(taintAlg.empty, intArrayOf(accessor), arrayOf(taintLeaf))
            }
        }
    }

    private fun <P : TrieLeaf> assertOwnedArrays(alg: LeafAlgebra<P>, first: TrieNode<P>, second: TrieNode<P>) {
        val accessors = intArrayOf(f, g)
        val children = arrayOf(first, second)
        val root = alg.node(alg.empty, accessors, children)
        val hash = root.hash
        val depth = root.boundedDepth
        val size = root.size
        val expected = alg.node(alg.empty, intArrayOf(f, g), arrayOf(first, second))
        accessors[0] = h
        children[0] = second
        val accessorSnapshot = requireNotNull(root.accessors)
        val childSnapshot = requireNotNull(root.children)
        accessorSnapshot[0] = k
        childSnapshot[1] = first
        assertNotSame(accessorSnapshot, root.accessors)
        assertNotSame(childSnapshot, root.children)
        assertContentEquals(intArrayOf(f, g), root.accessors)
        assertSame(first, root.child(f))
        assertSame(second, root.child(g))
        assertNull(root.child(h))
        assertEquals(expected, root)
        assertEquals(hash, root.hash)
        assertEquals(expected.hashCode(), root.hashCode())
        assertEquals(depth, root.boundedDepth)
        assertEquals(size, root.size)
    }

    @Test
    fun `flow nodes own input arrays and return public snapshots`() {
        assertOwnedArrays(FlowAlgebra, FlowAlgebra.LEAF_NODE,
            chain(FlowAlgebra, intArrayOf(h), FlowLeaf.LEAF))
    }

    @Test
    fun `taint nodes own input arrays and return public snapshots`() {
        val exact = taintAlg.leafNode(taintAlg.leaves(marks(t), MarkSet.EMPTY))
        val any = chain(taintAlg, intArrayOf(h), taintAlg.leaves(MarkSet.EMPTY, marks(u)))
        assertOwnedArrays(taintAlg, exact, any)
    }

    @Test
    fun `structural equality and hash do not depend on shared children`() {
        val shared = chain(FlowAlgebra, intArrayOf(h, k), FlowLeaf.LEAF)
        val withSharing = FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(f, g), arrayOf(shared, shared))
        val copyA = chain(FlowAlgebra, intArrayOf(h, k), FlowLeaf.LEAF)
        val copyB = chain(FlowAlgebra, intArrayOf(h, k), FlowLeaf.LEAF)
        val withoutSharing = FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(f, g), arrayOf(copyA, copyB))
        assertNotSame(shared, copyA)
        assertNotSame(copyA, copyB)
        assertEquals(withSharing, withoutSharing)
        assertEquals(withoutSharing, withSharing)
        assertEquals(withSharing.hash, withoutSharing.hash)
        assertEquals(withSharing.hashCode(), withoutSharing.hashCode())
        assertEquals(7L, withSharing.size)
        assertEquals(7L, withoutSharing.size)
        assertEquals(3, withSharing.boundedDepth)
        assertNotEquals(withSharing, FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, g), arrayOf(copyA, copyB)))
        assertNotEquals(withSharing, FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(f, h), arrayOf(copyA, copyB)))
        assertNotEquals(withSharing,
            FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(f, g), arrayOf(copyA, FlowAlgebra.LEAF_NODE)))
        assertFalse(withSharing.equals(null))
        assertFalse(withSharing.equals("trie"))
    }

    @Test
    fun `taint nodes compare structurally across leaf algebra tables`() {
        val otherAlg = TaintAlgebra(manager)
        val a = chain(taintAlg, intArrayOf(classC, f, element), taintAlg.leaves(marks(t), marks(u)))
        val b = chain(otherAlg, intArrayOf(classC, f, element), otherAlg.leaves(marks(t), marks(u)))
        assertNotSame(a, b)
        assertEquals(a, b)
        assertEquals(a.hash, b.hash)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(2, a.boundedDepth)
        assertFalse(a.equals(chain(FlowAlgebra, intArrayOf(classC, f, element), FlowLeaf.LEAF)))
    }

    @Test
    fun `structural hash includes each child accessor`() {
        val atF = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f), arrayOf(FlowAlgebra.LEAF_NODE))
        val atG = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(g), arrayOf(FlowAlgebra.LEAF_NODE))
        assertNotEquals(atF, atG)
        assertNotEquals(atF.hash, atG.hash)
    }

    @Test
    fun `bounded depth counts fields and elements and skips every class accessor`() {
        val path = intArrayOf(classC, f, element, classC, g)
        val root = chain(FlowAlgebra, path, FlowLeaf.LEAF)
        assertEquals(3, root.boundedDepth)
        assertEquals(6L, root.size)
        assertEquals(3, requireNotNull(root.child(classC)).boundedDepth)
        assertEquals(0, chain(FlowAlgebra, intArrayOf(classC, classC), FlowLeaf.LEAF).boundedDepth)
        val classBranch = chain(FlowAlgebra, intArrayOf(element, h), FlowLeaf.LEAF)
        val fieldBranch = chain(FlowAlgebra, intArrayOf(g, h, k), FlowLeaf.LEAF)
        val accessors = intArrayOf(classC, f).sortedArray()
        val children = accessors.map { if (it == classC) classBranch else fieldBranch }.toTypedArray()
        val branching = FlowAlgebra.node(FlowLeaf.LEAF, accessors, children)
        assertEquals(4, branching.boundedDepth)
        assertEquals(8L, branching.size)
    }

    @Test
    fun `size counts a shared subtree again for each incoming child`() {
        var root: FlowNode = FlowAlgebra.LEAF_NODE
        repeat(32) { root = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, g), arrayOf(root, root)) }
        assertEquals(8_589_934_591L, root.size)
        assertEquals(32, root.boundedDepth)
        assertSame(root.child(f), root.child(g))
    }

    @Test
    fun `shared binary DAG size reaches Long max and rejects the next level`() {
        var root: FlowNode = FlowAlgebra.LEAF_NODE
        repeat(62) { root = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, g), arrayOf(root, root)) }
        assertEquals(Long.MAX_VALUE, root.size)
        assertEquals(62, root.boundedDepth)
        assertFailsWith<ArithmeticException> {
            FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, g), arrayOf(root, root))
        }
        assertEquals(Long.MAX_VALUE, root.size)
    }

    @Test
    fun `independent 32-level shared DAGs compare by value and detect a changed tip leaf`() {
        fun dag(alg: TaintAlgebra, mark: Int): TrieNode<TaintLeaves> {
            var root = alg.leafNode(alg.leaves(marks(mark), MarkSet.EMPTY))
            repeat(32) { root = alg.node(alg.empty, intArrayOf(f, g), arrayOf(root, root)) }
            return root
        }
        val otherAlg = TaintAlgebra(manager)
        val a = dag(taintAlg, t)
        val b = dag(otherAlg, t)
        assertNotSame(a, b)
        assertNotSame(a.child(f), b.child(f))
        assertSame(a.child(f), a.child(g))
        assertSame(b.child(f), b.child(g))
        assertEquals(8_589_934_591L, a.size)
        assertEquals(32, a.boundedDepth)
        assertEquals(a, b)
        assertEquals(b, a)
        assertEquals(a.hash, b.hash)
        assertEquals(a.hashCode(), b.hashCode())
        val differentTip = dag(otherAlg, u)
        assertEquals(a.size, differentTip.size)
        assertEquals(a.boundedDepth, differentTip.boundedDepth)
        assertNotEquals(a, differentTip)
        assertNotEquals(differentTip, a)
    }

    @Test
    fun `hasAny is cached from the node leaf and every descendant`() {
        val exact = taintAlg.leafNode(taintAlg.leaves(marks(t), MarkSet.EMPTY))
        val any = taintAlg.leafNode(taintAlg.leaves(MarkSet.EMPTY, marks(u)))
        assertFalse(exact.hasAny)
        assertTrue(any.hasAny)
        assertFalse(chain(FlowAlgebra, intArrayOf(f, g), FlowLeaf.LEAF).hasAny)
        assertFalse(chain(taintAlg, intArrayOf(f, g), exact.leaf).hasAny)
        assertTrue(chain(taintAlg, intArrayOf(f, g), any.leaf).hasAny)
        val aboveAny = taintAlg.node(exact.leaf, intArrayOf(f), arrayOf(any))
        val atAny = taintAlg.node(any.leaf, intArrayOf(f), arrayOf(exact))
        assertTrue(aboveAny.hasAny)
        assertTrue(atAny.hasAny)
        assertFalse(aboveAny.isEmpty)
        assertFalse(taintAlg.node(taintAlg.empty, intArrayOf(f), arrayOf(exact)).isEmpty)
    }

    @Test
    fun `32768 counted edges retain exact depth and structural equality`() {
        val path = IntArray(32_768) { f }
        val a = chain(FlowAlgebra, path, FlowLeaf.LEAF)
        val b = chain(FlowAlgebra, path, FlowLeaf.LEAF)
        assertNotSame(a, b)
        assertEquals(32_768, a.boundedDepth)
        assertEquals(32_769L, a.size)
        assertEquals(a, b)
        assertEquals(b, a)
        assertEquals(a.hash, b.hash)
        assertEquals(a.hashCode(), b.hashCode())
        path[path.lastIndex] = g
        val different = chain(FlowAlgebra, path, FlowLeaf.LEAF)
        assertNotEquals(a, different)
        assertEquals(32_768, different.boundedDepth)
    }
}
