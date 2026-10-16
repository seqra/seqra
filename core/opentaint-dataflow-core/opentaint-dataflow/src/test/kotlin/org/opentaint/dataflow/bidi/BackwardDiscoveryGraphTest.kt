package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.fixtures.ProgramMethod
import kotlin.test.*

/** Independent forward searches specify I11(e), rather than copying the reverse-worklist implementation. */
class BackwardDiscoveryGraphTest {
    private fun reaches(edges: Set<Pair<Int, Int>>, start: Int, exits: Set<Int>): Boolean {
        val seen = HashSet<Int>()
        val todo = ArrayDeque<Int>()
        todo.addLast(start)
        while (todo.isNotEmpty()) {
            val node = todo.removeFirst()
            if (!seen.add(node)) continue
            if (node in exits) return true
            for ((from, to) in edges) if (from == node) todo.addLast(to)
        }
        return false
    }

    private fun edges(f: FiniteProgramFixtures, method: ProgramMethod, direction: Direction): Set<Pair<Int, Int>> =
        buildSet {
            val graph = f.graph(method.key(), direction)
            for (index in method.successors.indices) graph.forEachSuccessor(f.language, method.inst(index)) {
                add(index to it.location.index)
            }
        }

    @Test fun `selected backward discovery transposes exceptional wiring without changing forward return paths`() {
        val cases = listOf(
            listOf(listOf(1), listOf(2), listOf(1), emptyList(), emptyList()), // nonreturning SCC
            listOf(listOf(1, 3), listOf(2), listOf(1), emptyList(), emptyList()), // mixed return/loop
            listOf(listOf(1), listOf(4), emptyList(), emptyList(), emptyList()), // genuine throw
            listOf(listOf(1), listOf(3), emptyList(), emptyList(), emptyList()), // genuine return
        )
        FiniteProgramFixtures().use { f ->
            for ((n, adjacency) in cases.withIndex()) {
                val method = f.method("discovery$n", adjacency, normalExit = 3, exceptionalExit = 4,
                    backwardExitDiscovery = true)
                val raw = adjacency.flatMapIndexed { from, next -> next.map { from to it } }.toSet()
                val dead = adjacency.indices.filterNot { reaches(raw, it, setOf(3, 4)) }
                val expected = (raw + dead.map { it to 4 }).map { (from, to) -> to to from }.toSet()
                assertEquals(raw, edges(f, method, Direction.FORWARD), "Raw forward graph $n")
                assertEquals(expected, edges(f, method, Direction.BACKWARD), "Independent I11 reverse graph $n")
                val backward = f.contexts.directed(Direction.BACKWARD)
                assertEquals(setOf(3, 4), backward.startNodes(method.key(), zero = true).map { it.location.index }.toSet())
                assertEquals(listOf(3), backward.startNodes(method.key(), zero = false).map { it.location.index })
                assertEquals(reaches(raw, 0, setOf(3)), reaches(edges(f, method, Direction.BACKWARD), 3, setOf(0)),
                    "Discovery cannot add a normal-return path")
                assertTrue(reaches(edges(f, method, Direction.BACKWARD), 4, setOf(0)) || reaches(raw, 0, setOf(3)))
            }
        }
    }

    @Test fun `unflagged historical graph remains raw and discovery cannot fall back to the normal exit`() {
        FiniteProgramFixtures().use { f ->
            val adjacency = listOf(listOf(1), listOf(1), emptyList(), emptyList())
            val method = f.method("historical raw", adjacency, normalExit = 2, exceptionalExit = 3)
            assertEquals(setOf(1 to 0, 1 to 1), edges(f, method, Direction.BACKWARD))
            assertFalse(reaches(edges(f, method, Direction.BACKWARD), 3, setOf(0)))
            assertFailsWith<IllegalArgumentException> {
                f.method("missing exceptional", adjacency, normalExit = 2, backwardExitDiscovery = true)
            }
            assertFailsWith<IllegalArgumentException> {
                f.method("aliased exits", adjacency, normalExit = 2, exceptionalExit = 2, backwardExitDiscovery = true)
            }
        }
    }
}
