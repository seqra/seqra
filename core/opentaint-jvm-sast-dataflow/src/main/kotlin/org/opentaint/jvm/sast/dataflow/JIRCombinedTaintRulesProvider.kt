package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.RawMarkDeclarations
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.SpecializedRules
import org.opentaint.dataflow.configuration.jvm.originalCondition
import org.opentaint.dataflow.configuration.jvm.ruleOrdinal
import org.opentaint.dataflow.configuration.jvm.relabelRuleOrdinal
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.originalPassThroughRulesForAliases
import org.opentaint.dataflow.jvm.ap.ifds.taint.JVMRuleSelectionCapability
import org.opentaint.dataflow.jvm.ap.ifds.taint.executableRuleCatalog
import org.opentaint.dataflow.jvm.ap.ifds.taint.includeExecutableRuleScope
import org.opentaint.dataflow.jvm.ap.ifds.taint.currentMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.applyExecutableMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.selectRulesWithScope
import org.opentaint.dataflow.jvm.ap.ifds.taint.BidirectionalRulesProviderFactory
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.dataflow.jvm.ap.ifds.taint.JVMExecutableRuleCatalog
import org.opentaint.dataflow.jvm.ap.ifds.taint.ExecutableMarkReduction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.jvm.bidi.interp.mergeRawMarkDeclarations
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import kotlin.collections.toList

