package org.opentaint.dataflow.configuration.python

import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintAssignAction

sealed interface Action: CommonTaintAction

data class TaintAssignAction(
    val mark: TaintMark,
    val pos: Position,
) : Action, CommonTaintAssignAction

data class TaintCleanAction(
    val mark: TaintMark,
    val pos: Position,
) : Action

data class TaintPassAction(
    val mark: TaintMark?,
    val from: Position,
    val to: Position,
) : Action
