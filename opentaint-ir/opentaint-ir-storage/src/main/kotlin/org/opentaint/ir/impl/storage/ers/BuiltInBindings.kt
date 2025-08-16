package org.opentaint.ir.impl.storage.ers

import org.opentaint.ir.api.storage.ers.Binding
import org.opentaint.ir.api.storage.ers.BindingProvider
import org.opentaint.ir.api.storage.ers.ERSException
import kotlin.experimental.and

object BuiltInBindingProvider : BindingProvider {
    override fun <T : Any> getBinding(clazz: Class<T>): Binding<T> =
        clazz.getBinding()
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> Class<T>.getBinding(): Binding<T> = (when {
    this.isEnum -> EnumBinding(this as Class<Enum<*>>)
    else -> (builtInBindings.firstOrNull { this in it.classes })
} ?: throw ERSException("There is no binding for property values of class $this")) as Binding<T>

fun <T : Any> getBinding(obj: T): Binding<T> = obj.javaClass.getBinding()

private val builtInBindings: Array<BuiltInBinding<*>> = arrayOf(
    ByteArrayBinding, // trivial binding with no conversion to make it possible to deal with ByteArray properties
    StringBinding,    // UTF-8 strings
    IntegerBinding,   // 4-byte signed integers
    LongBinding,      // 8-byte signed integers (longs)
    DoubleBinging,    // 8-byte floating point numbers (doubles)
    BooleanBinding    // boolean values
)

private abstract class BuiltInBinding<T : Any> : Binding<T> {

    abstract val classes: Set<Class<*>>
}

private object ByteArrayBinding : BuiltInBinding<ByteArray>() {

    override val classes = setOf(ByteArray::class.java)
    override fun getObject(bytes: ByteArray, offset: Int): ByteArray =
        if (offset == 0) bytes else bytes.copyOfRange(offset, bytes.size)

    override fun getBytes(obj: ByteArray): ByteArray = obj
}

private object StringBinding : BuiltInBinding<String>() {

    override val classes = setOf(String::class.java)

    override fun getBytes(obj: String): ByteArray {
        return obj.encodeToByteArray()
    }

    override fun getObject(bytes: ByteArray, offset: Int): String {
        return String(bytes = bytes, offset = offset, length = bytes.size - offset, charset = Charsets.UTF_8)
    }
}

private object IntegerBinding : BuiltInBinding<Int>() {

    override val classes = setOf(Int::class.java, Integer::class.java)

    override fun getBytes(obj: Int): ByteArray {
        if (obj in cachedBytes.indices) {
            return cachedBytes[obj]
        }
        return getBytesUncached(obj)
    }

    override fun getObject(bytes: ByteArray, offset: Int): Int {
        val i0 = bytes[offset].toInt() and 0xff
        val i1 = bytes[offset + 1].toInt() and 0xff
        val i2 = bytes[offset + 2].toInt() and 0xff
        val i3 = bytes[offset + 3].toInt() and 0xff
        return ((i0 shl 24) or (i1 shl 16) or (i2 shl 8) or i3) xor Int.MIN_VALUE
    }

    override fun getBytesCompressed(obj: Int): ByteArray {
        if (obj in cachedBytesCompressed.indices) {
            return cachedBytesCompressed[obj]
        }
        return getBytesCompressedUncached(obj)
    }

    override fun getObjectCompressed(bytes: ByteArray, offset: Int): Int {
        var result = 0
        for (i in offset until bytes.size) {
            val nextByte = bytes[i].toInt()
            result = (result shl 7) or (nextByte and 0x7f)
            if ((nextByte and 0x80) == 0) {
                break
            }
        }
        return result
    }

    private fun getBytesUncached(obj: Int): ByteArray = ByteArray(Int.SIZE_BYTES).also { bytes ->
        // inverse sign bit
        (obj xor Int.MIN_VALUE).let { i ->
            bytes[0] = (i shr 24).toByte()
            bytes[1] = (i shr 16).toByte()
            bytes[2] = (i shr 8).toByte()
            bytes[3] = i.toByte()
        }
    }

    private fun getBytesCompressedUncached(obj: Int): ByteArray {
        if (obj < 0) {
            throw IllegalArgumentException("LongBinding.getBytesCompressed() cannot be applied to a negative value: $obj")
        }
        val resultSize = when {
            obj < 128 -> 1
            obj < 128 * 128 -> 2
            obj < 128 * 128 * 128 -> 3
            else -> 5 // 5 bytes is enough to save maximum 31 bits - by 7 bits for each byte
        }
        val temp = ByteArray(resultSize)
        var bytesCount = 0
        var l = obj
        while (true) {
            temp[bytesCount++] = ((l and 0x7f) + 0x80).toByte()
            l = l shr 7
            if (l == 0) {
                break
            }
        }
        // reverse bytes in temp and adjust final byte
        reverseAndAdjust(temp, bytesCount)
        return if (bytesCount == resultSize) temp else temp.copyOfRange(0, bytesCount)
    }

    private val cachedBytes = Array(1024) { i ->
        getBytesUncached(i)
    }
    private val cachedBytesCompressed = Array(16384) { i ->
        getBytesCompressedUncached(i)
    }
}

private object LongBinding : BuiltInBinding<Long>() {

