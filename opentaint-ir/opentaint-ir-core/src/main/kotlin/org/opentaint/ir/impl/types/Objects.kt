package org.opentaint.ir.impl.types


class ClassInfo(
    val name: String,
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
)

class OuterClassRef(
    val className: String,
    val name: String?
)

class MethodInfo(
    val name: String,
    val desc: String,
    val access: Int,
    val returnType: String,
    val parameters: List<String>,
    val annotations: List<AnnotationInfo>
)

class FieldInfo(
    val name: String,
    val access: Int,
    val type: String,
    val annotations: List<AnnotationInfo>
)

class AnnotationInfo(
    val className: String
)


