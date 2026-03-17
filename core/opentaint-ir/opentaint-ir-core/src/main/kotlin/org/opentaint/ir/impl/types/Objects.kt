package org.opentaint.ir.impl.types

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.opentaint.ir.api.jvm.ext.jIRdbName
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.ext.jvmName
import org.opentaint.ir.impl.storage.AnnotationValueKind
import org.opentaint.ir.impl.util.adjustEmptyList
import org.opentaint.ir.impl.util.interned
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
    val bytecode: ByteArray,
)

@Serializable
class OuterClassRef(
    val className: String,
    val name: String?,
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
    val returnClass: String get() = Type.getReturnType(desc).className.interned
    val parameters: List<String> get() = Type.getArgumentTypes(desc).map { it.className.interned }.adjustEmptyList()

}

@Serializable
class FieldInfo(
    val name: String,
    val signature: String?,
    val access: Int,
    val type: String,
    val annotations: List<AnnotationInfo>,
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
    val annotations: List<AnnotationInfo>,
)

@Serializable
sealed class AnnotationValue

@Serializable
open class AnnotationValueList(val annotations: List<AnnotationValue>) : AnnotationValue()

@Serializable
class PrimitiveValue(val dataType: AnnotationValueKind, val value: @Contextual Any) : AnnotationValue()

@Serializable
class ClassRef(val className: String) : AnnotationValue()

@Serializable
class EnumRef(val className: String, val enumName: String) : AnnotationValue()

@Serializable
data class TypeNameImpl private constructor(private val jvmName: String) : TypeName {
    override val typeName: String = jvmName.jIRdbName().interned

    override fun toString(): String = typeName

    companion object {
        fun fromJvmName(jvmName: String): TypeNameImpl = TypeNameImpl(jvmName)
        fun fromTypeName(typeName: String): TypeNameImpl = fromJvmName(typeName.jvmName())
    }
}
