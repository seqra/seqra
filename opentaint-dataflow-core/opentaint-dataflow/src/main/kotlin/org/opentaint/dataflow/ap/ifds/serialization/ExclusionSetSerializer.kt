package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import java.io.DataInputStream
import java.io.DataOutputStream

class ExclusionSetSerializer(private val context: SummarySerializationContext) {
    fun DataOutputStream.writeExclusionSet(exclusionSet: ExclusionSet) {
        when (exclusionSet) {
            ExclusionSet.Empty -> writeEnum(ExclusionSetType.EMPTY)
            ExclusionSet.Universe -> writeEnum(ExclusionSetType.UNIVERSE)
            is ExclusionSet.Concrete -> {
                writeEnum(ExclusionSetType.CONCRETE)
                writeInt(exclusionSet.set.size)
                exclusionSet.set.forEach {
                    writeLong(context.getIdByAccessor(it))
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
                val accessors = List(size) { context.getAccessorById(readLong()) }
                accessors.map(ExclusionSet::Concrete).reduce(ExclusionSet::union)
            }
        }
    }

    private enum class ExclusionSetType {
        EMPTY,
        UNIVERSE,
        CONCRETE
    }
}