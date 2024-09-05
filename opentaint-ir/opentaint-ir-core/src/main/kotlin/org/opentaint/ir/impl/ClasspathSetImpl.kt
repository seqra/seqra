package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet

class ClasspathSetImpl(override val locations: List<ByteCodeLocation>): ClasspathSet {

    override suspend fun findClassOrNull(name: String): ClassId? {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}