package org.opentaint.jvm.sast.dataflow

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.config.JavaDefaultConfigLoader
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.ResolvedPrescanGraph
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.bidi.driver.BidiEntry
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.dataflow.bidi.driver.Report
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature.JIRLambdaMethod
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.dataflow.jvm.bidi.JIRBidiAnalysis
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import org.opentaint.jvm.sast.ast.BasicTestUtils
import org.opentaint.jvm.sast.dataflow.DataFlowApproximationLoader.isApproximation
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.util.analysis.ApplicationGraph
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AnalysisTest : BasicTestUtils() {
    fun functionMatcher(fqn: String, methodName: String) = SerializedFunctionNameMatcher.Simple(
        `package` = SerializedSimpleNameMatcher.Simple(fqn.substringBeforeLast('.')),
        `class` = SerializedSimpleNameMatcher.Simple(fqn.substringAfterLast('.')),
        name = SerializedSimpleNameMatcher.Simple(methodName)
    )

    fun List<Pair<PositionBase, String>>.condition(): SerializedCondition =
        SerializedCondition.and(map {
            SerializedCondition.ContainsMark(it.second, PositionBaseWithModifiers.BaseOnly(it.first))
        })

    fun sourceRule(
        fqn: String,
        methodName: String,
        taintMark: String,
        condition: List<Pair<PositionBase, String>> = emptyList()
    ): SerializedRule.Source = SerializedRule.Source(
        function = functionMatcher(fqn, methodName),
        condition = condition.condition(),
        taint = listOf(
            SerializedTaintAssignAction(
                kind = taintMark,
                pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
            )
        )
    )

    fun entryPointRule(fqn: String, methodName: String, taintMark: String, argIndex: Int) =
        SerializedRule.EntryPoint(
            function = functionMatcher(fqn, methodName),
            taint = listOf(
                SerializedTaintAssignAction(
                    kind = taintMark,
                    pos = PositionBaseWithModifiers.BaseOnly(Argument(argIndex))
                )
            )
        )

    fun sinkRule(
        fqn: String,
        methodName: String,
        ruleId: String,
        condition: List<Pair<PositionBase, String>>
    ): SerializedRule.Sink {
        return SerializedRule.Sink(
            condition = condition.condition(),
            function = functionMatcher(fqn, methodName),
            id = ruleId,
            meta = SinkMetaData(note = "Sink message: $ruleId")
        )
    }

    fun methodExitSinkRule(
        fqn: String,
        methodName: String,
        ruleId: String,
        mark: String
    ): SerializedRule.MethodExitSink {
        return SerializedRule.MethodExitSink(
            condition = listOf(PositionBase.Result to mark).condition(),
            function = functionMatcher(fqn, methodName),
            id = ruleId
        )
    }

    open val useDefaultConfig = false

    /** Full suite opt-in. An unset environment retains the legacy entry. */
    open val useBidiEntry = System.getenv("OPENTAINT_TEST_BIDI") == "true"

    open val bidiLimits: List<Int> =
        if (System.getenv("OPENTAINT_TEST_BIDI") == "true") listOf(1) + (1..8).toList() else listOf(1, 1, 2)

    open val apMode: ApMode = ApMode.Tree

    open val analysisUnrollStrategy: AnyAccessorUnrollStrategy = AnyAccessorUnrollStrategy.AnyAccessorDisabled

    private class SingleLocationUnit(
        val loc: RegisteredLocation,
        private val admitLambdas: Boolean = false,
    ) : JIRUnitResolver {
        override fun resolve(method: JIRMethod): UnitType {
            if ((admitLambdas && method is JIRLambdaMethod) ||
                method.enclosingClass.declaration.location == loc || isApproximation(method)) {
                return SingletonUnit
            }

            return UnknownUnit
        }

        override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != this.loc
    }

    private val defaultConfig by lazy {
        JavaDefaultConfigLoader.loadConfig()
    }

    fun runAnalysis(
        config: SerializedTaintConfig,
        entryPointClass: String,
        entryPointMethod: String
    ): List<VulnerabilityWithTrace> = analyze(
        config, entryPointClass, entryPointMethod, bidi = useBidiEntry, limits = bidiLimits,
    ).output

    /** The same real prescan/optional-entry path, with decoded observations of actual joined runs. */
    fun runBidiAcceptance(
        config: SerializedTaintConfig,
        entryPointClass: String,
        entryPointMethod: String,
        limits: List<Int> = bidiLimits,
    ): BidiAcceptanceResult {
        val bridge = J03ObservedDriverBridge()
        val analyzed = analyze(config, entryPointClass, entryPointMethod, bidi = true, limits = limits, bridge = bridge)
        return bridge.result(analyzed.output, checkNotNull(analyzed.report) { "The actual bidi entry did not return a report" })
    }

    private data class Analyzed(val output: List<VulnerabilityWithTrace>, val report: Report?)

    private fun analyze(
        config: SerializedTaintConfig,
        entryPointClass: String,
        entryPointMethod: String,
        bidi: Boolean,
        limits: List<Int>,
        bridge: J03ObservedDriverBridge? = null,
    ): Analyzed {
        val cls = cp.findClassOrNull(entryPointClass) ?: error("Class $entryPointClass not found in CP")
        val ep = cls.declaredMethods.singleOrNull { it.name == entryPointMethod }
            ?: error("No $entryPointMethod method in $entryPointClass")

        val taintConfig = TaintConfiguration(cp)
        taintConfig.loadConfig(config)

        if (useDefaultConfig) {
            val defaultPassRules = SerializedTaintConfig(passThrough = defaultConfig?.passThrough)
            taintConfig.loadConfig(defaultPassRules)
        }

        var rulesProvider: TaintRulesProvider = JIRTaintRulesProvider(taintConfig)
        rulesProvider = JIRMethodExitRuleProvider(rulesProvider)
        if (bidi) rulesProvider = rulesProvider.forBidirectionalCore()

        val usages = runBlocking { cp.usagesExt() }
        val mainGraph = JApplicationGraphImpl(cp, usages)
        val ifdsGraph = JIRSafeApplicationGraph(JApplicationSingleExitGraph(mainGraph))

        val options = TaintAnalyzerOptions(
            ifdsTimeout = 1.minutes,
            ifdsApMode = apMode
        )

        val params = JIRAnalysisManager.Params()
        val units = SingleLocationUnit(cls.declaration.location, admitLambdas = bidi)
        var capturedReport: Report? = null

        val analyzer = object : TaintAnalyzer<JIRMethod, JIRInst>(options) {
            private val legacyManager by lazy { JIRAnalysisManager(cp, refManager, rulesProvider, params = params) }

            override val unrollStrategy: AnyAccessorUnrollStrategy
                get() = analysisUnrollStrategy

            override fun analysisGraph(): ApplicationGraph<JIRMethod, JIRInst> = ifdsGraph
            override fun analysisManager(): JIRAnalysisManager = legacyManager
            override fun unitResolver(): JIRUnitResolver = units

            override fun bidiEntry(): BidiEntry? {
                if (!bidi) return null
                val policy = FixedLimits(limits)
                val actual = if (bridge == null)
                    JIRBidiAnalysis(cp, ifdsGraph, units, rulesProvider, params, policy, refManager)
                else bridge.createEntry(cp, ifdsGraph, units, rulesProvider, params, policy, refManager)
                val owner = this
                return object : BidiEntry by actual {
                    override fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>,
                                        graph: ResolvedPrescanGraph) {
                        owner.ifdsEngine.requirePrescanJoined()
                        assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, owner.ifdsEngine.status.get(),
                            "Acceptance requires a complete real prescan before copying its selected rules")
                        assertTrue(manager === legacyManager, "The entry must gather the manager used by the real prescan")
                        actual.gather(manager, startMethods, graph)
                    }

                    override fun run(budget: Duration): Report {
                        // These are assertions about the actual framework transition, not manual cleanup.
                        owner.ifdsEngine.requirePrescanJoined()
                        assertTrue(owner.ifdsEngine.allUnits().isEmpty(), "Prescan runners must be released before bidi")
                        assertThrows(IllegalStateException::class.java) { owner.ifdsEngine.apManager }
                        assertTrue(legacyManager.prescanRuleIds().isEmpty(), "The prescan manager must release rule-ID storage")
                        assertTrue(legacyManager.prescanLambdas().isEmpty(), "The prescan manager must release lambda trackers")
                        assertEquals(TaintAnalysisManager.Phase.Prescan, legacyManager.phase,
                            "The bidi branch must skip the old full-scan phase")
                        return actual.run(budget).also { capturedReport = it }
                    }
                }
            }
        }

        return analyzer.use {
            val (output, status) = it.analyzeWithIfds(listOf(ep))
            if (bidi) {
                assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, status.analysisStatus,
                    "Bidi analysis did not complete for $entryPointClass.$entryPointMethod")
                assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, status.traceResolutionStatus)
                assertTrue(output.all { vulnerability -> vulnerability.trace === TracePathGenerationResult.Simple },
                    "Phase 3 output must retain its actual simple traces")
                checkNotNull(capturedReport) { "The real bidi entry did not run" }
            }
            Analyzed(output, capturedReport)
        }
    }

    fun assertReachable(
        config: SerializedTaintConfig,
        testCls: String,
        entryPointName: String,
        ruleId: String,
        testName: String,
    ) {
        val traces = runAnalysis(config, testCls, entryPointName)
        assertTrue(traces.isNotEmpty(), "$testName: expected taint to reach the sink, but no vulnerability was found")
        traces.forEach { vt ->
            assertEquals(
                ruleId, vt.vulnerability.rule.id,
                "$testName: unexpected rule id in vulnerability"
            )
        }
    }

    fun assertNotReachable(
        config: SerializedTaintConfig,
        testCls: String,
        entryPointName: String,
        testName: String,
    ) {
        val traces = runAnalysis(config, testCls, entryPointName)
        assertTrue(traces.isEmpty(), "$testName: expected no vulnerability, but found ${traces.size}")
    }
}
