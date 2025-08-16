package org.opentaint.ir.impl.storage.kv.rocks

import jetbrains.exodus.util.LightByteArrayOutputStream

object ByteArrayPairUtils {

    fun makePair(first: ByteArray, second: ByteArray): ByteArray {
        val encodedFirst = encodeFirst(first)
        val outputStream = LightByteArrayOutputStream(encodedFirst.size + second.size + 1)

        outputStream.write(encodedFirst)
        outputStream.write(0)
        outputStream.write(second)

        return outputStream.toByteArray()
    }

    fun getFirst(bytes: ByteArray): ByteArray = decodeFirst(bytes.copyOfRange(0, getSeparatorIndex(bytes)))
    fun getSecond(bytes: ByteArray): ByteArray = bytes.copyOfRange(getSeparatorIndex(bytes) + 1, bytes.size)

    private fun getSeparatorIndex(bytes: ByteArray): Int {
        return bytes.indexOf(0.toByte()).also {
            require(it != -1) { "Missing separator, can't split byte array: ${bytes.contentToString()}" }
        }
    }

    private fun encodeFirst(originalFirst: ByteArray): ByteArray {
        val encodedFirst = ByteArray(
            originalFirst.size + originalFirst.count { it == 0.toByte() || it == 1.toByte() }
        )
        var outputIndex = 0

        originalFirst.forEach { byte ->
            when (byte) {
                0.toByte() -> {
                    encodedFirst[outputIndex++] = 1.toByte()
                    encodedFirst[outputIndex++] = 2.toByte()
                }

                1.toByte() -> {
                    encodedFirst[outputIndex++] = 1.toByte()
                    encodedFirst[outputIndex++] = 3.toByte()
                }

                else -> {
                    encodedFirst[outputIndex++] = byte
                }
            }
        }

        return encodedFirst
    }

    private fun decodeFirst(encodedFirst: ByteArray): ByteArray {
        val decoded = ByteArray(encodedFirst.size - encodedFirst.count { it == 1.toByte() })
        var decodeIndex = 0
        var i = 0
        while (i < encodedFirst.size) {
            if (encodedFirst[i] == 1.toByte()) {
                i++
                when (encodedFirst[i++]) {
                    2.toByte() -> decoded[decodeIndex++] = 0.toByte()
                    3.toByte() -> decoded[decodeIndex++] = 1.toByte()
                }
            } else {
                decoded[decodeIndex++] = encodedFirst[i++]
            }
        }

        return decoded
    }
}
