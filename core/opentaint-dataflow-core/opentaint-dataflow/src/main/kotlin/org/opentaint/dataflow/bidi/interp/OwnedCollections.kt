package org.opentaint.dataflow.bidi.interp

import java.util.AbstractMap.SimpleImmutableEntry
import java.util.RandomAccess

/** A representation threshold only: every key is retained in either representation. */
internal const val COMPACT_LINEAR_LIMIT = 8

private fun elementIndex(index: Int, size: Int) {
    if (index < 0 || index >= size) throw IndexOutOfBoundsException("index=$index, size=$size")
}

private abstract class OwnedVector<out E> : AbstractList<E>(), RandomAccess {
    open fun storage(counts: FormStorageCounts) { counts.ownedContainerEntries += size.toLong() }
}

private object EmptyVector : OwnedVector<Any?>() {
    override val size: Int get() = 0
    override fun get(index: Int): Nothing = throw IndexOutOfBoundsException("index=$index, size=0")
}

private class SingletonVector<E>(private val element: E) : OwnedVector<E>() {
    override val size: Int get() = 1
    override fun get(index: Int): E {
        elementIndex(index, 1)
        return element
    }
}

private class ArrayVector<E>(private val elements: Array<Any?>) : OwnedVector<E>() {
    override val size: Int get() = elements.size
    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        elementIndex(index, size)
        return elements[index] as E
    }
}

/** The array is newly allocated by this file and is never exposed to a mutable caller. */
@Suppress("UNCHECKED_CAST")
private fun <E> adoptVector(elements: Array<Any?>): List<E> = when (elements.size) {
    0 -> EmptyVector as List<E>
    1 -> SingletonVector(elements[0] as E)
    else -> ArrayVector(elements)
}

/** Snapshot unknown inputs; an already owned immutable vector needs no second copy. */
@Suppress("UNCHECKED_CAST")
internal fun <E> ownedList(values: Collection<E>): List<E> {
    if (values is OwnedVector<*>) return values as List<E>
    return when (values.size) {
        0 -> EmptyVector as List<E>
        1 -> SingletonVector(if (values is List<E>) values[0] else values.iterator().next())
        else -> {
            val elements = arrayOfNulls<Any?>(values.size)
            if (values is List<E> && values is RandomAccess) {
                for (index in elements.indices) elements[index] = values[index]
            } else {
                var index = 0
                for (value in values) elements[index++] = value
            }
            adoptVector(elements)
        }
    }
}

/** Evaluate each supplied element once into storage owned by the result. */
internal fun <E> ownedList(size: Int, elementAt: (Int) -> E): List<E> {
    require(size >= 0)
    return when (size) {
        0 -> ownedList(emptyList())
        1 -> SingletonVector(elementAt(0))
        else -> adoptVector(Array<Any?>(size) { elementAt(it) })
    }
}

/** Single-use internal builder. Public form builders continue to create independent snapshots. */
internal class OwnedListBuilder<E>(private val initialCapacity: Int = 0) {
    init { require(initialCapacity >= 0) }
    private var first: Any? = null
    private var elements: Array<Any?>? = null
    private var count = 0
    private var frozen = false

    fun add(value: E) {
        check(!frozen) { "An owned vector builder was already frozen" }
        if (count == 0) first = value
        else {
            var array = elements
            if (array == null) {
                array = arrayOfNulls(maxOf(2, initialCapacity))
                array[0] = first
                first = null
                elements = array
            } else if (count == array.size) {
                val capacity = maxOf(count.toLong() + 1, array.size.toLong() * 2)
                check(capacity <= Int.MAX_VALUE) { "An owned vector exceeds its array representation" }
                array = array.copyOf(capacity.toInt())
                elements = array
            }
            array[count] = value
        }
        count++
    }

    @Suppress("UNCHECKED_CAST")
    fun freeze(): List<E> {
        check(!frozen) { "An owned vector builder was already frozen" }
        frozen = true
        val result: List<E> = when (count) {
            0 -> EmptyVector as List<E>
            1 -> SingletonVector(first as E)
            else -> {
                val array = checkNotNull(elements)
                adoptVector(if (count == array.size) array else array.copyOf(count))
            }
        }
        first = null
        elements = null
        return result
    }
}

