package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertSame

class ManagerOwnershipTest {
    @Test
    fun `foreign suffix cannot create a second key for the same path`() {
        val first = ApManager(Cancellation())
        val second = ApManager(Cancellation())
        val a = FieldAccessor("Box", "a", "Box")
        val b = FieldAccessor("Box", "b", "Box")
        val aIdx = first.accessors.index(a)
        val bIdx = first.accessors.index(b)
        second.accessors.index(a)
        val foreignSuffix = second.path(intArrayOf(second.accessors.index(b)))
        assertFails { first.path(aIdx, foreignSuffix) }
        assertSame(first.path(intArrayOf(aIdx, bIdx)), first.path(aIdx, first.path(intArrayOf(bIdx))))
    }

    @Test
    fun `initial rejects a foreign path instead of interning a second numeric key`() {
        val first = ApManager(Cancellation())
        val second = ApManager(Cancellation())
        val field = FieldAccessor("Box", "a", "Box")
        first.accessors.index(field)
        val foreign = second.path(intArrayOf(second.accessors.index(field)))
        val mark = MarkSlot.Concrete(first.marks.mark("T"))
        assertFails { first.initial(AccessPathBase.This, foreign, Tail.EXACT, ExclusionSet.Empty, mark) }
    }

    @Test
    fun `union validates both managers even on the identity shortcut`() {
        val first = ApManager(Cancellation())
        val second = ApManager(Cancellation())
        assertFails { first.union(second.zero, second.zero) }
        assertFails { first.union(first.zero, second.zero) }
        assertSame(first.zero, first.union(first.zero, first.zero))
    }
}
