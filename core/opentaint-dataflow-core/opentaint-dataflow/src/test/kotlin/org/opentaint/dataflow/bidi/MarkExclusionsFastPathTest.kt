package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class MarkExclusionsFastPathTest {
    /** Reference atoms have explicit test-owned kinds/owners, independent of production metadata. */
    private data class Atom(val mark: TaintMark, val owner: Int? = null, val group: Boolean = false)

    private class Inventory {
        val table = MarkTable(testDeclarations(
            TestMark("T", MarkGroupKey.Rule("g")),
            TestMark("U", MarkGroupKey.Rule("g")),
            TestMark("V", MarkGroupKey.Rule("h")),
            TestMark("late-T", MarkGroupKey.Rule("g")),
            TestMark("late-V", MarkGroupKey.Rule("h")),
        ))
        val zero = Atom(TaintMark.ZERO)
        val t = Atom(table.full("T"), 0)
        val u = Atom(table.full("U"), 0)
        val v = Atom(table.full("V"), 1)
        val g = Atom(table.group(table.groupOf(t.mark)), 0, group = true)
        val h = Atom(table.group(table.groupOf(v.mark)), 1, group = true)
        val atoms = listOf(zero, t, u, v, g, h)
        val subsets = (0 until (1 shl atoms.size)).map { mask ->
            atoms.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
        }
        fun groupOf(atom: Atom): Atom = when (atom.owner) {
            0 -> g
            1 -> h
            else -> zero
        }
        fun header(atoms: Collection<Atom>): MarkExclusions =
            MarkExclusions.fromMarks(MarkSet(atoms.map { it.mark.id }.toIntArray()), table)
    }

    private fun covers(selector: Atom, actual: Atom): Boolean = selector == actual ||
        (selector.group && actual.owner != null && !actual.group && selector.owner == actual.owner)

    private fun excludes(atoms: Collection<Atom>, actual: Atom): Boolean = atoms.any { covers(it, actual) }

    /** Minimal generators of the typed exclusion relation, not the optimized implementation. */
    private fun canonical(atoms: Collection<Atom>): List<Int> = atoms.distinct().filter { atom ->
        atoms.none { other -> other != atom && covers(other, atom) }
    }.map { it.mark.id }.sorted()

    private fun intersection(left: Collection<Atom>, right: Collection<Atom>): Set<Atom> = buildSet {
        for (a in left) for (b in right) {
            if (covers(a, b)) add(b)
            if (covers(b, a)) add(a)
        }
    }

    @Test fun `raw duplicate additions and same-catalog unions retain the old header`() {
        val f = Inventory()
        for (subset in f.subsets) {
            val header = f.header(subset)
            for (id in header.ids) {
                assertSame(header, header + TaintMark(id))
                assertSame(header, header + MarkSet(intArrayOf(id)))
                assertSame(header, header.union(MarkExclusions.of(f.table, TaintMark(id))))
            }
            assertSame(header, header.union(MarkExclusions.EMPTY))
            assertSame(header, header.union(header))
        }
        val raw = MarkExclusions.fromMarks(MarkSet(intArrayOf(f.g.mark.id, f.t.mark.id)))
        assertSame(raw, raw + f.g.mark)
        assertSame(raw, raw + f.t.mark)
        assertEquals(listOf(f.t.mark.id, f.g.mark.id).sorted(), raw.ids.toList())
        assertEquals(listOf(f.g.mark.id), raw.withCatalog(f.table).ids.toList())
    }

    @Test fun `all typed subsets normalize and project against the independent reference`() {
        val f = Inventory()
        for (subset in f.subsets) {
            val header = f.header(subset)
            assertEquals(canonical(subset), header.ids.toList(), "normalize $subset")
            for (atom in f.atoms) assertEquals(excludes(subset, atom), atom.mark in header)
            assertSame(header, header.projected(f.table, MarkMode.FULL))
            val projected = header.projected(f.table, MarkMode.GROUP)
            assertEquals(canonical(subset.map(f::groupOf)), projected.ids.toList(), "project $subset")
            assertSame(projected, projected.projected(f.table, MarkMode.GROUP))
            val hasRetainedFull = subset.any { atom ->
                !atom.group && atom.owner != null && subset.none { it != atom && covers(it, atom) }
            }
            if (hasRetainedFull) assertNotSame(header, projected) else assertSame(header, projected)
        }
    }

    @Test fun `union intersection subset and filtering preserve typed set semantics`() {
        val f = Inventory()
        for (left in f.subsets) for (right in f.subsets) {
            val a = f.header(left)
            val b = f.header(right)
            val union = a.union(b)
            val common = a.intersect(b)
            assertEquals(canonical(left + right), union.ids.toList())
            assertEquals(canonical(intersection(left, right)), common.ids.toList())
            assertEquals(left.all { excludes(right, it) }, a.isSubsetOf(b))
            assertEquals(canonical((left + right).map(f::groupOf)),
                union.projected(f.table, MarkMode.GROUP).ids.toList())
            assertEquals(canonical(intersection(left, right).map(f::groupOf)),
                common.projected(f.table, MarkMode.GROUP).ids.toList())
            val marks = MarkSet(right.map { it.mark.id }.toIntArray())
            assertEquals(right.filterNot { excludes(left, it) }.map { it.mark.id }.sorted(),
                a.filterMarks(marks).ids.toList())
        }
        // A finite collection of currently known FULLs does not cover the GROUP selector.
        assertFalse(f.header(listOf(f.g)).isSubsetOf(f.header(listOf(f.t, f.u))))
    }

    @Test fun `unchanged raw union must still acquire a catalog and remove shadowed full atoms`() {
        val f = Inventory()
        val raw = MarkExclusions.fromMarks(MarkSet(intArrayOf(f.t.mark.id, f.g.mark.id)))
        val result = raw.union(f.header(listOf(f.g)))
        assertNotSame(raw, result)
        assertEquals(listOf(f.g.mark.id), result.ids.toList())
        assertSame(result, result.projected(f.table, MarkMode.GROUP))
        val freeGroup = MarkExclusions.fromMarks(MarkSet(intArrayOf(f.g.mark.id)))
        val attached = freeGroup.union(f.header(listOf(f.g)))
        assertNotSame(freeGroup, attached)
        assertSame(attached, attached.withCatalog(f.table))
        assertSame(attached, attached.projected(f.table, MarkMode.GROUP))
    }

    @Test fun `different-catalog GROUP fallback reinterprets IDs while other APIs reject conflicts`() {
        val f = Inventory()
        val other = MarkTable(testDeclarations(*(0 until 4).map { TestMark("foreign-$it") }.toTypedArray()))
        val foreignFull = (0 until 4).map { other.full("foreign-$it") }.last()
        // The same integer is GROUP in f and FULL in other.
        assertEquals(f.g.mark.id, foreignFull.id)
        val foreignGroup = other.group(other.groupOf(foreignFull))
        val bound = f.header(listOf(f.g))
        val free = MarkExclusions.fromMarks(MarkSet(bound.ids))
        val otherBound = MarkExclusions.of(other, foreignFull)
        assertEquals(bound, otherBound)
        assertEquals(bound.hashCode(), otherBound.hashCode())
        for (header in listOf(bound, free)) {
            val projected = header.projected(other, MarkMode.GROUP)
            assertEquals(listOf(foreignGroup.id), projected.ids.toList())
            assertNotSame(header, projected)
            assertSame(projected, projected.projected(other, MarkMode.GROUP))
        }
        assertFailsWith<IllegalArgumentException> { bound.withCatalog(other) }
        assertFailsWith<IllegalArgumentException> { bound.projected(other, MarkMode.FULL) }
        assertFailsWith<IllegalArgumentException> { bound.union(otherBound) }
        assertFailsWith<IllegalArgumentException> { bound.intersect(otherBound) }
        assertFailsWith<IllegalArgumentException> { bound.isSubsetOf(otherBound) }
        val attachedHere = free.projected(f.table, MarkMode.GROUP)
        assertNotSame(free, attachedHere)
        assertEquals(bound, attachedHere)
        assertSame(attachedHere, attachedHere.projected(f.table, MarkMode.GROUP))
    }

    @Test fun `ZERO and inherited GROUP selectors retain their distinct FULL contracts`() {
        val f = Inventory()
        for (mode in MarkMode.entries) {
            assertSame(MarkExclusions.EMPTY, MarkExclusions.EMPTY.projected(f.table, mode))
        }
        val zero = MarkExclusions.of(f.table, TaintMark.ZERO)
        assertSame(zero, zero.projected(f.table, MarkMode.GROUP))
        assertSame(zero, zero.projected(f.table, MarkMode.FULL))
        assertTrue(TaintMark.ZERO in zero)
        assertFalse(f.t.mark in zero)
        val selected = MarkExclusions.of(f.table, listOf(f.g.mark, TaintMark.ZERO))
        assertSame(selected, selected.projected(f.table, MarkMode.FULL))
        assertSame(selected, selected.projected(f.table, MarkMode.GROUP))
        assertTrue(f.t.mark in selected)
        assertTrue(f.u.mark in selected)
        assertFalse(f.v.mark in selected)
        assertFailsWith<IllegalArgumentException> { f.table.runtimeAtom(f.g.mark, MarkMode.FULL) }
    }

    @Test fun `new headers classify the retained result of every changing operation`() {
        val f = Inventory()
        val group = f.header(listOf(f.g))
        val full = f.header(listOf(f.t))
        val intersection = group.intersect(full)
        assertEquals(listOf(f.t.mark.id), intersection.ids.toList())
        assertNotSame(intersection, intersection.projected(f.table, MarkMode.GROUP))
        val shadowed = full + f.g.mark
        assertEquals(listOf(f.g.mark.id), shadowed.ids.toList())
        assertSame(shadowed, shadowed.projected(f.table, MarkMode.GROUP))
        val mixed = group + f.v.mark
        assertEquals(listOf(f.g.mark.id, f.v.mark.id).sorted(), mixed.ids.toList())
        val projected = mixed.projected(f.table, MarkMode.GROUP)
        assertNotSame(mixed, projected)
        assertEquals(listOf(f.g.mark.id, f.h.mark.id).sorted(), projected.ids.toList())
    }

    @Test fun `array ownership catalog growth and manager interning preserve the header certificate`() {
        val f = Inventory()
        val source = intArrayOf(f.g.mark.id)
        val raw = MarkExclusions.fromMarks(MarkSet(source))
        source[0] = f.v.mark.id
        raw.ids[0] = f.v.mark.id
        val manager = ApManager(Cancellation(), marks = f.table)
        val bound = manager.intern(raw)
        bound.ids[0] = f.v.mark.id
        assertEquals(listOf(f.g.mark.id), bound.ids.toList())
        assertEquals(raw, bound)
        assertEquals(raw.hashCode(), bound.hashCode())
        assertSame(bound, manager.intern(f.header(listOf(f.g))))
        assertSame(bound, manager.intern(raw))
        assertSame(bound, bound.projected(f.table, MarkMode.GROUP))
        val lateT = f.table.full("late-T")
        val lateV = f.table.full("late-V")
        assertTrue(lateT in bound)
        assertFalse(lateV in bound)
        assertSame(bound, bound.projected(f.table, MarkMode.GROUP))
    }
}
