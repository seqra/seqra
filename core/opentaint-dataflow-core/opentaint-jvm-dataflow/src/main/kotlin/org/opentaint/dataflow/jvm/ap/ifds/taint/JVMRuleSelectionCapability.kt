package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.selection.ReducedMarkPlan
import org.opentaint.dataflow.configuration.selection.RuleSelectionBoundary
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.ir.api.common.CommonMethod

/** Original compiler input. This value contains no bound method rules. */
data class JVMExecutableRuleCatalog(val items: List<SerializedItem>, val generation: Long)

/** A frozen plan and its selection identity, shared by known composed views. */
data class ExecutableMarkReduction(val identity: Any, val plan: ReducedMarkPlan)

/** Explicit opt-in. A provider delegated with `by` can override rule queries;
 * delegation alone must never certify its complete executable mark scope. */
interface JVMRuleSelectionCapability {
    fun executableRuleCatalog(): JVMExecutableRuleCatalog?
    fun includeExecutableRuleScope(key: Any, scope: () -> JVMExecutableRuleCatalog?) = Unit
    fun currentMarkReduction(): ExecutableMarkReduction? = null
    fun applyExecutableMarkReduction(reduction: ExecutableMarkReduction) = Unit
    fun originalPassThroughRulesForAliases(method: CommonMethod): Iterable<TaintPassThrough>
}

/** Opaque wrappers keep the original public query, including their overrides. */
fun TaintRulesProvider.originalPassThroughRulesForAliases(method: CommonMethod): Iterable<TaintPassThrough> =
    (this as? JVMRuleSelectionCapability)?.originalPassThroughRulesForAliases(method)
        ?: passTroughRulesForMethod(method, null, null, false)

fun TaintRulesProvider.executableRuleCatalog(): JVMExecutableRuleCatalog? =
    (this as? JVMRuleSelectionCapability)?.executableRuleCatalog()

fun TaintRulesProvider.includeExecutableRuleScope(key: Any, scope: () -> JVMExecutableRuleCatalog?) {
    (this as? JVMRuleSelectionCapability)?.includeExecutableRuleScope(key, scope)
}

fun TaintRulesProvider.currentMarkReduction(): ExecutableMarkReduction? =
    (this as? JVMRuleSelectionCapability)?.currentMarkReduction()

fun TaintRulesProvider.applyExecutableMarkReduction(reduction: ExecutableMarkReduction) {
    (this as? JVMRuleSelectionCapability)?.applyExecutableMarkReduction(reduction)
}

/** The coordinator knows the complete outer provider. Keep its original
 * selectRules dispatch, but do not infer absence below an opaque wrapper. */
fun TaintRulesProvider.selectRulesWithScope(ruleIds: Set<String>) {
    RuleSelectionBoundary.withScope(this is JVMRuleSelectionCapability) { selectRules(ruleIds) }
}
