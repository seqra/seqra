package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.taint.MethodSideEffectHandlerWithAnyAccessorRequestHandling

class JIRMethodSideEffectHandler(
    override val runner: AnalysisRunner,
    override val analysisContext: MethodAnalysisContext
) : MethodSideEffectHandlerWithAnyAccessorRequestHandling
