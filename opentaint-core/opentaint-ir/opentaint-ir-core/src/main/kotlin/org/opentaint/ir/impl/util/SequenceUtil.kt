package org.opentaint.ir.impl.util

import java.util.*

inline fun <T> Sequence(crossinline it: () -> Iterable<T>): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> = it().iterator()
}

fun <T> Enumeration<T>?.asSequence(): Sequence<T> {
    if (this == null) return emptySequence()
    return object : Sequence<T> {
        override fun iterator(): Iterator<T> = object : Iterator<T> {
            override fun hasNext() = this@asSequence.hasMoreElements()
            override fun next(): T = this@asSequence.nextElement()
        }
    }
}