package org.opentaint.ir.impl.fs

import com.google.common.hash.Hashing
import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import java.io.File
import java.nio.charset.StandardCharsets

abstract class AbstractByteCodeLocation(override val jarOrFolder: File) : JIRByteCodeLocation {

    override val path: String
        get() = jarOrFolder.absolutePath

    abstract fun currentHash(): String

    override fun isChanged() = fileSystemId != currentHash()

    protected val String.shaHash: String
        get() {
            return Hashing.sha256()
                .hashString(this, StandardCharsets.UTF_8)
                .toString();
        }

}