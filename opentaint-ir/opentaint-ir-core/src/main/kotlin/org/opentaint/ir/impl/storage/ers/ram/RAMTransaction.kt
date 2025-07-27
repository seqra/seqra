package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.jvm.storage.ers.ERSConflictingTransactionException
import org.opentaint.ir.api.jvm.storage.ers.ERSException
import org.opentaint.ir.api.jvm.storage.ers.Entity
import org.opentaint.ir.api.jvm.storage.ers.EntityId
import org.opentaint.ir.api.jvm.storage.ers.EntityIterable
import org.opentaint.ir.api.jvm.storage.ers.Transaction
import org.opentaint.ir.api.jvm.storage.ers.probablyCompressed

internal class RAMTransaction(override val ers: RAMEntityRelationshipStorage) : Transaction {

    private var originContainer = ers.dataContainer
    private var dataContainer: RAMPersistentDataContainer? = originContainer

    override val isReadonly = false

    override val isFinished: Boolean
        get() = dataContainer == null

    override fun newEntity(type: String): Entity {
        val typeId = getOrAllocateTypeId(type)
        val instanceId = allocateInstanceId(typeId)
        return RAMEntity(this, EntityId(typeId, instanceId))
    }

    override fun getEntityOrNull(id: EntityId): Entity? =
        if (dataContainerChecked.entityExists(id)) RAMEntity(this, id) else null

    override fun deleteEntity(id: EntityId) {
        dataContainer = dataContainerChecked.deleteEntity(id)
    }

    override fun getTypeId(type: String): Int = dataContainerChecked.getTypeId(type)

    override fun getPropertyNames(type: String): Set<String> = dataContainerChecked.getPropertyNames(type)

    override fun getBlobNamesNames(type: String): Set<String> = dataContainerChecked.getBlobNames(type)

    override fun getLinkNamesNames(type: String): Set<String> = dataContainerChecked.getLinkNames(type)

    override fun all(type: String): EntityIterable = dataContainerChecked.all(this, type)

    override fun <T : Any> find(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesWithPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun <T : Any> findLt(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesLtPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun <T : Any> findEqOrLt(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesEqOrLtPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun <T : Any> findGt(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesGtPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun <T : Any> findEqOrGt(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesEqOrGtPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun dropAll() {
        dataContainer = RAMPersistentDataContainer()
    }

    override fun commit() {
        val originContainer = originContainer
        val resultContainer = dataContainer?.commit()
        // if transaction wasn't read-only
        if (resultContainer != null && originContainer !== resultContainer) {
            if (!ers.compareAndSetDataContainer(originContainer, resultContainer)) {
                throw ERSConflictingTransactionException(
                    "Cannot commit transaction since a parallel one has been committed in between"
                )
            }
        }
        dataContainer = null
    }

    override fun abort() {
        dataContainer = null
    }

    internal fun getRawProperty(id: EntityId, name: String): ByteArray? = dataContainerChecked.getRawProperty(id, name)

    internal fun setRawProperty(id: EntityId, name: String, value: ByteArray?) {
        dataContainer = dataContainerChecked.setRawProperty(id, name, value)
    }

    internal fun getBlob(id: EntityId, name: String): ByteArray? {
        return dataContainerChecked.getBlob(id, name)
    }

    internal fun setBlob(id: EntityId, name: String, value: ByteArray?) {
        dataContainer = dataContainerChecked.setBlob(id, name, value)
    }

    internal fun getLinks(id: EntityId, name: String): EntityIterable {
        return dataContainerChecked.getLinks(this, id, name)
    }

    internal fun addLink(id: EntityId, name: String, targetId: EntityId): Boolean {
        dataContainerChecked.let { dataContainer ->
            val newDataContainer = dataContainer.addLink(id, name, targetId)
            return (newDataContainer !== dataContainer).also { this.dataContainer = newDataContainer }
        }
    }

    internal fun deleteLink(id: EntityId, name: String, targetId: EntityId): Boolean {
        dataContainerChecked.let { dataContainer ->
            val newDataContainer = dataContainer.deleteLink(id, name, targetId)
            return (newDataContainer !== dataContainer).also { this.dataContainer = newDataContainer }
        }
    }

    private val dataContainerChecked: RAMPersistentDataContainer
        get() = dataContainer ?: throw ERSException("Transaction has been already finished")

    private fun getOrAllocateTypeId(type: String): Int {
        val (newContainer, typeId) = dataContainerChecked.getOrAllocateTypeId(type)
        dataContainer = newContainer
        return typeId
    }

    private fun allocateInstanceId(typeId: Int): Long {
        val (newContainer, instanceId) = dataContainerChecked.allocateInstanceId(typeId)
        dataContainer = newContainer
        return instanceId
    }
}