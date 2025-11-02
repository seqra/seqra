package org.opentaint.dataflow.ap.ifds.taint

data class TaintAnalysisContext(
    val taintConfig: TaintRulesProvider,
    val taintSinkTracker: TaintSinkTracker,
)
