package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * A common interface for anything that should be remembered and used
 * after the analysis of some unit is completed.
 */
sealed interface SummaryFact<Method> {
    val method: Method
}

/**
 * [SummaryFact] that denotes a possible vulnerability at [sink]
 */
data class VulnerabilityLocation<Method, Location, Statement>(
    val vulnerabilityDescription: VulnerabilityDescription,
    val sink: IfdsVertex<Method, Location, Statement>
) : SummaryFact<Method> where Location : CoreInstLocation<Method>,
                              Statement : CoreInst<Location, Method, *> {
    override val method: Method = sink.method
}

/**
 * Denotes some start-to-end edge that should be saved for the method
 */
data class SummaryEdgeFact<Method, Location, Statement>(
    val edge: IfdsEdge<Method, Location, Statement>
) : SummaryFact<Method> where Location : CoreInstLocation<Method>,
                      Statement : CoreInst<Location, Method, *> {
    override val method: Method = edge.method
}

/**
 * Saves info about cross-unit call.
 * This info could later be used to restore full [TraceGraph]s
 */
data class CrossUnitCallFact<Method, Location, Statement>(
    val callerVertex: IfdsVertex<Method, Location, Statement>,
    val calleeVertex: IfdsVertex<Method, Location, Statement>
) : SummaryFact<Method> where Location : CoreInstLocation<Method>,
                      Statement : CoreInst<Location, Method, *> {
    override val method: Method = callerVertex.method
}

/**
 * Wraps a [TraceGraph] that should be saved for some sink
 */
data class TraceGraphFact<Method>(val graph: TraceGraph) : SummaryFact<Method> {
    override val method: Method = graph.sink.method
}

/**
 * Contains summaries for many methods and allows to update them and subscribe for them.
 */
interface SummaryStorage<T: SummaryFact<Method>, Method> {
    /**
     * Adds [fact] to summary of its method
     */
    fun send(fact: T)

    /**
     * @return a flow with all facts summarized for the given [method].
     * Already received facts, along with the facts that will be sent to this storage later,
     * will be emitted to the returned flow.
     */
    fun getFacts(method: Method): Flow<T>

    /**
     * @return a list will all facts summarized for the given [method] so far.
     */
    fun getCurrentFacts(method: Method): List<T>

    /**
     * A list of all methods for which summaries are not empty.
     */
    val knownMethods: List<Method>
}

class SummaryStorageImpl<T: SummaryFact<Method>, Method> : SummaryStorage<T, Method> {
    private val summaries: MutableMap<Method, MutableSet<T>> = ConcurrentHashMap()
    private val outFlows: MutableMap<Method, MutableSharedFlow<T>> = ConcurrentHashMap()

    override fun send(fact: T) {
        if (summaries.computeIfAbsent(fact.method) { ConcurrentHashMap.newKeySet() }.add(fact)) {
            val outFlow = outFlows.computeIfAbsent(fact.method) { MutableSharedFlow(replay = Int.MAX_VALUE) }
            require(outFlow.tryEmit(fact))
        }
    }

    override fun getFacts(method: Method): SharedFlow<T> {
        return outFlows.computeIfAbsent(method) {
            MutableSharedFlow<T>(replay = Int.MAX_VALUE).also { flow ->
                summaries[method].orEmpty().forEach { fact ->
                    require(flow.tryEmit(fact))
                }
            }
        }
    }

    override fun getCurrentFacts(method: Method): List<T> {
        return getFacts(method).replayCache
    }

    override val knownMethods: List<Method>
        get() = summaries.keys.toList()
}