package org.opentaint.dataflow.ap.ifds.taint

interface TaintAnalysisContext {
    val taintSinkTracker: TaintSinkTracker
}

class CommonTaintAnalysisContext(
    override val taintSinkTracker: TaintSinkTracker
) : TaintAnalysisContext

