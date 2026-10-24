package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.go.GoFieldSignature
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.dataflow.go.GoGlobalFieldSignature
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.dataflow.configuration.selection.ReducedMarkPlan
import org.opentaint.dataflow.configuration.selection.RuleSelectionBoundary

/** A compiler-only view of a provider's complete original executable scope. */
data class GoExecutableRuleCatalog(val config: GoSerializedTaintConfig, val generation: Long)

/** Presence is taken before executable compaction, for OVERRIDE admission. */
data class GoRuleQueryResult<out T>(val rules: List<T>, val originalPresence: Boolean = rules.isNotEmpty())

interface GoTaintRulesProvider {
    fun sourceRulesForGlobal(signature: GoGlobalFieldSignature, statement: GoIRInst): List<TaintRule.GlobalReadSource>
    fun sourceRulesForFieldRead(signature: GoFieldSignature, statement: GoIRInst): List<TaintRule.FieldReadSource>
    fun sourceRulesForCall(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean = false): List<TaintRule.Source>
    fun sinkRulesForCall(signature: GoFunctionSignature, statement: GoIRInst): List<TaintRule.Sink>
    fun passThroughRulesForCall(signature: GoFunctionSignature, statement: GoIRInst): List<TaintRule.PassThrough>
    fun cleanerRulesForCall(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean = false): List<TaintRule.Cleaner>
    fun selectRules(ruleIds: Set<String>) = Unit
}

/** Explicit opt-in. A wrapper which delegates the ordinary provider API does
 * not inherit proof that its overridden rule queries have a complete catalog. */
interface GoRuleSelectionCapability : GoTaintRulesProvider {
    fun executableRuleCatalog(): GoExecutableRuleCatalog? = null
    fun includeExecutableRuleScope(scopeId: Any, scope: () -> GoExecutableRuleCatalog?) = Unit
    fun selectedMarkPlan(): ReducedMarkPlan? = null
    fun markReductionView(plan: ReducedMarkPlan): GoTaintRulesProvider = this
    fun sourceRulesForGlobalWithPresence(signature: GoGlobalFieldSignature, statement: GoIRInst) =
        GoRuleQueryResult(sourceRulesForGlobal(signature, statement))
    fun sourceRulesForFieldReadWithPresence(signature: GoFieldSignature, statement: GoIRInst) =
        GoRuleQueryResult(sourceRulesForFieldRead(signature, statement))
    fun sourceRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        GoRuleQueryResult(sourceRulesForCall(signature, statement, allRelevant))
    fun sinkRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst) =
        GoRuleQueryResult(sinkRulesForCall(signature, statement))
    fun cleanerRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
        GoRuleQueryResult(cleanerRulesForCall(signature, statement, allRelevant))
}

fun GoTaintRulesProvider.executableRuleCatalog(): GoExecutableRuleCatalog? =
    (this as? GoRuleSelectionCapability)?.executableRuleCatalog()
fun GoTaintRulesProvider.includeExecutableRuleScope(scopeId: Any, scope: () -> GoExecutableRuleCatalog?) {
    (this as? GoRuleSelectionCapability)?.includeExecutableRuleScope(scopeId, scope)
}
fun GoTaintRulesProvider.selectedMarkPlan(): ReducedMarkPlan? =
    (this as? GoRuleSelectionCapability)?.selectedMarkPlan()
fun GoTaintRulesProvider.selectRulesWithScope(ruleIds: Set<String>) {
    RuleSelectionBoundary.withScope(this is GoRuleSelectionCapability) { selectRules(ruleIds) }
}
fun GoTaintRulesProvider.markReductionView(plan: ReducedMarkPlan): GoTaintRulesProvider =
    (this as? GoRuleSelectionCapability)?.markReductionView(plan) ?: this
fun GoTaintRulesProvider.sourceRulesForGlobalWithPresence(signature: GoGlobalFieldSignature, statement: GoIRInst) =
    (this as? GoRuleSelectionCapability)?.sourceRulesForGlobalWithPresence(signature, statement)
        ?: GoRuleQueryResult(sourceRulesForGlobal(signature, statement))
fun GoTaintRulesProvider.sourceRulesForFieldReadWithPresence(signature: GoFieldSignature, statement: GoIRInst) =
    (this as? GoRuleSelectionCapability)?.sourceRulesForFieldReadWithPresence(signature, statement)
        ?: GoRuleQueryResult(sourceRulesForFieldRead(signature, statement))
fun GoTaintRulesProvider.sourceRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
    (this as? GoRuleSelectionCapability)?.sourceRulesForCallWithPresence(signature, statement, allRelevant)
        ?: GoRuleQueryResult(sourceRulesForCall(signature, statement, allRelevant))
fun GoTaintRulesProvider.sinkRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst) =
    (this as? GoRuleSelectionCapability)?.sinkRulesForCallWithPresence(signature, statement)
        ?: GoRuleQueryResult(sinkRulesForCall(signature, statement))
fun GoTaintRulesProvider.cleanerRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
    (this as? GoRuleSelectionCapability)?.cleanerRulesForCallWithPresence(signature, statement, allRelevant)
        ?: GoRuleQueryResult(cleanerRulesForCall(signature, statement, allRelevant))
