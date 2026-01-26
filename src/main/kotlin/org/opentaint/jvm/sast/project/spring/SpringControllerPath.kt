package org.opentaint.jvm.sast.project.spring

import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.Result
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private data class SpringControllerPath(
    val path: String,
    val method: String?
)

fun annotateSarifWithSpringRelatedInformation(
    result: Result,
    vulnerability: TaintSinkTracker.TaintVulnerability,
    trace: TraceResolver.Trace?,
    generateStatementLocation: (JIRInst) -> Location,
): Result {
    val relevantMethods = vulnRelevantMethods(vulnerability, trace)
    val relevantControllers = relevantMethods
        .filterIsInstance<JIRMethod>()
        .filter { it.isSpringControllerMethod() }

    if (relevantControllers.isEmpty()) return result

    val relatedLocations = result.relatedLocations.orEmpty().toMutableList()
    for (controller in relevantControllers) {
        val firstInst = controller.instList.firstOrNull() ?: continue
        val paths = controller.extractSpringPath()

        val logicalLoc = paths.mapIndexed { i, path ->
            LogicalLocation(
                fullyQualifiedName = "${path.method?.let { "$it " } ?: ""}${path.path}",
                index = i.toLong(),
                name = "${controller.enclosingClass.name}#${controller.name}",
                kind = "function"
            )
        }

        val loc = generateStatementLocation(firstInst)
        relatedLocations += Location(
            logicalLocations = logicalLoc,
            physicalLocation = loc.physicalLocation,
            message = Message(text = "Related Spring controller")
        )
    }
    return result.copy(relatedLocations = relatedLocations)
}

private fun JIRMethod.extractSpringPath(): List<SpringControllerPath> {
    val classRequestMapping = enclosingClass.collectSpringRequestMappingAnnotation()?.firstOrNull()

    val methodAnnotations = collectSpringControllerAnnotations()
    val methodRequestMapping = methodAnnotations?.firstOrNull { it.isSpringRequestMappingAnnotation() }
    val methodMethodMapping = methodAnnotations?.firstOrNull { it.isSpringMethodAnnotation() }

    val classPaths = classRequestMapping?.let { extractPathsFromRequestMapping(it) }
    val classMethods = classRequestMapping?.let { extractMethodsFromRequestMapping(it) }

    val methodPaths = when {
        methodRequestMapping != null -> extractPathsFromRequestMapping(methodRequestMapping)
        methodMethodMapping != null -> extractPathsFromMethodMapping(methodMethodMapping)
        else -> null
    }

    val methodMethods = when {
        methodRequestMapping != null -> extractMethodsFromRequestMapping(methodRequestMapping)
        methodMethodMapping != null -> extractMethodsFromMethodMapping(methodMethodMapping)
        else -> null
    }

    val methods = methodMethods ?: classMethods ?: setOf(null)
    val paths = when {
        classPaths == null -> methodPaths ?: emptyList()
        methodPaths == null -> classPaths
        else -> classPaths.flatMap { cp ->
            methodPaths.map { mp -> concatSpringPath(cp, mp) }
        }
    }

    return paths.flatMap { p -> methods.map { m -> SpringControllerPath(p, m) } }
}

@Suppress("UNCHECKED_CAST")
private fun extractPathsFromRequestMapping(rm: JIRAnnotation): Set<String>? {
    val value = rm.values["value"] as? List<String>
    val path = rm.values["path"] as? List<String>

    val paths = listOfNotNull(value, path).flatMapTo(hashSetOf()) { it }
    return paths.takeIf { it.isNotEmpty() }
}

// note: @(Post/Get/...)Mapping is an alias for @RequestMapping
private fun extractPathsFromMethodMapping(mm: JIRAnnotation): Set<String>? =
    extractPathsFromRequestMapping(mm)

private fun extractMethodsFromRequestMapping(rm: JIRAnnotation): Set<String>? {
    val method = rm.values["method"] ?: return null
    return (method as? List<*>)?.mapNotNullTo(hashSetOf()) { (it as? JIRField)?.name }?.takeIf { it.isNotEmpty() }
}

private fun extractMethodsFromMethodMapping(mm: JIRAnnotation): Set<String>? {
    val method = mm.jIRClass?.simpleName?.removeSuffix("Mapping")?.uppercase() ?: return null
    return setOf(method)
}

private fun concatSpringPath(base: String, other: String): String =
    Path(base.ensurePrefix("/")).resolve(other.removePrefix("/")).absolutePathString()

private fun String.ensurePrefix(prefix: String): String =
    if (startsWith(prefix)) this else "$prefix$this"

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

private fun collectRelevantMethods(e2sTrace: TraceResolver.EntryPointToStartTrace, methods: MutableSet<CommonMethod>) {
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
