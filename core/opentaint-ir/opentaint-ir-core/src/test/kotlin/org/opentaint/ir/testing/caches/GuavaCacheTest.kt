package org.opentaint.ir.testing.caches

import org.opentaint.ir.impl.caches.guava.GUAVA_CACHE_PROVIDER_ID

class GuavaCacheTest : PluggableCacheTest() {

    override val cacheId: String = GUAVA_CACHE_PROVIDER_ID
}