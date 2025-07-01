package org.opentaint.ir.impl.util

fun Throwable.allCauses(): Sequence<Throwable> {
    val seenCauses = mutableSetOf<Throwable>()
    return generateSequence(this) { cause }.takeWhile { seenCauses.add(it) }
}
