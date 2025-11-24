package org.opentaint.dataflow.configuration.jvm.serialized

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PositionBaseSerializer::class)
sealed interface PositionBase {
    data class ClassStatic(val className: String) : PositionBase
    data class Argument(val idx: Int?) : PositionBase
    data class AnyArgument(val classifier: String) : PositionBase
    data object This : PositionBase
    data object Result : PositionBase

    fun serializedStr(): String = when (this) {
        is Argument -> "arg(${idx ?: "*"})"
        is AnyArgument -> "any(${classifier})"
        Result -> "result"
        This -> "this"
        is ClassStatic -> "class($className)"
    }

    companion object {
        private val argPositionPattern = Regex("""arg\((\d+|\*)\)""")
        private val anyArgPositionPattern = Regex("""any\((.*)\)""")

        fun deserialize(str: String): PositionBase {
            return when (str) {
                "this" -> This
                "result" -> Result
                else -> {
                    val argMatch = argPositionPattern.matchEntire(str)
                    if (argMatch != null) {
                        val argKind = argMatch.groupValues[1]
                        val idx = if (argKind == "*") null else argKind.toInt()
                        return Argument(idx)
                    }

                    val anyArgMatch = anyArgPositionPattern.matchEntire(str)
                    if (anyArgMatch != null) {
                        val classifier = anyArgMatch.groupValues[1]
                        return AnyArgument(classifier)
                    }

                    error("Unexpected position: $str")
                }
            }
        }
    }
}

object PositionBaseSerializer : KSerializer<PositionBase> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("position", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PositionBase =
        PositionBase.deserialize(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: PositionBase) {
        encoder.encodeString(value.serializedStr())
    }
}

@Serializable(with = PositionBaseWithModifiersSerializer::class)
sealed interface PositionBaseWithModifiers {
    val base: PositionBase

    @Serializable(with = BaseOnlySerializer::class)
    data class BaseOnly(override val base: PositionBase) : PositionBaseWithModifiers

    @Serializable(with = WithModifiersSerializer::class)
    data class WithModifiers(override val base: PositionBase, val modifiers: List<PositionModifier>) :
        PositionBaseWithModifiers
}

class PositionBaseWithModifiersSerializer :
    YamlContentPolymorphicSerializer<PositionBaseWithModifiers>(PositionBaseWithModifiers::class) {

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor(serialName = super.descriptor.serialName, kind = SerialKind.CONTEXTUAL) {
            annotations = super.descriptor.annotations
        }

    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<PositionBaseWithModifiers> = when (node) {
        is YamlScalar -> PositionBaseWithModifiers.BaseOnly.serializer()
        is YamlList -> PositionBaseWithModifiers.WithModifiers.serializer()
        else -> error("Unexpected node: $node")
    }
}

class BaseOnlySerializer : KSerializer<PositionBaseWithModifiers.BaseOnly> {
    override val descriptor: SerialDescriptor
        get() = PositionBaseSerializer.descriptor

    override fun deserialize(decoder: Decoder): PositionBaseWithModifiers.BaseOnly =
        PositionBaseWithModifiers.BaseOnly(PositionBaseSerializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: PositionBaseWithModifiers.BaseOnly) {
        PositionBaseSerializer.serialize(encoder, value.base)
    }
}

class WithModifiersSerializer: KSerializer<PositionBaseWithModifiers.WithModifiers> {
    private val stringListSerializer = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor
        get() = stringListSerializer.descriptor

    override fun deserialize(decoder: Decoder): PositionBaseWithModifiers.WithModifiers {
        val stringValue = stringListSerializer.deserialize(decoder)
        check(stringValue.isNotEmpty()) { "Incorrect modifiers: $stringValue" }

        val base = PositionBase.deserialize(stringValue.first())
        val modifiers = stringValue.drop(1).map { PositionModifier.deserialize(it) }

        return PositionBaseWithModifiers.WithModifiers(base, modifiers)
    }

    override fun serialize(encoder: Encoder, value: PositionBaseWithModifiers.WithModifiers) {
        val stringValue = listOf(value.base.serializedStr()) + value.modifiers.map { it.serializedStr() }
        stringListSerializer.serialize(encoder, stringValue)
    }
}

sealed interface PositionModifier {
    data object ArrayElement : PositionModifier
    data object AnyField : PositionModifier
    data class Field(val className: String, val fieldName: String, val fieldType: String) : PositionModifier

    fun serializedStr(): String = when (this) {
        ArrayElement -> "[*]"
        AnyField -> ".*"
        is Field -> ".${className}#${fieldName}#${fieldType}"
    }

    companion object {
        private val fieldMatcher = Regex("""\.(.*)#(.*)#(.*)""")

        fun deserialize(str: String): PositionModifier = when (str) {
            "[*]" -> ArrayElement
            ".*" -> AnyField
            else -> {
                val fieldMatch = fieldMatcher.matchEntire(str)
                    ?: error("Unexpected position modifier: $str")

                val (className, fieldName, fieldType) = fieldMatch.destructured
                Field(className, fieldName, fieldType)
            }
        }
    }
}
