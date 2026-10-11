package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.graph.reversed
import org.opentaint.dataflow.jvm.bidi.interp.JIRExitWiredGraph
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.jvm.graph.*
import org.opentaint.util.analysis.ApplicationGraph
import kotlin.test.*

/** The oracle searches forward from each node instead of copying the port's reverse traversal. */
class JIRExitWiredGraphTest : BidiJIRTestUtils() {
    private val language get() = languageManager
    private fun method(name: String) = method(name, SAMPLE)
    @Suppress("UNCHECKED_CAST")
    private fun common(graph: JApplicationGraph) = graph as ApplicationGraph<CommonMethod, CommonInst>
    private fun forward(name: String) = common(graph).methodGraph(method(name))
    private fun wired(name: String) = JIRExitWiredGraph(common(graph)).methodGraph(method(name))
    private fun edges(g: ApplicationGraph.MethodGraph<CommonMethod, CommonInst>) =
        g.statements().flatMap { from -> g.successors(from).map { from to it } }.toSet()
    private fun reaches(g: ApplicationGraph.MethodGraph<CommonMethod, CommonInst>, start: CommonInst,
                        targets: Set<CommonInst>): Boolean {
        val seen = hashSetOf<CommonInst>()
        val todo = ArrayDeque<CommonInst>()
        todo.addLast(start)
        while (todo.isNotEmpty()) {
            val n = todo.removeFirst()
            if (!seen.add(n)) continue
            if (n in targets) return true
            todo.addAll(g.successors(n).toList())
        }
        return false
    }
    private fun assertWiring(name: String): Set<CommonInst> {
        val original = forward(name)
        val actual = wired(name)
        val exits = original.exitPoints().toSet()
        val exceptional = exits.single { it is JMethodExitExceptionalInst }
        val added = original.statements().filter { !reaches(original, it, exits) }.toSet()
        assertEquals(edges(original) + added.map { it to exceptional }, edges(actual), name)
        assertEquals(original.entryPoints().toSet(), actual.entryPoints().toSet())
        assertEquals(exits, actual.exitPoints().toSet())
        assertEquals(original.statements().toSet(), actual.statements().toSet())
        for (node in original.statements()) {
            val expectedPred = original.predecessors(node).toSet() + if (node == exceptional) added else emptySet()
            assertEquals(expectedPred, actual.predecessors(node).toSet(), "$name predecessors of $node")
        }
        assertTrue(actual.statements().all { reaches(actual, it, exits) }, name)
        return added
    }

    @Test fun `normal and throwing methods retain real boundary edges without extra wiring`(): Unit {
        for (name in listOf("normal", "throwOnly")) {
            val g = forward(name)
            assertIs<JMethodEnterInst>(g.entryPoints().single())
            assertEquals(setOf(JMethodExitNormalInst::class, JMethodExitExceptionalInst::class),
                g.exitPoints().map { it::class }.toSet())
            val normal = g.exitPoints().single { it is JMethodExitNormalInst }
            val exceptional = g.exitPoints().single { it is JMethodExitExceptionalInst }
            for (node in g.statements()) when (node) {
                is JIRReturnInst -> assertTrue(normal in g.successors(node).toSet())
                is JIRThrowInst -> assertTrue(exceptional in g.successors(node).toSet())
            }
            assertTrue(assertWiring(name).isEmpty())
        }
    }

    @Test fun `infinite body gets exceptional wiring and remains unreachable from backward normal exit`(): Unit {
        val original = forward("infinite")
        val nodes = assertWiring("infinite")
        assertTrue(nodes.isNotEmpty())
        assertTrue(original.entryPoints().single() in nodes)
        assertTrue(nodes.none { it is JMethodBoundaryInst && it !is JMethodEnterInst })
        val reverse = JIRExitWiredGraph(common(graph)).reversed.methodGraph(method("infinite"))
        val normal = original.exitPoints().single { it is JMethodExitNormalInst }
        val exceptional = original.exitPoints().single { it is JMethodExitExceptionalInst }
        val entry = original.entryPoints().single()
        assertFalse(reaches(reverse, normal, setOf(entry)))
        assertTrue(reaches(reverse, exceptional, setOf(entry)))
    }

