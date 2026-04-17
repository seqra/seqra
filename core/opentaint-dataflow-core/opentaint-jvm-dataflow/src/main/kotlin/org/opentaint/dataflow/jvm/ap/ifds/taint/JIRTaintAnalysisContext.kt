package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.configuration.CommonTaintRulesProvider

class JIRTaintAnalysisContext(
    override val taintSinkTracker: TaintSinkTracker,
    val taintConfig: CommonTaintRulesProvider,
    val externalMethodTracker: ExternalMethodTracker? = null,
): TaintAnalysisContext
