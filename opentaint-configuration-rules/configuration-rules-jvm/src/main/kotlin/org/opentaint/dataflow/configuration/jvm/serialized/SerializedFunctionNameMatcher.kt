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

@Serializable(with = SerializedFunctionNameSerializer::class)
sealed interface SerializedFunctionNameMatcher {
    val `package`: SerializedNameMatcher
    val `class`: SerializedNameMatcher
    val name: SerializedNameMatcher

    @Serializable(with = SimpleFunctionNameSerializer::class)
    data class Simple(
        override val `package`: SerializedNameMatcher.Simple,
        override val `class`: SerializedNameMatcher.Simple,
        override val name: SerializedNameMatcher.Simple,
    ) : SerializedFunctionNameMatcher

    @Serializable
    data class Complex(
        override val `package`: SerializedNameMatcher,
        override val `class`: SerializedNameMatcher,
        override val name: SerializedNameMatcher,
    ) : SerializedFunctionNameMatcher {
        fun simplify(): SerializedFunctionNameMatcher {
            if (
                `package` is SerializedNameMatcher.Simple
                && `class` is SerializedNameMatcher.Simple
                && name is SerializedNameMatcher.Simple
            ) {
                return Simple(`package`, `class`, name)
            }

            return this
        }
    }
}

class SerializedFunctionNameSerializer :
    YamlContentPolymorphicSerializer<SerializedFunctionNameMatcher>(SerializedFunctionNameMatcher::class) {
    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<SerializedFunctionNameMatcher> =
        when (node) {
            is YamlScalar -> SerializedFunctionNameMatcher.Simple.serializer()
            is YamlMap -> SerializedFunctionNameMatcher.Complex.serializer()
            else -> error("Unexpected node: $node")
        }
}

object SimpleFunctionNameSerializer : KSerializer<SerializedFunctionNameMatcher.Simple> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("functionName", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SerializedFunctionNameMatcher.Simple {
        val serializedName = decoder.decodeString()

        val functionName = serializedName.substringAfterLast('#', missingDelimiterValue = "")

        val fullClassName = serializedName.substringBeforeLast('#')
        val className = fullClassName.substringAfterLast('.', missingDelimiterValue = "")
        val packageName = fullClassName.substringBeforeLast('.', missingDelimiterValue = "")

        return SerializedFunctionNameMatcher.Simple(
            `package` = SerializedNameMatcher.Simple(packageName),
            `class` = SerializedNameMatcher.Simple(className),
            name = SerializedNameMatcher.Simple(functionName)
        )
    }

    override fun serialize(encoder: Encoder, value: SerializedFunctionNameMatcher.Simple) {
        encoder.encodeString("${value.`package`.value}.${value.`class`.value}#${value.name.value}")
    }
}
