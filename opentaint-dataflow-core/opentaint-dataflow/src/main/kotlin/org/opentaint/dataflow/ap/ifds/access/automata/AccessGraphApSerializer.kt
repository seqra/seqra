package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.serialization.AccessorSerializer
import org.opentaint.dataflow.ap.ifds.serialization.ApSerializer
import java.io.DataInputStream
import java.io.DataOutputStream

internal class AccessGraphApSerializer(private val accessorSerializer: AccessorSerializer) : ApSerializer {
    private val accessGraphSerializer = AccessGraph.Serializer(accessorSerializer)

    private fun DataOutputStream.writeAp(base: AccessPathBase, access: AccessGraph, exclusions: ExclusionSet) {
        with (accessorSerializer) {
            writeAccessPathBase(base)
            writeExclusionSet(exclusions)
        }
        with (accessGraphSerializer) {
            writeGraph(access)
        }
    }

    private fun <T> DataInputStream.readAp(builder: (AccessPathBase, AccessGraph, ExclusionSet) -> T): T {
        val base = with (accessorSerializer) {
            readAccessPathBase()
        }
        val exclusions = with (accessorSerializer) {
            readExclusionSet()
        }
        val access = with (accessGraphSerializer) {
            readGraph()
        }
        return builder(base, access, exclusions)
    }

    override fun DataOutputStream.writeFinalAp(ap: FinalFactAp) {
        (ap as AccessGraphFinalFactAp)
        writeAp(ap.base, ap.access, ap.exclusions)
    }

    override fun DataOutputStream.writeInitialAp(ap: InitialFactAp) {
        (ap as AccessGraphInitialFactAp)
        writeAp(ap.base, ap.access, ap.exclusions)
    }

    override fun DataInputStream.readFinalAp(): FinalFactAp {
        return readAp(::AccessGraphFinalFactAp)
    }

    override fun DataInputStream.readInitialAp(): InitialFactAp {
        return readAp(::AccessGraphInitialFactAp)
    }
}