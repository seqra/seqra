package org.opentaint.ir.taint.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SerializedTaintConfigurationItem {
    @SerialName("methodInfo")
    val methodInfo: FunctionMatcher

    fun updateMethodInfo(updatedMethodInfo: FunctionMatcher): SerializedTaintConfigurationItem =
        when (this) {
            is SerializedTaintCleaner -> copy(methodInfo = updatedMethodInfo)
            is SerializedTaintEntryPointSource -> copy(methodInfo = updatedMethodInfo)
            is SerializedTaintMethodSink -> copy(methodInfo = updatedMethodInfo)
            is SerializedTaintMethodSource -> copy(methodInfo = updatedMethodInfo)
            is SerializedTaintPassThrough -> copy(methodInfo = updatedMethodInfo)
        }
}

@Serializable
@SerialName("EntryPointSource")
data class SerializedTaintEntryPointSource(
    @SerialName("methodInfo") override val methodInfo: FunctionMatcher,
    @SerialName("condition") val condition: Condition,
    @SerialName("actionsAfter") val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("MethodSource")
data class SerializedTaintMethodSource(
    @SerialName("methodInfo") override val methodInfo: FunctionMatcher,
    @SerialName("condition") val condition: Condition,
    @SerialName("actionsAfter") val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("MethodSink")
data class SerializedTaintMethodSink(
    @SerialName("ruleNote") val ruleNote: String,
    @SerialName("cwe") val cwe: List<Int>,
    @SerialName("methodInfo") override val methodInfo: FunctionMatcher,
    @SerialName("condition") val condition: Condition,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("PassThrough")
data class SerializedTaintPassThrough(
    @SerialName("methodInfo") override val methodInfo: FunctionMatcher,
    @SerialName("condition") val condition: Condition,
    @SerialName("actionsAfter") val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("Cleaner")
data class SerializedTaintCleaner(
    @SerialName("methodInfo") override val methodInfo: FunctionMatcher,
    @SerialName("condition") val condition: Condition,
    @SerialName("actionsAfter") val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem
