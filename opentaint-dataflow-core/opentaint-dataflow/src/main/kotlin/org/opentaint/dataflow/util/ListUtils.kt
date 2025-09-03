package org.opentaint.dataflow.util

// index access is important because of concurrency
inline fun <T> MutableList<T>.concurrentReadSafeSumOf(element: (T) -> Long): Long {
    var result = 0L
    val size = this.size
    for (i in 0 until size) {
        result += element(this[i])
    }
    return result
}

inline fun <T> MutableList<T>.concurrentReadSafeForEach(block: (Int, T) -> Unit) {
    val size = this.size
    for (i in 0 until size) {
        block(i, this[i])
    }
}

fun <T> List<T>.concurrentReadSafeIterator(): Iterator<T> = object : Iterator<T> {
    private val listSize = size
    private var idx = 0

    override fun hasNext(): Boolean = idx < listSize
    override fun next(): T = this@concurrentReadSafeIterator[idx++]
}

inline fun <T, R> List<T>.concurrentReadSafeMapIndexed(body: (Int, T) -> R): List<R> {
    val result = mutableListOf<R>()
    val size = this.size
    for (i in 0 until size) {
        result.add(body(i, this[i]))
    }
    return result
}
