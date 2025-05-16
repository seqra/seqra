package org.opentaint.ir.analysis.ifds

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentHashMap

/**
 * A common interface for anything that should be remembered
 * and used after the analysis of some unit is completed.
 */
interface Summary<out Method : CommonMethod<Method, *>> {
    val method: Method
}

interface SummaryEdge<out Fact, out Method, out Statement> : Summary<Method>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val edge: Edge<Fact, Method, Statement>

    override val method: Method
        get() = edge.method
}

interface Vulnerability<out Fact, out Method, out Statement> : Summary<Method>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {
    val message: String
    val sink: Vertex<Fact, Method, Statement>

    override val method: Method
        get() = sink.method
}

/**
 * Contains summaries for many methods and allows to update them and subscribe for them.
 */
interface SummaryStorage<T : Summary<Method>, out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    /**
     * A list of all methods for which summaries are not empty.
     */
    val knownMethods: List<Method>

    /**
     * Adds [fact] to summary of its method.
     */
    fun add(fact: T)

    /**
     * @return a flow with all facts summarized for the given [method].
     * Already received facts, along with the facts that will be sent to this storage later,
     * will be emitted to the returned flow.
     */
    fun getFacts(method: @UnsafeVariance Method): Flow<T>

    /**
     * @return a list will all facts summarized for the given [method] so far.
     */
    fun getCurrentFacts(method: @UnsafeVariance Method): List<T>
}

class SummaryStorageImpl<T : Summary<Method>, out Method, out Statement> : SummaryStorage<T, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    private val summaries = ConcurrentHashMap<Method, MutableSet<T>>()
    private val outFlows = ConcurrentHashMap<Method, MutableSharedFlow<T>>()

    override val knownMethods: List<Method>
        get() = summaries.keys.toList()

    private fun getFlow(method: Method): MutableSharedFlow<T> {
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

    override fun getFacts(method: @UnsafeVariance Method): SharedFlow<T> {
        return getFlow(method)
    }

    override fun getCurrentFacts(method: @UnsafeVariance Method): List<T> {
        return getFacts(method).replayCache
    }
}
