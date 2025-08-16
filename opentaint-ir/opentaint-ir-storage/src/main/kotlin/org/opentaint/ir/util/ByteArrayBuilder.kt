package org.opentaint.ir.util

import java.nio.ByteBuffer
import kotlin.math.max

private const val maxCapacity = Int.MAX_VALUE - 8 // same as ArraysSupport.SOFT_MAX_ARRAY_LENGTH

class ByteArrayBuilder(initialCapacity: Int = 1024) {

    private var buffer = ByteArray(initialCapacity)
    private var count = 0

    fun reset() {
        count = 0
    }

    fun append(data: ByteArray): ByteArrayBuilder {
        val len = data.size
        ensureCapacity(count + len)
        System.arraycopy(data, 0, buffer, count, len)
        count += len
        return this
    }

    fun append(b: Byte): ByteArrayBuilder {
        ensureCapacity(count + 1)
        buffer[count++] = b
        return this
    }

    fun toByteArray(): ByteArray {
        return if (buffer.size == count) buffer else buffer.copyOf(count)
    }

    fun toByteBuffer(): ByteBuffer {
        return ByteBuffer.wrap(toByteArray())
    }

    private fun ensureCapacity(minCapacity: Int) {
        val capacity = buffer.size
        if (capacity < minCapacity) {
            var advancedCapacity = if (capacity < 0x100000) capacity * 2 else capacity / 34 * 55 /* phi */
            if (advancedCapacity < 0 || advancedCapacity > maxCapacity) {
                advancedCapacity = maxCapacity
            }
            buffer = buffer.copyOf(max(minCapacity, advancedCapacity))
        }
    }
}

inline fun ByteArrayBuilder.build(builderAction: ByteArrayBuilder.() -> Unit): ByteArray {
    reset()
    builderAction()
    return toByteArray()
}