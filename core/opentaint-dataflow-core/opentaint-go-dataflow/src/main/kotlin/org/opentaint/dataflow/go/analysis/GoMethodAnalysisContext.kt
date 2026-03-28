package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.dataflow.go.GoMethodCallFactMapper
import org.opentaint.dataflow.go.rules.GoTaintRulesProvider
import org.opentaint.ir.go.api.GoIRFunction

/**
 * Per-method analysis context for Go. Significantly simpler than JVM's.
 */
class GoMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val taint: TaintAnalysisContext,
    val rulesProvider: GoTaintRulesProvider,
) : MethodAnalysisContext {
    override val methodCallFactMapper: MethodCallFactMapper
        get() = GoMethodCallFactMapper

    val method: GoIRFunction
        get() = methodEntryPoint.method as GoIRFunction
}
