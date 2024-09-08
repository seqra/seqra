package org.opentaint.java.compilation.database.impl.meta

import org.opentaint.java.compilation.database.api.FieldId
import org.opentaint.java.compilation.database.impl.ClassIdService

class FieldIdImpl(
    private val info: FieldMetaInfo,
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