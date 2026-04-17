package org.opentaint.dataflow.util

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.ints.IntList

inline fun IntCollection.forEachInt(block: (Int) -> Unit) {
    val iter = intIterator()
    while (iter.hasNext()) {
        val element = iter.nextInt()
        block(element)
    }
}

inline fun IntList.reversedForEachInt(block: (Int) -> Unit) {
    val iter = listIterator(size)
    while (iter.hasPrevious()) {
        val element = iter.previousInt()
        block(element)
    }
}

inline fun Int2IntOpenHashMap.forEachIntEntry(block: (key: Int, value: Int) -> Unit) {
    val iter = int2IntEntrySet().fastIterator()
    while (iter.hasNext()) {
        val entry = iter.next()
        block(entry.intKey, entry.intValue)
    }
}

inline fun <T> Int2ObjectOpenHashMap<T>.forEachIntEntry(block: (key: Int, value: T) -> Unit) {
    val iter = int2ObjectEntrySet().fastIterator()
    while (iter.hasNext()) {
        val entry = iter.next()
        block(entry.intKey, entry.value)
    }
}

inline fun <D : IntCollection> IntCollection.mapIntTo(dst: D, mapper: (Int) -> Int): D {
    forEachInt { dst.add(mapper(it)) }
    return dst
}
