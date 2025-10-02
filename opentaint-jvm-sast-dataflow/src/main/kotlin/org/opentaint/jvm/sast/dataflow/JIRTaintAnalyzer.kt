package org.opentaint.api.checkers

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.ConstantTrue
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.ir.taint.configuration.v2.TaintConfiguration
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.TaintRuleFilter
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider
import org.opentaint.dataflow.ap.ifds.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.sarif.SarifGenerator
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.graph.ApplicationGraph
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.graph.JIRApplicationGraphImpl
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.ifds.PackageUnit
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.sarif.SourceFileResolver
import java.io.OutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class JIRTaintAnalyzer(
    val cp: JIRClasspath,
    val taintConfiguration: TaintConfiguration,
    val projectLocations: Set<RegisteredLocation>,
    val dependenciesLocations: Set<RegisteredLocation>,
    val ifdsTimeout: Duration,
    val ifdsApMode: ApMode,
    val opentaintTimeout: Duration,
    val symbolicExecutionEnabled: Boolean,
    val analysisCwe: Set<Int>?,
    val summarySerializationContext: SummarySerializationContext,
    val storeSummaries: Boolean,
    val analysisUnit: JIRUnitResolver = PackageUnitResolver(bannedLocations = dependenciesLocations),
) {
    private val ifdsAnalysisGraph by lazy {
        val usages = runBlocking { cp.usagesExt() }
        val mainGraph = JIRApplicationGraphImpl(cp, usages)
        JIRSafeApplicationGraph(mainGraph)
    }

    private lateinit var ifdsTraces: List<VulnerabilityWithTrace>
    private lateinit var verifiedIfdsTraces: List<VulnerabilityWithTrace>

    fun analyzeWithIfds(entryPoints: List<JIRMethod>) {
        ifdsTraces = analyzeTaintWithIfdsEngine(entryPoints)
    }

    fun filterIfdsTracesWithOpentaint(entryPoints: List<JIRMethod>) {
        check(this::ifdsTraces.isInitialized) { "No ifds traces" }
        entryPoints.let { }

        TODO("Symbolic execution is not implemented yet")
    }

    fun generateSarifReportFromIfdsTraces(output: OutputStream, sourceFileResolver: SourceFileResolver<CommonInst>) =
        generateSarifReportFromTraces(output, sourceFileResolver, ifdsTraces)

    fun generateSarifReportFromVerifiedIfdsTraces(output: OutputStream, sourceFileResolver: SourceFileResolver<CommonInst>) =
        generateSarifReportFromTraces(output, sourceFileResolver, verifiedIfdsTraces)

    private fun generateSarifReportFromTraces(
        output: OutputStream,
        sourceFileResolver: SourceFileResolver<CommonInst>,
        traces: List<VulnerabilityWithTrace>
    ) {
        val generator = SarifGenerator(sourceFileResolver, JIRTraits(cp))
        generator.generateSarif(output, traces.asSequence())
        logger.info { "Sarif trace generation stats: ${generator.traceGenerationStats}" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createIfdsEngine() = TaintAnalysisUnitRunnerManager(
        JIRLanguageManager(cp),
        ifdsAnalysisGraph as ApplicationGraph<CommonMethod, CommonInst>,
        analysisUnit as UnitResolver<CommonMethod>,
        taintConfig,
        summarySerializationContext,
        ifdsApMode
    )

    private fun analyzeTaintWithIfdsEngine(
        entryPoints: List<JIRMethod>,
    ): List<VulnerabilityWithTrace> = createIfdsEngine().use { ifdsEngine ->
        val analysisStart = TimeSource.Monotonic.markNow()

        val analysisTimeout = ifdsTimeout * 0.95 // Reserve 5% of time for report creation
        runCatching { ifdsEngine.runAnalysis(entryPoints, timeout = analysisTimeout, cancellationTimeout = 30.seconds) }
            .onFailure { logger.error(it) { "Ifds engine failed" } }

        if (storeSummaries) {
            logger.info { "Saving summaries" }
            ifdsEngine.storeSummaries()
        }

        var vulnerabilities = ifdsEngine.getVulnerabilities()
        logger.info { "Total vulnerabilities: ${vulnerabilities.size}" }

        if (analysisCwe != null) {
            vulnerabilities = vulnerabilities.filter {
                it.rule.cwe.intersect(analysisCwe).isNotEmpty()
            }

            logger.info { "Vulnerabilities with cwe $analysisCwe: ${vulnerabilities.size}" }
        }

        logger.info { "Start trace generation" }
        val traceResolutionTimeout = ifdsTimeout - analysisStart.elapsedNow()
        if (!traceResolutionTimeout.isPositive()) {
            logger.warn { "No time remaining for trace resolution" }
            return@use emptyList()
        }

        ifdsEngine.generateTraces(entryPoints, vulnerabilities, traceResolutionTimeout).also {
            logger.info { "Finish trace generation" }
        }
    }

    private fun TaintAnalysisUnitRunnerManager.generateTraces(
        entryPoints: List<JIRMethod>,
        vulnerabilities: List<TaintSinkTracker.TaintVulnerability>,
        timeout: Duration,
    ): List<VulnerabilityWithTrace> {
        val entryPointsSet = entryPoints.toHashSet()
        return resolveVulnerabilityTraces(
            entryPointsSet, vulnerabilities,
            resolverParams = TraceResolver.Params(
                resolveEntryPointToStartTrace = symbolicExecutionEnabled,
                startToSourceTraceResolutionLimit = 100,
                startToSinkTraceResolutionLimit = 100,
            ),
            timeout = timeout,
            cancellationTimeout = 30.seconds
        )
    }

    private val taintRuleFilter: TaintRuleFilter? = TaintRuleFilter {
        rule ->
            when {
                // todo: remove
                rule is TaintPassThrough -> (rule.method as JIRMethod).enclosingClass.declaration.location !in projectLocations
                rule is TaintMethodSink -> analysisCwe == null || rule.cwe.any { it in analysisCwe }
                else -> true
            }
    }

    private val taintConfig: TaintRulesProvider by lazy {
        val provider = object : TaintRulesProvider {
            override fun entryPointRulesForMethod(method: CommonMethod) = getRules(method) {
                taintConfiguration.entryPointForMethod(it)
            }

            override fun sourceRulesForMethod(method: CommonMethod) = getRules(method) {
                taintConfiguration.sourceForMethod(it)
            }

            override fun sinkRulesForMethod(method: CommonMethod) = getRules(method) {
                taintConfiguration.sinkForMethod(it)
            }

            override fun passTroughRulesForMethod(method: CommonMethod) = getRules(method) {
                taintConfiguration.passThroughForMethod(it)
            }

            override fun cleanerRulesForMethod(method: CommonMethod) = getRules(method) {
                taintConfiguration.cleanerForMethod(it)
            }

            private inline fun <T : TaintConfigurationItem> getRules(
                method: CommonMethod,
                body: (JIRMethod) -> Iterable<T>
            ): Iterable<T> {
                check(method is JIRMethod) { "Expected method to be JIRMethod" }
                val rules = body(method)
                if (taintRuleFilter == null) return rules
                return rules.filter { taintRuleFilter.ruleEnabled(it) }
            }
        }

        StringConcatRuleProvider(provider)
    }

    private class StringConcatRuleProvider(private val base: TaintRulesProvider) : TaintRulesProvider by base {
        private var stringConcatPassThrough: TaintPassThrough? = null

        private fun stringConcatPassThrough(method: JIRMethod): TaintPassThrough =
            stringConcatPassThrough ?: generateRule(method).also { stringConcatPassThrough = it }

        private fun generateRule(method: JIRMethod): TaintPassThrough {
            // todo: string concat hack
            val possibleArgs = (0..20).map { Argument(it) }

            return TaintPassThrough(
                method = method,
                condition = ConstantTrue,
                actionsAfter = possibleArgs.map { CopyAllMarks(from = it, to = Result) })
        }

        override fun passTroughRulesForMethod(method: CommonMethod): Iterable<TaintPassThrough> {
            check(method is JIRMethod) { "Expected method to be JIRMethod" }
            val baseRules = base.passTroughRulesForMethod(method)

            if (method.name == "makeConcatWithConstants" && method.enclosingClass.name == "java.lang.invoke.StringConcatFactory") {
                return (sequenceOf(stringConcatPassThrough(method)) + baseRules).asIterable()
            }

            return baseRules
        }
    }

    companion object {
        val logger = object : KLogging() {}.logger

        class PackageUnitResolver(private val bannedLocations: Set<RegisteredLocation>) : JIRUnitResolver {
            override fun resolve(method: JIRMethod): UnitType {
                if (method.enclosingClass.declaration.location in bannedLocations) {
                    return UnknownUnit
                }

                return PackageUnit(method.enclosingClass.packageName)
            }
        }
    }
}