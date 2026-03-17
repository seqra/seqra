package org.opentaint.ir.testing.storage.kv

import org.opentaint.ir.impl.storage.kv.lmdb.LMDB_KEY_VALUE_STORAGE_SPI

class LmdbKeyValueStorageTest : PluggableKeyValueStorageTest() {

    override val kvStorageId = LMDB_KEY_VALUE_STORAGE_SPI
}