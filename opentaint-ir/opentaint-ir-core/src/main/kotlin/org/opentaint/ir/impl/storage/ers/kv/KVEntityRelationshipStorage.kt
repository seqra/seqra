package org.opentaint.ir.impl.storage.ers.kv

import org.opentaint.ir.api.jvm.storage.ers.Binding
import org.opentaint.ir.api.jvm.storage.ers.ERSConflictingTransactionException
import org.opentaint.ir.api.jvm.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.jvm.storage.ers.Transaction
import org.opentaint.ir.api.jvm.storage.kv.EmptyNamedMap
import org.opentaint.ir.api.jvm.storage.kv.NamedMap
import org.opentaint.ir.api.jvm.storage.kv.PluggableKeyValueStorage
import org.opentaint.ir.impl.storage.ers.decorators.withAllDecorators
import org.opentaint.ir.impl.storage.ers.getBinding
import java.util.concurrent.ConcurrentHashMap

internal val intClass = Int::class.java
internal val longClass = Long::class.java
internal val stringClass = String::class.java

class KVEntityRelationshipStorage(private val kvStorage: PluggableKeyValueStorage) : EntityRelationshipStorage {

    private var entityTypesMap: NamedMap? = null
    private var entityCountersMap: NamedMap? = null
    private val entityTypes = ConcurrentHashMap<String, Int>() // entity type name -> type id
    private val deletedEntitiesMaps = ConcurrentHashMap<Int, NamedMap>() // typeId -> NamedMap
    private val propertiesMaps = ConcurrentHashMap<TypeIdWithName, NamedMap>() // (typeId, propName) -> NamedMap
    private val propertiesIndices = ConcurrentHashMap<TypeIdWithName, NamedMap>() // (typeId, propName) -> NamedMap
    private val blobsMaps = ConcurrentHashMap<TypeIdWithName, NamedMap>() // (typeId, blobName) -> NamedMap
    private val linkTargetTypesMaps = ConcurrentHashMap<Int, NamedMap>() // typeId -> NamedMap
    private val linkTargetsMaps = ConcurrentHashMap<TypeIdWithName, NamedMap>() // (typeId, linkName) -> NamedMap
    private val currentThreadRWTxn = ThreadLocal<Transaction>()

    override fun beginTransaction(readonly: Boolean): Transaction {
        currentThreadRWTxn.get()?.let {
            if (!readonly) {
                throw ERSConflictingTransactionException("Read-write transaction has already started in current thread")
            }
        }
        return KVErsTransaction(
            this,
            if (readonly) kvStorage.beginReadonlyTransaction() else kvStorage.beginTransaction()
        ).also { txn ->
            if (!readonly) {
                currentThreadRWTxn.set(txn)
            }
        }.withAllDecorators()
    }

    override fun close() = kvStorage.close()

    override fun <T : Any> getBinding(clazz: Class<T>): Binding<T> = clazz.getBinding()

    internal fun transactionFinished(txn: Transaction) {
        if (!txn.isReadonly) {
            val threadTxn = currentThreadRWTxn.get()
            if (txn !== threadTxn) {
                throw ERSConflictingTransactionException("Attempt to finish transaction in thread different from that it was started in")
            }
            currentThreadRWTxn.remove()
        }
    }

    internal val intBinding = getBinding(intClass)

    internal val longBinding = getBinding(longClass)

    internal val stringBinding = getBinding(stringClass)

    internal fun getEntityTypeId(
        type: String,
        kvTxn: org.opentaint.ir.api.jvm.storage.kv.Transaction
    ): Int? {
        entityTypes[type]?.let { return it }
        return kvTxn.get(entityTypesMap(kvTxn), stringBinding.getBytes(type))?.let { typeIdEntry ->
            intBinding.getObjectCompressed(typeIdEntry).also { entityTypes[type] = it }
        }
    }

    internal fun getOrAllocateEntityTypeId(
        type: String,
        kvTxn: org.opentaint.ir.api.jvm.storage.kv.Transaction
    ): Int {
        return entityTypes.computeIfAbsent(type) {
            val typeEntry = stringBinding.getBytes(type)
            kvTxn.get(entityTypesMap(kvTxn), typeEntry)?.let { typeIdEntry ->
                intBinding.getObjectCompressed(typeIdEntry)
            } ?: run {
                entityTypesMap(kvTxn).size(kvTxn).toInt().also { typeId ->
                    kvTxn.put(entityTypesMap(kvTxn), typeEntry, intBinding.getBytesCompressed(typeId))
                }
            }
        }
    }

