package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.removeTrueLiterals
import org.opentaint.dataflow.jvm.ap.ifds.taint.EvaluatedCleanAction
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
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

    private val conditionedActions: List<Triple<TaintConfigurationItem, List<AssignMark>, JIRMarkAwareConditionExpr?>> by lazy {
        val method = callExpr.method.method
        val sourceRules = config.sourceRulesForMethod(method, statement).toList()
        if (sourceRules.isEmpty()) return@lazy emptyList()

        val conditionedActions = mutableListOf<Triple<TaintConfigurationItem, List<AssignMark>, JIRMarkAwareConditionExpr?>>()

        for (rule in sourceRules) {
            val ruleCondition = rule.condition
            val simplifiedCondition = conditionRewriter.rewrite(ruleCondition)
            val conditionExpr = when {
                simplifiedCondition.isFalse -> continue
                simplifiedCondition.isTrue -> null
                else -> simplifiedCondition.expr
            }

            conditionedActions.add(Triple(rule, rule.actionsAfter, conditionExpr))
        }

        conditionedActions
    }

    fun rewriteSummaryFact(fact: FinalFactAp): Pair<FinalFactAp, FinalFactReader>? {
        val startFactReader = FinalFactReader(fact, apManager)

        val cleanEvaluator = TaintCleanActionEvaluator()
        var cleanedFact = EvaluatedCleanAction.initial(startFactReader)

        for ((rule, actions, cond) in conditionedActions) {
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

                for (mark in markToExclude) {
                    val removeAction = RemoveMark(mark, action.position)
                    cleanedFact = cleanEvaluator.evaluate(cleanedFact, rule, removeAction)
                }
            }
        }

        val resultFact = cleanedFact.fact ?: return null
        return resultFact.factAp to resultFact
    }
}
