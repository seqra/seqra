package org.opentaint.jvm.sast.project.spring

import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.PropertyBag
import io.github.detekt.sarif4k.Result
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.util.forEach
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.ast.JavaAstSpanResolver
import org.opentaint.jvm.sast.dataflow.matchedAnnotations
import org.opentaint.jvm.sast.sarif.TracePathNode
import org.opentaint.jvm.sast.sarif.getMethod
import org.opentaint.jvm.sast.sarif.isPureEntryPoint
import java.util.BitSet
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private data class SpringControllerPath(
    val path: String,
    val method: String?
)

private sealed interface SpringParamType {
    data object Other : SpringParamType

    data object ModelAttribute : SpringParamType

    data object RequestBody : SpringParamType

    data class RequestParam(val name : String) : SpringParamType

    data class PathVariable(val name : String) : SpringParamType
}

class SpringAnnotator(
    private val sourceFileResolver: JIRSourceFileResolver,
    private val spanResolver: JavaAstSpanResolver,
) {
    fun annotateSarifWithSpringRelatedInformation(
        result: Result,
        vulnerability: TaintSinkTracker.TaintVulnerability,
        trace: TraceResolver.Trace?,
        tracePaths: List<List<TracePathNode>>,
        generateStatementLocation: (JIRInst) -> Location,
    ): Result {
        val relevantMethods = vulnRelevantMethods(vulnerability, trace)
        val relevantControllers = relevantMethods
            .filterIsInstance<JIRMethod>()
            .filter { it.isSpringControllerMethod() }

        val tainted = collectTaintedArguments(tracePaths.flatten())

        if (relevantControllers.isEmpty()) return result

        val relatedLocations = result.relatedLocations.orEmpty().toMutableList()
        for (controller in relevantControllers) {
            val firstInst = controller.instList.firstOrNull() ?: continue
            val paths = controller.extractSpringPath()

            val taints = tainted.getOrDefault(controller, BitSet())
            val propertyBag = createProperties(controller, taints)

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
            relatedLocations += Location(
                logicalLocations = logicalLoc,
                physicalLocation = loc.physicalLocation,
                message = Message(text = "Related Spring controller")
            )
        }
        return result.copy(relatedLocations = relatedLocations)
    }

    private fun SpringParamType.makeProperty(): String =
        when (this) {
            is SpringParamType.RequestParam -> "query: $name"
            is SpringParamType.PathVariable -> "path: $name"
            is SpringParamType.RequestBody -> "body"
            else -> error("can't make property string from $this")
        }

    private fun createProperties(method: JIRMethod, params: BitSet): PropertyBag? {
        val springParams = mutableListOf<SpringParamType>()
        params.forEach { springParams.add(getSpringParamType(method, it)) }
        if (springParams.isEmpty() || springParams.any { it is SpringParamType.Other || it is SpringParamType.ModelAttribute })
            return null
        val properties = springParams.map { it.makeProperty() }
        return PropertyBag(tags = properties)
    }

    private fun collectTaintedArguments(nodes: List<TracePathNode>): Map<JIRMethod, BitSet> {
        val result = hashMapOf<JIRMethod, BitSet>()

        for (node in nodes) {
            if (node.entry.isPureEntryPoint() && node.entry != null) {
                node.entry.edges.forEach {
                    val method = node.getMethod()
                    val base = it.fact.base
                    if (base is AccessPathBase.Argument)
                        result.getOrPut(method as JIRMethod, ::BitSet).set(base.idx)
                }
            }
        }

        return result
    }

    private fun getParamNameFromSource(method: JIRMethod, paramIdx: Int): String? {
        if (method.instList.size == 0) return null
        val firstInst = method.instList.first()
        val src = sourceFileResolver.resolveByInst(firstInst) ?: return null
        return spanResolver.getParameterName(src, firstInst, paramIdx)
    }

    private fun getSpringParamType(
        param: JIRParameter,
        predicate: (String) -> Boolean,
        creator: (String) -> SpringParamType
    ): SpringParamType? =
        param.matchedAnnotations(predicate)
            .singleOrNull()
            ?.let { pathVariableAnnotation ->
                val pathName = pathVariableAnnotation.values["value"] as? String
                    ?: param.name ?: getParamNameFromSource(param.method, param.index) ?: return null
                creator(pathName)
            }

    private fun getPathVariable(param: JIRParameter): SpringParamType? =
        getSpringParamType(param, String::isSpringPathVariable) {
            SpringParamType.PathVariable(it)
        }

    private fun getRequestParam(param: JIRParameter): SpringParamType? =
        getSpringParamType(param, String::isSpringRequestParam) {
            SpringParamType.RequestParam(it)
        }

    private fun getModelAttribute(param: JIRParameter): SpringParamType? =
        param.matchedAnnotations(String::isSpringModelAttribute)
            .singleOrNull()?.let { SpringParamType.ModelAttribute }

    private fun getRequestBody(param: JIRParameter): SpringParamType? =
        param.matchedAnnotations(String::isSpringRequestBody)
            .singleOrNull()?.let { SpringParamType.RequestBody }

    private val paramGetters: List<(JIRParameter) -> SpringParamType?> = listOf(
        ::getModelAttribute,
        ::getRequestParam,
        ::getPathVariable,
        ::getRequestBody,
    )

    private fun getSpringParamType(method: JIRMethod, paramIdx: Int): SpringParamType {
        val params = method.parameters
        if (params.size <= paramIdx) return SpringParamType.Other
        val param = params[paramIdx]
        var springParam: SpringParamType?
        for (getter in paramGetters) {
            springParam = getter(param)
            if (springParam != null) return springParam
        }
        return SpringParamType.Other
    }

    private fun JIRMethod.extractSpringPath(): List<SpringControllerPath> {
        val classRequestMapping = enclosingClass.collectSpringRequestMappingAnnotation()?.firstOrNull()

        val methodAnnotations = collectSpringControllerAnnotations()
        val methodRequestMapping = methodAnnotations?.firstOrNull { it.name.isSpringRequestMappingAnnotation() }
        val methodMethodMapping = methodAnnotations?.firstOrNull { it.name.isSpringMethodAnnotation() }

        val classPaths = classRequestMapping?.let { extractPathsFromRequestMapping(it) }
        val classMethods = classRequestMapping?.let { extractMethodsFromRequestMapping(it) }

        val methodPaths = methodMethodMapping?.let { extractPathsFromMethodMapping(it) }
            ?: methodRequestMapping?.let { extractPathsFromRequestMapping(it) }

        val methodMethods = methodMethodMapping?.let { extractMethodsFromMethodMapping(it) }
            ?: methodRequestMapping?.let { extractMethodsFromRequestMapping(it) }

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
