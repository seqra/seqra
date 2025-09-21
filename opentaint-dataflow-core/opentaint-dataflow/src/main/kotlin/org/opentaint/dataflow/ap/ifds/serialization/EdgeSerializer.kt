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
    private val accessorSerializer = AccessorSerializer(context)
    private val instSerializer = InstSerializer(languageManager, context)
    private val apSerializer = apManager.createSerializer(accessorSerializer)
    private val methodSerializer = ContextAwareMethodSerializer(
        languageManager.methodSerializer,
        context
    )

    private fun DataOutputStream.writeMethodEntryPoint(entryPoint: MethodEntryPoint) {
        with (methodSerializer) {
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
        }

        writeEnum(edgeType)
        writeMethodEntryPoint(edge.methodEntryPoint)
        with (instSerializer) {
            writeInst(edge.statement)
        }

        if (edge is Edge.ZeroToFact) {
            with (apSerializer) {
                writeFinalAp(edge.factAp)
            }
        }
        if (edge is Edge.FactToFact) {
            with (apSerializer) {
                writeInitialAp(edge.initialFactAp)
                writeFinalAp(edge.factAp)
            }
        }
    }

    private fun DataInputStream.readMethodEntryPoint(): MethodEntryPoint {
        val context = with (methodSerializer) {
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
            EdgeType.ZERO_TO_FACT -> {
                val factAp = with (apSerializer) {
                    readFinalAp()
                }
                return Edge.ZeroToFact(methodEntryPoint, statement, factAp)
            }
            EdgeType.FACT_TO_FACT -> {
                val initialFactAp = with (apSerializer) {
                    readInitialAp()
                }
                val factAp = with (apSerializer) {
                    readFinalAp()
                }
                return Edge.FactToFact(methodEntryPoint, initialFactAp, statement, factAp)
            }
        }
    }

    private enum class EdgeType {
        ZERO_TO_ZERO,
        ZERO_TO_FACT,
        FACT_TO_FACT
    }
}