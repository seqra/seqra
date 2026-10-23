package org.opentaint.dataflow.jvm.ap.ifds.analysis

import mu.KLogger
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager.Phase
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallResolver
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodEdgePostProcessor
import org.opentaint.dataflow.ap.ifds.analysis.MethodEntrypointResolver
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSideEffectSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalVariableReachability
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodContextSerializer
import org.opentaint.dataflow.jvm.ap.ifds.jIRDowncast
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRTaintAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.trace.JIRMethodCallPrecondition
import org.opentaint.dataflow.jvm.ap.ifds.trace.JIRMethodSequentPrecondition
import org.opentaint.dataflow.jvm.ap.ifds.trace.JIRMethodStartPrecondition
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.jvm.graph.JMethodExitExceptionalInst
import org.opentaint.jvm.graph.JMethodExitNormalInst
import org.opentaint.util.analysis.ApplicationGraph
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class JIRAnalysisManager(
    cp: JIRClasspath,
    refManager: RefManager,
    val taintConfig: TaintRulesProvider,
    val externalMethodTracker: ExternalMethodTracker? = null,
    val params: Params = Params(),
) : JIRLanguageManager(cp), TaintAnalysisManager {
    private val refManager = refManager.softRefManager("JIRAnalysisManager")

    override val factTypeChecker = JIRFactTypeChecker(cp)

    data class Params(
        val aliasAnalysisParams: JIRLocalAliasAnalysis.Params = JIRLocalAliasAnalysis.Params(),
        val defaultGetModel: JIRMethodGetDefault? = null,
    )

    private val relevantRuleIds = ConcurrentHashMap.newKeySet<String>()
    private val contexts = ConcurrentLinkedQueue<JIRMethodAnalysisContext>()

    private var currentPhase: Phase = Phase.Prescan
    val phase: Phase get() = currentPhase

    override fun selectPhase(phase: Phase) {
        currentPhase = phase
        contexts.forEach { it.resetAnalysisCache() }
        when (phase) {
            Phase.Prescan -> {}
            Phase.FullScan -> taintConfig.selectRules(relevantRuleIds)
        }
    }

    override fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner
    ): JIRMethodCallResolver {
        jIRDowncast<JApplicationGraph>(graph)
        jIRDowncast<JIRUnitResolver>(unitResolver)

        val jIRCallResolver = JIRCallResolver(cp, unitResolver)
        return JIRMethodCallResolver(jIRCallResolver, runner, externalMethodTracker)
    }

    override fun getMethodAnalysisContext(
        methodEntryPoint: MethodEntryPoint,
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        callResolver: MethodCallResolver,
        taintAnalysisContext: TaintAnalysisContext,
        contextForEmptyMethod: MethodAnalysisContext?
    ): MethodAnalysisContext {
        val entryPointStatement = methodEntryPoint.statement
        jIRDowncast<JIRInst>(entryPointStatement)
        jIRDowncast<JApplicationGraph>(graph)
        callResolver as JIRMethodCallResolver

        val jirContextForEmptyMethod = contextForEmptyMethod as? JIRMethodAnalysisContext

        val method = entryPointStatement.location.method
        val instructions = method.instList.instructions
        check(instructions.isEmpty() || instructions.last() is JMethodExitExceptionalInst) {
            "Method $method is analysed without method boundary instructions"
        }

        val localVariableReachability = jirContextForEmptyMethod?.localVariableReachability
            ?: JIRLocalVariableReachability(method, graph, this)

        val runnerManager = callResolver.runner.manager
        val cancellation = runnerManager.cancellation

        val aliasAnalysisParams = params.aliasAnalysisParams
        val aliasAnalysis = if (aliasAnalysisParams.useAliasAnalysis) {
            jirContextForEmptyMethod?.aliasAnalysis
                ?: JIRLocalAliasAnalysis(
                    entryPointStatement, graph, callResolver.callResolver,
                    taintConfig,
                    localVariableReachability, cancellation, this, aliasAnalysisParams
                )
        } else {
            null
        }

        val taintContext = JIRTaintAnalysisContext(
            taintAnalysisContext.taintSinkTracker, taintConfig, externalMethodTracker, relevantRuleIds
        )

        return JIRMethodAnalysisContext(
            this,
            refManager,
            methodEntryPoint,
            factTypeChecker,
            localVariableReachability,
            aliasAnalysis,
            taintContext,
        ).also {
            contexts.add(it)
        }
    }

    override fun getMethodInstGraph(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        analysisContext: MethodAnalysisContext,
        method: CommonMethod
    ): MethodInstGraph = MethodInstGraph.build(this, graph, method)

    override fun getMethodEntrypointResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
    ): MethodEntrypointResolver {
        jIRDowncast<JApplicationGraph>(graph)
        return JIRMethodEntrypointResolver(graph)
    }

    override fun getMethodStartFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext
    ): MethodStartFlowFunction {
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)
        return JIRMethodStartFlowFunction(apManager, analysisContext)
    }

    override fun getMethodStartPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext
    ): MethodStartPrecondition {
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)
        return JIRMethodStartPrecondition(apManager, analysisContext)
    }

    override fun getMethodSequentPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst
    ): MethodSequentPrecondition {
        jIRDowncast<JIRInst>(currentInst)
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)

        return JIRMethodSequentPrecondition(apManager, currentInst, analysisContext)
    }

    override fun getMethodSequentFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst,
        generateTrace: Boolean
    ): MethodSequentFlowFunction {
        jIRDowncast<JIRInst>(currentInst)
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)

        return analysisContext.cachedSequentFF(currentInst.location.index, generateTrace) {
            JIRMethodSequentFlowFunction(apManager, analysisContext, currentInst, generateTrace)
        }
    }

    override fun getMethodCallFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst,
        generateTrace: Boolean
    ): MethodCallFlowFunction {
        jIRDowncast<JIRImmediate?>(returnValue)
        jIRDowncast<JIRCallExpr>(callExpr)
        jIRDowncast<JIRInst>(statement)
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)

        return analysisContext.cachedCallFF(statement.location.index) {
            JIRMethodCallFlowFunction(
                apManager,
                analysisContext,
                returnValue,
                callExpr,
                statement,
                generateTrace
            )
        }
    }

    override fun getMethodCallSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst
    ): MethodCallSummaryHandler {
        jIRDowncast<JIRInst>(statement)
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)

        return analysisContext.cachedCallSH(statement.location.index) {
            JIRMethodCallSummaryHandler(statement, analysisContext, apManager)
        }
    }

    override fun getMethodSideEffectSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst,
        runner: AnalysisRunner
    ): MethodSideEffectSummaryHandler {
        jIRDowncast<JIRInst>(statement)
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)

        return JIRMethodSideEffectHandler(runner, analysisContext)
    }

    override fun getMethodCallPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst
    ): MethodCallPrecondition {
        jIRDowncast<JIRImmediate?>(returnValue)
        jIRDowncast<JIRCallExpr>(callExpr)
        jIRDowncast<JIRInst>(statement)
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)

        return JIRMethodCallPrecondition(
            apManager,
            analysisContext,
            returnValue,
            callExpr,
            statement
        )
    }

    override fun getEdgePostProcessor(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        graph: MethodInstGraph,
        statement: CommonInst,
    ): MethodEdgePostProcessor {
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)
        jIRDowncast<JIRInst>(statement)

        return JIRMethodSummaryEdgeProcessor(analysisContext, graph, this, statement)
    }

    override fun isReachable(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        base: AccessPathBase,
        statement: CommonInst
    ): Boolean {
        jIRDowncast<JIRMethodAnalysisContext>(analysisContext)
        return analysisContext.localVariableReachability.isReachable(base, statement)
    }

    override fun isValidMethodExitFact(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        fact: FinalFactAp
    ): Boolean {
        return JIRMethodCallFactMapper.isValidMethodExitFact(fact)
    }

    override val methodContextSerializer = JIRMethodContextSerializer(cp)

    override fun isTraceRequiredInstruction(inst: CommonInst): Boolean =
        inst is JIRReturnInst || inst is JMethodExitNormalInst

    override fun onInstructionReached(inst: CommonInst) {

    }

    override fun reportLanguageSpecificRunnerProgress(logger: KLogger) {
        logger.debug {
            val localTotal = factTypeChecker.localFactsTotal.sum()
            val localRejected = factTypeChecker.localFactsRejected.sum()
            val accessTotal = factTypeChecker.accessTotal.sum()
            val accessRejected = factTypeChecker.accessRejected.sum()
            val compatTotal = factTypeChecker.compatibilityTotal.sum()
            val compatRejected = factTypeChecker.compatibilityRejected.sum()
            buildString {
                append("Fact types: ")
                append("local $localRejected/$localTotal (${percentToString(localRejected, localTotal)})")
                append(" | ")
                append("access $accessRejected/$accessTotal (${percentToString(accessRejected, accessTotal)})")
                append(" | ")
                append("compatibility $compatRejected/$compatTotal (${percentToString(compatRejected, compatTotal)})")
            }
        }
    }

    private fun percentToString(current: Long, total: Long): String {
        val percentValue = current.toDouble() / total
        return String.format("%.2f", percentValue * 100) + "%"
    }
}