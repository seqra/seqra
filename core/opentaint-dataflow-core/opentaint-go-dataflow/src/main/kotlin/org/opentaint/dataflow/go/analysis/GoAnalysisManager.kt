package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallResolver
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSideEffectSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.opentaint.dataflow.go.GoCallExpr
import org.opentaint.dataflow.go.GoLanguageManager
import org.opentaint.dataflow.go.GoMethodCallFactMapper
import org.opentaint.dataflow.go.graph.GoApplicationGraph
import org.opentaint.dataflow.go.rules.GoTaintConfig
import org.opentaint.dataflow.go.rules.GoTaintRulesProvider
import org.opentaint.dataflow.go.trace.GoMethodCallPrecondition
import org.opentaint.dataflow.go.trace.GoMethodSequentPrecondition
import org.opentaint.dataflow.go.trace.GoMethodStartPrecondition
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.value.GoIRValue
import org.opentaint.util.analysis.ApplicationGraph

/**
 * Central factory that wires all Go dataflow analysis components together.
 */
class GoAnalysisManager(cp: GoIRProgram) : GoLanguageManager(cp), TaintAnalysisManager {

    override val factTypeChecker: FactTypeChecker = FactTypeChecker.Dummy

    override fun getMethodAnalysisContext(
        methodEntryPoint: MethodEntryPoint,
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        callResolver: MethodCallResolver,
        taintAnalysisContext: TaintAnalysisContext,
        contextForEmptyMethod: MethodAnalysisContext?,
    ): MethodAnalysisContext {
        val config = taintAnalysisContext.taintConfig as GoTaintConfig
        val rulesProvider = GoTaintRulesProvider(config)
        return GoMethodAnalysisContext(methodEntryPoint, taintAnalysisContext, rulesProvider)
    }

    override fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner,
    ): MethodCallResolver {
        val goGraph = graph as GoApplicationGraph
        return GoMethodCallResolver(goGraph.callResolver, runner)
    }

    override fun getMethodStartFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
    ): MethodStartFlowFunction {
        return GoMethodStartFlowFunction(apManager, analysisContext as GoMethodAnalysisContext)
    }

    override fun getMethodSequentFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst,
        generateTrace: Boolean,
    ): MethodSequentFlowFunction {
        return GoMethodSequentFlowFunction(
            apManager, analysisContext as GoMethodAnalysisContext,
            currentInst as GoIRInst, generateTrace
        )
    }

    override fun getMethodCallFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst,
        generateTrace: Boolean,
    ): MethodCallFlowFunction {
        return GoMethodCallFlowFunction(
            apManager, analysisContext as GoMethodAnalysisContext,
            returnValue as? GoIRValue,
            callExpr as GoCallExpr,
            statement as GoIRInst,
            generateTrace,
        )
    }

    override fun getMethodCallSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst,
    ): MethodCallSummaryHandler {
        return GoMethodCallSummaryHandler(
            apManager, analysisContext as GoMethodAnalysisContext,
            statement as GoIRInst
        )
    }

    override fun getMethodSideEffectSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst,
        runner: AnalysisRunner,
    ): MethodSideEffectSummaryHandler {
        return GoMethodSideEffectHandler()
    }

    override fun getMethodStartPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
    ): MethodStartPrecondition {
        return GoMethodStartPrecondition()
    }

    override fun getMethodSequentPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst,
    ): MethodSequentPrecondition {
        return GoMethodSequentPrecondition()
    }

    override fun getMethodCallPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst,
    ): MethodCallPrecondition {
        return GoMethodCallPrecondition()
    }

    override fun isReachable(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        base: AccessPathBase,
        statement: CommonInst,
    ): Boolean {
        // In SSA form, all defined registers are reachable at their use points
        return true
    }

    override fun isValidMethodExitFact(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        fact: FinalFactAp,
    ): Boolean {
        return GoMethodCallFactMapper.isValidMethodExitFact(fact)
    }

    override fun onInstructionReached(inst: CommonInst) {
        // No-op for MVP
    }
}
