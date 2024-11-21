package org.opentaint.ir.impl.storage

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.time.Duration


fun <KEY, VALUE> cacheOf(size: Long): Cache<KEY, VALUE> {
    return CacheBuilder.newBuilder().maximumSize(size).expireAfterAccess(Duration.ofSeconds(10)).build()
}