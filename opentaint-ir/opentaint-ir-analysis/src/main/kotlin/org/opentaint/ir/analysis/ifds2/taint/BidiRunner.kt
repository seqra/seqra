package org.opentaint.ir.analysis.ifds2.taint

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.analysis.engine.UnitType
import org.opentaint.ir.analysis.ifds2.Aggregate
import org.opentaint.ir.analysis.ifds2.ControlEvent
import org.opentaint.ir.analysis.ifds2.Edge
import org.opentaint.ir.analysis.ifds2.Manager
import org.opentaint.ir.analysis.ifds2.QueueEmptinessChanged
import org.opentaint.ir.analysis.ifds2.Runner
import org.opentaint.ir.api.JIRMethod

class BidiRunner(
    val manager: TaintManager,
    val unitResolver: UnitResolver,
    override val unit: UnitType,
    newForwardRunner: (Manager<TaintFact, TaintEvent>) -> TaintRunner,
    newBackwardRunner: (Manager<TaintFact, TaintEvent>) -> TaintRunner,
) : Runner<TaintFact> {

    @Volatile
    private var forwardQueueIsEmpty: Boolean = false

    @Volatile
    private var backwardQueueIsEmpty: Boolean = false

    private val forwardManager: Manager<TaintFact, TaintEvent> =
        object : Manager<TaintFact, TaintEvent> {
            override fun handleEvent(event: TaintEvent) {
                when (event) {
                    is EdgeForOtherRunner -> {
                        if (unitResolver.resolve(event.edge.method) == unit) {
                            // Submit new edge directly to the backward runner:
                            backwardRunner.submitNewEdge(event.edge)
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
                method: JIRMethod,
                scope: CoroutineScope,
                handler: (TaintEdge) -> Unit,
            ) {
                manager.subscribeOnSummaryEdges(method, scope, handler)
            }
        }

    private val backwardManager: Manager<TaintFact, TaintEvent> =
        object : Manager<TaintFact, TaintEvent> {
            override fun handleEvent(event: TaintEvent) {
                when (event) {
                    is EdgeForOtherRunner -> {
                        check(unitResolver.resolve(event.edge.method) == unit)
                        // Submit new edge directly to the forward runner:
                        forwardRunner.submitNewEdge(event.edge)
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
                method: JIRMethod,
                scope: CoroutineScope,
                handler: (TaintEdge) -> Unit,
            ) {
                // TODO: ignore?
                manager.subscribeOnSummaryEdges(method, scope, handler)
            }
        }

    val forwardRunner: TaintRunner = newForwardRunner(forwardManager)
    val backwardRunner: TaintRunner = newBackwardRunner(backwardManager)

    init {
        check(forwardRunner.unit == unit)
        check(backwardRunner.unit == unit)
    }

    override fun submitNewEdge(edge: Edge<TaintFact>) {
        forwardRunner.submitNewEdge(edge)
    }

    override suspend fun run(startMethods: List<JIRMethod>) = coroutineScope {
        val backwardRunnerJob = launch(start = CoroutineStart.LAZY) { backwardRunner.run(startMethods) }
        val forwardRunnerJob = launch(start = CoroutineStart.LAZY) { forwardRunner.run(startMethods) }

        backwardRunnerJob.start()
        forwardRunnerJob.start()

        backwardRunnerJob.join()
        forwardRunnerJob.join()
    }

    override fun getAggregate(): Aggregate<TaintFact> {
        return forwardRunner.getAggregate()
    }
}
