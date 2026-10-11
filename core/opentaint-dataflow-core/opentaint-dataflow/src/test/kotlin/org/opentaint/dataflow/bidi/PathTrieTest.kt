package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.store.PathTrie
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

/** AP8 PATH TRIES: list meanings, with equality only at the same position. */
class PathTrieTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private data class Entry(val base: AccessPathBase, val path: List<Int>, val value: String)
    private fun prefixes(a: List<Int>, b: List<Int>) = a.size <= b.size && a.indices.all { a[it] == b[it] }
    private fun counts(values: List<String>) = values.groupingBy { it }.eachCount()

    @Test fun `all query branches equal complete positional scans`() {
        val trie = PathTrie<String>()
        val paths = mutableListOf(emptyList<Int>())
        var level = listOf(emptyList<Int>())
        repeat(3) { level = level.flatMap { p -> (0..3).map { p + it } }; paths += level }
        val entries = paths.flatMapIndexed { i, path ->
            listOf(Entry(x, path, "v${i % 7}"), Entry(x, path, "unique$i"), Entry(y, path, "y$i"))
        }
        for (e in entries) {
            assertTrue(trie.add(e.base, e.path.toIntArray(), e.value))
            assertFalse(trie.add(e.base, e.path.toIntArray(), e.value))
        }
        for (base in listOf(x, y, AccessPathBase.Argument(7))) for (path in paths + listOf(listOf(4), listOf(1, 4), listOf(2, 2, 2, 4))) {
            val relevant = entries.filter { it.base == base }
            assertEquals(counts(relevant.filter { prefixes(it.path, path) }.map { it.value }),
                counts(trie.lookupPrefixes(base, path.toIntArray())))
            assertEquals(counts(relevant.filter { prefixes(path, it.path) }.map { it.value }),
                counts(trie.lookupExtensions(base, path.toIntArray())))
            assertEquals(counts(relevant.filter { prefixes(it.path, path) || prefixes(path, it.path) }.map { it.value }),
                counts(trie.around(base, path.toIntArray())))
        }
        assertEquals(counts(entries.map { it.value }), counts(trie.all().toList()))
    }

    @Test fun `prefix order includes root and failed proper prefixes once`() {
        val trie = PathTrie<String>()
        trie.add(x, intArrayOf(), "rootA")
        trie.add(x, intArrayOf(), "rootB")
        trie.add(x, intArrayOf(1), "parent")
        trie.add(x, intArrayOf(1, 2), "tip")
        assertEquals(listOf("rootA", "rootB", "parent", "tip"), trie.lookupPrefixes(x, intArrayOf(1, 2)))
        assertEquals(listOf("rootA", "rootB", "parent"), trie.lookupPrefixes(x, intArrayOf(1, 7)))
        assertEquals(listOf("tip"), trie.lookupExtensions(x, intArrayOf(1, 2)))
        assertEquals(listOf("rootA", "rootB", "parent", "tip"), trie.around(x, intArrayOf(1, 2)))
        assertTrue(trie.lookupExtensions(x, intArrayOf(7)).isEmpty())
    }

    @Test fun `stored positions own the caller path and equal values at different positions remain distinct`() {
        val trie = PathTrie<String>()
        val path = intArrayOf(1, 2)
        trie.add(x, path, "same")
        path[0] = 9
        trie.add(x, intArrayOf(1), "same")
        assertEquals(listOf("same", "same"), trie.around(x, intArrayOf(1, 2)))
        assertTrue(trie.around(x, intArrayOf(9)).isEmpty())
        assertEquals(2, trie.all().count())
    }

    @Test fun `deep indexes use iterative insertion query and traversal`() {
        val trie = PathTrie<String>()
        val path = IntArray(32_768) { 1 }
        trie.add(x, intArrayOf(), "root")
        trie.add(x, path, "deep")
        assertEquals(listOf("root", "deep"), trie.lookupPrefixes(x, path))
        assertEquals(listOf("deep"), trie.lookupExtensions(x, path.copyOf(16_384)))
        assertEquals(setOf("root", "deep"), trie.all().toSet())
        assertEquals(listOf("root", "deep"), trie.around(x, path))
    }

    @Test fun `owned cancellation stops insertion and every query`() {
        val cancellation = Cancellation()
        val trie = PathTrie<String>(cancellation)
        trie.add(x, intArrayOf(1), "value")
        cancellation.cancel()
        assertFails { trie.add(x, intArrayOf(2), "other") }
        assertFails { trie.lookupPrefixes(x, intArrayOf(1)) }
        assertFails { trie.lookupExtensions(x, intArrayOf(1)) }
        assertFails { trie.around(x, intArrayOf(1)) }
        assertFails { trie.all().toList() }
    }

    @Test fun `cancellation during iteration stops buffered values before the next emission`() {
        val cancellation = Cancellation()
        val trie = PathTrie<String>(cancellation)
        trie.add(x, intArrayOf(1), "first")
        trie.add(x, intArrayOf(1), "second")
        val values = trie.all().iterator()
        assertTrue(values.hasNext())
        values.next()
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { values.next() }
    }
}
