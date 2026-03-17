package org.opentaint.ir.testing.caches

import org.opentaint.ir.impl.caches.xodus.XODUS_CACHE_PROVIDER_ID

class XodusCacheTest : PluggableCacheTest() {

    override val cacheId = XODUS_CACHE_PROVIDER_ID
}