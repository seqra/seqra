package org.opentaint.ir.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SerializedTaintConfigurationItem {
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
    override val methodInfo: FunctionMatcher,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("MethodSource")
data class SerializedTaintMethodSource(
    override val methodInfo: FunctionMatcher,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("MethodSink")
data class SerializedTaintMethodSink(
    val ruleNote: String,
    val cwe: List<Int>,
    override val methodInfo: FunctionMatcher,
    val condition: Condition
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("PassThrough")
data class SerializedTaintPassThrough(
    override val methodInfo: FunctionMatcher,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("Cleaner")
data class SerializedTaintCleaner(
    override val methodInfo: FunctionMatcher,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem
