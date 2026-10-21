package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.taint.MethodSideEffectHandlerWithAnyAccessorRequestHandling

class PIRMethodSideEffectSummaryHandler(
    override val runner: AnalysisRunner
) : MethodSideEffectHandlerWithAnyAccessorRequestHandling
