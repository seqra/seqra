package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.util.onSome
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionPreconditionEvaluator

class JIRMethodStartPrecondition(
    private val apManager: ApManager,
    private val context: JIRMethodAnalysisContext,
) : MethodStartPrecondition {
    override fun factPrecondition(fact: InitialFactAp): List<TaintRulePrecondition.Source> {
        val method = context.methodEntryPoint.method as JIRMethod

        val valueResolver = CalleePositionToJIRValueResolver(method)
        val conditionEvaluator = JIRFactAwareConditionEvaluator(facts = emptyList(), valueResolver, context.factTypeChecker)

        val entryFactReader = InitialFactReader(fact, apManager)
        val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(
            entryFactReader, context.factTypeChecker, returnValueType = null
        )

        val result = TaintConfigUtils.applyEntryPointConfig(
            context.taint.taintConfig as TaintRulesProvider,
            method, conditionEvaluator, sourcePreconditionEvaluator
        )

        result.onSome { sourceActions ->
            return sourceActions.map {
                TaintRulePrecondition.Source(
                    it.first as CommonTaintConfigurationSource,
                    it.second
                )
            }
        }

        return emptyList()
    }
}
