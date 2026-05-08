package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.ir.api.python.PIRFunction

class PIRMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val method: PIRFunction,
    val taint: TaintAnalysisContext,
    val callResolver: PIRCallResolver,
) : MethodAnalysisContext {

    override val methodCallFactMapper: MethodCallFactMapper
        get() = PIRMethodCallFactMapper(this, callResolver)
}
