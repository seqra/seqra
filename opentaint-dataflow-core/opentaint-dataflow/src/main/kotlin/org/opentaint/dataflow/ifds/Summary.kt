/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.ifds

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
interface Summary<out Method : CommonMethod> {
    val method: Method
}

interface SummaryEdge<out Fact, out Statement : CommonInst> : Summary<CommonMethod> {

    val edge: Edge<Fact, Statement>

    override val method: CommonMethod
        get() = edge.method
}

interface Vulnerability<out Fact, out Statement : CommonInst> : Summary<CommonMethod> {
    val message: String
    val sink: Vertex<Fact, Statement>

    override val method: CommonMethod
        get() = sink.method
}

/**
 * Contains summaries for many methods and allows to update them and subscribe for them.
 */
interface SummaryStorage<T : Summary<*>> {

    /**
     * A list of all methods for which summaries are not empty.
     */
    val knownMethods: List<CommonMethod>

    /**
     * Adds [summary] the summaries storage of its method.
     */
    fun add(summary: T)

    /**
     * @return a flow with all facts summarized for the given [method].
     * Already received facts, along with the facts that will be sent to this storage later,
     * will be emitted to the returned flow.
     */
    fun getFacts(method: CommonMethod): Flow<T>

    /**
     * @return a list will all facts summarized for the given [method] so far.
     */
    fun getCurrentFacts(method: CommonMethod): List<T>
}

class SummaryStorageImpl<T : Summary<*>> : SummaryStorage<T> {

    private val summaries = ConcurrentHashMap<CommonMethod, MutableSet<T>>()
    private val outFlows = ConcurrentHashMap<CommonMethod, MutableSharedFlow<T>>()

    override val knownMethods: List<CommonMethod>
        get() = summaries.keys.toList()

    private fun getFlow(method: CommonMethod): MutableSharedFlow<T> {
        return outFlows.computeIfAbsent(method) {
            MutableSharedFlow(replay = Int.MAX_VALUE)
        }
    }

    override fun add(summary: T) {
        val isNew = summaries.computeIfAbsent(summary.method) {
            ConcurrentHashMap.newKeySet()
        }.add(summary)
        if (isNew) {
            val flow = getFlow(summary.method)
            check(flow.tryEmit(summary))
        }
    }

    override fun getFacts(method: CommonMethod): SharedFlow<T> {
        return getFlow(method)
    }

    override fun getCurrentFacts(method: CommonMethod): List<T> {
        return getFacts(method).replayCache
    }
}
