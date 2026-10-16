package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.store.VulnerabilityKey
import kotlin.test.*

class DemandLinkTest {
    @Test
    fun `DLINK runs one through five because the root exact cleaner creates a demand link despite normal run three edges`(): Unit {
        DriverFixtures().use { f ->
            val program = f.demandLink()
            val driver = f.driver(FixedLimits(listOf(1, 2, 3, 4, 5)))
            val report = driver.analyze(listOf(program.root))
            val argument = AccessPathBase.Argument(0)
            val mark = MarkSlot.Concrete(program.mark)
            val coarseLink = Pattern(PathFact(argument, emptyList(), Tail.ANY_TAINT, mark), ExclusionSet.Empty)
            val exactSink = Pattern(PathFact(argument, listOf(program.g, program.h), Tail.EXACT, mark), ExclusionSet.Empty)
            assertEquals(listOf(1, 2, 3, 4, 5), f.configs.map { it.index })
            assertEquals(listOf(1, 2, 3, 4, 5), f.configs.map { it.fieldLimit })
            assertTrue(f.results.all { it.status == RunStatus.COMPLETE })
            val runThree = f.results[2]
            val stored = f.processed.filter { it.run == 3 }
            assertTrue(stored.isNotEmpty())
            assertTrue(stored.all { it.delta.facts.layer == Layer.NORMAL },
                "At limit 3 every processed delta is normal; a coarse link is the remaining demand object")
            assertTrue(runThree.analyzers.all { actor -> actor.summaries.all().all { it.second.layer == Layer.NORMAL } })
            val mThree = runThree.analyzers.single { it.key == program.sinkMethod }
            val demandLinks = f.links(3, mThree.key).asSequence().filter { it.linkLayer == Layer.DEMAND }.toList()
            assertTrue(demandLinks.isNotEmpty(), "The primitive root EXACT cleaner demotes the layer while retaining the must payload")
            assertTrue(demandLinks.any { it.addedFact == coarseLink })
            assertTrue(runThree.demandLayerEdges > 0L, "NO_DEMAND_EDGE includes accepted demand links")
            assertTrue(mThree.summaries.all().none(), "The throwing method has no reached normal exit summary")
            val key = VulnerabilityKey(program.rule, program.sinkMethod.method, program.sink)
            assertTrue(runThree.witnessesByKey().getValue(key).all { witness ->
                !witness.confirmed && witness.edges.all { it.layer == Layer.NORMAL }
            }, "Normal concrete emission cannot confirm through a demand link")
            assertTrue(runThree.witnessesByKey().getValue(key).any { witness ->
                witness.edges.any { edge -> f.ops.leaves(edge.facts).any { it == exactSink } }
            })
            val coarseDemand = Pattern(PathFact(argument, listOf(program.q, program.r), Tail.ANY, mark), ExclusionSet.Empty)
            assertContains(f.configs[2].demand!!.near(program.reader, argument, f.manager.path(listOf(program.q, program.r)))
                .map { it.entry }, coarseDemand)
            val preciseDemand = Pattern(PathFact(argument, listOf(program.q, program.r, program.g, program.h), Tail.EXACT, mark),
                ExclusionSet.Empty)
            assertContains(f.configs[4].demand!!.near(program.reader, argument, f.manager.path(preciseDemand.fact.path))
                .map { it.entry }, preciseDemand)
            val mFive = f.results[4].analyzers.single { it.key == program.sinkMethod }
            assertTrue(f.links(5, mFive.key).asSequence().any { link ->
                link.linkLayer == Layer.NORMAL && link.addedFact == exactSink
            }, "The precise descendant survives root EXACT cleaning in run 5")
            assertTrue(f.results[4].witnessesByKey().getValue(key).all { !it.confirmed })
            assertEquals(ReportState.DEMAND, report.entries.single { it.key == key }.state)
            assertEquals(5, report.entries.single { it.key == key }.run)
            assertTrue(f.links(5, mFive.key).any { it.linkLayer == Layer.DEMAND && it.addedFact == coarseLink },
                "The actual broad query remains additive to the precise guide refinement")
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 5, Direction.FORWARD, EndReason.POLICY), report.end)
            assertEquals(listOf(1, 2, 3, 4, 5), driver.frontiers.map { it.run })
            assertEquals(1, driver.frontiers[2].demandVulnerabilities)
            assertEquals(1, driver.frontiers[2].seeds)
            assertEquals(0, driver.frontiers[4].confirmedVulnerabilities)
            assertEquals(1, driver.frontiers[4].seeds)
        }
    }
}
