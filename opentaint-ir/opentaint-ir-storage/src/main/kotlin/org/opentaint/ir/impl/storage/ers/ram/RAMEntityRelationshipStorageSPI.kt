package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorageSPI
import org.opentaint.ir.api.storage.ers.ErsSettings
import org.opentaint.ir.impl.RamErsSettings

const val RAM_ERS_SPI = "org.opentaint.ir.impl.storage.ers.ram.RAMEntityRelationshipStorageSPI"

class RAMEntityRelationshipStorageSPI : EntityRelationshipStorageSPI {

    override val id = RAM_ERS_SPI

    override fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage {
        require(persistenceLocation == null) { "RAM ERS can't be persisted" }
        require(settings is RamErsSettings) { "RamErsSettings is expected" }
        return RAMEntityRelationshipStorage(settings = settings)
    }
}