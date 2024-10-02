package org.opentaint.ir.impl.types

import kotlinx.serialization.Serializable

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
    val returnType: String,
    val parameters: List<String>,
    val annotations: List<AnnotationInfo>
)

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
class PredefinedClassInfo(val name: String): ClassInfoContainer()

@Serializable
class ArrayClassInfo(
    val elementInfo: ClassInfoContainer
) : ClassInfoContainer()