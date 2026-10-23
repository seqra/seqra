package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.taint.MarkUnfoldDemand

interface MethodAnalysisContext {
    val methodEntryPoint: MethodEntryPoint

    /**
     * Accessors already demanded by answers to a `TaintMarkFieldUnfoldRequest` handled while
     * analysing this method. One per analysed method, so a question is only ever conflated with
     * another asked in the same frame.
     */
    val markUnfoldDemand: MarkUnfoldDemand

    // todo: remove, required for trace generation
    val methodCallFactMapper: MethodCallFactMapper
}
