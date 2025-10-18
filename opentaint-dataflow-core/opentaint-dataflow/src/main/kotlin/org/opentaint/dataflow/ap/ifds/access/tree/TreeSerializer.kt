package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.serialization.AccessPathBaseSerializer
import org.opentaint.dataflow.ap.ifds.serialization.ApSerializer
import org.opentaint.dataflow.ap.ifds.serialization.ExclusionSetSerializer
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import java.io.DataInputStream
import java.io.DataOutputStream

internal class TreeSerializer(
    private val context: SummarySerializationContext
) : ApSerializer {
    private val accessNodeSerializer = AccessTree.AccessNode.Serializer(context)
    private val exclusionSetSerializer = ExclusionSetSerializer(context)

    override fun DataOutputStream.writeFinalAp(ap: FinalFactAp) {
        (ap as AccessTree)
        with (AccessPathBaseSerializer) {
            writeAccessPathBase(ap.base)
        }
        with (exclusionSetSerializer) {
            writeExclusionSet(ap.exclusions)
        }
        with (accessNodeSerializer) {
            writeAccessNode(ap.access)
        }
    }

    override fun DataOutputStream.writeInitialAp(ap: InitialFactAp) {
        (ap as AccessPath)
        with (AccessPathBaseSerializer) {
            writeAccessPathBase(ap.base)
        }
        with (exclusionSetSerializer) {
            writeExclusionSet(ap.exclusions)
        }

        val accessors = ap.access?.toList() ?: emptyList()
        writeInt(accessors.size)
        accessors.forEach { accessor ->
            writeLong(context.getIdByAccessor(accessor))
        }
    }

    override fun DataInputStream.readFinalAp(): FinalFactAp {
        val base = with (AccessPathBaseSerializer) {
            readAccessPathBase()
        }
        val exclusions = with (exclusionSetSerializer) {
            readExclusionSet()
        }
        val access = with (accessNodeSerializer) {
            readAccessNode()
        }
        return AccessTree(base, access, exclusions)
    }

    override fun DataInputStream.readInitialAp(): InitialFactAp {
        val base = with (AccessPathBaseSerializer) {
            readAccessPathBase()
        }
        val exclusions = with (exclusionSetSerializer) {
            readExclusionSet()
        }

        val accessorsSize = readInt()
        val accessors = List(accessorsSize) {
            context.getAccessorById(readLong())
        }
        val accessNode = AccessPath.AccessNode.createNodeFromAccessors(accessors)
        return AccessPath(base, accessNode, exclusions)
    }
}