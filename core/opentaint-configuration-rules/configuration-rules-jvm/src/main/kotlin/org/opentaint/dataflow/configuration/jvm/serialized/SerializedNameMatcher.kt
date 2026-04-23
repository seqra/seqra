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

@Serializable(with = SerializedTypeNameMatcherSerializer::class)
sealed interface SerializedTypeNameMatcher {
    @Serializable
    data class ClassPattern(
        val `package`: SerializedSimpleNameMatcher,
        val `class`: SerializedSimpleNameMatcher,
        val typeArgs: List<SerializedTypeNameMatcher> = emptyList()
    ) : SerializedTypeNameMatcher

    @Serializable
    data class Array(val element: SerializedTypeNameMatcher) : SerializedTypeNameMatcher

    /**
     * Matches only an unbounded Java wildcard (`?`) at a type-argument slot.
     * Distinct from an "any" [ClassPattern] so a pattern like `Foo<?>` does not
     * match a concrete parameterization like `Foo<String>`.
     */
    @Serializable
    data object Wildcard : SerializedTypeNameMatcher
}

@Serializable(with = SimpleNameMatcherSerializer::class)
sealed interface SerializedSimpleNameMatcher : SerializedTypeNameMatcher {
    @Serializable
    data class Pattern(val pattern: String) : SerializedSimpleNameMatcher


    @Serializable(with = SimpleNameMatcherSimpleSerializer::class)
    data class Simple(val value: String) : SerializedSimpleNameMatcher
}

class SerializedTypeNameMatcherSerializer :
    YamlContentPolymorphicSerializer<SerializedTypeNameMatcher>(SerializedTypeNameMatcher::class) {
    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<SerializedTypeNameMatcher> = when (node) {
        is YamlMap -> {
            val classProperty = node.getKey("class")
            val elementProperty = node.getKey("element")

            if (classProperty != null) {
                SerializedTypeNameMatcher.ClassPattern.serializer()
            } else if (elementProperty != null) {
                SerializedTypeNameMatcher.Array.serializer()
            } else {
                SerializedSimpleNameMatcher.serializer()
            }
        }

        else -> SerializedSimpleNameMatcher.serializer()
    }
}

class SimpleNameMatcherSerializer :
    YamlContentPolymorphicSerializer<SerializedSimpleNameMatcher>(SerializedSimpleNameMatcher::class) {
    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<SerializedSimpleNameMatcher> =
        when (node) {
            is YamlScalar -> SerializedSimpleNameMatcher.Simple.serializer()
            is YamlMap -> {
                val patternProperty = node.getKey("pattern")
                if (patternProperty != null) {
                    SerializedSimpleNameMatcher.Pattern.serializer()
                } else {
                    error("Unexpected node: $node")
                }
            }

            else -> error("Unexpected node: $node")
        }
}

class SimpleNameMatcherSimpleSerializer : KSerializer<SerializedSimpleNameMatcher.Simple> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("value", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SerializedSimpleNameMatcher.Simple =
        SerializedSimpleNameMatcher.Simple(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: SerializedSimpleNameMatcher.Simple) {
        encoder.encodeString(value.value)
    }
}
