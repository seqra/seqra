package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Analyzer inventory 9: explicit joined-result witness-boundary regression, not a native-program theorem. */
class EarlierConfirmationStopTest {
    @Test
    fun `a final earlier confirmation suppresses latest demand only evidence and its seed before the stop decision`(): Unit {
        DriverFixtures().use { f ->
            val confirmed = f.confirmed()
            val pending = f.persistentDemand()
            val callbacks = ArrayList<Int>()
            var firstWitness: SinkWitness? = null
            var injected: SinkWitness? = null
            val policy = object : IterationPolicy {
                override fun fieldLimit(runIndex: Int): Int = maxOf(1, runIndex - 1)
                override fun continueAfter(run: RunConfig, result: RunResult<RunMethodAnalyzer>, frontiers: List<Frontier>): Boolean {
                    callbacks += run.index
                    assertTrue(run.index in listOf(1, 3), "STOP_RULE must precede a run-5 policy callback")
                    assertEquals(if (run.index == 1) 0 else 1, frontiers.last().confirmedVulnerabilities)
                    assertEquals(if (run.index == 1) 2 else 1, frontiers.last().demandVulnerabilities)
                    return true
                }
            }
            val driver = f.driver(policy, budget = 10.seconds, invokeRun = { config, owner, timeout ->
                val actual = f.run(config, owner, timeout)
                assertEquals(RunStatus.COMPLETE, actual.status)
                if (config.index == 3) {
                    assertEquals(setOf(confirmed.rule, pending.rule), actual.witnessesByKey().keys.map { it.rule }.toSet())
                    firstWitness = actual.witnessesByKey().entries.single { it.key.rule === confirmed.rule }.value.single()
                    // This test supplies an independent certificate at the joined-result seam.
                    // NORMAL alone is deliberately insufficient in the minimal driver.
                    assertNotNull(firstWitness).confirmed = true
                }
                if (config.index != 5) actual else {
                    val actualRows = actual.witnessesByKey()
                    assertEquals(setOf(pending.rule), actualRows.keys.map { it.rule }.toSet(),
                        "The unchanged real engine must still have exactly its persistent DEMAND finding")
                    assertTrue(actualRows.values.flatten().all { it.edges.any { edge -> edge.layer == Layer.DEMAND } })
                    assertTrue(actual.demandLayerEdges > 0L, "STOP_RULE must beat the object-based stop and policy")
                    val key = VulnerabilityKey(confirmed.rule, confirmed.root.method, confirmed.sink)
                    val demandFacts = f.ops.targetTree(confirmed.source.to, Layer.DEMAND)
                    val latest = SinkWitness(0, confirmed.root,
                        listOf(SinkEdge(f.manager.zero, Layer.DEMAND, demandFacts)), config.index)
                    injected = latest
                    // Only witness enumeration is replaced. Actual joined/frozen actors and every engine store stay intact.
                    val view = object : VulnerabilityStore {
                        override fun add(key: VulnerabilityKey, witness: SinkWitness): Unit = error("The complete-run view is read only")
                        override fun witnessesOf(run: Int): Sequence<Pair<VulnerabilityKey, SinkWitness>> =
                            if (run == 5) sequenceOf(key to latest) else actual.vulnerabilities.witnessesOf(run)
                    }
                    RunResult(actual.status, actual.analyzers, actual.runIndex, actual.direction, view)
                }
            })
            val report = driver.analyze(listOf(confirmed.root, pending.root))
            assertEquals(listOf(1, 2, 3, 4, 5), f.configs.map { it.index })
            assertEquals(listOf(1, 3), callbacks)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 5, Direction.FORWARD, EndReason.STOP_RULE), report.end)
            val entry = report.entries.single()
            assertSame(confirmed.rule, entry.key.rule)
            assertEquals(ReportState.CONFIRMED, entry.state)
            assertEquals(3, entry.run)
            assertSame(firstWitness, entry.witnesses.single())
            assertTrue(entry.witnesses.single().confirmed)
            assertFalse(assertNotNull(injected).confirmed, "The minimal driver must not confirm the injected latest DEMAND edge")
            assertEquals(listOf(1, 2, 3, 4, 5), driver.frontiers.map { it.run })
            val final = driver.frontiers.last()
            assertEquals(1, final.confirmedVulnerabilities)
            assertEquals(0, final.demandVulnerabilities)
            assertEquals(0, final.seeds, "A final prior CONFIRMED key cannot create a sink seed from latest DEMAND evidence")
            assertTrue(f.results.last().demandLayerEdges > 0L)
            assertTrue(f.configs[3].seeds.at(confirmed.root, confirmed.sink).isEmpty())
            assertTrue(f.configs[3].seeds.at(pending.root, pending.sink).isNotEmpty())
        }
    }
}
