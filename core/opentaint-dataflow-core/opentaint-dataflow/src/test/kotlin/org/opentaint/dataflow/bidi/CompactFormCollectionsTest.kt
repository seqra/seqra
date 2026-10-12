package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.interp.OwnedListBuilder
import org.opentaint.dataflow.bidi.interp.ownedList
import org.opentaint.dataflow.bidi.interp.ownedMap
import org.opentaint.dataflow.bidi.interp.ownedSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompactFormCollectionsTest {
    private val sizes = listOf(0, 1, 2, 4, 8, 9, 16, 64)

    @Test fun `lists own their inputs and keep order duplicates and list equality`(): Unit {
        for (size in sizes) {
            val source = MutableList(size) { it % 3 }
            val expected = source.toList()
            val snapshot = ownedList(source)
            source.clear()
            assertEquals(expected, snapshot)
            assertEquals(snapshot, expected)
            assertEquals(expected.hashCode(), snapshot.hashCode())
            assertEquals(expected.asReversed(), snapshot.listIterator(snapshot.size).let { iterator ->
                buildList { while (iterator.hasPrevious()) add(iterator.previous()) }
            })
            assertFails { snapshot[-1] }
            assertFails { snapshot[snapshot.size] }
            if (snapshot.isNotEmpty()) {
                val retained = snapshot.subList(0, minOf(2, snapshot.size))
                assertFails { (retained as MutableList<Int>).clear() }
                assertEquals(expected.take(2), retained)
            }
            assertFails { (snapshot as MutableList<Int>).add(123) }
            assertEquals(expected, snapshot)
        }
    }

    @Test fun `ordered sets own inputs and compare as sets across the lookup threshold`(): Unit {
        for (size in sizes) {
            val source = (size - 1 downTo 0).toCollection(LinkedHashSet())
            val expectedOrder = source.toList()
            val snapshot = ownedSet(source)
            source.clear()
            assertEquals(expectedOrder, snapshot.toList())
            assertEquals(expectedOrder.toSet(), snapshot)
            assertEquals(snapshot, expectedOrder.toSet())
            assertEquals(expectedOrder.toSet().hashCode(), snapshot.hashCode())
            for (value in expectedOrder) assertTrue(value in snapshot)
            assertFalse(-1 in snapshot)
            assertFails { (snapshot as MutableSet<Int>).add(-1) }
            assertEquals(expectedOrder, snapshot.toList())
        }
        assertEquals(listOf(3, 1, 2), ownedSet(listOf(3, 1, 3, 2, 1)).toList())
    }

    @Test fun `maps own inputs and preserve entry order nullable values and equality`(): Unit {
        for (size in sizes) {
            val source = LinkedHashMap<Int, String?>()
            for (index in size - 1 downTo 0) source[index] = if (index % 2 == 0) null else "v$index"
            val expected = LinkedHashMap(source)
            val snapshot = ownedMap(source)
            val retainedKeys = snapshot.keys
            val retainedValues = snapshot.values
            source.clear()
            assertEquals(expected, snapshot)
            assertEquals(snapshot, expected)
            assertEquals(expected.hashCode(), snapshot.hashCode())
            assertEquals(expected.keys.toList(), retainedKeys.toList())
            assertEquals(expected.values.toList(), retainedValues.toList())
            for ((key, value) in expected) {
                assertTrue(snapshot.containsKey(key))
                assertEquals(value, snapshot[key])
            }
            assertFalse(snapshot.containsKey(-1))
            assertEquals(null, snapshot[-1])
            assertFails { (snapshot as MutableMap<Int, String?>)[-1] = "changed" }
            if (snapshot.isNotEmpty()) {
                val entry = snapshot.entries.first()
                assertFails { (entry as java.util.Map.Entry<Int, String?>).setValue("changed") }
                assertFails { (snapshot.entries.iterator() as MutableIterator<*>).remove() }
            }
            assertEquals(expected, snapshot)
        }
        val nullable = ownedMap(linkedMapOf<Int?, String?>(null to "null-key", 1 to null))
        assertTrue(nullable.containsKey(null))
        assertEquals("null-key", nullable[null])
        assertTrue(nullable.containsValue(null))
    }

    private data class Collision(val value: Int) {
        override fun hashCode(): Int = 1
    }

    @Test fun `indexed collections distinguish equal-hash keys and missing keys`(): Unit {
        val keys = (0 until 64).map(::Collision)
        val set = ownedSet(keys)
        val map = ownedMap(keys.associateWith { it.value })
        for (index in keys.indices) {
            assertTrue(Collision(index) in set)
            assertEquals(index, map[Collision(index)])
        }
        assertFalse(Collision(100) in set)
        assertFalse(map.containsKey(Collision(100)))
        assertEquals(keys, set.toList())
        assertEquals(keys, map.keys.toList())
    }

    @Test fun `known owned inputs reuse their immutable container`(): Unit {
        for (size in sizes) {
            val list = ownedList(List(size) { it })
            val set = ownedSet(list)
            val map = ownedMap(list.associateWith { it })
            assertSame(list, ownedList(list))
            assertSame(set, ownedSet(set))
            assertSame(map, ownedMap(map))
        }
    }

    @Test fun `builder freezes once and cannot mutate a published vector`(): Unit {
        for (size in sizes) {
            val builder = OwnedListBuilder<Int>()
            repeat(size) { builder.add(it) }
            val snapshot = builder.freeze()
            assertEquals(List(size) { it }, snapshot)
            assertFails { builder.add(-1) }
            assertFails { builder.freeze() }
            assertEquals(List(size) { it }, snapshot)
        }
    }

    @Test fun `element producers execute once including empty and singleton cases`(): Unit {
        for (size in sizes) {
            val visits = IntArray(size)
            val values = ownedList(size) { index -> visits[index]++; index }
            assertEquals(List(size) { it }, values)
            assertEquals(List(size) { 1 }, visits.toList())
        }
    }

    private data class OrderedEqual(val value: Int, val order: Int) {
        override fun equals(other: Any?): Boolean = other is OrderedEqual && other.value == value
        override fun hashCode(): Int = value
    }

    @Test fun `set snapshots normalize element equality even if the source comparator distinguishes them`(): Unit {
        val first = OrderedEqual(1, 0)
        val second = OrderedEqual(1, 1)
        val input = java.util.TreeSet(compareBy<OrderedEqual> { it.order }).apply { add(first); add(second) }
        val snapshot = ownedSet(input)
        assertEquals(LinkedHashSet(input), snapshot)
        assertEquals(listOf(first), snapshot.toList())
    }

    @Test fun `map snapshots normalize equal keys with first key order and last value`(): Unit {
        for (size in listOf(2, 9, 64)) {
            val input = java.util.TreeMap<OrderedEqual, String?>(compareBy { it.order })
            repeat(size) { index -> input[OrderedEqual(index / 2, index)] = if (index % 2 == 0) "v$index" else null }
            val expected = LinkedHashMap(input)
            val snapshot = ownedMap(input)
            input.clear()
            assertEquals(expected, snapshot)
            assertEquals(expected.hashCode(), snapshot.hashCode())
            assertEquals(expected.keys.map { it.order }, snapshot.keys.map { it.order })
            assertEquals(expected.values.toList(), snapshot.values.toList())
            for ((key, value) in expected) {
                assertTrue(snapshot.containsKey(key))
                assertEquals(value, snapshot[key])
            }
        }
    }

    @Test fun `Java map mutations and retained views cannot change owned snapshots`(): Unit {
        fun rejects(receiver: Any, owner: Class<*>, name: String,
                    types: Array<Class<*>>, vararg arguments: Any?) {
            val thrown = assertFailsWith<java.lang.reflect.InvocationTargetException> {
                owner.getMethod(name, *types).invoke(receiver, *arguments)
            }
            assertTrue(thrown.cause is UnsupportedOperationException, "$name: ${thrown.cause}")
        }
        val objectType = Any::class.java
        for (size in listOf(0, 1, 8, 9, 64)) {
            val expected = (0 until size).associateWith { "v$it" }
            val snapshot = ownedMap(expected)
            val keys = snapshot.keys
            val values = snapshot.values
            val entries = snapshot.entries
            rejects(snapshot, java.util.Map::class.java, "put", arrayOf(objectType, objectType), -1, "changed")
            rejects(snapshot, java.util.Map::class.java, "putAll", arrayOf(java.util.Map::class.java), mapOf(-1 to "changed"))
            rejects(snapshot, java.util.Map::class.java, "clear", emptyArray())
            for (view in listOf(keys, values, entries))
                rejects(view, java.util.Collection::class.java, "clear", emptyArray())
            if (size > 0) {
                rejects(snapshot, java.util.Map::class.java, "remove", arrayOf(objectType), 0)
                rejects(keys, java.util.Collection::class.java, "remove", arrayOf(objectType), 0)
                rejects(values, java.util.Collection::class.java, "remove", arrayOf(objectType), "v0")
                rejects(entries.first(), java.util.Map.Entry::class.java, "setValue", arrayOf(objectType), "changed")
            }
            assertEquals(expected, snapshot)
            assertEquals(expected.keys.toList(), keys.toList())
            assertEquals(expected.values.toList(), values.toList())
            assertEquals(expected.entries.toList(), entries.toList())
        }
    }
}
