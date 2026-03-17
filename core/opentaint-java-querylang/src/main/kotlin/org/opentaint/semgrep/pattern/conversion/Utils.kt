package org.opentaint.semgrep.pattern.conversion

inline fun <reified T, R> List<List<T>>.cartesianProductMapTo(body: (Array<T>) -> R): List<R> {
    val resultSize = fold(1) { acc, lst -> acc * lst.size }
    if (resultSize == 0) return emptyList()

    val result = mutableListOf<R>()
    val chunk = arrayOfNulls<T>(size)
    for (chunkIdx in 0 until resultSize) {

        var currentChunkPos = chunkIdx
        for (i in indices) {
            val lst = this[i]
            val lstSize = lst.size
            chunk[i] = lst[currentChunkPos % lstSize]
            currentChunkPos /= lstSize
        }

        @Suppress("UNCHECKED_CAST")
        result += body(chunk as Array<T>)
    }

    return result
}
