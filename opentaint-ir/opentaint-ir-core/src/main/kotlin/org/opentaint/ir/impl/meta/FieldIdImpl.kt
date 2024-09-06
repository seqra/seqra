package org.opentaint.ir.impl.meta

import org.opentaint.ir.api.FieldId
import org.opentaint.ir.impl.ClassIdService
import org.opentaint.ir.impl.fs.FieldMetaInfo

class FieldIdImpl(
    val info: FieldMetaInfo,
    private val classIdService: ClassIdService
) : FieldId {

    override val name: String
        get() = info.name

    private val lazyType by lazy(LazyThreadSafetyMode.NONE) {
        classIdService.toClassId(info.type)
    }

    override suspend fun access() = info.access
    override suspend fun type() = lazyType

}