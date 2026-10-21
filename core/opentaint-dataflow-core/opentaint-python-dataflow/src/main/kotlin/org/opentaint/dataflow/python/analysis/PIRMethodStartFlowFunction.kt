package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.resolveAp
import org.opentaint.dataflow.taint.TaintSourceActionEvaluator
import org.opentaint.util.onSome

class PIRMethodStartFlowFunction(
    private val ctx: PIRMethodAnalysisContext,
    private val apManager: ApManager,
) : MethodStartFlowFunction {
    private val rulesProvider get() = ctx.taint.taintConfig

    override fun propagateZero(): List<StartFact> = buildList {
        if (ctx.phase is TaintAnalysisManager.Phase.Prescan) return emptyList()

        this += StartFact.Zero

        val rules = rulesProvider.entryPointSourcesForMethod(ctx.method)
        val evaluator = TaintSourceActionEvaluator(apManager, ExclusionSet.Universe)

        rules.forEach { rule ->
            check(rule.condition.isTrue()) { "Unexpected entry point rule condition: ${rule.condition}" }

            rule.taint.forEach { action ->
                val pos = action.pos.resolveAp() ?: return@forEach
                val mark = TaintMarkAccessor(action.mark.name)

                evaluator.evaluate(rule, action, pos, mark).onSome { facts ->
                    facts.forEach { fact ->
                        this += StartFact.Fact(fact)
                    }
                }
            }
        }
    }

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> =
        listOf(StartFact.Fact(fact))
}
