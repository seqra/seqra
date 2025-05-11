package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import org.opentaint.ir.analysis.graph.BackwardJIRApplicationGraph
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph

/**
 * This factory produces composite runners. Each of them launches two runners (backward and forward)
 * on the same unit with the same startMethods.
 *
 * Backward runner is launched on the reversed application graph,
 * while forward runner is launched on the direct graph.
 *
 * Both runners will be given their own managers with the following policy:
 * - all [NewSummaryFact] events are delegated to the outer manager
 * - [EdgeForOtherRunnerQuery] events are submitted to the other runner if the corresponding edge
 * belongs to the same unit, otherwise they are transmitted to the outer manager (for forward runner)
 * or ignored (for backward runner)
 * - Queue is thought to be empty when queues of both forward and backward runners are empty.
 * The [QueueEmptinessChanged] event is sent to outer manager correspondingly.
 * - [SubscriptionForSummaryEdges] event is delegated to the outer manager for forward runner and
 * is ignored for backward runner
 *
 * @param forwardRunnerFactory a factory that produces forward runner for each [newRunner] call
 * @param backwardRunnerFactory a factory that produces backward runner for each [newRunner] call
 * @param isParallel if true, the produced composite runner will launch backward and forward runners in parallel.
 * Otherwise, the backward runner will be executed first, and after it, the forward runner will be executed.
 */
class BidiIfdsUnitRunnerFactory(
    private val forwardRunnerFactory: IfdsUnitRunnerFactory,
    private val backwardRunnerFactory: IfdsUnitRunnerFactory,
    private val isParallel: Boolean = true,
) : IfdsUnitRunnerFactory {

    override fun newRunner(
        graph: JIRApplicationGraph,
        manager: IfdsUnitManager,
        unitResolver: UnitResolver,
        unit: UnitType,
        startMethods: List<JIRMethod>,
    ): IfdsUnitRunner = BidiIfdsUnitRunner(graph, manager, unitResolver, unit, startMethods)

    internal inner class BidiIfdsUnitRunner(
        graph: JIRApplicationGraph,
        private val manager: IfdsUnitManager,
        private val unitResolver: UnitResolver,
        unit: UnitType,
        startMethods: List<JIRMethod>,
    ) : AbstractIfdsUnitRunner(unit) {

        @Volatile
        private var forwardQueueIsEmpty: Boolean = false

        @Volatile
        private var backwardQueueIsEmpty: Boolean = false

        private val forwardManager: IfdsUnitManager = object : IfdsUnitManager by manager {
            override suspend fun handleEvent(event: IfdsUnitRunnerEvent, runner: IfdsUnitRunner) {
                when (event) {
                    is EdgeForOtherRunnerQuery -> {
                        if (unitResolver.resolve(event.edge.method) == unit) {
                            // Submit new edge directly to the backward runner:
                            backwardRunner.submitNewEdge(event.edge)
                        } else {
                            // Submit new edge via the manager:
                            manager.handleEvent(event, this@BidiIfdsUnitRunner)
                        }
                    }

                    is NewSummaryFact -> {
                        manager.handleEvent(event, this@BidiIfdsUnitRunner)
                    }

                    is QueueEmptinessChanged -> {
                        forwardQueueIsEmpty = event.isEmpty
                        val newEvent = QueueEmptinessChanged(backwardQueueIsEmpty && forwardQueueIsEmpty)
                        manager.handleEvent(newEvent, this@BidiIfdsUnitRunner)
                    }

                    is SubscriptionForSummaryEdges -> {
                        manager.handleEvent(event, this@BidiIfdsUnitRunner)
                    }
                }
            }
        }

        private val backwardManager: IfdsUnitManager = object : IfdsUnitManager {
            override suspend fun handleEvent(event: IfdsUnitRunnerEvent, runner: IfdsUnitRunner) {
                when (event) {
                    is EdgeForOtherRunnerQuery -> {
                        if (unitResolver.resolve(event.edge.method) == unit) {
                            forwardRunner.submitNewEdge(event.edge)
                        }
                    }

                    is NewSummaryFact -> {
                        manager.handleEvent(event, this@BidiIfdsUnitRunner)
                    }

                    is QueueEmptinessChanged -> {
                        backwardQueueIsEmpty = event.isEmpty
                        if (!isParallel && event.isEmpty) {
                            runner.job?.cancel() ?: error("Runner job is not instantiated")
                        }
                        val newEvent = QueueEmptinessChanged(backwardQueueIsEmpty && forwardQueueIsEmpty)
                        manager.handleEvent(newEvent, this@BidiIfdsUnitRunner)
                    }

                    is SubscriptionForSummaryEdges -> {}
                }
            }
        }

        internal val backwardRunner: IfdsUnitRunner = backwardRunnerFactory
            .newRunner(BackwardJIRApplicationGraph(graph), backwardManager, unitResolver, unit, startMethods)

        internal val forwardRunner: IfdsUnitRunner = forwardRunnerFactory
            .newRunner(graph, forwardManager, unitResolver, unit, startMethods)

        override suspend fun submitNewEdge(edge: IfdsEdge) {
            forwardRunner.submitNewEdge(edge)
        }

        override suspend fun run() = coroutineScope {
            val backwardRunnerJob: Job = backwardRunner.launchIn(this)
            val forwardRunnerJob: Job

            if (isParallel) {
                forwardRunnerJob = forwardRunner.launchIn(this)

                backwardRunnerJob.join()
                forwardRunnerJob.join()
            } else {
                backwardRunnerJob.join()

                forwardRunnerJob = forwardRunner.launchIn(this)
                forwardRunnerJob.join()
            }
        }
    }
}
