package org.opentaint.ir.util.io

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets

/**
 * Writes unsigned quantity as a
 * [variable-length bytes sequence](https://en.wikipedia.org/wiki/Variable-length_quantity).
 *
 * @param quantity unsigned value to write
 */
fun OutputStream.writeVlqUnsigned(quantity: Long) {
    check(quantity >= 0) { "Unsigned value is excepted: $quantity" }
    var l = quantity
    while (true) {
        val c = (l and 0x7fL).toInt()
        l = l shr 7
        if (l == 0L) {
            write(c)
            break
        }
        write(c + 0x80)
    }
}

fun OutputStream.writeVlqUnsigned(quantity: Int) = writeVlqUnsigned(quantity.toLong())

/**
 * Writes a UTF-8 nullable string.
 *
 * @param str string to write to the stream
 */
fun OutputStream.writeString(str: String?) {
    if (str == null) {
        writeVlqUnsigned(0)
    } else {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        writeVlqUnsigned((bytes.size + 1).toLong())
        write(bytes)
    }
}

/**
 * Writes non-decreasing ordered iterable of unsigned longs.
 * Each next long should be greater than or equal to the previous one.
 */
fun OutputStream.writeUnsignedOrderedLongs(longs: Iterable<Long>) {
    var prev = 0L
    longs.forEach {
        check(it >= prev) { "Next long ($it) is less than prev ($prev)" }
        writeVlqUnsigned(it - prev + 1L)
        prev = it
    }
    writeVlqUnsigned(0L) // zero termination
}

/**
 * Writes `ByteBuffer` storing its length.
 * Additionally, it checks that all bytes written, so it won't work with somewhat
 * non-blocking or asynchronous streams.
 */
fun OutputStream.writeByteBuffer(buffer: ByteBuffer) {
    val copy = buffer.duplicate()
    val bytes = copy.remaining()
    writeVlqUnsigned(bytes)
    check(Channels.newChannel(this).write(copy) == bytes) {
        "All bytes of ByteBuffer are expected to be written"
    }
}