package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testApManager
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

/** The oracle uses immutable keys and scalar set denotation. It does not call
 * runtimeAtom, runtimeMarks, projected or withCatalog to compute expectations. */
class GroupNormalizationReferenceTest {
    private val declarations = testDeclarations(
        TestMark("T", MarkGroupKey.Rule("same")),
        TestMark("U", MarkGroupKey.Rule("same")),
        TestMark("late", MarkGroupKey.Rule("same")),
        TestMark("V", MarkGroupKey.Rule("other")),
    )
    private val m = testApManager(Cancellation(), declarations = declarations)
    private val table = m.marks
    private val t = table.full("T")
    private val u = table.full("U")
    private val v = table.full("V")
    private val g = table.group(table.groupOf(t))
    private val h = table.group(table.groupOf(v))
    private val atoms = listOf(TaintMark.ZERO, t, u, v, g, h)
    private val grouped = ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.GROUP)
    private val full = ApMode(false, Direction.BACKWARD, 1)

    private fun referenceAtom(table: MarkTable, mark: TaintMark): TaintMark = when (val key = table.key(mark)) {
        MarkAtomKey.ControlZero -> TaintMark.ZERO
        is MarkAtomKey.Full -> table.group(key.mark.group)
        is MarkAtomKey.Group -> table.group(key.id)
    }

    private fun referenceNormalized(table: MarkTable, input: Iterable<TaintMark>): Set<TaintMark> {
        val unique = input.toSet()
        val groups = unique.mapNotNull { (table.key(it) as? MarkAtomKey.Group)?.id }.toSet()
        return unique.filterTo(linkedSetOf()) {
            val key = table.key(it)
            key !is MarkAtomKey.Full || key.mark.group !in groups
        }
    }

    private fun referenceContains(table: MarkTable, selectors: Set<TaintMark>, mark: TaintMark): Boolean {
        if (mark in selectors) return true
        val fullKey = table.key(mark) as? MarkAtomKey.Full ?: return false
        return selectors.any { (table.key(it) as? MarkAtomKey.Group)?.id == fullKey.mark.group }
    }

    private fun subsets(): Sequence<List<TaintMark>> = (0 until (1 shl atoms.size)).asSequence().map { mask ->
        atoms.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
    }

    @Test fun `all typed atom and public set projections match scalar reference`() {
        for (atom in atoms) {
            assertEquals(referenceAtom(table, atom), table.runtimeAtom(atom, MarkMode.GROUP))
            if (table.key(atom) is MarkAtomKey.Group) {
                assertFailsWith<IllegalArgumentException> { table.runtimeAtom(atom, MarkMode.FULL) }
            } else {
                assertEquals(atom, table.runtimeAtom(atom, MarkMode.FULL))
            }
        }
        for (members in subsets()) {
            val input = MarkSet(members.asReversed().flatMap { listOf(it.id, it.id) }.toIntArray())
            val expected = members.map { referenceAtom(table, it) }.toSet()
            val result = m.runtimeMarks(input, grouped)
            assertEquals(expected, result.ids.map(::TaintMark).toSet())
            assertSame(result, m.runtimeMarks(MarkSet(result.ids), grouped), "Public unchanged sets still intern")
            assertSame(input, m.runtimeMarks(input, full), "FULL set projection retains its existing identity")
            assertEquals(result, m.runtimeMarks(result, grouped))
        }
        val lateFull = table.full("late")
        assertTrue(lateFull.id > g.id)
        val groupBeforeFull = MarkSet(intArrayOf(g.id, lateFull.id))
        assertEquals(listOf(g.id, lateFull.id), groupBeforeFull.ids.toList())
        assertEquals(setOf(referenceAtom(table, g), referenceAtom(table, lateFull)),
            m.runtimeMarks(groupBeforeFull, grouped).ids.map(::TaintMark).toSet())
    }

    @Test fun `private exclusion factories and projection match symbolic scalar denotation`() {
        for (members in subsets()) {
            val selectors = referenceNormalized(table, members)
            val attached = MarkExclusions.of(table, members)
            assertEquals(selectors, attached.ids.map(::TaintMark).toSet())
            atoms.forEach { assertEquals(referenceContains(table, selectors, it), it in attached) }
            val expected = referenceNormalized(table, selectors.map { referenceAtom(table, it) })
            val projected = attached.projected(table, MarkMode.GROUP)
            assertEquals(expected, projected.ids.map(::TaintMark).toSet())
            atoms.forEach { assertEquals(referenceContains(table, expected, it), it in projected) }
            assertEquals(attached, attached.withCatalog(table))
            assertEquals(attached, attached.projected(table, MarkMode.FULL))
            val detached = MarkExclusions.fromMarks(MarkSet(members.map { it.id }.toIntArray()))
            assertEquals(expected, detached.projected(table, MarkMode.GROUP).ids.map(::TaintMark).toSet())
            assertEquals(selectors, detached.withCatalog(table).ids.map(::TaintMark).toSet())
        }
    }

    @Test fun `foreign GROUP projection keeps numeric remap while FULL attachment rejects foreign catalog`() {
        val foreign = MarkTable(declarations)
        val foreignV = foreign.full("V") // atom 1, while atom 1 is T in the target table
        val foreignH = foreign.group(foreign.groupOf(foreignV)) // atom 2, target U
        val attached = MarkExclusions.of(foreign, listOf(foreignV, foreignH))
        val expected = referenceNormalized(table, attached.ids.map { referenceAtom(table, TaintMark(it)) })
        assertEquals(expected, attached.projected(table, MarkMode.GROUP).ids.map(::TaintMark).toSet())
        assertFailsWith<IllegalArgumentException> { attached.withCatalog(table) }
        assertFailsWith<IllegalArgumentException> { attached.projected(table, MarkMode.FULL) }
        // Empty exclusions have no catalog even if a caller supplied one.
        assertSame(MarkExclusions.EMPTY, MarkExclusions.of(foreign, emptyList()).withCatalog(table))
    }

    @Test fun `group exclusion covers a later first use but finite FULL set never covers a group`() {
        val symbolic = MarkExclusions.of(table, g).projected(table, MarkMode.GROUP)
        val finite = MarkExclusions.of(table, listOf(t, u))
        val late = table.full("late")
        assertTrue(late in symbolic)
        assertFalse(late in finite)
        assertFalse(MarkExclusions.of(table, g).isSubsetOf(finite))
        assertTrue(finite.isSubsetOf(symbolic))
        assertEquals(setOf(t, u), finite.ids.map(::TaintMark).toSet())
    }

    @Test fun `runtime fact still maps every TAINT leaf and forces existing headers`() {
        val f = m.accessors.index(FieldAccessor("Box", "f", "Box"))
        val exclusion = ExclusionSet.Concrete(intArrayOf(f))
        val root = m.taintAlg.leafNode(m.taintLeaves(MarkSet(intArrayOf(t.id, h.id)),
            MarkSet(intArrayOf(u.id, v.id))))
        val input = m.taintTree(AccessPathBase.Argument(0), Layer.NORMAL, root, exclusion, Tail.ANY_TAINT)
        val projected = assertIs<TaintTree>(m.runtimeFact(input, grouped))
        assertEquals(Layer.DEMAND, projected.layer)
        assertEquals(input.base, projected.base)
        assertEquals(input.exclusion, projected.exclusion)
        assertEquals(input.anyTail, projected.anyTail)
        val leaves = ApOps(m).leaves(projected).toList()
        for (tail in listOf(Tail.EXACT, Tail.ANY_TAINT)) {
            assertEquals(setOf(g, h), leaves.filter { it.fact.tail == tail }
                .map { (it.fact.mark as MarkSlot.Concrete).mark }.toSet())
        }
        assertSame(input, m.runtimeFact(input, full))
        val flow = m.flowTree(STATIC, Layer.NORMAL, exclusion,
            MarkExclusions.of(table, listOf(t, h)), FlowAlgebra.LEAF_NODE, Tail.STAR)
        val projectedFlow = assertIs<FlowTree>(m.runtimeFact(flow, grouped))
        assertEquals(Layer.DEMAND, projectedFlow.layer)
        assertEquals(flow.tail, projectedFlow.tail)
        assertEquals(flow.exclusion, projectedFlow.exclusion)
        assertSame(flow.root, projectedFlow.root)
        assertEquals(setOf(g, h), projectedFlow.markExclusion.ids.map(::TaintMark).toSet())
    }
}
