package org.opentaint.dataflow.jvm.ap.ifds.sarif

import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.Version
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opentaint.dataflow.jvm.util.SequenceSerializer

private const val SARIF_SCHEMA =
    "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"

@Serializable
data class LazySarifReport(
    @SerialName("\$schema")
    val schema: String = SARIF_SCHEMA,
    val version: Version = Version.The210,
    val runs: List<LazyToolRunReport>
)

@Serializable
data class LazyToolRunReport(
    val tool: Tool,
    @Serializable(with = ResultSequenceSerializer::class)
    val results: Sequence<Result>
)

class ResultSequenceSerializer : KSerializer<Sequence<Result>> {
    private val serializer = SequenceSerializer(Result.serializer())
    override val descriptor: SerialDescriptor get() = serializer.descriptor
    override fun deserialize(decoder: Decoder): Sequence<Result> = serializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Sequence<Result>) {
        serializer.serialize(encoder, value)
    }
}