private abstract class OwnedSet<E> : AbstractSet<E>() {
    open fun storageIndex(): IntArray? = null
}

/** Ordinal plus one; zero denotes an empty bucket. Keys stay in their ordered array. */
private fun keyLookup(keys: Array<Any?>, size: Int = keys.size): IntArray? {
    if (size <= COMPACT_LINEAR_LIMIT) return null
    val required = size.toLong() * 2
    if (required > (1L shl 30)) return null
    var capacity = 1
    while (capacity.toLong() < required) capacity = capacity shl 1
    val buckets = IntArray(capacity)
    val mask = capacity - 1
    for (index in 0 until size) addKeyBucket(buckets, keys[index], index)
    return buckets
}

private fun addKeyBucket(buckets: IntArray, key: Any?, index: Int) {
    val mask = buckets.size - 1
    val hash = key?.hashCode() ?: 0
    var bucket = (hash xor (hash ushr 16)) and mask
    while (buckets[bucket] != 0) bucket = (bucket + 1) and mask
    buckets[bucket] = index + 1
}

private fun keyIndex(keys: Array<Any?>, lookup: IntArray?, key: Any?, size: Int = keys.size): Int {
    if (lookup == null) {
        for (index in 0 until size) if (keys[index] == key) return index
        return -1
    }
    val mask = lookup.size - 1
    val hash = key?.hashCode() ?: 0
    var bucket = (hash xor (hash ushr 16)) and mask
    while (true) {
        val index = lookup[bucket] - 1
        if (index < 0) return -1
        if (keys[index] == key) return index
        bucket = (bucket + 1) and mask
    }
}

private object EmptyOwnedSet : OwnedSet<Any?>() {
    override val size: Int get() = 0
    override fun contains(element: Any?): Boolean = false
    override fun iterator(): Iterator<Any?> = EmptyVector.iterator()
}

private class SingletonOwnedSet<E>(private val element: E) : OwnedSet<E>() {
    override val size: Int get() = 1
    override fun contains(element: E): Boolean = this.element == element
    override fun iterator(): Iterator<E> = object : Iterator<E> {
        private var remaining = true
        override fun hasNext(): Boolean = remaining
        override fun next(): E {
            if (!remaining) throw NoSuchElementException()
            remaining = false
            return element
        }
    }
}

private class ArrayOwnedSet<E>(private val elements: Array<Any?>,
                               private val index: IntArray? = keyLookup(elements)) : OwnedSet<E>() {
    override fun storageIndex(): IntArray? = index
    override val size: Int get() = elements.size
    override fun contains(element: E): Boolean = keyIndex(elements, index, element) >= 0
    override fun iterator(): Iterator<E> = object : Iterator<E> {
        private var next = 0
        override fun hasNext(): Boolean = next < elements.size
        @Suppress("UNCHECKED_CAST")
        override fun next(): E {
            if (!hasNext()) throw NoSuchElementException()
            return elements[next++] as E
        }
    }
}

/** Preserve first occurrence order, including when a collection contains equal values. */
@Suppress("UNCHECKED_CAST")
internal fun <E> ownedSet(values: Collection<E>): Set<E> {
    if (values is OwnedSet<*>) return values as Set<E>
    if (values is Set<E>) return when (values.size) {
        0 -> EmptyOwnedSet as Set<E>
        1 -> SingletonOwnedSet(values.iterator().next())
        else -> {
            val elements = arrayOfNulls<Any?>(values.size)
            var count = 0
            var lookup: IntArray? = null
            for (value in values) {
                if (keyIndex(elements, lookup, value, count) >= 0) continue
                elements[count] = value
                count++
                if (count > COMPACT_LINEAR_LIMIT) {
                    if (lookup == null || count.toLong() * 2 > lookup.size) lookup = keyLookup(elements, count)
                    else addKeyBucket(lookup, value, count - 1)
                }
            }
            if (count == 1) SingletonOwnedSet(elements[0] as E)
            else ArrayOwnedSet(if (count == elements.size) elements else elements.copyOf(count), lookup)
        }
    }
    val unique = ArrayList<E>(minOf(values.size, COMPACT_LINEAR_LIMIT))
    var index: HashSet<E>? = null
    for (value in values) {
        val added = index?.add(value) ?: !unique.contains(value)
        if (!added) continue
        unique.add(value)
        if (unique.size > COMPACT_LINEAR_LIMIT && index == null) index = HashSet(unique)
    }
    return when (unique.size) {
        0 -> EmptyOwnedSet as Set<E>
        1 -> SingletonOwnedSet(unique[0])
        else -> ArrayOwnedSet(Array<Any?>(unique.size) { unique[it] })
    }
}

