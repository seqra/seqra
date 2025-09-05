package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.ApManager
import java.util.concurrent.ConcurrentLinkedQueue

class TaintAnalysisUnitStorage(apManager: ApManager) : MethodSummariesUnitStorage(apManager) {
    private val vulnerabilities = ConcurrentLinkedQueue<TaintSinkTracker.TaintVulnerability>()

    fun addVulnerability(vulnerability: TaintSinkTracker.TaintVulnerability) {
        vulnerabilities.add(vulnerability)
    }

    fun collectVulnerabilities(collector: MutableList<TaintSinkTracker.TaintVulnerability>) {
        collector.addAll(vulnerabilities)
    }
}
