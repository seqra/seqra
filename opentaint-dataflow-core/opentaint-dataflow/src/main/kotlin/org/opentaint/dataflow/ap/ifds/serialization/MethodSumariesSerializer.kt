package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.access.ApManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class MethodSummariesSerializer(
    summarySerializationContext: SummarySerializationContext,
    languageManager: LanguageManager,
    apManager: ApManager,
) {
    private val methodEntryPointSummariesSerializer = MethodEntryPointSummariesSerializer(
        summarySerializationContext,
        languageManager,
        apManager
    )

    fun serializeMethodSummaries(methodSummaries: List<MethodEntryPointSummaries>): ByteArray {
        val serializedSummaries = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(serializedSummaries)).use { stream ->
            stream.writeInt(methodSummaries.size)
            methodSummaries.forEach {
                with (methodEntryPointSummariesSerializer) {
                    stream.writeSummaries(it)
                }
            }
        }
        return serializedSummaries.toByteArray()
    }

    fun deserializeMethodSummaries(serializedSummaries: ByteArray): List<MethodEntryPointSummaries> {
        return DataInputStream(GZIPInputStream(ByteArrayInputStream(serializedSummaries))).use { stream ->
            val methodEntryPointsCnt = stream.readInt()
            List(methodEntryPointsCnt) {
                with (methodEntryPointSummariesSerializer) {
                    stream.readSummaries()
                }
            }
        }
    }
}