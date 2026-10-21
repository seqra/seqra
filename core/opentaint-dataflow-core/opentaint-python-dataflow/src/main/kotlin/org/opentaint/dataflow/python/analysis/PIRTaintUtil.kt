package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.TraceInfo
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.python.PythonRuleCondition
import org.opentaint.dataflow.configuration.python.TaintAssignAction
import org.opentaint.dataflow.configuration.python.TaintConfigurationItem
import org.opentaint.dataflow.configuration.python.TaintConfigurationSink
import org.opentaint.dataflow.configuration.python.TaintConfigurationSource
import org.opentaint.dataflow.python.PIRFlowFunctionUtils
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.resolveAp
import org.opentaint.dataflow.taint.FinalFactReader
import org.opentaint.dataflow.taint.TaintSourceActionEvaluator
import org.opentaint.dataflow.taint.TaintUtil
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.ir.api.python.PIRLoadAttr
import org.opentaint.ir.api.python.PIRReturn
import org.opentaint.util.onSome

abstract class PIRTaintUtil<I : PIRInstruction, TraceInfo>(
    val context: PIRMethodAnalysisContext,
    val statement: I,
    apManager: ApManager
) : TaintUtil<PythonRuleCondition, TaintConfigurationSource, TaintConfigurationSink, TraceInfo>(apManager) {
    private val sinkTracker get() = context.taint.taintSinkTracker

    val factsAfterSink = mutableListOf<Pair<FinalFactAp, TraceInfo>>()

    override fun sourceAssumptionsManager(): RuleAssumptionsManager<TaintConfigurationSource> =
        object : RuleAssumptionsManager<TaintConfigurationSource> {
            override fun storeAssumptions(
                rule: TaintConfigurationSource,
                assumptions: Map<InitialFactAp, Set<InitialFactAp>>
            ) {
                sinkTracker.addSourceRuleAssumptions(rule, statement, assumptions)
            }

            override fun currentAssumptions(rule: TaintConfigurationSource): Set<InitialFactAp> =
                sinkTracker.currentSourceRuleAssumptions(rule, statement)

            override fun currentAssumptionPreconditions(
                rule: TaintConfigurationSource,
                assumptions: List<InitialFactAp>
            ) = sinkTracker.currentSourceRuleAssumptionsPreconditions(rule, statement, assumptions)
        }

    override fun sinkAssumptionsManager(): RuleAssumptionsManager<TaintConfigurationSink> =
        object : RuleAssumptionsManager<TaintConfigurationSink> {
            override fun storeAssumptions(
                rule: TaintConfigurationSink,
                assumptions: Map<InitialFactAp, Set<InitialFactAp>>
            ) {
                sinkTracker.addSinkRuleAssumptions(rule, statement, assumptions)
            }

            override fun currentAssumptions(rule: TaintConfigurationSink): Set<InitialFactAp> =
                sinkTracker.currentSinkRuleAssumptions(rule, statement)
        }

    override fun handleReachedSink(
        rule: TaintConfigurationSink,
        factReader: FinalFactReader?,
        evaluatedFacts: List<InitialFactAp>
    ) {
        val factAfterSinkEvaluator by lazy {
            TaintSourceActionEvaluator(apManager, exclusion = ExclusionSet.Universe)
        }

        if (evaluatedFacts.isEmpty()) {
            if (factReader != null) return

            if (rule.trackFactsReachAnalysisEnd.isEmpty()) {
                sinkTracker.addUnconditionalVulnerability(context.methodEntryPoint, statement, rule)
                return
            }

            sinkTracker.addUnconditionalVulnerabilityWithEndFactRequirement(
                context.methodEntryPoint,
                statement,
                rule,
                requiredEndFacts(rule, factAfterSinkEvaluator),
            )
            return
        }

        val callerFacts = evaluatedFacts.mapTo(hashSetOf()) {
            mapFactToReturn(it).single()
        }

        if (rule.trackFactsReachAnalysisEnd.isEmpty()) {
            sinkTracker.addVulnerability(context.methodEntryPoint, callerFacts, statement, rule)
            return
        }

        sinkTracker.addVulnerabilityWithEndFactRequirement(
            context.methodEntryPoint,
            callerFacts,
            statement,
            rule,
            requiredEndFacts(rule, factAfterSinkEvaluator),
        )
    }

    private fun requiredEndFacts(
        rule: TaintConfigurationSink,
        sourceEvaluator: TaintSourceActionEvaluator,
    ): Set<FinalFactAp> {
        val facts = hashSetOf<FinalFactAp>()
        applySourceAction(rule, rule.trackFactsReachAnalysisEnd, sourceEvaluator) { fact, action ->
            facts += fact
            factsAfterSink += fact to createRuleTraceInfo(rule, action)
        }
        return facts
    }

    protected open val positionCall: PIRCall? get() = null

    override fun applySourceAction(
        rule: TaintConfigurationSource,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp, TraceInfo) -> Unit
    ) = applySourceAction(rule, rule.taint, sourceEvaluator) { fact, action ->
        createFinalFact(fact, createRuleTraceInfo(rule, action))
    }

    private inline fun applySourceAction(
        rule: TaintConfigurationItem,
        actions: List<TaintAssignAction>,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp, TaintAssignAction) -> Unit,
    ) {
        actions.forEach { action ->
            val pos = action.pos.resolveAp(positionCall) ?: return@forEach
            val mark = TaintMarkAccessor(action.mark.name)
            sourceEvaluator.evaluate(rule, action, pos, mark).onSome { facts ->
                facts.forEach { fact ->
                    val callerFacts = mapFactToReturn(fact)
                    callerFacts.forEach { createFinalFact(it, action) }
                }
            }
        }
    }

    abstract fun createRuleTraceInfo(rule: CommonTaintConfigurationItem, action: CommonTaintAction): TraceInfo
    abstract fun mapFactToReturn(fact: FinalFactAp): List<FinalFactAp>
    abstract fun mapFactToReturn(fact: InitialFactAp): List<InitialFactAp>
}

