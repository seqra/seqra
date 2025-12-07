package org.opentaint.dataflow.jvm.ap.ifds.analysis

import mu.KLogger
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.JIRLambdaTracker
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalVariableReachability
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodContextSerializer
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.ap.ifds.jirDowncast
import org.opentaint.dataflow.jvm.ap.ifds.trace.JIRMethodCallPrecondition
import org.opentaint.dataflow.jvm.ap.ifds.trace.JIRMethodSequentPrecondition
import org.opentaint.dataflow.jvm.ap.ifds.trace.JIRMethodStartPrecondition
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.util.analysis.ApplicationGraph

class JIRAnalysisManager(
    cp: JIRClasspath,
    private val applyAliasInfo: Boolean = false,
) : JIRLanguageManager(cp), TaintAnalysisManager {
    private val lambdaTracker = JIRLambdaTracker()
    private val factTypeChecker = JIRFactTypeChecker(cp)

    override fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner
    ): JIRMethodCallResolver {
        jirDowncast<JApplicationGraph>(graph)
        jirDowncast<JIRUnitResolver>(unitResolver)

        val jirCallResolver = JIRCallResolver(cp, graph, unitResolver)
        return JIRMethodCallResolver(lambdaTracker, jirCallResolver, runner)
    }

    override fun getMethodAnalysisContext(
        methodEntryPoint: MethodEntryPoint,
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        taintAnalysisContext: TaintAnalysisContext
    ): org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext {
        val entryPointStatement = methodEntryPoint.statement
        jirDowncast<JIRInst>(entryPointStatement)
        jirDowncast<JApplicationGraph>(graph)

        val aliasAnalysis = if (applyAliasInfo) {
            JIRLocalAliasAnalysis(entryPointStatement, graph, this)
        } else {
            null
        }

        val localVariableReachability = JIRLocalVariableReachability(entryPointStatement.method, graph, this)
        return JIRMethodAnalysisContext(
            methodEntryPoint,
            factTypeChecker,
            localVariableReachability,
            aliasAnalysis,
            taintAnalysisContext
        )
    }

    override fun getMethodStartFlowFunction(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
    ): MethodStartFlowFunction {
        jirDowncast<JIRMethodAnalysisContext>(analysisContext)
        return JIRMethodStartFlowFunction(apManager, analysisContext)
    }

    override fun getMethodStartPrecondition(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
    ): MethodStartPrecondition {
        jirDowncast<JIRMethodAnalysisContext>(analysisContext)
        return JIRMethodStartPrecondition(apManager, analysisContext)
    }

    override fun getMethodSequentPrecondition(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        currentInst: CommonInst
    ): MethodSequentPrecondition {
        jirDowncast<JIRInst>(currentInst)
        jirDowncast<JIRMethodAnalysisContext>(analysisContext)

        return JIRMethodSequentPrecondition(currentInst, analysisContext)
    }

    override fun getMethodSequentFlowFunction(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        currentInst: CommonInst
    ): MethodSequentFlowFunction {
        jirDowncast<JIRInst>(currentInst)
        jirDowncast<JIRMethodAnalysisContext>(analysisContext)

        return JIRMethodSequentFlowFunction(apManager, analysisContext, currentInst)
    }

    override fun getMethodCallFlowFunction(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst
    ): MethodCallFlowFunction {
        jirDowncast<JIRImmediate?>(returnValue)
        jirDowncast<JIRCallExpr>(callExpr)
        jirDowncast<JIRInst>(statement)
        jirDowncast<JIRMethodAnalysisContext>(analysisContext)

        return JIRMethodCallFlowFunction(
            apManager,
            analysisContext,
            returnValue,
            callExpr,
            statement,
        )
    }

    override fun getMethodCallSummaryHandler(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        statement: CommonInst
    ): MethodCallSummaryHandler {
        jirDowncast<JIRInst>(statement)
        jirDowncast<JIRMethodAnalysisContext>(analysisContext)

        return JIRMethodCallSummaryHandler(statement, analysisContext)
    }

    override fun getMethodCallPrecondition(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst
    ): MethodCallPrecondition {
        jirDowncast<JIRImmediate?>(returnValue)
        jirDowncast<JIRCallExpr>(callExpr)
        jirDowncast<JIRInst>(statement)
        jirDowncast<JIRMethodAnalysisContext>(analysisContext)

        return JIRMethodCallPrecondition(
            apManager,
            analysisContext,
            returnValue,
            callExpr,
            statement
        )
    }

    override fun isReachable(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        base: AccessPathBase,
        statement: CommonInst
    ): Boolean {
        jirDowncast<JIRMethodAnalysisContext>(analysisContext)
        return analysisContext.localVariableReachability.isReachable(base, statement)
    }

    override fun isValidMethodExitFact(
        apManager: ApManager,
        analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
        fact: FinalFactAp
    ): Boolean {
        return JIRMethodCallFactMapper.isValidMethodExitFact(fact)
    }

    override val methodContextSerializer = JIRMethodContextSerializer(cp)

    override fun onInstructionReached(inst: CommonInst) {
        jirDowncast<JIRInst>(inst)
        val allocatedLambda = LambdaExpressionToAnonymousClassTransformerFeature.findLambdaAllocation(inst)
        if (allocatedLambda != null) {
            lambdaTracker.registerLambda(allocatedLambda)
        }
    }

    override fun reportLanguageSpecificRunnerProgress(logger: KLogger) {
        logger.debug {
            val localTotal = factTypeChecker.localFactsTotal.sum()
            val localRejected = factTypeChecker.localFactsRejected.sum()
            val accessTotal = factTypeChecker.accessTotal.sum()
            val accessRejected = factTypeChecker.accessRejected.sum()
            buildString {
                append("Fact types: ")
                append("local $localRejected/$localTotal (${percentToString(localRejected, localTotal)})")
                append(" | ")
                append("access $accessRejected/$accessTotal (${percentToString(accessRejected, accessTotal)})")
            }
        }
    }

    private fun percentToString(current: Long, total: Long): String {
        val percentValue = current.toDouble() / total
        return String.format("%.2f", percentValue * 100) + "%"
    }
}