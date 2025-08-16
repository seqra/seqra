package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable

internal interface RAMDataContainer : MutableContainer<RAMDataContainer> {

    fun toImmutable(): RAMDataContainer

    fun entityExists(id: EntityId): Boolean

    fun getTypeId(type: String): Int

    fun getOrAllocateTypeId(type: String): Pair<RAMDataContainer, Int>

    fun allocateInstanceId(typeId: Int): Pair<RAMDataContainer, Long>

    fun getPropertyNames(type: String): Set<String>

    fun getBlobNames(type: String): Set<String>

    fun getLinkNames(type: String): Set<String>

    fun all(txn: RAMTransaction, type: String): EntityIterable

    fun deleteEntity(id: EntityId): RAMDataContainer

    fun getRawProperty(id: EntityId, propertyName: String): ByteArray?

    fun setRawProperty(id: EntityId, propertyName: String, value: ByteArray?): RAMDataContainer

    fun getEntitiesWithPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable>

    fun getEntitiesLtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable>

    fun getEntitiesEqOrLtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable>

    fun getEntitiesGtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable>

    fun getEntitiesEqOrGtPropertyValue(
        txn: RAMTransaction,
        type: String,
        propertyName: String,
        value: ByteArray
    ): Pair<RAMDataContainer?, EntityIterable>

    fun getBlob(id: EntityId, blobName: String): ByteArray?

    fun setBlob(id: EntityId, blobName: String, value: ByteArray?): RAMDataContainer

    fun getLinks(txn: RAMTransaction, id: EntityId, linkName: String): EntityIterable

    fun addLink(id: EntityId, linkName: String, targetId: EntityId): RAMDataContainer

    fun deleteLink(id: EntityId, linkName: String, targetId: EntityId): RAMDataContainer
}