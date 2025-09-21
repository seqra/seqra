package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.serialization.MethodSerializer
import org.opentaint.dataflow.ap.ifds.serialization.readEnum
import org.opentaint.dataflow.ap.ifds.serialization.writeEnum
import java.io.DataInputStream
import java.io.DataOutputStream

class JIRMethodSerializer(private val cp: JIRClasspath) : MethodSerializer {
    override fun DataOutputStream.writeMethod(method: CommonMethod) {
        jirDowncast<JIRMethod>(method)
        writeUTF(method.enclosingClass.name)
        writeUTF(method.name)
        writeUTF(method.description)
    }

    override fun DataOutputStream.writeMethodContext(context: MethodContext) {
        when (context) {
            EmptyMethodContext -> writeEnum(ContextType.EMPTY)
            is JIRInstanceTypeMethodContext -> {
                writeEnum(ContextType.JIR_INSTANCE_TYPE)
                writeUTF(context.type.name)
            }
            else -> error("Unknown method context: $context")
        }
    }

    override fun DataInputStream.readMethod(): CommonMethod {
        val className = readUTF()
        val methodName = readUTF()
        val methodDescription = readUTF()
        val jirClass = cp.findClassOrNull(className) ?: error(
            "Deserialization error: can't find class $className in classpath"
        )
        val jirMethod = jirClass.findMethodOrNull(methodName, methodDescription) ?: error(
            "Deserialization error: can't find method $methodName in class $className"
        )
        return jirMethod
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