package org.opentaint.ir.impl

import jetbrains.exodus.env.EnvironmentConfig
import org.opentaint.ir.api.storage.ers.ErsSettings
import org.opentaint.ir.impl.storage.kv.lmdb.LMDB_KEY_VALUE_STORAGE_SPI
import org.opentaint.ir.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI

class RamErsSettings(
    val immutableDumpsPath: String? = null
) : ErsSettings

/**
 * Id of pluggable K/V storage being passed for [org.opentaint.ir.impl.storage.ers.kv.KVEntityRelationshipStorageSPI].
 */
open class JIRKvErsSettings(val kvId: String) : ErsSettings

// by default, mapSize is 1Gb
class JIRLmdbErsSettings(val mapSize: Long = 0x40_00_00_00) : JIRKvErsSettings(LMDB_KEY_VALUE_STORAGE_SPI)

class JIRXodusErsSettings(val configurer: (EnvironmentConfig.() -> Unit)? = null) :
    JIRKvErsSettings(XODUS_KEY_VALUE_STORAGE_SPI)

