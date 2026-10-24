package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.go.GoFieldSignature
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.dataflow.go.GoGlobalFieldSignature
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.dataflow.configuration.selection.ReducedMarkPlan

class GoCombinedTaintRulesProvider private constructor(
    private val base: GoTaintRulesProvider,
    private val combined: GoTaintRulesProvider,
    private val options: CombinationOptions,
    registerScopes: Boolean,
) : GoRuleSelectionCapability {
    constructor(base: GoTaintRulesProvider, combined: GoTaintRulesProvider,
                options: CombinationOptions = CombinationOptions()) : this(base, combined, options, true)

    private val scopeId = Any()
    private var baseView = base
    private var combinedView = combined
    private var appliedPlan: ReducedMarkPlan? = null
    private var selected = false
    @Volatile private var scopeStale = false
    private val includedScopes = linkedMapOf<Any, () -> GoExecutableRuleCatalog?>()

    private inline fun <T> query(block: () -> T): T {
        check(!scopeStale) { "Go composition scope changed; join readers and select rules before new queries" }
        return block()
    }

    init {
        if (registerScopes) {
            if (hasExtraScope()) base.includeExecutableRuleScope(scopeId) { extraScope() }
            combined.includeExecutableRuleScope(scopeId) { base.executableRuleCatalog() }
        }
    }

    private var lastCatalogScopes: List<GoExecutableRuleCatalog>? = null
    private var catalogGeneration = 0L

    private fun hasExtraScope(): Boolean = listOf(options.globalSource, options.source,
        options.sink, options.passThrough, options.cleaner).any { it != CombinationMode.IGNORE }

    private fun extraScope(): GoExecutableRuleCatalog? = combined.executableRuleCatalog()?.let { scope ->
        scope.copy(config = scope.config.copy(
            globalSource = if (options.globalSource == CombinationMode.IGNORE) emptyList() else scope.config.globalSource,
            fieldSource = if (options.source == CombinationMode.IGNORE) emptyList() else scope.config.fieldSource,
            source = if (options.source == CombinationMode.IGNORE) emptyList() else scope.config.source,
            sink = if (options.sink == CombinationMode.IGNORE) emptyList() else scope.config.sink,
            passThrough = if (options.passThrough == CombinationMode.IGNORE) emptyList() else scope.config.passThrough,
            cleaner = if (options.cleaner == CombinationMode.IGNORE) emptyList() else scope.config.cleaner))
    }

    @Synchronized
    override fun executableRuleCatalog(): GoExecutableRuleCatalog? {
        val baseScope = base.executableRuleCatalog() ?: return null
        val extra = if (hasExtraScope()) extraScope() ?: return null else
            GoExecutableRuleCatalog(GoSerializedTaintConfig(), 0)
        val scopes = listOf(baseScope, extra)
        if (scopes != lastCatalogScopes) {
            lastCatalogScopes = scopes
            catalogGeneration++
        }
        val b = baseScope.config
        val e = extra.config
        return GoExecutableRuleCatalog(GoSerializedTaintConfig(
            b.globalSource + e.globalSource, b.fieldSource + e.fieldSource, b.source + e.source,
            b.sink + e.sink, b.passThrough + e.passThrough, b.cleaner + e.cleaner), catalogGeneration)
    }

    enum class CombinationMode { EXTEND, OVERRIDE, IGNORE }

    data class CombinationOptions(
        val source: CombinationMode = CombinationMode.OVERRIDE,
        val sink: CombinationMode = CombinationMode.OVERRIDE,
        val passThrough: CombinationMode = CombinationMode.EXTEND,
        val cleaner: CombinationMode = CombinationMode.EXTEND,
        val globalSource: CombinationMode = CombinationMode.OVERRIDE,
    )

    override fun sourceRulesForGlobal(signature: GoGlobalFieldSignature, statement: GoIRInst) =
        sourceRulesForGlobalWithPresence(signature, statement).rules
    override fun sourceRulesForFieldRead(signature: GoFieldSignature, statement: GoIRInst) =
        sourceRulesForFieldReadWithPresence(signature, statement).rules
    override fun sourceRulesForCall(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        sourceRulesForCallWithPresence(signature, statement, allRelevant).rules
    override fun sinkRulesForCall(signature: GoFunctionSignature, statement: GoIRInst) =
        sinkRulesForCallWithPresence(signature, statement).rules

    override fun sourceRulesForGlobalWithPresence(signature: GoGlobalFieldSignature, statement: GoIRInst) =
        query { combine(options.globalSource, baseView.sourceRulesForGlobalWithPresence(signature, statement),
            combinedView.sourceRulesForGlobalWithPresence(signature, statement)) }
    override fun sourceRulesForFieldReadWithPresence(signature: GoFieldSignature, statement: GoIRInst) =
        query { combine(options.source, baseView.sourceRulesForFieldReadWithPresence(signature, statement),
            combinedView.sourceRulesForFieldReadWithPresence(signature, statement)) }
    override fun sourceRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        query { combine(options.source, baseView.sourceRulesForCallWithPresence(signature, statement, allRelevant),
            combinedView.sourceRulesForCallWithPresence(signature, statement, allRelevant)) }
    override fun sinkRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst) =
        query { combine(options.sink, baseView.sinkRulesForCallWithPresence(signature, statement),
            combinedView.sinkRulesForCallWithPresence(signature, statement)) }

    override fun passThroughRulesForCall(signature: GoFunctionSignature, statement: GoIRInst) =
        query { combine(options.passThrough,
            GoRuleQueryResult(baseView.passThroughRulesForCall(signature, statement)),
            GoRuleQueryResult(combinedView.passThroughRulesForCall(signature, statement))).rules }

    override fun cleanerRulesForCall(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        cleanerRulesForCallWithPresence(signature, statement, allRelevant).rules
    override fun cleanerRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        query { combine(options.cleaner, baseView.cleanerRulesForCallWithPresence(signature, statement, allRelevant),
            combinedView.cleanerRulesForCallWithPresence(signature, statement, allRelevant)) }

    @Synchronized
    override fun selectRules(ruleIds: Set<String>) {
        base.selectRulesWithScope(ruleIds)
        combined.selectRulesWithScope(ruleIds)
        val plan = base.selectedMarkPlan() ?: combined.selectedMarkPlan()
        if (plan !== appliedPlan) {
            appliedPlan = plan
            baseView = plan?.let(base::markReductionView) ?: base
            combinedView = plan?.let(combined::markReductionView) ?: combined
        }
        selected = true
        scopeStale = false
    }

    override fun selectedMarkPlan(): ReducedMarkPlan? = query { appliedPlan }

    @Synchronized
    override fun includeExecutableRuleScope(scopeId: Any, scope: () -> GoExecutableRuleCatalog?) {
        if (includedScopes[scopeId] === scope) return
        includedScopes[scopeId] = scope
        base.includeExecutableRuleScope(scopeId, scope)
        combined.includeExecutableRuleScope(scopeId, scope)
        if (selected) scopeStale = true
    }

    override fun markReductionView(plan: ReducedMarkPlan): GoTaintRulesProvider =
        query { GoCombinedTaintRulesProvider(base.markReductionView(plan), combined.markReductionView(plan), options, false).also {
            it.appliedPlan = plan
            it.selected = true
        } }

    private fun <T> combine(mode: CombinationMode, base: GoRuleQueryResult<T>,
                            extra: GoRuleQueryResult<T>): GoRuleQueryResult<T> = when (mode) {
        CombinationMode.EXTEND -> GoRuleQueryResult(base.rules + extra.rules, base.originalPresence || extra.originalPresence)
        CombinationMode.OVERRIDE -> if (extra.originalPresence) extra else base
        CombinationMode.IGNORE -> base
    }
}
