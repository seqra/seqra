package org.opentaint.jvm.sast.rules

import org.opentaint.common.sast.rules.SemgrepRuleProvider
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.MarkGroupKey
import org.opentaint.dataflow.bidi.ap.RawMarkDeclarations
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.JVMRuleSelectionCapability
import org.opentaint.dataflow.jvm.ap.ifds.taint.executableRuleCatalog
import org.opentaint.dataflow.jvm.ap.ifds.taint.includeExecutableRuleScope
import org.opentaint.dataflow.jvm.ap.ifds.taint.currentMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.applyExecutableMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.BidirectionalRulesProviderFactory
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.dataflow.jvm.ap.ifds.taint.JVMExecutableRuleCatalog
import org.opentaint.dataflow.jvm.ap.ifds.taint.ExecutableMarkReduction
import org.opentaint.dataflow.jvm.bidi.interp.mergeRawMarkDeclarations
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.dataflow.rules.JVMRuleSpecialization
import org.opentaint.jvm.sast.dataflow.rules.executableMarkCatalog
import org.opentaint.jvm.sast.dataflow.rules.markCatalogRow
import org.opentaint.jvm.sast.dataflow.rules.selectionRole
import org.opentaint.jvm.sast.dataflow.rules.originalExecutableRuleCatalog
import org.opentaint.dataflow.configuration.selection.MarkCatalog
import org.opentaint.dataflow.configuration.selection.RuleSelectionSnapshot
import org.opentaint.dataflow.configuration.selection.allReferencedMarks
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep

