package org.opentaint.ir.impl.storage.ers

import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRDatabasePersistence
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorageSPI
import org.opentaint.ir.impl.JIRDatabaseImpl
import org.opentaint.ir.impl.JIRDatabasePersistenceSPI
import org.opentaint.ir.impl.JIRErsSettings
import org.opentaint.ir.impl.JIRSettings
import org.opentaint.ir.impl.LocationsRegistry
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.storage.PersistentLocationsRegistry

const val ERS_DATABASE_PERSISTENCE_SPI = "org.opentaint.ir.impl.storage.ers.ErsDatabasePersistenceSPI"

class ErsDatabasePersistenceSPI : JIRDatabasePersistenceSPI {

    override val id = ERS_DATABASE_PERSISTENCE_SPI

    override fun newPersistence(runtime: JavaRuntime, settings: JIRSettings): JIRDatabasePersistence {
        val persistenceSettings = settings.persistenceSettings
        val jIRErsSettings = persistenceSettings.implSettings as JIRErsSettings
        return ErsPersistenceImpl(
            javaRuntime = runtime,
            clearOnStart = settings.persistenceClearOnStart ?: false,
            ers = EntityRelationshipStorageSPI.getProvider(jIRErsSettings.ersId).newStorage(
                persistenceLocation = settings.persistenceSettings.persistenceLocation,
                settings = jIRErsSettings.ersSettings
            )
        )
    }

    override fun newLocationsRegistry(jIRdb: JIRDatabase): LocationsRegistry {
        return PersistentLocationsRegistry(jIRdb as JIRDatabaseImpl)
    }
}