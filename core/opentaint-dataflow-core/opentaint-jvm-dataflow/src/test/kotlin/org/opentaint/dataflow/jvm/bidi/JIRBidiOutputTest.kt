package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.TaintSinkMeta
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRPrescanFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Analyzer §9 output: conversion is independent of full trace resolution and retains both report states. */
class JIRBidiOutputTest : BidiJIRTestUtils() {
    private data class Context(val name: String) : MethodContext

    private fun entry(): BidiEntry = JIRBidiAnalysis(cp, graph, object : JIRUnitResolver {
        override fun resolve(method: JIRMethod) = SingletonUnit
        override fun locationIsUnknown(loc: RegisteredLocation): Boolean = false
    }, NoTaintRules, JIRAnalysisManager.Params(
        aliasAnalysisParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = false)), FixedLimits(listOf(1, 1, 2)), RefManager())

    private fun rule(id: String): RuleId = object : CommonTaintConfigurationSink {
        override val id: String = id
        override val meta = TaintSinkMeta("output $id", CommonTaintConfigurationSinkMeta.Severity.Warning, null)
    }

    private fun witness(context: String, confirmed: Boolean = false, demand: Boolean = false, run: Int = 1): SinkWitness {
        val method = method("copy")
        val fact = PathFact(AccessPathBase.Return, emptyList(), Tail.EXACT,
            MarkSlot.Concrete(manager.marks.mark("output T")))
        val layer = if (demand) Layer.DEMAND else Layer.NORMAL
        return SinkWitness(0, key(method, Context(context)), listOf(SinkEdge(manager.zero, layer, ops.targetTree(fact, layer))), run)
            .also { it.confirmed = confirmed }
    }

    private fun reportEntry(id: String, state: ReportState, witnesses: List<SinkWitness>, run: Int = 1): Report.Entry {
        val method = method("copy")
        return Report.Entry(VulnerabilityKey(rule(id), method, normalExit(method)), state, run, witnesses)
    }

    @Test
    fun `confirmed and demand report entries both become their exact sink rule with a simple trace`(): Unit {
        val confirmed = reportEntry("confirmed", ReportState.CONFIRMED, listOf(witness("confirmed", confirmed = true)))
        val demand = reportEntry("demand", ReportState.DEMAND, listOf(witness("demand", demand = true)))
        val report = Report(listOf(confirmed, demand), AnalysisEnd(RunStatus.COMPLETE, 1, Direction.FORWARD, EndReason.POLICY))
        val output = entry().toVulnerabilities(report)
        assertEquals(2, output.size)
        assertEquals(listOf("confirmed", "demand"), output.map { it.vulnerability.ruleId })
        for ((expected, actual) in report.entries.zip(output)) {
            assertSame(expected.key.statement, actual.vulnerability.statement)
            assertSame(expected.key.rule, actual.vulnerability.rule)
            assertEquals(setOf(expected.key.rule), actual.vulnerability.vulnerabilityRules.keys)
            assertSame(TracePathGenerationResult.Simple, actual.trace)
            val node = assertIs<TaintSinkTracker.TaintVulnerabilityRuleNode.Unconditional>(
                actual.vulnerability.vulnerabilityRules.getValue(expected.key.rule))
            assertSame(expected.witnesses.single().methodKey, node.methodEntryPoint)
        }
    }

    @Test
    fun `output chooses a confirmed witness context even when an unconfirmed witness is first`(): Unit {
        val first = witness("first context", demand = true)
        val confirmed = witness("confirmed context", confirmed = true)
        val last = witness("later confirmed context", confirmed = true)
        val e = reportEntry("selected context", ReportState.CONFIRMED, listOf(first, confirmed, last))
        val report = Report(listOf(e), AnalysisEnd(RunStatus.COMPLETE, 1, Direction.FORWARD, EndReason.STOP_RULE))
        val output = entry().toVulnerabilities(report).single()
        val node = assertIs<TaintSinkTracker.TaintVulnerabilityRuleNode.Unconditional>(output.vulnerability.vulnerabilityRules[e.key.rule])
        assertSame(confirmed.methodKey, node.methodEntryPoint)
        assertNotEquals(first.methodKey, node.methodEntryPoint)
        assertSame(e.key.statement, output.vulnerability.statement)
        assertSame(TracePathGenerationResult.Simple, output.trace)
    }

    @Test
    fun `a demand entry with no confirmed witness chooses its first full context key`(): Unit {
        val first = witness("first demand context", demand = true, run = 3)
        val later = witness("later demand context", demand = true, run = 3)
        val e = reportEntry("demand context", ReportState.DEMAND, listOf(first, later), run = 3)
        val report = Report(listOf(e), AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE))
        val output = entry().toVulnerabilities(report).single()
        val node = assertIs<TaintSinkTracker.TaintVulnerabilityRuleNode.Unconditional>(output.vulnerability.vulnerabilityRules[e.key.rule])
        assertSame(first.methodKey, node.methodEntryPoint)
        assertNotEquals(later.methodKey, node.methodEntryPoint)
        assertSame(e.key.rule, output.vulnerability.rule)
        assertSame(TracePathGenerationResult.Simple, output.trace)
    }

    @Test
    fun `all four statuses map exactly while both retained report states stay in output`(): Unit {
        val expected = mapOf(
            RunStatus.COMPLETE to TaintAnalysisUnitRunnerManager.Status.OK,
            RunStatus.TIMEOUT to TaintAnalysisUnitRunnerManager.Status.TIMEOUT,
            RunStatus.OOM to TaintAnalysisUnitRunnerManager.Status.OOM,
            RunStatus.FAILED to TaintAnalysisUnitRunnerManager.Status.EXCEPTION,
        )
        val entries = listOf(reportEntry("retained confirmed", ReportState.CONFIRMED, listOf(witness("confirmed", confirmed = true))),
            reportEntry("retained demand", ReportState.DEMAND, listOf(witness("demand", demand = true))))
        val converter = entry()
        for ((status, mapped) in expected) {
            val report = Report(entries, AnalysisEnd(status, 3, Direction.FORWARD,
                if (status == RunStatus.COMPLETE) EndReason.POLICY else EndReason.ABNORMAL))
            assertEquals(mapped, converter.status(report))
            val output = converter.toVulnerabilities(report)
            assertEquals(setOf("retained confirmed", "retained demand"), output.map { it.vulnerability.ruleId }.toSet())
            assertTrue(output.all { it.trace === TracePathGenerationResult.Simple })
        }
    }

    @Test
    fun `an incomplete first run converts its empty real report to no output and preserves its cause`(): Unit {
        val converter = entry()
        for ((status, mapped) in listOf(
            RunStatus.TIMEOUT to TaintAnalysisUnitRunnerManager.Status.TIMEOUT,
            RunStatus.OOM to TaintAnalysisUnitRunnerManager.Status.OOM,
            RunStatus.FAILED to TaintAnalysisUnitRunnerManager.Status.EXCEPTION,
        )) {
            val report = Report(emptyList(), AnalysisEnd(status, 1, Direction.FORWARD, EndReason.ABNORMAL))
            assertTrue(converter.toVulnerabilities(report).isEmpty())
            assertEquals(mapped, converter.status(report))
        }
    }

    @Test
    fun `the JVM entry requires gather and consumes the copied prescan once for a real empty program run`(): Unit {
        val converter = entry()
        assertFailsWith<IllegalStateException> { converter.run(5.seconds) }
        JIRPrescanFixtures(cp, graph).use { f ->
            assertFailsWith<IllegalStateException> { f.engine.requirePrescanJoined() }
            f.run()
            f.engine.requirePrescanJoined()
            converter.gather(f.manager, emptyList(), f.engine.prescanResolvedGraph())
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            assertTrue(f.engine.allUnits().isEmpty())
            assertFailsWith<IllegalStateException> { f.engine.apManager }
            val report = converter.run(5.seconds)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 1, Direction.FORWARD, EndReason.STOP_RULE), report.end)
            assertTrue(report.entries.isEmpty())
            assertTrue(converter.toVulnerabilities(report).isEmpty())
            assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, converter.status(report))
            assertFailsWith<IllegalStateException> { converter.run(5.seconds) }
        }
    }
}