class JIRCombinedTaintRulesProvider(
    private val base: TaintRulesProvider,
    private val combined: TaintRulesProvider,
    private val combinationOptions: CombinationOptions = CombinationOptions(),
    private val compilerScopeKey: Any = Any(),
) : TaintRulesProvider, BidirectionalRulesProviderFactory, JVMRuleSelectionCapability {
    init {
        if (hasExtraScope()) base.includeExecutableRuleScope(compilerScopeKey) { extraScope() }
        combined.includeExecutableRuleScope(compilerScopeKey) { base.executableRuleCatalog() }
        base.currentMarkReduction()?.let(combined::applyExecutableMarkReduction)
    }

    private var lastCatalogScopes: List<JVMExecutableRuleCatalog>? = null
    private var catalogGeneration = 0L

    private fun hasExtraScope(): Boolean = with(combinationOptions) {
        listOf(entryPoint, source, sink, passThrough, cleaner).any { it != CombinationMode.IGNORE }
    }

    private fun extraScope(): JVMExecutableRuleCatalog? = combined.executableRuleCatalog()?.let { scope ->
        scope.copy(items = scope.items.filter { item ->
            val mode = when (item) {
                is SerializedRule.EntryPoint -> combinationOptions.entryPoint
                is SerializedRule.Source, is SerializedRule.MethodExitSource,
                is SerializedFieldRule.SerializedStaticFieldSource -> combinationOptions.source
                is SerializedRule.Sink, is SerializedRule.MethodEntrySink,
                is SerializedRule.MethodExitSink -> combinationOptions.sink
                is SerializedRule.Cleaner -> combinationOptions.cleaner
                is SerializedRule.PassThrough -> combinationOptions.passThrough
            }
            mode != CombinationMode.IGNORE
        })
    }

    @Synchronized
    override fun executableRuleCatalog(): JVMExecutableRuleCatalog? {
        val own = base.executableRuleCatalog() ?: return null
        val extra = if (hasExtraScope()) extraScope() ?: return null else JVMExecutableRuleCatalog(emptyList(), 0)
        val scopes = listOf(own, extra)
        if (scopes != lastCatalogScopes) {
            lastCatalogScopes = scopes
            catalogGeneration++
        }
        return JVMExecutableRuleCatalog(own.items + extra.items, catalogGeneration)
    }

    override fun includeExecutableRuleScope(key: Any, scope: () -> JVMExecutableRuleCatalog?) {
        base.includeExecutableRuleScope(key, scope)
        combined.includeExecutableRuleScope(key, scope)
    }

    override fun currentMarkReduction(): ExecutableMarkReduction? =
        base.currentMarkReduction() ?: combined.currentMarkReduction()

    override fun applyExecutableMarkReduction(reduction: ExecutableMarkReduction) {
        base.applyExecutableMarkReduction(reduction)
        combined.applyExecutableMarkReduction(reduction)
    }
    override val markDeclarations: RawMarkDeclarations
        get() = mergeRawMarkDeclarations(listOf(base.markDeclarations, combined.markDeclarations))

    override val markOwners: Map<String, String> by lazy {
        buildMap {
            for (provider in listOf(base, combined)) for ((mark, owner) in provider.markOwners) {
                val previous = put(mark, owner)
                require(previous == null || previous == owner) { "Conflicting generated mark owner: $mark" }
            }
        }
    }

    override fun bidirectionalRulesView(): TaintRulesProvider =
        JIRCombinedTaintRulesProvider(base.forBidirectionalCore(), combined.forBidirectionalCore(), combinationOptions,
            compilerScopeKey)

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

    override fun originalPassThroughRulesForAliases(method: CommonMethod) =
        combine(combinationOptions.passThrough, { condition }) { originalPassThroughRulesForAliases(method) }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.cleaner, { condition }) { cleanerRulesForMethod(method, statement, fact, allRelevant) }

    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        combine(combinationOptions.source, { condition }) { sourceRulesForStaticField(field, statement, fact, allRelevant) }

    @Suppress("UNCHECKED_CAST")
    private fun <T : TaintConfigurationItem> combine(
        mode: CombinationMode,
        condition: T.() -> Any?,
        rules: TaintRulesProvider.() -> Iterable<T>,
    ): Iterable<T> {
        val baseRules = base.rules()
        val extraRules = combined.rules()
        if (mode == CombinationMode.IGNORE) return baseRules
        val baseRulesList = baseRules.toList()
        val combinedRules = extraRules.toList()

        val labelled = baseRules is SpecializedRules<*> || extraRules is SpecializedRules<*> ||
            baseRulesList.any { it.specialization != null } || combinedRules.any { it.specialization != null }
        if (labelled) {
            fun originalConditions(rows: Iterable<T>, values: List<T>): List<Any?> =
                (rows as? SpecializedRules<*>)?.originalConditions ?:
                    values.map { if (it.specialization == null) it.condition() else it.originalCondition() }
            val baseConditions = originalConditions(baseRules, baseRulesList)
            val extraConditions = originalConditions(extraRules, combinedRules)
            val originalConditions: List<Any?>
            val baseOrdinals = IntArray(baseConditions.size) { it }
            val extraOffset: Int
            if (mode == CombinationMode.EXTEND) {
                originalConditions = baseConditions + extraConditions
                extraOffset = baseConditions.size
            } else {
                val overrides = extraConditions.toHashSet()
                var next = extraConditions.size
                for (index in baseConditions.indices)
                    baseOrdinals[index] = if (baseConditions[index] in overrides) -1 else next++
                originalConditions = extraConditions + baseConditions.filterNot { it in overrides }
                extraOffset = 0
            }
            val total = originalConditions.size
            val keptBase = baseRulesList.mapIndexedNotNull { index, row ->
                val ordinal = baseOrdinals[row.ruleOrdinal(index)]
                if (ordinal < 0) null else row.relabelRuleOrdinal(ordinal, total) as T
            }
            val keptExtra = combinedRules.mapIndexed { index, row ->
                row.relabelRuleOrdinal(extraOffset + row.ruleOrdinal(index), total) as T
            }
            return SpecializedRules(if (mode == CombinationMode.EXTEND) keptBase + keptExtra else keptExtra + keptBase,
                total, originalConditions)
        }

        return when (mode) {
            CombinationMode.EXTEND -> baseRules + combinedRules
            CombinationMode.OVERRIDE -> {
                if (combinedRules.isEmpty()) return baseRules

                if (baseRulesList.isEmpty()) return combinedRules

                val combinedConditions = combinedRules.mapTo(hashSetOf()) { it.condition() }
                val nonOverriddenBase = baseRulesList.filterNot { it.condition() in combinedConditions }

                combinedRules + nonOverriddenBase
            }
            CombinationMode.IGNORE -> baseRules
        }
    }

    override fun selectRules(ruleIds: Set<String>) {
        base.selectRulesWithScope(ruleIds)
        combined.selectRulesWithScope(ruleIds)
        base.currentMarkReduction()?.let(combined::applyExecutableMarkReduction)
    }
}
