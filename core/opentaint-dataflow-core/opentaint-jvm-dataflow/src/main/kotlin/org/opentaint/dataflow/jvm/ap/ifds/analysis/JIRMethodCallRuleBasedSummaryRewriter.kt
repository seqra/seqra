package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.configuration.jvm.ActionPosition
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodPositionBaseTypeResolver
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyCleanerActions
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRTaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveBaseAp
import org.opentaint.dataflow.taint.EvaluatedCleanAction
import org.opentaint.dataflow.taint.FinalFactReader
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr

class JIRMethodCallRuleBasedSummaryRewriter(
    private val statement: JIRInst,
    private val analysisContext: JIRMethodAnalysisContext,
    private val apManager: ApManager
) {
    private val taintCtx get() = analysisContext.taint

    private val callExpr by lazy {
        statement.callExpr ?: error("Call summary handler at statement without method call")
    }

    private val conditionRewriter by lazy {
        val returnValue: JIRImmediate? = (statement as? JIRAssignInst)?.lhv as? JIRImmediate

        JIRMarkAwareConditionRewriter(
            CallPositionToJIRValueResolver(callExpr, returnValue),
            analysisContext, statement
        )
    }

    private val typeResolver by lazy {
        JIRMethodPositionBaseTypeResolver(callExpr.method.method)
    }

    private data class UserRuleDefinedAction(
        val rule: TaintConfigurationItem,
        val positions: Set<ActionPosition>,
    )

    private val userRuleDefinedActions: Map<AccessPathBase, Map<String, List<UserRuleDefinedAction>>> by lazy {
        val result = hashMapOf<AccessPathBase, MutableMap<String, MutableList<UserRuleDefinedAction>>>()

        fun indexRule(rule: TaintConfigurationItem, positions: Set<ActionPosition>, marks: Set<String>) {
            positions.groupBy { it.resolveBaseAp() }.forEach { (base, basePositions) ->
                val actionsByMark = result.computeIfAbsent(base) { hashMapOf() }
                val action = UserRuleDefinedAction(rule, basePositions.toSet())
                marks.forEach { mark ->
                    actionsByMark.computeIfAbsent(mark) { mutableListOf() }.add(action)
                }
            }
        }

        for (sourceRule in taintCtx.allRelevantSourceRulesForCallStatement(statement)) {
            val ruleInfo = sourceRule.info as? UserDefinedRuleInfo ?: continue

            val simplifiedCondition = conditionRewriter.rewrite(sourceRule.condition)
            if (simplifiedCondition.isFalse) continue

            val positions = sourceRule.actionsAfter.mapTo(hashSetOf()) { it.position }
            indexRule(sourceRule, positions, ruleInfo.relevantTaintMarks)
        }

        for (cleanRule in taintCtx.allRelevantCleanRulesForCallStatement(statement)) {
            val ruleInfo = cleanRule.info as? UserDefinedRuleInfo ?: continue

            val simplifiedCondition = conditionRewriter.rewrite(cleanRule.condition)
            if (simplifiedCondition.isFalse) continue

            val positions = cleanRule.actionsAfter.filterIsInstance<RemoveMark>().mapTo(hashSetOf()) { it.position }
            indexRule(cleanRule, positions, ruleInfo.relevantTaintMarks)
        }

        result
    }

    fun rewriteSummaryFact(fact: FinalFactAp): List<Pair<FinalFactAp, FinalFactReader>> {
        val startFactReader = FinalFactReader(fact, apManager)
        val actionsForBase = userRuleDefinedActions[fact.base].orEmpty()
        if (actionsForBase.isEmpty()) return listOf(fact to startFactReader)

        val cleanEvaluator = JIRTaintCleanActionEvaluator()
        val cleanedFact = actionsForBase.entries.applyCleanerActions(
            initial = EvaluatedCleanAction.initial(startFactReader)
        ) { (mark, actions), current ->
            actions.applyCleanerActions(
                evaluator = cleanEvaluator,
                itemRule = { it.rule },
                itemActions = { ruleDefinedAction ->
                    val taintMark = TaintMark(mark)
                    ruleDefinedAction.positions.map { RemoveMark(taintMark, it) }
                },
                initial = current
            )
        }

        return cleanedFact.mapNotNull {
            val resultFact = it.fact ?: return@mapNotNull null
            resultFact.factAp to resultFact
        }
    }
}
