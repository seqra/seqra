package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.dataflow.ap.ifds.MethodContext
import java.io.DataInputStream
import java.io.DataOutputStream

internal class ContextAwareMethodSerializer(
    private val inner: MethodSerializer,
    private val context: SummarySerializationContext
) {
    private fun DataOutputStream.serializeMethod(method: CommonMethod): Int {
        context.serializedMethods[method]?.let {
            return it
        }

        val id = context.serializedMethods.size
        writeEnum(ValueType.NEW_METHOD)
        writeInt(id)
        with (inner) {
            writeMethod(method)
        }
        context.serializedMethods[method] = id
        return id
    }

    fun DataOutputStream.writeMethod(method: CommonMethod) {
        val id = serializeMethod(method)
        writeEnum(ValueType.SERIALIZED)
        writeInt(id)
    }

    fun DataOutputStream.writeMethodContext(context: MethodContext) {
        with (inner) {
            writeMethodContext(context)
        }
    }

    private fun DataInputStream.deserializeMethod(): CommonMethod {
        return with (inner) {
            readMethod()
        }
    }

    fun DataInputStream.readMethod(): CommonMethod {
        val kind = readEnum<ValueType>()
        val id = readInt()
        return when (kind) {
            ValueType.SERIALIZED -> context.deserializedMethods[id]!!
            ValueType.NEW_METHOD -> {
                context.deserializedMethods[id] = deserializeMethod()
                readMethod()
            }
        }
    }

    fun DataInputStream.readMethodContext(): MethodContext {
        return with (inner) {
            readMethodContext()
        }
    }

    private enum class ValueType {
        SERIALIZED,
        NEW_METHOD
    }
}