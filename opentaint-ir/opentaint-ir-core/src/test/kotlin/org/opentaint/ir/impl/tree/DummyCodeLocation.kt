package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.LocationType
import java.io.File

open class DummyCodeLocation(val id: String) : JIRByteCodeLocation {
    override val hash: String
        get() = id
    override val type=  LocationType.APP

    override suspend fun classes() = null

    override val jarOrFolder: File
        get() = TODO("Not yet implemented")
    override val path: String
        get() = TODO("")

    override fun isChanged() = false

    override fun createRefreshed() = this

    override suspend fun resolve(classFullName: String) = null

}

