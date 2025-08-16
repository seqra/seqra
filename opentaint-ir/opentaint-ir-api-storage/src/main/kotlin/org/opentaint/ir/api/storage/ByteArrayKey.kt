package org.opentaint.ir.api.storage

import kotlin.math.min

class ByteArrayKey(val bytes: ByteArray) : Comparable<ByteArrayKey> {

    override fun compareTo(other: ByteArrayKey): Int {
        val a = bytes
        val b = other.bytes
        if (a === b) return 0
        for (i in 0 until min(a.size, b.size)) {
            val cmp = (a[i].toInt() and 0xff).compareTo(b[i].toInt() and 0xff)
            if (cmp != 0) return cmp
        }
        return a.size - b.size
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is ByteArrayKey && bytes contentEquals other.bytes

    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = bytes.contentToString()
}

fun ByteArray.asComparable() = ByteArrayKey(this)