package org.opentaint.ir.impl.meta

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.impl.ClassIdService
import org.opentaint.ir.impl.reader.MethodMetaInfo
import org.opentaint.ir.impl.reader.reader

class MethodIdImpl(
    private val methodInfo: MethodMetaInfo,
    override val classId: ClassId,
    private val classIdService: ClassIdService
) : MethodId {

    override val name: String get() = methodInfo.name

    private val lazyParameters by lazy {
        methodInfo.parameters.mapNotNull {
            classIdService.toClassId(it)
        }
    }
    private val lazyAnnotations by lazy {
        methodInfo.annotations.mapNotNull {
            classIdService.toClassId(it.type)
        }
    }

    override suspend fun returnType() = classIdService.toClassId(methodInfo.returnType)

    override suspend fun parameters() = lazyParameters

    override suspend fun annotations() = lazyAnnotations

    override suspend fun readBody(): Any {
        val location = classId.location
        if (location.isChanged()) {
            throw IllegalStateException("bytecode is changed")
        }
        return location.reader(classId.name).readClassMeta()
    }
}