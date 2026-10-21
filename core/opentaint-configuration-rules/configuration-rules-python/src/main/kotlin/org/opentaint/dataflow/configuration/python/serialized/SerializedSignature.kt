package org.opentaint.dataflow.configuration.python.serialized

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SerializedPythonSignatureMatcherSerializer::class)
data class SerializedPythonSignatureMatcher(
    val args: List<String>,
    val `return`: String,
) {
    fun serializedStr(): String =
        "(${args.joinToString(", ")}) ${`return`}"

    companion object {
        private val pattern = Regex("""\((.*)\)\s*(.*)""")

        fun deserialize(str: String): SerializedPythonSignatureMatcher {
            val match = pattern.matchEntire(str) ?: error("Unexpected signature: $str")
            val (paramsStr, returnStr) = match.destructured
            val args = if (paramsStr.trim().isBlank()) emptyList() else paramsStr.split(',').map { it.trim() }
            return SerializedPythonSignatureMatcher(args, returnStr.trim())
        }
    }
}

object SerializedPythonSignatureMatcherSerializer : KSerializer<SerializedPythonSignatureMatcher> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("python.signature", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SerializedPythonSignatureMatcher =
        SerializedPythonSignatureMatcher.deserialize(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: SerializedPythonSignatureMatcher) {
        encoder.encodeString(value.serializedStr())
    }
}
