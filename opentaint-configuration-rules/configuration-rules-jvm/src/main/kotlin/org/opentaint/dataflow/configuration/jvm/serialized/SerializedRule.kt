package org.opentaint.dataflow.configuration.jvm.serialized

import kotlinx.serialization.Serializable
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta

sealed interface SerializedRule {
    val function: SerializedFunctionNameMatcher
    val signature: SerializedSignatureMatcher?
    val overrides: Boolean

    sealed interface SourceRule : SerializedRule {
        val condition: SerializedCondition?
        val taint: List<SerializedTaintAssignAction>
    }

    @Serializable
    data class EntryPoint(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val taint: List<SerializedTaintAssignAction>
    ) : SourceRule

    @Serializable
    data class Source(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val taint: List<SerializedTaintAssignAction>
    ) : SourceRule

    @Serializable
    data class Cleaner(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        val condition: SerializedCondition? = null,
        val cleans: List<SerializedTaintCleanAction>
    ) : SerializedRule

    @Serializable
    data class PassThrough(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        val condition: SerializedCondition? = null,
        val copy: List<SerializedTaintPassAction>
    ) : SerializedRule

    @Serializable
    data class SinkMetaData(
        val cwe: List<Int>? = null,
        val note: String? = null,
        val severity: CommonTaintConfigurationSinkMeta.Severity? = null,
    )

    sealed interface SinkRule : SerializedRule {
        val condition: SerializedCondition?
        val id: String?
        val meta: SinkMetaData?
    }

    @Serializable
    data class Sink(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val id: String? = null,
        private val cwe: List<Int>? = null, // todo: remove
        private val note: String? = null,
        override val meta: SinkMetaData? = SinkMetaData(cwe, note),
    ) : SinkRule

    @Serializable
    data class MethodExitSink(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val id: String? = null,
        private val cwe: List<Int>? = null, // todo: remove
        private val note: String? = null,
        override val meta: SinkMetaData? = SinkMetaData(cwe, note),
    ) : SinkRule

    @Serializable
    data class MethodEntrySink(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        override val condition: SerializedCondition? = null,
        override val id: String? = null,
        private val cwe: List<Int>? = null, // todo: remove
        private val note: String? = null,
        override val meta: SinkMetaData? = SinkMetaData(cwe, note),
    ) : SinkRule
}

sealed interface SerializedFieldRule {
    val className: SerializedNameMatcher
    val fieldName: String

    @Serializable
    data class SerializedStaticFieldSource(
        override val className: SerializedNameMatcher,
        override val fieldName: String,
        val condition: SerializedCondition?,
        val taint: List<SerializedTaintAssignAction>
    ): SerializedFieldRule
}
