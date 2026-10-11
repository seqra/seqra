package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.BoundedDenotation
import org.opentaint.dataflow.bidi.fixtures.TestLocation
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CurrentTaintGroupKeys destination regression, with a local sparse builder and decoder. */
class TaintGroupKeysTest {
    private val m = ApManager(Cancellation())
    private val x = AccessPathBase.Argument(0)
    private val f = m.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = m.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = m.accessors.index(FieldAccessor("Box", "fresh", "Box"))
    private val t = m.marks.mark("T")
    private val u = m.marks.mark("U")
    private val v = m.marks.mark("Fresh")
    private val empty = ExclusionSet.Empty
    private val universe = BoundedDenotation(listOf(x), listOf(f, g, h), listOf(t, u, v), 3)
    private data class Atom(val path: List<Int>, val tail: Tail, val mark: TaintMark)
    private fun a(path: List<Int>, tail: Tail, mark: TaintMark = t) = Atom(path, tail, mark)
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private class Prefix {
        val exact = sortedSetOf<Int>()
        val any = sortedSetOf<Int>()
        val children = sortedMapOf<Int, Prefix>()
    }
    private fun tree(vararg atoms: Atom, e: ExclusionSet = empty): TaintTree {
        val root = Prefix()
        for (atom in atoms) {
            var node = root
            for (accessor in atom.path) node = node.children.getOrPut(accessor) { Prefix() }
            if (atom.tail == Tail.EXACT) node.exact += atom.mark.id else node.any += atom.mark.id
        }
        fun build(node: Prefix): TaintNode = m.taintAlg.node(
            m.taintLeaves(MarkSet(node.exact.toIntArray()), MarkSet(node.any.toIntArray())),
            node.children.keys.toIntArray(), node.children.values.map { build(it) }.toTypedArray())
        return m.taintTree(x, Layer.NORMAL, build(root), e)
    }
    private fun decode(tree: TaintTree): Set<Conclusion> = buildSet {
        val pending = ArrayDeque<Pair<List<Int>, TaintNode>>()
        pending.addLast(emptyList<Int>() to tree.root)
        while (pending.isNotEmpty()) {
            val (path, node) = pending.removeLast()
            for (id in node.leaf.exact.ids) add(Conclusion(PathFact(tree.base, path, Tail.EXACT,
                MarkSlot.Concrete(TaintMark(id))), empty, false))
            for (id in node.leaf.any.ids) add(Conclusion(PathFact(tree.base, path, Tail.ANY_TAINT,
                MarkSlot.Concrete(TaintMark(id))), tree.exclusion, false))
            val accessors = node.accessors
            val children = node.children
            if (accessors != null && children != null) {
                for (i in accessors.indices) pending.addLast((path + accessors[i]) to children[i])
            }
        }
    }
    private fun denotation(trees: List<TaintTree>): Set<TestLocation> = trees.flatMap { decode(it) }
        .flatMap { universe.marked(Pattern(it.fact, it.exclusion)) }.toSet()
    private fun assertUnion(inputs: List<TaintTree>, stored: List<TaintTree>) {
        assertEquals(denotation(inputs), denotation(stored))
        assertEquals(stored.size, stored.map { it.groupKey }.toSet().size, "One tree per canonical key")
        assertTrue(stored.all { it.base == x && it.layer == Layer.NORMAL && it.groupKey.kind == FactsKind.TAINT })
    }

    @Test
    fun `last must removed merges exact remainder into the existing empty E must destination`() {
        val covering = tree(a(emptyList(), Tail.ANY_TAINT))
        val incoming = tree(a(emptyList(), Tail.ANY_TAINT), a(listOf(g), Tail.EXACT, u), e = ex(f))
        val exact = tree(a(listOf(g), Tail.EXACT, u))
        val expected = tree(a(emptyList(), Tail.ANY_TAINT), a(listOf(g), Tail.EXACT, u))
        val group = TaintGroup(m, m.newInterners().taint)
        group.add(covering)
        assertEquals(decode(exact), decode(requireNotNull(group.add(incoming))))
        assertEquals(listOf(expected), group.all().toList())
        assertUnion(listOf(covering, incoming), group.all().toList())
        assertNull(group.add(incoming))
    }

    @Test
    fun `both input orders retain the same location union and unique keys`() {
        val covering = tree(a(emptyList(), Tail.ANY_TAINT))
        val incoming = tree(a(emptyList(), Tail.ANY_TAINT), a(listOf(g), Tail.EXACT, u), e = ex(f))
        for (order in listOf(listOf(covering, incoming), listOf(incoming, covering))) {
            val group = TaintGroup(m, m.newInterners().taint)
            val seen = mutableListOf<TaintTree>()
            for (input in order) {
                val before = denotation(group.all().toList())
                val delta = requireNotNull(group.add(input))
                seen += input
                val stored = group.all().toList()
                assertUnion(seen, stored)
                val required = denotation(seen) - before
                assertTrue(denotation(listOf(delta)).containsAll(required))
                assertTrue(denotation(stored).containsAll(denotation(listOf(delta))))
            }
        }
    }

