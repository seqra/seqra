package org.opentaint.dataflow.jvm.ap.ifds

import mu.KLogger
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider
import org.opentaint.dataflow.ap.ifds.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.jvm.ap.ifds.trace.JIRMethodCallPrecondition
import org.opentaint.dataflow.jvm.ap.ifds.trace.JIRMethodSequentPrecondition
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.util.JIRTraits
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class JIRLanguageManager(private val cp: JIRClasspath) : LanguageManager {
    private val lambdaTracker = JIRLambdaTracker()
    private val traits = JIRTraits(cp)

    override fun getInstIndex(inst: CommonInst): Int {
        jirDowncast<JIRInst>(inst)
        return inst.location.index
    }

    override fun getMaxInstIndex(method: CommonMethod): Int {
        jirDowncast<JIRMethod>(method)
        return method.instList.maxOf { it.location.index }
    }

    override fun isEmpty(method: CommonMethod): Boolean {
        jirDowncast<JIRMethod>(method)
        return method.instList.size == 0
    }

    override fun getCallExpr(inst: CommonInst): JIRCallExpr? {
        jirDowncast<JIRInst>(inst)
        return inst.callExpr
    }

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean {
        return inst is JIRThrowInst
    }

    override fun getCalleeMethod(callExpr: CommonCallExpr): JIRMethod {
        jirDowncast<JIRCallExpr>(callExpr)
        return callExpr.method.method
    }

    override fun accessPathBase(value: CommonValue): AccessPathBase? {
        jirDowncast<JIRValue>(value)
        return MethodFlowFunctionUtils.accessPathBase(value)
    }

    override fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner
    ): JIRMethodCallResolver {
        jirDowncast<JIRApplicationGraph>(graph)
        jirDowncast<JIRUnitResolver>(unitResolver)

        val jirCallResolver = JIRCallResolver(cp, graph, unitResolver)
        return JIRMethodCallResolver(lambdaTracker, jirCallResolver, runner)
    }

    override fun getLocalVariableReachability(
        method: CommonMethod,
        graph: ApplicationGraph<CommonMethod, CommonInst>
    ): JIRLocalVariableReachability {
        jirDowncast<JIRMethod>(method)
        jirDowncast<JIRApplicationGraph>(graph)
        return JIRLocalVariableReachability(method, graph, languageManager = this)
    }

    override fun checkInitialFactTypes(methodEntryPoint: MethodEntryPoint, factAp: FinalFactAp): FinalFactAp? {
        if (factAp.base !is AccessPathBase.This) return factAp

        val thisClass = when (val context = methodEntryPoint.context) {
            EmptyMethodContext -> {
                val method = methodEntryPoint.method
                jirDowncast<JIRMethod>(method)
                method.enclosingClass
            }
            is JIRInstanceTypeMethodContext -> context.type
            else -> error("Unexpected value for context: $context")
        }

        val thisType = thisClass.toType()
        return factTypeChecker.filterFactByLocalType(thisType, factAp)
    }

    override fun getMethodSequentPrecondition(currentInst: CommonInst): MethodSequentPrecondition {
        jirDowncast<JIRInst>(currentInst)
        return JIRMethodSequentPrecondition(currentInst)
    }

    override fun getMethodSequentFlowFunction(
        apManager: ApManager,
        currentInst: CommonInst
    ): MethodSequentFlowFunction {
        jirDowncast<JIRInst>(currentInst)
        return JIRMethodSequentFlowFunction(apManager, currentInst, factTypeChecker)
    }

    override fun getMethodCallFlowFunction(
        apManager: ApManager,
        config: TaintRulesProvider,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factTypeChecker: FactTypeChecker,
        statement: CommonInst,
        sinkTracker: TaintSinkTracker,
        methodEntryPoint: MethodEntryPoint
    ): JIRMethodCallFlowFunction {
        jirDowncast<JIRImmediate?>(returnValue)
        jirDowncast<JIRCallExpr>(callExpr)
        jirDowncast<JIRInst>(statement)

        return JIRMethodCallFlowFunction(
            apManager,
            config,
            returnValue,
            callExpr,
            factTypeChecker,
            statement,
            sinkTracker,
            methodEntryPoint,
            traits
        )
    }

    override fun getMethodCallPrecondition(
        apManager: ApManager,
        config: TaintRulesProvider,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factsAtStatement: List<FinalFactAp>,
    ): MethodCallPrecondition {
        jirDowncast<JIRImmediate?>(returnValue)
        jirDowncast<JIRCallExpr>(callExpr)

        return JIRMethodCallPrecondition(
            apManager,
            config,
            returnValue,
            callExpr,
            factsAtStatement,
            traits
        )
    }

    override fun applyEntryPointConfigDefault(
        apManager: ApManager,
        config: TaintRulesProvider,
        method: CommonMethod
    ): Maybe<List<FinalFactAp>> {
        jirDowncast<JIRMethod>(method)
        return JIRMethodCallFlowFunction.applyEntryPointConfigDefault(apManager, config, method, traits)
    }

    override val factTypeChecker = JIRFactTypeChecker(cp)
    override val methodCallFactMapper = JIRMethodCallFactMapper

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

@OptIn(ExperimentalContracts::class)
internal inline fun <reified T> jirDowncast(value: Any?) {
    contract {
        returns() implies(value is T)
    }
    check(value is T) { "Downcast error: expected ${T::class}, got $value" }
}