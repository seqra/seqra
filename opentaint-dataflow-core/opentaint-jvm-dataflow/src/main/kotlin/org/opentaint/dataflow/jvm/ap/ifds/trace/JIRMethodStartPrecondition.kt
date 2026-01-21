package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.JIRSimpleFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.util.onSome

class JIRMethodStartPrecondition(
    private val apManager: ApManager,
    private val context: JIRMethodAnalysisContext,
) : MethodStartPrecondition {
    override fun factPrecondition(fact: InitialFactAp): List<TaintRulePrecondition.Source> {
        val method = context.methodEntryPoint.method as JIRMethod

        val valueResolver = CalleePositionToJIRValueResolver(method)
        val conditionRewriter = JIRMarkAwareConditionRewriter(
            valueResolver,
            context.factTypeChecker
        )
        val conditionEvaluator = JIRSimpleFactAwareConditionEvaluator(conditionRewriter, evaluator = null)

        val entryFactReader = InitialFactReader(fact, apManager)
        val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(
            entryFactReader, context.factTypeChecker, returnValueType = null
        )

        val result = TaintConfigUtils.applyEntryPointConfig(
            context.taint.taintConfig as TaintRulesProvider,
            method, fact = null, conditionEvaluator, sourcePreconditionEvaluator
        )

        result.onSome { sourceActions ->
            return sourceActions.map {
                TaintRulePrecondition.Source(
                    it.first as CommonTaintConfigurationSource,
                    setOf(it.second)
                )
            }
        }

        return emptyList()
    }
}
