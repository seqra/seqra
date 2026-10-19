package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Raw path sets supply the union and delta oracle. No ApOps or production enumerator decodes results. */
class TrieMergeDeltaTest {
    private val manager = testApManager(Cancellation())
    private val taintAlg = manager.taintAlg
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val k = manager.accessors.index(FieldAccessor("Box", "k", "Box"))
    private val c = manager.accessors.index(ClassStaticAccessor("Box"))
    private val element = manager.accessors.index(ElementAccessor)
    private val t = manager.marks.mark("T").id
    private val u = manager.marks.mark("U").id
    private val v = manager.marks.mark("V").id

    private fun p(vararg accessors: Int): List<Int> = accessors.toList()
    private fun marks(vararg ids: Int): MarkSet = MarkSet(ids)
    private fun leaf(exact: Set<Int> = emptySet(), any: Set<Int> = emptySet()): TaintLeaves =
        taintAlg.leaves(MarkSet(exact.toIntArray()), MarkSet(any.toIntArray()))
    private data class Payload(val exact: Set<Int> = emptySet(), val any: Set<Int> = emptySet())
    private data class Atom(val path: List<Int>, val exact: Boolean, val mark: Int)
    private class Draft<P : TrieLeaf>(var leaf: P) {
        val children = sortedMapOf<Int, Draft<P>>()
    }

    /** Sparse fixture construction uses only A03 nodes, and only short paths. */
    private fun <P : TrieLeaf> sparse(alg: LeafAlgebra<P>, leaves: Map<List<Int>, P>): TrieNode<P> {
        val root = Draft(alg.empty)
        for ((path, payload) in leaves) {
            var tip = root
            for (accessor in path) tip = tip.children.getOrPut(accessor) { Draft(alg.empty) }
            tip.leaf = payload
        }
        fun build(draft: Draft<P>): TrieNode<P> = alg.node(draft.leaf,
            draft.children.keys.toIntArray(), draft.children.values.map(::build).toTypedArray())
        return build(root)
    }

    private fun flow(vararg paths: List<Int>): FlowNode =
        sparse(FlowAlgebra, paths.associateWith { FlowLeaf.LEAF })
    private fun taint(leaves: Map<List<Int>, Payload>): TaintNode =
        sparse(taintAlg, leaves.mapValues { (_, payload) -> leaf(payload.exact, payload.any) })

    /** Walk node fields directly. A shared subtree contributes once for each distinct path. */
    private fun <P : TrieLeaf> raw(root: TrieNode<P>?): Map<List<Int>, P> {
        if (root == null) return emptyMap()
        val result = linkedMapOf<List<Int>, P>()
        val pending = ArrayDeque<Pair<List<Int>, TrieNode<P>>>()
        pending.addLast(emptyList<Int>() to root)
        while (pending.isNotEmpty()) {
            val (path, node) = pending.removeLast()
            if (!node.leaf.isEmpty) result[path] = node.leaf
            val accessors = node.accessors ?: continue
            val children = requireNotNull(node.children)
            for (index in accessors.indices.reversed()) pending.addLast((path + accessors[index]) to children[index])
        }
        return result
    }

    private fun atoms(root: TaintNode?): Set<Atom> = buildSet {
        for ((path, payload) in raw(root)) {
            for (mark in payload.exact.ids) add(Atom(path, true, mark))
            for (mark in payload.any.ids) add(Atom(path, false, mark))
        }
    }

    private fun literalAtoms(leaves: Map<List<Int>, Payload>): Set<Atom> = buildSet {
        for ((path, payload) in leaves) {
            for (mark in payload.exact) add(Atom(path, true, mark))
            for (mark in payload.any) add(Atom(path, false, mark))
        }
    }

    private fun <P : TrieLeaf> bottomUp(alg: LeafAlgebra<P>, path: IntArray, tip: P): TrieNode<P> {
        var node = alg.leafNode(tip)
        for (index in path.indices.reversed()) node = alg.node(alg.empty, intArrayOf(path[index]), arrayOf(node))
        return node
    }

    private fun <P : TrieLeaf> tip(root: TrieNode<P>, path: IntArray): TrieNode<P> {
        var node = root
        for (accessor in path) node = requireNotNull(node.child(accessor))
        return node
    }

