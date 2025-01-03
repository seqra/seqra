package org.opentaint.ir.impl.features.classpaths

import com.google.common.cache.CacheBuilder
import org.opentaint.ir.api.JIRClassFoundEvent
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathExtFeature
import org.opentaint.ir.api.JIRClasspathFeatureEvent
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeFoundEvent
import java.time.Duration

/**
 * any class cache should extend this class
 */
open class ClasspathCache(
    protected val maxSize: Long = 10_000,
    protected val expiration: Duration = Duration.ofMinutes(1)
) : JIRClasspathExtFeature {
    /**
     *
     */
    private val classesCache = CacheBuilder.newBuilder()
        .expireAfterAccess(expiration)
        .maximumSize(maxSize)
        .build<String, JIRClassOrInterface>()

    /**
     *
     */
    private val typesCache = CacheBuilder.newBuilder()
        .expireAfterAccess(expiration)
        .maximumSize(maxSize)
        .build<String, JIRType>()

    override fun tryFindClass(classpath: JIRClasspath, name: String): JIRClassOrInterface? {
        return classesCache.getIfPresent(name)
    }

    override fun on(event: JIRClasspathFeatureEvent) {
        when(event) {
            is JIRClassFoundEvent -> classesCache.put(event.clazz.name, event.clazz)
            is JIRTypeFoundEvent -> typesCache.put(event.type.typeName, event.type)
        }
    }
}