package org.opentaint.ir.impl.features.classpaths

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRCacheSegmentSettings
import org.opentaint.ir.api.jvm.JIRCacheSettings
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature.JIRResolvedClassResult
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature.JIRResolvedTypeResult
import org.opentaint.ir.api.jvm.JIRFeatureEvent
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRMethodExtFeature
import org.opentaint.ir.api.jvm.JIRMethodExtFeature.JIRFlowGraphResult
import org.opentaint.ir.api.jvm.JIRMethodExtFeature.JIRInstListResult
import org.opentaint.ir.api.jvm.JIRMethodExtFeature.JIRRawInstListResult
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.impl.caches.PluggableCache
import org.opentaint.ir.impl.caches.PluggableCacheProvider
import org.opentaint.ir.impl.caches.PluggableCacheStats
import org.opentaint.ir.impl.caches.xodus.XODUS_CACHE_PROVIDER_ID
import org.opentaint.ir.impl.features.classpaths.AbstractJIRInstResult.JIRFlowGraphResultImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRInstResult.JIRInstListResultImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRInstResult.JIRRawInstListResultImpl
import java.text.NumberFormat

/**
 * any class cache should extend this class
 */
open class ClasspathCache(settings: JIRCacheSettings) : JIRClasspathExtFeature, JIRMethodExtFeature, KLogging() {

    private val cacheProvider = PluggableCacheProvider.getProvider(settings.cacheSpiId ?: XODUS_CACHE_PROVIDER_ID)

    private val classesCache = newSegment<String, JIRResolvedClassResult>(settings.classes)

    private val typesCache = newSegment<TypeKey, JIRResolvedTypeResult>(settings.types)

    private val rawInstCache = newSegment<JIRMethod, JIRInstList<JIRRawInst>>(settings.rawInstLists)

    private val instCache = newSegment<JIRMethod, JIRInstList<JIRInst>>(settings.instLists)

    private val cfgCache = newSegment<JIRMethod, JIRGraph>(settings.flowGraphs)

    private var javaObjectResolvedClass: JIRResolvedClassResult? = null
    private var javaObjectResolvedType: JIRResolvedTypeResult? = null
    private var javaObjectResolvedNotNullType: JIRResolvedTypeResult? = null
    private var javaObjectResolvedNullableType: JIRResolvedTypeResult? = null

    override fun tryFindClass(classpath: JIRClasspath, name: String): JIRResolvedClassResult? {
        return if (name == JAVA_OBJECT) javaObjectResolvedClass else classesCache[name]
    }

    override fun tryFindType(classpath: JIRClasspath, name: String, nullable: Boolean?): JIRResolvedTypeResult? {
        if (name == JAVA_OBJECT) {
            return when (nullable) {
                null -> javaObjectResolvedType
                true -> javaObjectResolvedNullableType
                false -> javaObjectResolvedNotNullType
            }
        }
        return typesCache[TypeKey(name, nullable)]
    }

    override fun flowGraph(method: JIRMethod) = cfgCache[method]?.let {
        JIRFlowGraphResultImpl(method, it)
    }

    override fun instList(method: JIRMethod) = instCache[method]?.let {
        JIRInstListResultImpl(method, it)
    }

    override fun rawInstList(method: JIRMethod) = rawInstCache[method]?.let {
        JIRRawInstListResultImpl(method, it)
    }

    override fun on(event: JIRFeatureEvent) {
        when (val result = event.result) {
            is JIRResolvedClassResult -> {
                val name = result.name
                if (name == JAVA_OBJECT) {
                    javaObjectResolvedClass = result
                } else {
                    classesCache[name] = result
                }
            }

            is JIRResolvedTypeResult -> {
                val found = result.type
                if (found != null && found is JIRClassType) {
                    val nullable = found.nullable
                    val typeName = result.name
                    if (typeName == JAVA_OBJECT) {
                        when (nullable) {
                            null -> javaObjectResolvedType = result
                            true -> javaObjectResolvedNullableType = result
                            false -> javaObjectResolvedNotNullType = result
                        }
                    } else {
                        typesCache[TypeKey(typeName, nullable)] = result
                    }
                }
            }

            is JIRFlowGraphResult -> cfgCache[result.method] = result.flowGraph
            is JIRInstListResult -> instCache[result.method] = result.instList
            is JIRRawInstListResult -> rawInstCache[result.method] = result.rawInstList
        }
    }

    open fun stats(): Map<String, PluggableCacheStats> = buildMap {
        this["classes"] = classesCache.getStats()
        this["types"] = typesCache.getStats()
        this["cfg"] = cfgCache.getStats()
        this["raw-instructions"] = rawInstCache.getStats()
        this["instructions"] = instCache.getStats()
    }

    open fun dumpStats() {
        stats().entries.toList()
            .sortedBy { it.key }
            .forEach { (key, stat) ->
                logger.info(
                    "$key cache hit rate: ${
                        stat.hitRate.forPercentages()
                    }, total count ${stat.requestCount}"
                )
            }
    }

    private fun <K : Any, V : Any> newSegment(settings: JIRCacheSegmentSettings): PluggableCache<K, V> {
        with(settings) {
            return cacheProvider.newCache {
                maximumSize = maxSize.toInt()
                expirationDuration = expiration
                valueRefType = valueStoreType
            }
        }
    }

    private fun Double.forPercentages(): String {
        return NumberFormat.getPercentInstance().format(this)
    }

    private data class TypeKey(val typeName: String, val nullable: Boolean? = null)
}
