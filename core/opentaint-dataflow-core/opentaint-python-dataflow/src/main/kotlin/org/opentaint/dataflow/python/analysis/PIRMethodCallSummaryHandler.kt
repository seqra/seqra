package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.ir.api.python.PIRCall

class PIRMethodCallSummaryHandler(
    private val callInst: PIRCall,
    private val ctx: PIRMethodAnalysisContext,
    override val factTypeChecker: FactTypeChecker,
) : MethodCallSummaryHandler {

    private val factMapper get() = ctx.methodCallFactMapper as PIRMethodCallFactMapper

    override fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp> =
        factMapper.mapMethodExitToReturnFlowFact(callInst, fact, factTypeChecker)
}
