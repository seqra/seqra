package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.taint.EvaluatedCleanAction
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.UserDefinedRuleInfo
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr

class JIRMethodCallRuleBasedSummaryRewriter(
    private val statement: JIRInst,
    private val analysisContext: JIRMethodAnalysisContext,
    private val apManager: ApManager
) {
    private val config get() = analysisContext.taint.taintConfig as TaintRulesProvider

    private val callExpr by lazy {
        statement.callExpr ?: error("Call summary handler at statement without method call")
    }

    private val conditionRewriter by lazy {
        val returnValue: JIRImmediate? = (statement as? JIRAssignInst)?.lhv as? JIRImmediate

        JIRMarkAwareConditionRewriter(
            CallPositionToJIRValueResolver(callExpr, returnValue),
            analysisContext.factTypeChecker
        )
    }

    private data class UserRuleDefinedAction(
        val rule: TaintConfigurationItem,
        val positions: List<Position>,
        val controlledMarks: Set<String>
    )

    private val userRuleDefinedActions: List<UserRuleDefinedAction> by lazy {
        val method = callExpr.method.method

        val result = mutableListOf<UserRuleDefinedAction>()
        for (sourceRule in config.sourceRulesForMethod(method, statement)) {
            val ruleInfo = sourceRule.info as? UserDefinedRuleInfo ?: continue

            val simplifiedCondition = conditionRewriter.rewrite(sourceRule.condition)
            if (simplifiedCondition.isFalse) continue

            val positions = sourceRule.actionsAfter.map { it.position }
            result += UserRuleDefinedAction(sourceRule, positions, ruleInfo.relevantTaintMarks)
        }

        for (cleanRule in config.cleanerRulesForMethod(method, statement)) {
            val ruleInfo = cleanRule.info as? UserDefinedRuleInfo ?: continue

            val simplifiedCondition = conditionRewriter.rewrite(cleanRule.condition)
            if (simplifiedCondition.isFalse) continue

            val positions = cleanRule.actionsAfter.filterIsInstance<RemoveMark>().map { it.position }
            result += UserRuleDefinedAction(cleanRule, positions, ruleInfo.relevantTaintMarks)
        }

        result
    }

    fun rewriteSummaryFact(fact: FinalFactAp): Pair<FinalFactAp, FinalFactReader>? {
        val startFactReader = FinalFactReader(fact, apManager)

        val cleanEvaluator = TaintCleanActionEvaluator()
        var cleanedFact = EvaluatedCleanAction.initial(startFactReader)

        for (ruleDefinedAction in userRuleDefinedActions) {
            val markToExclude = ruleDefinedAction.controlledMarks.map { TaintMark(it) }
            for (mark in markToExclude) {
                for (pos in ruleDefinedAction.positions) {
                    val removeAction = RemoveMark(mark, pos)
                    cleanedFact = cleanEvaluator.evaluate(cleanedFact, ruleDefinedAction.rule, removeAction)
                }
            }
        }

        val resultFact = cleanedFact.fact ?: return null
        return resultFact.factAp to resultFact
    }
}
