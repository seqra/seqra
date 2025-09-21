package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.dataflow.ap.ifds.MethodContext
import java.io.DataInputStream
import java.io.DataOutputStream

interface MethodSerializer {
    fun DataOutputStream.writeMethod(method: CommonMethod)
    fun DataOutputStream.writeMethodContext(context: MethodContext)

    fun DataInputStream.readMethod(): CommonMethod
    fun DataInputStream.readMethodContext(): MethodContext
}