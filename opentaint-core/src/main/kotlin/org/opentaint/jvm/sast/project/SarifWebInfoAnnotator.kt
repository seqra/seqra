package org.opentaint.jvm.sast.project

import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.PropertyBag
import io.github.detekt.sarif4k.Result
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.ast.AstSpanResolverProvider
import org.opentaint.jvm.sast.sarif.TracePathNode

abstract class SarifWebInfoAnnotator(
    val sourceFileResolver: JIRSourceFileResolver,
    val spanResolver: AstSpanResolverProvider,
) {
    interface ControllerParams

    data class ControllerPathInfo(
        val path: String,
        val method: String?,
    )

    data class ControllerInfo(
        val controller: JIRMethod,
        val pathInfo: List<ControllerPathInfo>,
        val params: ControllerParams?,
    )

    abstract fun JIRMethod.isController(): Boolean

    abstract fun createControllerInfo(
        controllers: List<JIRMethod>,
        vulnerability: TaintSinkTracker.TaintVulnerability,
        trace: TraceResolver.Trace?,
        tracePaths: List<List<TracePathNode>>,
    ): List<ControllerInfo>

    abstract fun ControllerInfo.paramsToProperties(): PropertyBag?

    fun annotateSarif(
        result: Result,
        vulnerability: TaintSinkTracker.TaintVulnerability,
        trace: TraceResolver.Trace?,
        tracePaths: List<List<TracePathNode>>,
        generateStatementLocation: (JIRInst) -> Location?
    ): Result {
        val relevantMethods = vulnRelevantMethods(vulnerability, trace)
        val relevantControllers = relevantMethods
            .filterIsInstance<JIRMethod>()
            .filter { it.isController() }

        if (relevantControllers.isEmpty()) return result

        val relevantControllerInfo = createControllerInfo(relevantControllers, vulnerability, trace, tracePaths)

        val relatedLocations = result.relatedLocations.orEmpty().toMutableList()
        for (controllerInfo in relevantControllerInfo) {
            val controller = controllerInfo.controller
            val firstInst = controller.instList.firstOrNull() ?: continue
            val paths = controllerInfo.pathInfo
            val propertyBag = controllerInfo.paramsToProperties()

            val logicalLoc = paths.mapIndexed { i, path ->
                LogicalLocation(
                    fullyQualifiedName = "${path.method?.let { "$it " } ?: ""}${path.path}",
                    index = i.toLong(),
                    name = "${controller.enclosingClass.name}#${controller.name}",
                    kind = "function",
                    properties = propertyBag
                )
            }

            val loc = generateStatementLocation(firstInst)
                ?: continue

            relatedLocations += Location(
                logicalLocations = logicalLoc,
                physicalLocation = loc.physicalLocation,
                message = Message(text = "Related Spring controller")
            )
        }
        return result.copy(relatedLocations = relatedLocations)
    }

    private fun vulnRelevantMethods(
        vulnerability: TaintSinkTracker.TaintVulnerability,
        trace: TraceResolver.Trace?
    ): Set<CommonMethod> {
        val methods = hashSetOf<CommonMethod>()
        methods.add(vulnerability.statement.location.method)
        methods.add(vulnerability.methodEntryPoint.method)

        trace?.sourceToSinkTrace?.let { collectRelevantMethods(it, methods) }
        trace?.entryPointToStart?.let { collectRelevantMethods(it, methods) }

        return methods
    }

    private fun collectRelevantMethods(
        e2sTrace: TraceResolver.EntryPointToStartTrace,
        methods: MutableSet<CommonMethod>
    ) {
        e2sTrace.entryPoints.forEach { collectRelevantMethod(it, methods) }
        e2sTrace.successors.forEach { (k, v) ->
            collectRelevantMethod(k, methods)
            v.forEach { collectRelevantMethod(it, methods) }
        }
    }

    private fun collectRelevantMethods(s2sTrace: TraceResolver.SourceToSinkTrace, methods: MutableSet<CommonMethod>) {
        s2sTrace.startNodes.forEach { collectRelevantMethod(it, methods) }
        s2sTrace.sinkNodes.forEach { collectRelevantMethod(it, methods) }
        for ((node, successors) in s2sTrace.successors) {
            collectRelevantMethod(node, methods)
            successors.forEach { collectRelevantMethod(it.node, methods) }
        }
    }

    private fun collectRelevantMethod(node: TraceResolver.TraceNode, methods: MutableSet<CommonMethod>) {
        methods += when (node) {
            is TraceResolver.CallTraceNode -> node.methodEntryPoint.method
            is TraceResolver.EntryPointTraceNode -> node.method
            is TraceResolver.SourceToSinkTraceNode -> node.methodEntryPoint.method
        }
    }
}
