package org.opentaint.dataflow.configuration.jvm.serialized

import kotlinx.serialization.Serializable
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta

interface ItemInfo

sealed interface SerializedItem {
    val info: ItemInfo?
}

sealed interface SourceRule: SerializedItem {
    val condition: SerializedCondition?
    val taint: List<SerializedTaintAssignAction>
}

sealed interface SinkRule : SerializedItem {
    val condition: SerializedCondition?
    val trackFactsReachAnalysisEnd: List<SerializedTaintAssignAction>?
    val id: String?
    val meta: SinkMetaData?
}

@Serializable
data class SinkMetaData(
    val cwe: List<Int>? = null,
    val note: String? = null,
    val severity: CommonTaintConfigurationSinkMeta.Severity? = null,
)

sealed interface SerializedRule: SerializedItem {
    val function: SerializedFunctionNameMatcher
    val signature: SerializedSignatureMatcher?
    val overrides: Boolean

    @Serializable
    data class EntryPoint(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val taint: List<SerializedTaintAssignAction>,
        override val info: ItemInfo? = null
    ) : SourceRule, SerializedRule

    @Serializable
    data class Source(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val taint: List<SerializedTaintAssignAction>,
        override val info: ItemInfo? = null
    ) : SourceRule, SerializedRule

    @Serializable
    data class Cleaner(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        val condition: SerializedCondition? = null,
        val cleans: List<SerializedTaintCleanAction>,
        override val info: ItemInfo? = null
    ) : SerializedRule

    @Serializable
    data class PassThrough(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        val condition: SerializedCondition? = null,
        val copy: List<SerializedTaintPassAction>,
        override val info: ItemInfo? = null
    ) : SerializedRule

    @Serializable
    data class Sink(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val trackFactsReachAnalysisEnd: List<SerializedTaintAssignAction>? = null,
        override val id: String? = null,
        private val cwe: List<Int>? = null, // todo: remove
        private val note: String? = null,
        override val meta: SinkMetaData? = SinkMetaData(cwe, note),
        override val info: ItemInfo? = null
    ) : SinkRule, SerializedRule

    @Serializable
    data class MethodExitSink(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val trackFactsReachAnalysisEnd: List<SerializedTaintAssignAction>? = null,
        override val id: String? = null,
        private val cwe: List<Int>? = null, // todo: remove
        private val note: String? = null,
        override val meta: SinkMetaData? = SinkMetaData(cwe, note),
        override val info: ItemInfo? = null
    ) : SinkRule, SerializedRule

    @Serializable
    data class MethodEntrySink(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val trackFactsReachAnalysisEnd: List<SerializedTaintAssignAction>? = null,
        override val id: String? = null,
        private val cwe: List<Int>? = null, // todo: remove
        private val note: String? = null,
        override val meta: SinkMetaData? = SinkMetaData(cwe, note),
        override val info: ItemInfo? = null
    ) : SinkRule, SerializedRule

    @Serializable
    data class MethodExitSource(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val taint: List<SerializedTaintAssignAction>,
        override val info: ItemInfo? = null
    ) : SourceRule, SerializedRule
}

sealed interface SerializedFieldRule: SerializedItem {
    val className: SerializedNameMatcher
    val fieldName: String

    @Serializable
    data class SerializedStaticFieldSource(
        override val className: SerializedNameMatcher,
        override val fieldName: String,
        override val condition: SerializedCondition?,
        override val taint: List<SerializedTaintAssignAction>,
        override val info: ItemInfo? = null
    ): SerializedFieldRule, SourceRule
}

@Suppress("UNCHECKED_CAST")
inline fun <S : SerializedRule> S.modifyCondition(mapper: (SerializedCondition?) -> SerializedCondition?): S {
    return when (this) {
        is SerializedRule.Cleaner -> copy(condition = mapper(condition))
        is SerializedRule.EntryPoint -> copy(condition = mapper(condition))
        is SerializedRule.MethodExitSource -> copy(condition = mapper(condition))
        is SerializedRule.MethodEntrySink -> copy(condition = mapper(condition))
        is SerializedRule.MethodExitSink -> copy(condition = mapper(condition))
        is SerializedRule.PassThrough -> copy(condition = mapper(condition))
        is SerializedRule.Sink -> copy(condition = mapper(condition))
        is SerializedRule.Source -> copy(condition = mapper(condition))
        else -> error("impossible")
    } as S
}
