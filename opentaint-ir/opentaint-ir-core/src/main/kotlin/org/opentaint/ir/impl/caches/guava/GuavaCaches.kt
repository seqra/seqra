package org.opentaint.ir.impl.caches.guava

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.opentaint.ir.api.jvm.ValueStoreType
import org.opentaint.ir.impl.caches.PluggableCache
import org.opentaint.ir.impl.caches.PluggableCacheBuilder
import org.opentaint.ir.impl.caches.PluggableCacheProvider
import org.opentaint.ir.impl.caches.PluggableCacheStats
import java.time.Duration

const val GUAVA_CACHE_PROVIDER_ID = "org.opentaint.ir.impl.caches.guava.GuavaCacheProvider"

class GuavaCacheProvider : PluggableCacheProvider {

    override val id = GUAVA_CACHE_PROVIDER_ID

    override fun <K : Any, V : Any> newBuilder(): PluggableCacheBuilder<K, V> = GuavaCacheBuilder()
}

private class GuavaCacheBuilder<K : Any, V : Any> : PluggableCacheBuilder<K, V>() {

    override fun build(): PluggableCache<K, V> {
        return GuavaCache(
            CacheBuilder.newBuilder()
                .maximumSize(maximumSize.toLong())
                .apply {
                    expirationDuration.let {
                        if (it != Duration.ZERO) {
                            expireAfterAccess(it)
                        }
                    }
                    when (valueRefType) {
                        ValueStoreType.WEAK -> weakValues()
                        ValueStoreType.SOFT -> softValues()
                        ValueStoreType.STRONG -> {} // do nothing
                    }
                }
                .recordStats()
                .build()
        )
    }
}

private class GuavaCache<K : Any, V : Any>(private val guavaCache: Cache<K, V>) : PluggableCache<K, V> {

    override fun get(key: K): V? = guavaCache.getIfPresent(key)

    override fun set(key: K, value: V) = guavaCache.put(key, value)

    override fun remove(key: K) = guavaCache.invalidate(key)

    override fun getStats() = object : PluggableCacheStats {

        val guavaStats = guavaCache.stats()

        override val hitRate: Double get() = guavaStats.hitRate()

        override val requestCount: Long get() = guavaStats.requestCount()
    }
}