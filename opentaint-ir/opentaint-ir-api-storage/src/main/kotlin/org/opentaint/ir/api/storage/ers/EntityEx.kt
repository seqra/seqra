package org.opentaint.ir.api.storage.ers

import kotlin.reflect.KProperty

interface LazyProperty<T : Any> {

    fun getValue(propName: String): T?

    operator fun getValue(thisRef: Nothing?, property: KProperty<*>): T? {
        return getValue(property.name)
    }
}

inline fun <reified T : Any> propertyOf(
    entity: Entity,
    name: String? = null,
    compressed: Boolean = false
): LazyProperty<T> =
    object : LazyProperty<T> {
        override fun getValue(propName: String): T? {
            return if (compressed) entity.getCompressed(name ?: propName) else entity[name ?: propName]
        }
    }

inline fun <reified T : Any> blobOf(
    entity: Entity,
    name: String? = null,
    compressed: Boolean = false
): LazyProperty<T> =
    object : LazyProperty<T> {
        override fun getValue(propName: String): T? {
            return if (compressed) entity.getCompressedBlob(name ?: propName) else entity.getBlob(name ?: propName)
        }
    }

class LazyLinks(private val entity: Entity, private val linkName: String) {

    val asIterable: EntityIterable get() = entity.getLinks(linkName)

    operator fun contains(e: Entity): Boolean = e in asIterable

    operator fun plusAssign(e: Entity) {
        entity.addLink(linkName, e)
    }

    operator fun minusAssign(e: Entity) {
        entity.deleteLink(linkName, e)
    }
}

fun links(entity: Entity, name: String) = LazyLinks(entity, name)

interface NonSearchable<T : Any> {
    fun get(): T
}

/**
 * Marks a property value as non-searchable. This would result in storing the value to blob rather than to property.
 * This extension property should be always used after the [compressed] property if it is used.
 */
val <T : Any> T.nonSearchable: NonSearchable<T>
    get() = object : NonSearchable<T> {
        override fun get(): T = this@nonSearchable
    }

interface Compressed<T : Any> {
    fun get(): T
}

/**
 * Marks a property or a blob value as compressed. If corresponding binding supports compression it would be used.
 */
val <T : Any> T.compressed: Compressed<T>
    get() = object : Compressed<T> {
        override fun get(): T = this@compressed
    }

fun <T : Any> Entity.getBinding(clazz: Class<T>): Binding<T> = txn.ers.getBinding(clazz)