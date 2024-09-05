package org.opentaint.ir.impl.meta

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.MethodId

class MethodIdImpl(
    override val name: String,
    override val classId: ClassId
) : MethodId {

    override suspend fun returnType(): ClassId? {
        TODO("Not yet implemented")
    }

    override suspend fun parameters(): List<ClassId> {
        TODO("Not yet implemented")
    }

    override suspend fun annotations(): List<ClassId> {
        TODO("Not yet implemented")
    }

    override suspend fun readBody(): Any {
        TODO("Not yet implemented")
    }
}