package org.opentaint.dataflow.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodSummariesUnitStorage
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class TaintAnalysisUnitStorage(apManager: ApManager, languageManager: LanguageManager)
    : MethodSummariesUnitStorage(apManager, languageManager)
{
    private data class VulnerabilityIdentity(
        val ruleId: String,
        val statement: CommonInst,
    )

    private class IdentityBucket {
        private val vulnerabilities = ConcurrentLinkedQueue<TaintSinkTracker.TaintVulnerability>()

        fun add(vulnerability: TaintSinkTracker.TaintVulnerability) {
            vulnerabilities.add(vulnerability)
        }

        private fun factSize(v: TaintSinkTracker.TaintVulnerability): Int = when (v) {
            is TaintSinkTracker.TaintVulnerabilityWithFact -> v.factAp.sumOf { it.size }
            is TaintSinkTracker.TaintVulnerabilityWithEndFactRequirement ->
                factSize(v.vulnerability) + v.endFactRequirement.sumOf { it.size }
            else -> 0
        }

        fun collect(collector: MutableList<TaintSinkTracker.TaintVulnerability>) {
            vulnerabilities.minByOrNull { factSize(it) }?.let(collector::add)
        }
    }

    private val vulnerabilityBuckets = ConcurrentHashMap<VulnerabilityIdentity, IdentityBucket>()

    fun addVulnerability(vulnerability: TaintSinkTracker.TaintVulnerability) {
        val identity = VulnerabilityIdentity(vulnerability.rule.id, vulnerability.statement)
        vulnerabilityBuckets.computeIfAbsent(identity) { IdentityBucket() }.add(vulnerability)
    }

    fun collectVulnerabilities(collector: MutableList<TaintSinkTracker.TaintVulnerability>) {
        vulnerabilityBuckets.values.forEach { it.collect(collector) }
    }
}
