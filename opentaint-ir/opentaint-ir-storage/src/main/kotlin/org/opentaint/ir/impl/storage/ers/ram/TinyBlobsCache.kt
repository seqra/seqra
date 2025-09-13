package org.opentaint.ir.impl.storage.ers.ram

internal fun ByteArray.probablyCached(): ByteArray {
    return if (size == 1) {
        theCache[this[0].toInt() and 0xff]
    } else {
        this
    }
}

private val theCache = Array(256) { i ->
    byteArrayOf(i.toByte())
}