    @Test
    fun `flow union and new-only delta match path sets in both input orders`() {
        val cases = listOf(
            emptySet(), setOf(p()), setOf(p(f)), setOf(p(), p(f), p(f, g), p(h)),
            setOf(p(f, f, f), p(element, element, element), p(c, f, c)),
        )
        for ((ai, aPaths) in cases.withIndex()) for ((bi, bPaths) in cases.withIndex()) {
            val a = flow(*aPaths.toTypedArray())
            val b = flow(*bPaths.toTypedArray())
            val expected = aPaths + bPaths
            val added = bPaths - aPaths
            val merged = FlowAlgebra.mergeAdd(a, b)
            val (withDelta, delta) = FlowAlgebra.mergeAddDelta(a, b)
            assertEquals(expected, raw(merged).keys, "union $ai <- $bi")
            assertEquals(expected, raw(withDelta).keys, "delta union $ai <- $bi")
            assertEquals(added, raw(delta).keys, "new-only delta $ai <- $bi")
            if (added.isEmpty()) {
                assertSame(a, merged, "unchanged union $ai <- $bi")
                assertSame(a, withDelta, "unchanged delta union $ai <- $bi")
                assertNull(delta)
            }
        }
    }

    @Test
    fun `taint union and new-only delta keep exact and any mark columns separate`() {
        val cases = listOf(
            emptyMap(), mapOf(p() to Payload(setOf(t))),
            mapOf(p() to Payload(any = setOf(t)), p(f) to Payload(setOf(u))),
            mapOf(p() to Payload(setOf(t, u), setOf(u)), p(f, f) to Payload(setOf(v), setOf(t))),
            mapOf(p(f) to Payload(setOf(t), setOf(u)),
                p(g, element, element, c) to Payload(setOf(v), setOf(u))),
        )
        for ((ai, aLeaves) in cases.withIndex()) for ((bi, bLeaves) in cases.withIndex()) {
            val a = taint(aLeaves)
            val b = taint(bLeaves)
            val aAtoms = literalAtoms(aLeaves)
            val bAtoms = literalAtoms(bLeaves)
            val merged = taintAlg.mergeAdd(a, b)
            val (withDelta, delta) = taintAlg.mergeAddDelta(a, b)
            assertEquals(aAtoms + bAtoms, atoms(merged), "union $ai <- $bi")
            assertEquals(aAtoms + bAtoms, atoms(withDelta), "delta union $ai <- $bi")
            assertEquals(bAtoms - aAtoms, atoms(delta), "new-only delta $ai <- $bi")
            if ((bAtoms - aAtoms).isEmpty()) {
                assertSame(a, merged)
                assertSame(a, withDelta)
                assertNull(delta)
            }
        }
    }

    @Test
    fun `unchanged merge inputs keep identity and changed paths retain other subtrees`() {
        val a = flow(p(f, h), p(g, k))
        val b = flow(p(f, g))
        assertSame(a, FlowAlgebra.mergeAdd(a, a))
        val (same, noDelta) = FlowAlgebra.mergeAddDelta(a, a)
        assertSame(a, same)
        assertNull(noDelta)
        val (merged, delta) = FlowAlgebra.mergeAddDelta(a, b)
        assertSame(a.child(g), merged.child(g))
        assertSame(a.child(f)?.child(h), merged.child(f)?.child(h))
        assertSame(b.child(f)?.child(g), merged.child(f)?.child(g))
        assertEquals(setOf(p(f, h), p(g, k), p(f, g)), raw(merged).keys)
        assertEquals(setOf(p(f, g)), raw(delta).keys)
        assertSame(b.child(f)?.child(g), delta?.child(f)?.child(g))
    }

    @Test
    fun `withLeaf and mapChildren normalize even unchanged empty nodes to null`() {
        val empty = FlowAlgebra.leafNode(FlowLeaf.NONE)
        assertNull(FlowAlgebra.withLeaf(empty, FlowLeaf.NONE))
        assertNull(FlowAlgebra.mapChildren(empty, FlowLeaf.NONE, f = { _, child -> child }))
        assertNull(FlowAlgebra.withLeaf(null, FlowLeaf.NONE))
        assertSame(FlowAlgebra.LEAF_NODE, FlowAlgebra.withLeaf(null, FlowLeaf.LEAF))
        assertSame(FlowAlgebra.LEAF_NODE, FlowAlgebra.withLeaf(empty, FlowLeaf.LEAF))
        assertNull(FlowAlgebra.withLeaf(FlowAlgebra.LEAF_NODE, FlowLeaf.NONE))
        val root = flow(p(f, g))
        assertSame(root, FlowAlgebra.withLeaf(root, FlowLeaf.NONE))
        val withRoot = requireNotNull(FlowAlgebra.withLeaf(root, FlowLeaf.LEAF))
        assertEquals(setOf(p(), p(f, g)), raw(withRoot).keys)
        assertSame(root.child(f), withRoot.child(f))
    }

