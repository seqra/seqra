package org.opentaint.ir.impl.storage.kv.rocks

import org.opentaint.ir.api.jvm.storage.kv.Cursor
import org.opentaint.ir.api.jvm.storage.kv.EmptyCursor
import org.opentaint.ir.api.jvm.storage.kv.withFirstMovePrevSkipped
import org.opentaint.ir.api.jvm.storage.kv.withFirstMoveSkipped
import org.rocksdb.RocksIterator

internal fun seekNoDuplicateKeyCursor(txn: RocksTransaction, iterator: RocksIterator, key: ByteArray): Cursor {
    val result = RocksCursor(txn, iterator)
    iterator.seek(key)
    if (iterator.isValid) {
        return result.withFirstMoveSkipped()
    }
    iterator.seekToLast()
    return if (iterator.isValid) result.withFirstMovePrevSkipped() else EmptyCursor(txn)
}

internal fun seekDuplicateKeyCursor(txn: RocksTransaction, iterator: RocksIterator, key: ByteArray): Cursor {
    val result = RocksCursor(txn, iterator).decodeDuplicateKeys()
    iterator.seek(ByteArrayPairUtils.makePair(key, ByteArray(0)))
    if (iterator.isValid) {
        return result.withFirstMoveSkipped()
    }
    iterator.seekToLast()
    return if (iterator.isValid) result.withFirstMovePrevSkipped() else EmptyCursor(txn)

}

internal fun seekFirstOrLastCursor(txn: RocksTransaction, iterator: RocksIterator): Cursor {
    return FirstSeekCursor(RocksCursor(txn, iterator))
}

private class RocksCursor(
    override val txn: RocksTransaction,
    val iterator: RocksIterator
) : Cursor {

    override fun moveNext(): Boolean {
        iterator.next()
        val valid = iterator.isValid
        if (!valid) close()
        return valid
    }

    override fun movePrev(): Boolean {
        iterator.prev()
        val valid = iterator.isValid
        if (!valid) close()
        return valid
    }

    override val key: ByteArray get() = iterator.key()

    override val value: ByteArray get() = iterator.value()

    override fun close() = iterator.close()
}

internal fun Cursor.decodeDuplicateKeys(): Cursor = DecodingDuplicateKeysCursorDecorator(this)

private class DecodingDuplicateKeysCursorDecorator(
    private val decorated: Cursor
) : Cursor by decorated {
    override val key: ByteArray
        get() = ByteArrayPairUtils.getFirst(decorated.key)

    override val value: ByteArray
        get() = ByteArrayPairUtils.getSecond(decorated.key)
}

private class FirstSeekCursor(private val decorated: RocksCursor) : Cursor by decorated {

    private var firstMove = true

    override fun moveNext(): Boolean {
        if (firstMove) {
            firstMove = false
            return decorated.iterator.let {
                it.seekToFirst()
                it.isValid
            }
        }
        return decorated.moveNext()
    }

    override fun movePrev(): Boolean {
        if (firstMove) {
            firstMove = false
            return decorated.iterator.let {
                it.seekToLast()
                it.isValid
            }
        }
        return decorated.movePrev()
    }
}