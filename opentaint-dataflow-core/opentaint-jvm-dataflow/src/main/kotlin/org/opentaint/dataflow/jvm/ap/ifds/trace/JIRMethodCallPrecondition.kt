package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.ap.ifds.FinalFactReader
import org.opentaint.dataflow.ap.ifds.InitialFactReader
import org.opentaint.dataflow.ap.ifds.TaintPassActionPreconditionEvaluator
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider
import org.opentaint.dataflow.ap.ifds.TaintSourceActionPreconditionEvaluator
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallPositionToAccessPathResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.util.Maybe

class JIRMethodCallPrecondition(
    private val apManager: ApManager,
    private val taintConfig: TaintRulesProvider,
    returnValue: JIRImmediate?,
    callExpr: JIRCallExpr,
    factsAtStatement: List<FinalFactAp>,
    traits: JIRTraits
): MethodCallPrecondition {
    private val predecessorFactsReaders = factsAtStatement.map { FinalFactReader(it, apManager) }
    private val apResolver = JIRCallPositionToAccessPathResolver(callExpr, returnValue)
    private val jirValueResolver = CallPositionToJIRValueResolver(callExpr, returnValue)
    private val method = callExpr.callee

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
            ruleConditionEvaluator,
            rulePreconditionEvaluator
        )
    }
}