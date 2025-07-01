package org.opentaint.ir.impl.util

val String.interned: String get() = StringInterner.intern(this)

object StringInterner {

    private val internerSize = Integer.getInteger("org.opentaint.ir.impl.util.internerSize", 131072).also {
        if ((it and (it - 1)) != 0) {
            throw IllegalArgumentException("Interner size must be a power of 2")
        }
    }
    private val mask = internerSize - 1
    private val strings = Array(internerSize) { "" }

    fun intern(s: String): String {
        val i = s.hashCode().let { h -> h xor (h shr 16) } and mask
        val interned = strings[i]
        if (s == interned) {
            return interned
        }
        return s.also { strings[i] = s }
    }
}