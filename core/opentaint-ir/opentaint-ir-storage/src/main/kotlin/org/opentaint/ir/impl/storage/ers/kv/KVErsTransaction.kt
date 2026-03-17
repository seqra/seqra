package org.opentaint.ir.impl.storage.ers.kv

import jetbrains.exodus.core.dataStructures.ConcurrentObjectCache
import jetbrains.exodus.core.dataStructures.ObjectCacheBase
import jetbrains.exodus.core.dataStructures.hash.IntHashMap
import org.opentaint.ir.api.storage.ers.Binding
import org.opentaint.ir.api.storage.ers.ERSConflictingTransactionException
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.InstanceIdCollectionEntityIterable
import org.opentaint.ir.api.storage.ers.Transaction
import org.opentaint.ir.api.storage.ers.probablyCompressed
import org.opentaint.ir.api.storage.kv.Cursor
import org.opentaint.ir.api.storage.kv.NamedMap
import org.opentaint.ir.api.storage.kv.asIterable
import org.opentaint.ir.api.storage.kv.asIterableWithKey
import org.opentaint.ir.impl.storage.kv.xodus.getOrPut

class KVErsTransaction(
    override val ers: KVEntityRelationshipStorage,
    val kvTxn: org.opentaint.ir.api.storage.kv.Transaction
) : Transaction {

    private val isDeletedCache: ObjectCacheBase<EntityId, Boolean> = ConcurrentObjectCache(1_000, 2)
    private val linkTargetTypes = hashMapOf<TypeIdWithName, Int>() // typeId + linkName -> targetTypeId
    private val entityCounters = hashMapOf<Int, Long>() // typeId -> entityCounter
    private val dirtyEntityCounters = sortedMapOf<Int, Long>() // typeId -> entityCounter (modified within transaction)
    private val deletedEntitiesCounts = IntHashMap<Long>() // typeId -> number of deleted entities

    override val isReadonly: Boolean get() = kvTxn.isReadonly

    override val isFinished: Boolean get() = kvTxn.isFinished

    override fun newEntity(type: String): Entity {
        val typeId = ers.getOrAllocateEntityTypeId(type, kvTxn)
        val entityCounter = getEntityCounter(typeId) ?: 0L
        entityCounters[typeId] = entityCounter + 1
        dirtyEntityCounters[typeId] = entityCounter + 1
        return KVEntity(EntityId(typeId, entityCounter), this)
    }

    override fun getEntityOrNull(id: EntityId): Entity? {
        val entityCounter = getEntityCounter(id.typeId) ?: return null
        return if (id.instanceId in 0 until entityCounter && !isEntityDeleted(id)) KVEntity(id, this) else null
    }

    override fun getEntityUnsafe(id: EntityId): Entity = KVEntity(id, txn = this)

    override fun deleteEntity(id: EntityId) {
        // invalidate isDeleted cache
        isDeletedCache.cacheObject(id, true)
        val typeId = id.typeId
        deletedEntitiesCounts[typeId] = getDeletedEntitiesCount(typeId) + 1L
        kvTxn.put(
            ers.deletedEntitiesMap(typeId, kvTxn, create = true)!!,
            ers.longBinding.getBytesCompressed(id.instanceId),
            byteArrayOf(0)
        )
    }

    override fun isEntityDeleted(id: EntityId): Boolean {
        return getDeletedEntitiesCount(id.typeId) > 0L && isDeletedCache.getOrPut(id) {
            ers.deletedEntitiesMap(id.typeId, kvTxn, create = false)?.let {
                isDeleted(it, ers.longBinding, id.instanceId)
            } ?: false
        }
    }

    override fun getTypeId(type: String): Int {
        return ers.getEntityTypeId(type, kvTxn) ?: -1
    }

    override fun getTypes(): Map<String, Int> = ers.getEntityTypes(kvTxn)

    override fun getPropertyNames(type: String): Set<String> = getAttributeName(type) {
        ers.getPropNameFromMapName(it)
    }

    override fun getBlobNames(type: String): Set<String> = getAttributeName(type) {
        ers.getBlobNameFromMapName(it)
    }

    override fun getLinkNames(type: String): Set<String> = getAttributeName(type) {
        ers.getLinkNameFromMapName(it)
    }

    override fun all(type: String): EntityIterable {
        val typeId = ers.getEntityTypeId(type, kvTxn) ?: return EntityIterable.EMPTY
        val entityCounter = getEntityCounter(typeId) ?: return EntityIterable.EMPTY
        if (getDeletedEntitiesCount(typeId) == 0L) {
            return InstanceIdCollectionEntityIterable(this, typeId, (0 until entityCounter).toList())
        }
        val deletedMap = ers.deletedEntitiesMap(typeId, kvTxn, create = false)
        return if (deletedMap == null) {
            InstanceIdCollectionEntityIterable(this, typeId, (0 until entityCounter).toList())
        } else {
            InstanceIdCollectionEntityIterable(this, typeId,
                buildList {
                    (0 until entityCounter).forEach { instanceId ->
                        if (!isDeleted(deletedMap, ers.longBinding, instanceId)) {
                            add(instanceId)
                        }
                    }
                }
            )
        }
    }

    override fun <T : Any> find(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) { valueEntry ->
            asIterableWithKey(valueEntry)
        }
    }

    override fun <T : Any> findLt(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) { valueEntry ->
            asReversedIterable(valueEntry).asSequence().filter { (key, _) -> !key.contentEquals(valueEntry) }
                .asIterable()
        }
    }

    override fun <T : Any> findEqOrLt(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) { valueEntry ->
            asReversedIterable(valueEntry)
        }
    }

    override fun <T : Any> findGt(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) { valueEntry ->
            asIterable().asSequence().filter { (key, _) -> !key.contentEquals(valueEntry) }.asIterable()
        }
    }

    override fun <T : Any> findEqOrGt(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) {
            asIterable()
        }
    }

    override fun dropAll() {
        TODO("Not yet implemented")
    }

    override fun commit() {
        if (!isFinished) {
            flushDirty()
            if (!kvTxn.commit()) {
                throw ERSConflictingTransactionException()
            }
            ers.transactionFinished(this)
        }
    }

    override fun abort() {
        if (!isFinished) {
            kvTxn.abort()
            ers.transactionFinished(this)
        }
    }

    private fun isDeleted(deletedMap: NamedMap, longBinding: Binding<Long>, instanceId: Long): Boolean {
        return isDeleted(deletedMap, longBinding.getBytesCompressed(instanceId))
    }

    internal fun isDeleted(deletedMap: NamedMap, instanceIdEntry: ByteArray): Boolean {
        return kvTxn.get(deletedMap, instanceIdEntry) != null
    }

    internal fun getLinkTargetType(typeId: Int, linkName: String): Int? {
        return linkTargetTypes.getOrElse(typeId with linkName) {
            val nameEntry = ers.stringBinding.getBytes(linkName)
            ers.linkTargetTypesMap(typeId, kvTxn, create = false)
                ?.let { kvTxn.get(it, nameEntry) }
                ?.let { typeIdEntry ->
                    ers.intBinding.getObjectCompressed(typeIdEntry)
                }?.also {
                    linkTargetTypes[typeId with linkName] = it
                }
        }
    }

    private fun <T : Any> genericFind(
        type: String,
        propertyName: String,
        value: T,
        cursorFun: Cursor.(valueEntry: ByteArray) -> Iterable<Pair<ByteArray, ByteArray>>
    ): EntityIterable {
        val typeId = getTypeId(type)
        if (typeId < 0) {
            return EntityIterable.EMPTY
        }
        val valueEntry = probablyCompressed(value)
        val index = ers.propertiesIndex(typeId, propertyName, kvTxn, create = false)
        val deletedMap = ers.deletedEntitiesMap(typeId, kvTxn, create = false)
        return if (deletedMap == null) {
            if (index == null) {
                EntityIterable.EMPTY
            } else {
                InstanceIdCollectionEntityIterable(this, typeId,
                    buildList {
                        kvTxn.navigateTo(index, valueEntry).use { cursor ->
                            cursor.cursorFun(valueEntry).forEach { (_, instanceIdEntry) ->
                                add(ers.longBinding.getObjectCompressed(instanceIdEntry))
                            }
                        }
                    }
                )
            }
        } else {
            if (index == null) {
                EntityIterable.EMPTY
            } else {
                InstanceIdCollectionEntityIterable(this, typeId,
                    buildList {
                        kvTxn.navigateTo(index, valueEntry).use { cursor ->
                            cursor.cursorFun(valueEntry).forEach { (_, instanceIdEntry) ->
                                if (!isDeleted(deletedMap, instanceIdEntry)) {
                                    add(ers.longBinding.getObjectCompressed(instanceIdEntry))
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun getEntityCounter(typeId: Int): Long? {
        return entityCounters.getOrElse(typeId) {
            ers.entityCountersMap(kvTxn, create = false)
                ?.let { kvTxn.get(it, ers.intBinding.getBytesCompressed(typeId)) }
                ?.let { entityCounterEntry ->
                    ers.longBinding.getObjectCompressed(entityCounterEntry)
                }?.also {
                    entityCounters[typeId] = it
                }
        }
    }

    private fun getDeletedEntitiesCount(typeId: Int): Long {
        return deletedEntitiesCounts.getOrElse(typeId) {
            ers.deletedEntitiesMap(typeId, kvTxn, create = false)?.size(kvTxn)
                ?.also { deletedEntitiesCounts[typeId] = it } ?: 0L
        }
    }

    private fun flushDirty() {
        if (dirtyEntityCounters.isNotEmpty()) {
            val entityCountersMap = this.ers.entityCountersMap(kvTxn, create = true)!!
            dirtyEntityCounters.forEach { (typeId, entityCounter) ->
                kvTxn.put(
                    entityCountersMap,
                    ers.intBinding.getBytesCompressed(typeId),
                    ers.longBinding.getBytesCompressed(entityCounter)
                )
            }
        }
        dirtyEntityCounters.clear()
        // clear caches
        isDeletedCache.clear()
        linkTargetTypes.clear()
        entityCounters.clear()
    }

    private fun getAttributeName(type: String, checkMapNameFunc: (String) -> Pair<String, Int>?): Set<String> {
        val typeId = ers.getEntityTypeId(type, kvTxn) ?: return emptySet()
        return kvTxn.getMapNames().mapNotNullTo(mutableSetOf()) {
            checkMapNameFunc(it)?.let { pair ->
                pair.first.takeIf { pair.second == typeId }
            }
        }
    }
}