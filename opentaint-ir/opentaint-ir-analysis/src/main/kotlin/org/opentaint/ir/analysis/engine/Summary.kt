package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.opentaint.ir.api.JIRMethod
import java.util.concurrent.ConcurrentHashMap

sealed interface SummaryFact {
    val method: JIRMethod
}

data class VulnerabilityLocation(val vulnerabilityType: String, val sink: IfdsVertex) : SummaryFact {
    override val method: JIRMethod = sink.method
}

data class PathEdgeFact(val edge: IfdsEdge) : SummaryFact {
    override val method: JIRMethod = edge.method
}

data class CrossUnitCallFact(val callerVertex: IfdsVertex, val calleeVertex: IfdsVertex) : SummaryFact {
    override val method: JIRMethod = callerVertex.method
}

data class TraceGraphFact(val graph: TraceGraph) : SummaryFact {
    override val method: JIRMethod = graph.sink.method
}

fun interface SummarySender {
    fun send(fact: SummaryFact)
}

interface Summary {
    fun createSender(method: JIRMethod): SummarySender

    fun getFacts(method: JIRMethod, startVertex: IfdsVertex?): Flow<SummaryFact>

    fun getCurrentFacts(method: JIRMethod, startVertex: IfdsVertex?): List<SummaryFact>

    val knownMethods: List<JIRMethod>
}

class SummaryImpl : Summary {
    private val loadedFacts: MutableMap<JIRMethod, MutableSet<SummaryFact>> = ConcurrentHashMap()
    private val outFlows: MutableMap<JIRMethod, MutableSharedFlow<SummaryFact>> = ConcurrentHashMap()

    override fun createSender(method: JIRMethod): SummarySender {
        return SummarySender { fact ->
            if (loadedFacts.computeIfAbsent(method) { ConcurrentHashMap.newKeySet() }.add(fact)) {
                val outFlow = outFlows.computeIfAbsent(method) { MutableSharedFlow(replay = Int.MAX_VALUE) }
                require(outFlow.tryEmit(fact))
            }
        }
    }

    override fun getFacts(method: JIRMethod, startVertex: IfdsVertex?): SharedFlow<SummaryFact> {
        return outFlows.computeIfAbsent(method) {
            MutableSharedFlow<SummaryFact>(replay = Int.MAX_VALUE).also { flow ->
                loadedFacts[method].orEmpty().forEach { fact ->
                    require(flow.tryEmit(fact))
                }
            }
        }
    }

    override fun getCurrentFacts(method: JIRMethod, startVertex: IfdsVertex?): List<SummaryFact> {
        return getFacts(method, startVertex).replayCache
    }

    override val knownMethods: List<JIRMethod>
        get() = loadedFacts.keys.toList()
}

inline fun <reified T : SummaryFact> Summary.getFactsFiltered(method: JIRMethod, startVertex: IfdsVertex?): Flow<T> {
    return getFacts(method, startVertex).filterIsInstance<T>()
}

inline fun <reified T : SummaryFact> Summary.getCurrentFactsFiltered(method: JIRMethod, startVertex: IfdsVertex?): List<T> {
    return getCurrentFacts(method, startVertex).filterIsInstance<T>()
}