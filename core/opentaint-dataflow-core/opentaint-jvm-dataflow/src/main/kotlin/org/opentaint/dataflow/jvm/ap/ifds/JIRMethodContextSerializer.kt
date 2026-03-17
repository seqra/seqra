package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.ap.ifds.CombinedMethodContext
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.dataflow.ap.ifds.serialization.readEnum
import org.opentaint.dataflow.ap.ifds.serialization.writeEnum
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.ext.findClass
import java.io.DataInputStream
import java.io.DataOutputStream

class JIRMethodContextSerializer(private val cp: JIRClasspath) : MethodContextSerializer {
    override fun DataOutputStream.writeMethodContext(methodContext: MethodContext) {
        when (methodContext) {
            is EmptyMethodContext -> writeEnum(ContextType.EMPTY)

            is CombinedMethodContext -> {
                writeEnum(ContextType.COMBINED)
                writeMethodContext(methodContext.first)
                writeMethodContext(methodContext.second)
            }

            is JIRInstanceTypeMethodContext -> {
                writeEnum(ContextType.JIR_INSTANCE_TYPE)
                writeConstraint(methodContext.typeConstraint)
            }

            is JIRArgumentTypeMethodContext -> {
                writeEnum(ContextType.JIR_INSTANCE_TYPE)
                writeInt(methodContext.argIdx)
                writeConstraint(methodContext.typeConstraint)
            }

            else -> error("Unknown method context: $methodContext")
        }
    }

    override fun DataInputStream.readMethodContext(): MethodContext {
        val contextType = readEnum<ContextType>()
        return when (contextType) {
            ContextType.EMPTY -> EmptyMethodContext

            ContextType.COMBINED -> {
                val first = readMethodContext()
                val second = readMethodContext()
                CombinedMethodContext(first, second)
            }

            ContextType.JIR_INSTANCE_TYPE -> {
                val constraint = readConstraint()
                JIRInstanceTypeMethodContext(constraint)
            }

            ContextType.JIR_ARGUMENT_TYPE -> {
                val argIdx = readInt()
                val constraint = readConstraint()
                JIRArgumentTypeMethodContext(argIdx, constraint)
            }
        }
    }

    private fun DataOutputStream.writeConstraint(constraint: TypeConstraintInfo) {
        writeBoolean(constraint.exactType)
        writeUTF(constraint.type.name)
    }

    private fun DataInputStream.readConstraint(): TypeConstraintInfo {
        val exactType = readBoolean()
        val typeName = readUTF()
        return TypeConstraintInfo(cp.findClass(typeName), exactType)
    }

    private enum class ContextType {
        EMPTY,
        COMBINED,
        JIR_INSTANCE_TYPE,
        JIR_ARGUMENT_TYPE,
    }
}