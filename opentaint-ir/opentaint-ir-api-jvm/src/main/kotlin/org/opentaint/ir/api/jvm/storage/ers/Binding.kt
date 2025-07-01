package org.opentaint.ir.api.jvm.storage.ers

interface Binding<T : Any> {

    val withCompression: Boolean get() = false

    fun getBytes(obj: T): ByteArray

    fun getObject(bytes: ByteArray, offset: Int): T

    fun getObject(bytes: ByteArray): T = getObject(bytes, 0)

    fun getBytesCompressed(obj: T): ByteArray = getBytes(obj)

    fun getObjectCompressed(bytes: ByteArray, offset: Int) = getObject(bytes, offset)

    fun getObjectCompressed(bytes: ByteArray) = getObjectCompressed(bytes, 0)
}
