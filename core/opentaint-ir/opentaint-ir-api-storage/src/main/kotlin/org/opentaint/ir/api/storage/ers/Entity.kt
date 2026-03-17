package org.opentaint.ir.api.storage.ers

abstract class Entity : Comparable<Entity> {

    abstract val id: EntityId

    override fun equals(other: Any?): Boolean {
        return other is Entity && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun compareTo(other: Entity): Int =
        id.typeId.compareTo(other.id.typeId).let { cmp ->
            if (cmp != 0) {
                cmp
            } else {
                id.instanceId.compareTo(other.id.instanceId)
            }
        }

    abstract val txn: Transaction

    inline operator fun <reified T : Any> set(name: String, value: T?) {
        if (value is NonSearchable<*>) {
            @Suppress("UNCHECKED_CAST") val blob = (value as NonSearchable<T>).get()
            setRawBlob(name, txn.probablyCompressed(blob))
        } else {
            value?.let { setRawProperty(name, txn.probablyCompressed(it)) } ?: deleteProperty(name)
        }
    }

    inline operator fun <reified T : Any> get(name: String): T? {
        return getRawProperty(name)?.let { getBinding(T::class.java).getObject(it) }
    }

    inline fun <reified T : Any> getCompressed(name: String): T? {
        return getRawProperty(name)?.let { getBinding(T::class.java).getObjectCompressed(it) }
    }

    abstract fun getRawProperty(name: String): ByteArray?

    abstract fun setRawProperty(name: String, value: ByteArray?)

    fun deleteProperty(name: String) = setRawProperty(name, null)

    inline fun <reified T : Any> getBlob(name: String): T? {
        return getRawBlob(name)?.let { getBinding(T::class.java).getObject(it) }
    }

    inline fun <reified T : Any> getCompressedBlob(name: String): T? {
        return getRawBlob(name)?.let { getBinding(T::class.java).getObjectCompressed(it) }
    }

    abstract fun getRawBlob(name: String): ByteArray?

    abstract fun setRawBlob(name: String, blob: ByteArray?)

    fun deleteBlob(name: String) = setRawBlob(name, null)

    abstract fun getLinks(name: String): EntityIterable

    fun getLink(name: String): Entity = getLinks(name).single()

    abstract fun addLink(name: String, targetId: EntityId): Boolean

    abstract fun deleteLink(name: String, targetId: EntityId): Boolean

    fun addLink(name: String, target: Entity) = addLink(name, target.id)

    fun deleteLink(name: String, target: Entity) = deleteLink(name, target.id)

    fun delete() {
        txn.deleteEntity(id)
    }
}

data class EntityId(val typeId: Int, val instanceId: Long) : Comparable<EntityId> {

    override fun compareTo(other: EntityId): Int =
        typeId.compareTo(other.typeId).let {
            if (it == 0) instanceId.compareTo(other.instanceId) else it
        }
}