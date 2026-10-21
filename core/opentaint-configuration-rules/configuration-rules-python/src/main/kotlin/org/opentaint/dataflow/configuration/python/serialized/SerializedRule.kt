package org.opentaint.dataflow.configuration.python.serialized

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class PythonSinkMetaData(
    val cwe: List<Int>? = null,
    val note: String? = null,
)

sealed interface SerializedPythonRule {
    val target: PythonTarget

    val info: ItemInfo?

    val serializedId: String?
}

sealed interface SerializedPythonSourceRule : SerializedPythonRule {
    val condition: SerializedPythonCondition?
    val taint: List<SerializedPythonTaintAssignAction>
}

@Serializable(with = SerializedPythonEntryPointSerializer::class)
data class SerializedPythonEntryPointSource(
    override val target: PythonTarget,
    override val condition: SerializedPythonCondition? = null,
    override val taint: List<SerializedPythonTaintAssignAction>,
    override val info: ItemInfo? = null,
    override val serializedId: String? = null,
) : SerializedPythonSourceRule

@Serializable(with = SerializedPythonSourceSerializer::class)
data class SerializedPythonSource(
    override val target: PythonTarget,
    override val condition: SerializedPythonCondition? = null,
    override val taint: List<SerializedPythonTaintAssignAction>,
    override val info: ItemInfo? = null,
    override val serializedId: String? = null,
) : SerializedPythonSourceRule

@Serializable(with = SerializedPythonSinkSerializer::class)
data class SerializedPythonSink(
    override val target: PythonTarget,
    val condition: SerializedPythonCondition? = null,
    val trackFactsReachAnalysisEnd: List<SerializedPythonTaintAssignAction>? = null,
    val meta: PythonSinkMetaData? = null,
    override val info: ItemInfo? = null,
    override val serializedId: String? = null,
) : SerializedPythonRule

@Serializable(with = SerializedPythonExitSinkSerializer::class)
data class SerializedPythonExitSink(
    override val target: PythonTarget,
    val condition: SerializedPythonCondition? = null,
    val trackFactsReachAnalysisEnd: List<SerializedPythonTaintAssignAction>? = null,
    val meta: PythonSinkMetaData? = null,
    override val info: ItemInfo? = null,
    override val serializedId: String? = null,
) : SerializedPythonRule

@Serializable(with = SerializedPythonPassThroughSerializer::class)
data class SerializedPythonPassThrough(
    override val target: PythonTarget,
    val condition: SerializedPythonCondition? = null,
    val copy: List<SerializedPythonTaintPassAction>,
    override val info: ItemInfo? = null,
    override val serializedId: String? = null,
) : SerializedPythonRule

@Serializable(with = SerializedPythonCleanerSerializer::class)
data class SerializedPythonCleaner(
    override val target: PythonTarget,
    val condition: SerializedPythonCondition? = null,
    val cleans: List<SerializedPythonTaintCleanAction>,
    val `for`: String? = null,
    override val info: ItemInfo? = null,
    override val serializedId: String? = null,
) : SerializedPythonRule

// region Surrogates

private fun buildTarget(function: String?, attribute: String?, signature: SerializedPythonSignatureMatcher?): PythonTarget {
    return when {
        function != null && attribute != null ->
            error("Rule cannot specify both 'function:' and 'attribute:'")
        function != null -> PythonTarget.Function(function, signature)
        attribute != null -> {
            require(signature == null) { "'signature:' is only valid with 'function:'" }
            PythonTarget.Attribute(attribute)
        }
        else -> error("Rule must specify 'function:' or 'attribute:'")
    }
}

@Serializable
private data class EntryPointSurrogate(
    val function: String? = null,
    val attribute: String? = null,
    val signature: SerializedPythonSignatureMatcher? = null,
    val condition: SerializedPythonCondition? = null,
    val taint: List<SerializedPythonTaintAssignAction>,
)

object SerializedPythonEntryPointSerializer : KSerializer<SerializedPythonEntryPointSource> {
    override val descriptor: SerialDescriptor = EntryPointSurrogate.serializer().descriptor
    override fun deserialize(decoder: Decoder): SerializedPythonEntryPointSource {
        val raw = decoder.decodeSerializableValue(EntryPointSurrogate.serializer())
        return SerializedPythonEntryPointSource(
            target = buildTarget(raw.function, raw.attribute, raw.signature),
            condition = raw.condition,
            taint = raw.taint,
        )
    }
    override fun serialize(encoder: Encoder, value: SerializedPythonEntryPointSource) = unsupported()
}

