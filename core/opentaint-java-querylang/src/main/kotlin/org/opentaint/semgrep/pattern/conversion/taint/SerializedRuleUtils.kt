package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.Companion.mkFalse
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAnyFieldAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.semgrep.pattern.Mark.GeneratedMark

fun PositionBase.base(): PositionBaseWithModifiers =
    PositionBaseWithModifiers.BaseOnly(this)

fun anyName() = SerializedSimpleNameMatcher.Pattern(".*")

fun anyFunction() = SerializedFunctionNameMatcher.Complex(anyName(), anyName(), anyName())

fun SerializedFunctionNameMatcher.matchAnything(): Boolean =
    `class` == anyName() && `package` == anyName() && name == anyName()

fun serializedConditionOr(args: List<SerializedCondition>): SerializedCondition {
    val result = mutableSetOf<SerializedCondition>()
    for (arg in args) {
        if (arg is SerializedCondition.Or) {
            result.addAll(arg.anyOf)
            continue
        }

        if (arg is SerializedCondition.True) return SerializedCondition.True

        if (arg.isFalse()) continue

        result.add(arg)
    }

    return when (result.size) {
        0 -> mkFalse()
        1 -> result.single()
        else -> SerializedCondition.Or(result.toList())
    }
}

fun GeneratedMark.mkContainsMark(pos: PositionBaseWithModifiers) =
    SerializedCondition.ContainsMark(taintMarkStr(), pos)

fun GeneratedMark.mkContainsMarkOnAny(pos: PositionBaseWithModifiers) =
    SerializedCondition.ContainsMarkAnyField(taintMarkStr(), pos)

fun GeneratedMark.mkAssignMark(pos: PositionBaseWithModifiers) =
    SerializedTaintAssignAction(taintMarkStr(), pos = pos)

fun GeneratedMark.mkAssignMarkAnyField(pos: PositionBaseWithModifiers) =
    SerializedTaintAssignAnyFieldAction(taintMarkStr(), posAnyField = pos)

fun GeneratedMark.mkCleanMark(pos: PositionBaseWithModifiers) =
    SerializedTaintCleanAction(taintMarkStr(), pos = pos)