    @Test
    fun `mapChildren maps each direct child keeps order and shares unchanged children`() {
        val root = flow(p(), p(f, g), p(h, k))
        val visited = arrayListOf<Int>()
        assertSame(root, FlowAlgebra.mapChildren(root, root.leaf, f = { accessor, child ->
            visited += accessor
            child
        }))
        assertEquals(listOf(f, h), visited)
        val selected = requireNotNull(FlowAlgebra.mapChildren(root, FlowLeaf.NONE,
            f = { accessor, child -> if (accessor == h) child else null }))
        assertEquals(setOf(p(h, k)), raw(selected).keys)
        assertSame(root.child(h), selected.child(h))
        assertContentEquals(intArrayOf(h), selected.accessors)
        assertNull(FlowAlgebra.mapChildren(root, FlowLeaf.NONE, f = { _, _ -> null }))
        assertSame(FlowAlgebra.LEAF_NODE,
            FlowAlgebra.mapChildren(root, FlowLeaf.LEAF, f = { _, _ -> null }))
        val retained = requireNotNull(FlowAlgebra.retainChildren(root, pred = { it == f }))
        assertEquals(setOf(p(f, g)), raw(retained).keys)
        assertSame(root.child(f), retained.child(f))
        assertNull(FlowAlgebra.retainChildren(root, pred = { false }))
    }

    @Test
    fun `replaceChild inserts in sorted position replaces removes and shares other children`() {
        val root = flow(p(g, element), p(k, h))
        val insertedF = requireNotNull(FlowAlgebra.replaceChild(root, FlowLeaf.LEAF, f, FlowAlgebra.LEAF_NODE))
        val insertedH = requireNotNull(FlowAlgebra.replaceChild(insertedF, FlowLeaf.LEAF, h, FlowAlgebra.LEAF_NODE))
        assertContentEquals(intArrayOf(f, g, h, k), insertedH.accessors)
        assertEquals(setOf(p(), p(f), p(g, element), p(h), p(k, h)), raw(insertedH).keys)
        assertSame(root.child(g), insertedH.child(g))
        assertSame(root.child(k), insertedH.child(k))
        val replacement = flow(p(c, f))
        val replaced = requireNotNull(FlowAlgebra.replaceChild(insertedH, FlowLeaf.NONE, g, replacement))
        assertEquals(setOf(p(f), p(g, c, f), p(h), p(k, h)), raw(replaced).keys)
        assertSame(replacement, replaced.child(g))
        assertSame(insertedH.child(k), replaced.child(k))
        assertSame(replaced, FlowAlgebra.replaceChild(replaced, FlowLeaf.NONE, g, replacement))
        val removed = requireNotNull(FlowAlgebra.replaceChild(replaced, FlowLeaf.NONE, g, null))
        assertContentEquals(intArrayOf(f, h, k), removed.accessors)
        assertEquals(setOf(p(f), p(h), p(k, h)), raw(removed).keys)
        assertSame(removed, FlowAlgebra.replaceChild(removed, FlowLeaf.NONE, g, null))
        assertNull(FlowAlgebra.replaceChild(flow(p(f)), FlowLeaf.NONE, f, null))
        assertSame(FlowAlgebra.LEAF_NODE, FlowAlgebra.replaceChild(null, FlowLeaf.LEAF, f, null))
        assertEquals(setOf(p(c)), raw(FlowAlgebra.replaceChild(null, FlowLeaf.NONE, c, FlowAlgebra.LEAF_NODE)).keys)
    }

    @Test
    fun `mapLeaves changes payload columns prunes empty branches and keeps identity otherwise`() {
        val leaves = mapOf(p() to Payload(setOf(t, u), setOf(t)),
            p(f, f) to Payload(setOf(u)), p(g, element) to Payload(setOf(v), setOf(u, v)))
        val root = taint(leaves)
        assertSame(root, taintAlg.mapLeaves(root, f = { it }))
        val mapped = taintAlg.mapLeaves(root, f = { payload ->
            taintAlg.leaves(payload.exact - marks(u), payload.any - marks(t))
        })
        val expected = setOf(Atom(p(), true, t), Atom(p(g, element), true, v),
            Atom(p(g, element), false, u), Atom(p(g, element), false, v))
        assertEquals(expected, atoms(mapped))
        assertNull(mapped?.child(f))
        assertSame(root.child(g), mapped?.child(g))
        assertNull(taintAlg.mapLeaves(root, f = { taintAlg.empty }))
        assertNull(FlowAlgebra.mapLeaves(FlowAlgebra.leafNode(FlowLeaf.NONE), f = { it }))
    }

