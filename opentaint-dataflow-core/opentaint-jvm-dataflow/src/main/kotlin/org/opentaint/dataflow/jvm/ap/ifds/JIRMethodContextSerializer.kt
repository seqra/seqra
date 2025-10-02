package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.dataflow.ap.ifds.serialization.readEnum
import org.opentaint.dataflow.ap.ifds.serialization.writeEnum
import java.io.DataInputStream
import java.io.DataOutputStream

class JIRMethodContextSerializer(private val cp: JIRClasspath) : MethodContextSerializer {
    override fun DataOutputStream.writeMethodContext(methodContext: MethodContext) {
        when (methodContext) {
            EmptyMethodContext -> writeEnum(ContextType.EMPTY)
            is JIRInstanceTypeMethodContext -> {
                writeEnum(ContextType.JIR_INSTANCE_TYPE)
                writeUTF(methodContext.type.name)
            }
            else -> error("Unknown method context: $methodContext")
        }
    }

    override fun DataInputStream.readMethodContext(): MethodContext {
        val contextType = readEnum<ContextType>()
        return when (contextType) {
            ContextType.EMPTY -> EmptyMethodContext
            ContextType.JIR_INSTANCE_TYPE -> {
                val typeName = readUTF()
                JIRInstanceTypeMethodContext(cp.findClass(typeName))
            }
        }
    }

    private enum class ContextType {
        EMPTY,
        JIR_INSTANCE_TYPE
    }
}