/** Entry views are read-only and created only for callers that use the collection interface. */
private abstract class OwnedMap<K, V> : AbstractMap<K, V>() {
    @Volatile private var entryView: Set<Map.Entry<K, V>>? = null
    abstract fun keyAt(index: Int): K
    abstract fun valueAt(index: Int): V
    abstract fun keyIndex(key: K): Int
    open fun storageIndex(): IntArray? = null

    override fun containsKey(key: K): Boolean = keyIndex(key) >= 0
    override fun get(key: K): V? = keyIndex(key).let { if (it < 0) null else valueAt(it) }
    override fun containsValue(value: V): Boolean {
        for (index in 0 until size) if (valueAt(index) == value) return true
        return false
    }

    override val entries: Set<Map.Entry<K, V>>
        get() = entryView ?: object : AbstractSet<Map.Entry<K, V>>() {
            override val size: Int get() = this@OwnedMap.size
            override fun iterator(): Iterator<Map.Entry<K, V>> = object : Iterator<Map.Entry<K, V>> {
                private var next = 0
                override fun hasNext(): Boolean = next < size
                override fun next(): Map.Entry<K, V> {
                    if (!hasNext()) throw NoSuchElementException()
                    val index = next++
                    return SimpleImmutableEntry(keyAt(index), valueAt(index))
                }
            }
        }.also { entryView = it }
}

private object EmptyOwnedMap : OwnedMap<Any?, Any?>() {
    override val size: Int get() = 0
    override fun keyAt(index: Int): Nothing = throw IndexOutOfBoundsException("index=$index, size=0")
    override fun valueAt(index: Int): Nothing = throw IndexOutOfBoundsException("index=$index, size=0")
    override fun keyIndex(key: Any?): Int = -1
}

private class SingletonOwnedMap<K, V>(private val key: K, private val value: V) : OwnedMap<K, V>() {
    override val size: Int get() = 1
    override fun keyAt(index: Int): K { elementIndex(index, 1); return key }
    override fun valueAt(index: Int): V { elementIndex(index, 1); return value }
    override fun keyIndex(key: K): Int = if (this.key == key) 0 else -1
}

private class ArrayOwnedMap<K, V>(private val keyData: Array<Any?>, private val valueData: Array<Any?>,
                                 private val index: IntArray? = keyLookup(keyData)) : OwnedMap<K, V>() {
    override fun storageIndex(): IntArray? = index
    override val size: Int get() = keyData.size
    @Suppress("UNCHECKED_CAST")
    override fun keyAt(index: Int): K = keyData[index] as K
    @Suppress("UNCHECKED_CAST")
    override fun valueAt(index: Int): V = valueData[index] as V
    override fun keyIndex(key: K): Int = keyIndex(keyData, index, key)
}

@Suppress("UNCHECKED_CAST")
internal fun <K, V> ownedMap(values: Map<K, V>): Map<K, V> {
    if (values is OwnedMap<*, *>) return values as Map<K, V>
    return when (values.size) {
        0 -> EmptyOwnedMap as Map<K, V>
        1 -> values.entries.first().let { SingletonOwnedMap(it.key, it.value) }
        else -> {
            val keys = arrayOfNulls<Any?>(values.size)
            val entries = arrayOfNulls<Any?>(values.size)
            var count = 0
            var lookup: IntArray? = null
            for ((key, value) in values) {
                val existing = keyIndex(keys, lookup, key, count)
                if (existing >= 0) {
                    entries[existing] = value
                    continue
                }
                keys[count] = key
                entries[count] = value
                count++
                if (count > COMPACT_LINEAR_LIMIT) {
                    if (lookup == null || count.toLong() * 2 > lookup.size) lookup = keyLookup(keys, count)
                    else addKeyBucket(lookup, key, count - 1)
                }
            }
            if (count == 1) SingletonOwnedMap(keys[0] as K, entries[0] as V)
            else ArrayOwnedMap(if (count == keys.size) keys else keys.copyOf(count),
                if (count == entries.size) entries else entries.copyOf(count), lookup)
        }
    }
}

