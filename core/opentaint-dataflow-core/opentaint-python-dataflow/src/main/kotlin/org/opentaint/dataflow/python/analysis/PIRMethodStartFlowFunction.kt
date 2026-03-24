package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact

class PIRMethodStartFlowFunction(
    private val ctx: PIRMethodAnalysisContext,
    private val apManager: ApManager,
) : MethodStartFlowFunction {

    override fun propagateZero(): List<StartFact> =
        listOf(StartFact.Zero)

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> =
        listOf(StartFact.Fact(fact))
}
