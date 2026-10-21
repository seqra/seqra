package org.opentaint.dataflow.configuration.python.serialized

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SerializedPythonConditionSerializer::class)
sealed interface SerializedPythonCondition {
    @Serializable(with = PythonTrueConditionSerializer::class)
    data object True : SerializedPythonCondition

    @Serializable
    data class Or(val anyOf: List<SerializedPythonCondition>) : SerializedPythonCondition

    @Serializable
    data class And(val allOf: List<SerializedPythonCondition>) : SerializedPythonCondition

    @Serializable
    data class Not(val not: SerializedPythonCondition) : SerializedPythonCondition

    @Serializable
    data class ContainsMark(
        val tainted: String,
        val pos: PythonPosition,
    ) : SerializedPythonCondition

    @Serializable
    data class ContainsMarkOnAnyAccessor(
        @SerialName("taintedAny") val tainted: String,
        val pos: PythonPosition,
    ) : SerializedPythonCondition

    @Serializable
    data class NumberOfArgs(val n: Int) : SerializedPythonCondition

    @Serializable
    data class MethodDecorated(val decorator: String) : SerializedPythonCondition

    @Serializable
    data class ClassExtends(val baseClass: String) : SerializedPythonCondition

    @Serializable
    data class ConstantCmp(
        val pos: PythonPosition,
        val value: ConstantValue,
        val cmp: ConstantCmpType,
    ) : SerializedPythonCondition

    @Serializable
    data class ConstantMatches(
        val pos: PythonPosition,
        val pattern: String,
    ) : SerializedPythonCondition

    @Serializable
    data class ConstantValue(val type: ConstantType, val value: String)

    enum class ConstantCmpType { Eq, Lt, Gt }

    enum class ConstantType { Bool, Int, Str }

    fun isFalse(): Boolean = this is Not && this.not is True

    companion object {
        fun mkFalse() = Not(True)

        fun not(arg: SerializedPythonCondition): SerializedPythonCondition =
            if (arg is Not) arg.not else Not(arg)

        fun and(args: List<SerializedPythonCondition>): SerializedPythonCondition =
            mkFlatOp(
                args, And::allOf, ::And,
                isNeutral = { it is True },
                mkNeutral = { True },
                isZero = { it.isFalse() },
                mkZero = { mkFalse() },
            )

        fun or(args: List<SerializedPythonCondition>): SerializedPythonCondition =
            mkFlatOp(
                args, Or::anyOf, ::Or,
                isNeutral = { it.isFalse() },
                mkNeutral = { mkFalse() },
                isZero = { it is True },
                mkZero = { True },
            )

        private inline fun <reified Op : SerializedPythonCondition> mkFlatOp(
            args: List<SerializedPythonCondition>,
            opArgs: Op.() -> List<SerializedPythonCondition>,
            mkOp: (List<SerializedPythonCondition>) -> Op,
            isNeutral: (SerializedPythonCondition) -> Boolean,
            mkNeutral: () -> SerializedPythonCondition,
            isZero: (SerializedPythonCondition) -> Boolean,
            mkZero: () -> SerializedPythonCondition,
        ): SerializedPythonCondition {
            val result = mutableSetOf<SerializedPythonCondition>()
            for (arg in args) {
                if (arg is Op) {
                    result.addAll(opArgs(arg))
                    continue
                }
                if (isNeutral(arg)) continue
                if (isZero(arg)) return mkZero()
                result.add(arg)
            }
            return when (result.size) {
                0 -> mkNeutral()
                1 -> result.single()
                else -> mkOp(result.toList())
            }
        }
    }
}

class PythonTrueConditionSerializer : KSerializer<SerializedPythonCondition.True> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("true.condition", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): SerializedPythonCondition.True {
        val value = decoder.decodeBoolean()
        check(value) { "Only true value allowed" }
        return SerializedPythonCondition.True
    }

    override fun serialize(encoder: Encoder, value: SerializedPythonCondition.True) {
        encoder.encodeBoolean(true)
    }
}

class SerializedPythonConditionSerializer :
    YamlContentPolymorphicSerializer<SerializedPythonCondition>(SerializedPythonCondition::class) {
    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<SerializedPythonCondition> {
        when (node) {
            is YamlScalar -> return SerializedPythonCondition.True.serializer()

            is YamlMap -> {
                for ((property, serializer) in serializerByProperty) {
                    if (node.getKey(property) != null) return serializer
                }
                error("Unexpected condition node: $node")
            }

            else -> error("Unexpected condition node: $node")
        }
    }

    companion object {
        private val serializerByProperty = mapOf(
            "anyOf" to SerializedPythonCondition.Or.serializer(),
            "allOf" to SerializedPythonCondition.And.serializer(),
            "not" to SerializedPythonCondition.Not.serializer(),
            "tainted" to SerializedPythonCondition.ContainsMark.serializer(),
            "taintedAny" to SerializedPythonCondition.ContainsMarkOnAnyAccessor.serializer(),
            "n" to SerializedPythonCondition.NumberOfArgs.serializer(),
            "cmp" to SerializedPythonCondition.ConstantCmp.serializer(),
            "pattern" to SerializedPythonCondition.ConstantMatches.serializer(),
            "decorator" to SerializedPythonCondition.MethodDecorated.serializer(),
            "baseClass" to SerializedPythonCondition.ClassExtends.serializer(),
        )
    }
}