    @Test
    fun `changed destination merges existing exact marks and returns only new exact leaves`() {
        val covering = tree(a(emptyList(), Tail.ANY_TAINT), e = ex(g))
        val destination = tree(a(listOf(h), Tail.EXACT, v))
        val incoming = tree(a(emptyList(), Tail.ANY_TAINT), a(listOf(f), Tail.EXACT),
            a(listOf(g), Tail.EXACT, u), a(listOf(h), Tail.EXACT, v), e = ex(f, g))
        val delta = tree(a(listOf(f), Tail.EXACT), a(listOf(g), Tail.EXACT, u))
        val expectedDestination = tree(a(listOf(f), Tail.EXACT), a(listOf(g), Tail.EXACT, u), a(listOf(h), Tail.EXACT, v))
        for (preload in listOf(listOf(covering, destination), listOf(destination, covering))) {
            val group = TaintGroup(m, m.newInterners().taint)
            for (input in preload) group.add(input)
            val actualDelta = requireNotNull(group.add(incoming))
            assertFalse(actualDelta.root.hasAny)
            assertEquals(empty, actualDelta.exclusion)
            assertEquals(decode(delta), decode(actualDelta))
            assertEquals(setOf(covering, expectedDestination), group.all().toSet())
            assertUnion(preload + incoming, group.all().toList())
            assertNull(group.add(incoming))
        }
    }

    @Test
    fun `subtraction creates a fresh empty E destination when no such tree exists`() {
        val covering = tree(a(emptyList(), Tail.ANY_TAINT), e = ex(g))
        val incoming = tree(a(emptyList(), Tail.ANY_TAINT), a(listOf(g), Tail.EXACT, u), e = ex(f, g))
        val expected = tree(a(listOf(g), Tail.EXACT, u))
        val group = TaintGroup(m, m.newInterners().taint)
        group.add(covering)
        assertEquals(expected, group.add(incoming))
        assertEquals(setOf(covering, expected), group.all().toSet())
        assertUnion(listOf(covering, incoming), group.all().toList())
    }

    @Test
    fun `fully covered remainder returns no delta and creates no duplicate destination`() {
        val covering = tree(a(emptyList(), Tail.ANY_TAINT), e = ex(g))
        val destination = tree(a(listOf(g), Tail.EXACT, u))
        val incoming = tree(a(emptyList(), Tail.ANY_TAINT), a(listOf(g), Tail.EXACT, u), e = ex(f, g))
        for (preload in listOf(listOf(covering, destination), listOf(destination, covering))) {
            val group = TaintGroup(m, m.newInterners().taint)
            for (input in preload) group.add(input)
            assertNull(group.add(incoming))
            assertEquals(preload.toSet(), group.all().toSet())
            assertUnion(preload + incoming, group.all().toList())
        }
    }

    @Test
    fun `a remaining must mark retains the original E and its separate destination key`() {
        val covering = tree(a(emptyList(), Tail.ANY_TAINT), e = ex(g))
        val destination = tree(a(listOf(f), Tail.EXACT, v))
        val incoming = tree(a(emptyList(), Tail.ANY_TAINT), a(emptyList(), Tail.ANY_TAINT, u),
            a(listOf(g), Tail.EXACT, v), e = ex(f, g))
        val expected = tree(a(emptyList(), Tail.ANY_TAINT, u), a(listOf(g), Tail.EXACT, v), e = ex(f, g))
        for (preload in listOf(listOf(covering, destination), listOf(destination, covering))) {
            val group = TaintGroup(m, m.newInterners().taint)
            for (input in preload) group.add(input)
            val delta = requireNotNull(group.add(incoming))
            assertTrue(delta.root.hasAny)
            assertEquals(ex(f, g), delta.exclusion)
            assertEquals(decode(expected), decode(delta))
            assertEquals((preload + expected).toSet(), group.all().toSet())
            assertUnion(preload + incoming, group.all().toList())
        }
    }

    @Test
    fun `removing the last descendant must leaf clears E before destination lookup`() {
        val covering = tree(a(listOf(f), Tail.ANY_TAINT), e = ex(g))
        val destination = tree(a(listOf(g), Tail.EXACT, u))
        val incoming = tree(a(emptyList(), Tail.EXACT, v), a(listOf(f), Tail.ANY_TAINT), e = ex(f, g))
        val delta = tree(a(emptyList(), Tail.EXACT, v))
        val expectedDestination = tree(a(emptyList(), Tail.EXACT, v), a(listOf(g), Tail.EXACT, u))
        for (preload in listOf(listOf(covering, destination), listOf(destination, covering))) {
            val group = TaintGroup(m, m.newInterners().taint)
            for (input in preload) group.add(input)
            assertEquals(delta, group.add(incoming))
            assertEquals(setOf(covering, expectedDestination), group.all().toSet())
            assertUnion(preload + incoming, group.all().toList())
        }
    }
}
