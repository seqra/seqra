package org.opentaint.semgrep.util

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.api.checkers.DummySerializationContext
import org.opentaint.api.checkers.JIRTaintRulesProvider
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.TaintConfiguration
import org.opentaint.dataflow.graph.ApplicationGraph
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.graph.JIRApplicationGraphImpl
import org.opentaint.dataflow.jvm.graph.MethodReturnInstNormalizerFeature
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.machine.interpreter.transformers.JIRMultiDimArrayAllocationTransformer
import org.opentaint.machine.interpreter.transformers.JIRStringConcatTransformer
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
            JIRStringConcatTransformer, JIRMultiDimArrayAllocationTransformer
        )

        val allCpFiles = listOf(samples.samplesJar.toFile())
        cp = samples.db.classpath(allCpFiles, features)
    }

    override fun close() {
        cp.close()
    }

    private val ifdsAnalysisGraph by lazy {
        val usages = runBlocking { cp.usagesExt() }
        val mainGraph = JIRApplicationGraphImpl(cp, usages)
        JIRSafeApplicationGraph(mainGraph)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupEngine(configProvider: TaintRulesProvider): TaintAnalysisUnitRunnerManager {
        return TaintAnalysisUnitRunnerManager(
            JIRAnalysisManager(cp),
            ifdsAnalysisGraph as ApplicationGraph<CommonMethod, CommonInst>,
            unitResolver = JIRUnitResolver {
                if (it.enclosingClass.declaration.location.isRuntime) UnknownUnit else SingletonUnit
            } as UnitResolver<CommonMethod>,
            apMode = ApMode.Tree,
            summarySerializationContext = DummySerializationContext,
            taintConfig = configProvider,
            taintRulesStatsSamplingPeriod = null,
        )
    }

    fun run(
        config: SerializedTaintConfig,
        samples: Set<String>
    ): Map<String, List<TaintSinkTracker.TaintVulnerability>> =
        samples.associate { sample ->
            val cls = cp.findClassOrNull(sample) ?: error("No sample in CP")
            val ep = cls.declaredMethods.singleOrNull { it.name == "entrypoint" }
                ?: error("No entrypoint in $sample")

            val rulesProvider = rulesProvider(config, hashSetOf(ep))
            setupEngine(rulesProvider).use { engine ->
                engine.runAnalysis(listOf(ep), timeout = 1.minutes, cancellationTimeout = 10.seconds)
                sample to engine.getVulnerabilities()
            }
        }

    private fun rulesProvider(config: SerializedTaintConfig, ep: Set<CommonMethod>): TaintRulesProvider {
        val taintConfig = TaintConfiguration()
        taintConfig.loadConfig(config)
        val configProvider = JIRTaintRulesProvider(taintConfig)
        return ConfigWithEpMethodExits(ep, configProvider)
    }

    private class ConfigWithEpMethodExits(
        private val ep: Set<CommonMethod>,
        private val base: TaintRulesProvider
    ) : TaintRulesProvider by base {
        override fun sinkRulesForMethodExit(
            method: CommonMethod,
            statement: CommonInst
        ): Iterable<TaintMethodExitSink> {
            if (method !in ep) return emptyList()

            return base.sinkRulesForMethodExit(method, statement)
        }
    }
}