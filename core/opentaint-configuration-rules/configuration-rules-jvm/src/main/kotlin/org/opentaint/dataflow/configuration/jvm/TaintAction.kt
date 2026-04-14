package org.opentaint.dataflow.configuration.jvm

import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintAssignAction

sealed interface Action: CommonTaintAction

data class CopyAllMarks(
    val from: Position,
    val to: Position,
) : Action

data class CopyMark(
    val mark: TaintMark,
    val from: Position,
    val to: Position,
) : Action

sealed interface AssignAction : Action, CommonTaintAssignAction {
    val mark: TaintMark
}

data class AssignMark(
    override val mark: TaintMark,
    val position: Position,
) : AssignAction

data class AssignMarkAnyField(
    override val mark: TaintMark,
    val barePosition: Position,
) : AssignAction {
    val positionWithAny: Position = PositionWithAccess(barePosition, PositionAccessor.AnyFieldAccessor)
}

data class RemoveAllMarks(
    val position: Position,
) : Action

data class RemoveMark(
    val mark: TaintMark,
    val position: Position,
) : Action
