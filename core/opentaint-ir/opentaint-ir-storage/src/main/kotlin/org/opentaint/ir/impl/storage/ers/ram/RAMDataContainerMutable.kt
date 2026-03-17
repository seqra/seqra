package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.filterInstanceIds
import org.opentaint.ir.api.storage.ers.longRangeIterable
import org.opentaint.ir.util.ByteArrayBuilder
import org.opentaint.ir.util.collections.EmptySparseBitSet
import org.opentaint.ir.util.collections.SparseBitSet

internal class RAMDataContainerMutable(
    private var typeIdCounter: Int, // next free type id
    private var types: TransactionalPersistentMap<String, Int>, // types
    private var instances: TransactionalPersistentMap<Int, Entities>, // type id -> Entities
    private var properties: TransactionalPersistentMap<AttributeKey, PropertiesMutable>, // (typeId, propName) -> Properties
    private var links: TransactionalPersistentMap<AttributeKey, LinksMutable>, // (typeId, linkName) -> // Links
    private var blobs: TransactionalPersistentMap<AttributeKey, TransactionalPersistentLongMap<ByteArray>>, // (typeId, linkName) -> blobs
    override val isMutable: Boolean = false
) : RAMDataContainer {

    private val mutableEntities = hashMapOf<Int, Entities>()
    private val mutableBlobs = hashMapOf<AttributeKey, TransactionalPersistentLongMap<ByteArray>>()

    constructor() : this(
        0,
        TransactionalPersistentMap(),
        TransactionalPersistentMap(),
        TransactionalPersistentMap(),
        TransactionalPersistentMap(),
        TransactionalPersistentMap(),
    )

    override fun mutate(): RAMDataContainer {
        return if (isMutable) this
        else RAMDataContainerMutable(
            typeIdCounter,
            types.getClone(),
            instances.getClone(),
            properties.getClone(),
            links.getClone(),
            blobs.getClone(),
            isMutable = true
        )
    }

    override fun commit(): RAMDataContainer {
        return if (!isMutable) {
            this
        } else {
            mutableEntities.forEach { (typeId, entities) ->
                instances.put(typeId, entities.commit())
            }
            mutableEntities.clear()
            mutableBlobs.forEach { (attributeKey, blobs) ->
                this.blobs.put(attributeKey, blobs.commit())
            }
            mutableBlobs.clear()
            RAMDataContainerMutable(
                typeIdCounter,
                types.commit(),
                instances.commit(),
                properties.commit(),
                links.commit(),
                blobs.commit()
            )
        }
    }

    override fun toImmutable(): RAMDataContainerImmutable {
        val types = this.types.entries().associate { it.key to it.value }
        val instances = arrayOfNulls<Pair<Long, SparseBitSet>>(this.instances.keys().max() + 1)
        this.instances.entries().forEach { entry ->
            val entities = entry.value
            instances[entry.key] = entities.instanceIdCounter to
                    if (entities.deleted.isEmpty()) EmptySparseBitSet else SparseBitSet().apply {
                        entities.deleted.forEach { deletedId ->
                            set(deletedId)
                        }
                    }
        }
        val builder = ByteArrayBuilder()
        val properties = HashMap<AttributeKey, PropertiesImmutable>().also { map ->
            this.properties.entries().forEach { entry ->
                map[entry.key] = entry.value.toImmutable(builder)
            }
        }
        val links = HashMap<AttributeKey, LinksImmutable>().also { map ->
            this.links.entries().forEach { entry ->
                map[entry.key] = entry.value.toImmutable(builder)
            }
        }
        val blobs = HashMap<AttributeKey, AttributesImmutable>().also { map ->
            this.blobs.entries().forEach { entry ->
                map[entry.key] = entry.value.entries().toAttributesImmutable(builder)
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

    override fun entityExists(id: EntityId): Boolean {
        val entities = instances[id.typeId] ?: return false
        return !entities.deleted.contains(id.instanceId)
    }

    override fun getTypeId(type: String): Int = types[type] ?: -1

    override fun getEntityTypes(): Map<String, Int> = types.entries().associate { it.key to it.value }

    override fun getOrAllocateTypeId(type: String): Pair<RAMDataContainer, Int> {
        types[type]?.let { id ->
            return this to id
        }
        val id = typeIdCounter
        return withMutableCopy {
            types.put(type, id)
            typeIdCounter = id + 1
        } to id
    }

    override fun allocateInstanceId(typeId: Int): Pair<RAMDataContainer, Long> {
        val entities = instances[typeId] ?: Entities()
        val id = entities.instanceIdCounter
        return entities.withMutableEntities(typeId) {
            instanceIdCounter = id + 1
        } to id
    }

    override fun getPropertyNames(type: String): Set<String> = getAttributeNames(type, properties.getClone().keys())

    override fun getBlobNames(type: String): Set<String> = getAttributeNames(type, blobs.getClone().keys())

    override fun getLinkNames(type: String): Set<String> = getAttributeNames(type, links.getClone().keys())

    override fun all(txn: RAMTransaction, type: String): EntityIterable {
        val typeId = types[type] ?: return EntityIterable.EMPTY
        val entities = instances[typeId] ?: return EntityIterable.EMPTY
        val entitiesCount = entities.instanceIdCounter
        if (entitiesCount == 0L) {
            return EntityIterable.EMPTY
        }
        return if (entities.deleted.isEmpty()) {
            longRangeIterable(txn, typeId, 0 until entitiesCount)
        } else {
            longRangeIterable(txn, typeId, 0 until entitiesCount) {
                it !in entities.deleted
            }
        }
    }

    override fun deleteEntity(id: EntityId): RAMDataContainer {
        val typeId = id.typeId
        val entities = instances[typeId] ?: return this
        return entities.withMutableEntities(typeId) {
            deleted = deleted.add(id.instanceId)
        }
    }

    override fun getRawProperty(id: EntityId, propertyName: String): ByteArray? {
        val typeId = id.typeId
        return properties[typeId withField propertyName]?.let { it.props[id.instanceId] }
    }

    override fun setRawProperty(id: EntityId, propertyName: String, value: ByteArray?): RAMDataContainer {
        val propertiesKey = id.typeId withField propertyName
        // if value == null we are to delete the property
        val newProperties = if (value == null) {
            val properties = properties[propertiesKey] ?: return this
            properties.deleteProperty(id.instanceId) ?: return this
        } else {
            val properties = properties[propertiesKey] ?: PropertiesMutable()
            properties.setProperty(id.instanceId, value) ?: return this
        }
        return withMutableCopy {
            properties.put(propertiesKey, newProperties)
        }
    }

    override fun getEntitiesWithPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesWithValue(txn, typeId, value)
        }
    }

    override fun getEntitiesLtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesLtValue(txn, typeId, value)
        }
    }

    override fun getEntitiesEqOrLtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesEqOrLtValue(txn, typeId, value)
        }
    }

    override fun getEntitiesGtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesGtValue(txn, typeId, value)
        }
    }

    override fun getEntitiesEqOrGtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesEqOrGtValue(txn, typeId, value)
        }
    }

    override fun getBlob(id: EntityId, blobName: String): ByteArray? {
        val typeId = id.typeId
        return blobs[typeId withField blobName]?.let { it[id.instanceId] }
    }

    override fun setBlob(id: EntityId, blobName: String, value: ByteArray?): RAMDataContainer {
        val blobsKey = id.typeId withField blobName
        // if value == null we are to delete the blob
        return if (value == null) {
            val blobs = blobs[blobsKey] ?: return this
            blobs.withMutableBlobs(blobsKey) {
                remove(id.instanceId)
            }
        } else {
            val blobs = blobs[blobsKey] ?: TransactionalPersistentLongMap()
            blobs.withMutableBlobs(blobsKey) {
                put(id.instanceId, value.probablyCached())
            }
        }
    }

    override fun getLinks(txn: RAMTransaction, id: EntityId, linkName: String): EntityIterable {
        val typeId = id.typeId
        val links = links[typeId withField linkName] ?: return EntityIterable.EMPTY
        return links.getLinks(txn, id.instanceId).filterDeleted(typeId)
    }

    override fun addLink(id: EntityId, linkName: String, targetId: EntityId): RAMDataContainer {
        val typeId = id.typeId
        val linksKey = typeId withField linkName
        val links = links[linksKey] ?: LinksMutable()
        val newLinks = links.addLink(id.instanceId, targetId)
        return if (newLinks === links) {
            this
        } else {
            withMutableCopy {
                this.links.put(linksKey, newLinks)
            }
        }
    }

    override fun deleteLink(id: EntityId, linkName: String, targetId: EntityId): RAMDataContainer {
        val typeId = id.typeId
        val linksKey = typeId withField linkName
        val links = links[linksKey] ?: return this
        val newLinks = links.deleteLink(id.instanceId, targetId)
        return if (newLinks === links) {
            this
        } else {
            withMutableCopy {
                this.links.put(linksKey, newLinks)
            }
        }
    }

    private fun getEntitiesWithPropertyFunction(
        type: String,
        propertyName: String,
        f: PropertiesMutable.(Int) -> Pair<PropertiesMutable?, EntityIterable>
    ): Pair<RAMDataContainer?, EntityIterable> {
        val typeId = types[type] ?: return null to EntityIterable.EMPTY
        val propertiesKey = typeId withField propertyName
        val properties = properties[propertiesKey] ?: return null to EntityIterable.EMPTY
        val (newProperties, result) = properties.f(typeId)
        newProperties?.let {
            return withMutableCopy {
                this.properties.put(propertiesKey, newProperties)
            } to result.filterDeleted(typeId)
        }
        return null to result.filterDeleted(typeId)
    }

    private fun EntityIterable.filterDeleted(typeId: Int): EntityIterable {
        if (this === EntityIterable.EMPTY) {
            return this
        }
        val instances = this@RAMDataContainerMutable.instances[typeId] ?: return EntityIterable.EMPTY
        return if (instances.deleted.isEmpty()) {
            this
        } else {
            filterInstanceIds { instanceId -> instanceId !in instances.deleted }
        }
    }

    private fun withMutableCopy(action: RAMDataContainerMutable.() -> Unit): RAMDataContainer {
        return if (isMutable) {
            this
        } else {
            mutate() as RAMDataContainerMutable
        }.apply(action)
    }

    private fun Entities.withMutableEntities(typeId: Int, action: Entities.() -> Unit): RAMDataContainer {
        return if (isMutable) {
            action()
            this@RAMDataContainerMutable
        } else mutate().let {
            it.action()
            withMutableCopy {
                mutableEntities[typeId] = it
                instances.put(typeId, it)
            }
        }
    }

    private fun TransactionalPersistentLongMap<ByteArray>.withMutableBlobs(
        key: AttributeKey,
        action: TransactionalPersistentLongMap<ByteArray>.() -> Unit
    ): RAMDataContainer {
        return if (isMutable) {
            action()
            this@RAMDataContainerMutable
        } else mutate().let {
            it.action()
            withMutableCopy {
                mutableBlobs[key] = it
                blobs.put(key, it)
            }
        }
    }
}

internal data class AttributeKey(val typeId: Int, val name: String) : Comparable<AttributeKey> {

    override fun compareTo(other: AttributeKey): Int {
        return typeId.compareTo(other.typeId).let {
            if (it == 0) name.compareTo(other.name) else it
        }
    }
}

internal fun RAMDataContainer.getAttributeNames(type: String, keys: Iterable<AttributeKey>): Set<String> =
    getTypeId(type).let { typeId ->
        if (typeId >= 0L) {
            keys.mapNotNullTo(sortedSetOf()) { if (it.typeId == typeId) it.name else null }
        } else {
            emptySet()
        }
    }

internal infix fun Int.withField(name: String): AttributeKey = AttributeKey(this, name)

internal class Entities(
    var instanceIdCounter: Long = 0L,
    var deleted: CompactPersistentLongSet = CompactPersistentLongSet(),
    override val isMutable: Boolean = false
) : MutableContainer<Entities> {

    override fun mutate(): Entities {
        return if (isMutable) this else Entities(instanceIdCounter, deleted, isMutable = true)
    }

    override fun commit(): Entities {
        return if (!isMutable) this else Entities(instanceIdCounter, deleted)
    }
}

