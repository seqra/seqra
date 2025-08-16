package org.opentaint.ir.testing.storage.kv

import org.opentaint.ir.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI

class XodusKeyValueStorageTest : PluggableKeyValueStorageTest() {

    override val kvStorageId = XODUS_KEY_VALUE_STORAGE_SPI
}