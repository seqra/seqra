package org.opentaint.ir.impl.storage.kv.xodus

import org.opentaint.ir.api.jvm.storage.ers.ErsSettings
import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorage
import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorageSPI
import kotlin.io.path.createTempDirectory

const val XODUS_KEY_VALUE_STORAGE_SPI = "org.opentaint.ir.impl.storage.kv.xodus.XodusKeyValueStorageSPI"

class XodusKeyValueStorageSPI : PluggableKeyValueStorageSPI {

    override val id = XODUS_KEY_VALUE_STORAGE_SPI

    override fun newStorage(location: String?, settings: ErsSettings): PluggableKeyValueStorage =
        XodusKeyValueStorage(location ?: createTempDirectory(prefix = "xodusKeyValueStorage").toString())
}