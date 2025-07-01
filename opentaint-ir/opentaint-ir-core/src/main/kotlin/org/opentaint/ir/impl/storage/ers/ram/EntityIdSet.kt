package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.jvm.storage.ers.EntityId

internal class EntityIdSet(
    private var typeId: Int = -1,
    private val instances: CompactPersistentLongSet = CompactPersistentLongSet()
) {

    val size: Int get() = instances.size

    val isEmpty: Boolean get() = instances.isEmpty()

    fun toList(): List<EntityId> {
        return instances.map { EntityId(typeId, it) }
    }

    fun add(id: EntityId): EntityIdSet {
        val typeId = checkTypeId(id)
        val newInstances = instances.add(id.instanceId)
        return if (newInstances === instances) this else EntityIdSet(typeId, newInstances)
    }

    fun remove(id: EntityId): EntityIdSet {
        val typeId = checkTypeId(id)
        val newInstances = instances.remove(id.instanceId)
        return if (newInstances === instances) this else EntityIdSet(typeId, newInstances)
    }

    private fun checkTypeId(id: EntityId): Int {
        val typeId = id.typeId
        if (this.typeId != -1 && this.typeId != typeId) {
            throw IllegalStateException("EntityIdSet can only store ids of the same typeId")
        }
        return typeId
    }
}