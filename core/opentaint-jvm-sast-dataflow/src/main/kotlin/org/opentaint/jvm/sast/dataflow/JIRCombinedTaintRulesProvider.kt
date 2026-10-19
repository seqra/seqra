package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.BidirectionalRulesProviderFactory
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import kotlin.collections.toList

class JIRCombinedTaintRulesProvider(
    private val base: TaintRulesProvider,
    private val combined: TaintRulesProvider,
    private val combinationOptions: CombinationOptions = CombinationOptions(),
) : TaintRulesProvider, BidirectionalRulesProviderFactory {
    override val markOwners: Map<String, String> by lazy {
        buildMap {
            for (provider in listOf(base, combined)) for ((mark, owner) in provider.markOwners) {
                val previous = put(mark, owner)
                require(previous == null || previous == owner) { "Conflicting generated mark owner: $mark" }
            }
        }
    }

    override fun bidirectionalRulesView(): TaintRulesProvider =
        JIRCombinedTaintRulesProvider(base.forBidirectionalCore(), combined.forBidirectionalCore(), combinationOptions)

    enum class CombinationMode {
        EXTEND, OVERRIDE, IGNORE
    }

    data class CombinationOptions(
        val entryPoint: CombinationMode = CombinationMode.OVERRIDE,
        val source: CombinationMode = CombinationMode.OVERRIDE,
        val sink: CombinationMode = CombinationMode.OVERRIDE,
        val passThrough: CombinationMode = CombinationMode.EXTEND,
        val cleaner: CombinationMode = CombinationMode.EXTEND,
    )

    override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.entryPoint, { condition }) { entryPointRulesForMethod(method, statement, fact, allRelevant) }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.source, { condition }) { sourceRulesForMethod(method, statement, fact, allRelevant) }

    override fun exitSourceRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = combine(combinationOptions.source, { condition }) { exitSourceRulesForMethod(method, statement, fact, allRelevant) }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.sink, { condition }) { sinkRulesForMethod(method, statement, fact, allRelevant) }

    override fun sinkRulesForMethodExit(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        initialFacts: Set<InitialFactAp>?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSink> =
        combine(combinationOptions.sink, { condition }) { sinkRulesForMethodExit(method, statement, fact, initialFacts, allRelevant) }

    override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.sink, { condition }) { sinkRulesForMethodEntry(method, statement, fact, allRelevant) }

    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst?,
        fact: FactAp?,
        allRelevant: Boolean
    ) = combine(combinationOptions.passThrough, { condition }) { passTroughRulesForMethod(method, statement, fact, allRelevant) }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.cleaner, { condition }) { cleanerRulesForMethod(method, statement, fact, allRelevant) }

    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.source, { condition }) { sourceRulesForStaticField(field, statement, fact, allRelevant) }

    private inline fun <T> combine(
        mode: CombinationMode,
        condition: T.() -> Any?,
        rules: TaintRulesProvider.() -> Iterable<T>,
    ): Iterable<T> {
        val baseRules = base.rules()
        val combinedRules = combined.rules().toList()

        return when (mode) {
            CombinationMode.EXTEND -> baseRules + combinedRules
            CombinationMode.OVERRIDE -> {
                if (combinedRules.isEmpty()) return baseRules

                val baseRulesList = baseRules.toList()
                if (baseRulesList.isEmpty()) return combinedRules

                val combinedConditions = combinedRules.mapTo(hashSetOf()) { it.condition() }
                val nonOverriddenBase = baseRulesList.filterNot { it.condition() in combinedConditions }

                combinedRules + nonOverriddenBase
            }
            CombinationMode.IGNORE -> baseRules
        }
    }

    override fun selectRules(ruleIds: Set<String>) {
        base.selectRules(ruleIds)
        combined.selectRules(ruleIds)
    }
}