@Serializable
private data class SourceSurrogate(
    val function: String? = null,
    val attribute: String? = null,
    val signature: SerializedPythonSignatureMatcher? = null,
    val condition: SerializedPythonCondition? = null,
    val taint: List<SerializedPythonTaintAssignAction>,
)

object SerializedPythonSourceSerializer : KSerializer<SerializedPythonSource> {
    override val descriptor: SerialDescriptor = SourceSurrogate.serializer().descriptor
    override fun deserialize(decoder: Decoder): SerializedPythonSource {
        val raw = decoder.decodeSerializableValue(SourceSurrogate.serializer())
        return SerializedPythonSource(
            target = buildTarget(raw.function, raw.attribute, raw.signature),
            condition = raw.condition,
            taint = raw.taint,
        )
    }
    override fun serialize(encoder: Encoder, value: SerializedPythonSource) = unsupported()
}

@Serializable
private data class SinkSurrogate(
    val function: String? = null,
    val attribute: String? = null,
    val signature: SerializedPythonSignatureMatcher? = null,
    val condition: SerializedPythonCondition? = null,
    val trackFactsReachAnalysisEnd: List<SerializedPythonTaintAssignAction>? = null,
    val cwe: List<Int>? = null,
    val note: String? = null,
)

object SerializedPythonSinkSerializer : KSerializer<SerializedPythonSink> {
    override val descriptor: SerialDescriptor = SinkSurrogate.serializer().descriptor
    override fun deserialize(decoder: Decoder): SerializedPythonSink {
        val raw = decoder.decodeSerializableValue(SinkSurrogate.serializer())
        return SerializedPythonSink(
            target = buildTarget(raw.function, raw.attribute, raw.signature),
            condition = raw.condition,
            trackFactsReachAnalysisEnd = raw.trackFactsReachAnalysisEnd,
            meta = PythonSinkMetaData(raw.cwe, raw.note),
        )
    }
    override fun serialize(encoder: Encoder, value: SerializedPythonSink) = unsupported()
}

object SerializedPythonExitSinkSerializer : KSerializer<SerializedPythonExitSink> {
    override val descriptor: SerialDescriptor = SinkSurrogate.serializer().descriptor
    override fun deserialize(decoder: Decoder): SerializedPythonExitSink {
        val raw = decoder.decodeSerializableValue(SinkSurrogate.serializer())
        return SerializedPythonExitSink(
            target = buildTarget(raw.function, raw.attribute, raw.signature),
            condition = raw.condition,
            trackFactsReachAnalysisEnd = raw.trackFactsReachAnalysisEnd,
            meta = PythonSinkMetaData(raw.cwe, raw.note),
        )
    }
    override fun serialize(encoder: Encoder, value: SerializedPythonExitSink) = unsupported()
}

@Serializable
private data class PassThroughSurrogate(
    val function: String? = null,
    val attribute: String? = null,
    val signature: SerializedPythonSignatureMatcher? = null,
    val condition: SerializedPythonCondition? = null,
    val copy: List<SerializedPythonTaintPassAction>,
)

object SerializedPythonPassThroughSerializer : KSerializer<SerializedPythonPassThrough> {
    override val descriptor: SerialDescriptor = PassThroughSurrogate.serializer().descriptor
    override fun deserialize(decoder: Decoder): SerializedPythonPassThrough {
        val raw = decoder.decodeSerializableValue(PassThroughSurrogate.serializer())
        return SerializedPythonPassThrough(
            target = buildTarget(raw.function, raw.attribute, raw.signature),
            condition = raw.condition,
            copy = raw.copy,
        )
    }
    override fun serialize(encoder: Encoder, value: SerializedPythonPassThrough) = unsupported()
}

@Serializable
private data class CleanerSurrogate(
    val function: String? = null,
    val attribute: String? = null,
    val signature: SerializedPythonSignatureMatcher? = null,
    val condition: SerializedPythonCondition? = null,
    val cleans: List<SerializedPythonTaintCleanAction>,
    val `for`: String? = null,
)

object SerializedPythonCleanerSerializer : KSerializer<SerializedPythonCleaner> {
    override val descriptor: SerialDescriptor = CleanerSurrogate.serializer().descriptor
    override fun deserialize(decoder: Decoder): SerializedPythonCleaner {
        val raw = decoder.decodeSerializableValue(CleanerSurrogate.serializer())
        return SerializedPythonCleaner(
            target = buildTarget(raw.function, raw.attribute, raw.signature),
            condition = raw.condition,
            cleans = raw.cleans,
            `for` = raw.`for`,
        )
    }
    override fun serialize(encoder: Encoder, value: SerializedPythonCleaner) = unsupported()
}

private fun unsupported(): Nothing = error("Serialization of Python rules is not supported")
// endregion
