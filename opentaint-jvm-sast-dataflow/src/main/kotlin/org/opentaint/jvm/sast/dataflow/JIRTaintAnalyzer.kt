package org.opentaint.jvm.sast.dataflow

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintSinkMeta
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.ifds.PackageUnit
import org.opentaint.dataflow.util.percentToString
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.util.analysis.ApplicationGraph
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class JIRTaintAnalyzer(
    val cp: JIRClasspath,
    val taintConfiguration: TaintRulesProvider,
    val projectLocations: Set<RegisteredLocation>,
    val ifdsTimeout: Duration,
    val ifdsApMode: ApMode,
    val symbolicExecutionEnabled: Boolean,
    val analysisCwe: Set<Int>?,
    val summarySerializationContext: SummarySerializationContext,
    val storeSummaries: Boolean,
    val analysisUnit: JIRUnitResolver = PackageUnitResolver(projectLocations = projectLocations),
    val debugOptions: DebugOptions
): AutoCloseable {
    data class DebugOptions(
        val taintRulesStatsSamplingPeriod: Int?,
        val enableIfdsCoverage: Boolean,
        val factReachabilitySarif: Boolean,
        val enableVulnSummary: Boolean,
    )

    private val ifdsAnalysisGraph by lazy {
        val usages = runBlocking { cp.usagesExt() }
        val mainGraph = JApplicationGraphImpl(cp, usages)
        JIRSafeApplicationGraph(mainGraph)
    }

    val ifdsEngine by lazy { createIfdsEngine() }

    fun analyzeWithIfds(entryPoints: List<JIRMethod>): List<VulnerabilityWithTrace> {
        return analyzeTaintWithIfdsEngine(entryPoints)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createIfdsEngine() = TaintAnalysisUnitRunnerManager(
        JIRAnalysisManager(cp),
        ifdsAnalysisGraph as ApplicationGraph<CommonMethod, CommonInst>,
        analysisUnit as UnitResolver<CommonMethod>,
        taintConfig,
        summarySerializationContext,
        ifdsApMode,
        debugOptions.taintRulesStatsSamplingPeriod
    )

    private fun analyzeTaintWithIfdsEngine(
        entryPoints: List<JIRMethod>,
    ): List<VulnerabilityWithTrace> {
        val analysisStart = TimeSource.Monotonic.markNow()

        val analysisTimeout = ifdsTimeout * 0.95 // Reserve 5% of time for report creation
        runCatching { ifdsEngine.runAnalysis(entryPoints, timeout = analysisTimeout, cancellationTimeout = 30.seconds) }
            .onFailure { logger.error(it) { "Ifds engine failed" } }

        if (debugOptions.enableIfdsCoverage) {
            logger.debug {
                ifdsEngine.reportCoverage()
            }
        }

        if (storeSummaries) {
            logger.info { "Storing summaries" }
            ifdsEngine.storeSummaries()
        }

        val allVulnerabilities = ifdsEngine.getVulnerabilities()

        logger.info { "Start vulnerability confirmation" }
        val vulnCheckTimeout = ifdsTimeout - analysisStart.elapsedNow()
        var vulnerabilities = if (!vulnCheckTimeout.isPositive()) {
            logger.warn { "No time remaining for vulnerability confirmation" }
            allVulnerabilities
        } else {
            ifdsEngine.confirmVulnerabilities(
                entryPoints.toHashSet(), allVulnerabilities,
                vulnCheckTimeout, cancellationTimeout = 30.seconds
            )
        }

        logger.info { "Total vulnerabilities: ${vulnerabilities.size}" }

        if (debugOptions.enableVulnSummary) {
            logger.info {
                printVulnSummary(vulnerabilities)
            }
        }

        if (analysisCwe != null) {
            vulnerabilities = vulnerabilities.filter {
                val cwe = (it.rule.meta as TaintSinkMeta).cwe
                cwe?.intersect(analysisCwe)?.isNotEmpty() ?: true
            }

            logger.info { "Vulnerabilities with cwe $analysisCwe: ${vulnerabilities.size}" }
        }

        logger.info { "Start trace generation" }
        val traceResolutionTimeout = ifdsTimeout - analysisStart.elapsedNow()
        if (!traceResolutionTimeout.isPositive()) {
            logger.warn { "No time remaining for trace resolution" }
            return emptyList()
        }

        return ifdsEngine.generateTraces(entryPoints, vulnerabilities, traceResolutionTimeout).also {
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

    private val taintConfig: TaintRulesProvider by lazy {
        StringConcatRuleProvider(taintConfiguration)
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
                actionsAfter = possibleArgs.map { CopyAllMarks(from = it, to = Result) },
                info = null
            )
        }

        override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintPassThrough> {
            check(method is JIRMethod) { "Expected method to be JIRMethod" }
            val baseRules = base.passTroughRulesForMethod(method, statement)

            if (method.name == "makeConcatWithConstants" && method.enclosingClass.name == "java.lang.invoke.StringConcatFactory") {
                return (sequenceOf(stringConcatPassThrough(method)) + baseRules).asIterable()
            }

            return baseRules
        }
    }

    private fun TaintAnalysisUnitRunnerManager.reportCoverage() = buildString {
        val methodStats = collectMethodStats()
        val projectClassCoverage = methodStats.stats.entries
            .groupBy({ (it.key as JIRMethod).enclosingClass }, { it.key as JIRMethod to it.value })
            .filterKeys { it !is LambdaAnonymousClassFeature.JIRLambdaClass }
            .filterKeys { it.declaration.location in projectLocations }

        appendLine("Project class coverage")
        projectClassCoverage.entries
            .sortedBy { it.key.name }
            .forEach { (cls, methods) ->
                appendLine(cls.name)
                for ((method, cov) in methods.sortedBy { it.toString() }) {
                    val covPc = percentToString(cov.coveredInstructions.cardinality(), method.instList.size)
                    appendLine("$method | $covPc")
                }

                val missedMethods = cls.declaredMethods - methods.mapTo(hashSetOf()) { it.first }
                for (method in missedMethods.sortedBy { it.toString() }) {
                    appendLine("$method | MISSED")
                }

                appendLine("-".repeat(20))
            }
    }

    fun statementsWithFacts(): Map<CommonInst, Set<FinalFactAp>> {
        val statementFacts = hashMapOf<CommonInst, MutableSet<FinalFactAp>>()
        ifdsEngine.allUnits().forEach { unit ->
            val unitRunner = ifdsEngine.findUnitRunner(unit) ?: return@forEach

            val runnerFacts = hashMapOf<MethodEntryPoint, Map<CommonInst, Set<FinalFactAp>>>()
            unitRunner.collectAllIntraProceduralFacts(runnerFacts)
            runnerFacts.values.forEach { stmtFacts ->
                stmtFacts.forEach { (stmt, facts) ->
                    statementFacts.getOrPut(stmt, ::hashSetOf).addAll(facts)
                }
            }
        }
        return statementFacts
    }

    override fun close() {
        ifdsEngine.close()
    }

    private fun printVulnSummary(
        vulnerabilities: List<TaintSinkTracker.TaintVulnerability>
    ): String = buildString {
        data class VulnInfo(val location: String, val ruleId: String, val kind: String)

        fun TaintSinkTracker.TaintVulnerability.vulnSummary(): VulnInfo = when (this) {
            is TaintSinkTracker.TaintVulnerabilityWithEndFactRequirement -> {
                vulnerability.vulnSummary().let { it.copy(kind = "end#${it.kind}") }
            }

            is TaintSinkTracker.TaintVulnerabilityUnconditional -> {
                VulnInfo("${statement.location}|${statement}", rule.id, "unconditional")
            }

            is TaintSinkTracker.TaintVulnerabilityWithFact -> {
                VulnInfo("${statement.location}|${statement}", rule.id, "fact")
            }
        }

        val info = vulnerabilities.mapTo(mutableListOf()) { it.vulnSummary() }
        info.sortWith(compareBy<VulnInfo> { it.kind }.thenBy { it.ruleId }.thenBy { it.location })

        appendLine("VULNERABILITIES:")
        appendLine("#".repeat(50))
        for ((kind, sameKindVuln) in info.groupBy { it.kind }) {
            appendLine(kind)
            appendLine("-".repeat(50))
            for ((rule, sameRuleVuln) in sameKindVuln.groupBy { it.ruleId }) {
                appendLine(rule)
                for (vuln in sameRuleVuln) {
                    appendLine("\t\t${vuln.location}")
                }
            }
        }
        appendLine("#".repeat(50))
    }

    companion object {
        private val logger = object : KLogging() {}.logger

        class PackageUnitResolver(private val projectLocations: Set<RegisteredLocation>) : JIRUnitResolver {
            override fun resolve(method: JIRMethod): UnitType {
                if (method.enclosingClass.declaration.location !in projectLocations) {
                    return UnknownUnit
                }

                return PackageUnit(method.enclosingClass.packageName)
            }
        }
    }
}