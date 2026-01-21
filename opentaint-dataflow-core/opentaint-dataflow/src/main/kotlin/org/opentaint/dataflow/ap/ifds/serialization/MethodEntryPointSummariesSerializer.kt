package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.SideEffectSummary
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import java.io.DataInputStream
import java.io.DataOutputStream

data class MethodEntryPointSummaries(
    val methodEntryPoint: MethodEntryPoint,
    val summaryEdges: List<Edge>,
    val requirements: List<InitialFactAp>,
    val sideEffects: List<SideEffectSummary>,
)

class MethodEntryPointSummariesSerializer(
    summarySerializationContext: SummarySerializationContext,
    languageManager: LanguageManager,
    apManager: ApManager
) {
    private val edgeSerializer = EdgeSerializer(languageManager, apManager, summarySerializationContext)
    private val apSerializer = apManager.createSerializer(summarySerializationContext)

    fun DataOutputStream.writeSummaries(methodEntryPointSummaries: MethodEntryPointSummaries) {
        val (methodEntryPoint, edges, requirements) = methodEntryPointSummaries

        with (edgeSerializer) {
            writeMethodEntryPoint(methodEntryPoint)
        }
        writeInt(edges.size)
        edges.forEach { edge ->
            with (edgeSerializer) {
                writeEdge(edge)
            }
        }
        writeInt(requirements.size)
        requirements.forEach { requirement ->
            with (apSerializer) {
                writeInitialAp(requirement)
            }
        }

        //todo: write side effects
    }

    fun DataInputStream.readSummaries(): MethodEntryPointSummaries {
        val methodEntryPoint = with(edgeSerializer) {
            readMethodEntryPoint()
        }
        val edgesSize = readInt()
        val edges = List(edgesSize) {
            with (edgeSerializer) {
                readEdge()
            }
        }
        val requirementsSize = readInt()
        val requirements = List(requirementsSize) {
            with (apSerializer) {
                readInitialAp()
            }
        }

        // todo: read side effects
        val sideEffects = emptyList<SideEffectSummary>()

        return MethodEntryPointSummaries(methodEntryPoint, edges, requirements, sideEffects)
    }
}