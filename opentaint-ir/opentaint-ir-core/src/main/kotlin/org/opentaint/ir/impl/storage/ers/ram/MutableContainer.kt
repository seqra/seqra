package org.opentaint.ir.impl.storage.ers.ram

internal interface MutableContainer<T> {

    val isMutable: Boolean

    fun mutate(): T

    fun commit(): T
}