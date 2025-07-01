package org.opentaint.ir.impl.storage.kv.rocks

import org.opentaint.ir.api.jvm.storage.ers.ErsSettings
import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorage
import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorageSPI
import kotlin.io.path.createTempDirectory

const val ROCKS_KEY_VALUE_STORAGE_SPI = "org.opentaint.ir.impl.storage.kv.rocks.RocksKeyValueStorageSPI"

class RocksKeyValueStorageSPI : PluggableKeyValueStorageSPI {

    override val id = ROCKS_KEY_VALUE_STORAGE_SPI

    override fun newStorage(location: String?, settings: ErsSettings): PluggableKeyValueStorage =
        RocksKeyValueStorageImpl(location ?: createTempDirectory(prefix = "rocksKeyValueStorage").toString())
}