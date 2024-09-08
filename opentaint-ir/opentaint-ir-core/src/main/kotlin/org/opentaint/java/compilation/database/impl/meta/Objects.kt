package org.opentaint.java.compilation.database.impl.meta


class ClassMetaInfo(
    val name: String,
    val access: Int,

    val methods: List<MethodMetaInfo>,
    val fields: List<FieldMetaInfo>,

    val superClass: String? = null,
    val interfaces: List<String>,
    val annotations: List<AnnotationMetaInfo>
)

class MethodMetaInfo(
    val name: String,
    val desc: String,
    val access: Int,
    val returnType: String,
    val parameters: List<String>,
    val annotations: List<AnnotationMetaInfo>
)

class FieldMetaInfo(
    val name: String,
    val access: Int,
    val type: String
)

class AnnotationMetaInfo(
    val className: String
)


