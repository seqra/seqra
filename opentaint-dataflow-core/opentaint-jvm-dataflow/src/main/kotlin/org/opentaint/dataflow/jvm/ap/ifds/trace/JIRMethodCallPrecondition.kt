package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPreconditionFact
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.PassRuleConditionFacts
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.PreconditionFactsForInitialFact
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition.PassRuleCondition
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.analysis.forEachPossibleAliasAtStatement
import org.opentaint.dataflow.jvm.ap.ifds.removeTrueLiterals
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintPassActionPreconditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.mkInitialAccessPath
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.dataflow.util.cartesianProductMapTo
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.util.Maybe
import org.opentaint.util.maybeFlatMap

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
            entryFactReader, analysisContext.factTypeChecker, callExpr.method.returnType
        )

        val conditionRewriter = JIRMarkAwareConditionRewriter(
            jIRValueResolver,
            analysisContext.factTypeChecker
        )

        for (rule in taintConfig.sourceRulesForMethod(method, statement)) {
            val assignedMarks = rule.actionsAfter.maybeFlatMap {
                sourcePreconditionEvaluator.evaluate(rule, it)
            }
            if (assignedMarks.isNone) continue

            val sourceActions = assignedMarks.getOrThrow().mapTo(hashSetOf()) { it.second }

            val simplifiedCondition = conditionRewriter.rewrite(rule.condition)

            val simplifiedExpr = when {
                simplifiedCondition.isFalse -> continue
                simplifiedCondition.isTrue -> null
                else -> simplifiedCondition.expr
            }

            // We always treat negated mark condition as satisfied
            val exprWithoutNegations = simplifiedExpr?.removeNegated()
            if (exprWithoutNegations == null) {
                this += TaintRulePrecondition.Source(rule, sourceActions)
                continue
            }

            this += TaintRulePrecondition.Pass(
                rule, sourceActions,
                JIRPassRuleCondition.Expr(exprWithoutNegations)
            )
        }
    }

    private fun MutableList<TaintRulePrecondition>.factPassRulePrecondition(
        fact: InitialFactAp,
        startBase: AccessPathBase,
    ) {
        val passRules = taintConfig.passTroughRulesForMethod(method, statement).toList()
        if (passRules.isEmpty()) return

        val entryFactReader = InitialFactReader(fact.rebase(startBase), apManager)
        val rulePreconditionEvaluator = TaintPassActionPreconditionEvaluator(
            entryFactReader, analysisContext.factTypeChecker, callExpr.method.returnType
        )

        val conditionRewriter = JIRMarkAwareConditionRewriter(
            jIRValueResolver,
            analysisContext.factTypeChecker
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

    private fun JIRMarkAwareConditionExpr.removeNegated() = removeTrueLiterals { it.negated }

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

    private fun ContainsMark.preconditionFact(): InitialFactAp {
        return createPositionWithTaintMark(position.resolveAp(), mark)
    }

    private fun createPositionWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark.name))
        val finalPositionWithMark = PositionAccess.Complex(positionWithMark, FinalAccessor)
        return createPosition(finalPositionWithMark)
    }

    private fun createPosition(position: PositionAccess): InitialFactAp {
        var normalizedPosition = position
        if (position is PositionAccess.Complex && position.accessor is FinalAccessor) {
            // mkInitialAccessPath starts with final ap
            normalizedPosition = position.base
        }
        return apManager.mkInitialAccessPath(normalizedPosition, ExclusionSet.Universe)
    }

    private data class PreconditionCube(val facts: Set<InitialFactAp>)

    private fun JIRMarkAwareConditionExpr.preconditionDnf(): List<PreconditionCube> = when (this) {
        is JIRMarkAwareConditionExpr.Literal -> {
            val preconditionFact = condition.preconditionFact()
            val mappedFacts = methodCallFactMapper.mapMethodExitToReturnFlowFact(statement, preconditionFact)
            mappedFacts.map { PreconditionCube(setOf(it)) }
        }

        is JIRMarkAwareConditionExpr.Or -> args.flatMap { it.preconditionDnf() }
        is JIRMarkAwareConditionExpr.And -> {
            val result = mutableListOf<PreconditionCube>()
            val cubeLists = args.map { it.preconditionDnf() }
            cubeLists.cartesianProductMapTo { cubes ->
                val facts = hashSetOf<InitialFactAp>()
                cubes.flatMapTo(facts) { it.facts }
                result += PreconditionCube(facts)
            }
            result
        }
    }
}