    @Test
    fun `foldAll includes the root and foldLeaves filters only the first child accessor`() {
        val root = taint(mapOf(p() to Payload(setOf(t), setOf(u)),
            p(f, g) to Payload(setOf(v), setOf(t)), p(h, f) to Payload(setOf(u), setOf(v))))
        assertEquals(leaf(setOf(t, u, v), setOf(t, u, v)), taintAlg.foldAll(root))
        assertEquals(leaf(setOf(t, v), setOf(t, u)), taintAlg.foldLeaves(root, pred = { it == f }))
        assertEquals(leaf(setOf(t, u), setOf(u, v)), taintAlg.foldLeaves(root, pred = { it == h }))
        assertEquals(leaf(setOf(t), setOf(u)), taintAlg.foldLeaves(root, pred = { false }))
        assertEquals(taintAlg.foldAll(root), taintAlg.foldLeaves(root, pred = { true }))
        assertSame(FlowLeaf.LEAF, FlowAlgebra.foldAll(flow(p(f, g))))
        assertSame(FlowLeaf.NONE, FlowAlgebra.foldAll(FlowAlgebra.leafNode(FlowLeaf.NONE)))
    }

    @Test
    fun `prepend preserves repeated fields elements and classes without extra collapse`() {
        val prefix = intArrayOf(c, f, f, element, element, element, c)
        val root = flow(p(), p(g, f))
        val result = FlowAlgebra.prepend(manager, manager.path(prefix), root)
        assertEquals(setOf(prefix.toList(), prefix.toList() + p(g, f)), raw(result).keys)
        assertSame(root, tip(result, prefix))
        assertEquals(7, result.boundedDepth)
        assertSame(root, FlowAlgebra.prepend(manager, null, root))
    }

    @Test
    fun `chain joins the end leaf with its tip and keeps exact spine positions`() {
        val path = intArrayOf(c, f, f, element, element, element, c)
        val tipTree = taint(mapOf(p() to Payload(setOf(t)), p(h) to Payload(setOf(v))))
        val spine = List(path.size + 1) { depth -> when (depth) {
            0 -> leaf(setOf(u))
            2 -> leaf(any = setOf(v))
            path.size -> leaf(any = setOf(u))
            else -> taintAlg.empty
        } }
        val result = requireNotNull(taintAlg.chain(manager, path, spine, tipTree))
        val expected = setOf(Atom(p(), true, u), Atom(path.take(2), false, v),
            Atom(path.toList(), true, t), Atom(path.toList(), false, u),
            Atom(path.toList() + p(h), true, v))
        assertEquals(expected, atoms(result))
        assertSame(tipTree.child(h), tip(result, path).child(h))
        assertEquals(6, result.boundedDepth)
        val short = FlowAlgebra.chain(manager, path, listOf(FlowLeaf.LEAF, FlowLeaf.NONE, FlowLeaf.LEAF))
        assertEquals(setOf(p(), path.take(2)), raw(short).keys)
        assertSame(tipTree, taintAlg.chain(manager, intArrayOf(), emptyList(), tipTree))
        assertNull(FlowAlgebra.chain(manager, path, emptyList()))
        assertNull(FlowAlgebra.chain(manager, intArrayOf(), listOf(FlowLeaf.NONE)))
        assertFails { FlowAlgebra.chain(manager, intArrayOf(f), List(3) { FlowLeaf.LEAF }) }
    }

    @Test
    fun `minusNode removes only matching path column marks and shares other branches`() {
        val original = mapOf(p() to Payload(setOf(t, u), setOf(t, u)),
            p(f, g) to Payload(setOf(t, v), setOf(u)), p(h, k) to Payload(setOf(v)))
        val removed = mapOf(p() to Payload(setOf(t), setOf(u)), p(f, g) to Payload(setOf(v)))
        val root = taint(original)
        val result = taintAlg.minusNode(root, taint(removed))
        assertEquals(literalAtoms(original) - literalAtoms(removed), atoms(result))
        assertSame(root.child(h), result?.child(h))
        assertSame(root, taintAlg.minusNode(root, taintAlg.leafNode(taintAlg.empty)))
        assertNull(taintAlg.minusNode(root, root))
        assertNull(taintAlg.minusNode(root, taint(original)))
        val flowRoot = flow(p(), p(f), p(f, f), p(g, element))
        val flowRest = FlowAlgebra.minusNode(flowRoot, flow(p(f)))
        assertEquals(setOf(p(), p(f, f), p(g, element)), raw(flowRest).keys)
        assertSame(flowRoot.child(g), flowRest?.child(g))
    }