    private fun entityTypesMap(txn: org.opentaint.ir.api.jvm.storage.kv.Transaction): NamedMap {
        return entityTypesMap ?: txn.getNamedMap(entityTypesMapName).also {
            if (it !== EmptyNamedMap) {
                entityTypesMap = it
            }
        }
    }

    internal fun entityCountersMap(txn: org.opentaint.ir.api.jvm.storage.kv.Transaction): NamedMap {
        return entityCountersMap ?: txn.getNamedMap(entityCountersMapName).also {
            if (it !== EmptyNamedMap) {
                entityCountersMap = it
            }
        }
    }

    internal fun deletedEntitiesMap(typeId: Int, txn: org.opentaint.ir.api.jvm.storage.kv.Transaction): NamedMap {
        return deletedEntitiesMaps.getOrElse(typeId) {
            txn.getNamedMap(deletedEntitiesMapName(typeId)).also {
                if (it !== EmptyNamedMap) {
                    deletedEntitiesMaps[typeId] = it
                }
            }
        }
    }

    internal fun propertiesMap(
        typeId: Int,
        propName: String,
        txn: org.opentaint.ir.api.jvm.storage.kv.Transaction
    ): NamedMap {
        return propertiesMaps.getOrElse(typeId with propName) {
            txn.getNamedMap(propertiesMapName(typeId, propName)).also {
                if (it !== EmptyNamedMap) {
                    propertiesMaps[typeId with propName] = it
                }
            }
        }
    }

    internal fun propertiesIndex(
        typeId: Int,
        propName: String,
        txn: org.opentaint.ir.api.jvm.storage.kv.Transaction
    ): NamedMap {
        return propertiesIndices.getOrPut(typeId with propName) {
            txn.getNamedMap(propertiesIndexName(typeId, propName))
        }
    }

    internal fun blobsMap(
        typeId: Int,
        blobName: String,
        txn: org.opentaint.ir.api.jvm.storage.kv.Transaction
    ): NamedMap {
        return blobsMaps.getOrElse(typeId with blobName) {
            txn.getNamedMap(blobsMapName(typeId, blobName)).also {
                if (it !== EmptyNamedMap) {
                    blobsMaps[typeId with blobName] = it
                }
            }
        }
    }

    internal fun linkTargetTypesMap(typeId: Int, txn: org.opentaint.ir.api.jvm.storage.kv.Transaction): NamedMap {
        return linkTargetTypesMaps.getOrElse(typeId) {
            txn.getNamedMap(linkTargetTypesMapName(typeId)).also {
                if (it !== EmptyNamedMap) {
                    linkTargetTypesMaps[typeId] = it
                }
            }
        }
    }

    internal fun linkTargetsMap(
        typeId: Int,
        linkName: String,
        txn: org.opentaint.ir.api.jvm.storage.kv.Transaction
    ): NamedMap {
        return linkTargetsMaps.getOrPut(typeId with linkName) {
            txn.getNamedMap(linkTargetsMapName(typeId, linkName))
        }
    }
}

private const val withDuplicates = "#withDuplicates"
private const val packageNamePrefix = "org.opentaint.ir.impl.storage.ers.kv#"

internal val String.isMapWithKeyDuplicates: Boolean get() = endsWith(withDuplicates)

/**
 * Entity type (String) -> type id (Int)
 */
private val entityTypesMapName = "${packageNamePrefix}entities_types"

/**
 * Type id (Int) -> entity counter (Long)
 */
private val entityCountersMapName = "${packageNamePrefix}entity_counters"

private fun deletedEntitiesMapName(typeId: Int) = "${packageNamePrefix}$typeId#deleted_entities"

/**
 * InstanceId (Long) -> prop value (ByteArray)
 */
private fun propertiesMapName(typeId: Int, name: String) = "${packageNamePrefix}$typeId#$name#properties"

/**
 * InstanceId (Long) -> prop value (ByteArray)
 */
private fun propertiesIndexName(typeId: Int, name: String) =
    "${packageNamePrefix}$typeId#$name#properties_idx$withDuplicates"

/**
 * InstanceId (Long) -> prop value (ByteArray)
 */
private fun blobsMapName(typeId: Int, name: String) = "${packageNamePrefix}$typeId#$name#blobs"

private fun linkTargetTypesMapName(typeId: Int) = "${packageNamePrefix}$typeId#link_target_types"

private fun linkTargetsMapName(typeId: Int, name: String) =
    "${packageNamePrefix}$typeId#$name#link_targets$withDuplicates"