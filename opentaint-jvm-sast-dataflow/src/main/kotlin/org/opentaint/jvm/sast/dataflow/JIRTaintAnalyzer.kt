package org.opentaint.api.checkers

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRSingleEntryPointApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.jvm.ap.ifds.TaintAnalysisUnitRunnerManager.VulnerabilityWithTrace
import org.opentaint.dataflow.jvm.ap.ifds.TaintSinkTracker
import org.opentaint.dataflow.jvm.ap.ifds.access.ApMode
import org.opentaint.dataflow.jvm.ap.ifds.sarif.SarifGenerator
import org.opentaint.dataflow.jvm.graph.JIRApplicationGraphImpl
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.ifds.PackageUnit
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
        JIRSingleEntryPointApplicationGraph(mainGraph)
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

    fun generateSarifReportFromIfdsTraces(output: OutputStream, sourceFileResolver: JIRSourceFileResolver) =
        generateSarifReportFromTraces(output, sourceFileResolver, ifdsTraces)

    fun generateSarifReportFromVerifiedIfdsTraces(output: OutputStream, sourceFileResolver: JIRSourceFileResolver) =
        generateSarifReportFromTraces(output, sourceFileResolver, verifiedIfdsTraces)

    private fun generateSarifReportFromTraces(
        output: OutputStream,
        sourceFileResolver: JIRSourceFileResolver,
        traces: List<VulnerabilityWithTrace>
    ) {
        val generator = SarifGenerator(cp, sourceFileResolver)
        generator.generateSarif(output, traces.asSequence().map { it.vulnerability to it.trace })
        logger.info { "Sarif trace generation stats: ${generator.traceGenerationStats}" }
    }

    private fun createIfdsEngine() = TaintAnalysisUnitRunnerManager(ifdsAnalysisGraph, analysisUnit, ifdsApMode) { rule ->
        when {
            // todo: remove
            rule is TaintPassThrough -> (rule.method as JIRMethod).enclosingClass.declaration.location !in projectLocations
            rule is TaintMethodSink -> analysisCwe == null || rule.cwe.any { it in analysisCwe }
            else -> true
        }
    }

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
            resolveEntryPointToStartTrace = symbolicExecutionEnabled,
            timeout = timeout,
            cancellationTimeout = 30.seconds
        )
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
