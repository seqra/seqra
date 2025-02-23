package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.opentaint.ir.analysis.graph.reversed
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph

/**
 * Allows to run analysis for any given unit.
 * Note that multiple calls of [run] can be made concurrently for one instance of runner,
 * so the implementations should be thread-safe.
 */
interface IfdsUnitRunner {
    /**
     * Launches some analysis for given [unit], using given [startMethods] as entry points.
     * All start methods should belong to the [unit].
     *
     * @param graph provides supergraph for application (including methods, belonging to other units)
     *
     * @param summary can be used as a knowledge base about other units.
     * Also, anything observed during analysis may be saved to [summary] so that it can be seen by other units.
     *
     * @param unitResolver may be used to get units of methods observed during analysis.
     */
    suspend fun <UnitType> run(
        graph: JIRApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JIRMethod>,
    )
}

/**
 * A composite [IfdsUnitRunner] that launches two runners (backward and forward) in parallel
 * on the same unit with the same startMethods.
 *
 * [backwardRunner] is launched on the reversed application graph,
 * while [forwardRunner] is launched on the direct graph.
 */
class ParallelBidiIfdsUnitRunner(
    private val forwardRunner: IfdsUnitRunner,
    private val backwardRunner: IfdsUnitRunner
) : IfdsUnitRunner {
    override suspend fun <UnitType> run(
        graph: JIRApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JIRMethod>
    ): Unit = coroutineScope {
        launch {
            forwardRunner.run(graph, summary, unitResolver, unit, startMethods)
        }

        launch {
            backwardRunner.run(graph.reversed, summary, unitResolver, unit, startMethods)
        }
    }
}

/**
 * A composite [IfdsUnitRunner] that launches two runners (backward and forward) sequentially
 * on the same unit with the same startMethods.
 *
 * First, it launches [backwardRunner] on the reversed graph and waits for its completion.
 * Then it launches [forwardRunner] on the direct graph.
 */
class SequentialBidiIfdsUnitRunner(
    private val forwardRunner: IfdsUnitRunner,
    private val backwardRunner: IfdsUnitRunner,
) : IfdsUnitRunner {

    override suspend fun <UnitType> run(
        graph: JIRApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JIRMethod>
    ) {
        backwardRunner.run(graph.reversed, summary, unitResolver, unit, startMethods)

        forwardRunner.run(graph, summary, unitResolver, unit, startMethods)
    }
}