package org.opentaint.dataflow.ap.ifds.access.cactus

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.serialization.AccessPathBaseSerializer
import org.opentaint.dataflow.ap.ifds.serialization.ApSerializer
import org.opentaint.dataflow.ap.ifds.serialization.ExclusionSetSerializer
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import java.io.DataInputStream
import java.io.DataOutputStream

internal class CactusSerializer(private val context : SummarySerializationContext) : ApSerializer {
    private val accessNodeSerializer = AccessCactus.AccessNode.Serializer(context)
    private val exclusionSetSerializer = ExclusionSetSerializer(context)

    override fun DataOutputStream.writeFinalAp(ap: FinalFactAp) {
        (ap as AccessCactus)
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
        (ap as AccessPathWithCycles)
        with (AccessPathBaseSerializer) {
            writeAccessPathBase(ap.base)
        }
        with (exclusionSetSerializer) {
            writeExclusionSet(ap.exclusions)
        }
        val nodes = ap.access?.toList() ?: emptyList()

        writeInt(nodes.size)
        nodes.forEach { (accessor, cycles) ->
            writeLong(context.getIdByAccessor(accessor))
            writeInt(cycles.size)
            cycles.forEach { cycle ->
                writeInt(cycle.size)
                cycle.forEach { accessor ->
                    writeLong(context.getIdByAccessor(accessor))
                }
            }
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
        return AccessCactus(base, access, exclusions)
    }

    override fun DataInputStream.readInitialAp(): InitialFactAp {
        val base = with(AccessPathBaseSerializer) {
            readAccessPathBase()
        }
        val exclusions = with (exclusionSetSerializer) {
            readExclusionSet()
        }
        val nodesSize = readInt()
        val nodeBuilder = AccessPathWithCycles.AccessNode.Builder()
        repeat(nodesSize) {
            val accessor = context.getAccessorById(readLong())
            val cyclesSize = readInt()
            val cycles = List(cyclesSize) {
                val cycleSize = readInt()
                List(cycleSize) {
                    context.getAccessorById(readLong())
                }
            }
            nodeBuilder.append(accessor, cycles)
        }

        val access = nodeBuilder.build()
        return AccessPathWithCycles(base, access, exclusions)
    }
}