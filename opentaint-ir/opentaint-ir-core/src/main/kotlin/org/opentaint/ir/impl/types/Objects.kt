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
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.ext.jIRdbName
import org.opentaint.ir.impl.storage.AnnotationValueKind
import org.objectweb.asm.Type

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
    val exceptions: List<String>,
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
    val values: List<Pair<String, AnnotationValue>>,
    val typeRef: Int?, // -- only applicable to type annotations (null for others)
    val typePath: String?, // -- only applicable to type annotations (null for others, but also may be null for some type annotations)
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

@Serializable
class PrimitiveValue(val dataType: AnnotationValueKind, val value: Any) : AnnotationValue()

@Serializable
class ClassRef(val className: String) : AnnotationValue()

@Serializable
class EnumRef(val className: String, val enumName: String) : AnnotationValue()

@Serializable
data class TypeNameImpl(private val jvmName: String) : TypeName {
    override val typeName: String = jvmName.jIRdbName()

    override fun toString(): String = typeName
}