    @Test
    fun `enumeration emits one stable path per leaf with the supplied prefix`() {
        val root = flow(p(), p(f), p(f, g), p(h))
        val prefix = intArrayOf(c, element)
        val paths = arrayListOf<IntArray>()
        val payloads = arrayListOf<FlowLeaf>()
        FlowAlgebra.forEachLeaf(root, prefix, f = { path, payload -> paths += path; payloads += payload })
        assertEquals(listOf(p(c, element), p(c, element, f), p(c, element, f, g), p(c, element, h)),
            paths.map { it.toList() })
        assertEquals(List(4) { FlowLeaf.LEAF }, payloads)
        assertContentEquals(intArrayOf(c, element), prefix)
        val leaves = mapOf(p() to Payload(setOf(t, u), setOf(v)), p(f) to Payload(setOf(v)))
        val positions = arrayListOf<IntArray>()
        taintAlg.forEachLeafPosition(taint(leaves), f = { positions += it })
        assertEquals(listOf(p(), p(f)), positions.map { it.toList() })
        var emptyCalls = 0
        FlowAlgebra.forEachLeaf(FlowAlgebra.leafNode(FlowLeaf.NONE), intArrayOf(), f = { _, _ -> emptyCalls++ })
        assertEquals(0, emptyCalls)
        val shared = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, g), arrayOf(root, root))
        val sharedPaths = arrayListOf<List<Int>>()
        FlowAlgebra.forEachLeafPosition(shared, f = { sharedPaths += it.toList() })
        assertEquals((setOf(p(), p(f), p(f, g), p(h)).map { p(f) + it } +
            setOf(p(), p(f), p(f, g), p(h)).map { p(g) + it }).toSet(), sharedPaths.toSet())
        assertEquals(8, sharedPaths.size)
    }

    @Test
    fun `original positional callback arguments accept function values and preserve behavior`() {
        val root = flow(p(), p(f, g), p(h, k))
        val childMapper: (Int, FlowNode) -> FlowNode? = { accessor, child -> if (accessor == f) child else null }
        val leafMapper: (FlowLeaf) -> FlowLeaf = { it }
        val predicate: (Int) -> Boolean = { it == f }
        val mapped = FlowAlgebra.mapChildren(root, FlowLeaf.NONE, childMapper)
        assertEquals(setOf(p(f, g)), raw(mapped).keys)
        assertSame(root.child(f), mapped?.child(f))
        assertSame(root, FlowAlgebra.mapLeaves(root, leafMapper))
        val foldRoot = taint(mapOf(p() to Payload(setOf(t)),
            p(f, g) to Payload(setOf(u)), p(h, k) to Payload(setOf(v))))
        assertEquals(leaf(setOf(t, u)), taintAlg.foldLeaves(foldRoot, predicate))
        val retained = FlowAlgebra.retainChildren(root, predicate)
        assertEquals(setOf(p(f, g)), raw(retained).keys)
        assertSame(root.child(f), retained?.child(f))
        val paths = arrayListOf<List<Int>>()
        val leafCallback: (IntArray, FlowLeaf) -> Unit = { path, payload ->
            assertSame(FlowLeaf.LEAF, payload)
            paths += path.toList()
        }
        FlowAlgebra.forEachLeaf(root, intArrayOf(c), leafCallback)
        assertEquals(listOf(p(c), p(c, f, g), p(c, h, k)), paths)
        paths.clear()
        val positionCallback: (IntArray) -> Unit = { paths += it.toList() }
        FlowAlgebra.forEachLeafPosition(root, positionCallback)
        assertEquals(listOf(p(), p(f, g), p(h, k)), paths)
    }

    @Test
    fun `nested graft occurrences merge the caller relation rather than replace it`() {
        val summary = flow(p(), p(f))
        val result = taint(mapOf(p() to Payload(setOf(t)), p(f) to Payload(setOf(t))))
        val grafted = taintAlg.graft(summary, occurs = { it == FlowLeaf.LEAF }, r = result)
        assertEquals(setOf(Atom(p(), true, t), Atom(p(f), true, t), Atom(p(f, f), true, t)), atoms(grafted))
        assertSame(result, taintAlg.graft(FlowAlgebra.LEAF_NODE, occurs = { !it.isEmpty }, r = result))
        assertNull(taintAlg.graft(summary, occurs = { false }, r = result))
        val belowOnly = taintAlg.graft(flow(p(g)), occurs = { !it.isEmpty }, r = result)
        assertSame(result, belowOnly?.child(g))
        assertEquals(setOf(Atom(p(g), true, t), Atom(p(g, f), true, t)), atoms(belowOnly))
    }

    @Test
    fun `graft selects summary payloads across kinds and retains repeated path accessors`() {
        val summary = taint(mapOf(p(f) to Payload(setOf(t)), p(c, f, f) to Payload(any = setOf(u))))
        val resultPaths = setOf(p(), p(element, element, element, c, f))
        val result = flow(*resultPaths.toTypedArray())
        val grafted = FlowAlgebra.graft(summary, occurs = { it.hasAny }, r = result)
        assertEquals(resultPaths.map { p(c, f, f) + it }.toSet(), raw(grafted).keys)
        assertSame(result, tip(requireNotNull(grafted), intArrayOf(c, f, f)))
    }

    @Test
    fun `32768-edge merge and delta keep exact union and new tip marks`() {
        val path = IntArray(32_768) { f }
        val a = bottomUp(taintAlg, path, leaf(setOf(t)))
        val b = bottomUp(taintAlg, path, leaf(setOf(u), setOf(v)))
        val (merged, delta) = taintAlg.mergeAddDelta(a, b)
        val actualDelta = requireNotNull(delta)
        assertEquals(32_768, merged.boundedDepth)
        assertEquals(32_769L, merged.size)
        assertEquals(leaf(setOf(t, u), setOf(v)), tip(merged, path).leaf)
        assertEquals(leaf(setOf(u), setOf(v)), tip(actualDelta, path).leaf)
        assertEquals(32_768, actualDelta.boundedDepth)
        assertEquals(merged, taintAlg.mergeAdd(b, a))
        val (reverseMerged, reverseDelta) = taintAlg.mergeAddDelta(b, a)
        assertEquals(merged, reverseMerged)
        assertEquals(leaf(setOf(t)), tip(requireNotNull(reverseDelta), path).leaf)
    }

    @Test
    fun `32768-edge leaf maps and folds use no recursive stack`() {
        val path = IntArray(32_768) { f }
        val root = bottomUp(taintAlg, path, leaf(setOf(t), setOf(u)))
        val mapped = requireNotNull(taintAlg.mapLeaves(root, f = { payload -> taintAlg.asAny(payload) }))
        assertEquals(32_768, mapped.boundedDepth)
        assertEquals(leaf(any = setOf(t, u)), tip(mapped, path).leaf)
        assertEquals(leaf(setOf(t), setOf(u)), taintAlg.foldAll(root))
        assertEquals(leaf(any = setOf(t, u)), taintAlg.foldAll(mapped))
        assertSame(root, taintAlg.mapLeaves(root, f = { it }))
        assertNull(taintAlg.mapLeaves(root, f = { taintAlg.empty }))
    }

    @Test
    fun `32768-edge subtraction keeps only unremoved tip marks`() {
        val path = IntArray(32_768) { element }
        val root = bottomUp(taintAlg, path, leaf(setOf(t, u), setOf(v)))
        val removed = bottomUp(taintAlg, path, leaf(setOf(t), setOf(v)))
        val result = requireNotNull(taintAlg.minusNode(root, removed))
        assertEquals(32_768, result.boundedDepth)
        assertEquals(leaf(setOf(u)), tip(result, path).leaf)
        assertNull(taintAlg.minusNode(root, bottomUp(taintAlg, path, leaf(setOf(t, u), setOf(v)))))
    }

    @Test
    fun `32768-edge enumeration emits the complete path once with its prefix`() {
        val path = IntArray(32_768) { f }
        val root = bottomUp(FlowAlgebra, path, FlowLeaf.LEAF)
        var calls = 0
        FlowAlgebra.forEachLeaf(root, intArrayOf(c), f = { position, payload ->
            calls++
            assertContentEquals(intArrayOf(c) + path, position)
            assertSame(FlowLeaf.LEAF, payload)
        })
        assertEquals(1, calls)
        calls = 0
        FlowAlgebra.forEachLeafPosition(root, f = { position ->
            calls++
            assertContentEquals(path, position)
        })
        assertEquals(1, calls)
    }

    @Test
    fun `32768-edge prepend and chain retain every edge and tip identity`() {
        val path = IntArray(32_768) { f }
        val appended = FlowAlgebra.LEAF_NODE
        val pathNode = requireNotNull(manager.path(path))
        assertSame(pathNode, manager.path(path.copyOf()))
        assertSame(pathNode, manager.path(path.toList()))
        val prepended = FlowAlgebra.prepend(manager, pathNode, appended)
        assertEquals(32_768, prepended.boundedDepth)
        assertEquals(32_769L, prepended.size)
        assertSame(appended, tip(prepended, path))
        val spine = List(path.size + 1) { if (it == path.size) FlowLeaf.LEAF else FlowLeaf.NONE }
        val chained = requireNotNull(FlowAlgebra.chain(manager, path, spine))
        assertEquals(prepended, chained)
        assertEquals(32_768, chained.boundedDepth)
        assertSame(appended, tip(chained, path))
    }

    @Test
    fun `32768-edge graft retains the selected path without recursion`() {
        val path = IntArray(32_768) { f }
        val summary = bottomUp(FlowAlgebra, path, FlowLeaf.LEAF)
        val result = taintAlg.leafNode(leaf(setOf(t), setOf(u)))
        val grafted = requireNotNull(taintAlg.graft(summary, occurs = { !it.isEmpty }, r = result))
        assertEquals(32_768, grafted.boundedDepth)
        assertEquals(32_769L, grafted.size)
        assertSame(result, tip(grafted, path))
    }

    private fun <P : TrieLeaf> sharedDag(alg: LeafAlgebra<P>, depth: Int, inner: P, last: P): TrieNode<P> {
        var node = alg.leafNode(last)
        repeat(depth) { node = alg.node(inner, intArrayOf(f, g), arrayOf(node, node)) }
        return node
    }

    /** Counters measure node work without enumerating the DAG's many paths. */
    private class CountingFlowAlgebra : LeafAlgebra<FlowLeaf>() {
        var unions = 0
        var differences = 0
        override val empty = FlowLeaf.NONE
        override fun union(a: FlowLeaf, b: FlowLeaf): FlowLeaf {
            unions++
            return if (a == FlowLeaf.LEAF || b == FlowLeaf.LEAF) FlowLeaf.LEAF else FlowLeaf.NONE
        }
        override fun minus(a: FlowLeaf, b: FlowLeaf): FlowLeaf {
            differences++
            return if (a == FlowLeaf.LEAF && b == FlowLeaf.NONE) FlowLeaf.LEAF else FlowLeaf.NONE
        }
        override fun below(p: FlowLeaf) = p
        override fun asAny(p: FlowLeaf) = p
        override fun intern(p: FlowLeaf) = p
    }

    @Test
    fun `shared DAG merge and delta memo each node pair and keep shared outputs`() {
        val alg = CountingFlowAlgebra()
        val a = sharedDag(alg, 32, FlowLeaf.NONE, FlowLeaf.LEAF)
        val b = sharedDag(alg, 32, FlowLeaf.LEAF, FlowLeaf.LEAF)
        val merged = alg.mergeAdd(a, b)
        assertEquals(8_589_934_591L, merged.size)
        assertTrue(alg.unions <= 33, "merge union calls: ${alg.unions}")
        assertSame(merged.child(f), merged.child(g))
        alg.unions = 0
        val (withDelta, delta) = alg.mergeAddDelta(a, b)
        assertEquals(merged, withDelta)
        assertTrue(alg.unions <= 33, "delta union calls: ${alg.unions}")
        assertTrue(alg.differences <= 33, "delta leaf differences: ${alg.differences}")
        assertEquals(4_294_967_295L, delta?.size)
        assertEquals(31, delta?.boundedDepth)
        assertSame(delta?.child(f), delta?.child(g))
        var level = requireNotNull(delta)
        repeat(31) {
            assertSame(FlowLeaf.LEAF, level.leaf)
            assertSame(level.child(f), level.child(g))
            level = requireNotNull(level.child(f))
        }
        assertSame(FlowLeaf.LEAF, level.leaf)
        assertNull(level.child(f))
    }

    @Test
    fun `shared DAG leaf maps folds and subtraction visit each unique node or pair once`() {
        val alg = CountingFlowAlgebra()
        val root = sharedDag(alg, 32, FlowLeaf.NONE, FlowLeaf.LEAF)
        var maps = 0
        assertSame(root, alg.mapLeaves(root, f = { maps++; it }))
        assertEquals(33, maps)
        maps = 0
        val changed = requireNotNull(alg.mapLeaves(root, f = { maps++; FlowLeaf.LEAF }))
        assertEquals(33, maps)
        assertSame(changed.child(f), changed.child(g))
        assertEquals(8_589_934_591L, changed.size)
        assertSame(FlowLeaf.LEAF, alg.foldAll(root))
        assertTrue(alg.unions <= 64, "fold union calls: ${alg.unions}")
        val unionsAfterFold = alg.unions
        assertSame(FlowLeaf.LEAF, alg.foldAll(root))
        assertEquals(unionsAfterFold, alg.unions)
        val remainder = alg.minusNode(changed, root)
        assertTrue(alg.differences <= 33, "minus leaf differences: ${alg.differences}")
        assertEquals(4_294_967_295L, remainder?.size)
        assertSame(remainder?.child(f), remainder?.child(g))
    }

    @Test
    fun `shared summary DAG graft memo visits occurrences once and shares resulting children`() {
        val summary = sharedDag(FlowAlgebra, 32, FlowLeaf.NONE, FlowLeaf.LEAF)
        val result = taintAlg.leafNode(leaf(setOf(t), setOf(u)))
        var occurrences = 0
        val grafted = requireNotNull(taintAlg.graft(summary, occurs = { occurrences++; !it.isEmpty }, r = result))
        assertEquals(33, occurrences)
        assertEquals(8_589_934_591L, grafted.size)
        assertEquals(32, grafted.boundedDepth)
        var node = grafted
        repeat(32) {
            assertTrue(node.leaf.isEmpty)
            assertSame(node.child(f), node.child(g))
            node = requireNotNull(node.child(f))
        }
        assertSame(result, node)
    }

    @Test
    fun `explicit cancellation stops long generic operations before traversal completes`() {
        val path = IntArray(1_024) { f }
        val a = bottomUp(taintAlg, path, leaf(setOf(t, u)))
        val b = bottomUp(taintAlg, path, leaf(setOf(t)))
        val summary = bottomUp(FlowAlgebra, path, FlowLeaf.LEAF)
        val cancellation = Cancellation().also { it.cancel() }
        assertFailsWith<Cancellation.Cancelled> { taintAlg.mergeAdd(a, b, cancellation = cancellation) }
        assertFailsWith<Cancellation.Cancelled> { taintAlg.mergeAddDelta(a, b, cancellation = cancellation) }
        assertFailsWith<Cancellation.Cancelled> { taintAlg.mapLeaves(a, cancellation = cancellation, f = { it }) }
        assertFailsWith<Cancellation.Cancelled> { taintAlg.foldAll(a, cancellation = cancellation) }
        assertFailsWith<Cancellation.Cancelled> { taintAlg.minusNode(a, b, cancellation = cancellation) }
        assertFailsWith<Cancellation.Cancelled> {
            taintAlg.forEachLeaf(a, intArrayOf(), cancellation = cancellation, f = { _, _ -> })
        }
        assertFailsWith<Cancellation.Cancelled> {
            taintAlg.forEachLeafPosition(a, cancellation = cancellation, f = { _ -> })
        }
        assertFailsWith<Cancellation.Cancelled> {
            taintAlg.graft(summary, occurs = { !it.isEmpty }, r = taintAlg.leafNode(leaf(setOf(t))),
                cancellation = cancellation)
        }
        cancellation.activate()
        assertSame(a, taintAlg.mergeAdd(a, b, cancellation = cancellation))
        var visits = 0
        assertFailsWith<Cancellation.Cancelled> {
            taintAlg.mapLeaves(a, cancellation = cancellation, f = { payload ->
                if (++visits == 4) cancellation.cancel()
                payload
            })
        }
        assertTrue(visits < 1_025)
    }

    @Test
    fun `prepend and chain read manager cancellation on long paths`() {
        val path = IntArray(1_024) { element }
        val pathNode = manager.path(path)
        val spine = List(path.size + 1) { if (it == path.size) FlowLeaf.LEAF else FlowLeaf.NONE }
        manager.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { FlowAlgebra.prepend(manager, pathNode, FlowAlgebra.LEAF_NODE) }
        assertFailsWith<Cancellation.Cancelled> { FlowAlgebra.chain(manager, path, spine) }
        manager.cancellation.activate()
        val prepended = FlowAlgebra.prepend(manager, pathNode, FlowAlgebra.LEAF_NODE)
        assertEquals(1_024, prepended.boundedDepth)
        assertEquals(prepended, FlowAlgebra.chain(manager, path, spine))
    }
}
