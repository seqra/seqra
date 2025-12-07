package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint

interface MethodAnalysisContext {
    val methodEntryPoint: MethodEntryPoint

    // todo: remove, required for trace generation
    val methodCallFactMapper: org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
}
