package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class CurrentModeIterationTest {
    private val arg = AccessPathBase.Argument(0)
    private val ret = AccessPathBase.Return

    private fun checkRuns(f: CurrentModeFixtures, driver: IterationDriver): Unit {
        assertEquals(listOf(1, 2, 3), f.actual.configs.map { it.index })
        assertEquals(listOf(1, 2, 3), f.actual.configs.map { it.fieldLimit })
        assertTrue(f.actual.results.all { it.status == RunStatus.COMPLETE })
        assertTrue(f.requests.none { it.run != 1 }, "F72 suppresses every restricted request")
        assertEquals(listOf(Direction.FORWARD, Direction.BACKWARD, Direction.FORWARD),
            driver.frontiers.map { it.direction })
        for (result in f.actual.results) {
            assertEquals(result.analyzers.sumOf { it.steps },
                f.processed.count { it.run == result.runIndex }.toLong())
            assertEquals(result.analyzers.sumOf { it.demandLayerEdges }, result.demandLayerEdges)
            assertEquals(result.analyzers.sumOf { it.recordCrossings }, result.recordCrossings)
            assertTrue(result.analyzers.all { it.edgeDeltas > 0 })
            assertTrue(f.processed.filter { it.run == result.runIndex }.none {
                it.delta.facts.base is AccessPathBase.CleanerTemporary
            }, "F74 temporaries are projected before method work is scheduled")
            for (actor in result.analyzers) {
                assertFailsWith<IllegalStateException> { actor.initials }
                assertFailsWith<IllegalStateException> { actor.edges }
            }
        }
    }
    private fun normalBackwardRecord(f: CurrentModeFixtures, c: CurrentCase, exit: Pattern,
                                     output: Pattern): Unit {
        assertTrue(f.actual.records.byEntry(c.callee, exit).any { record ->
            record.direction == Direction.BACKWARD && record.premise.toPattern() == exit &&
                record.conclusion.layer == Layer.NORMAL && output in f.ops.leaves(record.conclusion).toSet()
        }, "R1 persists the raw normal singleton rather than its transient read view")
    }
    private fun reportKey(c: CurrentCase, index: Int): VulnerabilityKey =
        VulnerabilityKey(c.rules[index], c.root.method, c.sinks)

    @Test
    fun `two marks share one backward getter initial and run three crosses the normal reversed record`(): Unit {
        CurrentModeFixtures().use { f ->
            val c = f.sharedGetter()
            val driver = f.driver()
            val report = driver.analyze(listOf(c.root))
            checkRuns(f, driver)
            assertTrue(f.requests.isEmpty(), "The two known source marks need no gate or cleaner request")
            val flowInput = f.p(arg, tail = Tail.STAR)
            val mayOutput = f.p(ret, tail = Tail.ANY)
            val flowReturn = f.p(ret, tail = Tail.STAR)
            val flowField = f.p(arg, c.fields, Tail.STAR)
            val forward = CurrentSummary(setOf(flowInput), Conclusion(mayOutput.fact, ExclusionSet.Empty, true))
            val backward = CurrentSummary(setOf(flowReturn), Conclusion(flowField.fact, ExclusionSet.Empty, false))
            assertContains(f.summaries(1, c.callee), forward)
            assertContains(f.summaries(1, c.callee, selected = true), forward)
            assertEquals(setOf(DemandPattern(mayOutput, flowInput)), f.patterns(2, c.callee, ret))
            assertEquals(setOf(flowReturn), f.premises(2, c.callee),
                "The T and U requirements emit the same interned FLOW initial")
            assertContains(f.summaries(2, c.callee), backward)
            assertTrue(f.summaries(2, c.callee, selected = true).none { it.conclusion.fact.base != AccessPathBase.Zero })
            normalBackwardRecord(f, c, flowReturn, flowField)
            assertTrue(f.patterns(3, c.callee, arg).isEmpty())
            assertTrue(f.patterns(3, c.callee, ret).isEmpty())
            assertTrue(f.premises(3, c.callee).isEmpty())
            assertFalse(f.actor(3, c.callee).nonZeroInitial)
            assertContains(driver.frontiers[1].analysed, c.callee)
            assertContains(driver.frontiers[2].zeroOnly, c.callee)
            assertTrue(f.actual.results[2].recordCrossings >= 2)
            for (mark in listOf(c.t, c.u)) {
                assertTrue(f.links.any { it.run == 1 && it.callee == c.callee && it.layer == Layer.NORMAL &&
                    it.added == f.p(arg, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(mark)) })
            }
            assertEquals(2, driver.frontiers[0].demandVulnerabilities)
            assertEquals(2, driver.frontiers[2].confirmedVulnerabilities)
            for (i in c.rules.indices) {
                val key = reportKey(c, i)
                assertTrue(f.actual.results[0].witnessesByKey().getValue(key).all { !it.confirmed &&
                    it.edges.all { edge -> edge.layer == Layer.DEMAND } })
                assertEquals(ReportState.CONFIRMED, report.entries.single { it.key == key }.state)
                assertEquals(3, report.entries.single { it.key == key }.run)
            }
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.STOP_RULE), report.end)
        }
    }

    @Test
    fun `cleaner publications retain excluded T while backward demand normalizes the mark and reversed records still stop T`(): Unit {
        CurrentModeFixtures().use { f ->
            val c = f.rootCleanerNormalization()
            val driver = f.driver()
            val report = driver.analyze(listOf(c.root))
            checkRuns(f, driver)
            val input = f.p(arg, tail = Tail.STAR)
            val excluded = MarkSlot.Star(MarkSet(intArrayOf(c.t.id)))
            val output = f.p(ret, tail = Tail.ANY, mark = excluded)
            val native = CurrentSummary(setOf(input), Conclusion(output.fact, ExclusionSet.Empty, true))
            assertContains(f.summaries(1, c.callee), native)
            assertContains(f.summaries(1, c.callee, selected = true), native)
            assertContains(f.patterns(2, c.callee, ret), DemandPattern(
                f.p(ret, tail = Tail.ANY), input))
            assertTrue(f.patterns(2, c.callee, ret).all { it.entry.fact.mark !is MarkSlot.Star ||
                (it.entry.fact.mark as MarkSlot.Star).excluded.isEmpty })
            assertEquals(setOf(f.p(ret, tail = Tail.STAR)), f.premises(2, c.callee))
            val backwardResult = f.p(arg, c.fields, Tail.STAR, excluded)
            normalBackwardRecord(f, c, f.p(ret, tail = Tail.STAR), backwardResult)
            assertTrue(f.premises(3, c.callee).isEmpty())
            assertTrue(f.actual.results[2].recordCrossings > 0)
            assertEquals(setOf(CurrentRequest(1, c.callee, input, RequestKind.Mark(c.t))), f.requests)
            val blocked = reportKey(c, 0)
            assertTrue(f.actual.results.all { blocked !in it.witnessesByKey() })
            assertTrue(report.entries.none { it.key == blocked })
            assertEquals(ReportState.CONFIRMED, report.entries.single { it.key == reportKey(c, 1) }.state)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.STOP_RULE), report.end)
        }
    }
}
