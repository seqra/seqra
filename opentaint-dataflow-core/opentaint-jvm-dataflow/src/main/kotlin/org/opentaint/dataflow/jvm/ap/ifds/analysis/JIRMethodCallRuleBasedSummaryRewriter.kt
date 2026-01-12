package org.opentaint.dataflow.jvm.ap.ifds.analysis

import mu.KotlinLogging
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.removeTrueLiterals
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
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

    private val conditionedActions: List<Pair<List<AssignMark>, JIRMarkAwareConditionExpr?>> by lazy {
        val method = callExpr.method.method
        val sourceRules = config.sourceRulesForMethod(method, statement).toList()
        if (sourceRules.isEmpty()) return@lazy emptyList()

        val conditionedActions = mutableListOf<Pair<List<AssignMark>, JIRMarkAwareConditionExpr?>>()

        for (rule in sourceRules) {
            val ruleCondition = rule.condition
            val simplifiedCondition = conditionRewriter.rewrite(ruleCondition)
            val conditionExpr = when {
                simplifiedCondition.isFalse -> continue
                simplifiedCondition.isTrue -> null
                else -> simplifiedCondition.expr
            }

            conditionedActions.add(rule.actionsAfter to conditionExpr)
        }

        conditionedActions
    }

    fun rewriteSummaryFact(fact: FinalFactAp): Pair<FinalFactAp, FinalFactReader>? {
        val factReader = FinalFactReader(fact, apManager)
        for ((actions, cond) in conditionedActions) {
            val relevantPositiveConditions = hashSetOf<ContainsMark>()
            cond?.removeTrueLiterals {
                if (!it.negated) {
                    relevantPositiveConditions.add(it.condition)
                }
                false
            }

            val allRelevantMarks = relevantPositiveConditions.mapTo(hashSetOf()) { it.mark }

            for (action in actions) {
                val markToExclude = allRelevantMarks.toHashSet()
                markToExclude.remove(action.mark)

                val pos = action.position.resolveAp()
                for (mark in markToExclude) {
                    if (!factReader.containsPositionWithTaintMark(pos, mark)) {
                        continue
                    }

                    logger.error("Summary fact handled unproperly due to conflict with rule")
                    return null
                }
            }
        }

        return fact to factReader
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
