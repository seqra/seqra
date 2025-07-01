package org.opentaint.ir.impl.storage.ers.sql

import org.opentaint.ir.api.jvm.storage.ers.ErsSettings
import org.opentaint.ir.api.jvm.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.jvm.storage.ers.EntityRelationshipStorageSPI
import org.opentaint.ir.impl.storage.configuredSQLiteDataSource
import org.opentaint.ir.impl.storage.ers.BuiltInBindingProvider

const val SQL_ERS_SPI = "org.opentaint.ir.impl.storage.ers.sql.SQLEntityRelationshipStorageSPI"

class SqlEntityRelationshipStorageSPI : EntityRelationshipStorageSPI {

    override val id = SQL_ERS_SPI

    override fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage =
        SqlEntityRelationshipStorage(
            dataSource = configuredSQLiteDataSource(location = persistenceLocation),
            bindingProvider = BuiltInBindingProvider
        )
}
