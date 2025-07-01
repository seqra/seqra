package org.opentaint.ir.impl.storage.ers.kv

import org.opentaint.ir.api.jvm.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.jvm.storage.ers.EntityRelationshipStorageSPI
import org.opentaint.ir.api.jvm.storage.ers.ErsSettings
import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorageSPI
import org.opentaint.ir.impl.JIRKvErsSettings

const val KV_ERS_SPI = "org.opentaint.ir.impl.storage.ers.kv.KVEntityRelationshipStorageSPI"

/**
 * Service provider interface for creating instances of [org.opentaint.ir.api.storage.ers.EntityRelationshipStorage]
 * running atop of an instance of [org.opentaint.ir.api.storage.kv.PluggableKeyValueStorage] identified by its id.
 */
class KVEntityRelationshipStorageSPI : EntityRelationshipStorageSPI {

    override val id = KV_ERS_SPI

    override fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage {
        settings as JIRKvErsSettings
        val kvSpi = PluggableKeyValueStorageSPI.getProvider(settings.kvId)
        val kvStorage = kvSpi.newStorage(persistenceLocation, settings)
        kvStorage.isMapWithKeyDuplicates = { mapName -> mapName.isMapWithKeyDuplicates }
        return KVEntityRelationshipStorage(kvStorage)
    }
}