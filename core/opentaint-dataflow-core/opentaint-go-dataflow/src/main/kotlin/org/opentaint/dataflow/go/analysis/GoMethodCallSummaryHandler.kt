package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.go.GoMethodCallFactMapper
import org.opentaint.ir.go.inst.GoIRInst

/**
 * Applies callee summaries back to the caller.
 * Mostly delegates to default interface implementations.
 */
class GoMethodCallSummaryHandler(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val statement: GoIRInst,
) : MethodCallSummaryHandler {

    override val factTypeChecker: FactTypeChecker = FactTypeChecker.Dummy

    override fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp> {
        return GoMethodCallFactMapper.mapMethodExitToReturnFlowFact(
            statement, fact, factTypeChecker
        )
    }
}
