package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import org.opentaint.ir.analysis.graph.reversed
import org.opentaint.ir.api.core.CoreMethod
import org.opentaint.ir.api.core.analysis.ApplicationGraph
import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation

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
class BidiIfdsUnitRunnerFactory<Method, Location, Statement>(
    private val forwardRunnerFactory: IfdsUnitRunnerFactory<Method, Location, Statement>,
    private val backwardRunnerFactory: IfdsUnitRunnerFactory<Method, Location, Statement>,
    private val isParallel: Boolean = true
) : IfdsUnitRunnerFactory<Method, Location, Statement>
        where Method : CoreMethod<Statement>,
              Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, *> {

    private inner class BidiIfdsUnitRunner<UnitType>(
        graph: ApplicationGraph<Method, Statement>,
        private val manager: IfdsUnitManager<UnitType, Method, Location, Statement>,
        private val unitResolver: UnitResolver<UnitType, Method>,
        unit: UnitType,
        startMethods: List<Method>,
    ) : AbstractIfdsUnitRunner<UnitType, Method, Location, Statement>(unit) {

        @Volatile
        private var forwardQueueIsEmpty: Boolean = false

        @Volatile
        private var backwardQueueIsEmpty: Boolean = false

        private val forwardManager: IfdsUnitManager<UnitType, Method, Location, Statement> =
            object : IfdsUnitManager<UnitType, Method, Location, Statement> by manager {

                override suspend fun handleEvent(
                    event: IfdsUnitRunnerEvent,
                    runner: IfdsUnitRunner<UnitType, Method, Location, Statement>
                ) {
                    when (event) {
                        is EdgeForOtherRunnerQuery<*, *, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            event as EdgeForOtherRunnerQuery<Method, Location, Statement>
                            if (unitResolver.resolve(event.edge.method) == unit) {
                                backwardRunner.submitNewEdge(event.edge)
                            } else {
                                manager.handleEvent(event, this@BidiIfdsUnitRunner)
                            }
                        }

                        is NewSummaryFact<*> -> {
                            manager.handleEvent(event, this@BidiIfdsUnitRunner)
                        }

                        is QueueEmptinessChanged -> {
                            forwardQueueIsEmpty = event.isEmpty
                            val newEvent = QueueEmptinessChanged(backwardQueueIsEmpty && forwardQueueIsEmpty)
                            manager.handleEvent(newEvent, this@BidiIfdsUnitRunner)
                        }

                        is SubscriptionForSummaryEdges<*, *, *> -> {
                            manager.handleEvent(event, this@BidiIfdsUnitRunner)
                        }
                    }
                }
            }

        private val backwardManager: IfdsUnitManager<UnitType, Method, Location, Statement> =
            object : IfdsUnitManager<UnitType, Method, Location, Statement> {
                override suspend fun handleEvent(
                    event: IfdsUnitRunnerEvent,
                    runner: IfdsUnitRunner<UnitType, Method, Location, Statement>
                ) {
                    when (event) {
                        is EdgeForOtherRunnerQuery<*, *, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            event as EdgeForOtherRunnerQuery<Method, Location, Statement>
                            if (unitResolver.resolve(event.edge.method) == unit) {
                                forwardRunner.submitNewEdge(event.edge)
                            }
                        }

                        is NewSummaryFact<*> -> {
                            manager.handleEvent(event, this@BidiIfdsUnitRunner)
                        }

                        is QueueEmptinessChanged -> {
                            backwardQueueIsEmpty = event.isEmpty
                            if (!isParallel && event.isEmpty) {
                                runner.job?.cancel() ?: error("Runner job is not instantiated")
                            }
                            val newEvent =
                                QueueEmptinessChanged(backwardQueueIsEmpty && forwardQueueIsEmpty)
                            manager.handleEvent(newEvent, this@BidiIfdsUnitRunner)
                        }

                        is SubscriptionForSummaryEdges<*, *, *> -> {}
                    }
                }
            }

        private val backwardRunner: IfdsUnitRunner<UnitType, Method, Location, Statement> = backwardRunnerFactory
            .newRunner(graph.reversed, backwardManager, unitResolver, unit, startMethods)

        private val forwardRunner: IfdsUnitRunner<UnitType, Method, Location, Statement> = forwardRunnerFactory
            .newRunner(graph, forwardManager, unitResolver, unit, startMethods)

        override suspend fun submitNewEdge(edge: IfdsEdge<Method, Location, Statement>) {
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

    override fun <UnitType> newRunner(
        graph: ApplicationGraph<Method, Statement>,
        manager: IfdsUnitManager<UnitType, Method, Location, Statement>,
        unitResolver: UnitResolver<UnitType, Method>,
        unit: UnitType,
        startMethods: List<Method>
    ): IfdsUnitRunner<UnitType, Method, Location, Statement> = BidiIfdsUnitRunner(graph, manager, unitResolver, unit, startMethods)
}