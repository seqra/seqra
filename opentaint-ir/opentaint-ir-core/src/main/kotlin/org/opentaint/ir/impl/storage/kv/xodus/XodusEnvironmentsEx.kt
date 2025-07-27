package org.opentaint.ir.impl.storage.kv.xodus

import jetbrains.exodus.ArrayByteIterable
import jetbrains.exodus.ByteIterable
import jetbrains.exodus.core.dataStructures.ObjectCacheBase
import jetbrains.exodus.env.EnvironmentConfig

internal val ByteArray.asByteIterable: ByteIterable get() = ArrayByteIterable(this)

internal val ByteIterable.asByteArray: ByteArray
    get() = bytesUnsafe.let { array -> if (array.size == length) array else array.copyOf(length) }

internal fun environmentConfig(configurer: EnvironmentConfig.() -> Unit) = EnvironmentConfig().apply(configurer)

fun <K : Any, V : Any> ObjectCacheBase<K, V>.getOrElse(key: K, retriever: (K) -> V): V {
    return tryKey(key) ?: retriever(key)
}

fun <K : Any, V : Any> ObjectCacheBase<K, V>.getOrPut(key: K, retriever: (K) -> V): V {
    return getOrElse(key, retriever).also { obj -> cacheObject(key, obj) }
}

fun <K : Any, V : Any> ObjectCacheBase<K, V>.getOrPutConcurrent(key: K, retriever: (K) -> V): V {
    return tryKeyLocked(key) ?: retriever(key).also { obj -> newCriticalSection().use { cacheObject(key, obj) } }
}