package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.jvm.ap.ifds.FactAwareConditionEvaluatorWithAssumptions.FactAssumption
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.util.JIRTraits
import java.util.concurrent.ConcurrentHashMap

class TaintSinkTracker(
    private val storage: TaintAnalysisUnitRunnerManager.UnitStorage
) {

    sealed interface TaintVulnerability {
        val rule: TaintMethodSink
        val statement: JIRInst
    }

    data class NormalVulnerability(
        override val rule: TaintMethodSink,
        override val statement: JIRInst,
        val fact: FinalFactAp
    ): TaintVulnerability

    data class UnconditionalVulnerability(
        override val rule: TaintMethodSink,
        override val statement: JIRInst
    ): TaintVulnerability

    private val reportedVulnerabilities = ConcurrentHashMap<TaintMethodSink, MutableSet<JIRInst>>()

    fun addNormalVulnerability(fact: FinalFactAp, statement: JIRInst, rule: TaintMethodSink) =
        addVulnerability(statement, rule) {
            NormalVulnerability(rule, statement, fact)
        }

    fun addUnconditionalVulnerability(statement: JIRInst, rule: TaintMethodSink) =
        addVulnerability(statement, rule) {
            UnconditionalVulnerability(rule, statement)
        }

    private inline fun addVulnerability(
        statement: JIRInst,
        rule: TaintMethodSink,
        vulnerabilityBuilder: () -> TaintVulnerability
    ) {
        val reportedVulnerabilitiesFoRule = reportedVulnerabilities.computeIfAbsent(rule) {
            ConcurrentHashMap.newKeySet()
        }

        // todo: current deduplication is incompatible with traces
        if (reportedVulnerabilitiesFoRule.add(statement)) {
            storage.addVulnerability(vulnerabilityBuilder())
        }
    }

    private val vulnerabilitiesWithAssumptions = ConcurrentHashMap<Pair<JIRInst, TaintMethodSink>, VulnWithAssumptions>()

    fun addVulnerabilityWithAssumption(
        factAp: FinalFactAp,
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
                vulnerabilities.add(EdgeWithAssumptions(factAp, statement, assumptions))
                if (vulnerabilities.vulnerabilityAssumptionsSatisfied()) {
                    vulnerabilitiesWithAssumptions[statement to rule] = BannedVulnerability

                    // Report vulnerability with last edge
                    addNormalVulnerability(factAp, statement, rule)
                }
            }
        }
    }

    data class EdgeWithAssumptions(val factAp: FinalFactAp, val statement: JIRInst, val assumptions: Set<FactAssumption>)

    private sealed interface VulnWithAssumptions
    private object BannedVulnerability : VulnWithAssumptions

    private class VulnerabilityWithAssumptions : VulnWithAssumptions {
        private val edges = mutableListOf<EdgeWithAssumptions>()

        fun add(edge: EdgeWithAssumptions) {
            edges.add(edge)
        }

        fun vulnerabilityAssumptionsSatisfied(): Boolean {
            val cp = edges.firstOrNull()?.statement?.method?.enclosingClass?.classpath ?: return false

            val factReaders = edges.mapTo(hashSetOf()) { it.factAp }.map(::FactReader)

            val evaluator = FactAwareConditionEvaluator(
                JIRTraits(cp), factReaders, NonePositionResolver, NonePositionResolver
            )
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
