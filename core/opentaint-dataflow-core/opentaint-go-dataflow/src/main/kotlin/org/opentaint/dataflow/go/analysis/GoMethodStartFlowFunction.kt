package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact

/**
 * Entry-point flow function. Processes facts at method entry.
 */
class GoMethodStartFlowFunction(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
) : MethodStartFlowFunction {

    override fun propagateZero(): List<StartFact> {
        val result = mutableListOf<StartFact>(StartFact.Zero)
        // MVP: no entry-point rules — all test cases use source() function calls
        return result
    }

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> {
        // MVP: no type checking — accept all initial facts
        return listOf(StartFact.Fact(fact))
    }
}
