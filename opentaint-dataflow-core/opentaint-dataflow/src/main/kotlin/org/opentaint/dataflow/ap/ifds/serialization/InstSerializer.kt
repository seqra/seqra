package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.LanguageManager
import java.io.DataInputStream
import java.io.DataOutputStream

internal class InstSerializer(
    private val languageManager: LanguageManager,
    context: SummarySerializationContext,
) {
    private val methodSerializer = ContextAwareMethodSerializer(
        languageManager.methodSerializer,
        context
    )

    fun DataOutputStream.writeInst(inst: CommonInst) {
        with (methodSerializer) {
            writeMethod(inst.method)
        }
        writeInt(languageManager.getInstIndex(inst))
    }

    fun DataInputStream.readInst(): CommonInst {
        val method = with (methodSerializer) {
            readMethod()
        }
        val instIndex = readInt()
        return languageManager.getInstByIndex(method, instIndex)
    }
}