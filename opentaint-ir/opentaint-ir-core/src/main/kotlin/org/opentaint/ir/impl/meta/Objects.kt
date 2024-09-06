package org.opentaint.ir.impl.fs

import kotlinx.collections.immutable.PersistentList


class ClassMetaInfo(
    val name: String,
    val access: Int,

    val methods: PersistentList<MethodMetaInfo>,
    val fields: PersistentList<FieldMetaInfo>,

    val superClass: String? = null,
    val interfaces: PersistentList<String>,
    val annotations: PersistentList<AnnotationMetaInfo>
)

class MethodMetaInfo(
    val name: String,
    val desc: String,
    val access: Int,
    val returnType: String,
    val parameters: PersistentList<String>,
    val annotations: PersistentList<AnnotationMetaInfo>
)

class FieldMetaInfo(
    val name: String,
    val access: Int,
    val type: String
)

class AnnotationMetaInfo(
    val className: String
)
