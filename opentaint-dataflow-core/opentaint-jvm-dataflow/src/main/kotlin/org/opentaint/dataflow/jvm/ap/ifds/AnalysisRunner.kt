package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzer.MethodCallHandler
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzer.MethodCallResolutionFailureHandler
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager

interface AnalysisRunner {
    val graph: JIRApplicationGraph
    val taintConfiguration: TaintRulesProvider
    val factTypeChecker: FactTypeChecker
    val sinkTracker: TaintSinkTracker
    val lambdaTracker: JIRLambdaTracker
    val apManager: ApManager

    fun submitNewUnprocessedEdge(edge: Edge)
    fun addNewSummaryEdges(methodEntryPoint: MethodEntryPoint, edges: List<Edge>)
    fun addNewSinkRequirement(methodEntryPoint: MethodEntryPoint, requirement: Fact.InitialFact)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToZero, methodEntryPoint: MethodEntryPoint)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToFact, methodEntryPoint: MethodEntryPoint, methodFactBase: AccessPathBase)
    fun subscribeOnMethodSummaries(edge: Edge.FactToFact, methodEntryPoint: MethodEntryPoint, methodFactBase: AccessPathBase)

    fun resolveMethodCall(
        callerEntryPoint: MethodEntryPoint,
        callExpr: JIRCallExpr,
        location: JIRInst,
        handler: MethodCallHandler,
        failureHandler: MethodCallResolutionFailureHandler,
    )
}
