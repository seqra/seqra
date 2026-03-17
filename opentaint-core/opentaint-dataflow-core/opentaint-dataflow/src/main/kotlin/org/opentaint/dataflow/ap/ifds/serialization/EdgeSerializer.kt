package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.access.ApManager
import java.io.DataInputStream
import java.io.DataOutputStream

internal class EdgeSerializer(
    languageManager: LanguageManager,
    apManager: ApManager,
    context: SummarySerializationContext,
) {
    private val instSerializer = InstSerializer(languageManager, context)
    private val apSerializer = apManager.createSerializer(context)
    private val methodContextSerializer = languageManager.methodContextSerializer

    fun DataOutputStream.writeMethodEntryPoint(entryPoint: MethodEntryPoint) {
        with (methodContextSerializer) {
            writeMethodContext(entryPoint.context)
        }
        with (instSerializer) {
            writeInst(entryPoint.statement)
        }
    }

    fun DataOutputStream.writeEdge(edge: Edge) {
        val edgeType = when (edge) {
            is Edge.FactToFact -> EdgeType.FACT_TO_FACT
            is Edge.ZeroToFact -> EdgeType.ZERO_TO_FACT
            is Edge.ZeroToZero -> EdgeType.ZERO_TO_ZERO
            is Edge.NDFactToFact -> EdgeType.ND_FACT_TO_FACT
        }

        writeEnum(edgeType)
        writeMethodEntryPoint(edge.methodEntryPoint)
        with (instSerializer) {
            writeInst(edge.statement)
        }

        when (edge) {
            is Edge.ZeroToZero -> {
                // do nothing
            }

            is Edge.ZeroToFact -> with (apSerializer) {
                writeFinalAp(edge.factAp)
            }

            is Edge.FactToFact -> with (apSerializer) {
                writeInitialAp(edge.initialFactAp)
                writeFinalAp(edge.factAp)
            }

            is Edge.NDFactToFact -> with(apSerializer) {
                writeInt(edge.initialFacts.size)
                edge.initialFacts.forEach { writeInitialAp(it) }
                writeFinalAp(edge.factAp)
            }
        }
    }

    fun DataInputStream.readMethodEntryPoint(): MethodEntryPoint {
        val context = with (methodContextSerializer) {
            readMethodContext()
        }
        val statement = with (instSerializer) {
            readInst()
        }
        return MethodEntryPoint(context, statement)
    }

    fun DataInputStream.readEdge(): Edge {
        val edgeType = readEnum<EdgeType>()
        val methodEntryPoint = readMethodEntryPoint()
        val statement = with (instSerializer) {
            readInst()
        }

        when (edgeType) {
            EdgeType.ZERO_TO_ZERO -> {
                return Edge.ZeroToZero(methodEntryPoint, statement)
            }

            EdgeType.ZERO_TO_FACT -> with(apSerializer) {
                val factAp = readFinalAp()

                return Edge.ZeroToFact(methodEntryPoint, statement, factAp)
            }

            EdgeType.FACT_TO_FACT -> with(apSerializer) {
                val initialFactAp = readInitialAp()
                val factAp = readFinalAp()

                return Edge.FactToFact(methodEntryPoint, initialFactAp, statement, factAp)
            }

            EdgeType.ND_FACT_TO_FACT -> with (apSerializer) {
                val initialFactSize = readInt()
                val initialFacts = List(initialFactSize) {
                    readInitialAp()
                }

                val factAp = readFinalAp()

                return Edge.NDFactToFact(methodEntryPoint, initialFacts.toHashSet(), statement, factAp)
            }
        }
    }

    private enum class EdgeType {
        ZERO_TO_ZERO,
        ZERO_TO_FACT,
        FACT_TO_FACT,
        ND_FACT_TO_FACT,
    }
}