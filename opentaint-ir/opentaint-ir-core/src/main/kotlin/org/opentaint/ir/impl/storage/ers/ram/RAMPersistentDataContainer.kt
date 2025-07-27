package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.jvm.storage.ers.CollectionEntityIterable
import org.opentaint.ir.api.jvm.storage.ers.Entity
import org.opentaint.ir.api.jvm.storage.ers.EntityId
import org.opentaint.ir.api.jvm.storage.ers.EntityIterable

internal class RAMPersistentDataContainer(
    private var typeIdCounter: Int, // next free type id
    private var types: TransactionalPersistentMap<String, Int>, // types
    private var instances: TransactionalPersistentMap<Int, Entities>, // type id -> Entities
    private var properties: TransactionalPersistentMap<AttributeKey, Properties>, // (typeId, propName) -> Properties
    private var links: TransactionalPersistentMap<AttributeKey, Links>, // (typeId, linkName) -> // Links
    private var blobs: TransactionalPersistentMap<AttributeKey, TransactionalPersistentLongMap<ByteArray>>, // (typeId, linkName) -> blobs
    override val isMutable: Boolean = false
) : MutableContainer<RAMPersistentDataContainer> {

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

    override fun mutate(): RAMPersistentDataContainer {
        return if (isMutable) this
        else RAMPersistentDataContainer(
            typeIdCounter,
            types.getClone(),
            instances.getClone(),
            properties.getClone(),
            links.getClone(),
            blobs.getClone(),
            isMutable = true
        )
    }

    override fun commit(): RAMPersistentDataContainer {
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
            RAMPersistentDataContainer(
                typeIdCounter,
                types.commit(),
                instances.commit(),
                properties.commit(),
                links.commit(),
                blobs.commit()
            )
        }
    }

    fun entityExists(id: EntityId): Boolean {
        val entities = instances[id.typeId] ?: return false
        return !entities.deleted.contains(id.instanceId)
    }

    fun getTypeId(type: String): Int = types[type] ?: -1

    fun getOrAllocateTypeId(type: String): Pair<RAMPersistentDataContainer, Int> {
        types[type]?.let { id ->
            return this to id
        }
        val id = typeIdCounter
        return withMutableCopy {
            types.put(type, id)
            typeIdCounter = id + 1
        } to id
    }

    fun allocateInstanceId(typeId: Int): Pair<RAMPersistentDataContainer, Long> {
        val entities = instances[typeId] ?: Entities()
        val id = entities.instanceIdCounter
        return entities.withMutableEntities(typeId) {
            instanceIdCounter = id + 1
        } to id
    }

    fun getPropertyNames(type: String): Set<String> = getAttributeNames(type, properties)

    fun getBlobNames(type: String): Set<String> = getAttributeNames(type, blobs)

    fun getLinkNames(type: String): Set<String> = getAttributeNames(type, links)

    fun all(txn: RAMTransaction, type: String): EntityIterable {
        val typeId = types[type] ?: return EntityIterable.EMPTY
        val entities = instances[typeId] ?: return EntityIterable.EMPTY
        val entitiesCount = entities.instanceIdCounter
        if (entitiesCount == 0L) {
            return EntityIterable.EMPTY
        }
        val result = if (entities.deleted.isEmpty()) {
            ArrayList<Entity>(entitiesCount.toInt()).apply {
                (0 until entitiesCount).forEach { instanceId ->
                    add(RAMEntity(txn, EntityId(typeId, instanceId)))
                }
            }
        } else {
            (0 until entitiesCount).mapNotNullTo(ArrayList<Entity>(entitiesCount.toInt())) { instanceId ->
                if (instanceId in entities.deleted) {
                    null
                } else {
                    RAMEntity(txn, EntityId(typeId, instanceId))
                }
            }
        }
        return CollectionEntityIterable(result)
    }

    fun deleteEntity(id: EntityId): RAMPersistentDataContainer {
        val typeId = id.typeId
        val entities = instances[typeId] ?: return this
        return entities.withMutableEntities(typeId) {
            deleted = deleted.add(id.instanceId)
        }
    }

    fun getRawProperty(id: EntityId, propertyName: String): ByteArray? {
        val typeId = id.typeId
        return properties[typeId withField propertyName]?.let { it.props[id.instanceId] }
    }

    fun setRawProperty(id: EntityId, propertyName: String, value: ByteArray?): RAMPersistentDataContainer {
        val propertiesKey = id.typeId withField propertyName
        // if value == null we are to delete the property
        val newProperties = if (value == null) {
            val properties = properties[propertiesKey] ?: return this
            properties.deleteProperty(id.instanceId) ?: return this
        } else {
            val properties = properties[propertiesKey] ?: Properties()
            properties.setProperty(id.instanceId, value) ?: return this
        }
        return withMutableCopy {
            properties.put(propertiesKey, newProperties)
        }
    }

    fun getEntitiesWithPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMPersistentDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesWithValue(txn, typeId, value)
        }
    }

    fun getEntitiesLtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMPersistentDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesLtValue(txn, typeId, value)
        }
    }

    fun getEntitiesEqOrLtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMPersistentDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesEqOrLtValue(txn, typeId, value)
        }
    }

    fun getEntitiesGtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMPersistentDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesGtValue(txn, typeId, value)
        }
    }

    fun getEntitiesEqOrGtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMPersistentDataContainer?, EntityIterable> {
        return getEntitiesWithPropertyFunction(type, propertyName) { typeId ->
            getEntitiesEqOrGtValue(txn, typeId, value)
        }
    }

    fun getBlob(id: EntityId, blobName: String): ByteArray? {
        val typeId = id.typeId
        return blobs[typeId withField blobName]?.let { it[id.instanceId] }
    }

    fun setBlob(id: EntityId, blobName: String, value: ByteArray?): RAMPersistentDataContainer {
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
                put(id.instanceId, value)
            }
        }
    }

    fun getLinks(txn: RAMTransaction, id: EntityId, linkName: String): EntityIterable {
        val typeId = id.typeId
        val links = links[typeId withField linkName] ?: return EntityIterable.EMPTY
        return links.getLinks(txn, id.instanceId).filterDeleted(typeId)
    }

    fun addLink(id: EntityId, linkName: String, targetId: EntityId): RAMPersistentDataContainer {
        val typeId = id.typeId
        val linksKey = typeId withField linkName
        val links = links[linksKey] ?: Links()
        val newLinks = links.addLink(id.instanceId, targetId)
        return if (newLinks === links) {
            this
        } else {
            withMutableCopy {
                this.links.put(linksKey, newLinks)
            }
        }
    }

    fun deleteLink(id: EntityId, linkName: String, targetId: EntityId): RAMPersistentDataContainer {
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
        f: Properties.(Int) -> Pair<Properties?, EntityIterable>
    ): Pair<RAMPersistentDataContainer?, EntityIterable> {
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
        val instances = this@RAMPersistentDataContainer.instances[typeId] ?: return EntityIterable.EMPTY
        return if (instances.deleted.isEmpty()) {
            this
        } else {
            CollectionEntityIterable(filterTo(toMutableSet()) { e -> e.id.instanceId !in instances.deleted })
        }
    }

    private fun withMutableCopy(action: RAMPersistentDataContainer.() -> Unit): RAMPersistentDataContainer {
        return if (isMutable) {
            this
        } else {
            mutate()
        }.apply(action)
    }

    private fun Entities.withMutableEntities(typeId: Int, action: Entities.() -> Unit): RAMPersistentDataContainer {
        return if (isMutable) {
            action()
            this@RAMPersistentDataContainer
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
    ): RAMPersistentDataContainer {
        return if (isMutable) {
            action()
            this@RAMPersistentDataContainer
        } else mutate().let {
            it.action()
            withMutableCopy {
                mutableBlobs[key] = it
                blobs.put(key, it)
            }
        }
    }

    private fun getAttributeNames(type: String, map: TransactionalPersistentMap<AttributeKey, *>): Set<String> =
        types[type]?.let { typeId ->
            map.getClone().entries()
                .mapNotNullTo(sortedSetOf()) { if (it.key.typeId == typeId) it.key.name else null }
        } ?: emptySet()
}

internal data class AttributeKey(val typeId: Int, val name: String) : Comparable<AttributeKey> {

    override fun compareTo(other: AttributeKey): Int {
        return typeId.compareTo(other.typeId).let {
            if (it == 0) name.compareTo(other.name) else it
        }
    }
}

private infix fun Int.withField(name: String): AttributeKey = AttributeKey(this, name)

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

