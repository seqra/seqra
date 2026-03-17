package org.opentaint.ir.testing.storage.kv

import org.opentaint.ir.impl.storage.kv.rocks.ROCKS_KEY_VALUE_STORAGE_SPI

class RocksKeyValueStorageTest : PluggableKeyValueStorageTest() {

    override val kvStorageId = ROCKS_KEY_VALUE_STORAGE_SPI
}
