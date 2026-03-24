package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact
import org.opentaint.dataflow.python.rules.PIRTaintConfig

class PIRMethodStartFlowFunction(
    private val ctx: PIRMethodAnalysisContext,
    private val apManager: ApManager,
) : MethodStartFlowFunction {

    override fun propagateZero(): List<StartFact> {
        val results = mutableListOf<StartFact>(StartFact.Zero)

        // Entry sources: inject initial taint on parameters of the entry point method.
        // This is used for benchmarks where the source is the function's parameter.
        val config = ctx.taint.taintConfig
        if (config is PIRTaintConfig) {
            val methodName = ctx.method.qualifiedName
            for (entrySource in config.entrySources) {
                if (methodName == entrySource.function || methodName.endsWith(".${entrySource.function}")) {
                    val base = AccessPathBase.Argument(entrySource.paramIndex)
                    val fact = apManager.createAbstractAp(base, ExclusionSet.Universe)
                        .prependAccessor(TaintMarkAccessor(entrySource.mark))
                    results.add(StartFact.Fact(fact))
                }
            }
        }

        return results
    }

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> =
        listOf(StartFact.Fact(fact))
}
