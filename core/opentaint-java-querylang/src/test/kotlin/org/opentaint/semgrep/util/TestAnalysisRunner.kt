package org.opentaint.semgrep.util

import kotlinx.coroutines.runBlocking
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.config.JavaDefaultConfigLoader
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.bidi.driver.IterationPolicy
import org.opentaint.dataflow.bidi.driver.BidiEntry
import org.opentaint.dataflow.bidi.driver.Report
import org.opentaint.dataflow.jvm.bidi.JIRBidiAnalysis
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import org.opentaint.jvm.graph.JMethodBoundaryInstFeature
import org.opentaint.jvm.sast.dataflow.JIRMethodExitRuleProvider
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.rules.JIRSemgrepRuleProvider
import org.opentaint.jvm.transformer.JMultiDimArrayAllocationTransformer
import org.opentaint.jvm.transformer.JStringConcatTransformer
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestAnalysisRunner(
    private val samples: SamplesDb,
    private val bidiPolicy: IterationPolicy? = null,
    private val prescanFailure: Throwable? = null,
    private val useAliasAnalysis: Boolean = true,
    private val observeFlow: Boolean = false,
) : AutoCloseable {
    private lateinit var cp: JIRClasspath
    private val failNextPrescan = AtomicBoolean(prescanFailure != null)
    var lastPrescanStatus: TaintAnalysisUnitRunnerManager.Status? = null
        private set
    var lastAnalysisStatus: TaintAnalyzer.Status? = null
        private set
    private var lastReport: Report? = null

    init {
        initializeCp()
    }

    private fun initializeCp() = runBlocking {
        val lambdaAnonymousClass = LambdaAnonymousClassFeature()
        val lambdaTransformer = LambdaExpressionToAnonymousClassTransformerFeature(lambdaAnonymousClass)

        val features = mutableListOf(
            UnknownClasses, lambdaAnonymousClass, lambdaTransformer,
            JStringConcatTransformer, JMultiDimArrayAllocationTransformer,
            JMethodBoundaryInstFeature,
        )

        val allCpFiles = listOf(samples.samplesJar.toFile())
        cp = samples.db.classpath(allCpFiles, features)
    }

    override fun close() {
        cp.close()
    }

    private val ifdsAnalysisGraph by lazy {
        val usages = runBlocking { cp.usagesExt() }
        val mainGraph = JApplicationGraphImpl(cp, usages)
        JIRSafeApplicationGraph(JApplicationSingleExitGraph(mainGraph))
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupEngine(configProvider: TaintRulesProvider): TaintAnalyzer<JIRMethod, JIRInst> {
        val params = JIRAnalysisManager.Params(
            aliasAnalysisParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = useAliasAnalysis))
        val options = TaintAnalyzerOptions(
            ifdsTimeout = 1.minutes,
            ifdsApMode = ApMode.Tree
        )

        val analyzer = object : TaintAnalyzer<JIRMethod, JIRInst>(options) {
            override val unrollStrategy: AnyAccessorUnrollStrategy
                get() = AnyAccessorUnrollStrategy.AnyAccessorDisabled

            override fun analysisGraph() = this@TestAnalysisRunner.ifdsAnalysisGraph
            override fun analysisManager() = JIRAnalysisManager(cp, refManager, configProvider, params = params)
            override fun bidiEntry(): BidiEntry? = bidiPolicy?.let { policy ->
                lastPrescanStatus = ifdsEngine.status.get()
                val actual = if (observeFlow)
                    BidiFlowDiagnostics().createEntry(cp, this@TestAnalysisRunner.ifdsAnalysisGraph, unitResolver(),
                        configProvider, params, policy, refManager)
                else JIRBidiAnalysis(cp, this@TestAnalysisRunner.ifdsAnalysisGraph, unitResolver(),
                    configProvider, params, policy, refManager)
                object : BidiEntry by actual {
                    override fun run(budget: Duration): Report = actual.run(budget).also { lastReport = it }
                }
            }
            override fun unitResolver() = object :JIRUnitResolver {
                override fun locationIsUnknown(loc: RegisteredLocation): Boolean =
                    loc.isRuntime

                override fun resolve(method: JIRMethod): UnitType {
                    if (failNextPrescan.compareAndSet(true, false)) throw checkNotNull(prescanFailure)
                    return if (method.enclosingClass.declaration.location.isRuntime) UnknownUnit else SingletonUnit
                }

            }
        }

        return analyzer
    }

    fun run(
        rule: TaintRuleFromSemgrep<SerializedItem>,
        config: SerializedTaintConfig,
        useDefaultConfig: Boolean,
        samples: Set<String>
    ): Map<String, List<VulnerabilityWithTrace>> =
        samples.associate { sample ->
            val cls = cp.findClassOrNull(sample) ?: error("No sample in CP")
            val ep = cls.declaredMethods.singleOrNull { it.name == "entrypoint" }
                ?: error("No entrypoint in $sample")

            val rulesProvider = rulesProvider(rule, config, useDefaultConfig)
            lastReport = null
            setupEngine(rulesProvider).use { engine ->
                val (traces, status) = engine.analyzeWithIfds(listOf(ep))
                lastAnalysisStatus = status
                if (bidiPolicy != null) {
                    println("BIDI_ANALYSIS sample=$sample prescan=$lastPrescanStatus " +
                        "analysis=${status.analysisStatus} findings=${traces.size}")
                    lastReport?.entries?.forEach { finding ->
                        println("BIDI_FINDING sample=$sample rule=${finding.key.rule.id} state=${finding.state} " +
                            "method=${finding.key.method} witnesses=${finding.witnesses.map { it.methodKey }} " +
                            "serialized=${(finding.key.rule as? TaintConfigurationItem)?.serializedId} " +
                            "endFacts=${finding.witnesses.flatMap { it.endFacts }.distinct()} " +
                            "statement=${finding.key.statement}")
                    }
                    assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, status.analysisStatus, sample)
                    assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, status.traceResolutionStatus, sample)
                    assertTrue(traces.all { it.trace === TracePathGenerationResult.Simple }, sample)
                    assertTrue(engine.ifdsEngine.allUnits().isEmpty(), sample)
                    assertFailsWith<IllegalStateException> { engine.ifdsEngine.apManager }
                }
                sample to traces
            }
        }

    private val defaultConfig by lazy {
        JavaDefaultConfigLoader.loadConfig()
            ?: error("Error while loading default config")
    }

    private fun rulesProvider(
        rule: TaintRuleFromSemgrep<SerializedItem>,
        config: SerializedTaintConfig,
        useDefaultConfig: Boolean
    ): TaintRulesProvider {
        val taintConfig = TaintConfiguration(cp)
        taintConfig.loadConfig(config)

        if (useDefaultConfig) {
            val defaultPassRules = SerializedTaintConfig(passThrough = defaultConfig.passThrough)
            taintConfig.loadConfig(defaultPassRules)
        }

        var cfg: TaintRulesProvider = JIRSemgrepRuleProvider(listOf(rule), taintConfig)
        cfg = JIRMethodExitRuleProvider(cfg)
        return cfg
    }
}
