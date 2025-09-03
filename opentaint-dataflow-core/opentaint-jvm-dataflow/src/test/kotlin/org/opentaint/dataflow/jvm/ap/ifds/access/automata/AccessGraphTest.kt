package org.opentaint.dataflow.jvm.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.automata.AccessGraph
import kotlin.test.Test

class AccessGraphTest {
    private data class GraphValidation(
        val name: String,
        val graph: AccessGraph,
        val validSequences: List<List<Int>>,
        val invalidSequences: List<List<Int>>,
    )

    @Test
    fun test() {
        val a = List(10) { FieldAccessor("", "$it", "") }

        val g3142 = GraphValidation(
            name = "3142",
            graph = AccessGraph.empty()
                .prepend(a[1])
                .prepend(a[3])
                .prepend(a[4])
                .prepend(a[1])
                .prepend(a[3])
                .prepend(a[2])
                .prepend(a[3]),
            validSequences = listOf(
                listOf(3, 1),
                listOf(3, 1, 4, 3, 1),
                listOf(3, 2, 3, 1),
            ),
            invalidSequences = listOf(
                listOf(3, 2),
                listOf(3, 1, 2)
            )
        )

        val g1 = GraphValidation(
            name = "1",
            graph = AccessGraph.empty()
                .prepend(a[1]),
            validSequences = listOf(listOf(1)),
            invalidSequences = listOf(listOf(1, 1))
        )

        val g32f = GraphValidation(
            name = "32f",
            graph = AccessGraph.empty()
                .prepend(a[3])
                .prepend(a[2])
                .prepend(a[3]),
            validSequences = listOf(
                listOf(3),
                listOf(3, 2, 3),
                listOf(3, 2, 3, 2, 3),
            ),
            invalidSequences = listOf(
                listOf(3, 2)
            )
        )

        val g32if = GraphValidation(
            name = "32if",
            graph = AccessGraph.empty()
                .prepend(a[2])
                .prepend(a[3])
                .prepend(a[2])
                .prepend(a[3]),
            validSequences = listOf(
                listOf(),
                listOf(3, 2),
                listOf(3, 2, 3, 2)
            ),
            invalidSequences = listOf(
                listOf(2),
                listOf(2, 3, 2),
                listOf(3, 2, 3)
            )
        )

        val g23f = GraphValidation(
            name = "23f",
            graph = AccessGraph.empty()
                .prepend(a[2])
                .prepend(a[3])
                .prepend(a[2]),
            validSequences = listOf(
                listOf(2),
                listOf(2, 3, 2),
                listOf(2, 3, 2, 3, 2),
            ),
            invalidSequences = listOf(
                listOf(2, 3),
                listOf(2, 3, 2, 3),
            )
        )

        val g23if = GraphValidation(
            name = "23if",
            graph = AccessGraph.empty()
                .prepend(a[3])
                .prepend(a[2])
                .prepend(a[3])
                .prepend(a[2]),
            validSequences = listOf(
                listOf(),
                listOf(2, 3),
                listOf(2, 3, 2, 3),
            ),
            invalidSequences = listOf(
                listOf(2),
                listOf(2, 3, 2),
            )
        )

        fun AccessGraph.runSequence(sequence: List<Int>) =
            sequence.map { a[it] }.fold(this as AccessGraph?) { g, f -> g?.read(f) }

        fun runValidation(validation: GraphValidation) {
            println("-".repeat(20))
            println("Validate: ${validation.name}")
            println(validation.graph)

            for (validSeq in validation.validSequences) {
                val result = validation.graph.runSequence(validSeq)
                check(result != null && result.initialNodeIsFinal())
            }

            for (invalidSeq in validation.invalidSequences) {
                val result = validation.graph.runSequence(invalidSeq)
                check(result == null || !result.initialNodeIsFinal())
            }
        }

        fun runConcatValidation(left: GraphValidation, right: GraphValidation) {
            println("-".repeat(20))
            println("Validate concat: ${left.name} + ${right.name}")
            println(left.graph.concat(right.graph))

            for (ls in left.validSequences) {
                for (rs in right.validSequences) {
                    val concat = left.graph.concat(right.graph)
                    val result = concat.runSequence(ls + rs)
                    check(result != null && result.initialNodeIsFinal())
                }
            }
        }

        val graphs = listOf(g3142, g1, g32f, g32if, g23f, g23if)

        graphs.forEach { runValidation(it) }

        graphs.forEach { l ->
            graphs.forEach { r ->
                runConcatValidation(l, r)
            }
        }

        println("-".repeat(20))
        println("Delta 0")
        println(g3142.graph.delta(g32f.graph))

        println("Delta 1")
        println(g3142.graph.delta(g32if.graph))

        println("Delta 2")
        println(g3142.graph.delta(g1.graph))

        println("Delta 3")
        val cc = g3142.graph.concat(g32if.graph)
        println(cc.delta(g3142.graph))

        println("Delta 4")
        println(g1.graph.delta(g1.graph))

        println("-".repeat(20))
        println("Merge 0")
        println(g3142.graph.merge(g32f.graph))

        println("Merge 1")
        println(g3142.graph.merge(g3142.graph))
    }
}
