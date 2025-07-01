package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.persistent.PersistentLong23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import org.opentaint.ir.api.jvm.storage.ers.ERSException

class TransactionalPersistentLongMap<V : Any>(private val committed: PersistentLongMap<V> = PersistentLong23TreeMap()) :
    MutableContainer<TransactionalPersistentLongMap<V>> {

    private var mutated: PersistentLongMutableMap<V>? = null

    operator fun get(key: Long): V? {
        mutated?.let {
            return it[key]
        }
        return committed[key]
    }

    override val isMutable: Boolean get() = mutated != null

    override fun mutate(): TransactionalPersistentLongMap<V> {
        return if (mutated != null) this else TransactionalPersistentLongMap(committed.clone)
    }

    override fun commit(): TransactionalPersistentLongMap<V> {
        return mutated?.let {
            // the following line is not necessary, but earlier reclaim of used memory
            // looks effective according to benchmarks
            mutated = null
            if (!it.endWrite()) {
                throw ERSException("Failed to commit TransactionalPersistentLongMap.")
            }
            TransactionalPersistentLongMap(committed)
        } ?: this
    }

    fun put(key: Long, value: V) {
        mutated().put(key, value)
    }

    fun remove(key: Long) {
        mutated().remove(key)
    }

    private fun mutated(): PersistentLongMutableMap<V> {
        return mutated ?: committed.beginWrite().also { mutated = it }
    }
}