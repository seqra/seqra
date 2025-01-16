package org.opentaint.ir.impl.features.classpaths

import com.google.common.cache.CacheBuilder
import org.opentaint.ir.api.*
import java.time.Duration
import java.util.*

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
        .softValues()
        .maximumSize(maxSize)
        .build<String, Optional<JIRClassOrInterface>>()

    /**
     *
     */
    private val typesCache = CacheBuilder.newBuilder()
        .expireAfterAccess(expiration)
        .softValues()
        .maximumSize(maxSize)
        .build<String, Optional<JIRType>>()

    override fun tryFindClass(classpath: JIRClasspath, name: String): Optional<JIRClassOrInterface>? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JIRClasspath, name: String): Optional<JIRType>? {
        return typesCache.getIfPresent(name)
    }

    override fun on(event: JIRClasspathFeatureEvent) {
        when (event) {
            is JIRClassFoundEvent -> classesCache.put(event.clazz.name, Optional.of(event.clazz))
            is JIRClassNotFound -> classesCache.put(event.name, Optional.empty())
            is JIRTypeFoundEvent -> {
                val type = event.type
                if (type is JIRClassType && type.typeParameters.isEmpty()) {
                    typesCache.put(event.type.typeName, Optional.of(event.type))
                }
            }
        }
    }
}