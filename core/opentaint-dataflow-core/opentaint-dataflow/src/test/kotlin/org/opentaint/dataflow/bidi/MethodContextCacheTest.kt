package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.engine.MethodContextCache
import org.opentaint.dataflow.bidi.engine.MethodContextSource
import org.opentaint.dataflow.bidi.engine.RunMethodContext
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.interp.MethodForms
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.*

class MethodContextCacheTest {
    private val f = EngineShellFixtures()

    @Test fun `source delegation retains the full method context and requested graph direction`(): Unit {
        val formsCalls = arrayListOf<MethodKey>()
        val graphCalls = arrayListOf<Pair<MethodKey, Direction>>()
        val cache = MethodContextCache(f.interpreter, object : MethodContextSource {
            override fun forms(key: MethodKey): MethodForms {
                formsCalls += key
                return f.methodForms(key)
            }
            override fun graph(key: MethodKey, direction: Direction): MethodInstGraph {
                graphCalls += key to direction
                return f.graph(key.method, direction)
            }
        })
        val context = f.key(f.methodA, "second")
        assertSame(f.interpreter, cache.interpreter)
        assertSame(f.methodForms(f.a), cache.forms(f.a))
        assertSame(f.methodForms(context), cache.forms(context))
        for (key in listOf(f.a, context, f.b)) for (direction in Direction.entries)
            assertSame(f.graph(key.method, direction), cache.graph(key, direction))
        assertEquals(listOf(f.a, context), formsCalls)
        assertEquals(listOf(f.a, context, f.b).flatMap { key -> Direction.entries.map { key to it } }, graphCalls)
    }

    @Test fun `directed forms select cached statement call and boundary forms without rebuilding them`(): Unit {
        val statement = f.inst(f.methodA, 1)
        val call = f.inst(f.methodA, 2)
        val exit = f.inst(f.methodA, 3)
        val owner = f.methodForms(f.a)
        for (direction in Direction.entries) {
            val directed = f.contexts.directed(direction)
            assertSame(f.interpreter, directed.interp)
            assertEquals(direction, directed.direction)
            assertSame(owner.statement(direction, statement), directed.statement(f.a, statement))
            assertSame(owner.call(direction, call, f.callExpr), directed.call(f.a, call, f.callExpr))
            if (direction == Direction.FORWARD) {
                assertSame(owner.entryRules(direction), directed.startRules(f.a, f.a.statement))
                assertSame(owner.exitRules(direction, exit), directed.endRules(f.a, exit))
            } else {
                assertSame(owner.exitRules(direction, exit).rules, directed.startRules(f.a, exit))
                val end = directed.endRules(f.a, f.a.statement)
                assertSame(owner.entryRules(direction), end.rules)
                assertFalse(end.globalStateDrop)
                assertTrue(end.entryMarks.isEmpty())
            }
        }
    }

    @Test fun `start and end boundaries distinguish zero backward traversal from ordinary requirements`(): Unit {
        val forward = f.contexts.directed(Direction.FORWARD)
        val backward = f.contexts.directed(Direction.BACKWARD)
        val normal = f.inst(f.methodA, 3)
        val exceptional = f.inst(f.methodA, 4)
        assertEquals(listOf(f.a.statement), forward.startNodes(f.a, zero = false))
        assertEquals(listOf(f.a.statement), forward.startNodes(f.a, zero = true))
        assertEquals(listOf(normal), forward.endNodes(f.a))
        assertEquals(listOf(normal), backward.startNodes(f.a, zero = false))
        assertEquals(listOf(normal, exceptional), backward.startNodes(f.a, zero = true))
        assertEquals(listOf(f.a.statement), backward.endNodes(f.a))
    }

    @Test fun `forward context exposes shared owners and exceptional exit rules without treating them as summary ends`(): Unit {
        f.shared().use { shared ->
            val config = f.config()
            val context = RunMethodContext(f.a, f.contexts.directed(Direction.FORWARD), config, shared)
            assertSame(f.a, context.key)
            assertSame(config.mode, context.mode)
            assertSame(f.ops, context.ops)
            assertSame(f.manager, context.ap)
            assertSame(f.vulnerabilities, context.vulnerabilities)
            assertTrue(context.isEnd(f.inst(f.methodA, 3)))
            assertFalse(context.isEnd(f.inst(f.methodA, 4)))
            assertTrue(context.isExceptionalExit(f.inst(f.methodA, 4)))
            assertFalse(context.isExceptionalExit(f.inst(f.methodA, 3)))
            assertFalse(context.isEnd(f.a.statement))
            assertFalse(context.isEnd(f.inst(f.methodB, 3)))
            assertFalse(context.isExceptionalExit(f.inst(f.methodB, 4)))
        }
    }

    @Test fun `forward context successors and call lookup use the selected method graph`(): Unit {
        f.shared().use { shared ->
            val context = RunMethodContext(f.a, f.contexts.directed(Direction.FORWARD), f.config(), shared)
            val next = arrayListOf<CommonInst>()
            context.forEachSuccessor(f.a.statement) { next += it }
            assertEquals(listOf(f.inst(f.methodA, 1)), next)
            next.clear()
            context.forEachSuccessor(f.inst(f.methodA, 2)) { next += it }
            assertEquals(listOf(f.inst(f.methodA, 3), f.inst(f.methodA, 4)), next)
            assertSame(f.callExpr, context.callAt(f.inst(f.methodA, 2)))
            assertNull(context.callAt(f.inst(f.methodA, 1)))
        }
    }

    @Test fun `backward context ends at its entry and uses reversed successors without exceptional flags`(): Unit {
        f.shared().use { shared ->
            val key = f.key(f.methodB, "backward-context")
            val config = f.config(index = 2, roots = listOf(key))
            val context = RunMethodContext(key, f.contexts.directed(Direction.BACKWARD), config, shared)
            assertTrue(context.isEnd(key.statement))
            assertFalse(context.isEnd(f.inst(f.methodB, 3)))
            assertFalse(context.isExceptionalExit(f.inst(f.methodB, 4)))
            assertEquals(Direction.BACKWARD, context.mode.direction)
            val next = arrayListOf<CommonInst>()
            context.forEachSuccessor(f.inst(f.methodB, 3)) { next += it }
            assertEquals(listOf(f.inst(f.methodB, 2)), next)
            next.clear()
            context.forEachSuccessor(f.inst(f.methodB, 4)) { next += it }
            assertEquals(listOf(f.inst(f.methodB, 2)), next)
            next.clear()
            context.forEachSuccessor(key.statement) { next += it }
            assertTrue(next.isEmpty())
        }
    }
}
