package org.opentaint.api.checkers

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.ConstantTrue
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.TaintRuleFilter
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider
import org.opentaint.dataflow.ap.ifds.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.sarif.SarifGenerator
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
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
    val projectLocations: Set<RegisteredLocation>,
    val dependenciesLocations: Set<RegisteredLocation>,
    val ifdsTimeout: Duration,
    val ifdsApMode: ApMode,
    val opentaintTimeout: Duration,
    val symbolicExecutionEnabled: Boolean,
    val analysisCwe: Set<Int>?,
    val analysisUnit: JIRUnitResolver = PackageUnitResolver(bannedLocations = dependenciesLocations)
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
        ifdsApMode,
        taintConfig
    )

    private fun analyzeTaintWithIfdsEngine(
        entryPoints: List<JIRMethod>,
    ): List<VulnerabilityWithTrace> = createIfdsEngine().use { ifdsEngine ->
        val analysisStart = TimeSource.Monotonic.markNow()

        val analysisTimeout = ifdsTimeout * 0.95 // Reserve 5% of time for report creation
        runCatching { ifdsEngine.runAnalysis(entryPoints, timeout = analysisTimeout, cancellationTimeout = 30.seconds) }
            .onFailure { logger.error(it) { "Ifds engine failed" } }

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

    private val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    private val taintConfig: TaintRulesProvider by lazy {
        val provider = object : TaintRulesProvider {
            override fun taintMarks(): Set<TaintMark> =
                taintConfigurationFeature?.getAllTaintMarks() ?: emptySet()

            override fun rulesForMethod(method: CommonMethod): Iterable<TaintConfigurationItem> {
                check(method is JIRMethod) { "Expected method to be JIRMethod" }
                val config = taintConfigurationFeature ?: return emptyList()
                val rules = config.getConfigForMethod(method)

                if (taintRuleFilter == null) return rules
                return rules.filter { taintRuleFilter.ruleEnabled(it) }
            }
        }

        StringConcatRuleProvider(NoNullnessAnalysisRuleProvider(provider))
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

        override fun rulesForMethod(method: CommonMethod): Iterable<TaintConfigurationItem> {
            check(method is JIRMethod) { "Expected method to be JIRMethod" }
            val baseRules = base.rulesForMethod(method)

            if (method.name == "makeConcatWithConstants" && method.enclosingClass.name == "java.lang.invoke.StringConcatFactory") {
                return (sequenceOf(stringConcatPassThrough(method)) + baseRules).asIterable()
            }

            return baseRules
        }
    }

    private class NoNullnessAnalysisRuleProvider(private val base: TaintRulesProvider) : TaintRulesProvider by base {
        override fun rulesForMethod(method: CommonMethod): Iterable<TaintConfigurationItem> {
            val baseRules = base.rulesForMethod(method)
            return baseRules.mapNotNull { removeNullnessSources(it) }
        }

        private fun removeNullnessSources(rule: TaintConfigurationItem): TaintConfigurationItem? {
            if (rule !is TaintMethodSource) return rule

            val actions = rule.actionsAfter.filterNot { it is AssignMark && it.mark == TaintMark.NULLNESS }
            if (actions.isEmpty()) return null
            return rule.copy(actionsAfter = actions)
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