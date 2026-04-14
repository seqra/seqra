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
    private val f: AccessorIdx
    private val g: AccessorIdx
    private val h: AccessorIdx
    private val i: AccessorIdx
    private val j: AccessorIdx
    private val k: AccessorIdx

    init {
        with(manager) {
            a = FieldAccessor("", "a", "").idx
            b = FieldAccessor("", "b", "").idx
            c = FieldAccessor("", "c", "").idx
            d = FieldAccessor("", "d", "").idx
            e = FieldAccessor("", "e", "").idx
            f = FieldAccessor("", "f", "").idx
            g = FieldAccessor("", "g", "").idx
            h = FieldAccessor("", "h", "").idx
            i = FieldAccessor("", "i", "").idx
            j = FieldAccessor("", "j", "").idx
            k = FieldAccessor("", "k", "").idx
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

    @Test
    fun suffixMatchesAtMultipleDepths() {
        // Tree: a → b → a → b → *
        // Suffix: a.b
        // The suffix matches at two depths:
        //   - root level: a.b (consuming the full tree)
        //   - nested: after a.b, another a.b
        // Greedy: the outermost (root-level) match should win.
        val tree = buildPath(a, b, a, b)
        val suffix = buildSuffix(a, b)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match at root: prefix = * (abstract), matched suffix = a.b
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match at root")
        assertEquals(manager.abstractNode, fullMatch.first, "Full match should consume entire tree from root")

        assertRoundTrip(tree, result)
    }

    @Test
    fun suffixRepeatedInSequence() {
        // Tree: {a → b → a → b → *, c → a → b → *}
        // Suffix: a.b
        // Both root-level "a" branches match. Nested a.b also matches.
        val tree = buildTree(intArrayOf(a, b, a, b), intArrayOf(c, a, b))
        val suffix = buildSuffix(a, b)
        val result = tree.extractMatchingSuffix(suffix)

        assertRoundTrip(tree, result)
    }

    @Test
    fun multipleDisjointRootsMatchSameSuffix() {
        // Tree: {a → b → *, c → b → *, d → e → *}
        // Suffix: b
        // Two independent subtrees end with 'b', one doesn't.
        val tree = buildTree(intArrayOf(a, b), intArrayOf(c, b), intArrayOf(d, e))
        val suffix = buildSuffix(b)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match should include both a.* and c.* as prefixes
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        val expectedPrefix = buildTree(intArrayOf(a), intArrayOf(c))
        assertEquals(expectedPrefix, fullMatch.first, "Full match prefix should be {a.*, c.*}")

        // Remainder should be d → e → *
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder")
        assertEquals(buildPath(d, e), remainder.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun singleAccessorSuffix() {
        // Tree: a → b → c → *, suffix: c
        // Only the last accessor matches.
        val tree = buildPath(a, b, c)
        val suffix = buildSuffix(c)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a, b), fullMatch.first, "Prefix should be a.b.*")

        assertRoundTrip(tree, result)
    }

    @Test
    fun suffixIsEntireTree() {
        // Tree: a → b → * , suffix: a.b
        // Suffix covers entire tree. Prefix should be just abstract, no remainder.
        val tree = buildPath(a, b)
        val suffix = buildSuffix(a, b)
        val result = tree.extractMatchingSuffix(suffix)

        assertEquals(1, result.size, "Should produce exactly one group")
        assertEquals(manager.abstractNode, result[0].first)
        assertEquals(suffix, result[0].second)
        assertRoundTrip(tree, result)
    }

    @Test
    fun diamondDagBothBranchesLeadToSuffixMatch() {
        // Tree (diamond):
        //   root → a → X → d → *
        //   root → b → X → d → *
        //   (X is shared between a and b branches)
        // Suffix: d
        // Both a and b paths end with 'd', both should fully match.
        val tree = buildTree(intArrayOf(a, d), intArrayOf(b, d))
        val suffix = buildSuffix(d)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        val expectedPrefix = buildTree(intArrayOf(a), intArrayOf(b))
        assertEquals(expectedPrefix, fullMatch.first, "Both branches should be in prefix")

        assertRoundTrip(tree, result)
    }

    @Test
    fun partialMatchAtEverySuffixLevel() {
        // Tree: {a → b → c → *, a → b → d → *, a → e → *, d → *}
        // Suffix: a.b.c
        // Level 0 (full match): a.b.c.* → prefix = *
        // Level 1 (partial, matched b.c): nothing — no path ends with b.c but not a.b.c
        //   wait: a.b.d.* — 'd' doesn't match 'c', so no match at level 2. 'b' matches at level 1.
        //   Actually: suffix is a.b.c. For path a.b.d.*:
        //     - 'd' != 'c' so leaf-to-c fails. But 'b' matches suffix[1]='b'? No, matching is from leaf up.
        //     - Leaf at d: starts suffix matching. suffix[2]=c, predecessor via c? d's predecessor is b-node via d, not c.
        //     So a.b.d.* gets NO suffix elements matched → remainder.
        //   For path a.e.*: same — no match → remainder.
        //   For path d.*: no match → remainder.
        val tree = buildTree(
            intArrayOf(a, b, c),
            intArrayOf(a, b, d),
            intArrayOf(a, e),
            intArrayOf(d),
        )
        val suffix = buildSuffix(a, b, c)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix = *, matched = a.b.c
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(manager.abstractNode, fullMatch.first)

        // All other paths go to remainder
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder")
        val expectedRemainder = buildTree(intArrayOf(a, b, d), intArrayOf(a, e), intArrayOf(d))
        assertEquals(expectedRemainder, remainder.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun branchPeelsOffAtEachSuffixLevel() {
        // Tree:
        //   a → b → c → *    (full match for suffix a.b.c)
        //   a → b → e → *    (branch peels off at level 2: 'e' != 'c')
        //   a → d → *        (branch peels off at level 1: 'd' != 'b')
        //   e → *            (branch peels off at level 0: 'e' != 'a')
        // Suffix: a.b.c
        val tree = buildTree(
            intArrayOf(a, b, c),
            intArrayOf(a, b, e),
            intArrayOf(a, d),
            intArrayOf(e),
        )
        val suffix = buildSuffix(a, b, c)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix = *, matched = a.b.c
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(manager.abstractNode, fullMatch.first)

        // Remainder contains all non-matching branches
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder")
        val expectedRemainder = buildTree(intArrayOf(a, b, e), intArrayOf(a, d), intArrayOf(e))
        assertEquals(expectedRemainder, remainder.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun deepDiamondWithSuffixOnOneBranch() {
        // Tree (diamond at depth 2):
        //   a → b → c → d → *
        //   a → b → e → d → *
        //   (d→* is shared)
        // Suffix: c.d
        // Path a.b.c.d.* fully matches c.d.
        // Path a.b.e.d.* partially matches: 'd' = suffix[1] matches, so it goes to level 1.
        val tree = buildTree(intArrayOf(a, b, c, d), intArrayOf(a, b, e, d))
        val suffix = buildSuffix(c, d)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix = a.b.*, matched = c.d
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a, b), fullMatch.first)

        // Partial match at level 1 (matched 'd'): prefix = a.b.e.*, matched = d
        val partialSuffix = buildSuffix(d)
        val partialMatch = result.find { it.second == partialSuffix }
        assertTrue(partialMatch != null, "Expected partial match for 'd'")
        assertEquals(buildPath(a, b, e), partialMatch.first)

        // No remainder: all paths claimed
        val remainder = result.find { it.second == null }
        assertNull(remainder, "No remainder expected, all paths matched")

        assertRoundTrip(tree, result)
    }

    @Test
    fun suffixLongerThanAnyPath() {
        // Tree: a → *, suffix: a.b.c.d
        // The suffix is longer than the tree — no path can match.
        val tree = buildPath(a)
        val suffix = buildSuffix(a, b, c, d)
        val result = tree.extractMatchingSuffix(suffix)

        // Everything goes to remainder
        assertEquals(1, result.size)
        assertNull(result[0].second)
        assertEquals(tree, result[0].first)
        assertRoundTrip(tree, result)
    }

    @Test
    fun wideTreeManyBranchesSingleSuffixMatch() {
        // Tree with many branches, only one matches the suffix
        val tree = buildTree(
            intArrayOf(a, b, c),
            intArrayOf(a, b, d),
            intArrayOf(a, b, e),
            intArrayOf(a, c, d),
            intArrayOf(a, d, e),
        )
        val suffix = buildSuffix(b, c)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: a.b.c.* → prefix = a.*, matched = b.c
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a), fullMatch.first)

        // Remainder should have all other paths
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null)
        val expectedRemainder = buildTree(
            intArrayOf(a, b, d),
            intArrayOf(a, b, e),
            intArrayOf(a, c, d),
            intArrayOf(a, d, e),
        )
        assertEquals(expectedRemainder, remainder.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun abstractOnlyNode() {
        // Tree: just abstract (no accessors)
        val tree = manager.abstractNode
        val suffix = buildSuffix(a)
        val result = tree.extractMatchingSuffix(suffix)

        // No paths match, single remainder
        assertEquals(1, result.size)
        assertNull(result[0].second)
        assertEquals(tree, result[0].first)
        assertRoundTrip(tree, result)
    }

    @Test
    fun cutPointIsRoot() {
        // Tree: a → b → *, suffix: a.b
        // The root node IS the cut point (suffix[0]=a matches root's edge).
        // Full match consumes the entire tree.
        val tree = buildPath(a, b)
        val suffix = buildSuffix(a, b)
        val result = tree.extractMatchingSuffix(suffix)

        assertEquals(1, result.size)
        assertEquals(manager.abstractNode, result[0].first)
        assertEquals(suffix, result[0].second)
        assertRoundTrip(tree, result)
    }

    @Test
    fun cutPointIsRootWithSiblings() {
        // Tree: {a → b → *, c → *}, suffix: a.b
        // Root is the cut point for suffix[0]=a. Path c.* doesn't match.
        val tree = buildTree(intArrayOf(a, b), intArrayOf(c))
        val suffix = buildSuffix(a, b)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(manager.abstractNode, fullMatch.first)

        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder for c.*")
        assertEquals(buildPath(c), remainder.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun tripleDiamondOnlyOneBranchMatches() {
        // Three paths merge into same shared node, suffix matches only one branch:
        //   a → c → e → *
        //   b → c → e → *   (c→e→* shared)
        //   a → d → e → *
        // Suffix: c.e
        // a.c.e.* and b.c.e.* fully match. a.d.e.* does NOT match c.e.
        val tree = buildTree(intArrayOf(a, c, e), intArrayOf(b, c, e), intArrayOf(a, d, e))
        val suffix = buildSuffix(c, e)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        val expectedPrefix = buildTree(intArrayOf(a), intArrayOf(b))
        assertEquals(expectedPrefix, fullMatch.first, "Both a.* and b.* should be in prefix")

        assertRoundTrip(tree, result)
    }

    @Test
    fun twoFullMatchesDifferentPrefixLengths() {
        // Two independent paths both fully match the suffix but at different depths:
        //   a → c → d → *   (prefix = a.*)
        //   b → e → c → d → *   (prefix = b.e.*)
        // Both should be in the same full-match group.
        val tree = buildTree(intArrayOf(a, c, d), intArrayOf(b, e, c, d))
        val suffix = buildSuffix(c, d)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        val expectedPrefix = buildTree(intArrayOf(a), intArrayOf(b, e))
        assertEquals(expectedPrefix, fullMatch.first)

        assertEquals(1, result.size, "All paths fully match")
        assertRoundTrip(tree, result)
    }

    @Test
    fun independentPathsMatchAtDifferentDepths() {
        // Two completely independent sub-trees, each matching the suffix at different depths:
        //   a → b → c → *    (suffix b.c matches at depth 1)
        //   d → e → b → c → * (suffix b.c matches at depth 2)
        // Both should fully match with different prefixes.
        val tree = buildTree(intArrayOf(a, b, c), intArrayOf(d, e, b, c))
        val suffix = buildSuffix(b, c)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        val expectedPrefix = buildTree(intArrayOf(a), intArrayOf(d, e))
        assertEquals(expectedPrefix, fullMatch.first)

        assertEquals(1, result.size, "All paths fully match, no remainder")
        assertRoundTrip(tree, result)
    }

    @Test
    fun multiplePartialMatchLevelsOnIndependentPaths() {
        // Tree: {a → b → c → *, d → c → *, e → *}
        // Suffix: a.b.c
        // a.b.c.* → full match (level 0)
        // d.c.*   → partial: 'c' matches suffix[2], so matched suffix = c
        // e.*     → no match
        val tree = buildTree(intArrayOf(a, b, c), intArrayOf(d, c), intArrayOf(e))
        val suffix = buildSuffix(a, b, c)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match for a.b.c.*")
        assertEquals(manager.abstractNode, fullMatch.first)

        // d.c.* partially matches 'c'
        val partialSuffix = buildSuffix(c)
        val partialMatch = result.find { it.second == partialSuffix }
        assertTrue(partialMatch != null, "Expected partial match for d.c.*")
        assertEquals(buildPath(d), partialMatch.first)

        // e.* → remainder
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder for e.*")
        assertEquals(buildPath(e), remainder.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun suffixMatchAtBranchingLeaf() {
        // Node that is both a cut point AND has non-matching children:
        //   a → b → *   (b is abstract leaf)
        //   a → b → c → * (b also has child c)
        // Suffix: b
        // a.b.* fully matches. a.b.c.* does NOT match 'b' — 'c' isn't in suffix.
        val tree = buildTree(intArrayOf(a, b), intArrayOf(a, b, c))
        val suffix = buildSuffix(b)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a), fullMatch.first)

        // a.b.c.* should be in remainder
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder for a.b.c.*")
        assertEquals(buildPath(a, b, c), remainder.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun deepSharedPrefixDivergingAtSuffixMatch() {
        // Long shared prefix, then branches diverge at the suffix match point:
        //   a → b → c → d → e → *   (suffix d.e matches)
        //   a → b → c → d → a → *   (d matches suffix[0], but 'a' != 'e')
        //   a → b → c → a → *       (no match)
        val tree = buildTree(
            intArrayOf(a, b, c, d, e),
            intArrayOf(a, b, c, d, a),
            intArrayOf(a, b, c, a),
        )
        val suffix = buildSuffix(d, e)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a, b, c), fullMatch.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun suffixMatchesEverySinglePath() {
        // Every path ends with the suffix. No remainder expected.
        //   a → c → d → *
        //   b → c → d → *
        //   e → c → d → *
        val tree = buildTree(intArrayOf(a, c, d), intArrayOf(b, c, d), intArrayOf(e, c, d))
        val suffix = buildSuffix(c, d)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        val expectedPrefix = buildTree(intArrayOf(a), intArrayOf(b), intArrayOf(e))
        assertEquals(expectedPrefix, fullMatch.first)

        val remainder = result.find { it.second == null }
        assertNull(remainder, "No remainder expected")

        assertEquals(1, result.size)
        assertRoundTrip(tree, result)
    }

    @Test
    fun doubleDiamondWithPartialAndFullMatch() {
        // Two diamonds stacked:
        //   a → b → d → *
        //   a → c → d → *
        //   a → b → e → *
        //   a → c → e → *
        //   (d→* and e→* shared across b and c branches)
        // Suffix: b.d
        // a.b.d.* fully matches. a.c.d.* partial (matched 'd'). a.b.e.* and a.c.e.* no match.
        val tree = buildTree(
            intArrayOf(a, b, d),
            intArrayOf(a, c, d),
            intArrayOf(a, b, e),
            intArrayOf(a, c, e),
        )
        val suffix = buildSuffix(b, d)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match for a.b.d.*")
        assertEquals(buildPath(a), fullMatch.first)

        // a.c.d.* partial match: matched 'd'
        val partialSuffix = buildSuffix(d)
        val partialMatch = result.find { it.second == partialSuffix }
        assertTrue(partialMatch != null, "Expected partial match for a.c.d.*")
        assertEquals(buildPath(a, c), partialMatch.first)

        // a.b.e.* and a.c.e.* → remainder
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder")
        val expectedRemainder = buildTree(intArrayOf(a, b, e), intArrayOf(a, c, e))
        assertEquals(expectedRemainder, remainder.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun sharedSubtreePartialAndFullMatchDifferentParents() {
        // Shared subtree reached via matching and non-matching parents:
        //   a → b → c → *
        //   a → d → c → *   (c→* shared)
        //   a → e → *
        // Suffix: b.c
        // a.b.c.* fully matches. a.d.c.* partially matches (c). a.e.* no match.
        val tree = buildTree(intArrayOf(a, b, c), intArrayOf(a, d, c), intArrayOf(a, e))
        val suffix = buildSuffix(b, c)
        val result = tree.extractMatchingSuffix(suffix)

        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a), fullMatch.first)

        // a.d.c.* partial (matched c)
        val cSuffix = buildSuffix(c)
        val partialMatch = result.find { it.second == cSuffix }
        assertTrue(partialMatch != null, "Expected partial match for c")
        assertEquals(buildPath(a, d), partialMatch.first)

        // a.e.* in remainder
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder")
        assertEquals(buildPath(a, e), remainder.first)

        assertRoundTrip(tree, result)
    }

    @Test
    fun chainOfPartialMatches() {
        // Tree with paths matching at various suffix levels:
        //   a → b → c → d → *   (full match for b.c.d)
        //   a → e → c → d → *   (partial: c.d matches, but e≠b)
        //   a → e → d → *       (partial: d matches, but nothing more)
        //   a → e → a → *       (no match)
        val tree = buildTree(
            intArrayOf(a, b, c, d),
            intArrayOf(a, e, c, d),
            intArrayOf(a, e, d),
            intArrayOf(a, e, a),
        )
        val suffix = buildSuffix(b, c, d)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")
        assertEquals(buildPath(a), fullMatch.first)

        // a.e.c.d.* partial (matched c.d)
        val cdSuffix = buildSuffix(c, d)
        val cdMatch = result.find { it.second == cdSuffix }
        assertTrue(cdMatch != null, "Expected partial match for c.d")
        assertEquals(buildPath(a, e), cdMatch.first)

        // a.e.d.* — d matches suffix[2]=d, so partial (matched d).
        // BUT: the shared d→* node is also reached via c.d path.
        // Since d-node is dominated by a better match (c.d at level 1),
        // a.e.d.* goes to remainder instead of a separate partial group.
        // The actual behavior depends on DAG sharing — verify via round-trip.

        assertRoundTrip(tree, result)
    }

    @Test
    fun realWorldPartialMatchesThroughSameRootEdge() {
        // Models the real bug: suffix = a.b
        // Paths through a→b (full match): a.b.*, a.b.c.d.*, a.b.c.e.*
        // Paths ending with a→...→b (partial match on 'b'): a.c.d.b.*, a.c.e.b.*
        // Paths not matching at all: a.c.f.*
        //
        // The level-0 cut is at root (removing edge 'a'). All partial-match
        // paths also go through 'a', so they MUST NOT be blocked.
        val tree = buildTree(
            intArrayOf(a, b),
            intArrayOf(a, b, c, d),
            intArrayOf(a, b, c, e),
            intArrayOf(a, c, d, b),
            intArrayOf(a, c, e, b),
            intArrayOf(a, c, f),
        )
        val suffix = buildSuffix(a, b)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match: prefix = *, matched suffix = a.b
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match for a.b")

        // Partial matches for 'b': a.c.d.b and a.c.e.b end with 'b'
        val partialB = buildSuffix(b)
        val bMatch = result.find { it.second == partialB }
        assertTrue(bMatch != null, "Expected partial match for 'b' " +
                "(a.c.d.b.* and a.c.e.b.* should be extracted)")

        // a.c.f.* has no suffix match — should be in remainder
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder for non-matching paths")

        assertRoundTrip(tree, result)
    }

    @Test
    fun realWorldLargeTreeExtraction() {
        // Simplified version of the real case:
        // root → a → b → ...  (a.b.* = direct match)
        // root → a → c → X → b → ...  (ending in ...b.* = partial on 'b')
        // root → a → c → X → Y → Z → b → ...  (deeper ending in b.*)
        // root → a → d → ...  (no match)
        // Suffix: a.b
        //
        // After extraction, partial matches through a.c.*.b.* must NOT be lost.
        val tree = buildTree(
            intArrayOf(a, b),
            intArrayOf(a, b, c, d),
            intArrayOf(a, b, c, e),
            intArrayOf(a, b, d, e),
            intArrayOf(a, c, d, b),
            intArrayOf(a, c, e, b),
            intArrayOf(a, c, e, f, g, b),
            intArrayOf(a, c, d, e),
            intArrayOf(a, d, e),
            intArrayOf(a, d, f),
        )
        val suffix = buildSuffix(a, b)
        val result = tree.extractMatchingSuffix(suffix)

        // Full match exists
        val fullMatch = result.find { it.second == suffix }
        assertTrue(fullMatch != null, "Expected full match")

        // Partial match for 'b' exists
        val partialB = buildSuffix(b)
        val bMatch = result.find { it.second == partialB }
        assertTrue(bMatch != null, "Expected partial match for 'b'")

        // Remainder exists (a.d.e.*, a.d.f.*, a.c.d.e.* don't end with 'b')
        val remainder = result.find { it.second == null }
        assertTrue(remainder != null, "Expected remainder")

        assertRoundTrip(tree, result)
    }
}
