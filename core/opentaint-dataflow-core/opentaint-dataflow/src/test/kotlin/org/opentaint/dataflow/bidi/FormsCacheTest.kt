package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import kotlin.test.*

class FormsCacheTest {
    @Test fun `forward and backward forms are built once in either first direction for every slot`(): Unit {
        for (first in Direction.entries) {
            val builds = IntArray(3)
            var reversals = 0
            val cache = FormsCache<Any>(3) { reversals++; Any() }
            val retained = HashMap<Pair<Int, Direction>, Any>()
            repeat(20) { iteration ->
                for (slot in 0..2) for (direction in listOf(first, if (first == Direction.FORWARD) Direction.BACKWARD else Direction.FORWARD)) {
                    val result = cache.get(slot, direction) { builds[slot]++; Any() }
                    val old = retained.putIfAbsent(slot to direction, result)
                    if (iteration > 0) assertSame(old, result)
                }
            }
            assertContentEquals(intArrayOf(1, 1, 1), builds)
            assertEquals(3, reversals)
            for (slot in 0..2) assertNotSame(retained[slot to Direction.FORWARD], retained[slot to Direction.BACKWARD])
        }
    }

    @Test fun `invalid cache slots do not invoke form builders or disturb valid cached identities`(): Unit {
        var builds = 0
        val cache = FormsCache<Any>(2) { Any() }
        for (slot in listOf(-1, 2, Int.MAX_VALUE)) for (direction in Direction.entries)
            assertFails { cache.get(slot, direction) { builds++; Any() } }
        assertEquals(0, builds)
        val value = cache.get(0, Direction.BACKWARD) { builds++; Any() }
        assertSame(value, cache.get(0, Direction.BACKWARD) { error("Rebuilt cached form") })
        assertEquals(1, builds)
    }

    @Test fun `failed reversal retains its forward form for a later successful attempt`(): Unit {
        var builds = 0
        var reversals = 0
        val cache = FormsCache<Any>(1) { reversals++; if (reversals == 1) error("reverse failure") else Any() }
        assertFails { cache.get(0, Direction.BACKWARD) { builds++; Any() } }
        val forward = cache.get(0, Direction.FORWARD) { error("Forward form was already built") }
        val backward = cache.get(0, Direction.BACKWARD) { error("Forward form was already built") }
        assertSame(forward, cache.get(0, Direction.FORWARD) { Any() })
        assertSame(backward, cache.get(0, Direction.BACKWARD) { Any() })
        assertEquals(1, builds)
        assertEquals(2, reversals)
    }

    private val fixture = StoreFixtures()
    private val entry = fixture.inst(0)
    private val normal = fixture.inst(10)
    private val exceptional = fixture.inst(11)
    private val calls = ArrayList<String>()
    private val callExpr = object : CommonCallExpr {
        override val typeName = "void"
        override val args: List<CommonValue> = emptyList()
    }
    private val rules = RuleStatement(StatementSummary.identities(listOf(AccessPathBase.Argument(0))),
        StatementSummary.EMPTY, emptyList())
    private val exits = ExitRules(rules, true, setOf(fixture.mark("entry")))
    private val plan = CallPlan(emptySet(), emptyList(), emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
    private val interp = object : Interpreter {
        override fun entryNode(method: MethodKey) = entry
        override fun exitNodes(method: MethodKey) = listOf(ExitNode(normal, false), ExitNode(exceptional, true))
        override fun entryRules(method: MethodKey): RuleStatement = error("Use cached method forms")
        override fun exitRules(method: MethodKey, exit: CommonInst): ExitRules = error("Use cached method forms")
        override fun statementSummary(method: MethodKey, statement: CommonInst): StatementSummary = error("Use cached method forms")
        override fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr): CallPlan = error("Use cached method forms")
        override fun isSummaryBase(base: AccessPathBase) = true
    }
    private fun directed(direction: Direction) = DirectedForms(interp, direction) { method ->
        object : MethodForms {
            override fun statement(direction: Direction, s: CommonInst): StatementSummary {
                calls += "${method.context}:statement:$direction:${s.location.index}"
                return rules.summary
            }
            override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan {
                assertSame(callExpr, call)
                calls += "${method.context}:call:$direction:${s.location.index}"
                return plan
            }
            override fun entryRules(direction: Direction): RuleStatement {
                calls += "entry:$direction"
                return rules
            }
            override fun exitRules(direction: Direction, exit: CommonInst): ExitRules {
                calls += "exit:$direction:${exit.location.index}"
                return exits
            }
        }
    }

    @Test fun `directed start and end nodes distinguish backward zero and normal exits`(): Unit {
        val forward = directed(Direction.FORWARD)
        val backward = directed(Direction.BACKWARD)
        for (zero in listOf(false, true)) assertEquals(listOf(entry), forward.startNodes(fixture.method, zero))
        assertEquals(listOf(normal), backward.startNodes(fixture.method, false))
        assertEquals(listOf(normal, exceptional), backward.startNodes(fixture.method, true))
        assertEquals(listOf(normal), forward.endNodes(fixture.method))
        assertEquals(listOf(entry), backward.endNodes(fixture.method))
    }

    @Test fun `directed boundaries select cached rule orientation and remove backward exit cleanup`(): Unit {
        val forward = directed(Direction.FORWARD)
        val backward = directed(Direction.BACKWARD)
        assertSame(rules, forward.startRules(fixture.method, entry))
        assertSame(rules, backward.startRules(fixture.method, exceptional))
        assertSame(exits, forward.endRules(fixture.method, normal))
        val backwardEnd = backward.endRules(fixture.method, entry)
        assertSame(rules, backwardEnd.rules)
        assertFalse(backwardEnd.globalStateDrop)
        assertTrue(backwardEnd.entryMarks.isEmpty())
        assertEquals(listOf("entry:FORWARD", "exit:BACKWARD:11", "exit:FORWARD:10", "entry:BACKWARD"), calls)
    }

    @Test fun `directed statement and call lookup retain each method key and direction`(): Unit {
        val other = fixture.key("other", 1)
        for (direction in Direction.entries) for (method in listOf(fixture.method, other)) {
            val directed = directed(direction)
            assertSame(rules.summary, directed.statement(method, normal))
            assertSame(plan, directed.call(method, normal, callExpr))
        }
        assertEquals(Direction.entries.flatMap { direction -> listOf(fixture.method, other).flatMap { method ->
            listOf("${method.context}:statement:$direction:10", "${method.context}:call:$direction:10")
        } }, calls)
    }
}
