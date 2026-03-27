package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer
import org.opentaint.dataflow.ap.ifds.MethodWithContext

interface MethodCallResolver {
    fun resolveMethodCall(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler,
    )

    fun resolvedMethodCalls(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst
    ): List<MethodCallResolutionResult>

    sealed interface MethodCallResolutionResult {
        object ResolutionFailure : MethodCallResolutionResult
        data class ResolvedMethod(val method: MethodWithContext) : MethodCallResolutionResult
    }
}