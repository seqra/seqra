package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.store.KaryJoin
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class KaryJoinTest {
    private data class Event(val slot: Int, val value: String)
    private fun <T> permutations(values: List<T>): Sequence<List<T>> = sequence {
        if (values.isEmpty()) yield(emptyList())
        else for (index in values.indices) for (suffix in permutations(values.filterIndexed { i, _ -> i != index }))
            yield(listOf(values[index]) + suffix)
    }

    @Test fun `every arrival order gives each full combination once including duplicate events`(): Unit {
        val events = (0..2).flatMap { slot -> listOf(Event(slot, "A"), Event(slot, "B")) }
        val expected = listOf("A", "B").flatMap { a -> listOf("A", "B").flatMap { b ->
            listOf("A", "B").map { c -> listOf(a, b, c) } } }.toSet()
        var schedules = 0
        for (schedule in permutations(events)) {
            val join = KaryJoin<String>(3)
            val observed = ArrayList<List<String>>()
            for (event in schedule) {
                observed += join.add(event.slot, event.value)
                assertTrue(join.add(event.slot, event.value).isEmpty())
            }
            assertEquals(expected, observed.toSet())
            assertEquals(expected.size, observed.size)
            schedules++
        }
        assertEquals(720, schedules)
    }

    @Test fun `the last slot sees every earlier input and one input can occupy repeated slots`(): Unit {
        val join = KaryJoin<String>(3)
        assertTrue(join.add(0, "same").isEmpty())
        assertTrue(join.add(0, "other").isEmpty())
        assertTrue(join.add(1, "same").isEmpty())
        assertEquals(setOf(listOf("same", "same", "same"), listOf("other", "same", "same")), join.add(2, "same").toSet())
        assertEquals(setOf(listOf("same", "same", "late"), listOf("other", "same", "late")), join.add(2, "late").toSet())
        assertEquals(setOf(listOf("same", "late", "same"), listOf("same", "late", "late"),
            listOf("other", "late", "same"), listOf("other", "late", "late")), join.add(1, "late").toSet())
    }

    @Test fun `arity and slot guards reject invalid inputs before changing a join`(): Unit {
        for (arity in listOf(-1, 0, 1)) assertFailsWith<IllegalArgumentException> { KaryJoin<String>(arity) }
        val join = KaryJoin<String>(2)
        assertEquals(2, join.arity)
        for (slot in listOf(-1, 2, Int.MAX_VALUE)) assertFails { join.add(slot, "bad") }
        assertTrue(join.add(0, "A").isEmpty())
        assertEquals(listOf(listOf("A", "B")), join.add(1, "B"))
    }

    @Test fun `large literal sets use an iterative product`(): Unit {
        val arity = 32_768
        val join = KaryJoin<Int>(arity)
        for (slot in 0 until arity - 1) assertTrue(join.add(slot, slot).isEmpty())
        val only = join.add(arity - 1, arity - 1).single()
        assertEquals((0 until arity).toList(), only)
        assertTrue(join.add(arity - 1, arity - 1).isEmpty())
    }

    @Test fun `optional cancellation stops insertion before retaining a value`(): Unit {
        val cancellation = Cancellation()
        val join = KaryJoin<String>(2, cancellation)
        assertTrue(join.add(0, "A").isEmpty())
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { join.add(1, "B") }
        cancellation.activate()
        assertEquals(listOf(listOf("A", "B")), join.add(1, "B"))
    }
}
