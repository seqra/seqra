package org.opentaint.ir.testing.storage.ers

import org.opentaint.ir.impl.JIRKvErsSettings
import org.opentaint.ir.impl.storage.ers.kv.KV_ERS_SPI
import org.opentaint.ir.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI

class XodusKVEntityRelationshipStorageTest : EntityRelationshipStorageTest() {

    override val ersSettings = JIRKvErsSettings(XODUS_KEY_VALUE_STORAGE_SPI)

    override val ersId = KV_ERS_SPI
}