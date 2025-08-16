package org.opentaint.ir.testing.storage.kv

import jetbrains.exodus.env.ReadonlyTransactionException
import org.opentaint.ir.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class XodusKeyValueStorageTest : PluggableKeyValueStorageTest() {

    override val kvStorageId = XODUS_KEY_VALUE_STORAGE_SPI

    @Test
    fun `read-only storage throws ReadonlyTransactionException`() {
        storage.readonly = true
        assertThrows(ReadonlyTransactionException::class.java) {
            storage.transactional {
                it.put("a map", "key".asByteArray, "value".asByteArray)
            }
        }
        storage.readonly = false
        storage.transactional {
            it.put("a map", "key".asByteArray, "value".asByteArray)
        }
    }
}