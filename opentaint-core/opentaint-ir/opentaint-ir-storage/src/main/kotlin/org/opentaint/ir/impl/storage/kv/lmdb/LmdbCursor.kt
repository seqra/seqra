package org.opentaint.ir.impl.storage.kv.lmdb

import org.opentaint.ir.api.storage.kv.Cursor
import org.opentaint.ir.api.storage.kv.Transaction
import java.nio.ByteBuffer

class LmdbCursor(
    override val txn: Transaction,
    private val cursor: org.lmdbjava.Cursor<ByteBuffer>
) : Cursor {

    override fun moveNext(): Boolean = cursor.next()

    override fun movePrev(): Boolean = cursor.prev()

    override val key: ByteArray get() = cursor.key().asArray

    override val value: ByteArray
        get() = cursor.`val`().asArray

    override fun close() {
        cursor.close()
    }
}