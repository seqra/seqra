package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst

interface MethodCallResolver {
    fun resolveMethodCall(
        callerEntryPoint: MethodEntryPoint,
        callExpr: CommonCallExpr,
        location: CommonInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler,
    )

    fun resolvedMethodCalls(
        callerEntryPoint: MethodEntryPoint,
        callExpr: CommonCallExpr,
        location: CommonInst
    ): List<MethodWithContext>
}