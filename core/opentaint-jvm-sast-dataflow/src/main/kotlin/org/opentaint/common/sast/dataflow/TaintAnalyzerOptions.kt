package org.opentaint.common.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.ApMode
import kotlin.time.Duration

data class TaintAnalyzerOptions(
    val ifdsTimeout: Duration,
    val ifdsApMode: ApMode,
    val symbolicExecutionEnabled: Boolean = false,
    val analysisCwe: Set<Int>? = null,
    val storeSummaries: Boolean = false,
    val experimentalAAInterProcCallDepth: Int = 0,
    val tracePathLimit: Int? = null,
    val fieldLimit: Int? = null,
    val debugOptions: DebugOptions? = null,
)
