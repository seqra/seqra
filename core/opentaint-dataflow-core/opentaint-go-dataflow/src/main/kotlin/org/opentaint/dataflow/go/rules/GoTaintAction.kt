package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintAssignAction

sealed interface GoTaintAction : CommonTaintAction

sealed interface ActionPosition {
    data class Exact(val position: Position) : ActionPosition
    data class AnyAccessorAfter(val position: Position) : ActionPosition
}

data class CopyTaintMark(
    val mark: String,
    val from: ActionPosition,
    val to: ActionPosition,
) : GoTaintAction

data class CopyData(
    val from: ActionPosition,
    val to: ActionPosition,
) : GoTaintAction

data class RemoveMark(
    val mark: String,
    val pos: ActionPosition,
) : GoTaintAction

data class RemoveAllMarks(
    val pos: ActionPosition,
) : GoTaintAction

data class GoAssignAction(
    val mark: String,
    val pos: ActionPosition,
) : GoTaintAction, CommonTaintAssignAction
