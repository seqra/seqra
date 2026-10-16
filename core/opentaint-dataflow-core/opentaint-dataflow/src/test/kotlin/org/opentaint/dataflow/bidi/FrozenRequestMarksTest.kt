package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class FrozenRequestMarksTest {
    @Test fun `union consumes duplicate source cells across budget one steps in signed order`() {
        val a = RequestMarkSet.fromIds(intArrayOf(Int.MIN_VALUE, -1, 0, 9, Int.MAX_VALUE))
        val b = RequestMarkSet.fromIds(intArrayOf(Int.MIN_VALUE, 0, 1, 9, Int.MAX_VALUE))
        var checks = 0
        val view = distinctUnionView(a,b) { checks++ }
        val cursor = view.cursor()
        val actual = ArrayList<Int>()
        assertEquals(1,cursor.step(1) { actual += it.id })
        assertEquals(listOf(Int.MIN_VALUE),actual)
        assertEquals(1,cursor.step(1) { actual += it.id }) // consumes only b's duplicate
        assertEquals(listOf(Int.MIN_VALUE),actual)
        var used = 2
        while (!cursor.isComplete) {
            assertEquals(1,cursor.step(1) { actual += it.id })
            used++
        }
        assertEquals(listOf(Int.MIN_VALUE,-1,0,1,9,Int.MAX_VALUE),actual)
        assertEquals(a.size+b.size,used)
        assertEquals(used,checks)
        assertEquals(0,cursor.step(1) { fail("Complete cursor emits nothing") })
        for (id in actual) assertTrue(view.containsId(id))
        assertFalse(view.containsId(8))
        assertFailsWith<IllegalArgumentException> { cursor.step(0) {} }
    }

    @Test fun `empty and identical segments preserve existing payload identity`() {
        val a = RequestMarkSet.fromIds(intArrayOf(0,1))
        assertSame(a,distinctUnionView(a,RequestMarkSet.EMPTY) {})
        assertSame(a,distinctUnionView(RequestMarkSet.EMPTY,a) {})
        assertSame(a,distinctUnionView(a,a) {})
        assertSame(RequestMarkSet.EMPTY,distinctUnionView(RequestMarkSet.EMPTY,RequestMarkSet.EMPTY) {})
    }

    @Test fun `cancelled duplicate-only cell stops before consuming and can resume unchanged`() {
        val c = Cancellation()
        val view = distinctUnionView(RequestMarkSet.fromIds(intArrayOf(0,1)),
            RequestMarkSet.fromIds(intArrayOf(0,2)),c::checkpoint)
        val cursor = view.cursor(); val actual = ArrayList<Int>()
        cursor.step(1) { actual += it.id }
        c.cancel()
        assertFailsWith<Cancellation.Cancelled> { cursor.step(1) { actual += it.id } }
        assertEquals(listOf(0),actual)
        c.activate()
        assertEquals(1,cursor.step(1) { fail("The unconsumed cell is duplicate zero") })
        while (!cursor.isComplete) cursor.step(1) { actual += it.id }
        assertEquals(listOf(0,1,2),actual)
    }
}
