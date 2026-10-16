package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.TaintTree
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRPrescanFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** A nonempty real prescan is copied, released, and consumed by the actual JVM driver. */
class JIRBidiRealEntryTest : BidiJIRTestUtils() {
    @Test
    fun `copied rules and root produce an unproved exact exit candidate after prescan owners are released`(): Unit {
        val root = method("normal", JIRPrescanFixtures.SAMPLE)
        val mark = TaintMark("real entry T")
        val source = TaintEntryPointSource(root, mkTrue(), listOf(AssignMark(mark, Argument(0))),
            null, "real-entry-source")
        val sink = TaintMethodExitSink(root, CommonCondition.Atom(ContainsMark(Result, mark)), emptyList(),
            "real-entry-sink", TaintSinkMeta("real entry sink", CommonTaintConfigurationSinkMeta.Severity.Warning, null),
            null, "real-entry-sink-id")
        val selected = ArrayList<Set<String>>()
        var selection: Set<String>? = null
        val rules = object : TaintRulesProvider by NoTaintRules {
            override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?,
                allRelevant: Boolean): Iterable<TaintEntryPointSource> =
                if (method == root && (selection == null || source.serializedId in checkNotNull(selection)))
                    listOf(source) else emptyList()
            override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?,
                initialFacts: Set<InitialFactAp>?, allRelevant: Boolean): Iterable<TaintMethodExitSink> =
                if (method == root && (selection == null || sink.serializedId in checkNotNull(selection)))
                    listOf(sink) else emptyList()
            override fun selectRules(ruleIds: Set<String>) {
                selection = ruleIds.toSet()
                selected += checkNotNull(selection)
            }
        }
        JIRPrescanFixtures(cp, graph, rules).use { f ->
            f.run("normal")
            assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, f.engine.status.get())
            f.engine.requirePrescanJoined()
            val copiedIds = setOf("real-entry-source", "real-entry-sink-id")
            assertEquals(copiedIds, f.manager.prescanRuleIds())
            var runtimeShared: org.opentaint.dataflow.bidi.engine.SharedObjects? = null
            var actualDriver: IterationDriver? = null
            val entry = JIRBidiAnalysis(cp, graph, f.units, rules, f.params, FixedLimits(listOf(1, 2, 3)), f.refManager,
                driverFactory = { policy, shared, remaining ->
                    runtimeShared = shared
                    assertTrue(assertNotNull(shared.run1StaticProfiles).contains(root),
                        "The remapped catalog must remain available to the actual engine after prescan release")
                    IterationDriver(policy, shared, remaining).also { actualDriver = it }
                })
            entry.gather(f.manager, listOf(MethodWithContext(root, EmptyMethodContext)), f.engine.prescanResolvedGraph())
            assertEquals(listOf(copiedIds), selected, "Gather selects once before collecting effective metadata")
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            assertTrue(f.contexts().isEmpty())
            assertTrue(f.manager.prescanRuleIds().isEmpty())
            assertTrue(f.engine.allUnits().isEmpty())
            assertFailsWith<IllegalStateException> { f.engine.apManager }

            val report = entry.run(10.seconds)
            assertNull(assertNotNull(runtimeShared).run1StaticProfiles,
                "The actual joined run and analysis close must release the catalog, while the report remains usable")
            assertEquals(listOf(copiedIds), selected)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE), report.end)
            val frontiers = assertNotNull(actualDriver).frontiers
            assertEquals(listOf(1, 2, 3), frontiers.map { it.run }, "The NORMAL F1 candidate still requires B2/F3 refinement")
            assertEquals(listOf(Direction.FORWARD, Direction.BACKWARD, Direction.FORWARD), frontiers.map { it.direction })
            assertTrue(frontiers.all { it.confirmedVulnerabilities == 0 })
            assertEquals(1, frontiers.first().demandVulnerabilities)
            assertTrue(frontiers.first().demandEdges.isNotEmpty(), "NORMAL F1 publications remain guidance")
            assertEquals(0L, frontiers[1].recordCrossings, "B2 does not read an executable F1 record")
            val item = report.entries.single()
            assertEquals(ReportState.DEMAND, item.state)
            assertEquals(3, item.run)
            assertSame(sink, item.key.rule)
            assertSame(root, item.key.method)
            assertSame(normalExit(root), item.key.statement)
            assertTrue(item.witnesses.all { !it.confirmed }, "Normal root facts alone are not a confirmation certificate")
            for (witness in item.witnesses) {
                assertSame(root, witness.methodKey.method)
                assertEquals(EmptyMethodContext, witness.methodKey.context)
                for (edge in witness.edges) {
                    assertTrue(edge.premise.isZero)
                    assertEquals(Layer.NORMAL, edge.layer)
                    val exact = assertIs<TaintTree>(edge.facts)
                    assertEquals(AccessPathBase.Return, exact.base)
                    assertEquals(Layer.NORMAL, exact.layer)
                    assertEquals(1, exact.root.leaf.exact.ids.size)
                    assertTrue(exact.root.leaf.any.isEmpty)
                    assertNull(exact.root.accessors)
                }
            }
            val output = entry.toVulnerabilities(report).single()
            assertSame(sink, output.vulnerability.rule)
            assertSame(item.key.statement, output.vulnerability.statement)
            assertSame(TracePathGenerationResult.Simple, output.trace)
            val node = assertIs<TaintSinkTracker.TaintVulnerabilityRuleNode.Unconditional>(
                output.vulnerability.vulnerabilityRules.getValue(sink))
            assertSame(item.witnesses.first().methodKey, node.methodEntryPoint)
            assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, entry.status(report))
        }
    }
}
