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

@Serializable(with = SerializedConditionSerializer::class)
sealed interface SerializedCondition {
    @Serializable
    data class Or(val anyOf: List<SerializedCondition>) : SerializedCondition

    @Serializable
    data class And(val allOf: List<SerializedCondition>) : SerializedCondition

    @Serializable
    data class Not(val not: SerializedCondition) : SerializedCondition

    fun isFalse(): Boolean = this is Not && this.not is True

    companion object {
        fun mkFalse() = Not(True)

        fun not(arg: SerializedCondition) = if (arg is Not) arg.not else Not(arg)

        fun and(args: List<SerializedCondition>): SerializedCondition =
            mkFlatOp(
                args, And::allOf, ::And,
                isNeutral = { it is True },
                mkNeutral = { True },
                isZero = { it.isFalse() },
                mkZero = { mkFalse() },
            )

        fun or(args: List<SerializedCondition>): SerializedCondition =
            mkFlatOp(
                args, Or::anyOf, ::Or,
                isNeutral = { it.isFalse() },
                mkNeutral = { mkFalse() },
                isZero = { it is True },
                mkZero = { True }
            )

        private inline fun <reified Op : SerializedCondition> mkFlatOp(
            args: List<SerializedCondition>,
            opArgs: Op.() -> List<SerializedCondition>,
            mkOp: (List<SerializedCondition>) -> Op,
            isNeutral: (SerializedCondition) -> Boolean,
            mkNeutral: () -> SerializedCondition,
            isZero: (SerializedCondition) -> Boolean,
            mkZero: () -> SerializedCondition,
        ): SerializedCondition {
            val result = mutableSetOf<SerializedCondition>()
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

    @Serializable
    data class IsType(
        val typeIs: SerializedTypeNameMatcher,
        val pos: PositionBase
    ) : SerializedCondition

    @Serializable
    data class AnnotationType(
        val annotatedWith: SerializedTypeNameMatcher,
        val pos: PositionBase
    ) : SerializedCondition

    @Serializable
    data class IsConstant(val isConstant: PositionBase) : SerializedCondition

    @Serializable
    data class IsNull(val isNull: PositionBase) : SerializedCondition

    @Serializable
    data class ConstantMatches(val constantMatches: String, val pos: PositionBase) : SerializedCondition

    @Serializable
    enum class ConstantType {
        Str, Bool, Int
    }

    @Serializable
    data class ConstantValue(
        val type: ConstantType,
        val value: String
    )

    @Serializable
    enum class ConstantCmpType {
        Eq, Lt, Gt
    }

    @Serializable
    data class ConstantCmp(
        val pos: PositionBase,
        val value: ConstantValue,
        val cmp: ConstantCmpType
    ) : SerializedCondition

    @Serializable
    data class ConstantEq(val constantEq: String, val pos: PositionBase) : SerializedCondition

    @Serializable
    data class ConstantGt(val constantGt: String, val pos: PositionBase) : SerializedCondition

    @Serializable
    data class ConstantLt(val constantLt: String, val pos: PositionBase) : SerializedCondition

    @Serializable(with = TrueConditionSerializer::class)
    data object True : SerializedCondition

    @Serializable
    data class ContainsMark(
        val tainted: String,
        val pos: PositionBaseWithModifiers,
    ): SerializedCondition

    @Serializable
    data class NumberOfArgs(val numberOfArgs: Int): SerializedCondition

    sealed interface AnnotationParamMatcher {
        val name: SerializedSimpleNameMatcher
    }

    @Serializable
    data class AnnotationParamStringMatcher(
        override val name: SerializedSimpleNameMatcher,
        val value: SerializedSimpleNameMatcher
    ) : AnnotationParamMatcher

    @Serializable
    data class AnnotationConstraint(
        val type: SerializedTypeNameMatcher,
        val params: List<AnnotationParamMatcher>?
    )

    @Serializable
    data class MethodAnnotated(val annotation: AnnotationConstraint): SerializedCondition

    @Serializable
    data class ClassAnnotated(val annotation: AnnotationConstraint): SerializedCondition

    @Serializable
    data class ParamAnnotated(
        val pos: PositionBase,
        val annotation: AnnotationConstraint
    ) : SerializedCondition

    @Serializable
    data class MethodNameMatches(val methodName: SerializedSimpleNameMatcher) : SerializedCondition

    @Serializable
    data class ClassNameMatches(val className: SerializedTypeNameMatcher) : SerializedCondition

    @Serializable
    data class IsStaticField(
        val pos: PositionBase,
        val className: SerializedTypeNameMatcher,
        val fieldName: SerializedSimpleNameMatcher
    ): SerializedCondition

    data class ContainsMarkAnyField(
        val tainted: String,
        val pos: PositionBaseWithModifiers,
    ) : SerializedCondition
}

class TrueConditionSerializer : KSerializer<SerializedCondition.True> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("true.condition", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): SerializedCondition.True {
        val value = decoder.decodeBoolean()
        check(value) { "Only true value allowed" }
        return SerializedCondition.True
    }

    override fun serialize(encoder: Encoder, value: SerializedCondition.True) {
        encoder.encodeBoolean(true)
    }
}

class SerializedConditionSerializer :
    YamlContentPolymorphicSerializer<SerializedCondition>(SerializedCondition::class) {
    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<SerializedCondition> {
        when (node) {
            is YamlScalar -> return SerializedCondition.True.serializer()

            is YamlMap -> {
                for ((property, serializer) in serializerByProperty) {
                    if (node.getKey(property) != null) {
                        return serializer
                    }
                }
                error("Unexpected node: $node")
            }

            else -> error("Unexpected node: $node")
        }
    }

    companion object {
        private val serializerByProperty = mapOf(
            "tainted" to SerializedCondition.ContainsMark.serializer(),
            "anyOf" to SerializedCondition.Or.serializer(),
            "allOf" to SerializedCondition.And.serializer(),
            "not" to SerializedCondition.Not.serializer(),
            "typeIs" to SerializedCondition.IsType.serializer(),
            "annotatedWith" to SerializedCondition.AnnotationType.serializer(),
            "isConstant" to SerializedCondition.IsConstant.serializer(),
            "isNull" to SerializedCondition.IsNull.serializer(),
            "constantMatches" to SerializedCondition.ConstantMatches.serializer(),
            "constantEq" to SerializedCondition.ConstantEq.serializer(),
            "constantGt" to SerializedCondition.ConstantGt.serializer(),
            "constantLt" to SerializedCondition.ConstantLt.serializer(),
        )
    }
}
