package org.opentaint.ir.impl.storage.kv.xodus

import org.opentaint.ir.api.jvm.storage.kv.Cursor
import org.opentaint.ir.api.jvm.storage.kv.Transaction

internal class XodusCursor(
    override val txn: Transaction,
    private val cursor: jetbrains.exodus.env.Cursor
) : Cursor {

    private var isClosed: Boolean = false
    private var cachedKey: ByteArray? = null
    private var cachedValue: ByteArray? = null

    override fun moveNext(): Boolean = cursorChecked.next.also { invalidateCached() }

    override fun movePrev(): Boolean = cursorChecked.prev.also { invalidateCached() }

    override val key: ByteArray get() = cachedKey ?: cursorChecked.key.asByteArray.also { cachedKey = it }

    override val value: ByteArray get() = cachedValue ?: cursorChecked.value.asByteArray.also { cachedValue = it }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            cursor.close()
        }
    }

    internal fun invalidateCached() {
        cachedKey = null
        cachedValue = null
    }

    private val cursorChecked: jetbrains.exodus.env.Cursor
        get() = if (isClosed) throw IllegalStateException("Cursor is already closed") else cursor
}