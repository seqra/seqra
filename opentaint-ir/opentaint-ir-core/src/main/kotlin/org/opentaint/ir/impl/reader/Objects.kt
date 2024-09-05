package org.opentaint.ir.impl.reader

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
    val access: Int,
    val returnType: String,
    val parameters: PersistentList<String>,
    annotationsGetter: () -> PersistentList<AnnotationMetaInfo>
){
    val annotations by lazy(LazyThreadSafetyMode.NONE, annotationsGetter)
}

class FieldMetaInfo(
    val name: String,
    val access: Int,
    val type: String
)

class AnnotationMetaInfo(
    val visible: Boolean,
    val type: String
)
