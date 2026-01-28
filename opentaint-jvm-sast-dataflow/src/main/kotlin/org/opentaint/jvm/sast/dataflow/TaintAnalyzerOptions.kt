package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.ApMode
import kotlin.time.Duration

data class TaintAnalyzerOptions(
    val ifdsTimeout: Duration,
    val ifdsApMode: ApMode,
    val symbolicExecutionEnabled: Boolean,
    val analysisCwe: Set<Int>?,
    val storeSummaries: Boolean,
    val debugOptions: DebugOptions?
)
