package org.opentaint.dataflow.util

fun <V> int2ObjectMap() = ConcurrentReadSafeInt2ObjectMap<V>()

inline fun <V> ConcurrentReadSafeInt2ObjectMap<V>.forEachEntry(body: (Int, V) -> Unit) {
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

fun <K> object2IntMap() = ConcurrentReadSafeObject2IntMap<K>()

fun <K> ConcurrentReadSafeObject2IntMap<K>.getValue(key: K): Int {
    val value = getInt(key)
    check(value != ConcurrentReadSafeObject2IntMap.NO_VALUE) { "No value for $key found in $this" }
    return value
}

inline fun <K> ConcurrentReadSafeObject2IntMap<K>.getOrCreateIndex(key: K, onNewIndex: (Int) -> Nothing): Int {
    val newIndex = size
    val currentIndex = putIfAbsent(key, newIndex)
    if (currentIndex != ConcurrentReadSafeObject2IntMap.NO_VALUE) return currentIndex
    onNewIndex(newIndex)
}
