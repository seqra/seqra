package org.opentaint.dataflow.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentHashMap

class TaintSinkTracker(
    private val storage: TaintAnalysisUnitStorage,
) {
    sealed interface TaintVulnerability {
        val rule: CommonTaintConfigurationSink
        val methodEntryPoint: MethodEntryPoint
        val statement: CommonInst
    }

    data class TaintVulnerabilityUnconditional(
        override val rule: CommonTaintConfigurationSink,
        override val methodEntryPoint: MethodEntryPoint,
        override val statement: CommonInst
    ) : TaintVulnerability

    enum class VulnerabilityTriggerPosition {
        BEFORE_INST, AFTER_INST
    }

    data class TaintVulnerabilityWithFact(
        override val rule: CommonTaintConfigurationSink,
        override val methodEntryPoint: MethodEntryPoint,
        override val statement: CommonInst,
        val factAp: Set<InitialFactAp>,
        val vulnerabilityTriggerPosition: VulnerabilityTriggerPosition,
    ): TaintVulnerability

    data class TaintVulnerabilityWithEndFactRequirement(
        val vulnerability: TaintVulnerability,
        val endFactRequirement: Set<FinalFactAp>,
    ) : TaintVulnerability by vulnerability

    fun addUnconditionalVulnerability(
        methodEntryPoint: MethodEntryPoint,
        statement: CommonInst,
        rule: CommonTaintConfigurationSink,
    ) {
        addVulnerability(TaintVulnerabilityUnconditional(rule, methodEntryPoint, statement))
    }

    fun addUnconditionalVulnerabilityWithEndFactRequirement(
        methodEntryPoint: MethodEntryPoint,
        statement: CommonInst,
        rule: CommonTaintConfigurationSink,
        requiredEndFacts: Set<FinalFactAp>,
    ) {
        addVulnerability(
            TaintVulnerabilityWithEndFactRequirement(
                TaintVulnerabilityUnconditional(rule, methodEntryPoint, statement),
                requiredEndFacts,
            )
        )
    }

    fun addVulnerability(
        methodEntryPoint: MethodEntryPoint,
        facts: Set<InitialFactAp>,
        statement: CommonInst,
        rule: CommonTaintConfigurationSink,
        vulnerabilityTriggerPosition: VulnerabilityTriggerPosition = VulnerabilityTriggerPosition.BEFORE_INST,
    ) {
        addVulnerability(
            TaintVulnerabilityWithFact(rule, methodEntryPoint, statement, facts, vulnerabilityTriggerPosition)
        )
    }

    fun addVulnerabilityWithEndFactRequirement(
        methodEntryPoint: MethodEntryPoint,
        facts: Set<InitialFactAp>,
        statement: CommonInst,
        rule: CommonTaintConfigurationSink,
        requiredEndFacts: Set<FinalFactAp>,
        vulnerabilityTriggerPosition: VulnerabilityTriggerPosition = VulnerabilityTriggerPosition.BEFORE_INST,
    ) {
        addVulnerability(
            TaintVulnerabilityWithEndFactRequirement(
                TaintVulnerabilityWithFact(rule, methodEntryPoint, statement, facts, vulnerabilityTriggerPosition),
                requiredEndFacts,
            )
        )
    }

    private val reportedVulnerabilities = ConcurrentHashMap<String, MutableSet<CommonInst>>()

    private fun addVulnerability(vulnerability: TaintVulnerability) {
        val reportedVulnerabilitiesFoRule = reportedVulnerabilities.computeIfAbsent(vulnerability.rule.id) {
            ConcurrentHashMap.newKeySet()
        }

        // todo: current deduplication is incompatible with traces
        if (!reportedVulnerabilitiesFoRule.add(vulnerability.statement)) return

        storage.addVulnerability(vulnerability)
    }

    data class FactWithPreconditions(val fact: InitialFactAp, val preconditions: List<Set<InitialFactAp>>)

    private data class TaintRuleAssumptions<T: CommonTaintConfigurationItem>(
        val rule: T,
        val statement: CommonInst,
        val facts: MutableMap<InitialFactAp, MutableSet<Set<InitialFactAp>>>,
        val delayedResolutions: MutableSet<Pair<InitialFactAp, TaintMarkAccessor>>,
    )

    private val sourceRuleAssumptions = ConcurrentHashMap<CommonTaintConfigurationSource, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<CommonTaintConfigurationSource>>>()
    private val sinkRuleAssumptions = ConcurrentHashMap<CommonTaintConfigurationSink, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<CommonTaintConfigurationSink>>>()

    fun addSourceRuleAssumptions(rule: CommonTaintConfigurationSource, statement: CommonInst, assumptions: Map<InitialFactAp, Set<InitialFactAp>>) =
        addRuleAssumptions(sourceRuleAssumptions, rule, statement, assumptions)

    fun addSinkRuleDelayedAnyResolution(rule: CommonTaintConfigurationSink, statement: CommonInst, factWithAny: InitialFactAp, tmAccessor: TaintMarkAccessor) =
        addRuleDelayedAnyResolutions(sinkRuleAssumptions, rule, statement, factWithAny, tmAccessor)

    fun removeSinkRuleDelayedAnyResolution(rule: CommonTaintConfigurationSink, statement: CommonInst, factWithAny: InitialFactAp, tmAccessor: TaintMarkAccessor) =
        removeRuleDelayedAnyResolutions(sinkRuleAssumptions, rule, statement, factWithAny, tmAccessor)

    fun getSinkRuleDelayedResolutions(rule: CommonTaintConfigurationSink, statement: CommonInst) =
        getDelayedResolutions(sinkRuleAssumptions, rule, statement)

    fun currentSourceRuleAssumptions(rule: CommonTaintConfigurationSource, statement: CommonInst) =
        currentRuleAssumptions(sourceRuleAssumptions, rule, statement)

    fun currentSourceRuleAssumptionsPreconditions(rule: CommonTaintConfigurationSource, statement: CommonInst, facts: List<InitialFactAp>) =
        currentRuleAssumptionsPreconditions(sourceRuleAssumptions, rule, statement, facts)

    fun addSinkRuleAssumptions(rule: CommonTaintConfigurationSink, statement: CommonInst, assumptions: Map<InitialFactAp, Set<InitialFactAp>>) =
        addRuleAssumptions(sinkRuleAssumptions, rule, statement, assumptions)

    fun currentSinkRuleAssumptions(rule: CommonTaintConfigurationSink, statement: CommonInst) =
        currentRuleAssumptions(sinkRuleAssumptions, rule, statement)

    private fun <T : CommonTaintConfigurationItem> getDelayedResolutions(
        storage: ConcurrentHashMap<T, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<T>>>,
        rule: T, statement: CommonInst
    ): Set<Pair<InitialFactAp, TaintMarkAccessor>> {
        val currentAssumptions = storage[rule]?.get(statement) ?: return emptySet()
        return currentAssumptions.delayedResolutions.toSet()
    }

    private fun <T : CommonTaintConfigurationItem> addRuleAssumptions(
        storage: ConcurrentHashMap<T, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<T>>>,
        rule: T, statement: CommonInst, assumptions: Map<InitialFactAp, Set<InitialFactAp>>
    ) {
        val currentAssumptions = storage
            .computeIfAbsent(rule) { ConcurrentHashMap() }
            .computeIfAbsent(statement) { TaintRuleAssumptions(rule, statement, hashMapOf(), mutableSetOf()) }

        synchronized(currentAssumptions) {
            assumptions.forEach { (fact, factPre) ->
                currentAssumptions.facts.getOrPut(fact, ::hashSetOf).add(factPre)
            }
        }
    }

    private fun <T : CommonTaintConfigurationItem> addRuleDelayedAnyResolutions(
        storage: ConcurrentHashMap<T, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<T>>>,
        rule: T, statement: CommonInst, factWithAny: InitialFactAp, tmAccessor: TaintMarkAccessor
    ) {
        val currentAssumptions = storage
            .computeIfAbsent(rule) { ConcurrentHashMap() }
            .computeIfAbsent(statement) { TaintRuleAssumptions(rule, statement, hashMapOf(), mutableSetOf()) }

        synchronized(currentAssumptions) {
            currentAssumptions.delayedResolutions.add(factWithAny to tmAccessor)
        }
    }

    private fun <T : CommonTaintConfigurationItem> removeRuleDelayedAnyResolutions(
        storage: ConcurrentHashMap<T, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<T>>>,
        rule: T, statement: CommonInst, factWithAny: InitialFactAp, tmAccessor: TaintMarkAccessor
    ) {
        val currentAssumptions = storage
            .computeIfAbsent(rule) { ConcurrentHashMap() }
            .computeIfAbsent(statement) { TaintRuleAssumptions(rule, statement, hashMapOf(), mutableSetOf()) }

        synchronized(currentAssumptions) {
            currentAssumptions.delayedResolutions.remove(factWithAny to tmAccessor)
        }
    }

    private fun <T : CommonTaintConfigurationItem> currentRuleAssumptions(
        storage: ConcurrentHashMap<T, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<T>>>,
        rule: T, statement: CommonInst
    ): Set<InitialFactAp> {
        val currentAssumptions = storage[rule]?.get(statement) ?: return emptySet()
        synchronized(currentAssumptions) {
            return currentAssumptions.facts.keys.toSet()
        }
    }

    private fun <T : CommonTaintConfigurationItem> currentRuleAssumptionsPreconditions(
        storage: ConcurrentHashMap<T, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<T>>>,
        rule: T, statement: CommonInst,
        facts: List<InitialFactAp>
    ): List<FactWithPreconditions> {
        val currentAssumptions = storage[rule]?.get(statement) ?: return emptyList()
        synchronized(currentAssumptions) {
            return facts.map {
                val preconditions = currentAssumptions.facts[it]
                    ?: error("Missed precondition")
                FactWithPreconditions(it, preconditions.toList())
            }
        }
    }
}
