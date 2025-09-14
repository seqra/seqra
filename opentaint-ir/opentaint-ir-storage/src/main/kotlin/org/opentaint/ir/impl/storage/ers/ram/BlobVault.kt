package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.hash.IntHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object BlobVault {

    private val blobs = IntHashMap<MutableList<ByteArray>>()
    private val lock = ReentrantLock()

    internal fun ByteArray.asUnique(): ByteArray {
        val hc = this.contentHashCode()
        lock.withLock {
            val arrays = blobs[hc] ?: ArrayList(1)
            arrays.forEach { array ->
                if (this contentEquals array) {
                    return array
                }
            }
            arrays.add(this)
            if (arrays.size == 1) {
                blobs[hc] = arrays
            }
            return this
        }
    }
}