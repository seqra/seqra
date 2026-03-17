package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.dataflow.ap.ifds.MethodContext
import java.io.DataInputStream
import java.io.DataOutputStream

interface MethodContextSerializer {
    fun DataOutputStream.writeMethodContext(methodContext: MethodContext)
    fun DataInputStream.readMethodContext(): MethodContext
}