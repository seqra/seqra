package org.opentaint.dataflow.ap.ifds

import mu.KLogger
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.UnitResolver

interface LanguageManager {
    fun getInstIndex(inst: CommonInst): Int
    fun getMaxInstIndex(method: CommonMethod): Int
    fun isEmpty(method: CommonMethod): Boolean
    fun getCallExpr(inst: CommonInst): CommonCallExpr?
    fun producesExceptionalControlFlow(inst: CommonInst): Boolean
    fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod
    fun accessPathBase(value: CommonValue): AccessPathBase?

    fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner
    ): MethodCallResolver

    fun getLocalVariableReachability(
        method: CommonMethod,
        graph: ApplicationGraph<CommonMethod, CommonInst>
    ): LocalVariableReachability

    fun checkInitialFactTypes(methodEntryPoint: MethodEntryPoint, factAp: FinalFactAp): FinalFactAp?

    fun getMethodSequentPrecondition(currentInst: CommonInst): MethodSequentPrecondition
    fun getMethodSequentFlowFunction(apManager: ApManager, currentInst: CommonInst): MethodSequentFlowFunction

    fun getMethodCallFlowFunction(
        apManager: ApManager,
        config: TaintRulesProvider,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factTypeChecker: FactTypeChecker,
        statement: CommonInst,
        sinkTracker: TaintSinkTracker,
        methodEntryPoint: MethodEntryPoint,
    ): MethodCallFlowFunction
    fun getMethodCallPrecondition(
        apManager: ApManager,
        config: TaintRulesProvider,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factsAtStatement: List<FinalFactAp>,
    ): MethodCallPrecondition

    fun applyEntryPointConfigDefault(
        apManager: ApManager,
        config: TaintRulesProvider,
        method: CommonMethod,
    ): Maybe<List<FinalFactAp>>

    val factTypeChecker: FactTypeChecker
    val methodCallFactMapper: MethodCallFactMapper

    fun onInstructionReached(inst: CommonInst)
    fun reportLanguageSpecificRunnerProgress(logger: KLogger) = Unit
}