class JIRSemgrepRuleProvider(
    rules: List<TaintRuleFromSemgrep<SerializedItem>>,
    private val taintConfiguration: TaintConfiguration
) : TaintRulesProvider, BidirectionalRulesProviderFactory, JVMRuleSelectionCapability, SemgrepRuleProvider<SerializedItem, TaintConfigurationItem>(rules) {
    val base = JIRTaintRulesProvider(taintConfiguration)
    private val ordinarySelectedBase = JIRTaintRulesProvider(taintConfiguration)
    private val bidiBase = JIRTaintRulesProvider(taintConfiguration, ignoreSinkEndFacts = true)
    private val externalScopes = linkedMapOf<Any, () -> JVMExecutableRuleCatalog?>()
    private var compilerScopes: List<JVMExecutableRuleCatalog?>? = null
    private var compilerGeneration = 0L
    @Volatile private var scopeStale = false
    override val markOwners: Map<String, String> = buildMap {
        for (rule in rules) for ((mark, owner) in rule.markOwners) {
            val previous = put(mark, owner)
            require(previous == null || previous == owner) { "Conflicting generated mark owner: $mark" }
        }
    }

    private val generatedDeclarations: RawMarkDeclarations = run {
        val flags = linkedMapOf<MarkGroupKey, Boolean>()
        for (rule in rules) {
            require(rule.ruleId in rule.primitiveByOwner) {
                "Missing primitive flag for loaded rule: ${rule.ruleId}"
            }
            for ((owner, primitive) in rule.primitiveByOwner) {
                val key = MarkGroupKey.Rule(owner)
                val previous = flags.putIfAbsent(key, primitive)
                require(previous == null || previous == primitive) { "Conflicting primitive flag: $owner" }
            }
        }
        mergeRawMarkDeclarations(listOf(RawMarkDeclarations(
            names = markOwners.keys,
            explicitOwners = markOwners.mapValues { (_, owner) -> MarkGroupKey.Rule(owner) },
            primitiveByOwner = flags,
        )))
    }

    override val markDeclarations: RawMarkDeclarations
        get() = mergeRawMarkDeclarations(listOf(base.markDeclarations, generatedDeclarations))

    override fun bidirectionalRulesView(): TaintRulesProvider {
        return object : TaintRulesProvider by bidiBase, JVMRuleSelectionCapability {
            override val markOwners get() = this@JIRSemgrepRuleProvider.markOwners
            override val markDeclarations get() = this@JIRSemgrepRuleProvider.markDeclarations
            override fun selectRules(ruleIds: Set<String>) = this@JIRSemgrepRuleProvider.selectRules(ruleIds)
            override fun executableRuleCatalog() = this@JIRSemgrepRuleProvider.executableRuleCatalog()
            override fun includeExecutableRuleScope(key: Any, scope: () -> JVMExecutableRuleCatalog?) =
                this@JIRSemgrepRuleProvider.includeExecutableRuleScope(key, scope)
            override fun currentMarkReduction() = bidiSelectionSnapshot?.let { ExecutableMarkReduction(it, it.markPlan) }
            override fun originalPassThroughRulesForAliases(method: CommonMethod) =
                bidiBase.originalPassThroughRulesForAliases(method)
        }
    }

    override fun executableRuleCatalog() = taintConfiguration.originalExecutableRuleCatalog()
    @Synchronized
    override fun includeExecutableRuleScope(key: Any, scope: () -> JVMExecutableRuleCatalog?) {
        externalScopes[key] = scope
        if (selectionSnapshot != null) {
            val scopes = listOf(executableRuleCatalog()) + externalScopes.values.map { it() }
            if (scopes != compilerScopes) scopeStale = true
        }
    }
    override fun currentMarkReduction() = selectionSnapshot?.let { ExecutableMarkReduction(it, it.markPlan) }

    override fun executableMarkCatalog(ignoreEndFacts: Boolean): MarkCatalog {
        val scopes = listOf(executableRuleCatalog()) + externalScopes.values.map { it() }
        if (scopes != compilerScopes) {
            compilerScopes = scopes
            compilerGeneration++
        }
        val own = taintConfiguration.executableMarkCatalog(ignoreEndFacts)
        val external = scopes.drop(1).filterNotNull().flatMap { scope ->
            scope.items.map { it.markCatalogRow(ignoreEndFacts).copy(itemId = null) }
        }
        return own.copy(rows = own.rows + external, protectAll = scopes.any { it == null },
            sourceGeneration = compilerGeneration)
    }
    override fun ruleItemMarkReferences(item: SerializedItem): Set<String> = item.markCatalogRow(false).allReferencedMarks()

    override fun SerializedItem.ruleItemId(): String? = serializedId
    override fun TaintConfigurationItem.resolvedRuleId(): String? = serializedId

    init {
        val rules = rules.flatMap { groups -> groups.taintRules.flatMap { it.rules } }
        val serializedCfg = SerializedTaintConfig(
            entryPoint = rules.filterIsInstance<SerializedRule.EntryPoint>(),
            source = rules.filterIsInstance<SerializedRule.Source>(),
            methodExitSource = rules.filterIsInstance<SerializedRule.MethodExitSource>(),
            sink = rules.filterIsInstance<SerializedRule.Sink>(),
            passThrough = rules.filterIsInstance<SerializedRule.PassThrough>(),
            cleaner = rules.filterIsInstance<SerializedRule.Cleaner>(),
            methodExitSink = rules.filterIsInstance<SerializedRule.MethodExitSink>(),
            methodEntrySink = rules.filterIsInstance<SerializedRule.MethodEntrySink>(),
            staticFieldSource = rules.filterIsInstance<SerializedFieldRule.SerializedStaticFieldSource>(),
        )
        taintConfiguration.loadConfig(serializedCfg)
    }

    override fun selectRules(ruleIds: Set<String>) {
        selectRelevantSemgrepRules(ruleIds)
    }

    override fun selectionPublished() {
        selectionSnapshot?.let { ordinarySelectedBase.installSelection(it.resolverSelection()) }
        bidiSelectionSnapshot?.let { bidiBase.installSelection(it.resolverSelection()) }
        scopeStale = false
    }

    private fun RuleSelectionSnapshot.resolverSelection() = TaintConfiguration.ResolverSelection(
        identity = this,
        keepRaw = { item, metadata -> keepsRaw(item.serializedId, item.selectionRole(), metadata) },
        normalize = { item, metadata, _ -> JVMRuleSpecialization.normalize(item, metadata, markPlan) },
        validate = { check(!scopeStale) { "Executable rule scope changed; select rules again at a joined boundary" } },
    )

    override fun entryPointRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = ordinarySelectedBase.entryPointRulesForMethod(method, statement, fact, allRelevant)

    override fun sourceRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = ordinarySelectedBase.sourceRulesForMethod(method, statement, fact, allRelevant)

    override fun exitSourceRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = ordinarySelectedBase.exitSourceRulesForMethod(method, statement, fact, allRelevant)

    override fun sinkRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = ordinarySelectedBase.sinkRulesForMethod(method, statement, fact, allRelevant)

    override fun sinkRulesForMethodEntry(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = ordinarySelectedBase.sinkRulesForMethodEntry(method, statement, fact, allRelevant)

    override fun sinkRulesForMethodExit(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        initialFacts: Set<InitialFactAp>?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSink> =
        ordinarySelectedBase.sinkRulesForMethodExit(method, statement, fact, initialFacts, allRelevant)

    override fun sourceRulesForStaticField(
        field: JIRField,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = ordinarySelectedBase.sourceRulesForStaticField(field, statement, fact, allRelevant)

    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst?,
        fact: FactAp?,
        allRelevant: Boolean
    ) = ordinarySelectedBase.passTroughRulesForMethod(method, statement, fact, allRelevant)

    override fun originalPassThroughRulesForAliases(method: CommonMethod) =
        base.originalPassThroughRulesForAliases(method)

    override fun cleanerRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = ordinarySelectedBase.cleanerRulesForMethod(method, statement, fact, allRelevant)
}
