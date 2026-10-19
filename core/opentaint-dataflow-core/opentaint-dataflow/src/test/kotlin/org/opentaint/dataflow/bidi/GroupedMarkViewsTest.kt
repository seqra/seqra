package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class GroupedMarkViewsTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val t = m.marks.reserveFull("outer#left;state;v;1")
    private val u = m.marks.reserveFull("outer#right;state;v;2")
    private val v = m.marks.reserveFull("other;state;v;3")
    private val group = ApMode(true, Direction.FORWARD, 1)
    private val full = ApMode(false, Direction.FORWARD, 1)

    init {
        m.marks.register(m.marks.name(t), MarkGroupKey.Rule("outer"))
        m.marks.register(m.marks.name(u), MarkGroupKey.Rule("outer"))
        m.marks.register(m.marks.name(v), MarkGroupKey.Rule("other"))
        listOf(t, u, v).forEach(m.marks::markEligible)
        m.marks.finishOwnershipCensus()
    }
    private val g = m.marks.group(m.marks.groupOf(t))
    private val h = m.marks.group(m.marks.groupOf(v))
    private fun p(base: AccessPathBase, atom: TaintMark, tail: Tail = Tail.EXACT) =
        PathFact(base, emptyList(), tail, MarkSlot.Concrete(atom))
    private fun taint(atom: TaintMark, mode: ApMode = group): Facts =
        ops.runtimeFact(m.factsOf(p(x, atom), ExclusionSet.Empty, Layer.NORMAL), mode)
    private fun onlyAtom(f: Facts): TaintMark =
        ((ops.leaves(f).single().fact.mark) as MarkSlot.Concrete).mark

    @Test fun `full handles exist before census without guessing an outer rule`() {
        val catalog = MarkTable()
        val a = catalog.reserveFull("rule#with#literal-hash;classifier;v;0")
        assertEquals("rule#with#literal-hash;classifier;v;0", catalog.name(a))
        assertFails { catalog.key(a) }
        catalog.register(catalog.name(a), MarkGroupKey.Rule("rule#with#literal-hash"))
        catalog.markEligible(a)
        catalog.finishOwnershipCensus()
        assertEquals(a, catalog.reserveFull(catalog.name(a)))
        assertIs<MarkAtomKey.Full>(catalog.key(a))
        assertFails { catalog.register(catalog.name(a), MarkGroupKey.Rule("rule")) }
    }

    @Test fun `full group and control atoms use distinct namespaces`() {
        assertNotEquals(t, g)
        assertNotEquals(TaintMark.ZERO, t)
        assertNotEquals(TaintMark.ZERO, g)
        assertEquals(g, m.marks.runtimeAtom(u, MarkMode.GROUP))
        assertEquals(TaintMark.ZERO, m.marks.runtimeAtom(TaintMark.ZERO, MarkMode.GROUP))
        assertFails { m.marks.runtimeAtom(g, MarkMode.FULL) }
        assertEquals(listOf(t, u), m.marks.eligibleMembers(m.marks.groupOf(t)))
    }

    @Test fun `model fallback is installed only after all declarations and survives extension`() {
        val catalog = MarkTable()
        val imported = catalog.observeModelReference("shared")
        catalog.register("shared", MarkGroupKey.Model("explicit"))
        val undeclared = catalog.observeModelReference("plain")
        catalog.finishOwnershipCensus()
        assertNotEquals(catalog.groupOf(imported), catalog.groupOf(undeclared))
        val late = catalog.observeModelReference("late")
        assertEquals(catalog.groupOf(undeclared), catalog.groupOf(late))
        assertFails { catalog.register("plain", MarkGroupKey.Model("too late")) }
    }

    @Test fun `typed exclusion union intersection and extension-safe subset follow denotation`() {
        val eg = MarkExclusions.of(m.marks, g)
        val eu = MarkExclusions.of(m.marks, u)
        val ev = MarkExclusions.of(m.marks, v)
        assertTrue(t in eg)
        assertTrue(u in eg)
        assertFalse(v in eg)
        assertEquals(eu, eg.intersect(eu))
        assertEquals(eu, eu.intersect(eg))
        assertEquals(MarkExclusions.EMPTY, eg.intersect(ev))
        assertEquals(eg, eg.union(eu))
        assertTrue(eu.isSubsetOf(eg))
        assertFalse(eg.isSubsetOf(MarkExclusions.of(m.marks, listOf(t, u))))
        m.marks.register("late-outer", MarkGroupKey.Rule("outer"))
        val late = m.marks.observeModelReference("late-outer")
        assertTrue(late in eg)
    }

    @Test fun `cached full primitive projects at execution and keeps exact geometry`() {
        val edge = PathEdge(p(x, t), p(y, v), ExclusionSet.Empty)
        val initial = m.initial(Pattern(p(x, g), ExclusionSet.Empty))
        val out = CollectingOut()
        ops.applyEdge(taint(u), initial, edge, false, group, out)
        assertEquals(1, out.results.size)
        assertEquals(h, onlyAtom(out.results.single()))
        assertEquals(Tail.EXACT, ops.leaves(out.results.single()).single().fact.tail)
        assertEquals(Layer.DEMAND, out.results.single().layer)
        assertEquals(MarkSlot.Concrete(t), edge.from.mark)
        assertEquals(MarkSlot.Concrete(v), edge.to.mark)
        val later = CollectingOut()
        ops.applyEdge(taint(u, full), m.initial(Pattern(p(x, u), ExclusionSet.Empty)), edge, false, full, later)
        assertTrue(later.results.isEmpty(), "Full U is not full T despite a shared owner")
    }

    @Test fun `group cleaner cannot kill wildcard while RemoveAll still kills`() {
        val input = taint(t)
        val initial = m.initial(Pattern(p(x, g), ExclusionSet.Empty))
        val selected = CollectingOut()
        ops.clean(input, initial, Cleaner(x, null, CleanReach.EXACT, u), group, selected)
        assertEquals(listOf(input), selected.results)
        val all = CollectingOut()
        ops.clean(input, initial, Cleaner(x, null, CleanReach.EXACT, null), group, all)
        assertTrue(all.results.isEmpty())
    }

    @Test fun `an already excluded group still produces its required FLOW interest`() {
        val flow = m.flowTree(x, Layer.NORMAL, ExclusionSet.Empty,
            MarkExclusions.of(m.marks, g), FlowAlgebra.LEAF_NODE, Tail.STAR)
        val checked = ops.checkMark(flow, Pattern(p(x, t), ExclusionSet.Empty), group)
        assertEquals(g, assertIs<MarkCheck.Request>(checked).mark)
        val out = CollectingOut()
        val initial = m.initial(x, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
        ops.clean(flow, initial, Cleaner(x, null, CleanReach.EXACT, t), group, out)
        assertEquals(listOf(g), out.markRequests)
        assertEquals<List<Facts>>(listOf(flow), out.results)
    }

    @Test fun `excluded target records group interest before a disjoint transfer produces no value`() {
        val f = m.accessors.index(org.opentaint.dataflow.ap.ifds.FieldAccessor("Box", "f", "Box"))
        val k = m.accessors.index(org.opentaint.dataflow.ap.ifds.FieldAccessor("Box", "k", "Box"))
        val flow = m.flowTree(x, Layer.NORMAL, ExclusionSet.Empty, MarkExclusions.EMPTY,
            FlowAlgebra.prepend(m, m.path(listOf(f)), FlowAlgebra.LEAF_NODE), Tail.STAR)
        val edge = PathEdge(PathFact(x, listOf(k), Tail.STAR, MarkSlot.STAR),
            PathFact(y, emptyList(), Tail.STAR, MarkSlot.Star(MarkExclusions.of(m.marks, t))), ExclusionSet.Empty)
        val out = CollectingOut()
        ops.applyEdge(flow, m.initial(x, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR),
            edge, false, group, out)
        assertEquals(listOf(g), out.markRequests)
        assertTrue(out.results.isEmpty())
    }

    @Test fun `group guidance intersects an actual full query without inventing another member`() {
        val guide = DemandPattern(Pattern(p(x, g, Tail.ANY), ExclusionSet.Empty), null)
        val starts = ops.emit(guide, taint(u, full))
        assertEquals(listOf(MarkSlot.Concrete(u)), starts.map { it.mark })
        assertTrue(ops.emit(guide, taint(v, full)).isEmpty())
        assertFalse(m.marks.coversAtom(t, g))
        assertTrue(m.marks.coversAtom(g, u))
    }

    @Test fun `an inherited group exclusion rejects every later full member in record application`() {
        val edge = PathEdge(PathFact(x, emptyList(), Tail.STAR, MarkSlot.STAR),
            PathFact(y, emptyList(), Tail.STAR, MarkSlot.Star(MarkExclusions.of(m.marks, g))), ExclusionSet.Empty)
        val initial = m.initial(x, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
        for (atom in listOf(t, u, v)) {
            val out = CollectingOut()
            ops.applyEdge(taint(atom, full), initial, edge, false, full, out)
            assertEquals(if (atom == v) listOf(v) else emptyList(), out.results.map(::onlyAtom))
        }
    }

    @Test fun `group mark policy is the existential lift of the existing full policy`() {
        val accepts = object : FactTypeChecker.FactApFilter {
            override fun check(accessor: org.opentaint.dataflow.ap.ifds.Accessor) =
                FactTypeChecker.FilterResult.Accept
        }
        val keepU = TypeFilter(accepts, MarkPolicy { mark, level -> mark == u && level == 0 })
        assertEquals(g, onlyAtom(assertNotNull(ops.filter(taint(t), keepU))))
        assertNull(ops.filter(taint(v), keepU))
        assertNull(ops.filter(taint(t, full), keepU))
        assertEquals(u, onlyAtom(assertNotNull(ops.filter(taint(u, full), keepU))))
    }
}
