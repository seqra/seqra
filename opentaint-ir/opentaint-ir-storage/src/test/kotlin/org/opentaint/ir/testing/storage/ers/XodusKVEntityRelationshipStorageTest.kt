package org.opentaint.ir.testing.storage.ers

import jetbrains.exodus.env.ReadonlyTransactionException
import org.opentaint.ir.impl.JIRKvErsSettings
import org.opentaint.ir.impl.storage.ers.kv.KV_ERS_SPI
import org.opentaint.ir.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class XodusKVEntityRelationshipStorageTest : EntityRelationshipStorageTest() {

    override val ersSettings = JIRKvErsSettings(XODUS_KEY_VALUE_STORAGE_SPI)

    override val ersId = KV_ERS_SPI

    @Test
    fun `read-only storage throws ReadonlyTransactionException`() {
        val storage = txn.ers
        txn.close()
        assertThrows(ReadonlyTransactionException::class.java) {
            storage.asReadonly.transactional(false) {
                it.newEntity("User")
            }
        }
    }
}