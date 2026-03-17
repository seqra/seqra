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

data class AssignMark(
    val mark: TaintMark,
    val position: Position,
) : Action, CommonTaintAssignAction

data class RemoveAllMarks(
    val position: Position,
) : Action

data class RemoveMark(
    val mark: TaintMark,
    val position: Position,
) : Action
