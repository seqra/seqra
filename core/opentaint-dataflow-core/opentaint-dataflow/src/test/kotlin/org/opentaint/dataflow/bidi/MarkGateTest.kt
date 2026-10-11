package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.MarkGate
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertSame

class MarkGateTest {
    private val manager = ApManager(Cancellation())
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val fresh = manager.marks.mark("fresh")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun excluded(vararg ids: Int) = MarkSlot.Star(marks(*ids))

    @Test
    fun `concrete mark rows use the premise gate before target replacement or exclusion`() {
        data class Row(val premise: MarkSlot, val target: MarkSlot, val input: MarkSet, val output: MarkSet?)
        val rows = listOf(
            Row(MarkSlot.STAR, MarkSlot.STAR, MarkSet.EMPTY, null),
            Row(MarkSlot.STAR, MarkSlot.STAR, marks(t.id, u.id), marks(t.id, u.id)),
            Row(MarkSlot.STAR, excluded(t.id), marks(t.id, u.id), marks(u.id)),
            Row(MarkSlot.STAR, excluded(t.id, u.id), marks(t.id, u.id), null),
            Row(MarkSlot.STAR, excluded(t.id, u.id), marks(t.id, u.id, fresh.id), marks(fresh.id)),
            Row(mt, MarkSlot.STAR, marks(u.id, fresh.id), null),
            Row(mt, MarkSlot.STAR, marks(t.id, u.id, fresh.id), marks(t.id)),
            Row(mt, excluded(u.id), marks(t.id, u.id), marks(t.id)),
            Row(mt, excluded(t.id), marks(t.id, u.id), null),
            Row(mt, mu, MarkSet.EMPTY, null),
            Row(mt, mu, marks(u.id), null),
            Row(mt, mu, marks(t.id, u.id, fresh.id), marks(u.id)),
            Row(mu, mt, marks(t.id, u.id), marks(t.id)),
            Row(MarkSlot.Concrete(TaintMark.ZERO), mt, marks(TaintMark.ZERO.id), marks(t.id)),
        )
        for (row in rows) {
            assertEquals(row.output, MarkGate(manager, row.premise, row.target).taintMarks(row.input), row.toString())
        }
    }

    @Test
    fun `abstract flow rows union input and target mark exclusions`() {
        data class Row(val target: MarkSlot, val input: MarkSet, val output: MarkSet)
        val rows = listOf(
            Row(MarkSlot.STAR, MarkSet.EMPTY, MarkSet.EMPTY),
            Row(MarkSlot.STAR, marks(t.id), marks(t.id)),
            Row(excluded(t.id), marks(u.id), marks(t.id, u.id)),
            Row(excluded(t.id, u.id), marks(t.id, fresh.id), marks(t.id, u.id, fresh.id)),
        )
        for (row in rows) {
            val gate = MarkGate(manager, MarkSlot.STAR, row.target)
            assertEquals(row.output, gate.flowMarks(row.input), row.toString())
            assertNull(gate.flowRequest(row.input))
        }
    }

    @Test
    fun `a concrete flow premise requests its own unexcluded mark and produces no flow marks`() {
        for (target in listOf(MarkSlot.STAR, excluded(u.id), mu)) {
            val gate = MarkGate(manager, mt, target)
            for ((input, request) in listOf(
                MarkSet.EMPTY to t, marks(u.id) to t, marks(t.id) to null, marks(t.id, u.id) to null,
            )) {
                assertNull(gate.flowMarks(input))
                assertEquals(request, gate.flowRequest(input))
            }
        }
    }

    @Test
    fun `S7 rejects excluded premises and abstract to concrete targets at gate construction`() {
        assertFails { MarkGate(manager, excluded(t.id), MarkSlot.STAR) }
        assertFails { MarkGate(manager, excluded(t.id), mu) }
        assertFails { MarkGate(manager, MarkSlot.STAR, mt) }
    }

    @Test
    fun `nonempty gate outputs are canonical manager mark sets`() {
        val input = marks(t.id, u.id, fresh.id)
        val concrete = MarkGate(manager, mt, mu)
        assertSame(manager.markSetOf(u), concrete.taintMarks(input))
        val abstract = MarkGate(manager, MarkSlot.STAR, excluded(t.id))
        assertSame(manager.intern(marks(u.id, fresh.id)), abstract.taintMarks(input))
        assertSame(manager.intern(marks(t.id, u.id, fresh.id)), abstract.flowMarks(input))
        assertEquals(marks(t.id, u.id, fresh.id), input)
    }
}
