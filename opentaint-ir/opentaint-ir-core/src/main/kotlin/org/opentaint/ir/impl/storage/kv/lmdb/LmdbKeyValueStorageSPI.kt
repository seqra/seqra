package org.opentaint.ir.impl.storage.kv.lmdb

import org.opentaint.ir.api.jvm.storage.ers.ErsSettings
import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorage
import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorageSPI
import org.opentaint.ir.impl.JIRLmdbErsSettings
import kotlin.io.path.createTempDirectory

const val LMDB_KEY_VALUE_STORAGE_SPI = "org.opentaint.ir.impl.storage.kv.lmdb.LmdbKeyValueStorageSPI"

class LmdbKeyValueStorageSPI : PluggableKeyValueStorageSPI {

    override val id = LMDB_KEY_VALUE_STORAGE_SPI

    override fun newStorage(location: String?, settings: ErsSettings): PluggableKeyValueStorage {
        return LmdbKeyValueStorage(
            location ?: createTempDirectory(prefix = "lmdbKeyValueStorage").toString(),
            if (settings is JIRLmdbErsSettings) settings else JIRLmdbErsSettings()
        )
    }
}