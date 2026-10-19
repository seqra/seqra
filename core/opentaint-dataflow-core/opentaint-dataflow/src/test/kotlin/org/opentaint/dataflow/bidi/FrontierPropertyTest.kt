package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.demandProbe
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.HandOffFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class FrontierPropertyTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val returned = AccessPathBase.Return

    private fun emptyHandOff(f: HandOffFixtures): HandOff =
        HandOff(DemandStore.Builder(f.manager).build(), SeedIndex.EMPTY, emptyMap())

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun rule(id: String): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    @Test fun `real nonzero initial presence separates analyzed keys from zero-only stored work in both directions`(): Unit {
        for (index in listOf(3, 2)) HandOffFixtures(index).use { f ->
            val zero = f.key("zero only")
            val concrete = f.key("concrete initial")
            val flow = f.key("flow initial")
            f.core(zero).addRootZero()
            f.core(concrete).addInitial(f.initial())
            f.core(flow).addInitial(f.initial(y, tail = Tail.STAR, mark = MarkSlot.STAR))
            val result = f.result()
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(3L, f.core(zero).steps)
            assertEquals(2L, f.core(zero).edgeDeltas,
                "An accepted entry and summary count; unchanged interior processing is not another stored edge")
            assertFalse(f.core(zero).nonZeroInitial)
            assertTrue(f.core(concrete).nonZeroInitial)
            assertTrue(f.core(flow).nonZeroInitial)
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            val frontier = frontierOf(f.config, result, handOff, f.report)
            assertEquals(index, frontier.run)
            assertEquals(f.config.direction, frontier.direction)
            assertEquals(setOf(concrete, flow), frontier.analysed)
            assertEquals(setOf(zero), frontier.zeroOnly)
            assertEquals(2L, frontier.zeroOnlyEdges)
            assertEquals(emptyMap(), frontier.demandEdges)
            assertEquals(0L, frontier.recordCrossings)
            assertEquals(0, frontier.seeds)
            assertEquals(0, frontier.demandVulnerabilities)
            assertEquals(0, frontier.confirmedVulnerabilities)
            assertNull(frontier.demandByCause, "The engine has no optional cause counters")
        }
    }

    @Test fun `actual current queries authorize demand publications independently of absent exit guidance`(): Unit {
        HandOffFixtures(index = 3).use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            f.finite.statement(1, summary(PathEdge(PathFact(x, emptyList(), Tail.EXACT, t),
                PathFact(y, emptyList(), Tail.ANY, t), ExclusionSet.Empty)))
            val actor = f.core()
            actor.addInitial(f.initial(x, mark = t))
            val first = CallerRef(f.key("first caller"), f.manager.zero, Layer.NORMAL, f.inst(1))
            val second = first.copy(caller = f.key("second caller"))
            val added = f.fact(x, mark = t, layer = Layer.DEMAND)
            f.admit(actor, first, Layer.DEMAND, added)
            f.admit(actor, first, Layer.DEMAND, added)
            f.admit(actor, second, Layer.DEMAND, added)
            f.admit(actor, second, Layer.DEMAND, added)
            val result = f.result()
            assertEquals(3L, actor.demandLayerEdges,
                "One demand-input presence flag, one demand output edge and one demand summary delta")
            assertEquals(3L, result.demandLayerEdges)
            assertEquals(3L, actor.edgeDeltas)
            assertEquals(setOf(first, second), f.links(actor).asSequence().map { it.caller }.toSet())
            assertEquals(1, actor.summaries.demandEdges().count(), "The full current query authorizes its computed response without old exit demand")
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            val frontier = frontierOf(f.config, result, handOff, f.report)
            assertEquals(setOf(actor.key), frontier.analysed)
            assertTrue(frontier.zeroOnly.isEmpty())
            assertEquals(mapOf(actor.key to 1), frontier.demandEdges)
            assertEquals(0L, frontier.zeroOnlyEdges)
            assertNull(frontier.demandByCause)
        }
    }

    @Test fun `frontier records actual admitted native applications and zero-premise nonzero result work`(): Unit {
        HandOffFixtures(index = 3).use { f ->
            val callee = f.key("recorded callee")
            f.finite.call(1, CallPlan(setOf(x, returned), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(starEdge(returned, emptyList(), x, emptyList()))),
            ), emptyList(), BEFORE, AFTER))
            val actor = f.core(callFactory = MethodCallServicesFactory(::RunMethodCallServices))
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val initial = f.initial(x, mark = t)
            val sub = Subscription(callee, CallerRef(actor.key, f.manager.zero, Layer.NORMAL, f.inst(1)),
                Layer.NORMAL, f.fact(x, mark = t))
            actor.applyRecord(sub, Record(callee, Direction.FORWARD, initial, f.fact(returned, mark = u)))
            actor.applyRecord(sub, Record(callee, Direction.FORWARD, f.initial(x, mark = v), f.fact(returned, mark = u)))
            actor.applyRecord(sub, Record(callee, Direction.FORWARD, initial, f.fact(returned, mark = v)))
            val result = f.result()
            assertEquals(2L, actor.recordCrossings, "The wrong-mark record has no admitted native result")
            assertEquals(2L, result.recordCrossings)
            assertFalse(actor.nonZeroInitial, "Concrete effects of the zero premise are not nonzero initial facts")
            assertEquals(4L, actor.edgeDeltas)
            val frontier = frontierOf(f.config, result, emptyHandOff(f), f.report)
            assertEquals(2L, frontier.recordCrossings)
            assertTrue(frontier.analysed.isEmpty())
            assertEquals(setOf(actor.key), frontier.zeroOnly)
            assertEquals(4L, frontier.zeroOnlyEdges)
        }
    }

    @Test fun `bounded stored normalized demand counts equal an independent exact set in both hand-off directions`(): Unit {
        for (index in listOf(3, 2)) for (mask in 0..7) HandOffFixtures(index).use { f ->
            val a = f.field("a")
            val b = f.field("b")
            val t = f.mark("T")
            val u = f.mark("U")
            val markExclusions = listOf(MarkSet.EMPTY, f.manager.markSetOf(t), f.manager.markSetOf(u))
            val keys = listOf(f.key("left context"), f.key("right context"))
            val expected = LinkedHashSet<Pair<MethodKey, DemandPattern>>()
            for (key in keys) {
                val origins = if (index == 3) listOf(x to emptyList(), y to emptyList())
                    else listOf(x to emptyList(), x to listOf(a))
                for ((premiseBase, premisePath) in origins) {
                    val initial = f.initial(premiseBase, premisePath, Tail.STAR, MarkSlot.STAR)
                    f.core(key).addInitial(initial)
                    for (path in listOf(listOf(a), listOf(b))) for ((bit, exclusions) in markExclusions.withIndex()) {
                        if ((mask and (1 shl bit)) == 0) continue
                        val facts = f.fact(y, path, Tail.ANY, MarkSlot.Star(exclusions), layer = Layer.DEMAND)
                        f.selected(key, initial, facts) // An earlier stored union may already cover this attempt.
                        assertNull(f.selected(key, initial, facts), "Repeated selected-store inputs have no second delta")
                        // This expected set comes from the declared raw inputs, never from HandOff or an index query.
                        expected += key to DemandPattern(f.pattern(y, path, Tail.ANY, MarkSlot.STAR), initial.toPattern())
                    }
                }
                if (index == 2) {
                    f.selected(key, f.manager.zero, Reach.NORMAL)
                    f.selected(key, f.manager.zero, Reach.NORMAL)
                }
            }
            val result = f.result()
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            val counts = expected.groupingBy { it.first }.eachCount()
            assertEquals(counts, handOff.demandEdges, "index=$index mask=$mask")
            val actual = keys.flatMap { key -> listOf(listOf(a), listOf(b)).flatMap { path ->
                handOff.demand.near(key, demandProbe(y, path)).map { key to it }
            } }.toSet()
            assertEquals(expected, actual, "Normalized abstract exclusions collide, while full allowed premises and contexts remain distinct")
            val frontier = frontierOf(f.config, result, handOff, f.report)
            assertEquals(counts, frontier.demandEdges)
            assertEquals(keys.toSet(), frontier.analysed)
            assertTrue(frontier.zeroOnly.isEmpty())
            assertNull(frontier.demandByCause)
            assertEquals(if (mask == 0) 0 else 8, frontier.demandEdges.values.sum())
        }
    }

    @Test fun `frontier owns key and count snapshots and retains no mutable hand-off aliases`(): Unit {
        HandOffFixtures(index = 3).use { f ->
            val nonzero = f.key("nonzero")
            val zero = f.key("zero")
            f.core(nonzero).addInitial(f.initial())
            f.core(zero).addRootZero()
            val result = f.result()
            val counts = linkedMapOf(nonzero to 2)
            val seeds = arrayListOf<Seed>(Seed.Source(nonzero, f.inst(1),
                PathEdge(ZERO_FACT, f.pattern(returned).fact, ExclusionSet.Empty)))
            val handOff = HandOff(DemandStore.Builder(f.manager).build(), SeedIndex.of(seeds), counts)
            counts.clear()
            seeds.clear()
            val frontier = frontierOf(f.config, result, handOff, f.report)
            assertEquals(mapOf(nonzero to 2), handOff.demandEdges)
            assertEquals(mapOf(nonzero to 2), frontier.demandEdges)
            assertEquals(setOf(nonzero), frontier.analysed)
            assertEquals(setOf(zero), frontier.zeroOnly)
            assertEquals(1, frontier.seeds)
            assertFailsWith<UnsupportedOperationException> { (handOff.demandEdges as MutableMap).clear() }
            assertFailsWith<UnsupportedOperationException> { (frontier.demandEdges as MutableMap).clear() }
            assertFailsWith<UnsupportedOperationException> { (frontier.analysed as MutableSet).clear() }
            assertFailsWith<UnsupportedOperationException> { (frontier.zeroOnly as MutableSet).clear() }
            assertEquals(mapOf(nonzero to 2), frontier.demandEdges)
            assertEquals(2L, frontier.zeroOnlyEdges)
        }
    }

    @Test fun `frontier counts current report states only for a forward result`(): Unit {
        HandOffFixtures(index = 3).use { forward ->
            val actor = forward.core()
            actor.addRootZero()
            val confirmedRule = rule("confirmed")
            val demandRule = rule("demand")
            val confirmed = SinkWitness(0, actor.key,
                listOf(SinkEdge(forward.manager.zero, Layer.NORMAL, Reach.NORMAL)), 3).also { it.confirmed = true }
            val demand = SinkWitness(0, actor.key,
                listOf(SinkEdge(forward.manager.zero, Layer.DEMAND, Reach.DEMAND)), 3)
            forward.vulnerabilities.add(VulnerabilityKey(confirmedRule, actor.key.method, forward.inst(2)), confirmed)
            forward.vulnerabilities.add(VulnerabilityKey(demandRule, actor.key.method, forward.inst(2)), demand)
            val result = forward.result()
            forward.report.add(forward.config, result)
            val frontier = frontierOf(forward.config, result, emptyHandOff(forward), forward.report)
            assertEquals(1, frontier.confirmedVulnerabilities)
            assertEquals(1, frontier.demandVulnerabilities)
            HandOffFixtures(index = 2).use { backward ->
                backward.core().addRootZero()
                val backResult = backward.result()
                val backFrontier = frontierOf(backward.config, backResult, emptyHandOff(backward), forward.report)
                assertEquals(0, backFrontier.confirmedVulnerabilities)
                assertEquals(0, backFrontier.demandVulnerabilities)
                assertEquals(1, forward.report.confirmedCount)
                assertEquals(1, forward.report.demandEntries().size)
            }
        }
    }

    @Test fun `frontier rejects incomplete results and cancellation of a retained actor walk`(): Unit {
        HandOffFixtures(index = 3).use { f ->
            val handOff = emptyHandOff(f)
            for (status in listOf(RunStatus.TIMEOUT, RunStatus.OOM, RunStatus.FAILED))
                assertFailsWith<IllegalStateException> { frontierOf(f.config, f.result(status), handOff, f.report) }
            f.core().addRootZero()
            val complete = f.result()
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { frontierOf(f.config, complete, handOff, f.report) }
            f.cancellation.activate()
            assertEquals(setOf(f.key()), frontierOf(f.config, complete, handOff, f.report).zeroOnly)
        }
    }
}
