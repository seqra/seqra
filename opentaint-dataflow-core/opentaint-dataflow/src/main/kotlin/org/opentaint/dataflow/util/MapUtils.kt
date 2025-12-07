package org.opentaint.dataflow.util

fun <V> int2ObjectMap() = org.opentaint.dataflow.util.ConcurrentReadSafeInt2ObjectMap<V>()

inline fun <V> org.opentaint.dataflow.util.ConcurrentReadSafeInt2ObjectMap<V>.forEachEntry(body: (Int, V) -> Unit) {
    if (isEmpty()) return

    while (true) {
        val containsNullKey = getContainsNullKey()
        val key = getKeys()
        val value = getValues()
        val n = getN()

        // capture arrays to allow concurrent reads
        if (key.size != n + 1 || value.size != n + 1) continue

        if (containsNullKey) {
            body(0, value[n] as V)
        }

        for (i in 0 until n) {
            val k = key[i]
            if (k == 0) continue

            body(k, value[i] as V)
        }

        return
    }
}

fun <K> object2IntMap() = org.opentaint.dataflow.util.ConcurrentReadSafeObject2IntMap<K>()

fun <K> org.opentaint.dataflow.util.ConcurrentReadSafeObject2IntMap<K>.getValue(key: K): Int {
    val value = getInt(key)
    check(value != org.opentaint.dataflow.util.ConcurrentReadSafeObject2IntMap.NO_VALUE) { "No value for $key found in $this" }
    return value
}

inline fun <K> org.opentaint.dataflow.util.ConcurrentReadSafeObject2IntMap<K>.getOrCreateIndex(key: K, onNewIndex: (Int) -> Nothing): Int {
    val newIndex = size
    val currentIndex = putIfAbsent(key, newIndex)
    if (currentIndex != org.opentaint.dataflow.util.ConcurrentReadSafeObject2IntMap.NO_VALUE) return currentIndex
    onNewIndex(newIndex)
}
