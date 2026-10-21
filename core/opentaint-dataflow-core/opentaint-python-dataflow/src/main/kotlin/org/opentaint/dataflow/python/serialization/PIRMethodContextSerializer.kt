package org.opentaint.dataflow.python.serialization

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import java.io.DataInputStream
import java.io.DataOutputStream

class PIRMethodContextSerializer : MethodContextSerializer {

    override fun DataOutputStream.writeMethodContext(methodContext: MethodContext) {
        writeByte(0)
    }

    override fun DataInputStream.readMethodContext(): MethodContext {
        val tag = readByte().toInt()
        return when (tag) {
            0 -> EmptyMethodContext
            else -> error("Unknown method context tag: $tag")
        }
    }
}
