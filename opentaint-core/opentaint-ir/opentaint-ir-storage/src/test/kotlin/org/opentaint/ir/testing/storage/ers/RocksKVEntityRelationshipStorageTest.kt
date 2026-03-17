package org.opentaint.ir.testing.storage.ers

import org.opentaint.ir.impl.JIRKvErsSettings
import org.opentaint.ir.impl.storage.ers.kv.KV_ERS_SPI
import org.opentaint.ir.impl.storage.kv.rocks.ROCKS_KEY_VALUE_STORAGE_SPI

class RocksKVEntityRelationshipStorageTest : EntityRelationshipStorageTest() {

    override val ersSettings = JIRKvErsSettings(ROCKS_KEY_VALUE_STORAGE_SPI)

    override val ersId = KV_ERS_SPI
}
