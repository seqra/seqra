package org.opentaint.ir.impl.types

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.FieldId
import org.opentaint.ir.api.FieldResolution
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.ClassIdService
import org.opentaint.ir.impl.signature.FieldSignature
import org.opentaint.ir.impl.suspendableLazy

class FieldIdImpl(
    override val classId: ClassId,
    private val info: FieldInfo,
    private val classIdService: ClassIdService
) : FieldId {

    override val name: String
        get() = info.name

    private val lazyType = suspendableLazy {
        classIdService.toClassId(info.type)
    }

    private val lazyAnnotations = suspendableLazy {
        info.annotations.map { classIdService.toClassId(it.className) ?: it.className.throwClassNotFound() }
    }

    override suspend fun resolution(): FieldResolution {
        return FieldSignature.extract(info.signature, classId.classpath)
    }

    override suspend fun access() = info.access
    override suspend fun type() = lazyType() ?: info.type.throwClassNotFound()

    override suspend fun annotations() = lazyAnnotations()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is FieldIdImpl) {
            return false
        }
        return other.name == name && other.classId == classId
    }

    override fun hashCode(): Int {
        return 31 * classId.hashCode() + name.hashCode()
    }
}