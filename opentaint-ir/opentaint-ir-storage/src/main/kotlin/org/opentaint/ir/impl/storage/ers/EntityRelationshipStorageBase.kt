package org.opentaint.ir.impl.storage.ers

import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.impl.storage.ers.ram.toImmutable

abstract class EntityRelationshipStorageBase : EntityRelationshipStorage {

    override fun asImmutable(databaseId: String): EntityRelationshipStorage = toImmutable()
}