package org.opentaint.ir.impl.storage.ers.kv

import org.opentaint.ir.api.jvm.storage.ers.ERSException
import org.opentaint.ir.api.jvm.storage.ers.Entity
import org.opentaint.ir.api.jvm.storage.ers.EntityId
import org.opentaint.ir.api.jvm.storage.ers.EntityIterable
import org.opentaint.ir.api.jvm.storage.ers.InstanceIdCollectionEntityIterable
import org.opentaint.ir.api.jvm.storage.kv.forEachWithKey

class KVEntity(override val id: EntityId, override val txn: KVErsTransaction) : Entity() {

    override fun getRawProperty(name: String): ByteArray? {
        val propertiesMap = txn.ers.propertiesMap(id.typeId, name, txn.kvTxn, create = false) ?: return null
        val keyEntry = txn.ers.longBinding.getBytesCompressed(id.instanceId)
        return txn.kvTxn.get(propertiesMap, keyEntry)
    }

    override fun setRawProperty(name: String, value: ByteArray?) {
        val kvTxn = txn.kvTxn
        val propertiesMap = txn.ers.propertiesMap(id.typeId, name, kvTxn, create = true)!!
        val keyEntry = txn.ers.longBinding.getBytesCompressed(id.instanceId)
        val oldValue = kvTxn.get(propertiesMap, keyEntry)
        if (value != null) {
            if (oldValue != null && oldValue contentEquals value) {
                // property value hasn't changed, do nothing
                return
            }
            kvTxn.put(propertiesMap, keyEntry, value)
            val propertiesIndex = txn.ers.propertiesIndex(id.typeId, name, kvTxn, create = true)!!
            oldValue?.let {
                kvTxn.delete(propertiesIndex, oldValue, keyEntry)
            }
            kvTxn.put(propertiesIndex, value, keyEntry)
        } else {
            kvTxn.delete(propertiesMap, keyEntry)
            oldValue?.let {
                kvTxn.delete(txn.ers.propertiesIndex(id.typeId, name, kvTxn, create = true)!!, oldValue, keyEntry)
            }
        }
    }

    override fun getRawBlob(name: String): ByteArray? = txn.run {
        val keyEntry = ers.longBinding.getBytesCompressed(id.instanceId)
        ers.blobsMap(id.typeId, name, kvTxn, create = false)?.let { kvTxn.get(it, keyEntry) }
    }

    override fun setRawBlob(name: String, blob: ByteArray?) {
        txn.run {
            val keyEntry = ers.longBinding.getBytesCompressed(id.instanceId)
            val blobsMap = ers.blobsMap(id.typeId, name, kvTxn, create = true)!!
            if (blob == null) {
                kvTxn.delete(blobsMap, keyEntry)
            } else {
                kvTxn.put(blobsMap, keyEntry, blob)
            }
        }
    }

    override fun getLinks(name: String): EntityIterable = txn.run {
        val targetTypeId = getLinkTargetType(id.typeId, name) ?: return EntityIterable.EMPTY
        val longBinding = ers.longBinding
        val keyEntry = longBinding.getBytesCompressed(id.instanceId)
        ers.linkTargetsMap(id.typeId, name, kvTxn, create = false)?.let { linkTargetsMap ->
            val deletedMap = ers.deletedEntitiesMap(targetTypeId, txn.kvTxn, create = false)
            InstanceIdCollectionEntityIterable(
                this, targetTypeId,
                buildList {
                    kvTxn.navigateTo(linkTargetsMap, keyEntry).use { cursor ->
                        cursor.forEachWithKey(keyEntry) { _, instanceIdEntry ->
                            if (deletedMap == null || !isDeleted(deletedMap, instanceIdEntry)) {
                                add(longBinding.getObjectCompressed(instanceIdEntry))
                            }
                        }
                    }
                }
            )
        } ?: EntityIterable.EMPTY
    }

    override fun addLink(name: String, targetId: EntityId): Boolean {
        txn.getLinkTargetType(id.typeId, name)?.let {
            if (it != targetId.typeId) {
                throw ERSException("Only links of the same type can be added by the same name.")
            }
        } ?: txn.run {
            val nameEntry = ers.stringBinding.getBytes(name)
            kvTxn.put(
                ers.linkTargetTypesMap(id.typeId, kvTxn, create = true)!!,
                nameEntry,
                ers.intBinding.getBytesCompressed(targetId.typeId)
            )
        }
        return txn.run {
            kvTxn.put(
                ers.linkTargetsMap(id.typeId, name, kvTxn, create = true)!!,
                ers.longBinding.getBytesCompressed(id.instanceId),
                ers.longBinding.getBytesCompressed(targetId.instanceId)
            )
        }
    }

    override fun deleteLink(name: String, targetId: EntityId): Boolean {
        return txn.run {
            ers.linkTargetsMap(id.typeId, name, kvTxn, create = false)?.let { linkTargetsMap ->
                val longBinding = ers.longBinding
                kvTxn.delete(
                    linkTargetsMap,
                    longBinding.getBytesCompressed(id.instanceId),
                    longBinding.getBytesCompressed(targetId.instanceId)
                )
            } ?: false
        }
    }
}