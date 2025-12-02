package org.opentaint.jvm.util

fun <T> MutableList<T>.replace(replace: T, replacement: T): Boolean {
    val idx = indexOf(replace)
    return if (idx == -1) {
        false
    } else {
        this[idx] = replacement
        true
    }
}

inline fun <A, K, V, R : MutableMap<K, V>> Collection<A>.mapIndexedNotNullTo(
    result: R,
    body: (Int, A) -> Pair<K, V>?
): R {
    for (element in this.withIndex()) {
        val transformed = body(element.index, element.value) ?: continue
        result += transformed
    }
    return result
}
