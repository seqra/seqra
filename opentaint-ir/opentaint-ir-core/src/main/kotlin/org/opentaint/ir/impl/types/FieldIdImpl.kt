package org.opentaint.ir.impl.types

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.FieldId
import org.opentaint.ir.impl.ClassIdService

class FieldIdImpl(
    override val classId: ClassId,
    private val info: FieldInfo,
    private val classIdService: ClassIdService
) : FieldId {

    override val name: String
        get() = info.name

    private val lazyType by lazy(LazyThreadSafetyMode.NONE) {
        classIdService.toClassId(info.type)
    }

    private val lazyAnnotations by lazy(LazyThreadSafetyMode.NONE) {
        info.annotations.mapNotNull { classIdService.toClassId(it.className) }

    }

    override suspend fun access() = info.access
    override suspend fun type() = lazyType

    override suspend fun annotations() = lazyAnnotations

}