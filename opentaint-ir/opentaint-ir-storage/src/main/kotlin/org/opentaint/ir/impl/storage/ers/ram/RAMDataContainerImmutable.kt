package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.longRangeIterable
import org.opentaint.ir.util.collections.SparseBitSet

internal class RAMDataContainerImmutable(
    // map of entity types to their type ids
    private val types: Map<String, Int>,
    // arrays of instance info by typeId
    // typeId is an index in the array, pair contains next free instance id and bit set of ids of deleted entities
    private val instances: Array<Pair<Long, SparseBitSet>?>,
    // (typeId, propName) -> PropertiesImmutable
    private var properties: Map<AttributeKey, PropertiesImmutable>,
    // (typeId, linkName) -> // LinksImmutable
    private var links: Map<AttributeKey, LinksImmutable>,
    // (typeId, linkName) -> blobs
    private var blobs: Map<AttributeKey, AttributesImmutable>
) : RAMDataContainer {

    override val isMutable = false

    override fun mutate() = throwError("cannot mutate")

    override fun commit(): RAMDataContainer = this

    override fun toImmutable(): RAMDataContainer = this

    override fun entityExists(id: EntityId): Boolean {
        val typeId = id.typeId
        return typeId in instances.indices && true == instances[typeId]?.let { (nextFreeInstance, deleted) ->
            val instanceId = id.instanceId
            instanceId < nextFreeInstance && !deleted.contains(instanceId)
        }
    }

    override fun getTypeId(type: String): Int = types[type] ?: -1

    override fun getOrAllocateTypeId(type: String): Pair<RAMDataContainer, Int> {
        return getTypeId(type).let {
            if (it >= 0L) this to it else cantModify()
        }
    }

    override fun allocateInstanceId(typeId: Int): Pair<RAMDataContainer, Long> = cantModify()

    override fun getPropertyNames(type: String): Set<String> = getAttributeNames(type, properties.keys)

    override fun getBlobNames(type: String): Set<String> = getAttributeNames(type, blobs.keys)

    override fun getLinkNames(type: String): Set<String> = getAttributeNames(type, links.keys)

    override fun all(txn: RAMTransaction, type: String): EntityIterable {
        val typeId = types[type] ?: return EntityIterable.EMPTY
        val (nextFreeInstanceId, deleted) = instances[typeId] ?: return EntityIterable.EMPTY
        return if (deleted.isEmpty) {
            longRangeIterable(txn, typeId, 0 until nextFreeInstanceId)
        } else {
            longRangeIterable(txn, typeId, 0 until nextFreeInstanceId) {
                !deleted.contains(it)
            }
        }
    }

    override fun deleteEntity(id: EntityId): RAMDataContainer = cantModify()

    override fun getRawProperty(id: EntityId, propertyName: String): ByteArray? {
        val typeId = id.typeId
        return properties[typeId withField propertyName]?.let { it[id.instanceId] }
    }

    override fun setRawProperty(id: EntityId, propertyName: String, value: ByteArray?): RAMDataContainer = cantModify()

    override fun getEntitiesWithPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return null to getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesWithValue(txn, typeId, value)
        }
    }

    override fun getEntitiesLtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return null to getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesLtValue(txn, typeId, value)
        }
    }

    override fun getEntitiesEqOrLtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return null to getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesEqOrLtValue(txn, typeId, value)
        }
    }

    override fun getEntitiesGtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return null to getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesGtValue(txn, typeId, value)
        }
    }

    override fun getEntitiesEqOrGtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return null to getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesEqOrGtValue(txn, typeId, value)
        }
    }

    override fun getBlob(id: EntityId, blobName: String): ByteArray? {
        val typeId = id.typeId
        return blobs[typeId withField blobName]?.let { it[id.instanceId] }
    }

    override fun setBlob(id: EntityId, blobName: String, value: ByteArray?): RAMDataContainer = cantModify()

    override fun getLinks(
        txn: RAMTransaction,
        id: EntityId,
        linkName: String
    ): EntityIterable {
        val typeId = id.typeId
        val links = links[typeId withField linkName] ?: return EntityIterable.EMPTY
        return links.getLinks(txn, id.instanceId)
    }

    override fun addLink(id: EntityId, linkName: String, targetId: EntityId): RAMDataContainer = cantModify()

    override fun deleteLink(id: EntityId, linkName: String, targetId: EntityId): RAMDataContainer = cantModify()

    private fun getEntitiesWithPropertyFunction(
        type: String,
        propertyName: String,
        f: PropertiesImmutable.(Int) -> EntityIterable
    ): EntityIterable {
        val typeId = types[type] ?: return EntityIterable.EMPTY
        val properties = properties[typeId withField propertyName] ?: return EntityIterable.EMPTY
        return properties.f(typeId)
    }

    private fun cantModify(): Nothing = throwError("cannot modify")

    private fun throwError(msg: String): Nothing = error("RAMDataContainerImmutable: $msg")
}