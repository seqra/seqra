package org.opentaint.dataflow.ap.ifds.taint

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.TaintVulnerabilityRuleNode.Unconditional
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.TaintVulnerabilityRuleNode.WithRequirement
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentHashMap

class TaintSinkTracker(
    private val storage: TaintAnalysisUnitStorage,
) {
    data class TaintVulnerability(
        val statement: CommonInst,
        val ruleId: String,
        val vulnerabilityRules: MutableMap<CommonTaintConfigurationSink, TaintVulnerabilityRuleNode>,
    ) {
        val rule: CommonTaintConfigurationSink
            get() = vulnerabilityRules.keys.firstOrNull()
                    ?: error("Incorrect vulnerability without rules")

        fun mergeAdd(other: TaintVulnerability) {
            check(other.ruleId == ruleId && other.statement == statement) {
                "Unable to merge different vulnerabilities"
            }

            vulnerabilityRules.addAll(other.vulnerabilityRules, TaintVulnerabilityRuleNode::merge)
        }

        fun minimize() {
            vulnerabilityRules.values.forEach { it.minimize(ruleId) }
        }
    }

    sealed interface TaintVulnerabilityRuleNode {
        fun merge(other: TaintVulnerabilityRuleNode): TaintVulnerabilityRuleNode

        fun minimize(ruleId: String)

        data class Unconditional(val methodEntryPoint: MethodEntryPoint) : TaintVulnerabilityRuleNode {
            override fun merge(other: TaintVulnerabilityRuleNode) = this
            override fun minimize(ruleId: String) = Unit
        }

        data class Fact(
            val vulnerabilityTriggerPosition: VulnerabilityTriggerPosition,
            val facts: MutableMap<MethodEntryPoint, VulnerabilityFactGroups>,
        ) : TaintVulnerabilityRuleNode {
            override fun merge(other: TaintVulnerabilityRuleNode): TaintVulnerabilityRuleNode {
                val otherFact: Fact = when (other) {
                    is Unconditional -> {
                        return other
                    }

                    is WithRequirement -> {
                        logger.debug { "Vulnerability with requirement ignored: replaced with fact" }
                        return this
                    }

                    is Fact -> other
                }

                if (otherFact.vulnerabilityTriggerPosition != vulnerabilityTriggerPosition) {
                    logger.debug { "Vulnerability with fact ignored: position mismatch" }
                    return this
                }

                facts.addAll(otherFact.facts, VulnerabilityFactGroups::merge)
                return this
            }

            override fun minimize(ruleId: String) {
                facts.values.forEach { it.minimize(ruleId) }
            }
        }

        data class WithRequirement(
            val requirement: MutableMap<EndFactRequirement, TaintVulnerabilityRuleNode>
        ) : TaintVulnerabilityRuleNode {
            override fun merge(other: TaintVulnerabilityRuleNode): TaintVulnerabilityRuleNode {
                val otherWithReq: WithRequirement = when (other) {
                    is Unconditional -> return other
                    is Fact -> {
                        logger.debug { "Vulnerability with requirement ignored: replaced with fact" }
                        return other
                    }

                    is WithRequirement -> other
                }

                requirement.addAll(otherWithReq.requirement, TaintVulnerabilityRuleNode::merge)
                return this
            }

            override fun minimize(ruleId: String) {
                val minimalKeys = requirement.keys
                    .sortedWith(compareBy(finalFactSetComparator) { it.endFactRequirement })
                    .take(REQ_LIMIT)

                if (minimalKeys.size != requirement.size) {
                    logger.debug {
                        "Vulnerability minimize $ruleId: drop facts with req ${requirement.size - minimalKeys.size}"
                    }
                    val keysToRemove = requirement.keys - minimalKeys.toSet()
                    keysToRemove.forEach { requirement.remove(it) }
                }

                requirement.values.forEach { it.minimize(ruleId) }
            }

            companion object {
                private const val REQ_LIMIT = 3

                private val finalFactSetComparator = compareBy<Set<FinalFactAp>> { fs -> fs.sumOf { it.size } }
                    .thenComparing { fs -> fs.map { it.toString() }.sorted().joinToString() }
            }
        }
    }

    data class EndFactRequirement(
        val methodEntryPoint: MethodEntryPoint,
        val endFactRequirement: Set<FinalFactAp>
    )

    data class VulnerabilityFacts(val facts: Set<InitialFactAp>) {
        val size: Int get() = facts.sumOf { it.size }
    }

    data class VulnerabilityFactGroups(val facts: MutableSet<VulnerabilityFacts>) {
        fun merge(other: VulnerabilityFactGroups): VulnerabilityFactGroups {
            facts.addAll(other.facts)
            return this
        }

        fun minimize(ruleId: String) {
            val orderedFacts = facts.sortedBy { it.size }
            val minFactGroupSize = orderedFacts.firstOrNull()?.size
                ?: return

            val lastFactWithAllowedSize = orderedFacts.indexOfLast { it.size <= minFactGroupSize }
            val firstIndexToDrop = (lastFactWithAllowedSize + FACTS_OVER_LIMIT).coerceIn(0, orderedFacts.size)
            val factsToDrop = orderedFacts.subList(firstIndexToDrop, orderedFacts.size)

            val originalSize = facts.size
            facts.removeAll(factsToDrop.toSet())
            if (facts.size < originalSize) {
                logger.debug { "Vulnerability minimize $ruleId: drop facts ${originalSize - facts.size}" }
            }
        }

        companion object {
            private const val FACTS_OVER_LIMIT = 3
        }
    }

    enum class VulnerabilityTriggerPosition {
        BEFORE_INST, AFTER_INST
    }

    fun addUnconditionalVulnerability(
        methodEntryPoint: MethodEntryPoint,
        statement: CommonInst,
        rule: CommonTaintConfigurationSink,
    ) {
        val vuln = TaintVulnerability(statement, rule.id, hashMapOf(rule to Unconditional(methodEntryPoint)))
        addVulnerability(vuln)
    }

    fun addUnconditionalVulnerabilityWithEndFactRequirement(
        methodEntryPoint: MethodEntryPoint,
        statement: CommonInst,
        rule: CommonTaintConfigurationSink,
        requiredEndFacts: Set<FinalFactAp>,
    ) {
        val reqNode = WithRequirement(hashMapOf(EndFactRequirement(methodEntryPoint, requiredEndFacts) to Unconditional(methodEntryPoint)))
        val vuln = TaintVulnerability(statement, rule.id, hashMapOf(rule to reqNode))
        addVulnerability(vuln)
    }

    fun addVulnerability(
        methodEntryPoint: MethodEntryPoint,
        facts: Set<InitialFactAp>,
        statement: CommonInst,
        rule: CommonTaintConfigurationSink,
        vulnerabilityTriggerPosition: VulnerabilityTriggerPosition = VulnerabilityTriggerPosition.BEFORE_INST,
    ) {
        val factNode = createFactNode(vulnerabilityTriggerPosition, methodEntryPoint, facts)
        val vuln = TaintVulnerability(statement, rule.id, hashMapOf(rule to factNode))
        addVulnerability(vuln)
    }

    private fun createFactNode(
        vulnerabilityTriggerPosition: VulnerabilityTriggerPosition,
        methodEntryPoint: MethodEntryPoint,
        facts: Set<InitialFactAp>
    ): TaintVulnerabilityRuleNode.Fact = TaintVulnerabilityRuleNode.Fact(
        vulnerabilityTriggerPosition, hashMapOf(
            methodEntryPoint to VulnerabilityFactGroups(
                hashSetOf(VulnerabilityFacts(facts))
            )
        )
    )

    fun addVulnerabilityWithEndFactRequirement(
        methodEntryPoint: MethodEntryPoint,
        facts: Set<InitialFactAp>,
        statement: CommonInst,
        rule: CommonTaintConfigurationSink,
        requiredEndFacts: Set<FinalFactAp>,
        vulnerabilityTriggerPosition: VulnerabilityTriggerPosition = VulnerabilityTriggerPosition.BEFORE_INST,
    ) {
        val factNode = createFactNode(vulnerabilityTriggerPosition, methodEntryPoint, facts)
        val reqNode = WithRequirement(hashMapOf(EndFactRequirement(methodEntryPoint, requiredEndFacts) to factNode))
        val vuln = TaintVulnerability(statement, rule.id, hashMapOf(rule to reqNode))
        addVulnerability(vuln)
    }

    private fun addVulnerability(vulnerability: TaintVulnerability) {
        storage.addVulnerability(vulnerability)
    }

    data class FactWithPreconditions(val fact: InitialFactAp, val preconditions: List<Set<InitialFactAp>>)

    private data class TaintRuleAssumptions<T: CommonTaintConfigurationItem>(
        val rule: T,
        val statement: CommonInst,
        val facts: MutableMap<InitialFactAp, MutableSet<Set<InitialFactAp>>>
    )

    private val sourceRuleAssumptions = ConcurrentHashMap<CommonTaintConfigurationSource, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<CommonTaintConfigurationSource>>>()
    private val sinkRuleAssumptions = ConcurrentHashMap<CommonTaintConfigurationSink, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<CommonTaintConfigurationSink>>>()

    fun addSourceRuleAssumptions(rule: CommonTaintConfigurationSource, statement: CommonInst, assumptions: Map<InitialFactAp, Set<InitialFactAp>>) =
        addRuleAssumptions(sourceRuleAssumptions, rule, statement, assumptions)

    fun currentSourceRuleAssumptions(rule: CommonTaintConfigurationSource, statement: CommonInst) =
        currentRuleAssumptions(sourceRuleAssumptions, rule, statement)

    fun currentSourceRuleAssumptionsPreconditions(rule: CommonTaintConfigurationSource, statement: CommonInst, facts: List<InitialFactAp>) =
        currentRuleAssumptionsPreconditions(sourceRuleAssumptions, rule, statement, facts)

    fun addSinkRuleAssumptions(rule: CommonTaintConfigurationSink, statement: CommonInst, assumptions: Map<InitialFactAp, Set<InitialFactAp>>) =
        addRuleAssumptions(sinkRuleAssumptions, rule, statement, assumptions)

    fun currentSinkRuleAssumptions(rule: CommonTaintConfigurationSink, statement: CommonInst) =
        currentRuleAssumptions(sinkRuleAssumptions, rule, statement)

    private fun <T : CommonTaintConfigurationItem> addRuleAssumptions(
        storage: ConcurrentHashMap<T, ConcurrentHashMap<CommonInst, TaintRuleAssumptions<T>>>,
        rule: T, statement: CommonInst, assumptions: Map<InitialFactAp, Set<InitialFactAp>>
    ) {
        val currentAssumptions = storage
            .computeIfAbsent(rule) { ConcurrentHashMap() }
            .computeIfAbsent(statement) { TaintRuleAssumptions(rule, statement, hashMapOf()) }

        synchronized(currentAssumptions) {
            assumptions.forEach { (fact, factPre) ->
                currentAssumptions.facts.getOrPut(fact, ::hashSetOf).add(factPre)
            }
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

    companion object {
        private val logger = object : KLogging() {}.logger

        inline fun <K, V> MutableMap<K, V>.addAll(other: Map<K, V>, addValue: V.(V) -> V) {
            for ((key, value) in other) {
                val curValue = this[key]
                if (curValue != null) {
                    val modified = curValue.addValue(value)
                    if (modified !== curValue) {
                        put(key, modified)
                    }
                } else {
                    put(key, value)
                }
            }
        }
    }
}
