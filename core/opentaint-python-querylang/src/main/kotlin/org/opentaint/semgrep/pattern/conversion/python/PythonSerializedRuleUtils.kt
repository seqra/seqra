package org.opentaint.semgrep.pattern.conversion.python

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.python.serialized.PythonPosition
import org.opentaint.dataflow.configuration.python.serialized.PythonPositionBase
import org.opentaint.dataflow.configuration.python.serialized.PythonPositionModifier
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonCondition
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintAssignAction
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintCleanAction
import org.opentaint.semgrep.pattern.Mark.GeneratedMark
import org.opentaint.semgrep.pattern.conversion.PythonLanguageStrategy

internal const val ANY_PYTHON_FUNCTION = ".*"

internal fun SerializedPythonCondition.nullIfTrue(): SerializedPythonCondition? =
    takeUnless { it == SerializedPythonCondition.True }

internal fun PositionBaseWithModifiers.toPythonPosition(): PythonPosition = when (this) {
    is PositionBaseWithModifiers.BaseOnly -> PythonPosition.BaseOnly(base.toPythonPositionBase())
    is PositionBaseWithModifiers.WithModifiers ->
        PythonPosition.WithModifiers(base.toPythonPositionBase(), modifiers.map { it.toPythonPositionModifier() })
}

private fun PositionBase.toPythonPositionBase(): PythonPositionBase = when (this) {
    is PositionBase.Argument -> PythonPositionBase.Argument(idx)
    is PositionBase.AnyArgument ->
        PythonLanguageStrategy.kwargClassifierNameOrNull(classifier)?.let { PythonPositionBase.KwArgument(it) }
            ?: PythonPositionBase.Argument(null)
    PositionBase.This -> PythonPositionBase.This
    PositionBase.Result -> PythonPositionBase.Result
    is PositionBase.ClassStatic -> PythonPositionBase.ClassRef(className)
}

private fun PositionModifier.toPythonPositionModifier(): PythonPositionModifier = when (this) {
    PositionModifier.ArrayElement -> PythonPositionModifier.ArrayElement
    is PositionModifier.Field -> PythonPositionModifier.Field(fieldName)
    PositionModifier.AnyField -> error("Python rules have no any-field position modifier")
}

internal fun GeneratedMark.mkPythonContainsMark(pos: PositionBaseWithModifiers): SerializedPythonCondition.ContainsMark =
    SerializedPythonCondition.ContainsMark(taintMarkStr(), pos.toPythonPosition())

internal fun GeneratedMark.mkPythonContainsMarkOnAnyAccessor(pos: PositionBaseWithModifiers): SerializedPythonCondition.ContainsMarkOnAnyAccessor =
    SerializedPythonCondition.ContainsMarkOnAnyAccessor(taintMarkStr(), pos.toPythonPosition())

internal fun GeneratedMark.mkPythonAssignMark(pos: PositionBaseWithModifiers): SerializedPythonTaintAssignAction =
    SerializedPythonTaintAssignAction(kind = taintMarkStr(), pos = pos.toPythonPosition())

internal fun GeneratedMark.mkPythonCleanMark(pos: PositionBaseWithModifiers): SerializedPythonTaintCleanAction =
    SerializedPythonTaintCleanAction(taintKind = taintMarkStr(), pos = pos.toPythonPosition())
