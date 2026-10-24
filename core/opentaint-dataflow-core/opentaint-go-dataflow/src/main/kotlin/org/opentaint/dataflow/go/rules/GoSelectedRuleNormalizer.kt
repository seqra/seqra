package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.go.serialized.GoUserDefinedRuleInfo
import org.opentaint.dataflow.configuration.isFalse
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.configuration.mkAnd
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkOr
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.selection.ReducedMarkPlan
import org.opentaint.dataflow.taint.SignedConditionNormalizer

/** The catalog fixed point is compiled once. These folds only lower that frozen plan.
 * Metadata guards and original positional carriers are never executable-normalized. */
internal class GoSelectedRuleNormalizer(private val plan: ReducedMarkPlan) {
    private fun executableCondition(c: CommonCondition<GoRuleCondition>): CommonCondition<GoRuleCondition> =
        SignedConditionNormalizer.normalize(condition(c), negativeLiteralsAreTrue = false)

    private fun condition(c: CommonCondition<GoRuleCondition>): CommonCondition<GoRuleCondition> = when (c) {
        is CommonCondition.True -> c
        is CommonCondition.Atom -> when (val atom = c.atom) {
            is GoRuleCondition.ContainsMark -> if (plan.isLive(atom.mark)) c else mkFalse()
            is GoRuleCondition.ContainsMarkOnAnyAccessor -> if (plan.isLive(atom.mark)) c else mkFalse()
            else -> c
        }
        is CommonCondition.Not -> when (val arg = condition(c.arg)) {
            is CommonCondition.True -> mkFalse()
            is CommonCondition.Not -> arg.arg
            else -> CommonCondition.Not(arg)
        }
        is CommonCondition.And -> {
            val args = c.args.map(::condition)
            if (args.any { it.isFalse() }) mkFalse() else mkAnd(args.filterNot { it.isTrue() })
        }
        is CommonCondition.Or -> {
            val args = c.args.map(::condition)
            if (args.any { it.isTrue() }) mkTrue() else mkOr(args.filterNot { it.isFalse() })
        }
    }

    fun global(rule: TaintRule.GlobalReadSource): TaintRule.GlobalReadSource? {
        if (plan.protectAll) return rule
        val guard = executableCondition(rule.condition)
        if (guard.isFalse()) return null
        val actions = rule.actionsAfter.filter { plan.isLive(it.mark) }
        if (actions.isEmpty()) return null
        return if (guard == rule.condition && actions == rule.actionsAfter) rule
            else rule.copy(condition = guard, actionsAfter = actions)
    }

    fun field(rule: TaintRule.FieldReadSource): TaintRule.FieldReadSource? {
        if (plan.protectAll) return rule
        val guard = executableCondition(rule.condition)
        if (guard.isFalse()) return null
        val actions = rule.actionsAfter.filter { plan.isLive(it.mark) }
        if (actions.isEmpty()) return null
        return if (guard == rule.condition && actions == rule.actionsAfter) rule
            else rule.copy(condition = guard, actionsAfter = actions)
    }

    fun source(rule: TaintRule.Source, metadata: Boolean): TaintRule.Source? {
        if (plan.protectAll) return rule
        if (metadata) {
            if (rule.actionsAfter.isEmpty()) return null
            val info = rule.info as? GoUserDefinedRuleInfo ?: return rule
            val marks = info.relevantTaintMarks.filterTo(linkedSetOf(), plan::isLive)
            if (marks.isEmpty()) return null
            return if (marks == info.relevantTaintMarks) rule
                else rule.copy(effectiveRelevantTaintMarks = marks)
        }
        val guard = executableCondition(rule.condition)
        if (guard.isFalse()) return null
        val actions = rule.actionsAfter.filter { plan.isLive(it.mark) }
        if (actions.isEmpty()) return null
        return if (guard == rule.condition && actions == rule.actionsAfter) rule
            else rule.copy(condition = guard, actionsAfter = actions)
    }

    fun sink(rule: TaintRule.Sink): TaintRule.Sink? {
        if (plan.protectAll) return rule
        val guard = executableCondition(rule.condition)
        if (guard.isFalse()) return null
        // Legacy END facts remain protected executable behavior. Keep their binding.
        return if (guard == rule.condition) rule else rule.copy(condition = guard)
    }

    fun cleaner(rule: TaintRule.Cleaner, metadata: Boolean): TaintRule.Cleaner? {
        if (plan.protectAll) return rule
        if (rule.actionsAfter.isEmpty()) return null
        if (metadata) {
            val info = rule.info as? GoUserDefinedRuleInfo ?: return rule
            val marks = info.relevantTaintMarks.filterTo(linkedSetOf(), plan::isLive)
            if (marks.isEmpty() && rule.actionsAfter.all { it is RemoveMark }) return null
            return if (marks == info.relevantTaintMarks) rule
                else rule.copy(effectiveRelevantTaintMarks = marks)
        }
        val guard = executableCondition(rule.condition)
        if (guard.isFalse()) return null
        return if (guard == rule.condition) rule else rule.copy(condition = guard)
    }
}
