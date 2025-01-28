package org.opentaint.ir.impl.features.classpaths

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.CacheStats
import mu.KLogging
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
import org.opentaint.ir.impl.JIRCacheSegmentSettings
import org.opentaint.ir.impl.JIRCacheSettings
import org.opentaint.ir.impl.ValueStoreType
import org.opentaint.ir.impl.cfg.nonCachedFlowGraph
import org.opentaint.ir.impl.cfg.nonCachedInstList
import org.opentaint.ir.impl.cfg.nonCachedRawInstList
import java.text.NumberFormat
import java.util.*

/**
 * any class cache should extend this class
 */
open class ClasspathCache(settings: JIRCacheSettings) : JIRClasspathExtFeature, JIRMethodExtFeature {

    companion object : KLogging()

    /**
     *
     */
    private val classesCache = segmentBuilder(settings.classes)
        .build<String, Optional<JIRClassOrInterface>>()

    private val typesCache = segmentBuilder(settings.types)
        .build<String, Optional<JIRType>>()

    private val rawInstCache = segmentBuilder(settings.rawInstLists)
        .build(object : CacheLoader<JIRMethod, JIRInstList<JIRRawInst>>() {
            override fun load(key: JIRMethod): JIRInstList<JIRRawInst> {
                return nonCachedRawInstList(key)
            }
        });

    private val instCache = segmentBuilder(settings.instLists)
        .build(object : CacheLoader<JIRMethod, JIRInstList<JIRInst>>() {
            override fun load(key: JIRMethod): JIRInstList<JIRInst> {
                return nonCachedInstList(key)
            }
        });

    private val cfgCache = segmentBuilder(settings.flowGraphs)
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

    protected fun segmentBuilder(settings: JIRCacheSegmentSettings): CacheBuilder<Any, Any> {
        val maxSize = settings.maxSize
        val expiration = settings.expiration

        return CacheBuilder.newBuilder()
            .expireAfterAccess(expiration)
            .recordStats()
            .maximumSize(maxSize).let {
                when (settings.valueStoreType) {
                    ValueStoreType.WEAK -> it.weakValues()
                    ValueStoreType.SOFT -> it.softValues()
                    else -> it
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

    open fun dumpStats() {
        stats().entries.toList()
            .sortedBy { it.key }
            .forEach { (key, stat) ->
                logger.info("$key cache hit rate: ${stat.hitRate().forPercentages()}, total count ${stat.requestCount()}")
            }
    }

    protected fun Double.forPercentages(): String {
        return NumberFormat.getPercentInstance().format(this)
    }
}