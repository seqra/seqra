package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.longRangeIterable
import org.opentaint.ir.util.collections.SparseBitSet
import org.opentaint.ir.util.io.readString
import org.opentaint.ir.util.io.readUnsignedOrderedLongs
import org.opentaint.ir.util.io.readVlqUnsigned
import org.opentaint.ir.util.io.writeString
import org.opentaint.ir.util.io.writeUnsignedOrderedLongs
import org.opentaint.ir.util.io.writeVlqUnsigned
import java.io.InputStream
import java.io.OutputStream

internal class RAMDataContainerImmutable(
    // map of entity types to their type ids
    private val types: Map<String, Int>,
    // arrays of instance info by typeId.
    // typeId is an index in the array, pair contains next free instance id and bit set of ids of deleted entities.
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

    override fun getEntityTypes(): Map<String, Int> = types

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

    fun dump(output: OutputStream) {
        // save types
        output.writeVlqUnsigned(types.size)
        types.forEach { entry ->
            output.writeString(entry.key)
            output.writeVlqUnsigned(entry.value)
        }

        // save instance counters & deleted
        output.writeVlqUnsigned(instances.size)
        instances.forEachIndexed { i, pair ->
            pair?.let { (freeInstanceId, deleted) ->
                output.writeVlqUnsigned(i)
                output.writeVlqUnsigned(freeInstanceId)
                output.writeUnsignedOrderedLongs(deleted)
            }
        }
        // marks end of instances since instances.size is not an index in the array
        output.writeVlqUnsigned(instances.size)

        // save properties
        output.writeVlqUnsigned(properties.size)
        properties.forEach { entry ->
            output.writeAttributeKey(entry.key)
            entry.value.dump(output)
        }

        //save links
        output.writeVlqUnsigned(links.size)
        links.forEach { entry ->
            output.writeAttributeKey(entry.key)
            entry.value.dump(output)
        }

        //save blobs
        output.writeVlqUnsigned(blobs.size)
        blobs.forEach { entry ->
            output.writeAttributeKey(entry.key)
            entry.value.dump(output)
        }
    }

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

internal fun InputStream.readRAMDataContainerImmutable(): RAMDataContainerImmutable {
    // load types
    val typeCount = readVlqUnsigned().toInt()
    val types = HashMap<String, Int>(typeCount).also { map ->
        repeat(typeCount) {
            map[requireNotNull(readString())] = readVlqUnsigned().toInt()
        }
    }
    // load instance counters & deleted
    val instances: Array<Pair<Long, SparseBitSet>?> = arrayOfNulls(readVlqUnsigned().toInt())
    while (true) {
        val index = readVlqUnsigned().toInt()
        if (index !in instances.indices) break
        val freeInstanceId = readVlqUnsigned()
        val deleted = SparseBitSet(readUnsignedOrderedLongs())
        instances[index] = Pair(freeInstanceId, deleted)
    }
    // load properties
    val propertiesCount = readVlqUnsigned().toInt()
    val properties = HashMap<AttributeKey, PropertiesImmutable>(propertiesCount).also { map ->
        repeat(propertiesCount) {
            val key = AttributeKey(typeId = readVlqUnsigned().toInt(), name = requireNotNull(readString()))
            val value = readPropertiesImmutable()
            map[key] = value
        }
    }
    // load links
    val linksCount = readVlqUnsigned().toInt()
    val links = HashMap<AttributeKey, LinksImmutable>(linksCount).also { map ->
        repeat(linksCount) {
            val key = AttributeKey(typeId = readVlqUnsigned().toInt(), name = requireNotNull(readString()))
            val value = readLinksImmutable()
            map[key] = value
        }
    }
    // load blobs
    val blobsCount = readVlqUnsigned().toInt()
    val blobs = HashMap<AttributeKey, AttributesImmutable>(blobsCount).also { map ->
        repeat(blobsCount) {
            val key = AttributeKey(typeId = readVlqUnsigned().toInt(), name = requireNotNull(readString()))
            val value = readAttributesImmutable()
            map[key] = value
        }
    }

    return RAMDataContainerImmutable(
        types = types,
        instances = instances,
        properties = properties,
        links = links,
        blobs = blobs
    )
}

private fun OutputStream.writeAttributeKey(key: AttributeKey) {
    writeVlqUnsigned(key.typeId)
    writeString(key.name)
}