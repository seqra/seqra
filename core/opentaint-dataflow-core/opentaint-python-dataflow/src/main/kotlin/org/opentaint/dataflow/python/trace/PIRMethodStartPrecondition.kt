package org.opentaint.dataflow.python.trace

import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.resolveAp
import org.opentaint.dataflow.python.analysis.PIRMethodAnalysisContext
import org.opentaint.dataflow.taint.InitialFactReader
import org.opentaint.dataflow.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.util.Maybe
import org.opentaint.util.maybeFlatMap
import org.opentaint.util.onSome

class PIRMethodStartPrecondition(
    private val apManager: ApManager,
    private val ctx: PIRMethodAnalysisContext,
) : MethodStartPrecondition {
    override fun factPrecondition(fact: InitialFactAp): List<TaintRulePrecondition.Source> = buildList {
        val rules = ctx.taint.taintConfig.entryPointSourcesForMethod(ctx.method)
        if (rules.isEmpty()) return emptyList()

        val evaluator = TaintSourceActionPreconditionEvaluator(InitialFactReader(fact, apManager))

        for (rule in rules) {
            check(rule.condition.isTrue()) { "Unexpected entry point rule condition: ${rule.condition}" }

            val assignedMarks = rule.taint.maybeFlatMap { action ->
                val pos = action.pos.resolveAp() ?: return@maybeFlatMap Maybe.none()
                evaluator.evaluate(rule, action, pos, TaintMarkAccessor(action.mark.name))
            }

            assignedMarks.onSome { sourceActions ->
                this += sourceActions.map {
                    TaintRulePrecondition.Source(
                        it.first as CommonTaintConfigurationSource,
                        setOf(it.second)
                    )
                }
            }
        }
    }
}
