package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.taint.MethodSideEffectHandlerWithAnyAccessorRequestHandling

class GoMethodSideEffectHandler(
    override val runner: AnalysisRunner,
    override val analysisContext: MethodAnalysisContext
) : MethodSideEffectHandlerWithAnyAccessorRequestHandling
