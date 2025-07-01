package org.opentaint.ir.api.jvm.storage.kv

import java.io.Closeable

interface Transaction : Closeable {

    val storage: PluggableKeyValueStorage

    val isReadonly: Boolean

    val isFinished: Boolean

    fun getNamedMap(name: String): NamedMap

    fun get(map: String, key: ByteArray): ByteArray? = get(getNamedMap(map), key)

    fun get(map: NamedMap, key: ByteArray): ByteArray?

    fun put(map: String, key: ByteArray, value: ByteArray): Boolean = put(getNamedMap(map), key, value)

    fun put(map: NamedMap, key: ByteArray, value: ByteArray): Boolean

    fun delete(map: String, key: ByteArray): Boolean = delete(getNamedMap(map), key)

    fun delete(map: NamedMap, key: ByteArray): Boolean

    fun delete(map: String, key: ByteArray, value: ByteArray): Boolean = delete(getNamedMap(map), key, value)

    fun delete(map: NamedMap, key: ByteArray, value: ByteArray): Boolean

    fun navigateTo(map: String, key: ByteArray? = null): Cursor = navigateTo(getNamedMap(map), key)

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

object EmptyNamedMap : NamedMap {

    override val name = "EmptyNamedMap"

    override fun size(txn: Transaction) = 0L
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
