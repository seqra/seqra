package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.persistent.PersistentLong23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.InstanceIdCollectionEntityIterable
import org.opentaint.ir.util.io.writeVlqUnsigned
import java.io.OutputStream

internal class LinksMutable(
    internal val targetTypeId: Int = -1,
    internal val links: PersistentLongMap<CompactPersistentLongSet> = PersistentLong23TreeMap()
) {

    internal fun getLinks(txn: RAMTransaction, instanceId: Long): EntityIterable {
        return InstanceIdCollectionEntityIterable(
            txn,
            targetTypeId,
            links[instanceId]?.toList() ?: return EntityIterable.EMPTY
        )
    }

    internal fun addLink(instanceId: Long, targetId: EntityId): LinksMutable {
        val targetTypeId = checkTypeId(targetId)
        val idSet = links[instanceId] ?: CompactPersistentLongSet()
        val newIdSet = idSet.add(targetId.instanceId)
        return if (idSet === newIdSet) {
            this
        } else {
            LinksMutable(targetTypeId, links.write { put(instanceId, newIdSet) }.second)
        }
    }

    fun deleteLink(instanceId: Long, targetId: EntityId): LinksMutable {
        val targetTypeId = checkTypeId(targetId)
        val idSet = links[instanceId] ?: return this
        val newIdSet = idSet.remove(targetId.instanceId)
        return if (idSet === newIdSet) {
            this
        } else {
            LinksMutable(
                targetTypeId,
                links.write {
                    if (newIdSet.isEmpty()) {
                        remove(instanceId)
                    } else {
                        put(instanceId, newIdSet)
                    }
                }.second
            )
        }
    }

    private fun checkTypeId(id: EntityId): Int {
        val typeId = id.typeId
        if (targetTypeId != -1 && targetTypeId != typeId) {
            error("LinksMutable can only store ids of the same typeId")
        }
        return typeId
    }
}

internal class LinksImmutable(
    private val targetTypeId: Int,
    private val attributes: AttributesImmutable
) {

    internal fun getLinks(txn: RAMTransaction, instanceId: Long): EntityIterable {
        val bytes = attributes[instanceId] ?: return EntityIterable.EMPTY
        return EntityIterable {
            object : Iterator<Entity> {

                var offset = 0
                var nextLink: Entity? = null

                override fun hasNext(): Boolean {
                    if (nextLink == null) {
                        while (offset < bytes.size) {
                            val (targetInstanceId, len) = readCompressedUnsignedLong(bytes, offset)
                            offset += len
                            val e = txn.getEntityOrNull(EntityId(targetTypeId, targetInstanceId))
                            if (e != null) {
                                nextLink = e
                                return true
                            }
                        }
                        return false
                    }
                    return true
                }

                override fun next(): Entity = nextLink?.also { nextLink = null } ?: throw NoSuchElementException()
            }
        }
    }

    fun dump(output: OutputStream) {
        output.writeVlqUnsigned(targetTypeId)
        attributes.dump(output)
    }
}