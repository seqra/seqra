package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext

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