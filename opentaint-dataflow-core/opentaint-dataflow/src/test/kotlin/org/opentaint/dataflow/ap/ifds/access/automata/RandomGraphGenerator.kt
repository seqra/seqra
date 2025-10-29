package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.FieldAccessor
import kotlin.random.Random

class RandomGraphGenerator(
    val random: Random = Random(17),
    val accessorsSize: Int = 10,
    val operationsLimit: Int = 10,
) {
    val manager = AutomataApManager()

    init {
        with(manager) {
            repeat(accessorsSize) { FieldAccessor("", "$it", "").idx }
        }
    }

    fun nextAccessor() = random.nextInt(accessorsSize)

    fun nextRandomGraph(depth: Int = 0): AccessGraph {
        var graph = manager.emptyGraph()

        val operations = random.nextInt(operationsLimit)
        val operationSelector = random.nextDouble()

        for (i in 0 until operations) {
            when {
                operationSelector < 0.5 -> {
                    val accessor = nextAccessor()
                    graph = graph.prepend(accessor)
                }

                operationSelector in 0.5..<0.75 -> {
                    val accessor = nextAccessor()
                    graph = graph.clear(accessor) ?: continue
                }

                operationSelector in 0.75..<0.9 && depth < 10 -> {
                    val otherGraph = nextRandomGraph(depth + 1)
                    graph = graph.concat(otherGraph)
                }

                operationSelector > 0.9 && depth < 10 -> {
                    val otherGraph = nextRandomGraph(depth + 1)
                    if (graph.isEmpty() == otherGraph.isEmpty()) {
                        graph = graph.merge(otherGraph)
                    }
                }
            }
        }

        return graph
    }
}
