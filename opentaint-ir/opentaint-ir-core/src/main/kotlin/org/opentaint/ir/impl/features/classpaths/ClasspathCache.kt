package org.opentaint.ir.impl.features.classpaths

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import org.opentaint.ir.api.JIRClassFoundEvent
import org.opentaint.ir.api.JIRClassNotFound
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathExtFeature
import org.opentaint.ir.api.JIRClasspathFeatureEvent
import org.opentaint.ir.api.JIRInstExtFeature
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.api.JIRMethodRef
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeFoundEvent
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.impl.JIRCacheSettings
import org.opentaint.ir.impl.cfg.JIRGraphBuilder
import org.opentaint.ir.impl.cfg.JIRMethodRefImpl
import org.opentaint.ir.impl.cfg.RawInstListBuilder
import org.opentaint.ir.impl.weakLazy
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

    private val graphCache = segmentBuilder(settings.graphs)
        .build(object : CacheLoader<JIRMethodRef, JIRGraphHolder>() {
            override fun load(key: JIRMethodRef): JIRGraphHolder {
                return JIRGraphHolder(key)
            }
        });

    override fun tryFindClass(classpath: JIRClasspath, name: String): Optional<JIRClassOrInterface>? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JIRClasspath, name: String): Optional<JIRType>? {
        return typesCache.getIfPresent(name)
    }

    override fun flowGraph(method: JIRMethod): JIRGraph = method.holder().flowGraph

    private fun JIRMethod.holder(): JIRGraphHolder {
        return graphCache.getUnchecked(JIRMethodRefImpl(this)).also {
            it.bind(enclosingClass.classpath)
        }
    }

    override fun instList(method: JIRMethod): JIRInstList<JIRInst> = method.holder().instList

    override fun rawInstList(method: JIRMethod): JIRInstList<JIRRawInst> =
        method.holder().rawInstList

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

    protected fun segmentBuilder(settings: Pair<Long, Duration>): CacheBuilder<Any, Any> {
        val maxSize = settings.first
        val expiration = settings.second

        return CacheBuilder.newBuilder()
            .expireAfterAccess(expiration)
            .softValues()
            .maximumSize(maxSize)
    }
}

class JIRGraphHolder(private val methodRef: JIRMethodRef) {

    private val method get() = methodRef.method
    private lateinit var classpath: JIRClasspath
    private lateinit var methodFeatures: List<JIRInstExtFeature>

    fun bind(classpath: JIRClasspath) {
        this.classpath = classpath
        this.methodFeatures = classpath.features?.filterIsInstance<JIRInstExtFeature>().orEmpty()
    }

    val rawInstList: JIRInstList<JIRRawInst> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val list: JIRInstList<JIRRawInst> = RawInstListBuilder(method, method.asmNode()).build()
        methodFeatures.fold(list) { value, feature ->
            feature.transformRawInstList(method, value)
        }
    }

    val flowGraph by lazy(LazyThreadSafetyMode.PUBLICATION) {
        JIRGraphBuilder(method, rawInstList).buildFlowGraph()
    }

    val instList: JIRInstList<JIRInst> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val list: JIRInstList<JIRInst> = JIRGraphBuilder(method, rawInstList).buildInstList()
        methodFeatures.fold(list) { value, feature ->
            feature.transformInstList(method, value)
        }
    }

}
