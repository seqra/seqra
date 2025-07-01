package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.impl.JIRDatabaseImpl
import org.opentaint.ir.impl.JIRDatabasePersistenceSPI
import org.opentaint.ir.impl.JIRSettings
import org.opentaint.ir.impl.LocationsRegistry
import org.opentaint.ir.impl.fs.JavaRuntime

const val SQLITE_DATABASE_PERSISTENCE_SPI = "org.opentaint.ir.impl.storage.SQLiteDatabasePersistenceSPI"

class SQLiteDatabasePersistenceSPI : JIRDatabasePersistenceSPI {

    override val id = SQLITE_DATABASE_PERSISTENCE_SPI

    override fun newPersistence(runtime: JavaRuntime, settings: JIRSettings): JIRDatabasePersistence {
        return SQLitePersistenceImpl(
            javaRuntime = runtime,
            location = settings.persistenceLocation,
            clearOnStart = settings.persistenceClearOnStart ?: false
        )
    }

    override fun newLocationsRegistry(jIRdb: JIRDatabase): LocationsRegistry {
        return PersistentLocationsRegistry(jIRdb as JIRDatabaseImpl)
    }
}