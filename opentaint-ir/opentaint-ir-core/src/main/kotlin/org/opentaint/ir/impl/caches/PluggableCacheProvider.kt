package org.opentaint.ir.impl.caches

import org.opentaint.ir.api.spi.CommonSPI
import org.opentaint.ir.api.spi.SPILoader

/**
 * Service Provider Interface to load pluggable implementation of [PluggableCacheBuilder] and [PluggableCache]
 */
interface PluggableCacheProvider : CommonSPI {

    /**
     * Id of [PluggableCacheProvider] which is used to select particular cache implementation.
     * It can be an arbitrary unique string, but use of fully qualified name of the class
     * implementing [PluggableCacheProvider] is preferable.
     */
    override val id: String

    /**
     * Creates new instance of [PluggableCacheBuilder].
     */
    fun <K : Any, V : Any> newBuilder(): PluggableCacheBuilder<K, V>

    /**
     * Creates a cache instance using internally created [PluggableCacheBuilder] with respect to
     * specified lambda which is used to configure the builder.
     */
    fun <K : Any, V : Any> newCache(configurator: PluggableCacheBuilder<K, V>.() -> Unit): PluggableCache<K, V> {
        val builder = newBuilder<K, V>().apply(configurator)
        return builder.build()
    }

    companion object : SPILoader() {

        @JvmStatic
        fun getProvider(id: String): PluggableCacheProvider {
            return loadSPI(id) ?: throw PluggableCacheException("No PluggableCacheProvider found by id = $id")
        }
    }
}