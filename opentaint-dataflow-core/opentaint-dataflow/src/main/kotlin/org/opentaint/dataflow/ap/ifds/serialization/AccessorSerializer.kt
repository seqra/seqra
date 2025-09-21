package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import java.io.DataInputStream
import java.io.DataOutputStream

class AccessorSerializer(
    private val serializationContext: SummarySerializationContext
) {
    private fun DataOutputStream.serializeFieldAccessor(accessor: FieldAccessor): Int {
        serializationContext.serializedAccessors[accessor]?.let { return it }

        val id = serializationContext.serializedAccessors.size
        writeEnum(AccessorValueType.NEW_FIELD_ACCESSOR)
        writeInt(id)
        writeUTF(accessor.className)
        writeUTF(accessor.fieldName)
        writeUTF(accessor.fieldType)

        serializationContext.serializedAccessors[accessor] = id
        return id
    }

    private fun DataOutputStream.serializeTaintMarkAccessor(accessor: TaintMarkAccessor): Int {
        serializationContext.serializedAccessors[accessor]?.let { return it }

        val id = serializationContext.serializedAccessors.size
        writeEnum(AccessorValueType.NEW_TAINT_MARK_ACCESSOR)
        writeInt(id)
        writeUTF(accessor.mark.name)

        serializationContext.serializedAccessors[accessor] = id
        return id
    }

    fun DataOutputStream.writeAccessor(accessor: Accessor) {
        when (accessor) {
            AnyAccessor -> writeEnum(AccessorValueType.ANY_ACCESSOR)
            ElementAccessor -> writeEnum(AccessorValueType.ELEMENT_ACCESSOR)
            FinalAccessor -> writeEnum(AccessorValueType.FINAL_ACCESSOR)
            is FieldAccessor -> {
                val id = serializeFieldAccessor(accessor)
                writeEnum(AccessorValueType.SERIALIZED)
                writeInt(id)
            }
            is TaintMarkAccessor -> {
                val id = serializeTaintMarkAccessor(accessor)
                writeEnum(AccessorValueType.SERIALIZED)
                writeInt(id)
            }
        }
    }

    private fun DataInputStream.deserializeTaintMarkAccessor(): TaintMarkAccessor {
        val name = readUTF()
        return TaintMarkAccessor(TaintMark(name))
    }

    private fun DataInputStream.deserializeFieldAccessor(): FieldAccessor {
        val className = readUTF()
        val fieldName = readUTF()
        val fieldType = readUTF()
        return FieldAccessor(className, fieldName, fieldType)
    }

    fun DataInputStream.readAccessor(): Accessor {
        val kind = readEnum<AccessorValueType>()
        return when (kind) {
            AccessorValueType.ANY_ACCESSOR -> AnyAccessor
            AccessorValueType.ELEMENT_ACCESSOR -> ElementAccessor
            AccessorValueType.FINAL_ACCESSOR -> FinalAccessor

            AccessorValueType.SERIALIZED -> {
                val id = readInt()
                serializationContext.deserializedAccessors[id]!!
            }

            AccessorValueType.NEW_TAINT_MARK_ACCESSOR -> {
                val id = readInt()
                serializationContext.deserializedAccessors[id] = deserializeTaintMarkAccessor()
                readAccessor()
            }
            AccessorValueType.NEW_FIELD_ACCESSOR -> {
                val id = readInt()
                serializationContext.deserializedAccessors[id] = deserializeFieldAccessor()
                readAccessor()
            }
        }
    }

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
        }
    }

    fun DataOutputStream.writeExclusionSet(exclusionSet: ExclusionSet) {
        when (exclusionSet) {
            ExclusionSet.Empty -> writeEnum(ExclusionSetType.EMPTY)
            ExclusionSet.Universe -> writeEnum(ExclusionSetType.UNIVERSE)
            is ExclusionSet.Concrete -> {
                writeEnum(ExclusionSetType.CONCRETE)
                writeInt(exclusionSet.set.size)
                exclusionSet.set.forEach {
                    writeAccessor(it)
                }
            }
        }
    }

    fun DataInputStream.readExclusionSet(): ExclusionSet {
        val kind = readEnum<ExclusionSetType>()
        return when (kind) {
            ExclusionSetType.EMPTY -> ExclusionSet.Empty
            ExclusionSetType.UNIVERSE -> ExclusionSet.Universe
            ExclusionSetType.CONCRETE -> {
                val size = readInt()
                val accessors = List(size) { readAccessor() }
                accessors.map(ExclusionSet::Concrete).reduce(ExclusionSet::union)
            }
        }
    }

    private enum class AccessorValueType {
        SERIALIZED,
        NEW_TAINT_MARK_ACCESSOR,
        NEW_FIELD_ACCESSOR,
        ANY_ACCESSOR,
        ELEMENT_ACCESSOR,
        FINAL_ACCESSOR
    }

    // TODO: do we need memoization for AccessPathBase-s as well? (Seems useful only for ClassStatic-s)
    private enum class AccessPathBaseType {
        THIS,
        LOCAL_VAR,
        ARGUMENT,
        CONSTANT,
        CLASS_STATIC
    }

    private enum class ExclusionSetType {
        EMPTY,
        UNIVERSE,
        CONCRETE
    }
}