package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testApManager
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import org.opentaint.dataflow.bidi.interp.Guard
import org.opentaint.dataflow.bidi.interp.ZERO_FACT
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

/** Independent scalar observations for the selected run-1 alias contract. */
class Run1SummaryAliasPartitionTest {
    private val owner = MarkGroupKey.Rule("one")
    private val manager = testApManager(Cancellation(), declarations = testDeclarations(
        TestMark("T", owner), TestMark("U", owner), TestMark("V")))
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val group = manager.marks.group(manager.marks.groupOf(t))
    private val other = manager.marks.group(manager.marks.groupOf(manager.marks.mark("V")))
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private fun path(tail: Tail, mark: MarkSlot, fields: List<Int> = emptyList(), base: AccessPathBase = x) =
        PathFact(base, fields, tail, mark)
    private fun leaves(facts: Facts) = ops.leaves(facts).toList()

    @Test fun `raw scalar predicate checks all tail pairs without starting an initial`() {
        for (a in Tail.entries) for (b in Tail.entries) {
            assertEquals(a == Tail.STAR && b == Tail.STAR,
                hasNoMemoryEffect(path(a, MarkSlot.STAR), path(b, MarkSlot.Star(manager.markSetOf(t)))))
            assertTrue(hasNoMemoryEffect(path(a, MarkSlot.Concrete(group)), path(b, MarkSlot.Concrete(group))))
            assertFalse(hasNoMemoryEffect(path(a, MarkSlot.Concrete(t)), path(b, MarkSlot.Concrete(u))),
                "Different full marks stay different even within one group")
        }
        assertFalse(hasNoMemoryEffect(ZERO_FACT, ZERO_FACT))
        assertFalse(hasNoMemoryEffect(path(Tail.EXACT, MarkSlot.Concrete(group)),
            path(Tail.ANY, MarkSlot.Concrete(group), listOf(f))))
        assertFalse(hasNoMemoryEffect(path(Tail.EXACT, MarkSlot.Concrete(group)),
            path(Tail.EXACT, MarkSlot.Concrete(group), base = AccessPathBase.Return)))
        assertFalse(hasNoMemoryEffect(path(Tail.STAR, MarkSlot.STAR), path(Tail.EXACT, MarkSlot.Concrete(group))))
    }

    @Test fun `run1 guard admits unknown but never admits known no effect through DEMAND`() {
        for (layer in Layer.entries) for (flag in listOf(null, false, true)) {
            assertEquals(flag != true, Guard.MemoryEffect.admits(run1 = true, noEffect = flag, layer = layer))
        }
        for (layer in Layer.entries) for (flag in listOf(false, true)) {
            assertEquals(layer == Layer.DEMAND || !flag,
                Guard.MemoryEffect.admits(run1 = false, noEffect = flag, layer = layer))
        }
    }

    @Test fun `packed TAINT partitions marks and paths while preserving tail layer and E`() {
        val premise = manager.initial(Pattern(path(Tail.STAR, MarkSlot.Concrete(group), listOf(f)), ExclusionSet.Empty))
        val atF = manager.taintLeaves(manager.markSetOf(group) + manager.markSetOf(other), manager.markSetOf(group))
        val atG = manager.taintLeaves(MarkSet.EMPTY, manager.markSetOf(group))
        val root = manager.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(f, g), arrayOf(
            manager.taintAlg.leafNode(atF), manager.taintAlg.leafNode(atG)))
        val exclusion = ExclusionSet.of(g)
        val packed = manager.taintTree(x, Layer.DEMAND, root, exclusion, Tail.ANY_TAINT)
        val original = leaves(packed)
        val parts = manager.run1SummaryParts(premise, packed)
        val identities = parts.filter { it.second }.flatMap { leaves(it.first) }
        val effects = parts.filterNot { it.second }.flatMap { leaves(it.first) }
        assertEquals(original.filter { it.fact.path == listOf(f) && it.fact.mark == MarkSlot.Concrete(group) }.toSet(), identities.toSet())
        assertEquals(original.filterNot { it.fact.path == listOf(f) && it.fact.mark == MarkSlot.Concrete(group) }.toSet(), effects.toSet())
        assertEquals(original.toSet(), (identities + effects).toSet())
        assertTrue(identities.none { it in effects })
        assertTrue(parts.all { it.first.layer == Layer.DEMAND })
        assertTrue(identities.any { it.fact.tail == Tail.ANY_TAINT && it.exclusion == exclusion })
        val effectTree = parts.single { !it.second }.first as TaintTree
        assertSame(root.child(g), effectTree.root.child(g), "A different path remains shared")
    }

    @Test fun `FLOW partition keeps mx and actual field tails across layers`() {
        val premise = manager.initial(x, manager.path(listOf(f)), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
        val root = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(f, g), arrayOf(FlowAlgebra.LEAF_NODE, FlowAlgebra.LEAF_NODE))
        for (layer in Layer.entries) for (tail in listOf(Tail.STAR, Tail.ANY)) {
            val packed = manager.flowTree(x, layer, ExclusionSet.Empty, manager.markSetOf(group), root, tail)
            val parts = manager.run1SummaryParts(premise, packed)
            assertEquals(leaves(packed).toSet(), parts.flatMap { leaves(it.first) }.toSet())
            assertTrue(parts.all {
                val value = it.first as FlowTree
                value.markExclusion == packed.markExclusion && value.tail == tail
            })
            assertEquals(tail == Tail.STAR, parts.any { it.second })
            if (tail == Tail.STAR) assertEquals(listOf(listOf(f)), leaves(parts.single { it.second }.first).map { it.fact.path })
        }
    }

    @Test fun `raw STAR concrete initial matches exact result and no matching payload returns original`() {
        val premise = manager.initial(Pattern(path(Tail.STAR, MarkSlot.Concrete(group)), ExclusionSet.Empty))
        val result = manager.factsOf(path(Tail.EXACT, MarkSlot.Concrete(group)), ExclusionSet.Empty, Layer.DEMAND)
        val parts = manager.run1SummaryParts(premise, result)
        assertEquals(listOf(result to true), parts)
        val effect = manager.factsOf(path(Tail.EXACT, MarkSlot.Concrete(other)), ExclusionSet.Empty, Layer.DEMAND)
        assertSame(effect, manager.run1SummaryParts(premise, effect).single().first)
        assertFalse(manager.run1SummaryParts(manager.zero, result).single().second)
    }

    @Test fun `partition uses an iterative exact spine and does not absorb descendant paths`() {
        val fields = List(1200) { if (it % 2 == 0) f else g }
        val premise = manager.initial(Pattern(path(Tail.EXACT, MarkSlot.Concrete(group), fields), ExclusionSet.Empty))
        val selected = manager.taintAlg.prepend(manager, premise.path, manager.taintAlg.leafNode(
            manager.taintLeaves(manager.markSetOf(group), MarkSet.EMPTY)))
        val descendant = manager.taintAlg.prepend(manager, manager.path(fields + f), manager.taintAlg.leafNode(
            manager.taintLeaves(manager.markSetOf(group), MarkSet.EMPTY)))
        val result = manager.taintTree(x, Layer.DEMAND, manager.taintAlg.mergeAdd(selected, descendant))
        val parts = manager.run1SummaryParts(premise, result)
        assertEquals(listOf(fields), leaves(parts.single { it.second }.first).map { it.fact.path })
        assertEquals(listOf(fields + f), leaves(parts.single { !it.second }.first).map { it.fact.path })
    }
}
