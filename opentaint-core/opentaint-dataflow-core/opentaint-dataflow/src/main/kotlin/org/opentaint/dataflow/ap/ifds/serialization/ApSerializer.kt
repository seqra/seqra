package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import java.io.DataInputStream
import java.io.DataOutputStream

interface ApSerializer {
    fun DataOutputStream.writeFinalAp(ap: FinalFactAp)
    fun DataOutputStream.writeInitialAp(ap: InitialFactAp)

    fun DataInputStream.readFinalAp(): FinalFactAp
    fun DataInputStream.readInitialAp(): InitialFactAp
}