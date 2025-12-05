package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPreconditionFact
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.PreconditionFactsForInitialFact
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.analysis.forEachPossibleAliasAtStatement
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
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
    private val methodCallFactMapper: MethodCallFactMapper get() = analysisContext.methodCallFactMapper

    private val jirValueResolver = CallPositionToJIRValueResolver(callExpr, returnValue)
    private val method = callExpr.callee

    private val taintConfig get() = analysisContext.taint.taintConfig as TaintRulesProvider

    override fun factPrecondition(fact: InitialFactAp): CallPrecondition {
        val results = mutableListOf<PreconditionFactsForInitialFact>()

        preconditionForFact(fact)?.let {
            results.add(PreconditionFactsForInitialFact(fact, it))
        }

        analysisContext.aliasAnalysis?.forEachPossibleAliasAtStatement(statement, fact) { aliasedFact ->
            preconditionForFact(aliasedFact)?.let {
                results.add(PreconditionFactsForInitialFact(aliasedFact, it))
            }
        }

        return if (results.isEmpty()) {
            CallPrecondition.Unchanged
        } else {
            CallPrecondition.Facts(results)
        }
    }

    private fun preconditionForFact(fact: InitialFactAp): List<CallPreconditionFact>? {
        if (!JIRMethodCallFactMapper.factIsRelevantToMethodCall(returnValue, callExpr, fact)) {
            return null
        }

        val preconditions = mutableListOf<CallPreconditionFact>()

        if (returnValue != null) {
            val returnValueBase = MethodFlowFunctionUtils.accessPathBase(returnValue)
            if (returnValueBase == fact.base) {
                preconditions.preconditionForFact(fact, AccessPathBase.Return)
            }
        }

        val method = callExpr.callee
        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(method, callExpr, fact) { callerFact, startFactBase ->
            preconditions.preconditionForFact(callerFact, startFactBase)
        }

        return preconditions
    }

    private fun MutableList<CallPreconditionFact>.preconditionForFact(fact: InitialFactAp, startBase: AccessPathBase) {
        val rulePreconditions = mutableListOf<TaintRulePrecondition>()
        rulePreconditions.factSourceRulePrecondition(fact, startBase)
        rulePreconditions.factPassRulePrecondition(fact, startBase)

        rulePreconditions.mapTo(this) { CallPreconditionFact.CallToReturnTaintRule(it) }

        this += CallPreconditionFact.CallToStart(fact, startBase)
    }

    private fun MutableList<TaintRulePrecondition>.factSourceRulePrecondition(
        fact: InitialFactAp,
        startBase: AccessPathBase
    ) {
        val entryFactReader = InitialFactReader(fact.rebase(startBase), apManager)
        val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(
            entryFactReader, analysisContext.factTypeChecker, callExpr.method.returnType
        )

        for (rule in taintConfig.sourceRulesForMethod(method, statement)) {
            val rulePrecondition = JIRPreconditionFactReader(apManager)
            val ruleConditionEvaluator = JIRFactAwareConditionEvaluator(
                listOf(rulePrecondition),
                jirValueResolver,
                analysisContext.factTypeChecker,
            )

            if (!ruleConditionEvaluator.evalWithAssumptionsCheck(rule.condition)) continue

            val assignedMarks = rule.actionsAfter
                .maybeFlatMap { sourcePreconditionEvaluator.evaluate(rule, it) }

            assignedMarks.onSome { sourceActions ->
                val preconditions = rulePrecondition.preconditions
                if (preconditions.isEmpty()) {
                    sourceActions.mapTo(this) {
                        TaintRulePrecondition.Source(it.first as CommonTaintConfigurationSource, it.second)
                    }
                } else {
                    val exitToReturnPreconditions = mutableListOf<TaintRulePrecondition.Pass>()
                    preconditions.forEach { preconditionFact ->
                        sourceActions.mapTo(exitToReturnPreconditions) {
                            TaintRulePrecondition.Pass(it.first, it.second, preconditionFact)
                        }
                    }

                    this += exitToReturnPreconditions.mapExitToReturn()
                }
            }
        }
    }

    private fun MutableList<TaintRulePrecondition>.factPassRulePrecondition(
        fact: InitialFactAp,
        startBase: AccessPathBase
    ) {
        val entryFactReader = InitialFactReader(fact.rebase(startBase), apManager)
        val rulePreconditionEvaluator = TaintPassActionPreconditionEvaluator(
            entryFactReader, analysisContext.factTypeChecker, callExpr.method.returnType
        )

        val ruleConditionEvaluator = JIRFactAwareConditionEvaluator(
            listOf(JIRPreconditionFactReader(apManager)),
            jirValueResolver,
            analysisContext.factTypeChecker
        )

        val result = TaintConfigUtils.applyPassThrough(
            taintConfig,
            method,
            statement,
            ruleConditionEvaluator,
            rulePreconditionEvaluator
        )

        result.onSome { this += it.mapExitToReturn() }
    }

    private fun List<TaintRulePrecondition.Pass>.mapExitToReturn(): List<TaintRulePrecondition.Pass> =
        flatMap { rulePre ->
            val mappedFacts = methodCallFactMapper.mapMethodExitToReturnFlowFact(statement, rulePre.fact)
            mappedFacts.map { rulePre.copy(fact = it) }
        }
}