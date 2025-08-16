package org.opentaint.ir.impl

import org.opentaint.ir.api.jvm.JIRPersistenceImplSettings
import org.opentaint.ir.api.storage.ers.EmptyErsSettings
import org.opentaint.ir.api.storage.ers.ErsSettings
import org.opentaint.ir.impl.storage.SQLITE_DATABASE_PERSISTENCE_SPI
import org.opentaint.ir.impl.storage.ers.ERS_DATABASE_PERSISTENCE_SPI
import org.opentaint.ir.impl.storage.ers.kv.KV_ERS_SPI
import org.opentaint.ir.impl.storage.ers.ram.RAM_ERS_SPI
import org.opentaint.ir.impl.storage.ers.sql.SQL_ERS_SPI
import org.opentaint.ir.impl.storage.kv.rocks.ROCKS_KEY_VALUE_STORAGE_SPI
import org.opentaint.ir.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI

object JIRSQLitePersistenceSettings : JIRPersistenceImplSettings {
    override val persistenceId: String
        get() = SQLITE_DATABASE_PERSISTENCE_SPI
}

open class JIRErsSettings(
    val ersId: String,
    val ersSettings: ErsSettings = EmptyErsSettings
) : JIRPersistenceImplSettings {

    override val persistenceId: String
        get() = ERS_DATABASE_PERSISTENCE_SPI
}

object JIRRamErsSettings : JIRErsSettings(RAM_ERS_SPI, RamErsSettings())

object JIRSqlErsSettings : JIRErsSettings(SQL_ERS_SPI)

object JIRXodusKvErsSettings : JIRErsSettings(KV_ERS_SPI, JIRKvErsSettings(XODUS_KEY_VALUE_STORAGE_SPI))

object JIRRocksKvErsSettings : JIRErsSettings(KV_ERS_SPI, JIRKvErsSettings(ROCKS_KEY_VALUE_STORAGE_SPI))

object JIRLmdbKvErsSettings : JIRErsSettings(KV_ERS_SPI, JIRLmdbErsSettings()) {

    fun withMapSize(mapSize: Long): JIRErsSettings {
        return JIRErsSettings(KV_ERS_SPI, JIRLmdbErsSettings(mapSize))
    }
}