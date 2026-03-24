package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallResolver
import org.opentaint.dataflow.python.adapter.PIRCallExprAdapter
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.PIRCall

class PIRMethodCallResolver(
    private val callResolver: PIRCallResolver,
    private val runner: TaintAnalysisUnitRunner,
) : MethodCallResolver {

    override fun resolveMethodCall(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler,
    ) {
        val pirCall = location as PIRCall
        val callee = callResolver.resolve(pirCall)
        if (callee == null) {
            val analyzer = runner.getMethodAnalyzer(callerContext.methodEntryPoint)
            analyzer.handleMethodCallResolutionFailure(callExpr, failureHandler)
            return
        }
        val methodWithContext = MethodWithContext(callee, EmptyMethodContext)
        val analyzer = runner.getMethodAnalyzer(callerContext.methodEntryPoint)
        analyzer.handleResolvedMethodCall(methodWithContext, handler)
    }

    override fun resolvedMethodCalls(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst,
    ): List<MethodWithContext> {
        val pirCall = location as PIRCall
        val callee = callResolver.resolve(pirCall) ?: return emptyList()
        return listOf(MethodWithContext(callee, EmptyMethodContext))
    }
}
