package org.opentaint.ir.impl.storage.kv.lmdb

import java.nio.ByteBuffer

val ByteArray.asByteBuffer: ByteBuffer
    get() = ByteBuffer.allocateDirect(size).also { buffer ->
        forEach { b ->
            buffer.put(b)
        }
    }.flip() as ByteBuffer

val ByteBuffer.asArray: ByteArray
    get() = ByteArray(limit()).also { array ->
        repeat(array.size) { i ->
            array[i] = get()
        }
        flip()
    }