package org.opentaint.semgrep.util

import kotlinx.coroutines.runBlocking
import org.opentaint.config.ConfigLoader
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.graph.MethodReturnInstNormalizerFeature
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.sast.dataflow.DummySerializationContext
import org.opentaint.jvm.sast.dataflow.JIRMethodExitRuleProvider
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.transformer.JMultiDimArrayAllocationTransformer
import org.opentaint.jvm.transformer.JStringConcatTransformer
import org.opentaint.util.analysis.ApplicationGraph
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TestAnalysisRunner(
    private val samples: SamplesDb,
) : AutoCloseable {
    private lateinit var cp: JIRClasspath

    init {
        initializeCp()
    }

    private fun initializeCp() = runBlocking {
        val lambdaAnonymousClass = LambdaAnonymousClassFeature()
        val lambdaTransformer = LambdaExpressionToAnonymousClassTransformerFeature(lambdaAnonymousClass)
        val methodNormalizer = MethodReturnInstNormalizerFeature

        val features = mutableListOf(
            UnknownClasses, lambdaAnonymousClass, lambdaTransformer, methodNormalizer,
            JStringConcatTransformer, JMultiDimArrayAllocationTransformer
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
        JIRSafeApplicationGraph(mainGraph)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupEngine(configProvider: TaintRulesProvider): TaintAnalysisUnitRunnerManager {
        return TaintAnalysisUnitRunnerManager(
            JIRAnalysisManager(cp, configProvider),
            ifdsAnalysisGraph as ApplicationGraph<CommonMethod, CommonInst>,
            unitResolver = object :JIRUnitResolver {
                override fun locationIsUnknown(loc: RegisteredLocation): Boolean =
                    loc.isRuntime

                override fun resolve(method: JIRMethod): UnitType =
                    if (method.enclosingClass.declaration.location.isRuntime) UnknownUnit else SingletonUnit

            } as UnitResolver<CommonMethod>,
            apManager = TreeApManager(anyAccessorUnrollStrategy = AnyAccessorUnrollStrategy.AnyAccessorDisabled),
            summarySerializationContext = DummySerializationContext,
            taintRulesStatsSamplingPeriod = null,
        )
    }

    fun run(
        config: SerializedTaintConfig,
        useDefaultConfig: Boolean,
        samples: Set<String>
    ): Map<String, List<VulnerabilityWithTrace>> =
        samples.associate { sample ->
            val cls = cp.findClassOrNull(sample) ?: error("No sample in CP")
            val ep = cls.declaredMethods.singleOrNull { it.name == "entrypoint" }
                ?: error("No entrypoint in $sample")
            val startMethod = MethodWithContext(ep, EmptyMethodContext)

            val rulesProvider = rulesProvider(config, useDefaultConfig, hashSetOf(ep))
            setupEngine(rulesProvider).use { engine ->
                engine.runAnalysis(listOf(startMethod), timeout = 1.minutes, cancellationTimeout = 10.seconds)

                val allVulnerabilities = engine.getVulnerabilities()
                val vulnerabilities = engine.confirmVulnerabilities(
                    setOf(ep), allVulnerabilities,
                    timeout = 1.minutes, cancellationTimeout = 10.seconds
                )
                val traces = engine.resolveVulnerabilityTraces(
                    setOf(ep), vulnerabilities,
                    resolverParams = TraceResolver.Params(),
                    timeout = 1.minutes, cancellationTimeout = 10.seconds
                ).mapNotNull { trace ->
                    trace.takeIf { it.trace?.sourceToSinkTrace?.startNodes?.isNotEmpty() ?: false }
                }
                
                sample to traces
            }
        }

    private fun rulesProvider(
        config: SerializedTaintConfig,
        useDefaultConfig: Boolean,
        ep: Set<JIRMethod>
    ): TaintRulesProvider {
        val taintConfig = TaintConfiguration(cp)
        taintConfig.loadConfig(config)

        if (useDefaultConfig) {
            val defaultConfig = ConfigLoader.getConfig() ?: error("Error while loading default config")

            val defaultPassRules = SerializedTaintConfig(passThrough = defaultConfig.passThrough)
            taintConfig.loadConfig(defaultPassRules)
        }

        var cfg: TaintRulesProvider = JIRTaintRulesProvider(taintConfig)
        cfg = JIRMethodExitRuleProvider(cfg)
        return cfg
    }
}
