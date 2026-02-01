package org.opentaint.jvm.sast.util

import kotlin.time.Duration
import kotlin.time.TimeSource

fun <T> List<T>.asSequenceWithProgress(
    rate: Duration,
    message: (Int, Int) -> Unit
): Sequence<T> {
    var taken = 0

    var nextMessage = TimeSource.Monotonic.markNow() + rate

    val iterator = this.iterator()
    val iteratorWithProgress = object : Iterator<T> {
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): T {
            taken++

            if (nextMessage.hasPassedNow()) {
                message(taken, size)
                nextMessage = TimeSource.Monotonic.markNow() + rate
            }

            return iterator.next()
        }

    }

    return Sequence { iteratorWithProgress }
}
