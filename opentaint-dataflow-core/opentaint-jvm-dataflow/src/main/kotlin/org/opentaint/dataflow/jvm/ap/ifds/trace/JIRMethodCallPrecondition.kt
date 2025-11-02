package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.ap.ifds.CalleePositionToAccessPath
import org.opentaint.dataflow.ap.ifds.FinalFactReader
import org.opentaint.dataflow.ap.ifds.InitialFactReader
import org.opentaint.dataflow.ap.ifds.taint.TaintPassActionPreconditionEvaluator
import org.opentaint.dataflow.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.ap.ifds.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.config.JIRBasicConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallPositionToAccessPathResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.util.Maybe

class JIRMethodCallPrecondition(
    private val apManager: ApManager,
    private val analysisContext: JIRMethodAnalysisContext,
    returnValue: JIRImmediate?,
    callExpr: JIRCallExpr,
    private val statement: JIRInst,
    factsAtStatement: List<FinalFactAp>,
    traits: JIRTraits
): MethodCallPrecondition {
    private val predecessorFactsReaders = factsAtStatement.map { FinalFactReader(it, apManager) }
    private val apResolver = JIRCallPositionToAccessPathResolver(callExpr, returnValue)
    private val jirValueResolver = CallPositionToJIRValueResolver(callExpr, returnValue)
    private val method = callExpr.callee

    private val taintConfig get() = analysisContext.taint.taintConfig

    private val ruleConditionEvaluator = JIRFactAwareConditionEvaluator(
        traits,
        predecessorFactsReaders,
        apResolver,
        jirValueResolver
    )


    override fun factSourceRulePrecondition(initialFact: InitialFactAp): Maybe<List<Pair<TaintConfigurationItem, AssignMark>>> {
        val entryFactReader = InitialFactReader(initialFact, apManager)
        val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(apResolver, entryFactReader)

        return TaintConfigUtils.applySourceConfig(
                taintConfig,
                method,
                statement,
                ruleConditionEvaluator,
                sourcePreconditionEvaluator
            )
        }

    override fun factPassRulePrecondition(initialFact: InitialFactAp): Maybe<List<MethodCallPrecondition.Precondition>> {
        val entryFactReader = InitialFactReader(initialFact, apManager)
        val rulePreconditionEvaluator = TaintPassActionPreconditionEvaluator(apResolver, entryFactReader)

        return TaintConfigUtils.applyPassThrough(
            taintConfig,
            method,
            statement,
            ruleConditionEvaluator,
            rulePreconditionEvaluator
        )
    }

    companion object {
        fun getEntryPointPrecondition(
            apManager: ApManager,
            config: TaintRulesProvider,
            method: JIRMethod,
            traits: JIRTraits,
            initialFact: InitialFactAp,
        ): Maybe<List<Pair<TaintConfigurationItem, AssignMark>>> {
            val entryFactReader = InitialFactReader(initialFact, apManager)
            val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(
                CalleePositionToAccessPath(),
                entryFactReader
            )

            val conditionEvaluator = JIRBasicConditionEvaluator(
                traits,
                CalleePositionToJIRValueResolver(method)
            )

            return TaintConfigUtils.applyEntryPointConfig(
                config, method, conditionEvaluator, sourcePreconditionEvaluator
            )
        }
    }
}