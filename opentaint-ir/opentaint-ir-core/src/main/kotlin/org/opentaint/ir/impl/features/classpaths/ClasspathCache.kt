package org.opentaint.ir.impl.features.classpaths

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheStats
import mu.KLogging
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathExtFeature
import org.opentaint.ir.api.JIRClasspathExtFeature.JIRResolvedClassResult
import org.opentaint.ir.api.JIRClasspathExtFeature.JIRResolvedTypeResult
import org.opentaint.ir.api.JIRFeatureEvent
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.api.JIRMethodExtFeature.JIRFlowGraphResult
import org.opentaint.ir.api.JIRMethodExtFeature.JIRInstListResult
import org.opentaint.ir.api.JIRMethodExtFeature.JIRRawInstListResult
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.impl.JIRCacheSegmentSettings
import org.opentaint.ir.impl.JIRCacheSettings
import org.opentaint.ir.impl.ValueStoreType
import org.opentaint.ir.impl.features.classpaths.AbstractJIRInstResult.JIRFlowGraphResultImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRInstResult.JIRInstListResultImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRInstResult.JIRRawInstListResultImpl
import java.text.NumberFormat

/**
 * any class cache should extend this class
 */
open class ClasspathCache(settings: JIRCacheSettings) : JIRClasspathExtFeature, JIRMethodExtFeature {

    companion object : KLogging()

    private val classesCache = segmentBuilder(settings.classes)
        .build<String, JIRResolvedClassResult>()

    private val typesCache = segmentBuilder(settings.types)
        .build<String, JIRResolvedTypeResult>()

    private val rawInstCache = segmentBuilder(settings.rawInstLists)
        .build<JIRMethod, JIRInstList<JIRRawInst>>()

    private val instCache = segmentBuilder(settings.instLists)
        .build<JIRMethod, JIRInstList<JIRInst>>()

    private val cfgCache = segmentBuilder(settings.flowGraphs)
        .build<JIRMethod, JIRGraph>()

    override fun tryFindClass(classpath: JIRClasspath, name: String): JIRResolvedClassResult? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JIRClasspath, name: String): JIRResolvedTypeResult? {
        return typesCache.getIfPresent(name)
    }

    override fun flowGraph(method: JIRMethod) = cfgCache.getIfPresent(method)?.let {
        JIRFlowGraphResultImpl(method, it)
    }
    override fun instList(method: JIRMethod) = instCache.getIfPresent(method)?.let {
        JIRInstListResultImpl(method, it)
    }
    override fun rawInstList(method: JIRMethod) = rawInstCache.getIfPresent(method)?.let {
        JIRRawInstListResultImpl(method, it)
    }

    override fun on(event: JIRFeatureEvent) {
        when (val result = event.result) {
            is JIRResolvedClassResult -> classesCache.put(result.name, result)

            is JIRResolvedTypeResult -> {
                val found = result.type
                if (found != null && found is JIRClassType) {
                    typesCache.put(result.name, result)
                }
            }

            is JIRFlowGraphResult -> cfgCache.put(result.method, result.flowGraph)
            is JIRInstListResult -> instCache.put(result.method, result.instList)
            is JIRRawInstListResult -> rawInstCache.put(result.method, result.rawInstList)
        }
    }

    protected fun segmentBuilder(settings: JIRCacheSegmentSettings)
            : CacheBuilder<Any, Any> {
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
                logger.info(
                    "$key cache hit rate: ${
                        stat.hitRate().forPercentages()
                    }, total count ${stat.requestCount()}"
                )
            }
    }

    protected fun Double.forPercentages(): String {
        return NumberFormat.getPercentInstance().format(this)
    }
}