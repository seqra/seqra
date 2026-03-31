package org.opentaint.dataflow.ap.ifds.taint

import org.opentaint.dataflow.configuration.CommonTaintRulesProvider

data class TaintAnalysisContext(
    val taintConfig: CommonTaintRulesProvider,
    val taintSinkTracker: TaintSinkTracker,
    val externalMethodTracker: ExternalMethodTracker? = null,
)
