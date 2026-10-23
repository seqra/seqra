package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testApManager
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import org.opentaint.dataflow.bidi.interp.FormKey
import org.opentaint.dataflow.bidi.interp.FormOwner
import org.opentaint.dataflow.bidi.interp.OperationId
import org.opentaint.dataflow.bidi.interp.ResumeSite
import org.opentaint.dataflow.bidi.interp.ZERO_FACT
import org.opentaint.dataflow.bidi.interp.starEdge
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class CanonicalGroupProjectionTest {
    private val declarations = testDeclarations(
        TestMark("T", MarkGroupKey.Rule("one")), TestMark("U", MarkGroupKey.Rule("one")),
        TestMark("V", MarkGroupKey.Rule("two")),
    )
    private val m = testApManager(Cancellation(), declarations = declarations)
    private val grouped = ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.GROUP)
    private val full = ApMode(false, Direction.BACKWARD, 1)
    private val x = AccessPathBase.Argument(0)
    private val t = m.marks.full("T")
    private val u = m.marks.full("U")
    private val v = m.marks.full("V")
    private val g = m.marks.group(m.marks.groupOf(t))
    private val h = m.marks.group(m.marks.groupOf(v))
    private val f = m.accessors.index(FieldAccessor("Box", "f", "Box"))

    /** Scalar key oracle: no runtimeAtom/runtimeMarks/runtimeFact calls. */
    private fun scalarAtom(table: MarkTable, mark: TaintMark): TaintMark = when (val key = table.key(mark)) {
        MarkAtomKey.ControlZero -> TaintMark.ZERO
        is MarkAtomKey.Full -> table.group(key.mark.group)
        is MarkAtomKey.Group -> table.group(key.id)
    }

    private fun set(first: TaintMark, second: TaintMark? = null, third: TaintMark? = null) =
        MarkSet(listOfNotNull(first, second, third).map { it.id }.toIntArray())

    @Test fun `canonical group taint wrappers and their roots are reused only after normalization`() {
        for (tail in listOf(Tail.ANY, Tail.ANY_TAINT)) {
            val root = m.taintAlg.prepend(m, m.path(listOf(f)),
                m.taintAlg.leafNode(m.taintLeaves(set(g, TaintMark.ZERO), set(h))))
            val exclusion = if (tail == Tail.ANY_TAINT) ExclusionSet.Concrete(intArrayOf(f)) else ExclusionSet.Empty
            val value = m.taintTree(x, Layer.DEMAND, root, exclusion, tail)
            assertSame(value, m.runtimeFact(value, grouped))
            assertSame(root, assertIs<TaintTree>(m.runtimeFact(value, grouped)).root)
            assertSame(value, m.runtimeFact(value, full))
        }
        assertSame(Reach.NORMAL, m.runtimeFact(Reach.NORMAL, grouped))
        assertSame(Reach.DEMAND, m.runtimeFact(Reach.DEMAND, grouped))
    }

    @Test fun `unknown demand facts with FULL atoms still project every leaf and canonicalize the tail`() {
        val exact = m.taintAlg.leafNode(m.taintLeaves(set(t, h, TaintMark.ZERO), MarkSet.EMPTY))
        // Publicly supplied geometry need not have used the manager factory.
        val raw = TaintTree(x, Layer.DEMAND, exact, anyTail = Tail.ANY)
        val actual = assertIs<TaintTree>(m.runtimeFact(raw, grouped))
        assertNotSame(raw, actual)
        assertEquals(Tail.ANY_TAINT, actual.anyTail)
        assertEquals(setOf(g, h, TaintMark.ZERO), actual.root.leaf.exact.ids.map(::TaintMark).toSet())
        assertSame(actual, m.runtimeFact(actual, grouped))
        assertSame(raw, m.runtimeFact(raw, full))
    }

    @Test fun `flow reuse preserves static demotion field exclusions tail and projected mark exclusions`() {
        for (base in listOf(x, STATIC)) for (tail in listOf(Tail.STAR, Tail.ANY)) {
            val exclusion = if (tail == Tail.STAR) ExclusionSet.Concrete(intArrayOf(f)) else ExclusionSet.Empty
            val input = m.flowTree(base, Layer.NORMAL, exclusion,
                MarkExclusions.of(m.marks, listOf(t, h, TaintMark.ZERO)), FlowAlgebra.LEAF_NODE, tail)
            val result = assertIs<FlowTree>(m.runtimeFact(input, grouped))
            assertEquals(if (base == STATIC) Layer.DEMAND else Layer.NORMAL, result.layer)
            assertEquals(tail, result.tail)
            assertEquals(exclusion, result.exclusion)
            assertSame(input.root, result.root)
            assertEquals(setOf(g, h, TaintMark.ZERO), result.markExclusion.ids.map(::TaintMark).toSet())
            assertSame(result, m.runtimeFact(result, grouped))
            assertSame(input, m.runtimeFact(input, full))
        }
    }

    @Test fun `receiving manager selects its own leaf even if both managers share every mark set`() {
        val other = ApManager(Cancellation(), marks = m.marks)
        val shared = MarkSet(intArrayOf(g.id))
        assertSame(shared, m.intern(shared))
        assertSame(shared, other.intern(shared))
        val sourceLeaf = m.taintLeaves(shared, MarkSet.EMPTY)
        val receivingLeaf = other.taintLeaves(shared, MarkSet.EMPTY)
        assertNotSame(sourceLeaf, receivingLeaf)
        val roots = listOf(m.taintAlg.leafNode(sourceLeaf),
            m.taintAlg.prepend(m, m.path(listOf(f)), m.taintAlg.leafNode(sourceLeaf)),
            m.taintAlg.node(sourceLeaf, intArrayOf(f), arrayOf(other.taintAlg.leafNode(receivingLeaf))))
        for (root in roots) {
            val input = m.taintTree(x, Layer.DEMAND, root)
            val projected = assertIs<TaintTree>(other.runtimeFact(input, grouped))
            assertNotSame(input, projected)
            val leaf = if (projected.root.leaf.isEmpty) checkNotNull(projected.root.child(f)).leaf
                else projected.root.leaf
            assertSame(receivingLeaf, leaf)
            assertSame(receivingLeaf.exact, leaf.exact)
            projected.root.child(f)?.let { assertSame(receivingLeaf, it.leaf) }
            assertSame(projected, other.runtimeFact(projected, grouped))
        }
    }

    @Test fun `equal uninterned leaf payloads join the receiving manager leaf interner`() {
        val canonical = m.taintLeaves(set(g), set(h))
        val rawLeaf = TaintLeaves(canonical.exact, canonical.any)
        assertNotSame(canonical, rawLeaf)
        val rawRoot = TrieNode(rawLeaf, null, null, true)
        val input = TaintTree(x, Layer.DEMAND, rawRoot)
        val projected = assertIs<TaintTree>(m.runtimeFact(input, grouped))
        assertNotSame(input, projected)
        assertSame(canonical, projected.root.leaf)
        assertSame(projected, m.runtimeFact(projected, grouped))
    }

    @Test fun `equal numeric IDs from distinct catalogs are classified by the receiving catalog`() {
        val foreign = testApManager(Cancellation(), declarations = declarations)
        val foreignGroup = foreign.marks.group(foreign.marks.groupOf(foreign.marks.full("V")))
        val receivingFull = m.marks.full("U")
        assertEquals(receivingFull.id, foreignGroup.id)
        val input = foreign.taintTree(x, Layer.DEMAND,
            foreign.taintAlg.leafNode(foreign.taintLeaves(set(foreignGroup), MarkSet.EMPTY)))
        val projected = assertIs<TaintTree>(m.runtimeFact(input, grouped))
        assertEquals(setOf(scalarAtom(m.marks, TaintMark(foreignGroup.id))),
            projected.root.leaf.exact.ids.map(::TaintMark).toSet())
        assertEquals(setOf(g), projected.root.leaf.exact.ids.map(::TaintMark).toSet())
        assertSame(projected, m.runtimeFact(projected, grouped))
    }

    @Test fun `equal public header values use receiving canonical exclusion objects`() {
        val canonicalE = m.intern(ExclusionSet.Concrete(intArrayOf(f)))
        val publicE = ExclusionSet.Concrete(intArrayOf(f))
        val canonicalMx = m.intern(MarkExclusions.of(m.marks, listOf(g)))
        val publicMx = MarkExclusions.of(m.marks, listOf(g))
        val input = FlowTree(x, Layer.NORMAL, publicE, publicMx, FlowAlgebra.LEAF_NODE, Tail.STAR)
        val result = assertIs<FlowTree>(m.runtimeFact(input, grouped))
        assertSame(canonicalE, result.exclusion)
        assertSame(canonicalMx, result.markExclusion)
        val taint = TaintTree(x, Layer.DEMAND,
            m.taintAlg.leafNode(m.taintLeaves(MarkSet.EMPTY, set(g))), publicE, Tail.ANY_TAINT)
        val taintResult = assertIs<TaintTree>(m.runtimeFact(taint, grouped))
        assertSame(canonicalE, taintResult.exclusion)
        assertSame(taintResult, m.runtimeFact(taintResult, grouped))
    }

    @Test fun `GROUP cover outputs are projection fixed points with scalar mark and geometry equality`() {
        val choices = listOf(MarkSet.EMPTY, set(t, h), set(u, v, TaintMark.ZERO), set(g, h))
        for (exact in choices) for (any in choices) {
            if (exact.isEmpty && any.isEmpty) continue
            for (tail in listOf(Tail.ANY, Tail.ANY_TAINT)) {
                val exclusion = if (tail == Tail.ANY_TAINT && !any.isEmpty)
                    ExclusionSet.Concrete(intArrayOf(f)) else ExclusionSet.Empty
                val input = m.taintTree(x, Layer.NORMAL,
                    m.taintAlg.prepend(m, m.path(listOf(f)), m.taintAlg.leafNode(m.taintLeaves(exact, any))),
                    exclusion, tail)
                val projection = m.runtimeFact(input, grouped)
                val covered = buildList { ApOps(m).run1Cover(projection) { add(it) } }
                assertTrue(covered.isNotEmpty())
                covered.forEach { value ->
                    assertSame(value, m.runtimeFact(value, grouped), "Cover must retain projection fixed points")
                    assertEquals(Layer.DEMAND, value.layer)
                    assertEquals(ExclusionSet.Empty, assertIs<TaintTree>(value).exclusion)
                }
                val observed = covered.flatMap { ApOps(m).leaves(it).toList() }.toSet()
                val expected = buildSet {
                    exact.ids.forEach { id -> add(Pattern(PathFact(x, listOf(f), Tail.EXACT,
                        MarkSlot.Concrete(scalarAtom(m.marks, TaintMark(id)))), ExclusionSet.Empty)) }
                    any.ids.forEach { id -> add(Pattern(PathFact(x, listOf(f), tail,
                        MarkSlot.Concrete(scalarAtom(m.marks, TaintMark(id)))), ExclusionSet.Empty)) }
                }
                assertEquals(expected, observed)
            }
        }
    }

    @Test fun `the immediate applyEdge consumer equals generic collection for real primitive and GEN callbacks`() {
        for (index in 1..2) MethodAnalysisFixtures().use { fixture ->
            val core = fixture.core(index)
            val from = AccessPathBase.Argument(0)
            val to = AccessPathBase.Return
            val classId = fixture.manager.accessors.index(ClassStaticAccessor("C"))
            val site = ResumeSite.StatementInput(OperationId(FormKey(FormOwner(), core.key.method,
                core.key, fixture.inst(0))))
            val origins = listOf(fixture.manager.zero,
                fixture.manager.initial(fixture.pattern(from, tail = Tail.STAR, mark = MarkSlot.STAR)),
                fixture.manager.initial(fixture.pattern(STATIC, listOf(classId, fixture.field("f")),
                    Tail.STAR, MarkSlot.STAR)))
            val inputFlow = fixture.fact(from, tail = Tail.STAR, mark = MarkSlot.STAR,
                exclusion = ExclusionSet.Concrete(intArrayOf(fixture.field("excluded"))))
            val inputTaint = fixture.fact(from, tail = Tail.ANY_TAINT,
                exclusion = ExclusionSet.Concrete(intArrayOf(fixture.field("excluded"))))
            val samples = listOf(
                inputFlow to starEdge(from, emptyList(), to, emptyList()),
                inputTaint to starEdge(from, emptyList(), to, emptyList()),
                Reach.NORMAL to PathEdge(ZERO_FACT, fixture.pattern(to).fact, ExclusionSet.Empty),
            )
            for (origin in origins) for ((input, edge) in samples) {
                val at = OperationInput(site, origin, input)
                val before = arrayListOf<Pair<PremiseKey, Facts>>()
                val after = arrayListOf<Pair<PremiseKey, Facts>>()
                fixture.ops.applyEdge(input, origin, edge, false, core.mode,
                    core.collect(at) { p, result -> before += p to result })
                fixture.ops.applyEdge(input, origin, edge, false, core.mode,
                    core.collectAfterRun1Policy(at) { p, result -> after += p to result })
                assertEquals(before, after)
                assertTrue(after.isNotEmpty())
                if (index == 1 && origin.base == STATIC)
                    assertTrue(after.all { it.second.layer == Layer.DEMAND })
            }
        }
    }

    @Test fun `the immediate consumer preserves applyEdge requests and generic raw inputs still project`() {
        MethodAnalysisFixtures().use { fixture ->
            val core = fixture.core()
            val origin = fixture.manager.initial(fixture.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            val input = fixture.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR)
            val site = ResumeSite.StatementInput(OperationId(FormKey(FormOwner(), core.key.method,
                core.key, fixture.inst(0))))
            val at = OperationInput(site, origin, input)
            val edge = PathEdge(fixture.pattern(x).fact, fixture.pattern(AccessPathBase.Return).fact,
                ExclusionSet.Empty)
            fixture.ops.applyEdge(input, origin, edge, false, core.mode, core.collect(at) { _, _ -> })
            val before = fixture.requests.toList()
            fixture.requests.clear()
            fixture.ops.applyEdge(input, origin, edge, false, core.mode,
                core.collectAfterRun1Policy(at) { _, _ -> })
            assertTrue(before.isNotEmpty())
            assertEquals(before, fixture.requests)
            val raw = fixture.fact(AccessPathBase.Return, layer = Layer.DEMAND)
            val projected = arrayListOf<Facts>()
            core.collect(at) { _, result -> projected += result }.result(raw)
            val expectedGroup = scalarAtom(fixture.manager.marks, fixture.mark("T"))
            assertEquals(setOf(expectedGroup), projected.flatMap { fixture.ops.leaves(it).toList() }
                .map { (it.fact.mark as MarkSlot.Concrete).mark }.toSet())
        }
    }
}
