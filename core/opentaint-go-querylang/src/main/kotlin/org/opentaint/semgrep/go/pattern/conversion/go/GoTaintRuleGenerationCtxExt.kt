package org.opentaint.semgrep.go.pattern.conversion.go

import org.opentaint.dataflow.configuration.go.serialized.GoNameMatcher
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoUserDefinedRuleInfo
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.semgrep.pattern.UserRuleFromSemgrepInfo
import org.opentaint.semgrep.pattern.conversion.MetavarAtom

data class GoUserRuleFromSemgrepInfo(
    val ruleId: String,
    override val relevantTaintMarks: Set<String>
) : GoUserDefinedRuleInfo

fun UserRuleFromSemgrepInfo.toGo() =
    GoUserRuleFromSemgrepInfo(ruleId, relevantTaintMarks)

internal data class GoRuleConditionData(
    val function: GoFunctionNameMatcher,
    val condition: GoSerializedCondition,
)

data class GoFunctionNameMatcher(
    val pkgMatcher: GoNameMatcher,
    val nameMatcher: GoNameMatcher,
)

internal class GoRuleConditionBuilder {
    var function: GoFunctionNameMatcher? = null
    val conditions = hashSetOf<GoSerializedCondition>()

    fun build(): GoRuleConditionData = GoRuleConditionData(
        function = function ?: GoFunctionNameMatcher(goAnyNameMatcher(), goAnyNameMatcher()),
        condition = GoSerializedCondition.and(conditions.toList()),
    )
}

internal data class GoEvaluatedEdgeCondition(
    val ruleCondition: GoRuleConditionData,
    val accessedVarPosition: Map<MetavarAtom, GoRegisterVarPosition>,
)

internal data class GoRegisterVarPosition(
    val varName: MetavarAtom,
    val positions: MutableSet<GoStarredPosition>,
)

internal data class GoStarredPosition(
    val position: PositionBaseWithModifiers,
    val star: Boolean,
)

data class FieldModifierCtx(
    val resultModifiers: List<PositionModifier>?
)
