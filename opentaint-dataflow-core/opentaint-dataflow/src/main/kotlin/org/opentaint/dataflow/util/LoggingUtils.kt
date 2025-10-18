package org.opentaint.dataflow.util

fun percentToString(current: Long, total: Long): String {
    val percentValue = current.toDouble() / total
    return String.format("%.2f", percentValue * 100) + "%"
}

fun percentToString(current: Int, total: Int): String =
    percentToString(current.toLong(), total.toLong())
