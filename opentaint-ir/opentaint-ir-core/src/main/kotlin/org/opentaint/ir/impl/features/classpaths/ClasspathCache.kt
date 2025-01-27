package org.opentaint.ir.impl.features.classpaths

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.CacheStats
import org.opentaint.ir.api.JIRClassFoundEvent
import org.opentaint.ir.api.JIRClassNotFound
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathExtFeature
import org.opentaint.ir.api.JIRClasspathFeatureEvent
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeFoundEvent
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.impl.JIRCacheSettings
import org.opentaint.ir.impl.cfg.nonCachedFlowGraph
import org.opentaint.ir.impl.cfg.nonCachedInstList
import org.opentaint.ir.impl.cfg.nonCachedRawInstList
import java.time.Duration
import java.util.*

/**
 * any class cache should extend this class
 */
open class ClasspathCache(settings: JIRCacheSettings) : JIRClasspathExtFeature, JIRMethodExtFeature {
    /**
     *
     */
    private val classesCache = segmentBuilder(settings.classes)
        .build<String, Optional<JIRClassOrInterface>>()

    private val typesCache = segmentBuilder(settings.types)
        .build<String, Optional<JIRType>>()

    private val rawInstCache = segmentBuilder(settings.graphs)
        .build(object : CacheLoader<JIRMethod, JIRInstList<JIRRawInst>>() {
            override fun load(key: JIRMethod): JIRInstList<JIRRawInst> {
                return nonCachedRawInstList(key)
            }
        });

    private val instCache = segmentBuilder(settings.graphs, weakValues = true)
        .build(object : CacheLoader<JIRMethod, JIRInstList<JIRInst>>() {
            override fun load(key: JIRMethod): JIRInstList<JIRInst> {
                return nonCachedInstList(key)
            }
        });

    private val cfgCache = segmentBuilder(settings.graphs, weakValues = true)
        .build(object : CacheLoader<JIRMethod, JIRGraph>() {
            override fun load(key: JIRMethod): JIRGraph {
                return nonCachedFlowGraph(key)
            }
        });

    override fun tryFindClass(classpath: JIRClasspath, name: String): Optional<JIRClassOrInterface>? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JIRClasspath, name: String): Optional<JIRType>? {
        return typesCache.getIfPresent(name)
    }

    override fun flowGraph(method: JIRMethod) = cfgCache.getUnchecked(method)
    override fun instList(method: JIRMethod) = instCache.getUnchecked(method)
    override fun rawInstList(method: JIRMethod) = rawInstCache.getUnchecked(method)

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

    protected fun segmentBuilder(settings: Pair<Long, Duration>, weakValues: Boolean = false): CacheBuilder<Any, Any> {
        val maxSize = settings.first
        val expiration = settings.second

        return CacheBuilder.newBuilder()
            .expireAfterAccess(expiration)
            .recordStats()
            .maximumSize(maxSize).let {
                if (weakValues) {
                    it.weakValues()
                } else {
                    it.softValues()
                }
            }
    }

    open fun stats(): Map<String, CacheStats> = buildMap {
        this["classes"] = classesCache.stats()
        this["types"] = typesCache.stats()
        this["cfg"] = cfgCache.stats()
        this["raw-instructions"] = rawInstCache.stats()
        this["instructions"] = instCache.stats()
    }
}