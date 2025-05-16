package org.opentaint.ir.analysis.taint

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.opentaint.ir.analysis.ifds.ControlEvent
import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.IfdsResult
import org.opentaint.ir.analysis.ifds.Manager
import org.opentaint.ir.analysis.ifds.QueueEmptinessChanged
import org.opentaint.ir.analysis.ifds.Reason
import org.opentaint.ir.analysis.ifds.UnitResolver
import org.opentaint.ir.analysis.ifds.UnitType
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

class TaintBidiRunner<Method, Statement>(
    val manager: TaintManager<Method, Statement>,
    val unitResolver: UnitResolver<Method>,
    override val unit: UnitType,
    newForwardRunner: (Manager<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement>) -> TaintRunner<Method, Statement>,
    newBackwardRunner: (Manager<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement>) -> TaintRunner<Method, Statement>,
) : TaintRunner<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    @Volatile
    private var forwardQueueIsEmpty: Boolean = false

    @Volatile
    private var backwardQueueIsEmpty: Boolean = false

    private val forwardManager: Manager<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement> =
        object : Manager<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement> {
            override fun handleEvent(event: TaintEvent<Method, Statement>) {
                when (event) {
                    is EdgeForOtherRunner<Method, Statement> -> {
                        if (unitResolver.resolve(event.edge.method) == unit) {
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
                handler: (TaintEdge<Method, Statement>) -> Unit,
            ) {
                manager.subscribeOnSummaryEdges(method, scope, handler)
            }
        }

    private val backwardManager: Manager<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement> =
        object : Manager<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement> {
            override fun handleEvent(event: TaintEvent<Method, Statement>) {
                when (event) {
                    is EdgeForOtherRunner -> {
                        check(unitResolver.resolve(event.edge.method) == unit)
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
                handler: (TaintEdge<Method, Statement>) -> Unit,
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
        edge: Edge<TaintDomainFact, Method, Statement>,
        reason: Reason<TaintDomainFact, Method, Statement>,
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

    override fun getIfdsResult(): IfdsResult<TaintDomainFact, Method, Statement> {
        return forwardRunner.getIfdsResult()
    }
}
