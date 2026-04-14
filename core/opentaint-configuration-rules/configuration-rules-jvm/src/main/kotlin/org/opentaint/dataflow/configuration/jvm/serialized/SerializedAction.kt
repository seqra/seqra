package org.opentaint.dataflow.configuration.jvm.serialized

import kotlinx.serialization.Serializable

sealed interface SerializedAction

sealed interface SerializedAssignAction : SerializedAction {
    val kind: String
    val annotatedWith: SerializedTypeNameMatcher?
}

@Serializable
data class SerializedTaintAssignAction(
    override val kind: String,
    override val annotatedWith: SerializedTypeNameMatcher? = null,
    val pos: PositionBaseWithModifiers,
): SerializedAssignAction

@Serializable
data class SerializedTaintAssignAnyFieldAction(
    override val kind: String,
    override val annotatedWith: SerializedTypeNameMatcher? = null,
    val posAnyField: PositionBaseWithModifiers,
): SerializedAssignAction

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
