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

inline fun <T> List<T>.concurrentReadSafeForEach(block: (Int, T) -> Unit) {
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

inline fun <D, T> collectToListWithPostProcess(
    dst: MutableList<D>,
    collect: (MutableList<T>) -> Unit,
    after: (T) -> D
): MutableList<D> {
    val initialSize = dst.size

    @Suppress("UNCHECKED_CAST")
    collect(dst as MutableList<T>)

    for (i in initialSize until dst.size) {
        dst[i] = after(dst[i])
    }

    return dst
}

class PersistentArrayBuilder<T>(val original: Array<T?>) {
    private var modified: Array<T?>? = null
    var size = original.size

    operator fun get(index: Int): T? {
        val mod = modified ?: return original.getOrNull(index)
        return mod.getOrNull(index)
    }

    operator fun set(index: Int, value: T?): T? {
        var mod = modified
        if (mod == null) {
            mod = if (index < original.size) {
                original.clone()
            } else {
                original.copyOf(capacity(index))
            }
            modified = mod
        }

        if (index >= mod.size) {
            mod = mod.copyOf(capacity(index))
            modified = mod
        }

        val oldValue = mod[index]
        mod[index] = value

        if (value != null) {
            size = maxOf(size, index + 1)
        } else {
            while (size > 0 && mod[size - 1] == null) {
                size--
            }
        }

        return oldValue
    }

    fun persist(): Array<T?> {
        val mod = modified ?: return original

        if (mod.size == size) return mod

        return mod.copyOf(size)
    }

    companion object {
        @JvmStatic
        private fun capacity(index: Int): Int =
            if (index <= 7) 8 else index + index / 2
    }
}
