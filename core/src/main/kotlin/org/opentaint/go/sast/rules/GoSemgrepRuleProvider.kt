package org.opentaint.go.sast.rules

import org.opentaint.common.sast.rules.SemgrepRuleProvider
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedItem
import org.opentaint.dataflow.go.GoFieldSignature
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.dataflow.go.GoGlobalFieldSignature
import org.opentaint.dataflow.go.rules.GoTaintConfiguration
import org.opentaint.dataflow.go.rules.GoTaintRulesProvider
import org.opentaint.dataflow.go.rules.GoRuleSelectionCapability
import org.opentaint.dataflow.go.rules.TaintRule
import org.opentaint.dataflow.go.rules.GoExecutableRuleCatalog
import org.opentaint.dataflow.configuration.selection.RuleSelectionSnapshot
import org.opentaint.dataflow.configuration.selection.MarkCatalog
import org.opentaint.dataflow.configuration.selection.ReducedMarkPlan
import org.opentaint.dataflow.configuration.selection.allReferencedMarks
import org.opentaint.dataflow.go.rules.sourceRulesForGlobalWithPresence
import org.opentaint.dataflow.go.rules.sourceRulesForFieldReadWithPresence
import org.opentaint.dataflow.go.rules.sourceRulesForCallWithPresence
import org.opentaint.dataflow.go.rules.sinkRulesForCallWithPresence
import org.opentaint.dataflow.go.rules.cleanerRulesForCallWithPresence
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep

class GoSemgrepRuleProvider(
    rules: List<TaintRuleFromSemgrep<GoSerializedItem>>,
    private val base: GoTaintConfiguration,
) : SemgrepRuleProvider<GoSerializedItem, TaintRule>(rules), GoRuleSelectionCapability {
    @Volatile private var selectedResolver: GoTaintRulesProvider = base
    @Volatile private var scopeStale = false
    private var resolverSnapshot: RuleSelectionSnapshot? = null
    private val externalScopes = linkedMapOf<Any, () -> GoExecutableRuleCatalog?>()
    private var compilerScopes: List<GoExecutableRuleCatalog?>? = null
    private var compilerGeneration = 0L

    override fun GoSerializedItem.ruleItemId(): String? = serializedId
    override fun TaintRule.resolvedRuleId(): String? = serializedId
    override fun ruleItemMarkReferences(item: GoSerializedItem): Set<String> = item.markCatalogRow().allReferencedMarks()

    @Synchronized
    override fun selectRules(ruleIds: Set<String>) {
        selectRelevantSemgrepRules(ruleIds)
    }

    override fun selectionPublished() {
        val snapshot = selectionSnapshot ?: return
        if (snapshot !== resolverSnapshot) {
            selectedResolver = base.selectedView(snapshot)
            resolverSnapshot = snapshot
        }
        scopeStale = false
    }

    override fun executableRuleCatalog(): GoExecutableRuleCatalog = base.executableRuleCatalog()

    @Synchronized
    override fun includeExecutableRuleScope(scopeId: Any, scope: () -> GoExecutableRuleCatalog?) {
        if (externalScopes[scopeId] === scope) return
        externalScopes[scopeId] = scope
        if (resolverSnapshot != null) scopeStale = true
    }

    override fun executableMarkCatalog(ignoreEndFacts: Boolean): MarkCatalog {
        val own = base.executableRuleCatalog()
        val scopes = listOf(own) + externalScopes.values.map { it() }
        if (scopes != compilerScopes) {
            compilerScopes = scopes
            compilerGeneration++
        }
        val ownCatalog = own.config.markCatalog()
        val externalRows = scopes.drop(1).filterNotNull().flatMap { scope ->
            // The other provider does not apply this Semgrep view's S policy.
            // Keep original IDs in runtime rows; only compiler rows are broad.
            scope.config.markCatalog().rows.map { it.copy(itemId = null) }
        }
        return ownCatalog.copy(rows = ownCatalog.rows + externalRows,
            protectAll = scopes.any { it == null }, sourceGeneration = compilerGeneration)
    }

    private fun resolver(): GoTaintRulesProvider {
        check(!scopeStale) { "Go executable scope changed; join readers and select rules before new queries" }
        return selectedResolver
    }

    override fun selectedMarkPlan(): ReducedMarkPlan? {
        resolver()
        return resolverSnapshot?.markPlan
    }
    override fun markReductionView(plan: ReducedMarkPlan): GoTaintRulesProvider {
        resolver()
        return resolverSnapshot?.let { snapshot -> base.selectedView(snapshot.withMarkPlan(plan)) } ?: this
    }
    override fun sourceRulesForGlobalWithPresence(signature: GoGlobalFieldSignature, statement: GoIRInst) =
        resolver().sourceRulesForGlobalWithPresence(signature, statement)
    override fun sourceRulesForFieldReadWithPresence(signature: GoFieldSignature, statement: GoIRInst) =
        resolver().sourceRulesForFieldReadWithPresence(signature, statement)
    override fun sourceRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        resolver().sourceRulesForCallWithPresence(signature, statement, allRelevant)
    override fun sinkRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst) =
        resolver().sinkRulesForCallWithPresence(signature, statement)
    override fun cleanerRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        resolver().cleanerRulesForCallWithPresence(signature, statement, allRelevant)

    override fun sourceRulesForGlobal(signature: GoGlobalFieldSignature, statement: GoIRInst) =
        resolver().sourceRulesForGlobal(signature, statement)

    override fun sourceRulesForFieldRead(signature: GoFieldSignature, statement: GoIRInst) =
        resolver().sourceRulesForFieldRead(signature, statement)

    override fun sourceRulesForCall(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        resolver().sourceRulesForCall(signature, statement, allRelevant)

    override fun sinkRulesForCall(signature: GoFunctionSignature, statement: GoIRInst) =
        resolver().sinkRulesForCall(signature, statement)

    override fun passThroughRulesForCall(signature: GoFunctionSignature, statement: GoIRInst) =
        resolver().passThroughRulesForCall(signature, statement)

    override fun cleanerRulesForCall(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        resolver().cleanerRulesForCall(signature, statement, allRelevant)
}