    override val classes = setOf(Long::class.java, java.lang.Long::class.java)

    override fun getBytes(obj: Long): ByteArray {
        if (obj in cachedBytes.indices) {
            return cachedBytes[obj.toInt()]
        }
        return getBytesUncached(obj)
    }

    override fun getObject(bytes: ByteArray, offset: Int): Long {
        val i0 = bytes[offset].toLong() and 0xff
        val i1 = bytes[offset + 1].toLong() and 0xff
        val i2 = bytes[offset + 2].toLong() and 0xff
        val i3 = bytes[offset + 3].toLong() and 0xff
        val i4 = bytes[offset + 4].toLong() and 0xff
        val i5 = bytes[offset + 5].toLong() and 0xff
        val i6 = bytes[offset + 6].toLong() and 0xff
        val i7 = bytes[offset + 7].toLong() and 0xff
        return ((i0 shl 56) or (i1 shl 48) or (i2 shl 40) or (i3 shl 32) or
                (i4 shl 24) or (i5 shl 16) or (i6 shl 8) or i7) xor Long.MIN_VALUE
    }

    override fun getBytesCompressed(obj: Long): ByteArray {
        if (obj in cachedBytesCompressed.indices) {
            return cachedBytesCompressed[obj.toInt()]
        }
        return getBytesCompressedUncached(obj)
    }

    override fun getObjectCompressed(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in offset until bytes.size) {
            val nextByte = bytes[i].toLong()
            result = (result shl 7) or (nextByte and 0x7fL)
            if ((nextByte and 0x80L) == 0L) {
                break
            }
        }
        return result
    }

    private fun getBytesUncached(obj: Long): ByteArray = ByteArray(Long.SIZE_BYTES).also { bytes ->
        // inverse sign bit
        (obj xor Long.MIN_VALUE).let { i ->
            bytes[0] = (i shr 56).toByte()
            bytes[1] = (i shr 48).toByte()
            bytes[2] = (i shr 40).toByte()
            bytes[3] = (i shr 32).toByte()
            bytes[4] = (i shr 24).toByte()
            bytes[5] = (i shr 16).toByte()
            bytes[6] = (i shr 8).toByte()
            bytes[7] = i.toByte()
        }
    }

    private fun getBytesCompressedUncached(obj: Long): ByteArray {
        if (obj < 0L) {
            throw IllegalArgumentException("LongBinding.getBytesCompressed() cannot be applied to a negative value: $obj")
        }
        val resultSize = when {
            obj < 128L -> 1
            obj < 128L * 128L -> 2
            obj < 128L * 128L * 128L -> 3
            else -> 9 // 9 bytes is enough to save maximum 63 bits - by 7 bits for each byte
        }
        val temp = ByteArray(resultSize)
        var bytesCount = 0
        var l = obj
        while (true) {
            temp[bytesCount++] = ((l and 0x7f) + 0x80).toByte()
            l = l shr 7
            if (l == 0L) {
                break
            }
        }
        // reverse bytes in temp and adjust final byte
        reverseAndAdjust(temp, bytesCount)
        return if (bytesCount == resultSize) temp else temp.copyOfRange(0, bytesCount)
    }

    private val cachedBytes = Array(16384) { i ->
        getBytesUncached(i.toLong())
    }
    private val cachedBytesCompressed = Array(16384) { i ->
        getBytesCompressedUncached(i.toLong())
    }
}

private object DoubleBinging : BuiltInBinding<Double>() {

    override val classes: Set<Class<*>> get() = setOf(Double::class.java, java.lang.Double::class.java)

    override fun getBytes(obj: Double): ByteArray = LongBinding.getBytes(java.lang.Double.doubleToLongBits(obj))

    override fun getObject(bytes: ByteArray, offset: Int): Double =
        java.lang.Double.longBitsToDouble(LongBinding.getObject(bytes, offset))

}

private object BooleanBinding : BuiltInBinding<Boolean>() {

    override val classes = setOf(Boolean::class.java, java.lang.Boolean::class.java)

    override fun getBytes(obj: Boolean): ByteArray {
        return if (obj) TRUE_ARRAY else FALSE_ARRAY
    }

    override fun getObject(bytes: ByteArray, offset: Int): Boolean {
        return bytes[offset] != 0.toByte()
    }

    private val FALSE_ARRAY = byteArrayOf(0)
    private val TRUE_ARRAY = byteArrayOf(1)
}

private class EnumBinding<E : Enum<*>>(val clazz: Class<E>) : Binding<E> {
    override fun getBytes(obj: E): ByteArray = IntegerBinding.getBytes(obj.ordinal)

    override fun getObject(bytes: ByteArray, offset: Int): E =
        clazz.enumConstants[IntegerBinding.getObject(bytes)]
}

private fun reverseAndAdjust(a: ByteArray, size: Int) {
    for (i in 0 until size / 2) {
        val t = a[i]
        a[i] = a[size - i - 1]
        a[size - i - 1] = t
    }
    a[size - 1] = a[size - 1] and 0x7f
}