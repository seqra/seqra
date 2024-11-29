package org.opentaint.opentaint-ir.impl.fs

import org.opentaint.opentaint-ir.api.JIRByteCodeLocation
import java.io.File

abstract class AbstractByteCodeLocation(override val jarOrFolder: File) : JIRByteCodeLocation {

    override val path: String
        get() = jarOrFolder.absolutePath

    abstract fun currentHash(): String

    override fun isChanged(): Boolean {
        return fsId != currentHash()
    }

}