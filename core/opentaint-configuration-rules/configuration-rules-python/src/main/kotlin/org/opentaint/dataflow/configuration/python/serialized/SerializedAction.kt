package org.opentaint.dataflow.configuration.python.serialized

import kotlinx.serialization.Serializable

@Serializable
data class SerializedPythonTaintAssignAction(
    val kind: String,
    val pos: PythonPosition,
)

@Serializable
data class SerializedPythonTaintCleanAction(
    val taintKind: String,
    val pos: PythonPosition,
)

@Serializable
data class SerializedPythonTaintPassAction(
    val taintKind: String? = null,
    val from: PythonPosition,
    val to: PythonPosition,
)
