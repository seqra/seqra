package org.opentaint.ir.impl.features.classpaths

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheStats
import mu.KLogging
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathExtFeature
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.impl.JIRCacheSegmentSettings
import org.opentaint.ir.impl.JIRCacheSettings
import org.opentaint.ir.impl.ValueStoreType
import java.text.NumberFormat
import java.util.*

/**
 * any class cache should extend this class
 */
open class ClasspathCache(settings: JIRCacheSettings) : JIRClasspathExtFeature, JIRMethodExtFeature {

    companion object : KLogging()

    private val classesCache = segmentBuilder(settings.classes)
        .build<String, Optional<JIRClassOrInterface>>()

    private val typesCache = segmentBuilder(settings.types)
        .build<String, Optional<JIRType>>()

    private val rawInstCache = segmentBuilder(settings.rawInstLists)
        .build<JIRMethod, JIRInstList<JIRRawInst>>()

    private val instCache = segmentBuilder(settings.instLists)
        .build<JIRMethod, JIRInstList<JIRInst>>()

    private val cfgCache = segmentBuilder(settings.flowGraphs)
        .build<JIRMethod, JIRGraph>()

    override fun tryFindClass(classpath: JIRClasspath, name: String): Optional<JIRClassOrInterface>? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JIRClasspath, name: String): Optional<JIRType>? {
        return typesCache.getIfPresent(name)
    }

    override fun flowGraph(method: JIRMethod) = cfgCache.getIfPresent(method)
    override fun instList(method: JIRMethod) = instCache.getIfPresent(method)
    override fun rawInstList(method: JIRMethod) = rawInstCache.getIfPresent(method)

    override fun on(result: Any?, vararg input: Any) {
        if (result == null) {
            return
        }
        when (result) {
            is Optional<*> -> {
                if (result.isPresent) {
                    val found = result.get()
                    if (found is JIRClassOrInterface) {
                        classesCache.put(found.name, Optional.of(found))
                    } else if (found is JIRClassType && found.typeParameters.isEmpty()) {
                        typesCache.put(found.typeName, Optional.of(found))
                    }
                } else {
                    val name = input[0] as String
                    classesCache.put(name, Optional.empty())
                    typesCache.put(name, Optional.empty())
                }
            }

            is JIRGraph -> {
                val method = input[0] as JIRMethod
                cfgCache.put(method, result)
            }

            is JIRInstList<*> -> {
                val method = input[0] as JIRMethod
                if (result.instructions.isEmpty()) {
                    instCache.put(method, result as JIRInstList<JIRInst>)
                    rawInstCache.put(method, result as JIRInstList<JIRRawInst>)
                    return
                }
                if (result.instructions.first() is JIRInst) {
                    instCache.put(method, result as JIRInstList<JIRInst>)
                } else {
                    rawInstCache.put(method, result as JIRInstList<JIRRawInst>)
                }
            }
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