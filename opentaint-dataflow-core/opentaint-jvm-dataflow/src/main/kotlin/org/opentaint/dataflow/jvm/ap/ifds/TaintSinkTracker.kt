package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.jvm.ap.ifds.FactAwareConditionEvaluatorWithAssumptions.FactAssumption
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager
import org.opentaint.dataflow.jvm.util.JIRTraits
import java.util.concurrent.ConcurrentHashMap

class TaintSinkTracker(
    private val apManager: ApManager,
    private val storage: TaintAnalysisUnitRunnerManager.UnitStorage
) {
    sealed interface TaintVulnerability {
        val rule: TaintMethodSink
        val methodEntryPoint: MethodEntryPoint
        val statement: JIRInst
    }

    data class TaintVulnerabilityUnconditional(
        override val rule: TaintMethodSink,
        override val methodEntryPoint: MethodEntryPoint,
        override val statement: JIRInst
    ) : TaintVulnerability

    data class TaintVulnerabilityWithFact(
        override val rule: TaintMethodSink,
        override val methodEntryPoint: MethodEntryPoint,
        override val statement: JIRInst,
        val factAp: InitialFactAp
    ): TaintVulnerability

    private val unconditionalVulnerabilities = ConcurrentHashMap.newKeySet<TaintVulnerabilityUnconditional>()

    fun addUnconditionalVulnerability(
        methodEntryPoint: MethodEntryPoint,
        statement: JIRInst,
        rule: TaintMethodSink
    ) {
        val vulnerability = TaintVulnerabilityUnconditional(rule, methodEntryPoint, statement)
        if (unconditionalVulnerabilities.add(vulnerability)) {
            storage.addVulnerability(vulnerability)
        }
    }

    private val reportedVulnerabilities = ConcurrentHashMap<TaintMethodSink, MutableSet<JIRInst>>()

    fun addVulnerability(
        methodEntryPoint: MethodEntryPoint,
        factAp: InitialFactAp,
        statement: JIRInst,
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

    private val vulnerabilitiesWithAssumptions = ConcurrentHashMap<Pair<JIRInst, TaintMethodSink>, VulnWithAssumptions>()

    fun addVulnerabilityWithAssumption(
        methodEntryPoint: MethodEntryPoint,
        factAp: InitialFactAp,
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
                if (vulnerabilities.vulnerabilityAssumptionsSatisfied(apManager)) {
                    vulnerabilitiesWithAssumptions[statement to rule] = BannedVulnerability

                    // Report vulnerability with last edge
                    addVulnerability(methodEntryPoint, factAp, statement, rule)
                }
            }
        }
    }

    data class EdgeWithAssumptions(val factAp: InitialFactAp, val statement: JIRInst, val assumptions: Set<FactAssumption>)

    private sealed interface VulnWithAssumptions
    private object BannedVulnerability : VulnWithAssumptions

    private class VulnerabilityWithAssumptions : VulnWithAssumptions {
        private val edges = mutableListOf<EdgeWithAssumptions>()

        fun add(edge: EdgeWithAssumptions) {
            edges.add(edge)
        }

        fun vulnerabilityAssumptionsSatisfied(apManager: ApManager): Boolean {
            val cp = edges.firstOrNull()?.statement?.method?.enclosingClass?.classpath ?: return false

            val factReaders = edges.mapTo(hashSetOf()) { it.factAp }.map { fact ->
                InitialFactReader(fact, apManager)
            }

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
