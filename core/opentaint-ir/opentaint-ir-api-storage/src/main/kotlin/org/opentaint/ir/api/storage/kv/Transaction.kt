package org.opentaint.ir.api.storage.kv

import java.io.Closeable

interface Transaction : Closeable {

    val storage: PluggableKeyValueStorage

    val isReadonly: Boolean

    val isFinished: Boolean

    fun getNamedMap(name: String, create: Boolean = false): NamedMap?

    fun getMapNames(): Set<String>

    fun get(map: String, key: ByteArray): ByteArray? = getNamedMap(map, create = false)?.let { get(it, key) }

    fun get(map: NamedMap, key: ByteArray): ByteArray?

    fun put(map: String, key: ByteArray, value: ByteArray): Boolean = put(getNamedMap(map, create = true)!!, key, value)

    fun put(map: NamedMap, key: ByteArray, value: ByteArray): Boolean

    fun delete(map: String, key: ByteArray): Boolean = getNamedMap(map, create = false)?.let { delete(it, key) } == true

    fun delete(map: NamedMap, key: ByteArray): Boolean

    fun delete(map: String, key: ByteArray, value: ByteArray): Boolean =
        getNamedMap(map, create = false)?.let { delete(it, key, value) } == true

    fun delete(map: NamedMap, key: ByteArray, value: ByteArray): Boolean

    fun navigateTo(map: String, key: ByteArray? = null): Cursor =
        getNamedMap(map, create = false)?.let { navigateTo(it, key) } ?: EmptyCursor(this)

    fun navigateTo(map: NamedMap, key: ByteArray? = null): Cursor

    fun commit(): Boolean

    fun abort()

    override fun close() {
        if (isReadonly) abort() else commit()
    }
}

interface NamedMap {

    val name: String

    fun size(txn: Transaction): Long
}

abstract class TransactionDecorator(val decorated: Transaction) : Transaction by decorated

fun Transaction.withFinishedState(): Transaction = WithFinishedCheckingTxn(this)

private class WithFinishedCheckingTxn(decorated: Transaction) : TransactionDecorator(decorated) {

    private var isFinishedFlag = false

    override val isFinished: Boolean get() = isFinishedFlag || decorated.isFinished

    override fun commit(): Boolean {
        if (isFinished) {
            return false
        }
        isFinishedFlag = true
        return decorated.commit()
    }

    override fun abort() {
        if (!isFinished) {
            isFinishedFlag = true
            decorated.abort()
        }
    }
}
