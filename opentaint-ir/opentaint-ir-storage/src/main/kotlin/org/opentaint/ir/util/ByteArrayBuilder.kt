package org.opentaint.ir.util

import kotlin.math.max

class ByteArrayBuilder(initialCapacity: Int = 1024) {

    private var buffer = ByteArray(initialCapacity)
    private var count = 0

    fun append(data: ByteArray): ByteArrayBuilder {
        val len = data.size
        ensureCapacity(count + len)
        System.arraycopy(data, 0, buffer, count, len)
        count += len
        return this
    }

    fun toByteArray(): ByteArray {
        return if (buffer.size == count) buffer else buffer.copyOf(count)
    }

    private fun ensureCapacity(minCapacity: Int) {
        val capacity = buffer.size
        if (capacity < minCapacity) {
            buffer = buffer.copyOf(max(minCapacity, capacity * 2))
        }
    }
}