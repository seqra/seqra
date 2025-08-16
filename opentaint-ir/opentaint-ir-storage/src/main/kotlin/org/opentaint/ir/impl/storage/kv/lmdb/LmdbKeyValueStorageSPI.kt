package org.opentaint.ir.impl.storage.kv.lmdb

import org.opentaint.ir.api.storage.ers.ErsSettings
import org.opentaint.ir.api.storage.kv.PluggableKeyValueStorage
import org.opentaint.ir.api.storage.kv.PluggableKeyValueStorageSPI
import kotlin.io.path.createTempDirectory

const val LMDB_KEY_VALUE_STORAGE_SPI = "org.opentaint.ir.impl.storage.kv.lmdb.LmdbKeyValueStorageSPI"

class LmdbKeyValueStorageSPI : PluggableKeyValueStorageSPI {

    override val id = LMDB_KEY_VALUE_STORAGE_SPI

    override fun newStorage(location: String?, settings: ErsSettings): PluggableKeyValueStorage {
        return LmdbKeyValueStorage(
            location ?: createTempDirectory(prefix = "lmdbKeyValueStorage").toString(),
            if (settings is org.opentaint.ir.impl.JIRLmdbErsSettings) settings else org.opentaint.ir.impl.JIRLmdbErsSettings()
        )
    }
}