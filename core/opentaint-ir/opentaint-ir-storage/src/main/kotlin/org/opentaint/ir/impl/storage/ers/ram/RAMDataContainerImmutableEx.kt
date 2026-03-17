package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.storage.ers.toEntityIdSet
import org.opentaint.ir.impl.RamErsSettings
import org.opentaint.ir.util.ByteArrayBuilder
import org.opentaint.ir.util.build
import org.opentaint.ir.util.collections.EmptySparseBitSet
import org.opentaint.ir.util.collections.SparseBitSet

fun EntityRelationshipStorage.toImmutable(): EntityRelationshipStorage {
    val types = transactional(readonly = true) { txn -> txn.getTypes() }
    val entityCounters = types.entries.associate { (type, typeId) ->
        typeId to transactional { tx ->
            tx.newEntity(type).id.instanceId.also { tx.abort() }
        }
    }
    val instances = arrayOfNulls<Pair<Long, SparseBitSet>>(entityCounters.keys.max() + 1)
    val properties = HashMap<AttributeKey, PropertiesImmutable>()
    val links = HashMap<AttributeKey, LinksImmutable>()
    val blobs = HashMap<AttributeKey, AttributesImmutable>()
    val builder = ByteArrayBuilder()

    transactional(readonly = true) { txn ->
        types.forEach { (type, typeId) ->
            val all = txn.all(type).toEntityIdSet()
            val deleted = SparseBitSet()
            for (i in 0L until entityCounters[typeId]!!) {
                val entityId = EntityId(typeId, i)
                if (entityId !in all) {
                    deleted.set(entityId.instanceId)
                }
            }
            entityCounters.forEach { (typeId, instanceIdCounter) ->
                instances[typeId] = instanceIdCounter to if (deleted.isEmpty) EmptySparseBitSet else deleted
            }
            txn.getPropertyNames(type).forEach { propName ->
                val propValues = mutableListOf<Pair<Long, ByteArray>>()
                all.forEach { entityId ->
                    txn.getEntityOrNull(entityId)?.let { entity ->
                        entity.getRawProperty(propName)?.let { propValue ->
                            propValues += entityId.instanceId to propValue
                        }
                    }
                }
                properties[typeId withField propName] = PropertiesImmutable(propValues.toAttributesImmutable(builder))
            }
            txn.getLinkNames(type).forEach { linkName ->
                val linkValues = mutableListOf<Pair<Long, ByteArray>>()
                var targetTypeId = -1
                all.forEach { entityId ->
                    txn.getEntityOrNull(entityId)?.let { entity ->
                        linkValues += entityId.instanceId to builder.build {
                            entity.getLinks(linkName).forEach { link ->
                                val targetId = link.id
                                targetTypeId = targetId.typeId
                                writeCompressedUnsignedLong(builder, targetId.instanceId)
                            }
                        }
                    }
                }
                links[typeId withField linkName] =
                    LinksImmutable(targetTypeId, linkValues.toAttributesImmutable(builder))
            }
            txn.getBlobNames(type).forEach { blobName ->
                val blobValues = mutableListOf<Pair<Long, ByteArray>>()
                all.forEach { entityId ->
                    txn.getEntityOrNull(entityId)?.let { entity ->
                        entity.getRawBlob(blobName)?.let { blobValue ->
                            blobValues += entityId.instanceId to blobValue
                        }
                    }
                }
                blobs[typeId withField blobName] = blobValues.toAttributesImmutable(builder)
            }
        }
    }
    return RAMEntityRelationshipStorage(
        settings = RamErsSettings(),
        RAMDataContainerImmutable(
            types = types,
            instances = instances,
            properties = properties,
            links = links,
            blobs = blobs
        )
    )
}