package org.opentaint.dataflow.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodSummariesUnitStorage
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentHashMap

class TaintAnalysisUnitStorage(apManager: ApManager, languageManager: LanguageManager)
    : MethodSummariesUnitStorage(apManager, languageManager)
{
    private data class VulnerabilityIdentity(
        val ruleId: String,
        val statement: CommonInst,
    )

    private val vulnerabilityBuckets = ConcurrentHashMap<VulnerabilityIdentity, TaintSinkTracker.TaintVulnerability>()

    fun addVulnerability(vulnerability: TaintSinkTracker.TaintVulnerability) {
        val identity = VulnerabilityIdentity(vulnerability.ruleId, vulnerability.statement)
        val bucket = vulnerabilityBuckets.computeIfAbsent(identity) { vulnerability }

        synchronized(bucket) {
            bucket.mergeAdd(vulnerability)
        }
    }

    fun collectVulnerabilities(collector: MutableList<TaintSinkTracker.TaintVulnerability>) {
        vulnerabilityBuckets.values.forEach {
            it.minimize()
            collector.add(it)
        }
    }
}
