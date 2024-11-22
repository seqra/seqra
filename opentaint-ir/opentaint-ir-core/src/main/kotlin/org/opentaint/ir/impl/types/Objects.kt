
package org.opentaint.ir.impl.types

import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import org.objectweb.asm.Type
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.jirdbName
import org.opentaint.ir.impl.storage.AnnotationValueKind

@Serializable
class ClassInfo(
    val name: String,

    val signature: String?,
    val access: Int,

    val outerClass: OuterClassRef?,
    val outerMethod: String?,
    val outerMethodDesc: String?,

    val methods: List<MethodInfo>,
    val fields: List<FieldInfo>,

    val superClass: String? = null,
    val innerClasses: List<String>,
    val interfaces: List<String>,
    val annotations: List<AnnotationInfo>,
    val bytecode: ByteArray
)

@Serializable
class OuterClassRef(
    val className: String,
    val name: String?
)

@Serializable
class MethodInfo(
    val name: String,
    val desc: String,
    val signature: String?,
    val access: Int,
    val annotations: List<AnnotationInfo>,
    val parametersInfo: List<ParameterInfo>,
) {
    val returnClass: String get() = Type.getReturnType(desc).className
    val parameters: List<String> get() = Type.getArgumentTypes(desc).map { it.className }.toImmutableList()

}

@Serializable
class FieldInfo(
    val name: String,
    val signature: String?,
    val access: Int,
    val type: String,
    val annotations: List<AnnotationInfo>
)

@Serializable
class AnnotationInfo(
    val className: String,
    val visible: Boolean,
    val values: List<Pair<String, AnnotationValue>>
) : AnnotationValue()

@Serializable
class ParameterInfo(
    val type: String,
    val index: Int,
    val access: Int,
    val name: String?,
    val annotations: List<AnnotationInfo>
)

@Serializable
sealed class AnnotationValue

@Serializable
open class AnnotationValueList(val annotations: List<AnnotationValue>) : AnnotationValue()

@Serializable(with = PrimitiveValueSerializer::class)
class PrimitiveValue(val dataType: AnnotationValueKind, val value: Any) : AnnotationValue()

object PrimitiveValueSerializer : KSerializer<PrimitiveValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PrimitiveValue") {
        element("dataType", serialDescriptor<String>())
        element("value", buildClassSerialDescriptor("Any"))
    }

    @Suppress("UNCHECKED_CAST")
    private val dataTypeSerializers: Map<AnnotationValueKind, KSerializer<Any>> =
        mapOf(
            AnnotationValueKind.STRING to serializer<String>(),
            AnnotationValueKind.BYTE to serializer<Byte>(),
            AnnotationValueKind.SHORT to serializer<Short>(),
            AnnotationValueKind.CHAR to serializer<Char>(),
            AnnotationValueKind.LONG to serializer<Long>(),
            AnnotationValueKind.INT to serializer<Int>(),
            AnnotationValueKind.FLOAT to serializer<Float>(),
            AnnotationValueKind.DOUBLE to serializer<Double>(),
            AnnotationValueKind.BYTE to serializer<Byte>(),
            AnnotationValueKind.BOOLEAN to serializer<Boolean>()
            //list them all
        ).mapValues { (_, v) -> v as KSerializer<Any> }

    private fun getPayloadSerializer(dataType: AnnotationValueKind): KSerializer<Any> = dataTypeSerializers[dataType]
        ?: throw SerializationException("Serializer for class $dataType is not registered in PacketSerializer")

    override fun serialize(encoder: Encoder, value: PrimitiveValue) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.dataType.name)
            encodeSerializableElement(descriptor, 1, getPayloadSerializer(value.dataType), value.value)
        }
    }

    @ExperimentalSerializationApi
    override fun deserialize(decoder: Decoder): PrimitiveValue = decoder.decodeStructure(descriptor) {
        val dataType = AnnotationValueKind.valueOf(decodeStringElement(descriptor, 0))
        if (decodeSequentially()) {
            val payload = decodeSerializableElement(descriptor, 1, getPayloadSerializer(dataType))
            PrimitiveValue(dataType, payload)
        } else {
            require(decodeElementIndex(descriptor) == 0) { "dataType field should precede payload field" }
            val payload = when (val index = decodeElementIndex(descriptor)) {
                1 -> decodeSerializableElement(descriptor, 1, getPayloadSerializer(dataType))
                CompositeDecoder.DECODE_DONE -> throw SerializationException("payload field is missing")
                else -> error("Unexpected index: $index")
            }
            PrimitiveValue(dataType, payload)
        }
    }
}

@Serializable
class ClassRef(val className: String) : AnnotationValue()

@Serializable
class EnumRef(val className: String, val enumName: String) : AnnotationValue()

@Serializable
data class TypeNameImpl(private val jvmName: String) : TypeName {
    override val typeName: String = jvmName.jirdbName()
}
