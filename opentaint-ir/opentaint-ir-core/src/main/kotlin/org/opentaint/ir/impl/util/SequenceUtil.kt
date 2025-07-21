package org.opentaint.ir.impl.util

inline fun <T> Sequence(crossinline it: () -> Iterable<T>): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> = it().iterator()
}