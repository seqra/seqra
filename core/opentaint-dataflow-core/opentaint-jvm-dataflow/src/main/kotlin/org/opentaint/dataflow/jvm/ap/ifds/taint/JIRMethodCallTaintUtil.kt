package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.TraceInfo
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.JirCondition
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.accept
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.dataflow.taint.FinalFactReader
import org.opentaint.dataflow.taint.TaintSourceActionEvaluator
import org.opentaint.dataflow.taint.TaintUtil
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.util.onSome

class JIRMethodCallTaintUtil(
    apManager: ApManager,
    val statement: JIRInst,
    val callExpr: JIRCallExpr,
    val analysisContext: JIRMethodAnalysisContext,
    val generateTrace: Boolean,
) : TaintUtil<JirCondition, TaintMethodSource, TaintMethodSink, TraceInfo>(apManager) {
    private val sinkTracker get() = analysisContext.taint.taintSinkTracker

    override fun sourceAssumptionsManager(): RuleAssumptionsManager<TaintMethodSource> =
        object : RuleAssumptionsManager<TaintMethodSource> {
            override fun storeAssumptions(
                rule: TaintMethodSource,
                assumptions: Map<InitialFactAp, Set<InitialFactAp>>
            ) = storeInfo {
                sinkTracker.addSourceRuleAssumptions(rule, statement, assumptions)
            }

            override fun currentAssumptions(rule: TaintMethodSource): Set<InitialFactAp> =
                sinkTracker.currentSourceRuleAssumptions(rule, statement)

            override fun currentAssumptionPreconditions(
                rule: TaintMethodSource,
                assumptions: List<InitialFactAp>
            ) = sinkTracker.currentSourceRuleAssumptionsPreconditions(rule, statement, assumptions)
        }

    override fun sinkAssumptionsManager(): RuleAssumptionsManager<TaintMethodSink> =
        object : RuleAssumptionsManager<TaintMethodSink> {
            override fun storeAssumptions(rule: TaintMethodSink, assumptions: Map<InitialFactAp, Set<InitialFactAp>>) =
                storeInfo {
                    sinkTracker.addSinkRuleAssumptions(rule, statement, assumptions)
                }

            override fun currentAssumptions(rule: TaintMethodSink): Set<InitialFactAp> =
                sinkTracker.currentSinkRuleAssumptions(rule, statement)
        }

    override fun conditionFact(factReader: FinalFactReader): List<FinalFactReader> {
        val conditionFactReaders = mutableListOf<FinalFactReader>()
        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            statement,
            callee = callExpr.callee,
            callExpr = callExpr,
            returnValue = null,
            factAp = factReader.factAp,
            checker = analysisContext.factTypeChecker
        ) { callerFact, startFactBase ->
            conditionFactReaders += FinalFactReader(callerFact.rebase(startFactBase), apManager)
        }
        return conditionFactReaders
    }

    val factsAfterSink = mutableListOf<Pair<FinalFactAp, TraceInfo>>()

    override fun handleReachedSink(
        rule: TaintMethodSink,
        factReader: FinalFactReader?,
        evaluatedFacts: List<InitialFactAp>
    )  {
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
                storeInfo {
                    sinkTracker.addUnconditionalVulnerability(
                        analysisContext.methodEntryPoint, statement, rule
                    )
                }

                return
            }

            val requiredEndFacts = hashSetOf<FinalFactAp>()
            applySourceAction(rule, rule.trackFactsReachAnalysisEnd, factAfterSinkEvaluator) { f, action ->
                requiredEndFacts += f

                val trace = TraceInfo.Rule(rule, action)
                factsAfterSink += f to trace
            }

            storeInfo {
                sinkTracker.addUnconditionalVulnerabilityWithEndFactRequirement(
                    analysisContext.methodEntryPoint, statement, rule, requiredEndFacts
                )
            }

            return
        }

        val mappedFacts = evaluatedFacts.mapTo(hashSetOf()) {
            it.mapExitToReturnFact() ?: error("Fact mapping failure")
        }

        if (rule.trackFactsReachAnalysisEnd.isEmpty()) {
            storeInfo {
                sinkTracker.addVulnerability(
                    analysisContext.methodEntryPoint, mappedFacts, statement, rule
                )
            }

            return
        }

        val requiredEndFacts = hashSetOf<FinalFactAp>()
        applySourceAction(rule, rule.trackFactsReachAnalysisEnd, factAfterSinkEvaluator) { f, action ->
            requiredEndFacts += f

            val trace = TraceInfo.Rule(rule, action)
            factsAfterSink += f to trace
        }

        storeInfo {
            sinkTracker.addVulnerabilityWithEndFactRequirement(
                analysisContext.methodEntryPoint, mappedFacts, statement, rule, requiredEndFacts
            )
        }
    }

    override fun applySourceAction(
        rule: TaintMethodSource,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp, TraceInfo) -> Unit
    ) = applySourceAction(rule, rule.actionsAfter, sourceEvaluator) { f, action ->
        val trace = TraceInfo.Rule(rule, action)
        createFinalFact(f, trace)
    }

    private inline fun applySourceAction(
        rule: TaintConfigurationItem,
        actions: List<AssignMark>,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp, AssignMark) -> Unit,
    ) {
        for (action in actions) {
            sourceEvaluator.accept(rule, action).onSome { facts ->
                facts.forEach { f -> f.mapExitToReturnFact()?.also { createFinalFact(it, action) } }
            }
        }
    }

    private fun FinalFactAp.mapExitToReturnFact(): FinalFactAp? =
        JIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, this, analysisContext.factTypeChecker)
            .singleOrNull()

    private fun InitialFactAp.mapExitToReturnFact(): InitialFactAp? =
        JIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, this)
            .singleOrNull()

    private inline fun storeInfo(body: () -> Unit) {
        if (generateTrace) return
        body()
    }
}
