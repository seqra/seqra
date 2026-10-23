package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.automata.AccessGraphFinalFactAp
import org.opentaint.dataflow.ap.ifds.access.automata.AutomataApManager
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FieldLimitTest {
    private companion object {
        val FIELD_A = FieldAccessor("C", "a", "C")
        val FIELD_B = FieldAccessor("C", "b", "C")
        val FIELD_C = FieldAccessor("C", "c", "C")
        val FIELD_D = FieldAccessor("C", "d", "C")
        val MARK = TaintMarkAccessor("m")
    }

    private object UnrollStrategy : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean =
            accessor is FieldAccessor || accessor is ElementAccessor
    }

    private val manager = TreeApManager(UnrollStrategy, RefManager(), Cancellation())
    private val base = AccessPathBase.This

    private fun finalFact(vararg accessors: Accessor): AccessTree {
        var fact = manager.createFinalAp(base, ExclusionSet.Empty)
        for (accessor in accessors.reversed()) {
            fact = fact.prependAccessor(accessor)
        }
        return fact as AccessTree
    }

    private fun initialFact(vararg accessors: Accessor): InitialFactAp {
        var fact = manager.mostAbstractInitialAp(base)
        for (accessor in accessors.reversed()) {
            fact = fact.prependAccessor(accessor)
        }
        return fact
    }

    private fun abstractFact(vararg accessors: Accessor): AccessTree {
        var fact = manager.mostAbstractFinalAp(base)
        for (accessor in accessors.reversed()) {
            fact = fact.prependAccessor(accessor)
        }
        return fact as AccessTree
    }

    private fun merged(a: AccessTree, b: AccessTree): AccessTree =
        AccessTree(manager, base, a.access.mergeAdd(b.access), a.exclusions)

    private fun FinalFactAp?.paths(): Set<String> {
        if (this == null) return emptySet()
        val result = hashSetOf<String>()
        (this as AccessTree).access.collectPaths("", result)
        return result
    }

    private fun AccessTree.AccessNode.collectPaths(prefix: String, dst: MutableSet<String>) {
        if (isFinal) dst.add("$prefix.$")
        if (isAbstract) dst.add("$prefix.*${deepAccessorExclusion ?: ""}")
        forEachAccessor { accessor, node ->
            node.collectPaths("$prefix.${with(manager) { accessor.accessor }}", dst)
        }
    }

    private fun assertSplitPartition(fact: AccessTree, limit: Int, split: FinalFactAp.FieldLimitSplit) {
        val within = split.within
        val rest = split.rest
        assertTrue(within.paths().intersect(rest.paths()).isEmpty())
        assertEquals(fact.paths(), within.paths() + rest.paths())
        if (within != null) {
            assertFalse(within.exceedsFieldLimit(limit))
            assertEquals(fact.base, within.base)
            assertEquals(fact.exclusions, within.exclusions)
        }
        if (rest != null) {
            assertTrue((rest as AccessTree).access.minFieldDepth > limit)
            assertEquals(fact.base, rest.base)
            assertEquals(fact.exclusions, rest.exclusions)
        }
    }

    private fun FactAp.fieldDepth(): Int =
        (0..32).first { !exceedsFieldLimit(it) }

    @Test
    fun `fields count towards field depth`() {
        assertEquals(0, finalFact().fieldDepth())
        assertEquals(1, finalFact(FIELD_A).fieldDepth())
        assertEquals(3, finalFact(FIELD_A, FIELD_B, FIELD_C).fieldDepth())

        assertEquals(0, initialFact().fieldDepth())
        assertEquals(3, initialFact(FIELD_A, FIELD_B, FIELD_C).fieldDepth())
    }

    @Test
    fun `fact exceeds limit iff field depth is greater than limit`() {
        val fact = finalFact(FIELD_A, FIELD_B, FIELD_C)
        assertTrue(fact.exceedsFieldLimit(2))
        assertFalse(fact.exceedsFieldLimit(3))
        assertFalse(fact.exceedsFieldLimit(4))

        val initial = initialFact(FIELD_A, FIELD_B, FIELD_C)
        assertTrue(initial.exceedsFieldLimit(2))
        assertFalse(initial.exceedsFieldLimit(3))
    }

    @Test
    fun `element accessor counts towards field depth`() {
        assertEquals(2, finalFact(FIELD_A, ElementAccessor).fieldDepth())
        assertEquals(3, finalFact(ElementAccessor, ElementAccessor, FIELD_A).fieldDepth())

        assertEquals(2, initialFact(FIELD_A, ElementAccessor).fieldDepth())
    }

    @Test
    fun `any accessor counts as one`() {
        assertEquals(1, finalFact(AnyAccessor).fieldDepth())
        assertEquals(2, finalFact(FIELD_A, AnyAccessor).fieldDepth())
        assertEquals(2, finalFact(AnyAccessor, AnyAccessor, FIELD_A).fieldDepth())
    }

    @Test
    fun `non field accessors do not count`() {
        assertEquals(0, finalFact(MARK).fieldDepth())
        assertEquals(2, finalFact(FIELD_A, FIELD_B, MARK).fieldDepth())
        assertEquals(1, finalFact(TypeInfoGroupAccessor, TypeInfoAccessor("T"), FIELD_A).fieldDepth())

        assertEquals(0, initialFact(FinalAccessor).fieldDepth())
        assertEquals(1, initialFact(FIELD_A, FinalAccessor).fieldDepth())
        assertEquals(2, initialFact(FIELD_A, FIELD_B, MARK).fieldDepth())
        assertEquals(1, initialFact(TypeInfoGroupAccessor, TypeInfoAccessor("T"), FIELD_A).fieldDepth())
    }

    @Test
    fun `branching tree takes the deepest branch`() {
        val shallow = finalFact(FIELD_A)
        val deep = finalFact(FIELD_B, FIELD_C, FIELD_D)
        val tree = merged(shallow, deep)

        assertEquals(3, tree.fieldDepth())
        assertEquals(3, merged(deep, shallow).fieldDepth())
        assertTrue(tree.exceedsFieldLimit(2))
        assertFalse(tree.exceedsFieldLimit(3))
    }

    @Test
    fun `negative limit is always exceeded`() {
        assertTrue(finalFact().exceedsFieldLimit(-1))
        assertTrue(initialFact().exceedsFieldLimit(-1))
    }

    @Test
    fun `split keeps the short branch within and the long branch over the limit`() {
        val shallow = finalFact(FIELD_A)
        val deep = finalFact(FIELD_B, FIELD_C, FIELD_D)
        val tree = merged(shallow, deep)

        val split = tree.splitFieldLimit(1)

        assertEquals(shallow, split.within)
        assertEquals(deep, split.rest)
        assertEquals(tree, merged(split.within as AccessTree, split.rest as AccessTree))
        assertSplitPartition(tree, 1, split)
    }

    @Test
    fun `split within limit returns the same fact`() {
        val tree = merged(finalFact(FIELD_A), finalFact(FIELD_B, FIELD_C, FIELD_D))

        val split = tree.splitFieldLimit(3)

        assertSame(tree, split.within)
        assertNull(split.rest)
    }

    @Test
    fun `split with every path over the limit returns the same fact as rest`() {
        val chain = finalFact(FIELD_A, FIELD_B, FIELD_C)
        val chainSplit = chain.splitFieldLimit(1)
        assertNull(chainSplit.within)
        assertSame(chain, chainSplit.rest)

        val tree = merged(finalFact(FIELD_A, FIELD_B), finalFact(FIELD_C, FIELD_D, FIELD_A))
        val treeSplit = tree.splitFieldLimit(1)
        assertNull(treeSplit.within)
        assertSame(tree, treeSplit.rest)

        val negativeSplit = finalFact().splitFieldLimit(-1)
        assertNull(negativeSplit.within)
        assertNotNull(negativeSplit.rest)
    }

    @Test
    fun `min field depth is the shallowest terminal path`() {
        assertEquals(0, finalFact().access.minFieldDepth.toInt())
        assertEquals(0, abstractFact().access.minFieldDepth.toInt())
        assertEquals(3, finalFact(FIELD_A, FIELD_B, FIELD_C).access.minFieldDepth.toInt())
        assertEquals(2, finalFact(FIELD_A, FIELD_B, MARK).access.minFieldDepth.toInt())
        assertEquals(1, abstractFact(FIELD_A).access.minFieldDepth.toInt())

        val tree = merged(finalFact(FIELD_A, FIELD_B), finalFact(FIELD_C, FIELD_D, FIELD_A))
        assertEquals(2, tree.access.minFieldDepth.toInt())
        assertEquals(3, tree.access.maxFieldDepth.toInt())

        val terminalAtLimit = merged(finalFact(FIELD_A), finalFact(FIELD_A, FIELD_B, FIELD_C))
        assertEquals(1, terminalAtLimit.access.minFieldDepth.toInt())
        assertEquals(3, terminalAtLimit.access.maxFieldDepth.toInt())
    }

    @Test
    fun `node with every terminal path over the limit is pruned as a whole`() {
        val tree = merged(finalFact(FIELD_A, FIELD_B), finalFact(FIELD_C, FIELD_D, FIELD_A))

        assertNull(tree.access.fieldLimitWithin(1))
        assertSame(tree.access, tree.access.fieldLimitRest(1))

        val split = tree.splitFieldLimit(1)
        assertNull(split.within)
        assertSame(tree, split.rest)
    }

    @Test
    fun `node within the limit is kept as a whole`() {
        val tree = merged(finalFact(FIELD_A), finalFact(FIELD_B, FIELD_C))

        assertSame(tree.access, tree.access.fieldLimitWithin(2))
        assertNull(tree.access.fieldLimitRest(2))
    }

    @Test
    fun `subtrees with every terminal over the limit move to rest unchanged`() {
        val tree = merged(finalFact(FIELD_A), merged(finalFact(FIELD_A, FIELD_B, FIELD_C), finalFact(FIELD_A, FIELD_D, FIELD_B)))

        val split = tree.splitFieldLimit(2)

        assertEquals(setOf(".$FIELD_A.$"), split.within.paths())
        assertEquals(setOf(".$FIELD_A.$FIELD_B.$FIELD_C.$", ".$FIELD_A.$FIELD_D.$FIELD_B.$"), split.rest.paths())

        val (b, d) = with(manager) { FIELD_B.idx to FIELD_D.idx }
        val treeChild = tree.access.getChild(with(manager) { FIELD_A.idx })
        val restChild = (split.rest as AccessTree).access.getChild(with(manager) { FIELD_A.idx })
        assertNotNull(treeChild)
        assertNotNull(restChild)
        assertFalse(restChild.isFinal)
        assertSame(treeChild.getChild(b), restChild.getChild(b))
        assertSame(treeChild.getChild(d), restChild.getChild(d))
        assertSplitPartition(tree, 2, split)
    }

    @Test
    fun `access path field count is precomputed`() {
        val path = initialFact(FIELD_A, ElementAccessor, FIELD_B, MARK) as AccessPath
        assertEquals(3, path.access?.fieldCount?.toInt())
        assertTrue(path.exceedsFieldLimit(2))
        assertFalse(path.exceedsFieldLimit(3))

        val empty = initialFact() as AccessPath
        assertNull(empty.access)
        assertFalse(empty.exceedsFieldLimit(0))
        assertTrue(empty.exceedsFieldLimit(-1))
    }

    @Test
    fun `access graph counts every field accessor of the graph`() {
        val automata = AutomataApManager(UnrollStrategy, Cancellation())
        fun graphFact(vararg accessors: Accessor): AccessGraphFinalFactAp {
            var fact = automata.createFinalAp(base, ExclusionSet.Empty)
            for (accessor in accessors.reversed()) {
                fact = fact.prependAccessor(accessor)
            }
            return fact as AccessGraphFinalFactAp
        }

        val chain = graphFact(FIELD_A, MARK, FIELD_B)
        assertTrue(chain.exceedsFieldLimit(1))
        assertFalse(chain.exceedsFieldLimit(2))

        val shallow = graphFact(FIELD_A)
        val deep = graphFact(FIELD_B, FIELD_C)
        val branching = AccessGraphFinalFactAp(base, shallow.access.merge(deep.access), ExclusionSet.Empty)
        assertTrue(branching.exceedsFieldLimit(2))
        assertFalse(branching.exceedsFieldLimit(3))

        val split = branching.splitFieldLimit(2)
        assertNull(split.within)
        assertSame(branching, split.rest)
    }

    @Test
    fun `split moves deeper children of a terminal node at the limit to rest`() {
        val tree = merged(finalFact(FIELD_A), finalFact(FIELD_A, FIELD_B, FIELD_C))

        val split = tree.splitFieldLimit(1)

        assertEquals(setOf(".$FIELD_A.$"), split.within.paths())
        assertEquals(setOf(".$FIELD_A.$FIELD_B.$FIELD_C.$"), split.rest.paths())

        val restPrefix = (split.rest as AccessTree).access.getChild(with(manager) { FIELD_A.idx })
        assertNotNull(restPrefix)
        assertFalse(restPrefix.isFinal)
        assertFalse(restPrefix.isAbstract)
        assertSplitPartition(tree, 1, split)
    }

    @Test
    fun `split counts any accessor as one field`() {
        val tree = merged(finalFact(FIELD_A), finalFact(AnyAccessor, FIELD_B))

        val split = tree.splitFieldLimit(1)

        assertEquals(setOf(".$FIELD_A.$"), split.within.paths())
        assertEquals(setOf(".$AnyAccessor.$FIELD_B.$"), split.rest.paths())
        assertSplitPartition(tree, 1, split)
    }

    @Test
    fun `split does not count marks and final accessor`() {
        val tree = merged(finalFact(FIELD_A, MARK), finalFact(FIELD_B, FIELD_C, MARK))

        val split = tree.splitFieldLimit(1)

        assertEquals(finalFact(FIELD_A, MARK), split.within)
        assertEquals(finalFact(FIELD_B, FIELD_C, MARK), split.rest)
        assertSplitPartition(tree, 1, split)
    }

    @Test
    fun `split preserves abstract leaves and their deep exclusions`() {
        val shallow = abstractFact(FIELD_A).clearAllAccessorOccurrences(FIELD_D, keepStartAccessor = false) as AccessTree
        val deep = abstractFact(FIELD_B, FIELD_C)
        val tree = merged(merged(shallow, deep), finalFact(FIELD_B))

        val split = tree.splitFieldLimit(1)

        val shallowAbstract = shallow.access.getChild(with(manager) { FIELD_A.idx })
        assertNotNull(shallowAbstract?.deepAccessorExclusion)
        assertEquals(shallow.paths() + finalFact(FIELD_B).paths(), split.within.paths())
        assertEquals(deep.paths(), split.rest.paths())
        assertSplitPartition(tree, 1, split)
    }

    @Test
    fun `split preserves base exclusions on both parts`() {
        val exclusions = ExclusionSet.Concrete(FIELD_D)
        val tree = merged(finalFact(FIELD_A), finalFact(FIELD_B, FIELD_C)).replaceExclusions(exclusions) as AccessTree

        val split = tree.splitFieldLimit(1)

        assertEquals(exclusions, split.within?.exclusions)
        assertEquals(exclusions, split.rest?.exclusions)
        assertSplitPartition(tree, 1, split)
    }
}
