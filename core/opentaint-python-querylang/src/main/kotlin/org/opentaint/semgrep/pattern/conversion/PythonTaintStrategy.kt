package org.opentaint.semgrep.pattern.conversion

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.python.serialized.PythonTarget
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonCondition
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonRule
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSink
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintAssignAction
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintCleanAction
import org.opentaint.semgrep.pattern.Mark
import org.opentaint.semgrep.pattern.TaintRuleMatchAnything
import org.opentaint.semgrep.pattern.conversion.LanguageStrategy.SinkDiscardMode
import org.opentaint.semgrep.pattern.conversion.python.ANY_PYTHON_FUNCTION
import org.opentaint.semgrep.pattern.conversion.python.PythonTaintRuleGenerationCtx
import org.opentaint.semgrep.pattern.conversion.python.emitPythonTaintRules
import org.opentaint.semgrep.pattern.conversion.python.mkPythonAssignMark
import org.opentaint.semgrep.pattern.conversion.python.mkPythonCleanMark
import org.opentaint.semgrep.pattern.conversion.python.mkPythonContainsMark
import org.opentaint.semgrep.pattern.conversion.python.mkPythonContainsMarkOnAnyAccessor
import org.opentaint.semgrep.pattern.conversion.taint.MarkConditionBuilder
import org.opentaint.semgrep.pattern.conversion.taint.RuleConversionCtx
import org.opentaint.semgrep.pattern.conversion.taint.TaintRuleGenerationCtx

data object PythonTaintStrategy :
    TaintRuleStrategy<SerializedPythonRule, SerializedPythonCondition, SerializedPythonTaintAssignAction, SerializedPythonTaintCleanAction> {

    override fun generateTaintRules(
        ctx: TaintRuleGenerationCtx<SerializedPythonRule, *, *, *>,
        ruleCtx: RuleConversionCtx,
        sinkDiscardMode: SinkDiscardMode,
    ): List<SerializedPythonRule> {
        val rules = ctx.pythonCtx().emitPythonTaintRules(ruleCtx)
        if (sinkDiscardMode == SinkDiscardMode.NONE) return rules

        return rules.filter { r ->
            if (r !is SerializedPythonSink) return@filter true
            if (r.condition != null) return@filter true

            if (sinkDiscardMode == SinkDiscardMode.TRIVIAL_CONDITION_WITH_EMPTY_FUNCTION) {
                val function = (r.target as? PythonTarget.Function)?.function
                if (function != ANY_PYTHON_FUNCTION) return@filter true
            }

            ruleCtx.trace.error(TaintRuleMatchAnything())
            false
        }
    }

    data object PythonMarkConditionBuilder : MarkConditionBuilder<SerializedPythonCondition> {
        override fun checkTaintMark(mark: Mark.GeneratedMark, pos: PositionBaseWithModifiers): SerializedPythonCondition =
            mark.mkPythonContainsMarkOnAnyAccessor(pos)

        override fun negate(cond: SerializedPythonCondition): SerializedPythonCondition =
            SerializedPythonCondition.Not(cond)

        override fun and(args: List<SerializedPythonCondition>): SerializedPythonCondition = SerializedPythonCondition.and(args)
        override fun or(args: List<SerializedPythonCondition>): SerializedPythonCondition = SerializedPythonCondition.or(args)
        override fun mkTrue(): SerializedPythonCondition = SerializedPythonCondition.True
        override fun mkFalse(): SerializedPythonCondition = SerializedPythonCondition.mkFalse()
    }

    override fun serializedItemId(item: SerializedPythonRule): String? = item.serializedId

    override val conditionBuilder = PythonMarkConditionBuilder

    override fun posContainsAnyMark(
        pos: PositionBaseWithModifiers,
        marks: Set<Mark.GeneratedMark>,
    ): SerializedPythonCondition = SerializedPythonCondition.or(marks.map { it.mkPythonContainsMark(pos) })

    override fun createCleanAction(
        mark: Mark.GeneratedMark,
        pos: PositionBaseWithModifiers,
    ): SerializedPythonTaintCleanAction = mark.mkPythonCleanMark(pos)

    override fun createAssignMark(
        mark: Mark.GeneratedMark,
        pos: PositionBaseWithModifiers,
    ): SerializedPythonTaintAssignAction = mark.mkPythonAssignMark(pos)

    override fun assignedMark(assign: SerializedPythonTaintAssignAction): Mark.GeneratedMark = Mark.parseMark(assign.kind)

    @Suppress("UNCHECKED_CAST")
    private fun TaintRuleGenerationCtx<SerializedPythonRule, *, *, *>.pythonCtx(): PythonTaintRuleGenerationCtx =
        this as PythonTaintRuleGenerationCtx
}
