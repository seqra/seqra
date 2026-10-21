package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.python.Position
import org.opentaint.dataflow.configuration.python.TaintCleanAction
import org.opentaint.dataflow.configuration.python.TaintConfigurationItem
import org.opentaint.dataflow.configuration.python.TaintMark
import org.opentaint.dataflow.configuration.python.serialized.PIRUserDefinedRuleInfo
import org.opentaint.dataflow.python.PIRCallAnyArgumentResolver
import org.opentaint.dataflow.python.PIRCallAtomEvaluator
import org.opentaint.dataflow.python.PIRConditionRewriter
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.resolveAp
import org.opentaint.dataflow.taint.EvaluatedCleanAction
import org.opentaint.dataflow.taint.FinalFactReader
import org.opentaint.dataflow.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.taint.applyCleanerActions
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRFunction

class PIRCallRuleBasedSummaryRewriter(
    private val callInst: PIRCall,
    private val ctx: PIRMethodAnalysisContext,
    private val apManager: ApManager,
    private val method: PIRFunction,
) {
    private val config get() = ctx.taint.taintConfig

    private data class UserRuleDefinedAction(
        val rule: TaintConfigurationItem,
        val positions: Set<Position>,
        val controlledMarks: Set<String>,
    )

    private val userRuleDefinedActions: List<UserRuleDefinedAction> by lazy {
        val conditionRewriter = PIRConditionRewriter(
            PIRCallAnyArgumentResolver(callInst), PIRCallAtomEvaluator(callInst), callInst
        )

        val result = mutableListOf<UserRuleDefinedAction>()
        for (sourceRule in config.sourcesForMethod(method)) {
            val ruleInfo = sourceRule.info as? PIRUserDefinedRuleInfo ?: continue

            val simplifiedCondition = conditionRewriter.rewrite(sourceRule.condition)
            if (simplifiedCondition.isFalse) continue

            val positions = sourceRule.taint.mapTo(hashSetOf()) { it.pos }
            result += UserRuleDefinedAction(sourceRule, positions, ruleInfo.relevantTaintMarks)
        }

        for (cleanRule in config.cleanersForMethod(method)) {
            val ruleInfo = cleanRule.info as? PIRUserDefinedRuleInfo ?: continue

            val simplifiedCondition = conditionRewriter.rewrite(cleanRule.condition)
            if (simplifiedCondition.isFalse) continue

            cleanRule.cleans.forEach { action ->
                result += UserRuleDefinedAction(cleanRule, setOf(action.pos), ruleInfo.relevantTaintMarks + action.mark.name)
            }
        }

        result
    }

    fun rewriteSummaryFact(fact: FinalFactAp): List<Pair<FinalFactAp, FinalFactReader>> {
        val startFactReader = FinalFactReader(fact, apManager)

        val cleanEvaluator = TaintCleanActionEvaluator()

        val cleanedFact = userRuleDefinedActions.applyCleanerActions(
            evalAction = { f, rule, action ->
                val pos = action.pos.resolveAp(callInst) ?: return@applyCleanerActions listOf(f)

                cleanEvaluator.removeFinalFact(
                    f, pos, TaintMarkAccessor(action.mark.name), rule, action, TaintCleanReach.Exact
                )
            },
            itemRule = { it.rule },
            itemActions = { action ->
                action.controlledMarks.flatMap { mark ->
                    action.positions.map { TaintCleanAction(TaintMark(mark), it) }
                }
            },
            initial = EvaluatedCleanAction.initial(startFactReader),
        )

        return cleanedFact.mapNotNull {
            val resultFact = it.fact ?: return@mapNotNull null
            resultFact.factAp to resultFact
        }
    }
}
