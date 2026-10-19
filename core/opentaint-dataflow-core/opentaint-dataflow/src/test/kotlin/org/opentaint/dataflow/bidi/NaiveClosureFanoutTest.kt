package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*

/** Later Full-mode reference: every consumer registers before the callee publishes a nonzero summary. */
class NaiveClosureFanoutTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val returned = AccessPathBase.Return
    private val zero = setOf(ZERO_PATTERN)

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    @Test
    fun `delivery to seventeen excluded must consumers admits the good premise and rejects the bad premise`(): Unit =
        fanout(mayConsumers = false)

    @Test
    fun `delivery to seventeen may consumers admits both premises and keeps both returned results in DEMAND`(): Unit =
        fanout(mayConsumers = true)

    private fun fanout(mayConsumers: Boolean) {
        for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val callerMethod = f.method("fanoutConsumer", listOf(listOf(1), listOf(2), emptyList()), 2)
            val consumerKeys = (0 until 17).map { callerMethod.key("consumer-$it") }
            val producer = callerMethod.key("producer")
            val calleeMethod = f.method("fanoutCallee",
                (0 until 25).map { if (it == 24) emptyList() else listOf(it + 1) }, 24)
            val callee = calleeMethod.key()
            val good = f.field("good")
            val bad = f.field("bad")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val plan = CallPlan(setOf(x, y, returned), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                    summary(starEdge(returned, emptyList(), y, emptyList()), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER)
            val mustSource = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty)
            val exactSource = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
            val seeds = ArrayList<Seed>()
            for (caller in consumerKeys + producer) {
                val may = mayConsumers && caller != producer
                val source = if (may) exactSource else mustSource
                f.entry(caller, RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
                seeds += Seed.Source(caller, callerMethod.inst(0), source)
                val transfer = when {
                    may -> PathEdge(PathFact(x, emptyList(), Tail.EXACT, t),
                        PathFact(x, emptyList(), Tail.ANY, t), ExclusionSet.Empty)
                    caller == producer -> keepEdge(x)
                    else -> keepEdge(x, e = ExclusionSet.of(bad))
                }
                f.statement(caller, 0, summary(ZERO_KEEP, transfer))
                f.call(caller, 1, plan)
            }
            f.statement(callee, 0, summary(ZERO_KEEP,
                PathEdge(PathFact(x, listOf(good), Tail.STAR, t),
                    PathFact(returned, emptyList(), Tail.EXACT, u), ExclusionSet.Empty),
                PathEdge(PathFact(x, listOf(bad), Tail.STAR, t),
                    PathFact(returned, emptyList(), Tail.EXACT, v), ExclusionSet.Empty)))
            val demand = listOf(
                callee to DemandPattern(f.pattern(x, listOf(good), Tail.ANY, t),
                    f.pattern(returned, tail = Tail.ANY, mark = u)),
                callee to DemandPattern(f.pattern(x, listOf(bad), Tail.ANY, t),
                    f.pattern(returned, tail = Tail.ANY, mark = v)),
            ) + (consumerKeys + producer).map { caller ->
                caller to DemandPattern(ZERO_PATTERN, f.pattern(y, tail = Tail.ANY, mark = MarkSlot.STAR))
            }
            val config = f.config(index = 3, L = 2, roots = consumerKeys + producer, demand = demand, seeds = seeds)
            val expected = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run()
            val schedule = ProductionSchedule(f, config, sameUnit = sameUnit,
                policy = SchedulePolicy.REPLAY_FIRST, quantum = 1)
            val actual = schedule.run()
            val universe = BoundedDenotation(listOf(AccessPathBase.Zero, x, y, returned),
                listOf(good, bad, f.field("fresh accessor")),
                listOf(t.mark, u.mark, v.mark, f.mark("fresh mark"), TaintMark.ZERO), 2)
            try {
                actual.assertEquivalent(expected, universe)
                val wanted = buildSet {
                    add(ObservedEdge(consumerKeys.first(), zero, Conclusion(ZERO_FACT, ExclusionSet.Empty, false)))
                    add(ObservedEdge(consumerKeys.first(), zero,
                        Conclusion(PathFact(y, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, mayConsumers)))
                    if (mayConsumers) add(ObservedEdge(consumerKeys.first(), zero,
                        Conclusion(PathFact(y, emptyList(), Tail.EXACT, v), ExclusionSet.Empty, true)))
                }
                for (caller in consumerKeys) assertEquals(wanted.map { it.copy(method = caller) }.toSet(),
                    actual.rawSummaries.filter { it.method == caller }.toSet(), "consumer=$caller")
                assertEquals(setOf(u, v), actual.rawSummaries.filter { it.method == producer &&
                    it.conclusion.fact.base == y && !it.conclusion.demand }.map { it.conclusion.fact.mark }.toSet())
                val parts = schedule.subscriptions.filter { it.callee == callee && it.caller in consumerKeys &&
                    it.added.base == x }
                assertEquals(consumerKeys.toSet(), parts.map { it.caller }.toSet())
                assertEquals(17, parts.size)
                for (part in parts) {
                    assertEquals(if (mayConsumers) Layer.DEMAND else Layer.NORMAL, part.linkLayer)
                    assertEquals(setOf(f.pattern(x, tail = if (mayConsumers) Tail.ANY else Tail.ANY_TAINT,
                        mark = t, exclusion = if (mayConsumers) ExclusionSet.Empty else ExclusionSet.of(bad))),
                        f.ops.leaves(part.added).toSet())
                }
                val firstResult = schedule.trace.indexOfFirst { line ->
                    line.startsWith("process ") && line.contains("method=$callee,") &&
                        (line.contains("mark=$u") || line.contains("mark=$v"))
                }
                val firstDelivery = schedule.trace.indexOfFirst { line ->
                    line.startsWith("Delivery($callee,") && (line.contains("mark=$u") || line.contains("mark=$v"))
                }
                assertTrue(firstResult >= 0 && firstDelivery > firstResult, "Actual nonzero result delivery is required")
                for (caller in consumerKeys) {
                    val registered = schedule.trace.indexOfFirst { line ->
                        line.startsWith("subscribe callee=$callee caller=$caller ") && line.contains("mark=$t")
                    }
                    assertTrue(registered in 0 until firstResult,
                        "Every consumer must register before the first nonzero callee result: $caller")
                    assertTrue(schedule.trace.subList(registered + 1, firstResult).any { it == "replay" },
                        "An empty replay must precede publication for $caller")
                }
                assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty())
            } catch (failure: AssertionError) {
                throw AssertionError("mayConsumers=$mayConsumers sameUnit=$sameUnit " +
                    "program=${f.describe()} trace=${schedule.trace}", failure)
            }
        }
    }
}