    @Test fun `mixed returning and endless branches wire only the part unable to reach either original exit`(): Unit {
        val original = forward("split")
        val nodes = assertWiring("split")
        assertTrue(nodes.isNotEmpty())
        assertFalse(original.entryPoints().single() in nodes)
        assertTrue(original.statements().filterIsInstance<JIRReturnInst>().none { it in nodes })
        val exceptional = original.exitPoints().single { it is JMethodExitExceptionalInst }
        val actual = wired("split")
        assertEquals(original.predecessors(exceptional).toSet() + nodes, actual.predecessors(exceptional).toSet())
    }

    @Test fun `caught throws and post statement catch edges keep their original timing`(): Unit {
        for (name in listOf("caughtThrow", "callInTry")) {
            val original = forward(name)
            val catches = original.statements().filterIsInstance<JIRCatchInst>().toSet()
            assertTrue(catches.isNotEmpty())
            val originalCatchEdges = edges(original).filter { it.second in catches }.toSet()
            assertTrue(originalCatchEdges.isNotEmpty())
            assertTrue(assertWiring(name).isEmpty())
            assertEquals(originalCatchEdges, edges(wired(name)).filter { it.second in catches }.toSet())
        }
    }

    @Test fun `reversal transposes every edge and compact graphs preserve exact forward and wired backward adjacency`(): Unit {
        for (name in listOf("normal", "infinite", "split", "throwOnly", "caughtThrow", "callInTry")) {
            val method = method(name)
            val normalGraph = common(graph)
            val backwardGraph = JIRExitWiredGraph(normalGraph).reversed
            val wired = JIRExitWiredGraph(normalGraph).methodGraph(method)
            assertEquals(edges(wired).map { it.second to it.first }.toSet(), edges(backwardGraph.methodGraph(method)))
            for (application in listOf(normalGraph, backwardGraph)) {
                val expected = application.methodGraph(method)
                val compact = MethodInstGraph.build(language, application, method)
                for (node in expected.statements()) {
                    val next = arrayListOf<CommonInst>()
                    val prev = arrayListOf<CommonInst>()
                    compact.forEachSuccessor(language, node) { next += it }
                    compact.forEachPredecessor(language, node) { prev += it }
                    assertEquals(expected.successors(node).toSet(), next.toSet(), "$name successors of $node")
                    assertEquals(expected.predecessors(node).toSet(), prev.toSet(), "$name predecessors of $node")
                    assertEquals(node in expected.exitPoints().toSet(), compact.isExitPoint(language, node))
                    assertSame(node, compact.instructions[language.getInstIndex(node)])
                }
            }
        }
    }

    @Test fun `missing exceptional exit returns original method graph unchanged`(): Unit {
        val underlying = forward("infinite")
        val masked = object : ApplicationGraph.MethodGraph<CommonMethod, CommonInst> by underlying {
            override fun exitPoints() = underlying.exitPoints().filterNot { it is JMethodExitExceptionalInst }
        }
        val supplied = object : ApplicationGraph<CommonMethod, CommonInst> by common(graph) {
            override fun methodGraph(method: CommonMethod) = masked
        }
        assertSame(masked, JIRExitWiredGraph(supplied).methodGraph(method("infinite")))
    }

    @Test fun `graph adapter adds no implicit class initializer call and leaves empty native graph empty`(): Unit {
        val read = method("staticRead")
        assertTrue(assertNotNull(cp.findClassOrNull(SAMPLE)).declaredMethods.any { it.isClassInitializer })
        assertTrue(graph.methodGraph(read).statements().flatMap(graph::callees).none { it.isClassInitializer })
        val empty = method("empty")
        assertEquals(0, empty.instList.size)
        val native = JIRExitWiredGraph(common(graph)).methodGraph(empty)
        assertTrue(native.statements().none())
        assertTrue(native.entryPoints().none())
        assertTrue(native.exitPoints().none())
    }

    private companion object { const val SAMPLE = "sample.bidi.BidiGraphSample" }
}
