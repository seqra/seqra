package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.strongKeep
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Expected leaves are stated directly; no reference transfer computes them. */
/** Full-mark primitive/reference regression; selected GROUP behavior has separate differential controls. */
class Run1FieldPreservationTest {
    private val m = testApManager(Cancellation())
    private val ops = ApOps(m)
    private val run1 = ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.FULL)
    private val later = ApMode(false, Direction.FORWARD, 3)
    private val x = AccessPathBase.Argument(0)
    private val c = m.accessors.index(ClassStaticAccessor("C"))
    private val f = m.accessors.index(FieldAccessor("C", "f", "Box"))
    private val g = m.accessors.index(FieldAccessor("C", "g", "Box"))
    private val h = m.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val t = m.marks.mark("T")
    private val u = m.marks.mark("U")
    private val empty = ExclusionSet.Empty
    private val origin = m.initial(x, null, Tail.STAR, empty, MarkSlot.STAR)

    private fun fact(path: List<Int>, tail: Tail = Tail.STAR, mark: MarkSlot = MarkSlot.STAR,
                     layer: Layer = Layer.NORMAL, base: AccessPathBase = x, e: ExclusionSet = empty): Facts =
        m.factsOf(PathFact(base, path, tail, mark), e, layer)

    private fun leaf(path: List<Int>, tail: Tail = Tail.STAR, mark: MarkSlot = MarkSlot.STAR,
                     layer: Layer = Layer.NORMAL, base: AccessPathBase = x, e: ExclusionSet = empty): Conclusion =
        Conclusion(PathFact(base, path, tail, mark), e, layer == Layer.DEMAND)

    private fun decoded(values: Collection<Facts>): Set<Conclusion> = values.flatMap { value ->
        ops.leaves(value).map { Conclusion(it.fact, it.exclusion, value.layer == Layer.DEMAND) }.toList()
    }.toSet()

    private fun keep(input: Facts, destination: List<Int> = listOf(f), mode: ApMode = run1): CollectingOut =
        CollectingOut().also { out ->
            for (edge in strongKeep(input.base, destination))
                ops.applyEdge(input, origin, edge, false, mode, out)
        }

    @Test fun `broad preservation demotes its existing STAR payload without an exclusion`() {
        for (layer in Layer.entries) {
            val result = keep(fact(emptyList(), layer = layer))
            assertEquals(setOf(leaf(emptyList(), layer = Layer.DEMAND)), decoded(result.results))
            assertEquals(Tail.STAR, (result.results.single() as FlowTree).tail)
            assertTrue(result.markRequests.isEmpty())
        }
    }

    @Test fun `whole destination and descendants are killed in both layers`() {
        for (layer in Layer.entries) for (path in listOf(listOf(f), listOf(f,h))) {
            assertTrue(keep(fact(path, layer = layer)).results.isEmpty())
            assertTrue(keep(fact(path, Tail.EXACT, MarkSlot.Concrete(t), layer)).results.isEmpty())
            assertTrue(keep(fact(path, Tail.ANY_TAINT, MarkSlot.Concrete(t), layer)).results.isEmpty())
        }
    }

    @Test fun `disjoint sibling and exact ancestor retain their own layers`() {
        for (layer in Layer.entries) {
            assertEquals(setOf(leaf(listOf(g), layer = layer)), decoded(keep(fact(listOf(g), layer = layer)).results))
            val exact = fact(emptyList(), Tail.EXACT, MarkSlot.Concrete(t), layer)
            assertEquals(setOf(leaf(emptyList(), Tail.EXACT, MarkSlot.Concrete(t), layer)), decoded(keep(exact).results))
        }
    }

    @Test fun `packed ancestor destination and sibling get independent preservation results`() {
        val root = FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(f,g),
            arrayOf(FlowAlgebra.LEAF_NODE,FlowAlgebra.LEAF_NODE))
        val input = m.flowTree(x, Layer.NORMAL, empty, MarkSet.EMPTY, root)
        assertEquals(setOf(leaf(emptyList(), layer = Layer.DEMAND),leaf(listOf(g))), decoded(keep(input).results))
    }

    @Test fun `self assignment comes from pre store value edge after old destination kill`() {
        val input = fact(listOf(f), Tail.EXACT, MarkSlot.Concrete(t))
        val out = keep(input)
        assertTrue(out.results.isEmpty()) // Constant or empty field writeback has no old result.
        val identity = PathEdge(PathFact(x,listOf(f),Tail.STAR,MarkSlot.STAR),
            PathFact(x,listOf(f),Tail.STAR,MarkSlot.STAR),empty)
        ops.applyEdge(input,origin,identity,false,run1,out)
        assertEquals(setOf(leaf(listOf(f),Tail.EXACT,MarkSlot.Concrete(t))),decoded(out.results))
    }

    @Test fun `later definite preservation keeps strong spatial exclusions`() {
        val result = keep(fact(emptyList()), mode = later)
        assertEquals(setOf(leaf(emptyList(), e = ExclusionSet.of(f))), decoded(result.results))
    }

    @Test fun `partial must cleaning creates DEMAND Empty E without changing the must payload`() {
        val input = fact(emptyList(),Tail.ANY_TAINT,MarkSlot.Concrete(t))
        val cleaner = Cleaner(x,m.path(listOf(f)),CleanReach.AT_AND_BELOW,t)
        val out = CollectingOut()
        ops.clean(input,origin,cleaner,run1,out)
        assertEquals(setOf(leaf(emptyList(),Tail.ANY_TAINT,MarkSlot.Concrete(t),Layer.DEMAND)),decoded(out.results))
        assertEquals(Tail.ANY_TAINT,(out.results.single() as TaintTree).anyTail)
        assertEquals(empty,(out.results.single() as TaintTree).exclusion)
    }

    @Test fun `direct complete selected mark cleaning still removes the selected mark`() {
        val out = CollectingOut()
        ops.clean(fact(listOf(f),Tail.EXACT,MarkSlot.Concrete(t)),origin,
            Cleaner(x,m.path(listOf(f)),CleanReach.AT_AND_BELOW,t),run1,out)
        assertTrue(out.results.isEmpty())
    }

    @Test fun `static selected cleaner neither requests nor excludes on a disjoint field`() {
        val input = fact(listOf(c,g),base = STATIC)
        val out = CollectingOut()
        ops.clean(input,origin,Cleaner(STATIC,m.path(listOf(c,f)),CleanReach.AT_AND_BELOW,t),run1,out)
        assertTrue(out.markRequests.isEmpty())
        assertEquals(setOf(leaf(listOf(c,g),base = STATIC)),decoded(out.results))
    }

    @Test fun `static selected cleaner partitions mixed field headers before changing mx`() {
        val root = FlowAlgebra.prepend(m,m.path(listOf(c)),FlowAlgebra.node(FlowLeaf.NONE,
            intArrayOf(f,g),arrayOf(FlowAlgebra.LEAF_NODE,FlowAlgebra.LEAF_NODE)))
        val input = m.flowTree(STATIC,Layer.NORMAL,empty,MarkSet.EMPTY,root)
        val out = CollectingOut()
        ops.clean(input,origin,Cleaner(STATIC,m.path(listOf(c,f)),CleanReach.AT_AND_BELOW,t),run1,out)
        assertEquals(listOf(t),out.markRequests)
        assertEquals(setOf(leaf(listOf(c,f),mark = MarkSlot.Star(m.markSetOf(t)),base = STATIC),
            leaf(listOf(c,g),base = STATIC)),decoded(out.results))
    }

    @Test fun `ordinary stored root identity preserves actual field geometry for both layers`() {
        val premise = m.initial(STATIC,null,Tail.STAR,empty,MarkSlot.STAR)
        for (mode in listOf(run1,later)) for (inputLayer in Layer.entries) for (summaryLayer in Layer.entries) {
            val summary = fact(emptyList(),layer = summaryLayer,base = STATIC)
            for ((tail,mark) in listOf(Tail.STAR to MarkSlot.Star(m.markSetOf(u)),Tail.EXACT to MarkSlot.Concrete(t))) {
                val input = fact(listOf(c,f),tail,mark,inputLayer,STATIC)
                val part = assertNotNull(ops.satisfying(input,premise,mode))
                val out = CollectingOut()
                ops.applySummary(part,premise,summary,mode,out)
                val layer = if (inputLayer == Layer.DEMAND || summaryLayer == Layer.DEMAND) Layer.DEMAND else Layer.NORMAL
                assertEquals(setOf(leaf(listOf(c,f),tail,mark,layer,STATIC)),decoded(out.results))
            }
        }
    }

    @Test fun `run1 ancestor summary matching retains incoming exclusion and rejects excluded fields`() {
        val premise = m.initial(STATIC,m.path(listOf(c,f)),Tail.STAR,empty,MarkSlot.Concrete(t))
        val excluded = fact(listOf(c),Tail.ANY_TAINT,MarkSlot.Concrete(t),base = STATIC,e = ExclusionSet.of(f))
        assertNull(ops.satisfying(excluded,premise,run1))
        val admitted = fact(listOf(c),Tail.ANY_TAINT,MarkSlot.Concrete(t),base = STATIC,e = ExclusionSet.of(g))
        val matched = assertNotNull(ops.satisfying(admitted,premise,run1)) as TaintTree
        assertEquals(ExclusionSet.of(g),matched.exclusion)
        assertEquals(setOf(leaf(listOf(c),Tail.ANY_TAINT,MarkSlot.Concrete(t),base = STATIC,e = ExclusionSet.of(g))),decoded(listOf(matched)))
        assertNull(ops.satisfying(fact(listOf(c),Tail.EXACT,MarkSlot.Concrete(t),base = STATIC),premise,run1))
    }

    @Test fun `tail and layer are separate grouping coordinates`() {
        val star = fact(listOf(f),layer = Layer.DEMAND) as FlowTree
        val any = fact(listOf(f),Tail.ANY,layer = Layer.DEMAND) as FlowTree
        assertNotEquals(star.groupKey,any.groupKey)
        val group = FlowGroup(m,m.newInterners().flow)
        assertNotNull(group.add(star))
        assertNotNull(group.add(any))
        assertEquals(setOf(Tail.STAR,Tail.ANY),group.all().map { it.tail }.toSet())
    }

    @Test fun `stored may subsumes descendants without confusing demand must with may`() {
        val groups = ConclusionGroup(m,m.newInterners())
        assertNotNull(groups.add(fact(emptyList(),Tail.ANY,MarkSlot.Concrete(t),Layer.DEMAND)))
        for (tail in listOf(Tail.EXACT,Tail.ANY_TAINT,Tail.ANY))
            assertNull(groups.add(fact(listOf(f),tail,MarkSlot.Concrete(t),Layer.DEMAND)))
        val must = ConclusionGroup(m,m.newInterners())
        assertNotNull(must.add(fact(emptyList(),Tail.ANY_TAINT,MarkSlot.Concrete(t),Layer.DEMAND)))
        assertNotNull(must.add(fact(listOf(f),Tail.EXACT,MarkSlot.Concrete(t),Layer.DEMAND)))
        assertNotNull(must.add(fact(listOf(f),Tail.ANY,MarkSlot.Concrete(t),Layer.DEMAND)))
        val flow = ConclusionGroup(m,m.newInterners())
        assertNotNull(flow.add(fact(emptyList(),Tail.ANY,layer = Layer.DEMAND)))
        assertNull(flow.add(fact(listOf(f),layer = Layer.DEMAND)))
    }

    @Test fun `identical subtraction respects any tail but exact leaves are annotation free`() {
        val root = m.taintAlg.leafNode(m.taintLeaves(m.markSetOf(t),m.markSetOf(u)))
        val must = m.taintTree(x,Layer.DEMAND,root,anyTail = Tail.ANY_TAINT)
        val may = m.taintTree(x,Layer.DEMAND,root,anyTail = Tail.ANY)
        assertSame(must,ops.without(must,may), "A mixed operand with another any-tail annotation stays unmatched")
        val exact = fact(emptyList(),Tail.EXACT,MarkSlot.Concrete(t),Layer.DEMAND)
        val rest = assertNotNull(ops.without(must,exact))
        assertEquals(setOf(leaf(emptyList(),Tail.ANY_TAINT,MarkSlot.Concrete(u),Layer.DEMAND)),decoded(listOf(rest)))
        assertNull(ops.without(must,must))
    }

    @Test fun `mixed E cover demotes only must leaves and retains precise exact leaves`() {
        val marks = m.markSetOf(t)
        val root = m.taintAlg.leafNode(m.taintLeaves(marks,marks))
        val input = m.taintTree(x,Layer.NORMAL,root,ExclusionSet.of(f))
        val out = ArrayList<Facts>()
        ops.run1Cover(input) { out += it }
        assertEquals(setOf(leaf(emptyList(),Tail.EXACT,MarkSlot.Concrete(t)),
            leaf(emptyList(),Tail.ANY_TAINT,MarkSlot.Concrete(t),Layer.DEMAND)),decoded(out))
        assertFails { ops.run1Cover(input) }
    }

    @Test fun `an exact output ignores irrelevant shared field exclusions`() {
        val input = fact(emptyList(),Tail.ANY_TAINT,MarkSlot.Concrete(t))
        val edge = PathEdge(PathFact(x,listOf(f),Tail.STAR,MarkSlot.Concrete(t)),
            PathFact(AccessPathBase.Return,emptyList(),Tail.EXACT,MarkSlot.Concrete(u)),ExclusionSet.of(g))
        val out = CollectingOut()
        ops.applyEdge(input,origin,edge,false,run1,out)
        assertEquals(setOf(leaf(emptyList(),Tail.EXACT,MarkSlot.Concrete(u),base = AccessPathBase.Return)),decoded(out.results))
    }
}
