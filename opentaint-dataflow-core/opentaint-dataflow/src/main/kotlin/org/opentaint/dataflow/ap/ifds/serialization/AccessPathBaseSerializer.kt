package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import java.io.DataInputStream
import java.io.DataOutputStream

object AccessPathBaseSerializer {
    fun DataOutputStream.writeAccessPathBase(base: AccessPathBase) {
        when (base) {
            is AccessPathBase.Argument -> {
                writeEnum(AccessPathBaseType.ARGUMENT)
                writeInt(base.idx)
            }
            is AccessPathBase.ClassStatic -> {
                writeEnum(AccessPathBaseType.CLASS_STATIC)
                writeUTF(base.typeName)
            }
            is AccessPathBase.Constant -> {
                writeEnum(AccessPathBaseType.CONSTANT)
                writeUTF(base.typeName)
                writeUTF(base.value)
            }
            is AccessPathBase.LocalVar -> {
                writeEnum(AccessPathBaseType.LOCAL_VAR)
                writeInt(base.idx)
            }
            AccessPathBase.This -> writeEnum(AccessPathBaseType.THIS)
            AccessPathBase.Return -> writeEnum(AccessPathBaseType.RETURN)
            AccessPathBase.Exception -> writeEnum(AccessPathBaseType.EXCEPTION)
        }
    }

    fun DataInputStream.readAccessPathBase(): AccessPathBase {
        val kind = readEnum<AccessPathBaseType>()
        return when (kind) {
            AccessPathBaseType.THIS -> AccessPathBase.This
            AccessPathBaseType.LOCAL_VAR -> {
                val idx = readInt()
                AccessPathBase.LocalVar(idx)
            }
            AccessPathBaseType.ARGUMENT -> {
                val idx = readInt()
                AccessPathBase.Argument(idx)
            }
            AccessPathBaseType.CONSTANT -> {
                val typeName = readUTF()
                val value = readUTF()
                AccessPathBase.Constant(typeName, value)
            }
            AccessPathBaseType.CLASS_STATIC -> {
                val typeName = readUTF()
                AccessPathBase.ClassStatic(typeName)
            }
            AccessPathBaseType.RETURN -> AccessPathBase.Return
            AccessPathBaseType.EXCEPTION -> AccessPathBase.Exception
        }
    }

    // TODO: do we need memoization for AccessPathBase-s as well? (Seems useful only for ClassStatic-s)
    private enum class AccessPathBaseType {
        THIS,
        LOCAL_VAR,
        ARGUMENT,
        CONSTANT,
        CLASS_STATIC,
        RETURN,
        EXCEPTION
    }
}