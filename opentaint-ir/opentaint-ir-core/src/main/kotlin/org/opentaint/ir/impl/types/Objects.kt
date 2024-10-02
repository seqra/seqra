package org.opentaint.ir.impl.types

import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import org.objectweb.asm.Type

@Serializable
sealed class ClassInfoContainer

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
    val annotations: List<AnnotationInfo>
) : ClassInfoContainer()

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
    val annotations: List<AnnotationInfo>
) {

    fun signature(internalNames: Boolean): String {
        if (internalNames) {
            return name + desc
        }
        val params = parameters.joinToString(";") + (";".takeIf { parameters.isNotEmpty() } ?: "")
        return "$name($params)${returnType};"
    }

    val returnType get() = Type.getReturnType(desc).className
    val parameters get() = Type.getArgumentTypes(desc).map { it.className }.toImmutableList()

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
    val className: String
)

@Serializable
class LocationClasses(
    val classes: List<ClassInfo>
)

@Serializable
class PredefinedClassInfo(val name: String) : ClassInfoContainer()

@Serializable
class ArrayClassInfo(
    val elementInfo: ClassInfoContainer
) : ClassInfoContainer()