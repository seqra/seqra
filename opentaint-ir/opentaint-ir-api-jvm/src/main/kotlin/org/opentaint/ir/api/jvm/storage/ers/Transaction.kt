package org.opentaint.ir.api.jvm.storage.ers

import java.io.Closeable

interface Transaction : Closeable {

    val ers: EntityRelationshipStorage

    val isReadonly: Boolean

    val isFinished: Boolean

    fun checkIsNotFinished() {
        if (isFinished) {
            throw ERSTransactionFinishedException()
        }
    }

    fun newEntity(type: String): Entity

    fun getEntityOrNull(id: EntityId): Entity?

    fun deleteEntity(id: EntityId)

    fun isEntityDeleted(id: EntityId) = getEntityOrNull(id) == null

    /**
     * For specified entity type, return its id or `-1` if no entity of this type exists.
     */
    fun getTypeId(type: String): Int

    /**
     * Returns set of property names ever being set to an entity of specified `type`.
     */
    fun getPropertyNames(type: String): Set<String> = emptySet()

    /**
     * Returns set of blob names ever being set to an entity of specified `type`.
     */
    fun getBlobNamesNames(type: String): Set<String> = emptySet()

    /**
     * Returns set of link names ever being set to an entity of specified `type`.
     */
    fun getLinkNamesNames(type: String): Set<String> = emptySet()

    fun all(type: String): EntityIterable

    fun <T : Any> find(type: String, propertyName: String, value: T): EntityIterable

    fun <T : Any> findLt(type: String, propertyName: String, value: T): EntityIterable

    fun <T : Any> findEqOrLt(type: String, propertyName: String, value: T): EntityIterable

    fun <T : Any> findGt(type: String, propertyName: String, value: T): EntityIterable

    fun <T : Any> findEqOrGt(type: String, propertyName: String, value: T): EntityIterable

    fun <T : Any> find(type: String, propertyName: String, value: T, option: FindOption): EntityIterable =
        when (option) {
            FindOption.Eq -> find(type, propertyName, value)
            FindOption.Lt -> findLt(type, propertyName, value)
            FindOption.Gt -> findGt(type, propertyName, value)
            FindOption.EqOrLt -> findEqOrLt(type, propertyName, value)
            FindOption.EqOtGt -> findEqOrGt(type, propertyName, value)
        }

    fun dropAll()

    fun commit()

    fun abort()

    override fun close() {
        if (!isFinished) {
            if (isReadonly) {
                abort()
            } else {
                try {
                    commit()
                } catch (t: Throwable) {
                    abort()
                    throw t
                }
            }
        }
    }
}

enum class FindOption {
    Eq, Lt, Gt, EqOrLt, EqOtGt
}

/**
 * Returns exising or creates a new entity with specified property equal to specified value.
 */
inline fun <reified T : Any> Transaction.findOrNew(type: String, property: String, value: T): Entity {
    return find(type, property, value).firstOrNull() ?: newEntity(type).also { it[property] = value }
}

fun Transaction.getEntityOrNull(type: String, instanceId: Long): Entity? {
    return getEntityOrNull(EntityId(getTypeId(type), instanceId))
}

fun <T : Any> Transaction.getBinding(clazz: Class<T>): Binding<T> = ers.getBinding(clazz)

fun <T : Any> Transaction.probablyCompressed(value: T): ByteArray {
    return if (value is Compressed<*>) {
        @Suppress("UNCHECKED_CAST") val blob = (value as Compressed<T>).get()
        getBinding(blob.javaClass).getBytesCompressed(blob)
    } else {
        getBinding(value.javaClass).getBytes(value)
    }
}