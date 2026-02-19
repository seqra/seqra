package org.opentaint.jvm.sast.project.servlet

import io.github.detekt.sarif4k.PropertyBag
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.ast.JavaAstSpanResolver
import org.opentaint.jvm.sast.project.SarifWebInfoAnnotator
import org.opentaint.jvm.sast.sarif.TracePathNode

class ServletAnnotator(
    sourceFileResolver: JIRSourceFileResolver,
    spanResolver: JavaAstSpanResolver
) : SarifWebInfoAnnotator(sourceFileResolver, spanResolver) {
    private sealed interface ServletParam {
        data object Body : ServletParam
        data class Query(val name: String) : ServletParam
    }

    private data class ServletParams(val params: Set<ServletParam>) : ControllerParams

    override fun JIRMethod.isController(): Boolean =
        isWebServletMethod()

    override fun createControllerInfo(
        controllers: List<JIRMethod>,
        vulnerability: TaintSinkTracker.TaintVulnerability,
        trace: TraceResolver.Trace?,
        tracePaths: List<List<TracePathNode>>
    ): List<ControllerInfo> {
        val params = relevantServletParams(tracePaths)
        return controllers.map { controller ->
            val pathInfo = controller.extractPathInfo()
            ControllerInfo(controller, pathInfo, params)
        }
    }

    override fun ControllerInfo.paramsToProperties(): PropertyBag? {
        val servletParams = params as? ServletParams ?: return null
        val properties = servletParams.params.map { it.makeProperty() }
        return PropertyBag(tags = properties)
    }

    private fun ServletParam.makeProperty(): String =
        when (this) {
            is ServletParam.Query -> "query: $name"
            is ServletParam.Body -> "body"
        }

    private fun JIRMethod.extractPathInfo(): List<ControllerPathInfo> {
        val annotations = collectWebServletAnnotations() ?: return emptyList()
        val paths = annotations.mapNotNull { it.pathInfo() }.flatten()
        val method = name.removePrefix("do").uppercase()
        return paths.map { ControllerPathInfo(it, method) }
    }

    private fun JIRAnnotation.pathInfo(): List<String>? {
        @Suppress("UNCHECKED_CAST")
        return values["value"] as? List<String>
    }

    private fun relevantServletParams(tracePaths: List<List<TracePathNode>>): ServletParams? =
        tracePaths
            .flatMapTo(hashSetOf()) { nodes ->
                nodes.mapNotNull { (it.statement as? JIRInst)?.servletParam() }
            }
            .takeIf { it.isNotEmpty() }
            ?.let { ServletParams(it) }

    private fun JIRInst.servletParam(): ServletParam? {
        val call = callExpr as? JIRInstanceCallExpr ?: return null

        val method = call.declaredMethod.method
        if (!method.isServletRequestMethod()) return null

        val methodName = method.name
        if (methodName in bodyMethods) return ServletParam.Body

        if (methodName in paramMethods) {
            val paramNameArg = call.args.getOrNull(0) ?: return null
            return extractParamName(paramNameArg)?.let { ServletParam.Query(it) }
        }

        return null
    }

    private fun extractParamName(nameArg: JIRValue): String? {
        if (nameArg !is JIRStringConstant) return null
        return nameArg.value
    }

    companion object {
        private val bodyMethods = setOf("getReader", "getInputStream")
        private val paramMethods = setOf("getParameter", "getParameterValues")
    }
}
