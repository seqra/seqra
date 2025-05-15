package org.opentaint.ir.analysis.ifds

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.opentaint.ir.api.JIRMethod
import java.util.concurrent.ConcurrentHashMap

/**
 * A common interface for anything that should be remembered
 * and used after the analysis of some unit is completed.
 */
interface Summary {
    val method: JIRMethod
}

interface SummaryEdge<out Fact> : Summary {
    val edge: Edge<Fact>

    override val method: JIRMethod
        get() = edge.method
}

interface Vulnerability<out Fact> : Summary {
    val message: String
    val sink: Vertex<Fact>

    override val method: JIRMethod
        get() = sink.method
}

/**
 * Contains summaries for many methods and allows to update them and subscribe for them.
 */
interface SummaryStorage<T : Summary> {
    /**
     * A list of all methods for which summaries are not empty.
     */
    val knownMethods: List<JIRMethod>

    /**
     * Adds [fact] to summary of its method.
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
}

class SummaryStorageImpl<T : Summary> : SummaryStorage<T> {
    private val summaries = ConcurrentHashMap<JIRMethod, MutableSet<T>>()
    private val outFlows = ConcurrentHashMap<JIRMethod, MutableSharedFlow<T>>()

    override val knownMethods: List<JIRMethod>
        get() = summaries.keys.toList()

    private fun getFlow(method: JIRMethod): MutableSharedFlow<T> {
        return outFlows.computeIfAbsent(method) {
            MutableSharedFlow(replay = Int.MAX_VALUE)
        }
    }

    override fun add(fact: T) {
        val isNew = summaries.computeIfAbsent(fact.method) { ConcurrentHashMap.newKeySet() }.add(fact)
        if (isNew) {
            val flow = getFlow(fact.method)
            check(flow.tryEmit(fact))
        }
    }

    override fun getFacts(method: JIRMethod): SharedFlow<T> {
        return getFlow(method)
    }

    override fun getCurrentFacts(method: JIRMethod): List<T> {
        return getFacts(method).replayCache
    }
}
