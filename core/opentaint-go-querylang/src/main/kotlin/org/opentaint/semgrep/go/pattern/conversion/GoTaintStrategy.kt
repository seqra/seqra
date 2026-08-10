package org.opentaint.semgrep.go.pattern.conversion

import org.opentaint.dataflow.configuration.go.serialized.GoSerializedAssignAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCleanAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedItem
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.semgrep.go.pattern.conversion.go.emitGoTaintRules
import org.opentaint.semgrep.go.pattern.conversion.go.matchAnything
import org.opentaint.semgrep.go.pattern.conversion.go.mkGoAssignMark
import org.opentaint.semgrep.go.pattern.conversion.go.mkGoCleanMark
import org.opentaint.semgrep.go.pattern.conversion.go.mkGoContainsMark
import org.opentaint.semgrep.pattern.Mark
import org.opentaint.semgrep.pattern.TaintRuleMatchAnything
import org.opentaint.semgrep.pattern.conversion.LanguageStrategy.SinkDiscardMode
import org.opentaint.semgrep.pattern.conversion.TaintRuleStrategy
import org.opentaint.semgrep.pattern.conversion.taint.MarkConditionBuilder
import org.opentaint.semgrep.pattern.conversion.taint.RuleConversionCtx
import org.opentaint.semgrep.pattern.conversion.taint.TaintRuleGenerationCtx

typealias GoTaintRuleGenerationCtx = TaintRuleGenerationCtx<GoSerializedItem, GoSerializedCondition, GoSerializedAssignAction, GoSerializedCleanAction>

data object GoTaintStrategy :
    TaintRuleStrategy<GoSerializedItem, GoSerializedCondition, GoSerializedAssignAction, GoSerializedCleanAction> {

    override fun generateTaintRules(
        ctx: TaintRuleGenerationCtx<GoSerializedItem, *, *, *>,
        ruleCtx: RuleConversionCtx,
        sinkDiscardMode: SinkDiscardMode
    ): List<GoSerializedItem> {
        val rules = ctx.goCtx().emitGoTaintRules(ruleCtx)
        if (sinkDiscardMode == SinkDiscardMode.NONE) return rules

        return rules.filter { r ->
            if (r !is GoSerializedRule.Sink) return@filter true
            val cond = r.condition
            if (cond != null && cond !is GoSerializedCondition.True) return@filter true

            if (sinkDiscardMode == SinkDiscardMode.TRIVIAL_CONDITION_WITH_EMPTY_FUNCTION) {
                if (!r.function.matchAnything()) return@filter true
            }

            ruleCtx.trace.error(TaintRuleMatchAnything())
            false
        }
    }

    override fun serializedItemId(item: GoSerializedItem): String? = item.serializedId

    data object GoMarkConditionBuilder : MarkConditionBuilder<GoSerializedCondition> {
        override fun checkTaintMark(mark: Mark.GeneratedMark, pos: PositionBaseWithModifiers): GoSerializedCondition =
            mark.mkGoContainsMark(pos)

        override fun negate(cond: GoSerializedCondition) = GoSerializedCondition.not(cond)
        override fun and(args: List<GoSerializedCondition>) = GoSerializedCondition.and(args)
        override fun or(args: List<GoSerializedCondition>) = GoSerializedCondition.or(args)
        override fun mkTrue(): GoSerializedCondition = GoSerializedCondition.True
        override fun mkFalse(): GoSerializedCondition = GoSerializedCondition.mkFalse()
    }

    override val conditionBuilder = GoMarkConditionBuilder

    override fun posContainsAnyMark(
        pos: PositionBaseWithModifiers,
        marks: Set<Mark.GeneratedMark>
    ): GoSerializedCondition = GoSerializedCondition.or(marks.map { it.mkGoContainsMark(pos) })

    override fun createCleanAction(
        mark: Mark.GeneratedMark,
        pos: PositionBaseWithModifiers
    ): GoSerializedCleanAction = mark.mkGoCleanMark(pos)

    override fun createAssignMark(
        mark: Mark.GeneratedMark,
        pos: PositionBaseWithModifiers
    ): GoSerializedAssignAction = mark.mkGoAssignMark(pos)

    override fun createAssignTaintMark(
        mark: Mark.GeneratedMark,
        pos: PositionBaseWithModifiers
    ): GoSerializedAssignAction =
        mark.mkGoAssignMark(pos)

    override fun assignedMark(assign: GoSerializedAssignAction): Mark.GeneratedMark = Mark.parseMark(assign.kind)

    @Suppress("UNCHECKED_CAST")
    private fun TaintRuleGenerationCtx<GoSerializedItem, *, *, *>.goCtx(): GoTaintRuleGenerationCtx =
        this as GoTaintRuleGenerationCtx
}