/** Internal producer supplies distinct ordered keys and is evaluated once per key/value. */
@Suppress("UNCHECKED_CAST")
internal fun <K, V> ownedMap(size: Int, keyAt: (Int) -> K, valueAt: (Int) -> V): Map<K, V> {
    require(size >= 0)
    return when (size) {
        0 -> EmptyOwnedMap as Map<K, V>
        1 -> SingletonOwnedMap(keyAt(0), valueAt(0))
        else -> ArrayOwnedMap(Array<Any?>(size) { keyAt(it) }, Array<Any?>(size) { valueAt(it) })
    }
}

private class OrdinalVector<E>(private val source: List<E>, private val ordinals: IntArray,
                               private val from: Int, override val size: Int) : OwnedVector<E>() {
    override fun storage(counts: FormStorageCounts) {
        counts.include(source)
        counts.index(ordinals)
    }
    override fun get(index: Int): E {
        elementIndex(index, size)
        return source[ordinals[from + index]]
    }
}

/** One shared element vector and ordinal array; every group view is retained once. */
internal fun <K, E> ownedOrdinalGroups(input: List<E>, keyOf: (E) -> K): Map<K, List<E>> {
    val values = ownedList(input)
    if (values.isEmpty()) return ownedMap(emptyMap())
    val firstKey = keyOf(values[0])
    var keys: ArrayList<K>? = null
    var lookup: HashMap<K, Int>? = null
    var groups: IntArray? = null
    var counts: IntArray? = null
    for (i in 1 until values.size) {
        val key = keyOf(values[i])
        if (keys == null) {
            if (key == firstKey) continue
            keys = arrayListOf(firstKey)
            groups = IntArray(values.size)
            counts = IntArray(values.size)
            counts[0] = i
        }
        val ordered = keys
        var group = if (lookup == null) ordered.indexOf(key) else lookup[key] ?: -1
        if (group < 0) {
            group = ordered.size
            ordered.add(key)
            if (lookup != null) lookup[key] = group
            else if (ordered.size > COMPACT_LINEAR_LIMIT) {
                lookup = HashMap<K, Int>().also { map -> ordered.forEachIndexed { index, item -> map[item] = index } }
            }
        }
        checkNotNull(groups)[i] = group
        checkNotNull(counts)[group]++
    }
    if (keys == null) return ownedMap(1, { firstKey }, { values })
    val ordered = keys
    val sizes = checkNotNull(counts)
    val offsets = IntArray(ordered.size + 1)
    for (i in ordered.indices) offsets[i + 1] = offsets[i] + sizes[i]
    for (i in ordered.indices) sizes[i] = offsets[i]
    val ordinals = IntArray(values.size)
    for (i in values.indices) {
        val group = checkNotNull(groups)[i]
        ordinals[sizes[group]++] = i
    }
    return ownedMap(ordered.size, { ordered[it] }) { group ->
        val from = offsets[group]
        val size = offsets[group + 1] - from
        if (size == 1) SingletonVector(values[ordinals[from]]) else OrdinalVector(values, ordinals, from, size)
    }
}

/** Read retained backing storage without creating entries/keys/values compatibility views. */
internal fun ownedStorage(value: Any, counts: FormStorageCounts): Boolean = when (value) {
    is OwnedVector<*> -> { value.storage(counts); true }
    is OwnedSet<*> -> {
        counts.ownedContainerEntries += value.size.toLong()
        counts.index(value.storageIndex())
        true
    }
    is OwnedMap<*, *> -> {
        counts.ownedContainerEntries += 2L * value.size
        counts.index(value.storageIndex())
        true
    }
    else -> false
}

/** Only form directories have owned vector values; filter maps remain opaque payloads. */
internal fun ownedDirectoryStorage(directory: Map<*, *>, counts: FormStorageCounts) {
    counts.include(directory)
    check(directory is OwnedMap<*, *>)
    for (index in 0 until directory.size) counts.include(checkNotNull(directory.valueAt(index)))
}
