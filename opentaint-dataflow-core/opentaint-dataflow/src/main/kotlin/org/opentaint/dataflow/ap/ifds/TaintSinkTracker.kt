package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.ApManager
import java.util.concurrent.ConcurrentHashMap

class TaintSinkTracker(
    private val apManager: ApManager,
    private val storage: TaintAnalysisUnitRunnerManager.UnitStorage,
) {
    sealed interface TaintVulnerability {
        val rule: TaintMethodSink
        val methodEntryPoint: MethodEntryPoint
        val statement: CommonInst
    }

    data class TaintVulnerabilityUnconditional(
        override val rule: TaintMethodSink,
        override val methodEntryPoint: MethodEntryPoint,
        override val statement: CommonInst
    ) : TaintVulnerability

    data class TaintVulnerabilityWithFact(
        override val rule: TaintMethodSink,
        override val methodEntryPoint: MethodEntryPoint,
        override val statement: CommonInst,
        val factAp: InitialFactAp
    ): TaintVulnerability

    private val unconditionalVulnerabilities = ConcurrentHashMap.newKeySet<TaintVulnerabilityUnconditional>()

    fun addUnconditionalVulnerability(
        methodEntryPoint: MethodEntryPoint,
        statement: CommonInst,
        rule: TaintMethodSink
    ) {
        val vulnerability = TaintVulnerabilityUnconditional(rule, methodEntryPoint, statement)
        if (unconditionalVulnerabilities.add(vulnerability)) {
            storage.addVulnerability(vulnerability)
        }
    }

    private val reportedVulnerabilities = ConcurrentHashMap<TaintMethodSink, MutableSet<CommonInst>>()

    fun addVulnerability(
        methodEntryPoint: MethodEntryPoint,
        factAp: InitialFactAp,
        statement: CommonInst,
        rule: TaintMethodSink
    ) {
        val reportedVulnerabilitiesFoRule = reportedVulnerabilities.computeIfAbsent(rule) {
            ConcurrentHashMap.newKeySet()
        }

        // todo: current deduplication is incompatible with traces
        if (reportedVulnerabilitiesFoRule.add(statement)) {
            storage.addVulnerability(TaintVulnerabilityWithFact(rule, methodEntryPoint, statement, factAp))
        }
    }

    private val vulnerabilitiesWithAssumptions = ConcurrentHashMap<Pair<CommonInst, TaintMethodSink>, VulnWithAssumptions>()

    fun addVulnerabilityWithAssumption(
        methodEntryPoint: MethodEntryPoint,
        factAp: InitialFactAp,
        statement: CommonInst,
        rule: TaintMethodSink,
        assumptions: Set<FactAssumption>
    ) {
        val vulnerabilities = vulnerabilitiesWithAssumptions.computeIfAbsent(statement to rule) {
            VulnerabilityWithAssumptions()
        }

        when (vulnerabilities) {
            BannedVulnerability -> return
            is VulnerabilityWithAssumptions -> {
                vulnerabilities.add(EdgeWithAssumptions(factAp, statement, assumptions))
                if (vulnerabilities.vulnerabilityAssumptionsSatisfied(apManager)) {
                    vulnerabilitiesWithAssumptions[statement to rule] = BannedVulnerability

                    // Report vulnerability with last edge
                    addVulnerability(methodEntryPoint, factAp, statement, rule)
                }
            }
        }
    }

    data class FactAssumption(val mark: TaintMark, val position: PositionAccess)
    data class EdgeWithAssumptions(val factAp: InitialFactAp, val statement: CommonInst, val assumptions: Set<FactAssumption>)

    private sealed interface VulnWithAssumptions
    private object BannedVulnerability : VulnWithAssumptions

    private class VulnerabilityWithAssumptions : VulnWithAssumptions {
        private val edges = mutableListOf<EdgeWithAssumptions>()

        fun add(edge: EdgeWithAssumptions) {
            edges.add(edge)
        }

        fun vulnerabilityAssumptionsSatisfied(apManager: ApManager): Boolean {
            val factReaders = edges.mapTo(hashSetOf()) { it.factAp }.map { fact ->
                InitialFactReader(fact, apManager)
            }

            for (edge in edges) {
                val satisfied = edge.assumptions.all { assumption ->
                    factReaders.any { fact -> fact.containsPositionWithTaintMark(assumption.position, assumption.mark) }
                }

                if (satisfied) return true
            }
            return false
        }
    }
}
