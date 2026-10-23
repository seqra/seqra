package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.go.serialized.GoUserDefinedRuleInfo
import org.opentaint.dataflow.go.GoCallExpr
import org.opentaint.dataflow.go.GoFlowFunctionUtils.resolvePosAccess
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.dataflow.go.rules.ActionPosition
import org.opentaint.dataflow.go.rules.RemoveMark
import org.opentaint.dataflow.go.rules.TaintRule
import org.opentaint.dataflow.go.signature
import org.opentaint.dataflow.taint.EvaluatedCleanAction
import org.opentaint.dataflow.taint.FinalFactReader
import org.opentaint.dataflow.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.taint.applyCleanerActions
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.value.GoIRValue

class GoCallRuleBasedSummaryRewriter(
    private val statement: GoIRInst,
    private val callExpr: GoCallExpr,
    private val returnValue: GoIRValue?,
    private val analysisContext: GoMethodAnalysisContext,
    private val apManager: ApManager,
) {
    private val config get() = analysisContext.taint

    private val callSignature: GoFunctionSignature?
        get() = callExpr.signature()

    private fun ActionPosition.cleanReach(): TaintCleanReach = when (this) {
        is ActionPosition.Exact -> TaintCleanReach.Exact
        is ActionPosition.AnyAccessorAfter -> TaintCleanReach.ExactAndAnyField
    }

    private data class UserRuleDefinedAction(
        val rule: TaintRule,
        val positions: Set<ActionPosition>,
        val controlledMarks: Set<String>
    )

    private val userRuleDefinedActions: List<UserRuleDefinedAction> by lazy {
        val signature = callSignature ?: return@lazy emptyList()

        val result = mutableListOf<UserRuleDefinedAction>()
        for (sourceRuleWithCond in config.allRelevantSourceRulesForCallStatement(signature, statement, callExpr, returnValue)) {
            val sourceRule = sourceRuleWithCond.rule
            val ruleInfo = sourceRule.info as? GoUserDefinedRuleInfo ?: continue

            if (sourceRuleWithCond.condition.isFalse) continue

            val positions = sourceRule.actionsAfter.mapTo(hashSetOf()) { it.pos }
            result += UserRuleDefinedAction(sourceRule, positions, ruleInfo.relevantTaintMarks)
        }

        for (cleanRuleWithCond in config.allRelevantCleanRulesForCallStatement(signature, statement, callExpr, returnValue)) {
            val cleanRule = cleanRuleWithCond.rule
            val ruleInfo = cleanRule.info as? GoUserDefinedRuleInfo ?: continue

            if (cleanRuleWithCond.condition.isFalse) continue

            val positions = cleanRule.actionsAfter.filterIsInstance<RemoveMark>().mapTo(hashSetOf()) { it.pos }
            result += UserRuleDefinedAction(cleanRule, positions, ruleInfo.relevantTaintMarks)
        }

        result
    }

    fun rewriteSummaryFact(fact: FinalFactAp): List<Pair<FinalFactAp, FinalFactReader>> {
        val startFactReader = FinalFactReader(fact, apManager)

        val cleanEvaluator = TaintCleanActionEvaluator()

        val cleanedFact = userRuleDefinedActions.applyCleanerActions(
            evalAction = { f, rule, action ->
                val pos = action.pos.resolvePosAccess()
                cleanEvaluator.removeFinalFact(f, pos, TaintMarkAccessor(action.mark), rule, action, action.pos.cleanReach())
            },
            itemRule = { it.rule },
            itemActions = { action ->
                action.controlledMarks.flatMap { mark ->
                    action.positions.map { RemoveMark(mark, it) }
                }
            },
            initial = EvaluatedCleanAction.initial(startFactReader)
        )

        return cleanedFact.mapNotNull {
            val resultFact = it.fact ?: return@mapNotNull null
            resultFact.factAp to resultFact
        }
    }
}
