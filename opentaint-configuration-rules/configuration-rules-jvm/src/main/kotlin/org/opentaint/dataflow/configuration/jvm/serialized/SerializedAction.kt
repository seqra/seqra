package org.opentaint.dataflow.configuration.jvm.serialized

import kotlinx.serialization.Serializable

sealed interface SerializedAction

@Serializable
data class SerializedTaintAssignAction(
    val kind: String,
    val annotatedWith: SerializedTypeNameMatcher? = null,
    val pos: PositionBaseWithModifiers,
): SerializedAction

@Serializable
data class SerializedTaintCleanAction(
    val taintKind: String? = null,
    val pos: PositionBaseWithModifiers,
): SerializedAction

@Serializable
data class SerializedTaintPassAction(
    val taintKind: String? = null,
    val from: PositionBaseWithModifiers,
    val to: PositionBaseWithModifiers,
): SerializedAction
