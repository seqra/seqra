package org.opentaint.dataflow.jvm.taint

import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.taint.TaintManager

fun jirTaintManager(
    graph: ApplicationGraph<JIRMethod, JIRInst>,
    unitResolver: JIRUnitResolver,
    useBidiRunner: Boolean = false,
    getConfigForMethod: ((JIRMethod) -> List<TaintConfigurationItem>?)? = null
): TaintManager<JIRMethod, JIRInst> = with(JIRTraits) {
    val config: (JIRMethod) -> List<TaintConfigurationItem>? = getConfigForMethod ?: run {
        val taintConfigurationFeature = (graph.project as JIRClasspath).features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }

        return@run { method: JIRMethod -> taintConfigurationFeature?.getConfigForMethod(method) }
    }

    TaintManager(graph, unitResolver, useBidiRunner, config)
}
