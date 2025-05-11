package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.taint.configuration.TaintMethodSink
import java.util.concurrent.ConcurrentHashMap

/**
 * A common interface for anything that should be remembered and used
 * after the analysis of some unit is completed.
 */
interface SummaryFact {
    val method: JIRMethod
}

/**
 * [SummaryFact] that denotes a possible vulnerability at [sink]
 */
data class VulnerabilityLocation(
    val vulnerabilityDescription: VulnerabilityDescription,
    val sink: IfdsVertex,
    val edge: IfdsEdge? = null,
    val rule: TaintMethodSink? = null,
) : SummaryFact {
    override val method: JIRMethod
        get() = sink.method
}

/**
 * Denotes some start-to-end edge that should be saved for the method
 */
data class SummaryEdgeFact(
    val edge: IfdsEdge,
) : SummaryFact {
    override val method: JIRMethod
        get() = edge.method
}

/**
 * Saves info about cross-unit call.
 * This info could later be used to restore full [TraceGraph]s
 */
data class CrossUnitCallFact(
    val callerVertex: IfdsVertex,
    val calleeVertex: IfdsVertex,
) : SummaryFact {
    override val method: JIRMethod
        get() = callerVertex.method
}

/**
 * Wraps a [TraceGraph] that should be saved for some sink
 */
data class TraceGraphFact(
    val graph: TraceGraph,
) : SummaryFact {
    override val method: JIRMethod
        get() = graph.sink.method
}

/**
 * Contains summaries for many methods and allows to update them and subscribe for them.
 */
interface SummaryStorage<T : SummaryFact> {

    /**
     * Adds [fact] to summary of its method
     */
    fun add(fact: T)

    /**
     * @return a flow with all facts summarized for the given [method].
     * Already received facts, along with the facts that will be sent to this storage later,
     * will be emitted to the returned flow.
     */
    fun getFacts(method: JIRMethod): Flow<T>

    /**
     * @return a list will all facts summarized for the given [method] so far.
     */
    fun getCurrentFacts(method: JIRMethod): List<T>

    /**
     * A list of all methods for which summaries are not empty.
     */
    val knownMethods: List<JIRMethod>
}

class SummaryStorageImpl<T> : SummaryStorage<T>
    where T : SummaryFact {

    private val summaries: MutableMap<JIRMethod, MutableSet<T>> = ConcurrentHashMap()
    private val outFlows: MutableMap<JIRMethod, MutableSharedFlow<T>> = ConcurrentHashMap()

    override fun add(fact: T) {
        val isNew = summaries.computeIfAbsent(fact.method) { ConcurrentHashMap.newKeySet() }.add(fact)
        if (isNew) {
            val flow = outFlows.computeIfAbsent(fact.method) {
                MutableSharedFlow(replay = Int.MAX_VALUE)
            }
            check(flow.tryEmit(fact))
        }
    }

    override fun getFacts(method: JIRMethod): SharedFlow<T> {
        return outFlows[method] ?: MutableSharedFlow()
    }

    override fun getCurrentFacts(method: JIRMethod): List<T> {
        return getFacts(method).replayCache
    }

    override val knownMethods: List<JIRMethod>
        get() = summaries.keys.toList()
}
