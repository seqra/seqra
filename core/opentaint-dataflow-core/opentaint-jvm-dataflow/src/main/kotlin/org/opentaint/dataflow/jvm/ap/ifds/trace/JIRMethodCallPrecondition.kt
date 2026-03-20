package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPreconditionFact
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPreconditionFact.CallFailurePreconditionFact
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.PassRuleConditionFacts
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.PreconditionFactsForInitialFact
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition.PassRuleCondition
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.analysis.forEachPossibleAliasAtStatement
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintPassActionPreconditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.util.Maybe
import org.opentaint.util.maybeFlatMap
import kotlin.collections.plusAssign

class JIRMethodCallPrecondition(
    private val apManager: ApManager,
    private val analysisContext: JIRMethodAnalysisContext,
    private val returnValue: JIRImmediate?,
    private val callExpr: JIRCallExpr,
    private val statement: JIRInst,
) : MethodCallPrecondition {
    private val methodCallFactMapper: MethodCallFactMapper get() = analysisContext.methodCallFactMapper

    private val jIRValueResolver = CallPositionToJIRValueResolver(callExpr, returnValue)
    private val method = callExpr.callee

    private val taintConfig get() = analysisContext.taint.taintConfig as TaintRulesProvider

    override fun factPrecondition(fact: InitialFactAp): List<CallPrecondition> {
        val results = mutableListOf<CallPrecondition>()

        results += preconditionForFact(fact)?.let { PreconditionFactsForInitialFact(fact, it) }
            ?: CallPrecondition.Unchanged

        analysisContext.aliasAnalysis?.forEachPossibleAliasAtStatement(statement, fact) { aliasedFact ->
            preconditionForFact(aliasedFact)?.let { results += PreconditionFactsForInitialFact(aliasedFact, it) }
        }

        return results
    }

    override fun factPreconditionResolutionFailure(
        fact: InitialFactAp,
        startFactBase: AccessPathBase
    ): List<CallFailurePreconditionFact> {
        val preconditions = mutableListOf<CallFailurePreconditionFact>()

        if (startFactBase != AccessPathBase.Return) {
            preconditions += CallPreconditionFact.UnresolvedCallSkip
        }

        preconditions += rulePreconditionForFactResolutionFailure(fact, startFactBase)

        return preconditions
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
        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(method, callExpr, returnValue = null, fact = fact) { callerFact, startFactBase ->
            preconditions.preconditionForFact(callerFact, startFactBase)
        }

        return preconditions
    }

    private fun MutableList<CallPreconditionFact>.preconditionForFact(fact: InitialFactAp, startBase: AccessPathBase) {
        val rulePreconditions = mutableListOf<TaintRulePrecondition>()
        rulePreconditions.factSourceRulePrecondition(fact, startBase)

        rulePreconditions.mapTo(this) { CallPreconditionFact.CallToReturnTaintRule(it) }

        this += CallPreconditionFact.CallToStart(fact, startBase)
    }

    private fun rulePreconditionForFactResolutionFailure(fact: InitialFactAp, startBase: AccessPathBase): List<CallFailurePreconditionFact> {
        val rulePreconditions = mutableListOf<TaintRulePrecondition>()
        rulePreconditions.factPassRulePrecondition(fact, startBase)

        return rulePreconditions.map { CallPreconditionFact.CallToReturnTaintRule(it) }
    }

    sealed interface JIRPassRuleCondition : PassRuleCondition {
        data class Expr(val expr: JIRMarkAwareConditionExpr) : JIRPassRuleCondition
        data class Fact(val fact: InitialFactAp) : JIRPassRuleCondition
        data class FactWithExpr(val fact: InitialFactAp, val expr: JIRMarkAwareConditionExpr) : JIRPassRuleCondition
    }

    private fun MutableList<TaintRulePrecondition>.factSourceRulePrecondition(
        fact: InitialFactAp,
        startBase: AccessPathBase,
    ) {
        val entryFactReader = InitialFactReader(fact.rebase(startBase), apManager)
        val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(
            entryFactReader
        )

        val conditionRewriter = JIRMarkAwareConditionRewriter(
            jIRValueResolver,
            analysisContext, statement
        )

        for (rule in taintConfig.sourceRulesForMethod(method, statement, fact = null)) {
            evaluateSourceRulePrecondition(rule, sourcePreconditionEvaluator, conditionRewriter)
        }
    }

    private fun MutableList<TaintRulePrecondition>.evaluateSourceRulePrecondition(
        rule: TaintMethodSource,
        sourcePreconditionEvaluator: TaintSourceActionPreconditionEvaluator,
        conditionRewriter: JIRMarkAwareConditionRewriter,
    ) {
        return evaluateSourceRulePrecondition(
            rule,
            sourcePreconditionEvaluator,
            conditionRewriter,
            { r, a -> this += TaintRulePrecondition.Source(r, a) },
            { r, a, e -> this += TaintRulePrecondition.Pass(r, a, JIRPassRuleCondition.Expr(e)) }
        )
    }

    private fun MutableList<TaintRulePrecondition>.factPassRulePrecondition(
        fact: InitialFactAp,
        startBase: AccessPathBase,
    ) {
        val passRules = taintConfig.passTroughRulesForMethod(method, statement, fact = null).toList()
        if (passRules.isEmpty()) return

        val entryFactReader = InitialFactReader(fact.rebase(startBase), apManager)
        val rulePreconditionEvaluator = TaintPassActionPreconditionEvaluator(entryFactReader)

        val conditionRewriter = JIRMarkAwareConditionRewriter(
            jIRValueResolver,
            analysisContext, statement
        )

        for (rule in passRules) {
            val actions = rule.actionsAfter.maybeFlatMap {
                when (it) {
                    is CopyMark -> rulePreconditionEvaluator.evaluate(rule, it)
                    is CopyAllMarks -> rulePreconditionEvaluator.evaluate(rule, it)
                    else -> Maybe.none()
                }
            }
            if (actions.isNone) continue

            val passActions = actions.getOrThrow()

            val simplifiedCondition = conditionRewriter.rewrite(rule.condition)

            val simplifiedExpr = when {
                simplifiedCondition.isFalse -> continue
                simplifiedCondition.isTrue -> null
                else -> simplifiedCondition.expr
            }

            // We always treat negated mark condition as satisfied
            val exprWithoutNegations = simplifiedExpr?.removeNegated()

            val mappedAction = passActions.flatMap { (action, fact) ->
                methodCallFactMapper.mapMethodExitToReturnFlowFact(statement, fact).map { action to it }
            }

            mappedAction.mapTo(this) { (action, fact) ->
                val cond = if (exprWithoutNegations == null) {
                    JIRPassRuleCondition.Fact(fact)
                } else {
                    JIRPassRuleCondition.FactWithExpr(fact, exprWithoutNegations)
                }
                TaintRulePrecondition.Pass(rule, setOf(action), cond)
            }
        }
    }

    override fun resolvePassRuleCondition(precondition: PassRuleCondition): List<PassRuleConditionFacts> {
        precondition as JIRPassRuleCondition

        return when (precondition) {
            is JIRPassRuleCondition.Fact -> {
                listOf(PassRuleConditionFacts(listOf(precondition.fact)))
            }

            is JIRPassRuleCondition.Expr -> {
                precondition.expr.preconditionDnf().map { PassRuleConditionFacts(it.facts.toList()) }
            }

            is JIRPassRuleCondition.FactWithExpr -> {
                precondition.expr.preconditionDnf().map {
                    val allFacts = it.facts + precondition.fact
                    PassRuleConditionFacts(allFacts.toList())
                }
            }
        }
    }

    private fun JIRMarkAwareConditionExpr.preconditionDnf(): List<PreconditionCube> =
        preconditionDnf(apManager) { preconditionFact ->
            methodCallFactMapper.mapMethodExitToReturnFlowFact(statement, preconditionFact)
        }
}
