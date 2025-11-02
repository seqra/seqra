package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper

class JIRMethodCallSummaryHandler(
    private val statement: JIRInst,
    override val factTypeChecker: FactTypeChecker
) : MethodCallSummaryHandler {
    override fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp> =
        JIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, fact, factTypeChecker)
}
