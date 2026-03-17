package org.opentaint.ir.impl.storage.ers.ram

/**
 * Any persistent data structure that can have an intermediate "mutable" state prior to its new version.
 */
internal interface MutableContainer<T> {

    val isMutable: Boolean

    fun mutate(): T

    fun commit(): T
}