package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.isFalse
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.configuration.mkAnd
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkOr
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.selection.ReducedMarkPlan
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.ap.ifds.taint.ContainsMarkOnAnyField
import org.opentaint.dataflow.taint.SignedConditionNormalizer

/** Applies the one global executable plan after the ORIGINAL method binder.
 * Metadata retains its original resolved guard and all original position carriers. */
object JVMRuleSpecialization {
    fun indexed(item: TaintConfigurationItem, ordinal: Int, count: Int): TaintConfigurationItem =
        copy(item, specialization = RuleSpecializationIndices(ordinal, item,
            actions(item).indices.toList(), ruleCount = count))

    fun normalize(item: TaintConfigurationItem, metadata: Boolean, plan: ReducedMarkPlan): TaintConfigurationItem? {
        // An opaque executable scope can add effects to an empty row. TOP is
        // the original compatibility path, including its row presence.
        if (plan.protectAll) return item
        val condition = condition(item)
        if (metadata) {
            // An exact executable rejection must not reject a relaxed metadata
            // variant. The metadata binder has already applied its own guard.
            if (condition.isFalse()) return null
            val info = item.info as? UserDefinedRuleInfo ?: return item
            if (item !is TaintConfigurationSource && item !is TaintCleaner) return item
            val resetMarks = info.relevantTaintMarks.mapIndexedNotNull { ordinal, name ->
                ordinal.takeIf { plan.isLive(name) }
            }.toSet()
            val positionalActions = actions(item)
            val opaqueEffects = item is TaintCleaner && item.actionsAfter.any { it !is RemoveMark }
            if (!opaqueEffects && (resetMarks.isEmpty() || positionalActions.isEmpty())) return null
            val labels = checkNotNull(item.specialization)
            return copy(item, specialization = labels.copy(resetMarkOrdinals = resetMarks))
        }

        // The original binder can expand one CM into root/element alternatives.
        // Preserve exact Boolean identities in that complete bound condition;
        // this pass is outside the global structural-decrease loop.
        val folded = SignedConditionNormalizer.normalize(fold(condition, plan))
        if (folded.isFalse()) return null
        val before = actions(item)
        val selected = before.withIndex().filter { (_, action) -> action !is AssignMark || plan.isLive(action.mark.name) }
        if ((item is TaintConfigurationSource || item is TaintCleaner) && selected.isEmpty()) return null
        val labels = checkNotNull(item.specialization)
        return copy(item, folded, selected.map { it.value }, labels.copy(
            actionOrdinals = selected.map { labels.actionOrdinals[it.index] }))
    }

    fun actions(item: TaintConfigurationItem): List<Action> = when (item) {
        is TaintConfigurationSource -> item.actionsAfter
        is TaintConfigurationSink -> item.trackFactsReachAnalysisEnd
        is TaintCleaner -> item.actionsAfter
        is TaintPassThrough -> item.actionsAfter
    }

    private fun condition(item: TaintConfigurationItem): Condition = when (item) {
        is TaintConfigurationSource -> item.condition
        is TaintConfigurationSink -> item.condition
        is TaintCleaner -> item.condition
        is TaintPassThrough -> item.condition
    }

    private fun fold(condition: Condition, plan: ReducedMarkPlan): Condition = when (condition) {
        is CommonCondition.True -> condition
        is CommonCondition.Atom -> when (val atom = condition.atom) {
            is ContainsMark -> if (plan.isLive(atom.mark.name)) condition else mkFalse()
            is ContainsMarkOnAnyField -> if (plan.isLive(atom.mark.name)) condition else mkFalse()
            else -> condition
        }
        is CommonCondition.Not -> {
            val child = fold(condition.arg, plan)
            when { child.isFalse() -> mkTrue(); child.isTrue() -> mkFalse(); else -> CommonCondition.Not(child) }
        }
        is CommonCondition.And -> junction(condition.args.map { fold(it, plan) }, true)
        is CommonCondition.Or -> junction(condition.args.map { fold(it, plan) }, false)
    }

    private fun junction(args: List<Condition>, and: Boolean): Condition {
        if (args.any { if (and) it.isFalse() else it.isTrue() }) return if (and) mkFalse() else mkTrue()
        val retained = args.filterNot { if (and) it.isTrue() else it.isFalse() }
        return if (and) mkAnd(retained) else mkOr(retained)
    }

    private fun copy(item: TaintConfigurationItem, condition: Condition = condition(item),
                     actions: List<Action> = actions(item),
                     specialization: RuleSpecializationIndices?): TaintConfigurationItem = when (item) {
        is TaintEntryPointSource -> item.copy(condition = condition, actionsAfter = actions.map { it as AssignMark }, specialization = specialization)
        is TaintMethodSource -> item.copy(condition = condition, actionsAfter = actions.map { it as AssignMark }, specialization = specialization)
        is TaintMethodExitSource -> item.copy(condition = condition, actionsAfter = actions.map { it as AssignMark }, specialization = specialization)
        is TaintStaticFieldSource -> item.copy(condition = condition, actionsAfter = actions.map { it as AssignMark }, specialization = specialization)
        is TaintMethodSink -> item.copy(condition = condition, trackFactsReachAnalysisEnd = actions.map { it as AssignMark }, specialization = specialization)
        is TaintMethodEntrySink -> item.copy(condition = condition, trackFactsReachAnalysisEnd = actions.map { it as AssignMark }, specialization = specialization)
        is TaintMethodExitSink -> item.copy(condition = condition, trackFactsReachAnalysisEnd = actions.map { it as AssignMark }, specialization = specialization)
        is TaintCleaner -> item.copy(condition = condition, actionsAfter = actions, specialization = specialization)
        is TaintPassThrough -> item.copy(condition = condition, actionsAfter = actions, specialization = specialization)
    }
}
