package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import java.math.BigInteger

abstract class AbstractByteCodeLocation : JIRByteCodeLocation {
    override val fileSystemIdHash: BigInteger by lazy { currentHash }

    override fun isChanged() = fileSystemIdHash != currentHash
}
