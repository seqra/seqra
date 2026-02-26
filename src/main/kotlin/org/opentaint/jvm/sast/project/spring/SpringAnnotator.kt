package org.opentaint.jvm.sast.project.spring

import io.github.detekt.sarif4k.PropertyBag
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.ast.AstSpanResolverProvider
import org.opentaint.jvm.sast.dataflow.matchedAnnotations
import org.opentaint.jvm.sast.project.SarifWebInfoAnnotator
import org.opentaint.jvm.sast.sarif.TracePathNode
import org.opentaint.jvm.sast.sarif.getMethod
import org.opentaint.jvm.sast.sarif.isPureEntryPoint

class SpringAnnotator(
    sourceFileResolver: JIRSourceFileResolver,
    spanResolver: AstSpanResolverProvider,
) : SarifWebInfoAnnotator(sourceFileResolver, spanResolver) {
    private sealed interface SpringParamType {
        data object Other : SpringParamType

        data object RequestBody : SpringParamType

        data class RequestParam(val name: String) : SpringParamType

        data class PathVariable(val name: String) : SpringParamType

        data class ModelAttribute(val name: String) : SpringParamType
    }

    private data class SpringControllerParams(
        val params: List<String>
    ) : ControllerParams

    override fun JIRMethod.isController(): Boolean =
        isSpringControllerMethod()

    override fun createControllerInfo(
        controllers: List<JIRMethod>,
        vulnerability: TaintSinkTracker.TaintVulnerability,
        trace: TraceResolver.Trace?,
        tracePaths: List<List<TracePathNode>>
    ): List<ControllerInfo> {
        val tainted = collectTaintedArguments(tracePaths.flatten())
        return controllers.map { controller ->
            val paths = controller.extractSpringPath()

            val taints = tainted[controller]
            val params = taints?.let { createControllerParams(controller, it) }

            ControllerInfo(controller, paths, params)
        }
    }

    override fun ControllerInfo.paramsToProperties(): PropertyBag? {
        val springParams = params as? SpringControllerParams ?: return null
        return PropertyBag(tags = springParams.params)
    }

    private fun SpringParamType.makeProperty(): String? =
        when (this) {
            is SpringParamType.RequestParam -> "query: $name"
            is SpringParamType.PathVariable -> "path: $name"
            is SpringParamType.ModelAttribute -> "query: $name"
            is SpringParamType.RequestBody -> "body"
            else -> null
        }

    private fun createControllerParams(method: JIRMethod, params: Map<Int, InitialFactAp>): SpringControllerParams? {
        val springParams = params.map { (idx, fact) -> getSpringParamType(method, idx, fact) }
        val properties = springParams.mapNotNull { it.makeProperty() }
            .takeIf { it.isNotEmpty() }
            ?: return null
        return SpringControllerParams(properties)
    }

    private fun collectTaintedArguments(nodes: List<TracePathNode>): Map<JIRMethod, Map<Int, InitialFactAp>> {
        val result = hashMapOf<JIRMethod, MutableMap<Int, InitialFactAp>>()

        for (node in nodes) {
            if (node.entry.isPureEntryPoint() && node.entry != null) {
                node.entry.edges.forEach {
                    val method = node.getMethod()
                    val base = it.fact.base
                    if (base is AccessPathBase.Argument) {
                        result.getOrPut(method as JIRMethod, ::hashMapOf)[base.idx] = it.fact
                    }
                }
            }
        }

        return result
    }

    private fun getParamNameFromSource(method: JIRMethod, paramIdx: Int): String? {
        if (method.instList.size == 0) return null
        val firstInst = method.instList.first()
        val src = sourceFileResolver.resolveByInst(firstInst) ?: return null
        return spanResolver.resolver(src.language).getParameterName(src.path, firstInst, paramIdx)
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

    private fun getModelAttribute(param: JIRParameter, fact: InitialFactAp): SpringParamType? {
        // todo: handle non-primitive params without annotation
        val modelAttribute = param.matchedAnnotations(String::isSpringModelAttribute)
            .singleOrNull()
            ?: return null

        // todo: check if model is provided by another controller method
        val fields = extractFieldNames(fact)
            .takeIf { it.isNotEmpty() }
            ?: return null

        val paramName = fields.joinToString(".")
        return SpringParamType.ModelAttribute(paramName)
    }

    private fun getRequestBody(param: JIRParameter): SpringParamType? =
        param.matchedAnnotations(String::isSpringRequestBody)
            .singleOrNull()?.let { SpringParamType.RequestBody }

    private val paramGetters: List<(JIRParameter) -> SpringParamType?> = listOf(
        ::getRequestParam,
        ::getPathVariable,
        ::getRequestBody,
    )

    private fun getSpringParamType(method: JIRMethod, paramIdx: Int, fact: InitialFactAp): SpringParamType {
        val param = method.parameters.getOrNull(paramIdx)
            ?: return SpringParamType.Other

        paramGetters.firstNotNullOfOrNull { getter -> getter(param) }?.let { return it }
        getModelAttribute(param, fact)?.let { return it }

        return SpringParamType.Other
    }

    private fun JIRMethod.extractSpringPath(): List<ControllerPathInfo> {
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

        return paths.flatMap { p -> methods.map { m -> ControllerPathInfo(p, m) } }
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
        "/${base.removePathSeparator()}/${other.removePathSeparator()}"

    private fun String.removePathSeparator() = trim().trim('/')

    private fun extractFieldNames(fact: InitialFactAp): List<String> {
        val field = fact.getStartAccessors()
            .filterIsInstance<FieldAccessor>()
            .singleOrNull()
            ?: return emptyList()

        val tail = fact.readAccessor(field)?.let { extractFieldNames(it) }.orEmpty()
        return listOf(field.fieldName) + tail
    }
}
