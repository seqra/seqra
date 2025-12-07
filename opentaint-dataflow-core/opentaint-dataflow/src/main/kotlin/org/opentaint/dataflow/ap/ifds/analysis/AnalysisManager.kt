package org.opentaint.dataflow.ap.ifds.analysis

import mu.KLogger
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.util.analysis.ApplicationGraph
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.opentaint.dataflow.ifds.UnitResolver

interface AnalysisManager: LanguageManager {
    fun getMethodAnalysisContext(
        methodEntryPoint: MethodEntryPoint,
        graph: ApplicationGraph<CommonMethod, CommonInst>
    ): org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext

    fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner
    ): MethodCallResolver

    fun getMethodStartFlowFunction(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
    ): MethodStartFlowFunction

    fun getMethodStartPrecondition(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
    ): MethodStartPrecondition

    fun getMethodSequentPrecondition(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        currentInst: CommonInst
    ): MethodSequentPrecondition

    fun getMethodSequentFlowFunction(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        currentInst: CommonInst
    ): MethodSequentFlowFunction

    fun getMethodCallFlowFunction(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst,
    ): MethodCallFlowFunction

    fun getMethodCallPrecondition(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst,
    ): MethodCallPrecondition

    fun getMethodCallSummaryHandler(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        statement: CommonInst,
    ): MethodCallSummaryHandler

    fun isReachable(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        base: AccessPathBase,
        statement: CommonInst
    ): Boolean

    fun isValidMethodExitFact(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        fact: FinalFactAp,
    ): Boolean

    fun onInstructionReached(inst: CommonInst)
    fun reportLanguageSpecificRunnerProgress(logger: KLogger) = Unit
}
