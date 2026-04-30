package org.opentaint.dataflow.configuration.jvm.serialized

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class SerializedArgMatcher(val index: Int, val type: SerializedTypeNameMatcher)

@Serializable(with = SerializedSignatureMatcherSerializer::class)
sealed interface SerializedSignatureMatcher {
    @Serializable(with = SimpleSignatureSerializer::class)
    data class Simple(
        val args: List<SerializedSimpleNameMatcher.Simple>,
        val `return`: SerializedSimpleNameMatcher.Simple
    ) : SerializedSignatureMatcher

    @Serializable
    data class Partial(
        val params: List<SerializedArgMatcher>? = null,
    ) : SerializedSignatureMatcher
}

object SimpleSignatureSerializer : KSerializer<SerializedSignatureMatcher.Simple> {
    private val signaturePattern = Regex("""\((.*)\)\s*(.*)""")

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("signature", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SerializedSignatureMatcher.Simple) {
        val args = value.args.joinToString(prefix = "(", postfix = ")") { it.value }
        val ret = value.`return`.value
        encoder.encodeString("$args $ret")
    }

    override fun deserialize(decoder: Decoder): SerializedSignatureMatcher.Simple {
        val signatureStr = decoder.decodeString()
        val matchedSignature = signaturePattern.matchEntire(signatureStr)
            ?: error("Unexpected signature: $signatureStr")

        val (paramsStr, returnTypeStr) = matchedSignature.destructured
        val paramsStrSplit = if (paramsStr.trim().isBlank()) emptyList() else paramsStr.split(',')

        return SerializedSignatureMatcher.Simple(
            paramsStrSplit.map { SerializedSimpleNameMatcher.Simple(it.trim()) },
            SerializedSimpleNameMatcher.Simple(returnTypeStr.trim())
        )
    }
}

class SerializedSignatureMatcherSerializer :
    YamlContentPolymorphicSerializer<SerializedSignatureMatcher>(SerializedSignatureMatcher::class) {
    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<SerializedSignatureMatcher> =
        when (node) {
            is YamlScalar -> SerializedSignatureMatcher.Simple.serializer()
            is YamlMap -> SerializedSignatureMatcher.Partial.serializer()
            else -> error("Unexpected node: $node")
        }
}
