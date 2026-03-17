package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.object2IntMap
import java.util.BitSet
import kotlin.test.Test
import kotlin.test.assertEquals

class GraphIndexTest {
    @Test
    fun localizeGraphHasDeltaWithIndexedGraph() {
        val stats = mutableListOf<Stats>()
        val ctx = randomCtx(RANDOM_TEST_REPEAT)
        repeatGraph(RANDOM_TEST_REPEAT) { g ->
            stats.check(
                ctx, predicate = { g.delta(it).isNotEmpty() },
                localize = { localizeGraphHasDeltaWithIndexedGraph(g) }
            )
        }
        reportStats(ctx, stats)
    }

    @Test
    fun localizeGraphContainsAllIndexedGraph() {
        val stats = mutableListOf<Stats>()
        val ctx = randomCtx(RANDOM_TEST_REPEAT)
        repeatGraph(RANDOM_TEST_REPEAT) { g ->
            stats.check(
                ctx, predicate = { g.containsAll(it) },
                localize = { localizeGraphContainsAllIndexedGraph(g) }
            )
        }
        reportStats(ctx, stats)
    }

    @Test
    fun localizeIndexedGraphHasDeltaWithGraph() {
        val stats = mutableListOf<Stats>()
        val ctx = randomCtx(RANDOM_TEST_REPEAT)
        repeatGraph(RANDOM_TEST_REPEAT) { g ->
            stats.check(
                ctx, predicate = { it.delta(g).isNotEmpty() },
                localize = { localizeIndexedGraphHasDeltaWithGraph(g) }
            )
        }
        reportStats(ctx, stats)
    }

    @Test
    fun localizeIndexedGraphContainsAllGraph() {
        val stats = mutableListOf<Stats>()
        val ctx = randomCtx(RANDOM_TEST_REPEAT)
        repeatGraph(RANDOM_TEST_REPEAT) { g ->
            stats.check(
                ctx, predicate = { it.containsAll(g) },
                localize = { localizeIndexedGraphContainsAllGraph(g) }
            )
        }
        reportStats(ctx, stats)
    }

    private val randomGraphGen = RandomGraphGenerator()

    private class Ctx {
        val graphId = object2IntMap<AccessGraph>()
        val graphs = arrayListOf<AccessGraph>()
        val index = GraphIndex()

        val size: Int get() = graphs.size

        fun add(graph: AccessGraph) {
            graphId.getOrCreateIndex(graph) { idx ->
                check(idx == graphs.size)
                graphs.add(graph)
                index.add(graph, idx)
                return
            }
        }

        fun allGraphs(): List<AccessGraph> = graphs

        fun localizeGraphs(filter: GraphIndex.() -> BitSet): List<AccessGraph> {
            val localized = index.filter()
            val result = arrayListOf<AccessGraph>()
            localized.forEach { result.add(graphs[it]) }
            return result
        }
    }

    private fun randomCtx(size: Int): Ctx {
        val ctx = Ctx()
        while (ctx.size < size) {
            ctx.add(randomGraphGen.nextRandomGraph())
        }
        return ctx
    }

    private data class Stats(val loc: Int, val expected: Int)

    private fun MutableList<Stats>.check(
        ctx: Ctx, predicate: (AccessGraph) -> Boolean, localize: GraphIndex.() -> BitSet
    ) {
        val expected = ctx.allGraphs().filter(predicate)
        val localized = ctx.localizeGraphs(localize)
        val actual = localized.filter(predicate)

        assertEquals(expected, actual)

        this += Stats(localized.size, actual.size)
    }

    private inline fun repeatGraph(repetition: Int, body: (AccessGraph) -> Unit) = repeat(repetition) {
        val graph = randomGraphGen.nextRandomGraph()
        body(graph)
    }

    private fun reportStats(ctx: Ctx, stats: List<Stats>) {
        val (same, diff) = stats.partition { it.loc == it.expected }
        val (zeroSame, otherSame) = same.partition { it.expected == 0 }
        val (allSame, localizedSame) = otherSame.partition { it.expected == ctx.size }
        val relativeDiff = diff.map { it.loc - it.expected }

        val s = listOf(
            "Zero same: ${zeroSame.size}",
            "All same: ${allSame.size}",
            "Localized same: ${localizedSame.size}",
            "Diff: ${diff.size}",
            "Error score: ${relativeDiff.sum()}"
        )
        println(s.joinToString(" | "))
        println(relativeDiff.groupingBy { it }.eachCount().entries.sortedByDescending { it.value })
    }

    companion object {
        private const val RANDOM_TEST_REPEAT = 10_000
    }
}
