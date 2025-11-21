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

@Serializable(with = SerializedNameMatcherSerializer::class)
sealed interface SerializedNameMatcher {
    @Serializable
    data class Pattern(val pattern: String) : SerializedNameMatcher

    @Serializable
    data class ClassPattern(
        val `package`: SerializedNameMatcher,
        val `class`: SerializedNameMatcher
    ) : SerializedNameMatcher

    @Serializable(with = SimpleNameMatcherSerializer::class)
    data class Simple(val value: String) : SerializedNameMatcher
}

class SerializedNameMatcherSerializer :
    YamlContentPolymorphicSerializer<SerializedNameMatcher>(SerializedNameMatcher::class) {
    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<SerializedNameMatcher> = when (node) {
        is YamlScalar -> SerializedNameMatcher.Simple.serializer()
        is YamlMap -> {
            val patternProperty = node.getKey("pattern")
            if (patternProperty != null) {
                SerializedNameMatcher.Pattern.serializer()
            } else {
                SerializedNameMatcher.ClassPattern.serializer()
            }
        }
        else -> error("Unexpected node: $node")
    }
}

class SimpleNameMatcherSerializer : KSerializer<SerializedNameMatcher.Simple> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("value", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SerializedNameMatcher.Simple =
        SerializedNameMatcher.Simple(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: SerializedNameMatcher.Simple) {
        encoder.encodeString(value.value)
    }
}
