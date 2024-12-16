package org.opentaint.opentaint-ir.impl.analysis.features

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.analysis.JIRAnalysisFeature
import org.opentaint.opentaint-ir.api.cfg.JIRGraph

class JIRCacheGraphFeature(maxSize: Long) : JIRAnalysisFeature {

    private val cache: LoadingCache<JIRMethod, JIRGraph> = CacheBuilder.newBuilder()
        .softValues()
        .maximumSize(maxSize)
        .build(object : CacheLoader<JIRMethod, JIRGraph>() {
            override fun load(method: JIRMethod): JIRGraph {
                return method.flowGraph()
            }
        })

    override fun flowOf(method: JIRMethod): JIRGraph? {
        return cache.getIfPresent(method)
    }

    override fun transform(graph: JIRGraph): JIRGraph {
        cache.put(graph.method, graph)
        return graph
    }
}