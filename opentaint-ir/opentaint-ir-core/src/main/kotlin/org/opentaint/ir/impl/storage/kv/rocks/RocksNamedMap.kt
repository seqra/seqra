package org.opentaint.ir.impl.storage.kv.rocks

import org.opentaint.ir.api.jvm.storage.kv.Cursor
import org.opentaint.ir.api.jvm.storage.kv.EmptyCursor
import org.opentaint.ir.api.jvm.storage.kv.NamedMap
import org.opentaint.ir.api.jvm.storage.kv.Transaction
import org.opentaint.ir.api.jvm.storage.kv.TransactionDecorator
import org.opentaint.ir.impl.storage.ers.BuiltInBindingProvider
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.RocksIterator

internal interface RocksNamedMap : NamedMap {
    fun get(txn: RocksTransaction, key: ByteArray): ByteArray?
    fun put(txn: RocksTransaction, key: ByteArray, value: ByteArray): Boolean
    fun delete(txn: RocksTransaction, key: ByteArray): Boolean
    fun delete(txn: RocksTransaction, key: ByteArray, value: ByteArray): Boolean
    fun navigateTo(txn: RocksTransaction, key: ByteArray? = null): Cursor
}

internal class LazyRocksNamedMap(
    override val name: String,
    private val storage: RocksKeyValueStorage,
) : RocksNamedMap {
    private var cached: RocksNamedMap? = null

    private fun getDecorated(txn: Transaction): RocksNamedMap? {
        if (cached == null) {
            cached = if (txn.isReadonly) storage.getNamedMapOrNull(name) else storage.getOrCreateNamedMap(name)
        }
        return cached
    }

    override fun size(txn: Transaction): Long = getDecorated(txn)?.size(txn) ?: 0L
    override fun get(txn: RocksTransaction, key: ByteArray): ByteArray? = getDecorated(txn)?.get(txn, key)

    override fun put(txn: RocksTransaction, key: ByteArray, value: ByteArray): Boolean =
        getDecorated(txn)?.put(txn, key, value) ?: error("Can perform `put()` in a readonly transaction")

    override fun delete(txn: RocksTransaction, key: ByteArray): Boolean = getDecorated(txn)?.delete(txn, key) ?: false
    override fun delete(txn: RocksTransaction, key: ByteArray, value: ByteArray): Boolean =
        getDecorated(txn)?.delete(txn, key, value) ?: false

    override fun navigateTo(txn: RocksTransaction, key: ByteArray?): Cursor =
        getDecorated(txn)?.navigateTo(txn, key) ?: EmptyCursor(txn)
}

internal abstract class NonEmptyRocksNamedMap(private val columnFamilyHandle: ColumnFamilyHandle) : RocksNamedMap {

    private val columnFamilyHandleName by lazy { columnFamilyHandle.name }

    override val name: String
        get() = BuiltInBindingProvider.getBinding(String::class.java).getObject(columnFamilyHandleName)

    override fun size(txn: Transaction): Long {
        @Suppress("NAME_SHADOWING")
        var txn = txn
        while (txn is TransactionDecorator) {
            txn = txn.decorated
        }
        txn as RocksTransaction

        return txn.rawGet(txn.storage.sizesColumnFamily, columnFamilyHandleName)
            ?.let { BuiltInBindingProvider.getBinding(Long::class.java).getObjectCompressed(it) }
            ?: 0L
    }

    protected fun updateSize(txn: RocksTransaction, delta: Long) {
        if (delta == 0L) {
            return
        }
        return txn.rawPut(
            txn.storage.sizesColumnFamily,
            columnFamilyHandleName,
            BuiltInBindingProvider.getBinding(Long::class.java).getBytesCompressed(size(txn) + delta)
        )
    }

    /// region methods that do NOT encode duplicate keys
    protected fun RocksTransaction.rawGet(key: ByteArray): ByteArray? = rawGet(columnFamilyHandle, key)
    protected fun RocksTransaction.rawPut(key: ByteArray, value: ByteArray) = rawPut(columnFamilyHandle, key, value)
    protected fun RocksTransaction.rawDelete(key: ByteArray) = rawDelete(columnFamilyHandle, key)
    protected fun RocksTransaction.rawGetIterator(): RocksIterator = rawGetIterator(columnFamilyHandle)
    /// endregion
}

internal class NoDuplicateRocksNamedMap(
    columnFamilyHandle: ColumnFamilyHandle
) : NonEmptyRocksNamedMap(columnFamilyHandle) {
    override fun get(txn: RocksTransaction, key: ByteArray): ByteArray? {
        return txn.rawGet(key)
    }

    override fun put(txn: RocksTransaction, key: ByteArray, value: ByteArray): Boolean {
        val previous = txn.rawGet(key)
        if (previous?.contentEquals(value) == true) {
            return false
        }
        txn.rawPut(key, value)
        if (previous == null) {
            updateSize(txn, +1)
        }
        return true
    }

    override fun delete(txn: RocksTransaction, key: ByteArray): Boolean {
        get(txn, key) ?: return false
        txn.rawDelete(key)
        updateSize(txn, -1)
        return true
    }

    override fun delete(txn: RocksTransaction, key: ByteArray, value: ByteArray): Boolean {
        val previous = txn.rawGet(key)
        if (previous?.contentEquals(value) != true) {
            return false
        }
        txn.rawDelete(key)
        updateSize(txn, -1)
        return true
    }

    override fun navigateTo(txn: RocksTransaction, key: ByteArray?): Cursor {
        val iterator = txn.rawGetIterator()
        if (key == null) {
            return seekFirstOrLastCursor(txn, iterator)
        }
        return seekNoDuplicateKeyCursor(txn, iterator, key)
    }
}

internal class DuplicateRocksNamedMap(
    columnFamilyHandle: ColumnFamilyHandle
) : NonEmptyRocksNamedMap(columnFamilyHandle) {
    override fun get(txn: RocksTransaction, key: ByteArray): ByteArray? {
        return navigateTo(txn, key).use { cursor ->
            if (cursor.moveNext()) cursor.value else null
        }
    }

    override fun put(txn: RocksTransaction, key: ByteArray, value: ByteArray): Boolean {
        val entry = ByteArrayPairUtils.makePair(key, value)
        if (txn.rawGet(entry) != null) {
            return false
        }
        txn.rawPut(entry, ByteArray(0))
        updateSize(txn, +1)
        return true
    }

    override fun delete(txn: RocksTransaction, key: ByteArray): Boolean {
        val entriesToDelete = mutableListOf<ByteArray>()
        navigateTo(txn, key).use { cursor ->
            while (cursor.moveNext()) {
                entriesToDelete.add(ByteArrayPairUtils.makePair(cursor.key, cursor.value))
            }
        }
        entriesToDelete.forEach { entry -> txn.rawDelete(entry) }
        updateSize(txn, -entriesToDelete.size.toLong())
        return entriesToDelete.isNotEmpty()
    }

    override fun delete(txn: RocksTransaction, key: ByteArray, value: ByteArray): Boolean {
        val entry = ByteArrayPairUtils.makePair(key, value)
        txn.rawGet(entry) ?: return false
        txn.rawDelete(entry)
        updateSize(txn, -1)
        return true
    }

    override fun navigateTo(txn: RocksTransaction, key: ByteArray?): Cursor {
        val iterator = txn.rawGetIterator()
        if (key == null) {
            return seekFirstOrLastCursor(txn, iterator).decodeDuplicateKeys()
        }
        return seekDuplicateKeyCursor(txn, iterator, key)
    }
}
