package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.bidi.store.RequestMarkSetBuilder
import kotlin.test.*

class RequestMarkSetTest {
    @Test
    fun `owned canonical request marks sort signed ids and detach caller arrays`(): Unit {
        val ids = intArrayOf(7, 0, -1, Int.MAX_VALUE, Int.MIN_VALUE, 7, 0)
        val set = RequestMarkSet.fromIds(ids)
        ids.fill(99)
        assertEquals(listOf(Int.MIN_VALUE, -1, 0, 7, Int.MAX_VALUE), List(set.size, set::idAt))
        assertEquals(set, RequestMarkSet.fromIds(intArrayOf(Int.MAX_VALUE, 7, 0, -1, Int.MIN_VALUE)))
        assertTrue(TaintMark(0) in set)
        assertFalse(set.containsId(99))
        assertSame(set, set - MarkSet.EMPTY)
        assertSame(set, set - MarkSet(intArrayOf(98)))
        assertEquals(RequestMarkSet.fromIds(intArrayOf(Int.MIN_VALUE, 0, Int.MAX_VALUE)), set - MarkSet(intArrayOf(-1, 7)))
    }

    @Test
    fun `quantum one detached snapshots copy sort and hash wide sets without builder aliasing`(): Unit {
        val builder = RequestMarkSetBuilder()
        val ids = IntArray(513) { if (it % 2 == 0) -it else it }
        for (id in ids.reversed()) builder.addId(id)
        val cursor = builder.snapshotCursor()
        assertTrue(builder.isEmpty)
        builder.addId(9000)
        var steps = 0
        while (!cursor.isComplete) {
            assertTrue(cursor.step(1) in 0..1)
            check(++steps < 30_000)
        }
        val expected = RequestMarkSet.fromIds(ids)
        assertEquals(expected, cursor.result)
        assertEquals(expected.hashCode(), cursor.result.hashCode())
        assertEquals(RequestMarkSet.of(TaintMark(9000)), builder.takeSnapshot())
        assertTrue(builder.isEmpty)
        val visited = ArrayList<Int>()
        val members = cursor.result.cursor()
        while (!members.isComplete) assertTrue(members.step(1) { visited += it.id } <= 1)
        assertEquals(List(expected.size, expected::idAt), visited)
    }

    @Test
    fun `empty and singleton snapshots avoid radix inventory work including zero`(): Unit {
        for (id in listOf(0, -1, 42)) {
            val builder = RequestMarkSetBuilder()
            builder.addId(id)
            val cursor = builder.snapshotCursor()
            var units = 0
            while (!cursor.isComplete) units += cursor.step(1)
            assertTrue(units < 100, "A singleton must not execute four radix passes: $units")
            assertEquals(RequestMarkSet.of(TaintMark(id)), cursor.result)
        }
        val empty = RequestMarkSetBuilder().snapshotCursor()
        assertTrue(empty.isComplete)
        assertSame(RequestMarkSet.EMPTY, empty.result)
    }
}
