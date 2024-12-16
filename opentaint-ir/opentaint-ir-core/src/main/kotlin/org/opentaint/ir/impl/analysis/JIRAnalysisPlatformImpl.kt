package org.opentaint.opentaint-ir.impl.analysis

import org.opentaint.opentaint-ir.api.JIRClassOrInterface
import org.opentaint.opentaint-ir.api.JIRClassProcessingTask
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.analysis.JIRAnalysisFeature
import org.opentaint.opentaint-ir.api.analysis.JIRAnalysisPlatform
import org.opentaint.opentaint-ir.api.analysis.JIRCollectingAnalysisFeature
import org.opentaint.opentaint-ir.api.cfg.JIRGraph

open class JIRAnalysisPlatformImpl(
    override val classpath: JIRClasspath,
    override val features: List<JIRAnalysisFeature> = emptyList()
) : JIRAnalysisPlatform, JIRClassProcessingTask {

    private val collectors = features.filterIsInstance<JIRCollectingAnalysisFeature>()

    override fun flowGraph(method: JIRMethod): JIRGraph {
        var index = 0
        var maybeCached: JIRGraph? = null
        features.forEachIndexed { i, feature ->
            maybeCached = feature.flowOf(method)
            if (maybeCached != null) {
                index = i
            }
        }
        val initial = maybeCached ?: method.flowGraph()
        return features.drop(index).fold(initial) { value, feature ->
            feature.transform(value)
        }
    }

    override suspend fun collect() {
        if (collectors.isNotEmpty()) {
            classpath.execute(this)
        }
    }

    override fun process(clazz: JIRClassOrInterface) {
        clazz.declaredMethods.forEach { method ->
            val flowGraph = flowGraph(method)
            collectors.forEach { feature ->
                feature.collect(method, flowGraph)
            }
        }
    }
}