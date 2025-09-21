package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.serialization.AccessorSerializer
import org.opentaint.dataflow.ap.ifds.serialization.ApSerializer
import java.io.DataInputStream
import java.io.DataOutputStream

internal class TreeSerializer(
    private val accessorSerializer: AccessorSerializer
) : ApSerializer {
    private val accessNodeSerializer = AccessTree.AccessNode.Serializer(accessorSerializer)

    override fun DataOutputStream.writeFinalAp(ap: FinalFactAp) {
        (ap as AccessTree)
        with (accessorSerializer) {
            writeAccessPathBase(ap.base)
            writeExclusionSet(ap.exclusions)
        }
        with (accessNodeSerializer) {
            writeAccessNode(ap.access)
        }
    }

    override fun DataOutputStream.writeInitialAp(ap: InitialFactAp) {
        (ap as AccessPath)
        with (accessorSerializer) {
            writeAccessPathBase(ap.base)
            writeExclusionSet(ap.exclusions)
        }

        val accessors = ap.access?.toList() ?: emptyList()
        writeInt(accessors.size)
        accessors.forEach { accessor ->
            with (accessorSerializer) {
                writeAccessor(accessor)
            }
        }
    }

    override fun DataInputStream.readFinalAp(): FinalFactAp {
        val base = with (accessorSerializer) {
            readAccessPathBase()
        }
        val exclusions = with (accessorSerializer) {
            readExclusionSet()
        }
        val access = with (accessNodeSerializer) {
            readAccessNode()
        }
        return AccessTree(base, access, exclusions)
    }

    override fun DataInputStream.readInitialAp(): InitialFactAp {
        val base = with (accessorSerializer) {
            readAccessPathBase()
        }
        val exclusions = with (accessorSerializer) {
            readExclusionSet()
        }

        val accessorsSize = readInt()
        val accessors = List(accessorsSize) {
            with (accessorSerializer) {
                readAccessor()
            }
        }
        val accessNode = AccessPath.AccessNode.createNodeFromAp(accessors.iterator())
        return AccessPath(base, accessNode, exclusions)
    }
}