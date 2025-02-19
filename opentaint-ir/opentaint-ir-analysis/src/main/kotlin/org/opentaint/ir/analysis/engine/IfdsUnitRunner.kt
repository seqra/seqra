package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.opentaint.ir.analysis.graph.reversed
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph

interface IfdsUnitRunner {
    suspend fun <UnitType> run(
        graph: JIRApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JIRMethod>,
    )
}

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

class SequentialBidiIfdsUnitRunner(
    private val forward: IfdsUnitRunner,
    private val backward: IfdsUnitRunner,
) : IfdsUnitRunner {

    override suspend fun <UnitType> run(
        graph: JIRApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JIRMethod>
    ) {
        backward.run(graph.reversed, summary, unitResolver, unit, startMethods)

        forward.run(graph, summary, unitResolver, unit, startMethods)
    }
}