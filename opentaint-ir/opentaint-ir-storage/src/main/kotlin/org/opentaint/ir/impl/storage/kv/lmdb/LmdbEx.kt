package org.opentaint.ir.impl.storage.kv.lmdb

import java.nio.ByteBuffer

val ByteArray.asByteBuffer: ByteBuffer
    get() = ByteBuffer.allocateDirect(size).also { buffer ->
        buffer.put(this)
    }.flip() as ByteBuffer

val ByteBuffer.asArray: ByteArray
    get() = ByteArray(limit()).also { array ->
        get(array)
        flip()
    }