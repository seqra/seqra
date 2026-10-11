package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.fail

class FormsSinkTriggerTest {
    @Test
    fun `forward sink trigger uses the fired layer and zero premise independently of its ordinary sibling`(): Unit {
        val fixture = StoreFixtures()
        val x = AccessPathBase.Argument(0)
        val ordinaryBase = AccessPathBase.Return
        val endBase = AccessPathBase.Argument(1)
        val f = fixture.field("input")
        val g = fixture.field("end")
        val t = MarkSlot.Concrete(fixture.mark("T"))
        val u = MarkSlot.Concrete(fixture.mark("U"))
        val inputFact = PathFact(x, listOf(f), Tail.EXACT, t)
        val endFact = PathFact(endBase, listOf(g), Tail.EXACT, u)
        val premise = setOf(fixture.pattern(x, listOf(f), mark = t))
        val zeroPremise = setOf(ZERO_PATTERN)
        val end = MicroEdge.of(PathEdge(ZERO_FACT, endFact, ExclusionSet.Empty))
        val rule = object : CommonTaintConfigurationSink {
            override val id = "sink-trigger"
            override val meta = object : CommonTaintConfigurationSinkMeta {
                override val message = "test sink"
                override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
            }
        }
        val sink = SinkRule(rule, 0, premise.toList(), listOf(end))
        val unrelated = SinkRule(rule, 0, premise.toList(), listOf(end))
        assertNotSame(sink, unrelated)
        val guard = Guard.SinkTriggered(sink)
        assertSame(sink, guard.sink)
        val endStage = CallStage.Edges(BOUND, REWRITTEN, END_FACTS,
            StatementSummary(setOf(AccessPathBase.Zero), listOf(end), emptyList(), emptyMap()), guard)
        val plan = CallPlan(setOf(x), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, StatementSummary.identities(listOf(x))),
            CallStage.Edges(BOUND, REWRITTEN, BIND_BACK, StatementSummary(setOf(x),
                listOf(MicroEdge.of(starEdge(x, emptyList(), ordinaryBase, emptyList()))), emptyList(), emptyMap())),
            endStage,
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, StatementSummary.identities(listOf(ordinaryBase, endBase)))),
            listOf(sink, unrelated), BEFORE, AFTER)
        assertSame(sink, plan.sinks[0])
        val at = Place(fixture.inst(1), statementEdge = true, sources = true)
        val oracle = FormsReference(fixture.ops, ReferenceAlgebra(ApMode(false, Direction.FORWARD, 2),
            allowsSource = { _, _ -> fail("END_FACTS is not a source-seed place") },
            sourceHit = { _, _ -> fail("END_FACTS must not record a source hit") }, manager = fixture.manager))

        for (layer in Layer.entries) for (selection in listOf("same", "unrelated", "empty", "default")) {
            // The ordinary input deliberately has the other layer: the end fact reads the fired layer.
            val input = PlanItem(premise, Conclusion(inputFact, ExclusionSet.Empty,
                demand = layer == Layer.NORMAL), identityCandidate = true)
            assertNotEquals(AccessPathBase.Zero, input.c.fact.base)
            val ordinary = input.copy(c = input.c.copy(fact = inputFact.copy(base = ordinaryBase)))
            val fired = PlanItem(zeroPremise, Conclusion(endFact, ExclusionSet.Empty,
                demand = layer == Layer.DEMAND), identityCandidate = false)
            val boundBatches = ArrayList<List<PlanItem>>()
            val exitItems = ArrayList<PlanItem>()
            val atBound: (List<PlanItem>) -> List<Pair<SinkRule, Layer>> = { items ->
                boundBatches += items.toList()
                when (selection) {
                    "same" -> listOf(sink to layer)
                    "unrelated" -> listOf(unrelated to layer)
                    "empty" -> emptyList()
                    else -> fail("Default hooks must not check sinks at BOUND")
                }
            }
            val hooks = if (selection == "default") PlanHooks(atBound = atBound, exit = { exitItems += it; it })
                else PlanHooks(guards = true, atBound = atBound, exit = { exitItems += it; it })
            val reached = oracle.run(plan, listOf(input, input), at, hooks)
            val expected = if (selection == "same") setOf(ordinary, fired) else setOf(ordinary)
            assertEquals(listOf(input), reached.filter { it.first == BOUND }.map { it.second }, "$layer/$selection")
            assertEquals(if (selection == "default") emptyList() else listOf(listOf(input)), boundBatches,
                "The deduplicated BOUND batch is checked once: $layer/$selection")
            assertEquals(expected, reached.filter { it.first == REWRITTEN }.map { it.second }.toSet(), "$layer/$selection")
            assertEquals(expected, reached.filter { it.first == AFTER }.map { it.second }.toSet(), "$layer/$selection")
            assertEquals(expected, exitItems.toSet(), "$layer/$selection")
            assertEquals(expected.size, exitItems.size, "$layer/$selection")
            for (point in listOf(REWRITTEN, AFTER)) {
                assertEquals(listOf(ordinary), reached.filter { it.first == point && it.second.premise == premise }
                    .map { it.second }, "Only the ordinary sibling keeps the input premise: $layer/$selection/$point")
                assertEquals(if (selection == "same") listOf(fired) else emptyList(),
                    reached.filter { it.first == point && it.second.premise == zeroPremise }.map { it.second },
                    "$layer/$selection/$point")
            }
        }
    }
}
