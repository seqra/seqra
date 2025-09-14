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

package org.opentaint.dataflow.taint

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.opentaint.dataflow.ifds.ControlEvent
import org.opentaint.dataflow.ifds.Edge
import org.opentaint.dataflow.ifds.IfdsResult
import org.opentaint.dataflow.ifds.Manager
import org.opentaint.dataflow.ifds.QueueEmptinessChanged
import org.opentaint.dataflow.ifds.Reason
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.graph.ApplicationGraph

class TaintBidiRunner<Method, Statement>(
    val manager: TaintManager<Method, Statement>,
    override val graph: ApplicationGraph<Method, Statement>,
    val unitResolver: UnitResolver<Method>,
    override val unit: UnitType,
    newForwardRunner: (Manager<TaintDomainFact, TaintEvent<Statement>, Method, Statement>) -> TaintRunner<Method, Statement>,
    newBackwardRunner: (Manager<TaintDomainFact, TaintEvent<Statement>, Method, Statement>) -> TaintRunner<Method, Statement>,
) : TaintRunner<Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    @Volatile
    private var forwardQueueIsEmpty: Boolean = false

    @Volatile
    private var backwardQueueIsEmpty: Boolean = false

    private val forwardManager: Manager<TaintDomainFact, TaintEvent<Statement>, Method, Statement> =
        object : Manager<TaintDomainFact, TaintEvent<Statement>, Method, Statement> {
            override fun handleEvent(event: TaintEvent<Statement>) {
                when (event) {
                    is EdgeForOtherRunner<Statement> -> {
                        val m = graph.methodOf(event.edge.from.statement)
                        if (unitResolver.resolve(m) == unit) {
                            // Submit new edge directly to the backward runner:
                            backwardRunner.submitNewEdge(event.edge, event.reason)
                        } else {
                            // Submit new edge via the manager:
                            manager.handleEvent(event)
                        }
                    }

                    else -> manager.handleEvent(event)
                }
            }

            override fun handleControlEvent(event: ControlEvent) {
                when (event) {
                    is QueueEmptinessChanged -> {
                        forwardQueueIsEmpty = event.isEmpty
                        val newEvent = QueueEmptinessChanged(event.runner, forwardQueueIsEmpty && backwardQueueIsEmpty)
                        manager.handleControlEvent(newEvent)
                    }
                }
            }

            override fun subscribeOnSummaryEdges(
                method: Method,
                scope: CoroutineScope,
                handler: (TaintEdge<Statement>) -> Unit,
            ) {
                manager.subscribeOnSummaryEdges(method, scope, handler)
            }
        }

    private val backwardManager: Manager<TaintDomainFact, TaintEvent<Statement>, Method, Statement> =
        object : Manager<TaintDomainFact, TaintEvent<Statement>, Method, Statement> {
            override fun handleEvent(event: TaintEvent<Statement>) {
                when (event) {
                    is EdgeForOtherRunner -> {
                        val m = graph.methodOf(event.edge.from.statement)
                        check(unitResolver.resolve(m) == unit)
                        // Submit new edge directly to the forward runner:
                        forwardRunner.submitNewEdge(event.edge, event.reason)
                    }

                    else -> manager.handleEvent(event)
                }
            }

            override fun handleControlEvent(event: ControlEvent) {
                when (event) {
                    is QueueEmptinessChanged -> {
                        backwardQueueIsEmpty = event.isEmpty
                        val newEvent = QueueEmptinessChanged(event.runner, forwardQueueIsEmpty && backwardQueueIsEmpty)
                        manager.handleControlEvent(newEvent)
                    }
                }
            }

            override fun subscribeOnSummaryEdges(
                method: Method,
                scope: CoroutineScope,
                handler: (TaintEdge<Statement>) -> Unit,
            ) {
                // TODO: ignore?
                manager.subscribeOnSummaryEdges(method, scope, handler)
            }
        }

    val forwardRunner: TaintRunner<Method, Statement> = newForwardRunner(forwardManager)
    val backwardRunner: TaintRunner<Method, Statement> = newBackwardRunner(backwardManager)

    init {
        check(forwardRunner.unit == unit)
        check(backwardRunner.unit == unit)
    }

    override fun submitNewEdge(
        edge: Edge<TaintDomainFact, Statement>,
        reason: Reason<TaintDomainFact, Statement>,
    ) {
        forwardRunner.submitNewEdge(edge, reason)
    }

    override suspend fun run(startMethods: List<Method>) = coroutineScope {
        val backwardRunnerJob = launch(start = CoroutineStart.LAZY) { backwardRunner.run(startMethods) }
        val forwardRunnerJob = launch(start = CoroutineStart.LAZY) { forwardRunner.run(startMethods) }

        backwardRunnerJob.start()
        forwardRunnerJob.start()

        backwardRunnerJob.join()
        forwardRunnerJob.join()
    }

    override fun getIfdsResult(): IfdsResult<TaintDomainFact, Statement> {
        return forwardRunner.getIfdsResult()
    }
}
