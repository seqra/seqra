package org.opentaint.dataflow.bidi.driver

import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import kotlin.time.Duration

interface BidiEntry {
    fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>)
    fun run(budget: Duration): Report
    fun toVulnerabilities(report: Report): List<VulnerabilityWithTrace>
    fun status(report: Report): TaintAnalysisUnitRunnerManager.Status
}
