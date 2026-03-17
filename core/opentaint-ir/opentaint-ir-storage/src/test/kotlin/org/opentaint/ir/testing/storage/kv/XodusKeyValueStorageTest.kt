package org.opentaint.ir.testing.storage.kv

import jetbrains.exodus.io.DataReaderWriterProvider
import org.opentaint.ir.impl.JIRXodusErsSettings
import org.opentaint.ir.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.Long.getLong

class XodusKeyValueStorageTest : PluggableKeyValueStorageTest() {

    override val kvStorageId = XODUS_KEY_VALUE_STORAGE_SPI

    @Test
    fun `test shared usage of the same db`() {
        val settings = JIRXodusErsSettings {
            logDataReaderWriterProvider = DataReaderWriterProvider.WATCHING_READER_WRITER_PROVIDER
        }
        val roStorage = kvStorageSpi.newStorage(location = location, settings = settings)
        roStorage.transactional { txn ->
            assertTrue(txn.isReadonly)
            assertNull(txn.get("a map", "key".asByteArray))
        }
        putGet()
        Thread.sleep(getLong("jetbrains.exodus.io.watching.forceCheckEach", 3000L) + 500)
        roStorage.transactional { txn ->
            val got = txn.get("a map", "key".asByteArray)
            assertNotNull(got)
            assertEquals("value", got?.asString)
        }
    }
}