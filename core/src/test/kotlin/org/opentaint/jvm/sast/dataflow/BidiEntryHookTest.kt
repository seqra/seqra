package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.bidi.ap.MarkTable
import org.opentaint.dataflow.bidi.ap.FrozenMarkDeclarations
import kotlinx.coroutines.runBlocking
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.TaintSinkMeta
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.bidi.JIRBidiAnalysis
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import org.opentaint.jvm.sast.ast.BasicTestUtils
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Actual legacy prescan/entry hook; only the supplied phase-3 report is scripted. */
class BidiEntryHookTest : BasicTestUtils() {
    override val sourceFileExtension: String = "java"
    private val ap = ApManager(Cancellation().also { it.activate() }, marks = MarkTable(FrozenMarkDeclarations(setOf(
        "confirmed hook", "demand hook", "matching confirmed", "other demand", "unknown demand",
        "empty confirmed", "must not output",
    ).associateWith { name -> org.opentaint.dataflow.bidi.ap.FullKey(
        org.opentaint.dataflow.bidi.ap.encodeGroup(org.opentaint.dataflow.bidi.ap.MarkGroupKey.Model(name), false), name)
    })))
    private val ops = ApOps(ap)
    private val params = JIRAnalysisManager.Params(aliasAnalysisParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = false))
    private fun sample(): JIRMethod = findMethod("test.samples.AnyFieldPrimitiveSample", "sink")

    private fun report(status: RunStatus, entries: List<Report.Entry>, run: Int = 3): Report =
        Report(entries, AnalysisEnd(status, run, Direction.FORWARD,
            if (status == RunStatus.COMPLETE) EndReason.POLICY else EndReason.ABNORMAL))

    private fun item(id: String, state: ReportState, cwe: List<Int>? = null): Report.Entry {
        val method = sample()
        val statement = method.instList.last()
        val rule = object : CommonTaintConfigurationSink {
            override val id: String = id
            override val meta = TaintSinkMeta("hook $id", CommonTaintConfigurationSinkMeta.Severity.Warning, cwe)
        }
        val layer = if (state == ReportState.CONFIRMED) Layer.NORMAL else Layer.DEMAND
        val target = PathFact(AccessPathBase.Return, emptyList(), Tail.EXACT, MarkSlot.Concrete(ap.marks.mark(id)))
        val witness = SinkWitness(0, MethodEntryPoint(EmptyMethodContext, method.instList.first()),
            listOf(SinkEdge(ap.zero, layer, ops.targetTree(target, layer))), 1).also {
                it.confirmed = state == ReportState.CONFIRMED
            }
        return Report.Entry(VulnerabilityKey(rule, method, statement), state, 1, listOf(witness))
    }

    private fun exercise(
        supplied: Report,
        cwe: Set<Int>? = null,
        failure: Throwable? = null,
        failInGather: Boolean = false,
        failInFactory: Boolean = false,
        nullEntry: Boolean = false,
    ): Triple<List<VulnerabilityWithTrace>, TaintAnalyzer.Status, List<String>> {
        val method = sample()
        val units = object : JIRUnitResolver {
            override fun resolve(method: JIRMethod) =
                if (method.enclosingClass.declaration.location == sample().enclosingClass.declaration.location) SingletonUnit else UnknownUnit
            override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != method.enclosingClass.declaration.location
        }
        val graph = runBlocking { JApplicationSingleExitGraph(JApplicationGraphImpl(cp, cp.usagesExt())) }
        val rules = JIRTaintRulesProvider(TaintConfiguration(cp))
        val legacy = JIRAnalysisManager(cp, RefManager(), rules, params = params)
        val converter = JIRBidiAnalysis(cp, graph, units, rules, params, FixedLimits(listOf(1, 2, 3)), RefManager())
        val calls = ArrayList<String>()
        lateinit var analyzer: TaintAnalyzer<JIRMethod, JIRInst>
        val hook = object : BidiEntry {
            override fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>) {
                calls += "gather"
                assertSame(legacy, manager)
                analyzer.ifdsEngine.requirePrescanJoined()
                assertEquals(listOf(method), startMethods.map { it.method })
                assertTrue(startMethods.all { it.ctx == EmptyMethodContext })
                assertEquals(TaintAnalysisManager.Phase.Prescan, legacy.phase)
                if (failInGather && failure != null) throw failure
            }
            override fun run(budget: Duration): Report {
                calls += "run"
                assertTrue(budget.isPositive() && budget <= 10.seconds)
                assertFailsWith<IllegalStateException> { analyzer.ifdsEngine.apManager }
                assertEquals(TaintAnalysisManager.Phase.Prescan, legacy.phase, "The bidi branch must skip the legacy FullScan transition")
                if (!failInGather && failure != null) throw failure
                return supplied
            }
            override fun toVulnerabilities(report: Report): List<VulnerabilityWithTrace> {
                calls += "output"
                assertSame(supplied, report)
                return converter.toVulnerabilities(report)
            }
            override fun status(report: Report): TaintAnalysisUnitRunnerManager.Status {
                calls += "status"
                assertSame(supplied, report)
                return converter.status(report)
            }
        }
        analyzer = object : TaintAnalyzer<JIRMethod, JIRInst>(TaintAnalyzerOptions(10.seconds, ApMode.Tree, analysisCwe = cwe)) {
            override fun analysisGraph() = graph
            override fun analysisManager(): TaintAnalysisManager = legacy
            override fun unitResolver(): JIRUnitResolver = units
            override fun bidiEntry(): BidiEntry? {
                if (failInFactory) {
                    calls += "factory"
                    throw checkNotNull(failure)
                }
                return if (nullEntry) null else hook
            }
        }
        val result = analyzer.use { it.analyzeWithIfds(listOf(method)) }
        if (nullEntry) assertEquals(TaintAnalysisManager.Phase.FullScan, legacy.phase)
        return Triple(result.first, result.second, calls.toList())
    }

    @Test
    fun `phase three returns both states and maps every analysis status while simple trace status stays OK`(): Unit {
        val entries = listOf(item("confirmed hook", ReportState.CONFIRMED), item("demand hook", ReportState.DEMAND))
        for ((status, expected) in listOf(
            RunStatus.COMPLETE to TaintAnalysisUnitRunnerManager.Status.OK,
            RunStatus.TIMEOUT to TaintAnalysisUnitRunnerManager.Status.TIMEOUT,
            RunStatus.OOM to TaintAnalysisUnitRunnerManager.Status.OOM,
            RunStatus.FAILED to TaintAnalysisUnitRunnerManager.Status.EXCEPTION,
        )) {
            val (output, actual, calls) = exercise(report(status, entries))
            assertEquals(setOf("confirmed hook", "demand hook"), output.map { it.vulnerability.ruleId }.toSet())
            assertTrue(output.all { it.trace === TracePathGenerationResult.Simple })
            assertEquals(TaintAnalyzer.Status(expected, TaintAnalysisUnitRunnerManager.Status.OK), actual)
            assertEquals(listOf("gather", "run", "output", "status"), calls)
        }
    }

    @Test
    fun `phase three retains the existing CWE filter for both states and unknown CWE`(): Unit {
        val entries = listOf(item("matching confirmed", ReportState.CONFIRMED, listOf(89)),
            item("other demand", ReportState.DEMAND, listOf(79)),
            item("unknown demand", ReportState.DEMAND),
            item("empty confirmed", ReportState.CONFIRMED, emptyList()))
        val supplied = report(RunStatus.COMPLETE, entries)
        val filtered = exercise(supplied, cwe = setOf(89))
        assertEquals(setOf("matching confirmed", "unknown demand"), filtered.first.map { it.vulnerability.ruleId }.toSet())
        assertTrue(filtered.first.all { it.trace === TracePathGenerationResult.Simple })
        assertEquals(TaintAnalyzer.Status(TaintAnalysisUnitRunnerManager.Status.OK, TaintAnalysisUnitRunnerManager.Status.OK), filtered.second)
        val all = exercise(supplied)
        assertEquals(entries.map { it.key.rule.id }.toSet(), all.first.map { it.vulnerability.ruleId }.toSet())
    }

    @Test
    fun `setup exceptions and errors outside the driver return empty output and EXCEPTION with trace OK`(): Unit {
        for (duringGather in listOf(false, true)) for (failure in listOf(
            IllegalStateException("entry setup"), AssertionError("entry setup"), OutOfMemoryError("entry setup"))) {
            val (output, status, calls) = exercise(report(RunStatus.COMPLETE, listOf(item("must not output", ReportState.CONFIRMED))),
                failure = failure, failInGather = duringGather)
            assertTrue(output.isEmpty())
            assertEquals(TaintAnalyzer.Status(TaintAnalysisUnitRunnerManager.Status.EXCEPTION, TaintAnalysisUnitRunnerManager.Status.OK), status)
            assertEquals(if (duringGather) listOf("gather") else listOf("gather", "run"), calls)
        }
    }

    @Test
    fun `entry construction exceptions and errors stay inside the phase three setup guard`(): Unit {
        for (failure in listOf(IllegalStateException("entry factory"), AssertionError("entry factory"),
            OutOfMemoryError("entry factory"))) {
            val (output, status, calls) = exercise(
                report(RunStatus.COMPLETE, listOf(item("must not output", ReportState.CONFIRMED))),
                failure = failure, failInFactory = true)
            assertTrue(output.isEmpty())
            assertEquals(TaintAnalyzer.Status(TaintAnalysisUnitRunnerManager.Status.EXCEPTION,
                TaintAnalysisUnitRunnerManager.Status.OK), status)
            assertEquals(listOf("factory"), calls)
        }
    }

    @Test
    fun `an incomplete first report yields empty phase three output with its exact status`(): Unit {
        for ((status, expected) in listOf(
            RunStatus.TIMEOUT to TaintAnalysisUnitRunnerManager.Status.TIMEOUT,
            RunStatus.OOM to TaintAnalysisUnitRunnerManager.Status.OOM,
            RunStatus.FAILED to TaintAnalysisUnitRunnerManager.Status.EXCEPTION,
        )) {
            val (output, actual, calls) = exercise(report(status, emptyList(), run = 1))
            assertTrue(output.isEmpty())
            assertEquals(TaintAnalyzer.Status(expected, TaintAnalysisUnitRunnerManager.Status.OK), actual)
            assertEquals(listOf("gather", "run", "output", "status"), calls)
        }
    }

    @Test
    fun `a null bidi entry retains the actual prescan and legacy full scan path`(): Unit {
        val (output, status, calls) = exercise(report(RunStatus.COMPLETE, emptyList()), nullEntry = true)
        assertTrue(output.isEmpty())
        assertTrue(calls.isEmpty())
        assertEquals(TaintAnalyzer.Status(TaintAnalysisUnitRunnerManager.Status.OK, TaintAnalysisUnitRunnerManager.Status.OK), status)
    }
}
