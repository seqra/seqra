package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

class HandOffRegressionTest {
    private fun rule(id: String): RuleId = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun summary(vararg edges: PathEdge): StatementSummary = StatementSummary(
        edges.flatMap { listOf(it.from.base, it.to.base) }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    @Test
    fun `a confirmed end fact trigger recovers its source seed and retains the later demand finding`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val x = AccessPathBase.Argument(0)
            val y = AccessPathBase.Argument(1)
            val t = MarkSlot.Concrete(f.mark("source"))
            val u = MarkSlot.Concrete(f.mark("end"))
            val deep = listOf(f.field("f"), f.field("g"))
            val source = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
            val end = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(y, deep, Tail.EXACT, u), ExclusionSet.Empty))
            val early = SinkRule(rule("confirmed trigger"), 0, listOf(f.pattern(x, mark = t)), listOf(end))
            val later = SinkRule(rule("later demand"), 0, listOf(f.pattern(y, deep, mark = u)), emptyList())
            f.entryRules(RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val keep = summary(keepEdge(x), keepEdge(y), ZERO_KEEP)
            f.call(1, CallPlan(setOf(x, y), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, keep),
                CallStage.Edges(BOUND, RETURNED, END_FACTS, summary(end.forward), Guard.SinkTriggered(early)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, keep),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, keep),
            ), listOf(early), BEFORE, AFTER))
            f.exitRules(2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(later)),
                false, emptySet()))
            fun run(config: RunConfig): RunResult<RunMethodAnalyzer> {
                val actor = f.core(config, MethodLocalServicesFactory(::RunMethodLocalServices),
                    MethodCallServicesFactory(::RunMethodCallServices))
                actor.addRootZero()
                f.drain(actor, quantum = 1)
                actor.freeze()
                return RunResult(RunStatus.COMPLETE, listOf(actor), config.index, config.direction, f.vulnerabilities)
            }
            val report = ReportBuilder()
            val forwardConfig = f.config(index = 1, L = 1)
            val forward = run(forwardConfig)
            Support(forward, forwardConfig.roots, forwardConfig.mode, f.shared).confirm()
            report.add(forwardConfig, forward)
            val earlyKey = VulnerabilityKey(early.rule, f.key.method, f.inst(1))
            val laterKey = VulnerabilityKey(later.rule, f.key.method, f.inst(2))
            assertTrue(forward.witnessesByKey().getValue(earlyKey).all { it.confirmed })
            assertTrue(forward.witnessesByKey().getValue(laterKey).all { !it.confirmed })
            assertEquals(listOf(laterKey), report.demandEntries().map { it.key })
            assertEquals(1, report.confirmedCount)
            f.records.persist(Direction.FORWARD, forward.analyzers.asSequence().map { it.key to it.summaries })
            val toBackward = HandOff.of(forwardConfig, forward, report, f.shared)
            assertTrue(toBackward.seeds.at(f.key, f.inst(1)).isEmpty(), "The confirmed trigger adds no hand-off sink seed")
            assertEquals(listOf(Seed.Sink(later.rule, f.key, f.inst(2), later.patterns.single())),
                toBackward.seeds.at(f.key, f.inst(2)))
            val backwardConfig = f.config(index = 2, L = 1, demand = toBackward.demand, seeds = toBackward.seeds)
            val backward = run(backwardConfig)
            val actor = backward.analyzers.single()
            assertEquals(1L, actor.triggerSeeds, "The reversed end fact seeds the confirmed alternative")
            assertEquals(listOf(Triple(f.key, f.inst(0), source)), actor.sourceHits!!.entries().toList())
            assertSame(source, actor.sourceHits.entries().single().third)
            assertTrue(f.vulnerabilities.witnessesOf(2).none(), "Backward has no sink check")
            f.records.persist(Direction.BACKWARD, backward.analyzers.asSequence().map { it.key to it.summaries })
            val toForward = HandOff.of(backwardConfig, backward, report, f.shared)
            assertEquals(listOf(Seed.Source(f.key, f.inst(0), source)), toForward.seeds.at(f.key, f.inst(0)))
            assertEquals(1, toForward.seeds.size, "End facts create a trigger, not a synthetic source hit")
            val refinedConfig = f.config(index = 3, L = 1, demand = toForward.demand, seeds = toForward.seeds)
            val refined = run(refinedConfig)
            Support(refined, refinedConfig.roots, refinedConfig.mode, f.shared).confirm()
            report.add(refinedConfig, refined)
            assertTrue(refined.witnessesByKey().getValue(earlyKey).all { it.confirmed })
            assertTrue(refined.witnessesByKey().getValue(laterKey).all { !it.confirmed })
            val final = report.build(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.POLICY))
            assertEquals(setOf(earlyKey, laterKey), final.entries.map { it.key }.toSet())
            assertEquals(ReportState.CONFIRMED, final.entries.single { it.key == earlyKey }.state)
            assertEquals(1, final.entries.single { it.key == earlyKey }.run)
            assertEquals(ReportState.DEMAND, final.entries.single { it.key == laterKey }.state)
            assertEquals(3, final.entries.single { it.key == laterKey }.run)
            // A separate direct-core control omits the recovered source seed; this is not a driver schedule.
            val noSource = run(f.config(index = 5, L = 1, demand = toForward.demand, seeds = SeedIndex.EMPTY))
            assertTrue(noSource.witnessesByKey().isEmpty(), "Without the source seed neither positive sink fires")
        }
    }
}
