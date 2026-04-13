package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode.Companion.extractMatchingSuffix
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractMatchingSuffixTest {

    private val manager = TreeApManager(AnyAccessorUnrollStrategy.AnyAccessorDisabled)

    // Accessor indices for field accessors a, b, c, d, e
    private val a: AccessorIdx
    private val b: AccessorIdx
    private val c: AccessorIdx
    private val d: AccessorIdx
    private val e: AccessorIdx

    init {
        with(manager) {
            a = FieldAccessor("", "a", "").idx
            b = FieldAccessor("", "b", "").idx
            c = FieldAccessor("", "c", "").idx
            d = FieldAccessor("", "d", "").idx
            e = FieldAccessor("", "e", "").idx
        }
    }

    /** Builds a tree from a single path [accessors] ending in an abstract leaf. */
    private fun buildPath(vararg accessors: AccessorIdx): AccessNode {
        var node: AccessNode = manager.abstractNode
        for (i in accessors.indices.reversed()) {
            node = node.addParent(accessors[i])
        }
        return node
    }

    /** Builds a tree by merging multiple paths. */
    private fun buildTree(vararg paths: IntArray): AccessNode {
        return paths.map { buildPath(*it) }.reduce { acc, node -> acc.mergeAdd(node) }
    }

    /** Builds an AccessPath.AccessNode suffix from accessor indices. */
    private fun buildSuffix(vararg accessors: AccessorIdx): AccessPath.AccessNode? {
        var node: AccessPath.AccessNode? = null
        for (i in accessors.indices.reversed()) {
            node = AccessPath.AccessNode(manager, accessors[i], node)
        }
        return node
    }

    /** Enumerates all root-to-leaf paths in a tree as lists of accessor indices. */
    private fun allPaths(node: AccessNode): List<IntArray> {
        val result = mutableListOf<IntArray>()
        fun walk(n: AccessNode, prefix: MutableList<Int>) {
            if (n.isAbstract || n.isFinal) {
                result.add(prefix.toIntArray())
            }
            n.forEachAccessor { accessor, child ->
                prefix.add(accessor)
                walk(child, prefix)
                prefix.removeAt(prefix.lastIndex)
            }
        }
        walk(node, mutableListOf())
        return result
    }

    /** Converts an AccessPath.AccessNode suffix to a list of accessor indices. */
    private fun suffixToList(suffix: AccessPath.AccessNode?): IntArray {
        return suffix?.toList()?.toIntArray() ?: intArrayOf()
    }

    /**
     * Verifies extraction result invariants:
     *
     * Each pair (Pi, Si) means: paths in Pi, concatenated with Si, produce original paths.
     * - Si == null means no suffix matched (remainder group).
     * - Si != null means Si is the matched suffix; Pi has it stripped.
     *
     * Invariants:
     * 1. Soundness: every original path appears in some group.
     * 2. Greedy: longer matches take priority.
     * 3. Disjointness: no reconstructed path appears in two groups.
     */
    private fun assertRoundTrip(original: AccessNode, result: List<Pair<AccessNode, AccessPath.AccessNode?>>) {
        assertTrue(result.isNotEmpty(), "Result should not be empty")

        // Reconstruct original paths: for each (prefix, matchedSuffix),
        // every path in prefix ++ suffixToList(matchedSuffix) should be an original path.
        val reconstructedPathSets = result.map { (prefix, matchedSuffix) ->
            val suffixPart = if (matchedSuffix != null) suffixToList(matchedSuffix) else intArrayOf()
            allPaths(prefix).map { (it + suffixPart).toList() }.toSet()
        }

        // 1. Disjointness: no reconstructed path appears in multiple groups
        for (i in reconstructedPathSets.indices) {
            for (j in i + 1 until reconstructedPathSets.size) {
                val overlap = reconstructedPathSets[i].intersect(reconstructedPathSets[j])
                assertTrue(overlap.isEmpty(), "Groups $i and $j share reconstructed paths: $overlap")
            }
        }

        // 2. Soundness + completeness: merge all reconstructed paths into a tree
        var merged: AccessNode? = null
        for (paths in reconstructedPathSets) {
            for (path in paths) {
                val pathTree = buildPath(*path.toIntArray())
                merged = merged?.mergeAdd(pathTree) ?: pathTree
            }
        }
        assertEquals(original, merged, "Reconstructed paths don't match original tree")
    }

    @Test
    fun nullSuffix() {
        val tree = buildPath(a, b, c)
        val result = tree.extractMatchingSuffix(null)
        assertEquals(1, result.size)
        assertEquals(tree, result[0].first)
        assertNull(result[0].second)
    }

    @Test
    fun singlePathFullMatch() {
        // Tree: a → b → * , suffix: a.b
        val tree = buildPath(a, b)
        val suffix = buildSuffix(a, b)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix should be just abstract (empty path), matched suffix = a.b
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match result")
        assertEquals(manager.abstractNode, fullMatch.first)
        assertRoundTrip(tree, result)
    }

    @Test
    fun singlePathPartialMatch() {
        // Tree: a → b → c → * , suffix: b.c
        val tree = buildPath(a, b, c)
        val suffix = buildSuffix(b, c)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix = a.*, matched suffix = b.c
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match result")
        assertEquals(buildPath(a), fullMatch.first, "Prefix should be 'a.*'")
        assertRoundTrip(tree, result)
    }

    @Test
    fun singlePathNoMatch() {
        // Tree: a → b → c → * , suffix: x.y (using d.e)
        val tree = buildPath(a, b, c)
        val suffix = buildSuffix(d, e)
        val result = tree.extractMatchingSuffix(suffix)

        // No match: remainder with null suffix (nothing matched)
        assertEquals(1, result.size, "Only remainder expected")
        assertNull(result[0].second, "Should have null matched suffix")
        assertEquals(tree, result[0].first, "Remainder should be the original tree")
        assertRoundTrip(tree, result)
    }

    @Test
    fun branchingTreeSuffixMatchesOneBranch() {
        // Tree: a → {b → *, c → *} , suffix: b
        val tree = buildTree(intArrayOf(a, b), intArrayOf(a, c))
        val suffix = buildSuffix(b)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix = a.*, matched suffix = b
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a), fullMatch.first, "Prefix should be 'a.*'")

        assertRoundTrip(tree, result)
    }

    @Test
    fun branchingTreeDominationPartialConsumption() {
        // Tree: a → {b → c → *, d → *} , suffix: b.c
        val tree = buildTree(intArrayOf(a, b, c), intArrayOf(a, d))
        val suffix = buildSuffix(b, c)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix = a.*, matched suffix = b.c
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a), fullMatch.first, "Prefix should be 'a.*'")

        assertRoundTrip(tree, result)
    }

    @Test
    fun dominationNodeFullyConsumed() {
        // Tree: a → b → * , suffix: a.b
        // All paths fully match, nothing remains
        val tree = buildPath(a, b)
        val suffix = buildSuffix(a, b)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(manager.abstractNode, fullMatch.first, "Prefix should be just abstract leaf")

        // Only the full match, no other groups
        assertEquals(1, result.size, "Single path fully matched should produce exactly one result")
        assertRoundTrip(tree, result)
    }

    @Test
    fun dagSharedNodeSuffixMatchSelectsOnlyMatchingBranch() {
        // Tree: a → b → {c → d → *, e → d → *}  (node d→* is shared)
        // Suffix: b.e.d
        // Only the path a.b.e.d.* fully matches. a.b.c.d.* should NOT be in the full-match result.
        val tree = buildTree(intArrayOf(a, b, c, d), intArrayOf(a, b, e, d))
        val suffix = buildSuffix(b, e, d)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix = a.*, matched suffix = b.e.d
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a), fullMatch.first, "Full match prefix should be just 'a.*'")

        assertRoundTrip(tree, result)
    }

    @Test
    fun suffixMatchMidTreeWithSiblingBranch() {
        // Tree: a → b → {c → d → *, d → *}
        // Suffix: b.c.d
        // Path a.b.c.d.* fully matches (b.c.d). Path a.b.d.* matches only 'd' (1 of 3 elements).
        val tree = buildTree(intArrayOf(a, b, c, d), intArrayOf(a, b, d))
        val suffix = buildSuffix(b, c, d)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix = a.*, matched suffix = b.c.d
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a), fullMatch.first, "Prefix should be 'a.*'")

        assertRoundTrip(tree, result)
    }

    @Test
    fun roundTripInvariant() {
        // Multiple paths, verify that merging all results equals the original tree
        val tree = buildTree(
            intArrayOf(a, b, c),
            intArrayOf(a, b, d),
            intArrayOf(a, e),
            intArrayOf(b, c),
        )
        val suffix = buildSuffix(b, c)
        val result = tree.extractMatchingSuffix(suffix)

        assertRoundTrip(tree, result)
    }
}
