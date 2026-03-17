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

internal inline fun <T> PersistentHashSet<T>.write(writer: PersistentMutableHashSet<T>.() -> Unit): PersistentHashSet<T> =
    clone.also { clone ->
        clone.beginWrite().apply {
            writer()
            endWrite()
        }
    }

internal inline fun <K : Comparable<K>> Persistent23Tree<K>.write(writer: Persistent23Tree.MutableTree<K>.() -> Unit): Persistent23Tree<K> =
    clone.also { clone ->
        clone.beginWrite().apply {
            writer()
            endWrite()
        }
    }

internal inline fun <T> PersistentHashSet<*>.read(reader: ImmutablePersistentHashSet<*>.() -> T): T = beginRead().reader()

internal inline val PersistentHashSet<*>.size: Int get() = beginRead().size()

internal operator fun PersistentHashSet<*>.contains(value: Long): Boolean =
    value in (beginRead() as ImmutablePersistentHashSet)

internal operator fun <K : Any, V> PersistentHashMap<K, V>.get(key: K): V? = current[key]

internal inline fun <K : Any, V, T> PersistentHashMap<K, V>.write(writer: PersistentHashMap<K, V>.MutablePersistentHashMap.() -> T?): Pair<T?, PersistentHashMap<K, V>> =
    clone.run {
        beginWrite().run {
            val result = writer()
            endWrite()
            result
        } to this
    }

internal operator fun <K : Comparable<K>, V> Persistent23TreeMap<K, V>.get(key: K): V? = beginRead()[key]

internal inline fun <K : Comparable<K>, V, T> Persistent23TreeMap<K, V>.write(writer: Persistent23TreeMap.MutableMap<K, V>.() -> T?): Pair<T?, Persistent23TreeMap<K, V>> =
    clone.run {
        beginWrite().run {
            val result = writer()
            endWrite()
            result
        } to this
    }

internal inline fun PersistentLongSet.write(writer: PersistentLongMutableSet.() -> Unit): PersistentLongSet = clone.apply {
    beginWrite().apply {
        writer()
        endWrite()
    }
}

internal fun <T> PersistentLongSet.read(reader: PersistentLongImmutableSet.() -> T): T = beginRead().reader()

internal inline val PersistentLongSet.size: Int get() = beginRead().size()

internal fun PersistentLongSet.iterator(): Iterator<Long> = beginRead().longIterator()

internal operator fun PersistentLongSet.contains(value: Long): Boolean = value in beginRead()

internal inline fun <T, V> PersistentLongMap<V>.write(writer: PersistentLongMutableMap<V>.() -> T?): Pair<T?, PersistentLongMap<V>> =
    clone.run {
        beginWrite().run {
            val result = writer()
            endWrite()
            result
        } to this
    }

internal inline fun <T> PersistentLongMap<*>.read(reader: PersistentLongImmutableMap<*>.() -> T): T = beginRead().reader()

internal val PersistentLongMap<*>.size: Int get() = beginRead().size()

internal operator fun <V> PersistentLongMap<V>.get(key: Long): V? = beginRead()[key]