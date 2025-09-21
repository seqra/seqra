package org.opentaint.dataflow.ap.ifds.access.cactus

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.serialization.AccessorSerializer
import org.opentaint.dataflow.ap.ifds.serialization.ApSerializer
import java.io.DataInputStream
import java.io.DataOutputStream

internal class CactusSerializer(private val accessorSerializer: AccessorSerializer) : ApSerializer {
    private val accessNodeSerializer = AccessCactus.AccessNode.Serializer(accessorSerializer)

    override fun DataOutputStream.writeFinalAp(ap: FinalFactAp) {
        (ap as AccessCactus)
        with (accessorSerializer) {
            writeAccessPathBase(ap.base)
            writeExclusionSet(ap.exclusions)
        }
        with (accessNodeSerializer) {
            writeAccessNode(ap.access)
        }
    }

    override fun DataOutputStream.writeInitialAp(ap: InitialFactAp) {
        (ap as AccessPathWithCycles)
        with (accessorSerializer) {
            writeAccessPathBase(ap.base)
            writeExclusionSet(ap.exclusions)
        }
        val nodes = ap.access?.toList() ?: emptyList()

        writeInt(nodes.size)
        nodes.forEach { (accessor, cycles) ->
            with (accessorSerializer) {
                writeAccessor(accessor)
            }
            writeInt(cycles.size)
            cycles.forEach { cycle ->
                writeInt(cycle.size)
                cycle.forEach { accessor ->
                    with (accessorSerializer) {
                        writeAccessor(accessor)
                    }
                }
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
        return AccessCactus(base, access, exclusions)
    }

    override fun DataInputStream.readInitialAp(): InitialFactAp {
        val base = with(accessorSerializer) {
            readAccessPathBase()
        }
        val exclusions = with (accessorSerializer) {
            readExclusionSet()
        }
        val nodesSize = readInt()
        val nodeBuilder = AccessPathWithCycles.AccessNode.Builder()
        repeat(nodesSize) {
            val accessor = with (accessorSerializer) {
                readAccessor()
            }
            val cyclesSize = readInt()
            val cycles = List(cyclesSize) {
                val cycleSize = readInt()
                List(cycleSize) {
                    with (accessorSerializer) {
                        readAccessor()
                    }
                }
            }
            nodeBuilder.append(accessor, cycles)
        }

        val access = nodeBuilder.build()
        return AccessPathWithCycles(base, access, exclusions)
    }
}