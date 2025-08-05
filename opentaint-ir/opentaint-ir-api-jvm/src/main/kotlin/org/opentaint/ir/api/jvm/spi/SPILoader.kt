package org.opentaint.ir.api.jvm.spi

import java.lang.ref.SoftReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap

open class SPILoader {

    val spiCache = ConcurrentHashMap<String, SoftReference<*>>()

    inline fun <reified T : CommonSPI> loadSPI(id: String): T? {
        return spiCache[id]?.get() as? T ?: run {
            val clazz = T::class.java
            ServiceLoader.load(clazz, clazz.getClassLoader()).find { it.id == id }?.also {
                spiCache.putIfAbsent(id, SoftReference(it))
            }
        }
    }
}