package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testApManager
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import org.opentaint.dataflow.util.Cancellation
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ImmutableGroupFastPathsTest {
    private val m = testApManager(Cancellation(), declarations = testDeclarations(
        TestMark("T", MarkGroupKey.Rule("one")), TestMark("U", MarkGroupKey.Rule("one")),
        TestMark("V", MarkGroupKey.Rule("two")),
    ))
    private val t = m.marks.full("T")
    private val g = m.marks.group(m.marks.groupOf(t))
    private val u = m.marks.full("U")
    private val v = m.marks.full("V")
    private val h = m.marks.group(m.marks.groupOf(v))
    private val mode = ApMode(true, Direction.FORWARD, 1)

    @Test fun `attached normalized exclusions retain their object on repeated catalog attachment`() {
        // Both FULL and GROUP exclusions use the same private factory invariant.
        for (atoms in listOf(listOf(t, u), listOf(g), listOf(g, t, h, TaintMark.ZERO))) {
            val value = MarkExclusions.of(m.marks, atoms)
            assertSame(value, value.withCatalog(m.marks))
            assertSame(value, value.projected(m.marks, MarkMode.FULL))
        }
    }

    @Test fun `GROUP and ZERO exclusions retain their object after GROUP projection`() {
        for (atoms in listOf(listOf(g), listOf(g, h), listOf(g, h, TaintMark.ZERO), listOf(TaintMark.ZERO))) {
            val value = MarkExclusions.of(m.marks, atoms)
            assertSame(value, value.projected(m.marks, MarkMode.GROUP))
            assertSame(value, value.projected(m.marks, MarkMode.GROUP).projected(m.marks, MarkMode.GROUP))
        }
    }

    @Test fun `a FULL atom after a GROUP atom still projects and deduplicates`() {
        val groups = m.intern(MarkSet(intArrayOf(g.id, h.id, TaintMark.ZERO.id)))
        val mixed = MarkSet(intArrayOf(g.id, u.id, v.id, TaintMark.ZERO.id))
        assertTrue(mixed.ids.indexOf(g.id) < mixed.ids.indexOf(u.id), "The sorted input visits GROUP before FULL")
        assertSame(groups, m.runtimeMarks(mixed, mode))
        // A new public set with equal atoms must also join the manager's interner.
        assertSame(groups, m.runtimeMarks(MarkSet(groups.ids), mode))
        assertSame(MarkSet.EMPTY, m.runtimeMarks(MarkSet(IntArray(0)), mode))
        val mixedExclusions = MarkExclusions.of(m.marks, listOf(g, v, TaintMark.ZERO))
        val projected = mixedExclusions.projected(m.marks, MarkMode.GROUP)
        assertEquals(setOf(g, h, TaintMark.ZERO), projected.ids.map(::TaintMark).toSet())
        assertNotSame(mixedExclusions, projected)
        assertSame(projected, projected.projected(m.marks, MarkMode.GROUP))
    }

    @Test fun `immutable GROUP identities retain canonical set handles under concurrency`() {
        val canonical = m.intern(MarkSet(intArrayOf(g.id, h.id)))
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 16).map {
                pool.submit {
                    repeat(100) {
                        assertEquals(g, m.marks.runtimeAtom(g, MarkMode.GROUP))
                        assertSame(canonical, m.runtimeMarks(MarkSet(intArrayOf(h.id, g.id)), mode))
                    }
                }
            }
            tasks.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
