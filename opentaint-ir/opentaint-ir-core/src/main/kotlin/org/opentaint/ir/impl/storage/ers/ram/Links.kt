package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.persistent.PersistentLong23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import org.opentaint.ir.api.jvm.storage.ers.EntityId
import org.opentaint.ir.api.jvm.storage.ers.EntityIdCollectionEntityIterable
import org.opentaint.ir.api.jvm.storage.ers.EntityIterable

internal class Links(private val links: PersistentLongMap<EntityIdSet> = PersistentLong23TreeMap()) {

    internal fun getLinks(txn: RAMTransaction, instanceId: Long): EntityIterable {
        return EntityIdCollectionEntityIterable(txn, (links[instanceId]?.toList() ?: return EntityIterable.EMPTY))
    }

    internal fun addLink(instanceId: Long, targetId: EntityId): Links {
        val idSet = links[instanceId] ?: EntityIdSet()
        val newIdSet = idSet.add(targetId)
        return if (idSet === newIdSet) this else Links(links.write { put(instanceId, newIdSet) }.second)
    }

    fun deleteLink(instanceId: Long, targetId: EntityId): Links {
        val idSet = links[instanceId] ?: return this
        val newIdSet = idSet.remove(targetId)
        return if (idSet === newIdSet) {
            this
        } else {
            Links(
                links.write {
                    if (newIdSet.isEmpty) {
                        remove(instanceId)
                    } else {
                        put(instanceId, newIdSet)
                    }
                }.second
            )
        }
    }
}