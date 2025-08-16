package org.opentaint.ir.testing.storage.ers

import org.opentaint.ir.impl.storage.ers.ram.RAM_ERS_SPI

class RAMEntityRelationshipStorageTest : EntityRelationshipStorageTest() {
    override val ersId: String
        get() = RAM_ERS_SPI
}
