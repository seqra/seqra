package org.opentaint.dataflow.python.serialization

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Minimal context serializer for Python. Since we only use EmptyMethodContext,
 * serialization is trivial.
 */
class PIRMethodContextSerializer : MethodContextSerializer {

    override fun DataOutputStream.writeMethodContext(methodContext: MethodContext) {
        writeByte(0) // tag for EmptyMethodContext
    }

    override fun DataInputStream.readMethodContext(): MethodContext {
        val tag = readByte().toInt()
        return when (tag) {
            0 -> EmptyMethodContext
            else -> error("Unknown method context tag: $tag")
        }
    }
}
