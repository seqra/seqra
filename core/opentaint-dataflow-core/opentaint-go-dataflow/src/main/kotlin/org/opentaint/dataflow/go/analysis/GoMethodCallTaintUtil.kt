package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.TraceInfo
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.go.GoCallExpr
import org.opentaint.dataflow.go.GoMethodCallFactMapper.mapMethodCallToStartFlowAnyFact
import org.opentaint.dataflow.go.GoMethodCallFactMapper.mapMethodExitToReturnFlowFact
import org.opentaint.dataflow.go.rules.GoAssignAction
import org.opentaint.dataflow.go.rules.GoRuleCondition
import org.opentaint.dataflow.go.rules.TaintRule
import org.opentaint.dataflow.taint.FinalFactReader
import org.opentaint.dataflow.taint.TaintSourceActionEvaluator
import org.opentaint.dataflow.taint.TaintUtil
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.value.GoIRValue
import org.opentaint.util.onSome

class GoMethodCallTaintUtil(
    private val statement: GoIRInst,
    private val callExpr: GoCallExpr,
    val returnValue: GoIRValue?,
    private val context: GoMethodAnalysisContext,
    apManager: ApManager
) : TaintUtil<GoRuleCondition, TaintRule.Source, TaintRule.Sink, TraceInfo>(apManager) {
    private val sinkTracker get() = context.taint.taintSinkTracker

    override fun sourceAssumptionsManager(): RuleAssumptionsManager<TaintRule.Source> =
        object : RuleAssumptionsManager<TaintRule.Source> {
            override fun storeAssumptions(
                rule: TaintRule.Source,
                assumptions: Map<InitialFactAp, Set<InitialFactAp>>
            ) {
                sinkTracker.addSourceRuleAssumptions(rule, statement, assumptions)
            }

            override fun currentAssumptions(rule: TaintRule.Source): Set<InitialFactAp> =
                sinkTracker.currentSourceRuleAssumptions(rule, statement)

            override fun currentAssumptionPreconditions(
                rule: TaintRule.Source,
                assumptions: List<InitialFactAp>
            ) = sinkTracker.currentSourceRuleAssumptionsPreconditions(rule, statement, assumptions)
        }

    override fun sinkAssumptionsManager(): RuleAssumptionsManager<TaintRule.Sink> =
        object : RuleAssumptionsManager<TaintRule.Sink> {
            override fun storeAssumptions(
                rule: TaintRule.Sink,
                assumptions: Map<InitialFactAp, Set<InitialFactAp>>
            ) {
                sinkTracker.addSinkRuleAssumptions(rule, statement, assumptions)
            }

            override fun currentAssumptions(rule: TaintRule.Sink): Set<InitialFactAp> =
                sinkTracker.currentSinkRuleAssumptions(rule, statement)
        }

    override fun conditionFact(factReader: FinalFactReader): List<FinalFactReader> {
        val readers = mutableListOf<FinalFactReader>()
        mapMethodCallToStartFlowAnyFact(
            statement,
            callExpr,
            factReader.factAp,
        ) { fact, startBase ->
            readers += FinalFactReader(fact.rebase(startBase), apManager)
        }
        return readers
    }

    override fun handleReachedSink(
        rule: TaintRule.Sink,
        factReader: FinalFactReader?,
        evaluatedFacts: List<InitialFactAp>
    ) {
        val factAfterSinkEvaluator by lazy {
            TaintSourceActionEvaluator(
                apManager,
                exclusion = ExclusionSet.Universe,
            )
        }

        if (evaluatedFacts.isEmpty()) {
            // unconditional sinks handled with zero fact
            if (factReader != null) return

            if (rule.trackFactsReachAnalysisEnd.isEmpty()) {
                sinkTracker.addUnconditionalVulnerability(
                    context.methodEntryPoint, statement, rule
                )
                return
            }

            val requiredEndFacts = hashSetOf<FinalFactAp>()
            applySourceAction(rule, rule.trackFactsReachAnalysisEnd, factAfterSinkEvaluator) { f ->
                requiredEndFacts += f
            }

            sinkTracker.addUnconditionalVulnerabilityWithEndFactRequirement(
                context.methodEntryPoint, statement, rule, requiredEndFacts
            )
            return
        }

        val mappedFacts = evaluatedFacts.mapTo(hashSetOf()) {
            it.mapExitToReturnFact() ?: error("Fact mapping failure")
        }

        if (rule.trackFactsReachAnalysisEnd.isEmpty()) {
            sinkTracker.addVulnerability(
                context.methodEntryPoint, mappedFacts, statement, rule
            )
            return
        }

        val requiredEndFacts = hashSetOf<FinalFactAp>()
        applySourceAction(rule, rule.trackFactsReachAnalysisEnd, factAfterSinkEvaluator) { f ->
            requiredEndFacts += f
        }

        sinkTracker.addVulnerabilityWithEndFactRequirement(
            context.methodEntryPoint, mappedFacts, statement, rule, requiredEndFacts
        )
    }

    override fun applySourceAction(
        rule: TaintRule.Source,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp, TraceInfo) -> Unit
    ) = applySourceAction(rule, rule.actionsAfter, sourceEvaluator) { f ->
        createFinalFact(f, TraceInfo.Flow)
    }

    private inline fun applySourceAction(
        rule: CommonTaintConfigurationItem,
        actions: List<GoAssignAction>,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp) -> Unit,
    ) {
        for (action in actions) {
            val position = action.resolvePosAccess()
            sourceEvaluator.evaluate(rule, action, position, TaintMarkAccessor(action.mark)).onSome { facts ->
                facts.forEach { f ->
                    f.mapExitToReturnFact()?.let { createFinalFact(it) }
                }
            }
        }
    }

    private fun FinalFactAp.mapExitToReturnFact(): FinalFactAp? =
        mapMethodExitToReturnFlowFact(statement, this, FactTypeChecker.Dummy).singleOrNull()

    private fun InitialFactAp.mapExitToReturnFact(): InitialFactAp? =
        mapMethodExitToReturnFlowFact(statement, this).singleOrNull()
}
