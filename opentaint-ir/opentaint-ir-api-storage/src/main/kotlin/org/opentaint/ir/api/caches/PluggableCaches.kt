package org.opentaint.ir.api.caches

import java.time.Duration

enum class ValueStoreType { WEAK, SOFT, STRONG }

class PluggableCacheException(message: String) : RuntimeException(message)

interface PluggableCache<K : Any, V : Any> {

    operator fun get(key: K): V?

    fun get(key: K, valueGetter: () -> V): V {
        return this[key] ?: valueGetter.invoke().also { this[key] = it }
    }

    operator fun set(key: K, value: V)

    fun remove(key: K)

    fun getStats(): PluggableCacheStats

    companion object {

        fun <K : Any, V : Any> of(
            id: String,
            configurator: PluggableCacheBuilder<K, V>.() -> Unit
        ): PluggableCache<K, V> {
            return PluggableCacheProvider.getProvider(id).newCache(configurator)
        }
    }
}

interface PluggableCacheStats {

    val hitRate: Double

    val requestCount: Long
}

abstract class PluggableCacheBuilder<K : Any, V : Any> {

    /**
     * Maximum number of cached `<K,V>` pairs at the moment.
     */
    var maximumSize: Int = 0
        set(value) {
            field = value.verifyCacheSize()
        }

    /**
     * Type of reference to cached value: [ValueStoreType.STRONG], [ValueStoreType.SOFT], [ValueStoreType.WEAK].
     * Use of [ValueStoreType.WEAK] is not recommended as not every pluggable cache can support it.
     */
    var valueRefType: ValueStoreType = ValueStoreType.STRONG

    /**
     * Duration after which a value can expire if it wasn't accessed during this period of time.
     * Is not recommended for using as not every pluggable cache can support it.
     */
    var expirationDuration: Duration = Duration.ZERO

    /**
     * Creates new instance of [PluggableCache] with respect to the builder options.
     */
    abstract fun build(): PluggableCache<K, V>
}

private fun Int.verifyCacheSize(): Int {
    return this.also { if (it <= 0) throw PluggableCacheException("maximumSize <= 0: $it") }
}