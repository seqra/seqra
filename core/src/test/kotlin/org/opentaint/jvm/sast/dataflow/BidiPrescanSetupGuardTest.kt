package org.opentaint.jvm.sast.dataflow

import kotlinx.coroutines.runBlocking
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.bidi.driver.BidiEntry
import org.opentaint.dataflow.bidi.driver.Report
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
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

/** Construction before prescan is setup; cleanup must not retry a failed lazy engine. */
class BidiPrescanSetupGuardTest : BasicTestUtils() {
    override val sourceFileExtension: String = "java"

    @Test
    fun `graph and manager setup exceptions and errors map to empty output without entry callbacks or cleanup retries`(): Unit {
        val root = findMethod("test.samples.AnyFieldPrimitiveSample", "sink")
        val graph = runBlocking { JApplicationSingleExitGraph(JApplicationGraphImpl(cp, cp.usagesExt())) }
        val units = object : JIRUnitResolver {
            override fun resolve(method: JIRMethod) =
                if (method.enclosingClass.declaration.location == root.enclosingClass.declaration.location) SingletonUnit else UnknownUnit
            override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != root.enclosingClass.declaration.location
        }
        for (graphFailure in listOf(true, false)) for (failure in listOf(
            IllegalStateException("prescan setup"), AssertionError("prescan setup"), OutOfMemoryError("prescan setup"))) {
            var graphCalls = 0
            var managerCalls = 0
            val calls = ArrayList<String>()
            val hook = object : BidiEntry {
                override fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>) { calls += "gather" }
                override fun run(budget: Duration): Report { calls += "run"; error("Failed setup must block run") }
                override fun toVulnerabilities(report: Report): List<VulnerabilityWithTrace> { calls += "output"; return emptyList() }
                override fun status(report: Report): TaintAnalysisUnitRunnerManager.Status { calls += "status"; return TaintAnalysisUnitRunnerManager.Status.OK }
            }
            val analyzer = object : TaintAnalyzer<JIRMethod, JIRInst>(TaintAnalyzerOptions(10.seconds, ApMode.Tree)) {
                override fun analysisGraph(): JApplicationSingleExitGraph {
                    graphCalls++
                    if (graphFailure) throw failure
                    return graph
                }
                override fun analysisManager(): TaintAnalysisManager {
                    managerCalls++
                    if (!graphFailure) throw failure
                    return JIRAnalysisManager(cp, RefManager(), JIRTaintRulesProvider(TaintConfiguration(cp)),
                        params = JIRAnalysisManager.Params(aliasAnalysisParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = false)))
                }
                override fun unitResolver(): JIRUnitResolver = units
                override fun bidiEntry(): BidiEntry = hook
            }
            val (output, status) = analyzer.use { it.analyzeWithIfds(listOf(root)) }
            assertTrue(output.isEmpty())
            assertEquals(TaintAnalyzer.Status(TaintAnalysisUnitRunnerManager.Status.EXCEPTION,
                TaintAnalysisUnitRunnerManager.Status.OK), status)
            assertTrue(calls.isEmpty(), "No entry callback follows failed prescan setup")
            assertEquals(1, if (graphFailure) graphCalls else managerCalls,
                "Closing the analyzer must not reinitialize the failed lazy engine")
        }
    }
}
