package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.persistent.PersistentLong23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.InstanceIdCollectionEntityIterable

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
}

internal fun LinksMutable.toImmutable(): LinksImmutable {
    val linkList = mutableListOf<Pair<Long, ByteArray>>()
    links.beginRead().forEach { link ->
        val instanceId = link.key
        val linkSet = link.value
        var valueArray = byteArrayOf()
        linkSet.forEach { targetId ->
            valueArray += writeCompressedUnsignedLong(targetId)
        }
        linkList += instanceId to valueArray
    }
    return LinksImmutable(targetTypeId, toAttributesImmutable(linkList))
}

/**
 * Returns read unsigned long value and the length of the byte array used for the value.
 */
private fun readCompressedUnsignedLong(bytes: ByteArray, offset: Int): Pair<Long, Int> {
    var len = 0
    var result = 0L
    while (true) {
        val b = bytes[offset + len].toInt()
        result += (b and 0x7F) shl (len * 7)
        len++
        if ((b and 0x80) != 0) break
    }
    return result to len
}

/**
 * Writes compressed unsigned long value to a new byte array.
 */
private fun writeCompressedUnsignedLong(l: Long): ByteArray {
    check(l >= 0)
    var t = l
    var len = 1
    while (t > 127) {
        t = t shr 7
        len++
    }
    t = l
    val result = ByteArray(len)
    for (i in 0 until len) {
        result[i] = (t and 0x7F).toByte()
        t = t shr 7
    }
    result[len - 1] = (result[len - 1].toInt() or 0x80).toByte()
    return result
}