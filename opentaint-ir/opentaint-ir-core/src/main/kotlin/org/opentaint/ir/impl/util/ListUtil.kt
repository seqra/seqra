package org.opentaint.ir.impl.util

import java.util.*

/**
 * Concatenates several lists, each one is nullable.
 * If the result is empty always returns EmptyList singleton.
 * Can be used for a single list to return singleton if it is empty.
 */
fun <T> concatLists(vararg lists: List<T>?): List<T> {
    var resultSize = 0
    lists.forEach {
        resultSize += it?.size ?: 0
    }
    if (resultSize == 0) return emptyList()
    if (lists.size == 1) return lists[0].orEmpty()
    return ArrayList<T>(resultSize).apply {
        lists.forEach {
            it?.let { list ->
                addAll(list)
            }
        }
    }
}

fun <T> List<T>.adjustEmptyList(): List<T> = ifEmpty { Collections.emptyList() }