class PIRMethodCallTaintUtil(
    context: PIRMethodAnalysisContext,
    statement: PIRCall,
    apManager: ApManager
) : PIRTaintUtil<PIRCall, TraceInfo>(context, statement, apManager) {
    private val callFactMapper get() = context.methodCallFactMapper

    override fun createRuleTraceInfo(rule: CommonTaintConfigurationItem, action: CommonTaintAction): TraceInfo =
        TraceInfo.Rule(rule, action)

    override val positionCall get() = statement

    override fun mapFactToReturn(fact: FinalFactAp) =
        callFactMapper.mapMethodExitToReturnFlowFact(statement, fact, FactTypeChecker.Dummy)

    override fun mapFactToReturn(fact: InitialFactAp) =
        callFactMapper.mapMethodExitToReturnFlowFact(statement, fact)

    override fun conditionFact(factReader: FinalFactReader): List<FinalFactReader> = listOf(factReader)
}

class PIRSequentTaintUtil(
    context: PIRMethodAnalysisContext,
    statement: PIRInstruction,
    apManager: ApManager,
) : PIRTaintUtil<PIRInstruction, MethodSequentFlowFunction.TraceInfo>(context, statement, apManager) {
    private val callFactMapper get() = context.methodCallFactMapper

    override fun createRuleTraceInfo(rule: CommonTaintConfigurationItem, action: CommonTaintAction) =
        MethodSequentFlowFunction.TraceInfo.Rule(rule, action)

    override fun mapFactToReturn(fact: FinalFactAp) = when (statement) {
        is PIRLoadAttr -> listOfNotNull(callFactMapper.mapLoadAttributeFactToReturn(statement, fact))
        is PIRReturn -> listOfNotNull(fact.fromResultBase(statement))
        else -> error("Unexpected statement: $statement")
    }

    override fun mapFactToReturn(fact: InitialFactAp) = when (statement) {
        is PIRLoadAttr -> listOfNotNull(callFactMapper.mapLoadAttributeFactToReturn(statement, fact))
        is PIRReturn -> listOfNotNull(fact.fromResultBase(statement))
        else -> error("Unexpected statement: $statement")
    }

    override fun conditionFact(factReader: FinalFactReader): List<FinalFactReader> = when (statement) {
        is PIRLoadAttr -> buildList {
            callFactMapper.mapLoadAttributeFactToStart(statement, factReader.factAp) { fact, newBase ->
                this += FinalFactReader(fact.rebase(newBase), apManager)
            }
        }
        is PIRReturn -> listOfNotNull(factReader.factAp.toResultBase(statement)?.let { FinalFactReader(it, apManager) })
        else -> error("Unexpected statement: $statement")
    }

    // TODO move to fact mapper
    private fun FinalFactAp.toResultBase(statement: PIRReturn): FinalFactAp? {
        val result = statement.value?.let { PIRFlowFunctionUtils.accessPathBase(it) }
            ?: return null

        return when (base) {
            result -> rebase(AccessPathBase.Return)
            is AccessPathBase.ClassStatic -> this
            else -> null
        }
    }

    private fun FinalFactAp.fromResultBase(statement: PIRReturn): FinalFactAp? {
        val result = statement.value?.let { PIRFlowFunctionUtils.accessPathBase(it) }
            ?: return null

        return when (base) {
            AccessPathBase.Return -> rebase(result)
            is AccessPathBase.ClassStatic -> this
            else -> null
        }
    }

    private fun InitialFactAp.fromResultBase(statement: PIRReturn): InitialFactAp? {
        val result = statement.value?.let { PIRFlowFunctionUtils.accessPathBase(it) }
            ?: return null

        return when (base) {
            AccessPathBase.Return -> rebase(result)
            is AccessPathBase.ClassStatic -> this
            else -> null
        }
    }
}
