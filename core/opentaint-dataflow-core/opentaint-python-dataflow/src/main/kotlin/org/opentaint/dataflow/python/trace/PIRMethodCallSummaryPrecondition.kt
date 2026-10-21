package org.opentaint.dataflow.python.trace

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodCallSummaryPrecondition
import org.opentaint.dataflow.python.analysis.PIRMethodAnalysisContext
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRFunction

class PIRMethodCallSummaryPrecondition(
    private val callInst: PIRCall,
    private val ctx: PIRMethodAnalysisContext,
) : MethodCallSummaryPrecondition {
    override fun callSummaryPrecondition(fact: InitialFactAp, callee: MethodEntryPoint): List<InitialFactAp> {
        val factMapper = ctx.methodCallFactMapper
        val callSiteBase = factMapper.toCallerFrame(callInst, callee.method as PIRFunction, fact.base)
            ?: return emptyList()
        return factMapper.mapMethodExitToReturnFlowFact(callInst, fact.rebase(callSiteBase))
    }
}
