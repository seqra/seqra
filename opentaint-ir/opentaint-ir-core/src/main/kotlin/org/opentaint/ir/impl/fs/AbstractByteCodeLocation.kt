package org.opentaint.ir.impl.fs

import com.google.common.hash.Hashing
import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

abstract class AbstractByteCodeLocation(override val jarOrFolder: File) : JIRByteCodeLocation {

    override val path: String
        get() = jarOrFolder.absolutePath

    override val fileSystemIdHash: BigInteger by lazy { currentHash }

    override fun isChanged() = fileSystemIdHash != currentHash

    protected val String.shaHash: ByteArray
        get() {
            return Hashing.sha256()
                .hashString(this, StandardCharsets.UTF_8)
                .asBytes()
        }

    protected val ByteBuffer.shaHash: ByteArray
        get() {
            return Hashing.sha256()
                .hashBytes(this)
                .asBytes()
        }
}
