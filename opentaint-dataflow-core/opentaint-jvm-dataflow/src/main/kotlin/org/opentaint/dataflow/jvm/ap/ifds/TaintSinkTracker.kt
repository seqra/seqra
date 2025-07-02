package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.jvm.ap.ifds.FactAwareConditionEvaluatorWithAssumptions.FactAssumption
import java.util.concurrent.ConcurrentHashMap

class TaintSinkTracker(
    private val storage: TaintAnalysisUnitRunnerManager.UnitStorage
) {
    data class TaintVulnerability(val rule: TaintMethodSink, val fact: Fact, val statement: JIRInst)

    private val reportedVulnerabilities = ConcurrentHashMap<TaintMethodSink, MutableSet<JIRInst>>()

    fun addVulnerability(fact: Fact, statement: JIRInst, rule: TaintMethodSink) {
        val reportedVulnerabilitiesFoRule = reportedVulnerabilities.computeIfAbsent(rule) {
            ConcurrentHashMap.newKeySet()
        }

        // todo: current deduplication is incompatible with traces
        if (reportedVulnerabilitiesFoRule.add(statement)) {
            storage.addVulnerability(TaintVulnerability(rule, fact, statement))
        }
    }

    private val vulnerabilitiesWithAssumptions = ConcurrentHashMap<Pair<JIRInst, TaintMethodSink>, VulnWithAssumptions>()

    fun addVulnerabilityWithAssumption(
        fact: Fact,
        statement: JIRInst,
        rule: TaintMethodSink,
        assumptions: Set<FactAssumption>
    ) {
        val vulnerabilities = vulnerabilitiesWithAssumptions.computeIfAbsent(statement to rule) {
            VulnerabilityWithAssumptions()
        }

        when (vulnerabilities) {
            BannedVulnerability -> return
            is VulnerabilityWithAssumptions -> {
                vulnerabilities.add(EdgeWithAssumptions(fact, statement, assumptions))
                if (vulnerabilities.vulnerabilityAssumptionsSatisfied()) {
                    vulnerabilitiesWithAssumptions[statement to rule] = BannedVulnerability

                    // Report vulnerability with last edge
                    addVulnerability(fact, statement, rule)
                }
            }
        }
    }

    data class EdgeWithAssumptions(val fact: Fact, val statement: JIRInst, val assumptions: Set<FactAssumption>)

    private sealed interface VulnWithAssumptions
    private object BannedVulnerability : VulnWithAssumptions

    private class VulnerabilityWithAssumptions : VulnWithAssumptions {
        private val edges = mutableListOf<EdgeWithAssumptions>()

        fun add(edge: EdgeWithAssumptions) {
            edges.add(edge)
        }

        fun vulnerabilityAssumptionsSatisfied(): Boolean {
            val factReaders = edges.mapTo(hashSetOf()) { it.fact }.map { fact ->
                when (fact) {
                    is Fact.TaintedTree -> FactReader(fact)
                    else -> error("Unexpected fact: $fact")
                }
            }

            val evaluator = FactAwareConditionEvaluator(factReaders, NonePositionResolver, NonePositionResolver)
            for (edge in edges) {
                val satisfied = edge.assumptions.all { assumption ->
                    factReaders.any { fact -> evaluator.evalContainsMark(fact, assumption.mark, assumption.position) }
                }

                if (satisfied) return true
            }
            return false
        }
    }

    private object NonePositionResolver : PositionResolver<Maybe<Nothing>> {
        override fun resolve(position: Position): Maybe<Nothing> = Maybe.none()
    }
}
