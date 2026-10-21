package org.opentaint.dataflow.python.trace

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPreconditionFact
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.PreconditionFactsForInitialFact
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.python.PIRCallAnyArgumentResolver
import org.opentaint.dataflow.python.PIRCallAtomEvaluator
import org.opentaint.dataflow.python.PIRConditionRewriter
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.resolveAp
import org.opentaint.dataflow.python.rulesWithConditions
import org.opentaint.dataflow.python.adapter.callExpr
import org.opentaint.dataflow.python.alias.forEachAliasBeforeStatement
import org.opentaint.dataflow.python.alias.forEachPossibleAliasBeforeStatement
import org.opentaint.dataflow.python.analysis.PIRMethodAnalysisContext
import org.opentaint.dataflow.python.graph.PIRUnknownFunction
import org.opentaint.dataflow.configuration.python.TaintPassAction
import org.opentaint.dataflow.configuration.python.TaintPassThrough
import org.opentaint.dataflow.taint.InitialFactReader
import org.opentaint.dataflow.taint.TaintPassActionPreconditionEvaluator
import org.opentaint.dataflow.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.dataflow.taint.evaluatePassRulePrecondition
import org.opentaint.dataflow.taint.evaluateSourceRulePrecondition
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.util.Maybe

class PIRMethodCallPrecondition(
    override val apManager: ApManager,
    private val statement: PIRCall,
    private val analysisContext: PIRMethodAnalysisContext,
) : MethodCallPrecondition.Default {
    private val callExpr = statement.callExpr ?: error("Unexpected null call expr")
    private val rulesProvider get() = analysisContext.taint.taintConfig
    private val methodCallFactMapper get() = analysisContext.methodCallFactMapper
    private val returnValue get() = statement.target

    override fun mapExit2Return(fact: InitialFactAp): List<InitialFactAp> =
        methodCallFactMapper.mapMethodExitToReturnFlowFact(statement, fact)

    override fun factPrecondition(fact: InitialFactAp): List<CallPrecondition> = buildList {
        this += preconditionForFact(fact)?.let { PreconditionFactsForInitialFact(fact, it) }
            ?: CallPrecondition.Unchanged

        analysisContext.aliasAnalysis?.forEachPossibleAliasBeforeStatement(statement, fact) { aliasedFact ->
            preconditionForFact(aliasedFact)?.let {
                this += PreconditionFactsForInitialFact(aliasedFact, it)
            }
        }

        analysisContext.aliasAnalysis?.forEachAliasBeforeStatement(statement, fact) { aliasedFact ->
            preconditionForFact(aliasedFact)?.let {
                this += PreconditionFactsForInitialFact(aliasedFact, it)
            }
        }
    }

    override fun factPreconditionResolutionFailure(
        fact: InitialFactAp,
        startFactBase: AccessPathBase,
    ): List<MethodCallPrecondition.CallFailurePreconditionFact> = buildList {
        if (startFactBase != AccessPathBase.Return) {
            this += MethodCallPrecondition.UnresolvedCallSkip
        }
    }

    override fun factPreconditionResolutionSuccess(
        fact: InitialFactAp,
        startFactBase: AccessPathBase,
        method: MethodWithContext
    ): List<MethodCallPrecondition.CallSuccessPreconditionFact> = buildList {
        val callee = method.method as PIRFunction

        factSourceRulePrecondition(callee, fact, startFactBase).forEach {
            this += MethodCallPrecondition.CallToReturnTaintRule(it)
        }

        if (callee is PIRUnknownFunction) {
            unknownCallPrecondition(callee, fact, startFactBase)
        } else {
            methodCallFactMapper.toCalleeFrame(statement, callee, startFactBase)?.let {
                this += MethodCallPrecondition.CallToStartResolved(fact, it, method)
            }
        }
    }

    private fun MutableList<MethodCallPrecondition.CallSuccessPreconditionFact>.unknownCallPrecondition(
        callee: PIRUnknownFunction,
        fact: InitialFactAp,
        startFactBase: AccessPathBase,
    ) {
        if (startFactBase != AccessPathBase.Return) {
            this += MethodCallPrecondition.UnresolvedCallSkip
        }

        factPassRulePrecondition(callee, fact, startFactBase).forEach {
            this += MethodCallPrecondition.CallToReturnTaintRule(it)
        }
    }

    private fun preconditionForFact(fact: InitialFactAp): List<CallPreconditionFact>? {
        if (!methodCallFactMapper.factIsRelevantToMethodCall(statement, returnValue, callExpr, fact)) return null

        val preconditions = mutableListOf<CallPreconditionFact>()

        val ret = returnValue
        if (ret != null && accessPathBase(ret) == fact.base) {
            preconditions += MethodCallPrecondition.CallToStart(fact, AccessPathBase.Return)
        }

        methodCallFactMapper.mapMethodCallToStartFlowFact(
            statement, statement.location.method, callExpr, returnValue = null, fact
        ) { callerFact, startBase ->
            preconditions += MethodCallPrecondition.CallToStart(callerFact, startBase)
        }

        return preconditions
    }

    private fun factSourceRulePrecondition(
        callee: PIRFunction,
        fact: InitialFactAp,
        startBase: AccessPathBase,
    ): List<TaintRulePrecondition> {
        val sourceRules = rulesProvider.sourcesForMethod(callee)
        if (sourceRules.isEmpty()) return emptyList()

        val entryFactReader = InitialFactReader(fact.rebase(startBase), apManager)
        val evaluator = TaintSourceActionPreconditionEvaluator(entryFactReader)
        val conditionRewriter = callConditionRewriter()

        val result = mutableListOf<TaintRulePrecondition>()
        for (rule in conditionRewriter.rulesWithConditions(sourceRules)) {
            result += evaluateSourceRulePrecondition(
                rule,
                rule.rule.taint,
                sourcePreconditionEvaluator = evaluator,
                evalAction = { r, a ->
                    val pos = a.pos.resolveAp(statement)
                    if (pos == null) Maybe.none() else evaluate(r, a, pos, TaintMarkAccessor(a.mark.name))
                },
            )
        }
        return result
    }

    private fun factPassRulePrecondition(
        callee: PIRFunction,
        fact: InitialFactAp,
        startFactBase: AccessPathBase,
    ): List<TaintRulePrecondition> = buildList {
        val passRules = rulesProvider.passThroughForMethod(callee)
            .ifEmpty { rulesProvider.passThroughForMethod(callee, bySimpleName = true) }
        if (passRules.isEmpty()) return@buildList

        val entryFactReader = InitialFactReader(fact.rebase(startFactBase), apManager)
        val rulePreconditionEvaluator = TaintPassActionPreconditionEvaluator(entryFactReader)
        val conditionRewriter = callConditionRewriter()

        for (rule in conditionRewriter.rulesWithConditions(passRules)) {
            this += evaluatePassRulePrecondition(
                rule,
                rule.rule.copy,
                preconditionEvaluator = rulePreconditionEvaluator,
                evalAction = { r, a -> acceptPass(r, a) },
                mapExit2Return = { mapExit2Return(it) },
            )
        }
    }

    private fun TaintPassActionPreconditionEvaluator.acceptPass(
        rule: TaintPassThrough,
        action: TaintPassAction,
    ): Maybe<List<Pair<org.opentaint.dataflow.configuration.CommonTaintAction, InitialFactAp>>> {
        val from = action.from.resolveAp(statement) ?: return Maybe.none()
        val to = action.to.resolveAp(statement) ?: return Maybe.none()
        val mark = action.mark
        return if (mark == null) {
            propagateData(rule, action, from, to)
        } else {
            propagateTaint(rule, action, from, to, TaintMarkAccessor(mark.name))
        }
    }

    private fun callConditionRewriter() =
        PIRConditionRewriter(PIRCallAnyArgumentResolver(statement), PIRCallAtomEvaluator(statement), statement)

    override fun allStatements(): List<CommonInst> =
        statement.location.method.instList.toList()
}
