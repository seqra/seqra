package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.LanguageManager
import java.io.DataInputStream
import java.io.DataOutputStream

internal class InstSerializer(
    private val languageManager: LanguageManager,
    private val context: SummarySerializationContext,
) {
    fun DataOutputStream.writeInst(inst: CommonInst) {
        writeLong(context.getIdByMethod(inst.location.method))
        writeInt(languageManager.getInstIndex(inst))
    }

    fun DataInputStream.readInst(): CommonInst {
        val method = context.getMethodById(readLong())
        val instIndex = readInt()
        return languageManager.getInstByIndex(method, instIndex)
    }
}