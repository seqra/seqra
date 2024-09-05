package org.opentaint.ir.impl.meta

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.MethodId

class ClassIdImpl(
    override val location: ByteCodeLocation,
    override val name: String,
    override val simpleName: String
) : ClassId {

    override suspend fun methods(): List<MethodId> {
        TODO("Not yet implemented")
    }

    override suspend fun superclass(): ClassId? {
        TODO("Not yet implemented")
    }

    override suspend fun interfaces(): List<ClassId> {
        TODO("Not yet implemented")
    }

    override suspend fun annotations(): List<ClassId> {
        TODO("Not yet implemented")
    }
}