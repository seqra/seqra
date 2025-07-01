package org.opentaint.ir.testing.storage.ers

import org.opentaint.ir.impl.storage.ers.sql.SQL_ERS_SPI

class SqlEntityRelationshipStorageTest : EntityRelationshipStorageTest() {
    override val ersId: String
        get() = SQL_ERS_SPI
}
