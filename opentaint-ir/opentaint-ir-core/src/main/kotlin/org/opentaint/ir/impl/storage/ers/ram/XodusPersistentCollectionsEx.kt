package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.persistent.Persistent23Tree
import jetbrains.exodus.core.dataStructures.persistent.Persistent23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentHashMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentHashSet
import jetbrains.exodus.core.dataStructures.persistent.PersistentHashSet.ImmutablePersistentHashSet
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongSet

typealias PersistentMutableHashSet<K> = PersistentHashSet.MutablePersistentHashSet<K>
typealias PersistentLongImmutableSet = PersistentLongSet.ImmutableSet
typealias PersistentLongMutableSet = PersistentLongSet.MutableSet
typealias PersistentLongImmutableMap<V> = PersistentLongMap.ImmutableMap<V>
typealias PersistentLongMutableMap<V> = PersistentLongMap.MutableMap<V>

inline fun <T> PersistentHashSet<T>.write(writer: PersistentMutableHashSet<T>.() -> Unit): PersistentHashSet<T> =
    clone.also { clone ->
        clone.beginWrite().apply {
            writer()
            endWrite()
        }
    }

inline fun <T> PersistentHashSet<T>.writeCheckSize(writer: PersistentMutableHashSet<T>.() -> Unit): PersistentHashSet<T> {
    clone.also { clone ->
        clone.beginWrite().apply {
            val initialSize = size()
            writer()
            if (initialSize != size() && endWrite()) {
                return clone
            }
        }
    }
    return this
}

inline fun <K : Comparable<K>> Persistent23Tree<K>.write(writer: Persistent23Tree.MutableTree<K>.() -> Unit): Persistent23Tree<K> =
    clone.also { clone ->
        clone.beginWrite().apply {
            writer()
            endWrite()
        }
    }

inline fun <K : Comparable<K>> Persistent23Tree<K>.writeCheckSize(writer: Persistent23Tree.MutableTree<K>.() -> Unit): Persistent23Tree<K> {
    clone.also { clone ->
        clone.beginWrite().apply {
            val initialSize = size()
            writer()
            if (initialSize != size() && endWrite()) {
                return clone
            }
        }
    }
    return this
}

inline fun <T> PersistentHashSet<*>.read(reader: ImmutablePersistentHashSet<*>.() -> T): T = beginRead().reader()

inline val PersistentHashSet<*>.size: Int get() = beginRead().size()

operator fun PersistentHashSet<*>.contains(value: Long): Boolean =
    value in (beginRead() as ImmutablePersistentHashSet<Long>)

operator fun <K : Any, V> PersistentHashMap<K, V>.get(key: K): V? = current[key]

inline fun <K : Any, V, T> PersistentHashMap<K, V>.write(writer: PersistentHashMap<K, V>.MutablePersistentHashMap.() -> T?): Pair<T?, PersistentHashMap<K, V>> =
    clone.run {
        beginWrite().run {
            val result = writer()
            endWrite()
            result
        } to this
    }

operator fun <K : Comparable<K>, V> Persistent23TreeMap<K, V>.get(key: K): V? = beginRead()[key]

inline fun <K : Comparable<K>, V, T> Persistent23TreeMap<K, V>.write(writer: Persistent23TreeMap.MutableMap<K, V>.() -> T?): Pair<T?, Persistent23TreeMap<K, V>> =
    clone.run {
        beginWrite().run {
            val result = writer()
            endWrite()
            result
        } to this
    }

inline fun PersistentLongSet.write(writer: PersistentLongMutableSet.() -> Unit): PersistentLongSet = clone.apply {
    beginWrite().apply {
        writer()
        endWrite()
    }
}

inline fun PersistentLongSet.writeCheckSize(writer: PersistentLongMutableSet.() -> Unit): PersistentLongSet {
    clone.apply {
        beginWrite().apply {
            val initialSize = size()
            writer()
            if (initialSize != size() && endWrite()) {
                return clone
            }
        }
    }
    return this
}

fun <T> PersistentLongSet.read(reader: PersistentLongImmutableSet.() -> T): T = beginRead().reader()

inline val PersistentLongSet.size: Int get() = beginRead().size()

fun PersistentLongSet.asIterable(): Iterable<Long> = beginRead().longIterator().asSequence().asIterable()

fun PersistentLongSet.iterator(): Iterator<Long> = beginRead().longIterator()

operator fun PersistentLongSet.contains(value: Long): Boolean = value in beginRead()

inline fun <T, V> PersistentLongMap<V>.write(writer: PersistentLongMutableMap<V>.() -> T?): Pair<T?, PersistentLongMap<V>> =
    clone.run {
        beginWrite().run {
            val result = writer()
            endWrite()
            result
        } to this
    }

inline fun <T> PersistentLongMap<*>.read(reader: PersistentLongImmutableMap<*>.() -> T): T = beginRead().reader()

val PersistentLongMap<*>.size: Int get() = beginRead().size()

operator fun <V> PersistentLongMap<V>.get(key: Long): V? = beginRead()[key]