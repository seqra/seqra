package org.opentaint.ir.impl.storage.kv.xodus

import org.opentaint.ir.api.storage.ers.ErsSettings
import org.opentaint.ir.api.storage.kv.PluggableKeyValueStorage
import org.opentaint.ir.api.storage.kv.PluggableKeyValueStorageSPI
import org.opentaint.ir.impl.JIRXodusErsSettings
import kotlin.io.path.createTempDirectory

const val XODUS_KEY_VALUE_STORAGE_SPI = "org.opentaint.ir.impl.storage.kv.xodus.XodusKeyValueStorageSPI"

class XodusKeyValueStorageSPI : PluggableKeyValueStorageSPI {

    override val id = XODUS_KEY_VALUE_STORAGE_SPI

    override fun newStorage(location: String?, settings: ErsSettings): PluggableKeyValueStorage {
        val configurer = (settings as? JIRXodusErsSettings)?.configurer
        return XodusKeyValueStorage(
            location ?: createTempDirectory(prefix = "xodusKeyValueStorage").toString(),
            configurer
        )
    }
}