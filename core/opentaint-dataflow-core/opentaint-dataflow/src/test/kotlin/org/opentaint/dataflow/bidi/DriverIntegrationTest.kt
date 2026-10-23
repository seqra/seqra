package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.store.VulnerabilityKey
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class DriverIntegrationTest {
    @Test
    fun `limits one one two carry selected demand sink seed source hit and final raw record through joined barriers`(): Unit {
        DriverFixtures().use { f ->
            val program = f.refining()
            val driver = f.driver(FixedLimits(listOf(1, 1, 2)), run1MarkMode = MarkMode.GROUP)
            val report = driver.analyze(listOf(program.root))
            val mark = program.source.to.mark
            val path = program.source.to.path
            val zero = f.manager.zero.toPattern()
            val group = MarkSlot.Concrete(f.manager.marks.group(f.manager.marks.groupOf((mark as MarkSlot.Concrete).mark)))
            val coarse = Pattern(PathFact(AccessPathBase.Return, path.take(1), Tail.ANY, group), ExclusionSet.Empty)
            val precise = Pattern(program.source.to, ExclusionSet.Empty)
            assertEquals(listOf(1, 2, 3), f.configs.map { it.index })
            assertEquals(listOf(1, 1, 2), f.configs.map { it.fieldLimit })
            assertEquals(listOf(Direction.FORWARD, Direction.BACKWARD, Direction.FORWARD), f.configs.map { it.direction })
            assertTrue(f.results.all { it.status == RunStatus.COMPLETE })
            assertEquals(listOf(1, 2, 3), driver.frontiers.map { it.run })
            assertEquals(setOf(Conclusion(coarse.fact, ExclusionSet.Empty, true)),
                f.results[0].analyzers.single().summaries.demandEdges().filter { (_, facts) -> facts.base == AccessPathBase.Return }.flatMap { (_, facts) ->
                    f.ops.leaves(facts).map { Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND) }
                }.toSet())
            assertEquals(setOf(DemandPattern(coarse, zero)),
                f.configs[1].demand!!.near(program.root, coarse).toSet())
            assertEquals(listOf(Seed.Sink(program.rule, program.root, program.sink, precise)),
                f.configs[1].seeds.at(program.root, program.sink))
            val backward = f.results[1].analyzers.single()
            assertEquals(listOf(Triple(program.root, program.root.statement, program.source)),
                backward.sourceHits!!.entries().toList())
            assertSame(program.source, backward.sourceHits.entries().single().third)
            val backwardCoarse = coarse.copy(fact = coarse.fact.copy(mark = mark))
            assertEquals(setOf(DemandPattern(backwardCoarse, null)),
                f.configs[2].demand!!.near(program.root, backwardCoarse).toSet())
            assertEquals(listOf(Seed.Source(program.root, program.root.statement, program.source)),
                f.configs[2].seeds.at(program.root, program.root.statement))
            val finalRaw = f.results[2].analyzers.single().summaries.all().flatMap { (_, facts) ->
                f.ops.leaves(facts).map { Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND) }
            }.toSet()
            assertContains(finalRaw, Conclusion(precise.fact, ExclusionSet.Empty, false))
            assertTrue(f.results[2].analyzers.single().summaries.demandEdges().none())
            assertTrue(f.records.byEntry(program.root, zero).any { record ->
                record.direction == Direction.FORWARD && f.ops.leaves(record.conclusion).any { it == precise }
            }, "The final completed barrier persists the native normal source summary")
            val key = VulnerabilityKey(program.rule, program.root.method, program.sink)
            assertEquals(listOf(key), report.entries.map { it.key })
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(3, report.entries.single().run)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE,
                run1MarkMode = MarkMode.GROUP), report.end)
            assertEquals(listOf(1, 1, 1), driver.frontiers.map { it.seeds })
            assertTrue(driver.frontiers.last().demandEdges.isEmpty())
        }
    }

    @Test
    fun `selected limits run a real forward backward forward sequence before the fixed policy stops`(): Unit {
        DriverFixtures().use { f ->
            val program = f.persistentDemand()
            val driver = f.driver(FixedLimits(listOf(1, 1, 2)))
            val report = driver.analyze(listOf(program.root))
            assertEquals(listOf(1, 1, 2), f.configs.map { it.fieldLimit })
            assertEquals(listOf(1, 2, 3), driver.frontiers.map { it.run })
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.POLICY), report.end)
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(3, report.entries.single().run)
            assertTrue(f.results.filter { it.direction == Direction.FORWARD }.all { it.demandLayerEdges > 0L })
            assertTrue(f.configs[2].seeds.allowsSource(program.root, program.root.statement, program.source))
            assertEquals(listOf(1, 0, 1), driver.frontiers.map { it.demandVulnerabilities })
        }
    }

    @Test
    fun `public driver constructor wires the real method and call handlers and completes its final frontier`(): Unit {
        DriverFixtures().use { f ->
            val program = f.confirmed()
            val driver = IterationDriver(FixedLimits(listOf(1)), f.shared, 5.seconds)
            val report = driver.analyze(listOf(program.root))
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 1, Direction.FORWARD, EndReason.POLICY), report.end)
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(1, driver.frontiers.single().run)
            assertEquals(0, driver.frontiers.single().confirmedVulnerabilities)
            assertEquals(1, driver.frontiers.single().seeds)
        }
    }
}
