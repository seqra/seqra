package org.opentaint.ir.impl

import org.opentaint.ir.api.storage.ers.ErsSettings
import org.opentaint.ir.impl.storage.kv.lmdb.LMDB_KEY_VALUE_STORAGE_SPI

/**
 * Id of pluggable K/V storage being passed for [org.opentaint.ir.impl.storage.ers.kv.KVEntityRelationshipStorageSPI].
 */
open class JIRKvErsSettings(val kvId: String) : ErsSettings

// by default, mapSize is 1Gb
class JIRLmdbErsSettings(val mapSize: Long = 0x40_00_00_00) : JIRKvErsSettings(LMDB_KEY_VALUE_STORAGE_SPI)

