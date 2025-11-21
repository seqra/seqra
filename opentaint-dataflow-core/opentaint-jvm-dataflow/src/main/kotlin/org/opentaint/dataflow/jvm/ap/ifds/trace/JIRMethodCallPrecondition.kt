package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPreconditionFact
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallPositionToAccessPathResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.CalleePositionToAccessPath
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRBasicConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintPassActionPreconditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.util.maybeFlatMap
import org.opentaint.util.onSome

class JIRMethodCallPrecondition(
    private val apManager: ApManager,
    private val analysisContext: JIRMethodAnalysisContext,
    private val returnValue: JIRImmediate?,
    private val callExpr: JIRCallExpr,
    private val statement: JIRInst
) : MethodCallPrecondition {
    private val apResolver = JIRCallPositionToAccessPathResolver(callExpr, returnValue)
    private val jirValueResolver = CallPositionToJIRValueResolver(callExpr, returnValue)
    private val method = callExpr.callee

    private val taintConfig get() = analysisContext.taint.taintConfig as TaintRulesProvider

    override fun factPrecondition(fact: InitialFactAp): CallPrecondition {
        if (!JIRMethodCallFactMapper.factIsRelevantToMethodCall(returnValue, callExpr, fact)) {
            return CallPrecondition.Unchanged
        }

        val rulePreconditions = mutableListOf<TaintRulePrecondition>()
        rulePreconditions.factSourceRulePrecondition(fact)
        rulePreconditions.factPassRulePrecondition(fact)

        val ruleFacts = rulePreconditions.map { CallPreconditionFact.CallToReturnTaintRule(it) }

        val callToStart = mutableListOf<CallPreconditionFact.CallToStart>()

        if (returnValue != null) {
            val returnValueBase = MethodFlowFunctionUtils.accessPathBase(returnValue)
            if (returnValueBase == fact.base) {
                callToStart += CallPreconditionFact.CallToStart(fact, AccessPathBase.Return)
            }
        }

        val method = callExpr.callee
        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(method, callExpr, fact) { callerFact, startFactBase ->
            callToStart += CallPreconditionFact.CallToStart(callerFact, startFactBase)
        }

        return CallPrecondition.Facts(ruleFacts + callToStart)
    }

    private fun MutableList<TaintRulePrecondition>.factSourceRulePrecondition(fact: InitialFactAp) {
        val entryFactReader = InitialFactReader(fact, apManager)
        val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(apResolver, entryFactReader)

        for (rule in taintConfig.sourceRulesForMethod(method, statement)) {
            val rulePrecondition = JIRPreconditionFactReader(apManager)
            val ruleConditionEvaluator = JIRFactAwareConditionEvaluator(
                listOf(rulePrecondition),
                apResolver,
                jirValueResolver
            )

            if (!rule.condition.accept(ruleConditionEvaluator)) continue

            val assignedMarks = rule.actionsAfter
                .filterIsInstance<AssignMark>()
                .maybeFlatMap { sourcePreconditionEvaluator.evaluate(rule, it) }

            assignedMarks.onSome { sourceActions ->
                val preconditions = rulePrecondition.preconditions
                if (preconditions.isEmpty()) {
                    sourceActions.mapTo(this) {
                        TaintRulePrecondition.Source(it.first as CommonTaintConfigurationSource, it.second)
                    }
                } else {
                    preconditions.forEach { preconditionFact ->
                        sourceActions.mapTo(this) {
                            TaintRulePrecondition.Pass(it.first, it.second, preconditionFact)
                        }
                    }
                }
            }
        }
    }

    private fun MutableList<TaintRulePrecondition>.factPassRulePrecondition(fact: InitialFactAp) {
        val entryFactReader = InitialFactReader(fact, apManager)
        val rulePreconditionEvaluator = TaintPassActionPreconditionEvaluator(apResolver, entryFactReader)

        val ruleConditionEvaluator = JIRFactAwareConditionEvaluator(
            listOf(JIRPreconditionFactReader(apManager)),
            apResolver,
            jirValueResolver
        )

        val result = TaintConfigUtils.applyPassThrough(
            taintConfig,
            method,
            statement,
            ruleConditionEvaluator,
            rulePreconditionEvaluator
        )

        result.onSome { this += it }
    }

    companion object {
        fun getEntryPointPrecondition(
            apManager: ApManager,
            config: TaintRulesProvider,
            method: JIRMethod,
            initialFact: InitialFactAp,
        ): List<TaintRulePrecondition.Source> {
            val entryFactReader = InitialFactReader(initialFact, apManager)
            val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(
                CalleePositionToAccessPath(resultAp = null),
                entryFactReader
            )

            val conditionEvaluator = JIRBasicConditionEvaluator(
                CalleePositionToJIRValueResolver(method)
            )

            val result = TaintConfigUtils.applyEntryPointConfig(
                config, method, conditionEvaluator, sourcePreconditionEvaluator
            )

            result.onSome { sourceActions ->
                return sourceActions.map { TaintRulePrecondition.Source(it.first as CommonTaintConfigurationSource, it.second) }
            }

            return emptyList()
        }
    }
}