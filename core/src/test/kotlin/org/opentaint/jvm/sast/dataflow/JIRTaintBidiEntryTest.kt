package org.opentaint.jvm.sast.dataflow

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.configuration.jvm.serialized.*
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.PrimitiveTaintExt
import org.opentaint.dataflow.jvm.bidi.JIRBidiAnalysis
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import org.opentaint.jvm.sast.ast.BasicTestUtils
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The application entry uses its actual JVM constructor and the real legacy prescan. */
class JIRTaintBidiEntryTest : BasicTestUtils() {
    override val sourceFileExtension: String = "java"
    private fun sample(): JIRMethod = findMethod("test.samples.AnyFieldPrimitiveSample", "sink")
    private val mark = "real primitive T" + PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE

    private fun rules(): JIRTaintRulesProvider {
        val function = SerializedFunctionNameMatcher.Simple(
            `package` = SerializedSimpleNameMatcher.Simple("test.samples"),
            `class` = SerializedSimpleNameMatcher.Simple("AnyFieldPrimitiveSample"),
            name = SerializedSimpleNameMatcher.Simple("sink"))
        val arg = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
        val config = TaintConfiguration(cp)
        config.loadConfig(SerializedTaintConfig(
            entryPoint = listOf(SerializedRule.EntryPoint(function = function,
                taint = listOf(SerializedTaintAssignAction(kind = mark, pos = arg)), serializedId = "primitive-entry")),
            methodExitSink = listOf(SerializedRule.MethodExitSink(function = function,
                condition = SerializedCondition.ContainsMark(mark, arg), id = "primitive-exit",
                meta = SinkMetaData(note = "primitive exit"), serializedId = "primitive-exit-id"))))
        return JIRTaintRulesProvider(config)
    }

    private fun units(root: JIRMethod) = object : JIRUnitResolver {
        override fun resolve(method: JIRMethod) =
            if (method.enclosingClass.declaration.location == root.enclosingClass.declaration.location) SingletonUnit else UnknownUnit
        override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != root.enclosingClass.declaration.location
    }

    @Test
    fun `the actual optional JVM entry shares the prescan graph and its null control retains the old engine`(): Unit {
        val root = sample()
        val projects = object : ClassLocationChecker {
            override fun isProjectLocation(loc: RegisteredLocation): Boolean = loc == root.enclosingClass.declaration.location
            override fun isProjectClass(cls: JIRClassOrInterface): Boolean = isProjectLocation(cls.declaration.location)
        }
        for (bidi in listOf(false, true)) {
            val analyzer = JIRTaintAnalyzer(cp, rules(), projects, TaintAnalyzerOptions(20.seconds, ApMode.Tree),
                analysisUnit = units(root), bidiPolicy = if (bidi) FixedLimits(listOf(1, 1, 2)) else null)
            analyzer.use {
                val hook = analyzer.bidiEntry()
                if (bidi) {
                    val realEntry = assertIs<JIRBidiAnalysis>(hook)
                    val graph = JIRBidiAnalysis::class.java.getDeclaredField("graph").also { it.isAccessible = true }.get(realEntry)
                    assertSame(analyzer.ifdsEngine.graph, graph,
                        "The entry must use the already cached graph used by the real prescan engine")
                } else assertNull(hook)
                val (output, status) = analyzer.analyzeWithIfds(listOf(root))
                assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, status.analysisStatus)
                assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, status.traceResolutionStatus)
                assertEquals(setOf("primitive-exit"), output.map { it.vulnerability.ruleId }.toSet())
                assertTrue(output.all { (it.vulnerability.statement as JIRInst).location.method == root })
                if (bidi) {
                    assertTrue(output.all { it.trace === TracePathGenerationResult.Simple })
                    assertTrue(analyzer.ifdsEngine.allUnits().isEmpty())
                    assertFailsWith<IllegalStateException> { analyzer.ifdsEngine.apManager }
                } else {
                    assertTrue(analyzer.ifdsEngine.allUnits().isNotEmpty())
                    analyzer.ifdsEngine.apManager
                }
            }
        }
    }

    @Test
    fun `an unjoined prescan job blocks gather and run before either entry callback executes`(): Unit {
        val root = sample()
        val graph = runBlocking { JApplicationSingleExitGraph(JApplicationGraphImpl(cp, cp.usagesExt())) }
        val legacy = JIRAnalysisManager(cp, RefManager(), JIRTaintRulesProvider(TaintConfiguration(cp)),
            params = JIRAnalysisManager.Params(aliasAnalysisParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = false)))
        val calls = ArrayList<String>()
        val blocked = Job()
        val hook = object : BidiEntry {
            override fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>) { calls += "gather" }
            override fun run(budget: Duration): Report { calls += "run"; error("An unjoined prescan must block run") }
            override fun toVulnerabilities(report: Report): List<VulnerabilityWithTrace> { calls += "output"; return emptyList() }
            override fun status(report: Report): TaintAnalysisUnitRunnerManager.Status { calls += "status"; return TaintAnalysisUnitRunnerManager.Status.OK }
        }
        val analyzer = object : TaintAnalyzer<JIRMethod, JIRInst>(TaintAnalyzerOptions(10.seconds, ApMode.Tree)) {
            override fun analysisGraph() = graph
            override fun analysisManager(): TaintAnalysisManager = legacy
            override fun unitResolver(): JIRUnitResolver = units(root)
            override fun bidiEntry(): BidiEntry {
                calls += "factory"
                ifdsEngine.requirePrescanJoined()
                @Suppress("UNCHECKED_CAST")
                val owned = TaintAnalysisUnitRunnerManager::class.java.getDeclaredField("runnerJobs")
                    .also { it.isAccessible = true }.get(ifdsEngine) as ConcurrentLinkedQueue<Job>
                owned += blocked
                return hook
            }
        }
        try {
            val (output, status) = analyzer.analyzeWithIfds(listOf(root))
            assertTrue(blocked.isActive)
            assertFailsWith<IllegalStateException> { analyzer.ifdsEngine.requirePrescanJoined() }
            assertEquals(listOf("factory"), calls)
            assertTrue(output.isEmpty())
            assertEquals(TaintAnalyzer.Status(TaintAnalysisUnitRunnerManager.Status.EXCEPTION,
                TaintAnalysisUnitRunnerManager.Status.OK), status)
            analyzer.ifdsEngine.apManager
        } finally {
            blocked.complete()
            analyzer.close()
        }
    }
}
