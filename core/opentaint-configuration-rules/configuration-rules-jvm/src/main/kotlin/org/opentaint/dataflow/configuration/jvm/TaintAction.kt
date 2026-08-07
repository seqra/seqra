package org.opentaint.dataflow.configuration.jvm

import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintAssignAction

sealed interface Action: CommonTaintAction

sealed interface ActionPosition {
    data class Exact(val position: Position) : ActionPosition
    data class AnyAccessorAfter(val position: Position): ActionPosition
}

data class CopyAllMarks(
    val from: ActionPosition,
    val to: ActionPosition,
) : Action

data class CopyMark(
    val mark: TaintMark,
    val from: ActionPosition,
    val to: ActionPosition,
) : Action

data class AssignMark(
    val mark: TaintMark,
    val position: ActionPosition,
) : Action, CommonTaintAssignAction

data class RemoveAllMarks(
    val position: ActionPosition,
) : Action

data class RemoveMark(
    val mark: TaintMark,
    val position: ActionPosition,
) : Action
