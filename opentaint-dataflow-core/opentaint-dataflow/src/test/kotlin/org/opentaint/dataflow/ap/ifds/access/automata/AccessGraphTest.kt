package org.opentaint.dataflow.ap.ifds.access.automata

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AccessGraphTest {
    private data class GraphValidation(
        val name: String,
        val graph: AccessGraph,
        val validSequences: List<List<Int>>,
        val invalidSequences: List<List<Int>>,
    )

    private val randomGraphGen = RandomGraphGenerator()

    private val empty = with(randomGraphGen.manager) { emptyGraph() }

    @Test
    fun test() {
        val g3142 = GraphValidation(
            name = "3142",
            graph = empty
                .prepend(1)
                .prepend(3)
                .prepend(4)
                .prepend(1)
                .prepend(3)
                .prepend(2)
                .prepend(3),
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
            graph = empty
                .prepend(1),
            validSequences = listOf(listOf(1)),
            invalidSequences = listOf(listOf(1, 1))
        )

        val g32f = GraphValidation(
            name = "32f",
            graph = empty
                .prepend(3)
                .prepend(2)
                .prepend(3),
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
            graph = empty
                .prepend(2)
                .prepend(3)
                .prepend(2)
                .prepend(3),
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
            graph = empty
                .prepend(2)
                .prepend(3)
                .prepend(2),
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
            graph = empty
                .prepend(3)
                .prepend(2)
                .prepend(3)
                .prepend(2),
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
            sequence.map { it }.fold(this as AccessGraph?) { g, f -> g?.read(f) }

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

    @Test
    fun testEmptyDeltaImpliesContainsAll() = repeat(RANDOM_TEST_REPEAT) {
        val graph1 = randomGraphGen.nextRandomGraph()
        val graph2 = randomGraphGen.nextRandomGraph()
        val containsAll = graph1.containsAll(graph2)
        val hasEmptyDelta = graph1.hasEmptyDelta(graph2)
        if (hasEmptyDelta) {
            assertTrue(containsAll)
        }
    }

    @Test
    fun testDelta1() {
        val g1 = empty.prepend(1)
        val g2 = empty.prepend(1).prepend(1)
        val d1 = g1.delta(g2)
        assertTrue(d1.isEmpty())

        val d2 = g2.delta(g1)
        assertTrue(d2.any { !it.isEmpty() })
    }

    @Test
    fun randomDeltaTest() = repeat(RANDOM_TEST_REPEAT) {
        val g1 = randomGraphGen.nextRandomGraph()
        val g2 = randomGraphGen.nextRandomGraph()
        g1.delta(g2)
    }

    @Test
    fun testEmptyDeltaWithItself() = repeat(RANDOM_TEST_REPEAT) {
        val graph = randomGraphGen.nextRandomGraph()
        assertTrue(graph.hasEmptyDelta(graph))
    }

    @Test
    fun testConcatWithDeltaHasEmptyDeltaWithOriginal() = repeat(RANDOM_TEST_REPEAT) {
        val graph1 = randomGraphGen.nextRandomGraph()
        val graph2 = randomGraphGen.nextRandomGraph()

        val deltas = graph1.delta(graph2)
        if (deltas.isEmpty()) return@repeat

        assertTrue(deltas.any { delta ->
            val concatGraph = graph2.concat(delta)
            graph1.hasEmptyDelta(concatGraph)
        })
    }

    @Test
    fun testDeltaAfterConcat() = repeat(RANDOM_TEST_REPEAT) {
        val graph1 = randomGraphGen.nextRandomGraph()
        val graph2 = randomGraphGen.nextRandomGraph()

        val concat = graph1.concat(graph2)
        val deltas = concat.delta(graph1)
        assertTrue(deltas.isNotEmpty())

        assertTrue(deltas.any { delta ->
            val concatWithDelta = graph1.concat(delta)
            concatWithDelta.hasEmptyDelta(concat)
        })
    }

    @Test
    fun testWrite() = repeat(RANDOM_TEST_REPEAT) {
        val graph = randomGraphGen.nextRandomGraph()
        val accessors = List(randomGraphGen.random.nextInt(1, 10)) {
            randomGraphGen.nextAccessor()
        }

        val afterWrite = accessors.fold(graph) { g, accessor ->
            g.prepend(accessor)
        }

        val afterRead = accessors.foldRight(afterWrite) { accessor, g ->
            assertNotNull(g.read(accessor))
        }

        assertTrue(graph.isEmpty() || afterRead.containsAll(graph))
    }

    private fun AccessGraph.hasEmptyDelta(other: AccessGraph): Boolean =
        delta(other).any { it.isEmpty() }

    companion object {
        private const val RANDOM_TEST_REPEAT = 10_000
    }
}
