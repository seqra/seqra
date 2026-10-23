package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class CurrentModeActionsTest {
    private val arg = AccessPathBase.Argument(0)
    private val ret = AccessPathBase.Return

    private fun checkRuns(f: CurrentModeFixtures, driver: IterationDriver): Unit {
        assertEquals(listOf(1, 2, 3), f.actual.configs.map { it.index })
        assertEquals(listOf(1, 1, 2), f.actual.configs.map { it.fieldLimit })
        assertTrue(f.actual.results.all { it.status == RunStatus.COMPLETE })
        assertTrue(f.requests.none { it.run != 1 })
        assertEquals(3, driver.frontiers.size)
        for (result in f.actual.results) {
            val processed = f.processed.filter { it.run == result.runIndex }
            assertTrue(processed.isNotEmpty())
            assertEquals(result.analyzers.sumOf { it.steps }, processed.size.toLong())
            assertEquals(result.analyzers.sumOf { it.demandLayerEdges }, result.demandLayerEdges)
            assertEquals(result.analyzers.sumOf { it.recordCrossings }, result.recordCrossings)
            assertTrue(processed.none { it.delta.facts.base is AccessPathBase.CleanerTemporary })
            for (actor in result.analyzers) {
                assertFailsWith<IllegalStateException> { actor.initials }
                assertFailsWith<IllegalStateException> { actor.edges }
            }
        }
    }
    private fun key(c: CurrentCase, index: Int): VulnerabilityKey =
        VulnerabilityKey(c.rules[index], c.root.method, c.sinks)
    private fun native(f: CurrentModeFixtures, c: CurrentCase, entry: Pattern, output: Pattern): Unit {
        assertTrue(f.actual.records.byEntry(c.callee, entry).any { record ->
            record.direction == Direction.BACKWARD && record.premise.toPattern() == entry &&
                record.conclusion.layer == Layer.NORMAL && output in f.ops.leaves(record.conclusion).toSet()
        }, "entry=$entry output=$output backward=${f.summaries(2, c.callee)} records=" +
            f.actual.records.byEntry(c.callee, entry).map { record ->
                Triple(record.direction, record.premise.toPattern(), f.ops.leaves(record.conclusion).toList())
            }.toList())
    }

    @Test
    fun `mark changing pass asks once in run one and concrete backward record finds U without a restricted request`(): Unit {
        CurrentModeFixtures().use { f ->
            val c = f.markChangingPass()
            val driver = f.driver()
            val report = driver.analyze(listOf(c.root))
            checkRuns(f, driver)
            val flow = f.p(arg, tail = Tail.STAR)
            val grouped = GroupedScalarMarks(f.manager.marks)
            val groupT = grouped.slot(MarkSlot.Concrete(c.t)) as MarkSlot.Concrete
            val requested = f.p(arg, tail = Tail.STAR, mark = groupT)
            val concreteResult = f.p(ret, mark = grouped.slot(MarkSlot.Concrete(c.u)))
            val nativeCell = CurrentSummary(setOf(requested), Conclusion(concreteResult.fact, ExclusionSet.Empty, true))
            assertContains(f.premises(1, c.callee), flow)
            assertContains(f.premises(1, c.callee), requested)
            assertContains(f.summaries(1, c.callee), nativeCell)
            assertContains(f.summaries(1, c.callee, selected = true), nativeCell)
            assertTrue(f.summaries(1, c.callee).none { cell ->
                cell.premise == setOf(flow) && cell.conclusion.fact.base in setOf(ret, c.outputs[2])
            }, "An abstract FLOW mark cannot pass the concrete T-to-U gate")
            assertEquals(setOf(CurrentRequest(1, c.callee, flow, RequestKind.Mark(groupT.mark))), f.requests)
            assertContains(f.patterns(2, c.callee, ret), DemandPattern(concreteResult, requested))
            val fullResult = f.p(ret, mark = MarkSlot.Concrete(c.u))
            val atArgument = f.p(arg, c.fields, mark = MarkSlot.Concrete(c.t))
            assertContains(f.summaries(2, c.callee), CurrentSummary(setOf(fullResult),
                Conclusion(atArgument.fact, ExclusionSet.Empty, false)))
            native(f, c, fullResult, atArgument)
            assertTrue(f.summaries(2, c.callee, selected = true).none { it.conclusion.fact.base != AccessPathBase.Zero })
            assertTrue(f.patterns(3, c.callee, arg).isEmpty())
            assertEquals(setOf(atArgument), f.premises(3, c.callee),
                "The actual concrete input starts independently of reusable native records")
            assertContains(driver.frontiers[2].analysed, c.callee)
            assertTrue(f.actual.results[2].recordCrossings > 0)
            assertTrue(f.links.any { it.run == 1 && it.callee == c.callee && it.layer == Layer.DEMAND &&
                it.added == grouped.pattern(atArgument) })
            assertTrue(f.actual.results[0].witnessesByKey().getValue(key(c, 0)).all { !it.confirmed &&
                it.edges.all { edge -> edge.layer == Layer.DEMAND } })
            assertEquals(ReportState.DEMAND, report.entries.single { it.key == key(c, 0) }.state)
            assertEquals(3, report.entries.single { it.key == key(c, 0) }.run)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE,
                run1MarkMode = MarkMode.GROUP), report.end)
        }
    }

    @Test
    fun `lowered named field cleaner removes f and native records retain g and k through the full iteration`(): Unit {
        CurrentModeFixtures().use { f ->
            val c = f.partialFieldCleaner()
            val driver = f.driver()
            val report = driver.analyze(listOf(c.root))
            checkRuns(f, driver)
            val h = c.fields[0]; val field = c.fields[1]; val g = c.fields[2]; val k = c.fields[3]
            val action = assertNotNull(c.fieldAction)
            assertTrue(action.temporary is AccessPathBase.CleanerTemporary)
            assertSame(action.temporary, action.cleaner.base)
            assertNull(action.cleaner.path)
            assertEquals(CleanReach.AT_AND_BELOW, action.cleaner.reach)
            assertEquals(c.t, action.cleaner.mark)
            assertEquals(setOf(arg, action.temporary), action.read.touched)
            assertTrue(action.read.edges.any { it.edge.from.base == arg && it.edge.from.path == listOf(field) &&
                it.edge.to.base === action.temporary && it.edge.exclusion == ExclusionSet.Empty })
            assertTrue(action.write.edges.any { it.edge.from.base === action.temporary &&
                it.edge.to.base == arg && it.edge.to.path == listOf(field) })
            val flow = f.p(arg, tail = Tail.STAR)
            val groupT = GroupedScalarMarks(f.manager.marks).slot(MarkSlot.Concrete(c.t)) as MarkSlot.Concrete
            assertEquals(setOf(CurrentRequest(1, c.callee, flow, RequestKind.Mark(groupT.mark))), f.requests)
            assertTrue(f.cuts.any { cut -> cut.run == 1 && cut.point == Cut.STATEMENT &&
                cut.facts is FlowTree && f.ops.leaves(cut.facts).any { leaf ->
                    leaf.fact.base == arg && leaf.fact.path == listOf(field) && leaf.fact.tail == Tail.ANY &&
                        leaf.fact.mark == MarkSlot.Star(MarkExclusions.of(f.manager.marks, groupT.mark))
                }
            }, "F1 FLOW excludes the whole group on the temporary before the ordinary strong write")
            assertTrue(f.summaries(1, c.callee, selected = true).any { it.conclusion.demand &&
                it.conclusion.fact.base == ret && it.conclusion.fact.tail == Tail.ANY })
            assertContains(f.premises(1, c.callee), f.p(arg, tail = Tail.STAR, mark = groupT))
            val gReturn = f.p(ret, listOf(g), mark = MarkSlot.Concrete(c.t))
            val gInput = f.p(arg, listOf(h, g), mark = MarkSlot.Concrete(c.t))
            val kInput = f.p(arg, listOf(h, k), mark = MarkSlot.Concrete(c.u))
            val kReturn = f.p(ret, listOf(k), mark = MarkSlot.Concrete(c.u))
            val gResidual = f.p(arg, listOf(g), mark = MarkSlot.Concrete(c.t))
            val kResidual = f.p(arg, listOf(k), mark = MarkSlot.Concrete(c.u))
            native(f, c, gReturn, gResidual)
            native(f, c, kReturn, kResidual)
            // B2/L1 keeps the disjoint normal residual, but the h-prefix
            // source path needs two fields and therefore remains demanded.
            for ((entry, mark) in listOf(gReturn to c.t, kReturn to c.u)) {
                val coarse = f.p(arg, listOf(h), Tail.ANY, MarkSlot.Concrete(mark))
                assertContains(f.summaries(2, c.callee), CurrentSummary(setOf(entry),
                    Conclusion(coarse.fact, ExclusionSet.Empty, true)))
            }
            assertTrue(f.premises(2, c.callee).all { it.fact.mark is MarkSlot.Concrete },
                "Later phases preserve actual concrete marks instead of executing FLOW guidance")
            assertTrue(f.summaries(2, c.callee).none { it.conclusion.fact ==
                PathFact(arg, listOf(h, field), Tail.EXACT, MarkSlot.Concrete(c.t)) })
            assertTrue(f.patterns(3, c.callee, arg).all { it.entry.fact.mark is MarkSlot.Concrete },
                "Any retained guidance is concrete; it never creates executable FLOW starts")
            val cleanedInput = f.p(arg, listOf(h, field), mark = MarkSlot.Concrete(c.t))
            val actualInputs = setOf(cleanedInput, gInput, kInput)
            assertEquals(actualInputs, f.premises(3, c.callee),
                "A coarse B2 request may admit the f input; the Full cleaner must reject its sink output")
            val sources = f.program.entryInputs.single { it.key == c.root }.rules.summary.edges.map { it.forward }
                .filter { it.from.base == AccessPathBase.Zero && it.to.base != AccessPathBase.Zero }
            assertEquals(3, sources.size)
            for (source in sources) assertTrue(f.actual.configs[2].seeds.allowsSource(
                c.root, f.program.inst(c.root, 0), source), "Each admitted actual source retains its exact B2 source gate")
            assertContains(driver.frontiers[2].analysed, c.callee)
            for (expected in actualInputs) {
                assertTrue(f.links.any { it.run == 3 && it.callee == c.callee && it.layer == Layer.NORMAL &&
                    it.added == expected })
            }
            assertTrue(f.actual.results[2].recordCrossings >= 2)
            assertEquals(0L, f.actual.results[2].demandLayerEdges)
            val killed = key(c, 0)
            assertTrue(killed !in f.actual.results[2].witnessesByKey())
            assertTrue(report.entries.none { it.key == killed })
            assertEquals(setOf(key(c, 1), key(c, 2)), report.entries.map { it.key }.toSet())
            assertTrue(report.entries.all { it.state == ReportState.DEMAND && it.run == 3 })
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE,
                run1MarkMode = MarkMode.GROUP), report.end)
        }
    }
}
