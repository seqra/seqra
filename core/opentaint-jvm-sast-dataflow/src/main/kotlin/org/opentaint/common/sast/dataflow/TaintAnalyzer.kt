package org.opentaint.common.sast.dataflow

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodStats
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy.AnyAccessorDisabled
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.automata.AutomataApManager
import org.opentaint.dataflow.ap.ifds.access.cactus.CactusApManager
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.InnerCallTraceResolveStrategy
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntryAction.TraceSummaryEdge
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathResolveParams
import org.opentaint.dataflow.configuration.jvm.TaintSinkMeta
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.dataflow.util.percentToString
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.analysis.ApplicationGraph
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

abstract class TaintAnalyzer<Method: CommonMethod, Statement: CommonInst>(
    val options: TaintAnalyzerOptions,
    val externalMethodTracker: ExternalMethodTracker? = null,
): AutoCloseable {
    data class Status(
        val analysisStatus: TaintAnalysisUnitRunnerManager.Status,
        val traceResolutionStatus: TaintAnalysisUnitRunnerManager.Status,
    )

    abstract fun analysisGraph(): ApplicationGraph<Method, Statement>

    private val ifdsAnalysisGraph by lazy {
        analysisGraph()
    }

    val ifdsEngine by lazy { createIfdsEngine() }

    fun analyzeWithIfds(entryPoints: List<Method>): Pair<List<VulnerabilityWithTrace>, Status> {
        return analyzeStaged(entryPoints)
    }

    open val unrollStrategy: AnyAccessorUnrollStrategy = object : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean = when (accessor) {
            is ElementAccessor -> true
            is FieldAccessor -> true
            is ClassStaticAccessor,
            is AnyAccessor,
            is FinalAccessor,
            is TaintMarkAccessor,
            is TypeInfoAccessor,
            is TypeInfoGroupAccessor -> false

            is ValueAccessor -> error("Unexpected accessor to unroll: $accessor")
        }
    }

    val refManager = RefManager()
    val cancellation = Cancellation()

    private val apManager by lazy {
        when (options.ifdsApMode) {
            ApMode.Tree -> TreeApManager(unrollStrategy, refManager, cancellation)
            ApMode.Cactus -> CactusApManager(unrollStrategy, cancellation)
            ApMode.Automata -> AutomataApManager(unrollStrategy, cancellation)
        }
    }

    open fun summarySerializationContext(): SummarySerializationContext = DummySerializationContext

    private val summarySerializationContext by lazy {
        if (options.storeSummaries) summarySerializationContext() else DummySerializationContext
    }

    abstract fun analysisManager(): TaintAnalysisManager

    abstract fun unitResolver(): UnitResolver<Method>

    private val analysisManager by lazy { analysisManager() }

    @Suppress("UNCHECKED_CAST")
    private fun createIfdsEngine() = TaintAnalysisUnitRunnerManager(
        refManager, cancellation,
        analysisManager,
        ifdsAnalysisGraph as ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver() as UnitResolver<CommonMethod>,
        summarySerializationContext,
        options.debugOptions?.taintRulesStatsSamplingPeriod,
    )

    private fun analyzeStaged(entryPoints: List<Method>): Pair<List<VulnerabilityWithTrace>, Status> {
        val analysisStart = TimeSource.Monotonic.markNow()

        val startMethods = entryPoints.map { MethodWithContext(it, EmptyMethodContext) }

        logger.info { "Start prescan phase" }
        prescan(startMethods)
        logger.info { "Finish prescan phase" }

        logger.info { "Start full scan phase" }
        val fullScanResult = fullScan(analysisStart, entryPoints, startMethods)
        logger.info { "Finish full scan phase" }
        return fullScanResult
    }

    private fun prescan(startMethods: List<MethodWithContext>) {
        analysisManager.selectPhase(TaintAnalysisManager.Phase.Prescan)
        ifdsEngine.resetApManager(TreeApManager(AnyAccessorDisabled, refManager, cancellation))

        val prescanTimeout = options.ifdsTimeout * 0.3
        runCatching { ifdsEngine.runAnalysis(startMethods, timeout = prescanTimeout, cancellationTimeout = 30.seconds) }
            .onFailure { logger.error(it) { "Prescan failed" } }

        if (options.debugOptions?.enableIfdsCoverage == true) {
            logger.debug {
                ifdsEngine.reportCoverage()
            }
        }
    }

    private fun fullScan(
        analysisStart: TimeSource.Monotonic.ValueTimeMark,
        entryPoints: List<Method>,
        startMethods: List<MethodWithContext>,
    ): Pair<List<VulnerabilityWithTrace>, Status> {
        analysisManager.selectPhase(TaintAnalysisManager.Phase.FullScan)
        ifdsEngine.resetApManager(apManager)

        val analysisTimeout = (options.ifdsTimeout - analysisStart.elapsedNow()) * 0.80
        runCatching { ifdsEngine.runAnalysis(startMethods, timeout = analysisTimeout, cancellationTimeout = 30.seconds) }
            .onFailure { logger.error(it) { "Full analysis failed" } }

        val analysisStatus = ifdsEngine.status.get()

        if (options.storeSummaries) {
            logger.info { "Storing summaries" }
            ifdsEngine.storeSummaries()
        }

        ifdsEngine.cleanup()

        val allVulnerabilities = ifdsEngine.getVulnerabilities()

        logger.info { "Start vulnerability confirmation" }
        val vulnCheckTimeout = options.ifdsTimeout - analysisStart.elapsedNow()
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

        if (options.debugOptions?.enableVulnSummary == true) {
            logger.info {
                printVulnSummary(vulnerabilities)
            }
        }

        if (options.analysisCwe != null) {
            vulnerabilities = vulnerabilities.filter {
                val cwe = (it.rule.meta as TaintSinkMeta).cwe
                cwe?.intersect(options.analysisCwe)?.isNotEmpty() ?: true
            }

            logger.info { "Vulnerabilities with cwe ${options.analysisCwe}: ${vulnerabilities.size}" }
        }

        logger.info { "Start trace generation" }
        val leftTime = options.ifdsTimeout - analysisStart.elapsedNow()
        val traceResolutionTimeout = leftTime * 0.90 // Reserve 10% of time for report creation
        if (!traceResolutionTimeout.isPositive()) {
            logger.warn { "No time remaining for trace resolution" }
            val status = Status(analysisStatus, TaintAnalysisUnitRunnerManager.Status.TIMEOUT)
            return emptyList<VulnerabilityWithTrace>() to status
        }

        val vulnerabilitiesWithTraces = ifdsEngine.generateTraces(entryPoints, vulnerabilities, traceResolutionTimeout)
            .also { logger.info { "Finish trace generation" } }

        val filteredVulnerabilities = vulnerabilitiesWithTraces.filter {
            it.trace !is TracePathGenerationResult.Failure
        }
        if (filteredVulnerabilities.size != vulnerabilitiesWithTraces.size) {
            val delta = vulnerabilitiesWithTraces.size - filteredVulnerabilities.size
            logger.info { "Filter out $delta vulnerabilities without traces" }
        }

        val traceResolutionStatus = ifdsEngine.status.get()
        val status = Status(analysisStatus, traceResolutionStatus)

        return filteredVulnerabilities to status
    }

    private object InnerCallTaintTraceResolveStrategy : InnerCallTraceResolveStrategy {
        override fun innerCallSummaryEdgeIsRelevant(summaryEdge: TraceSummaryEdge): Boolean {
            if (summaryEdge.edge.fact.base is AccessPathBase.ClassStatic) return false
            return super.innerCallSummaryEdgeIsRelevant(summaryEdge)
        }
    }

    private fun TaintAnalysisUnitRunnerManager.generateTraces(
        entryPoints: List<Method>,
        vulnerabilities: List<TaintSinkTracker.TaintVulnerability>,
        timeout: Duration,
    ): List<VulnerabilityWithTrace> {
        val entryPointsSet = entryPoints.toHashSet()
        val interProcTraces = resolveVulnerabilityInterProceduralTraces(
            entryPointsSet, vulnerabilities,
            resolverParams = TraceResolver.Params(
                resolveEntryPointToStartTrace = options.symbolicExecutionEnabled,
            ),
            timeout = timeout * 0.5,
            cancellationTimeout = 30.seconds
        )

        return resolveVulnerabilityTraces(
            interProcTraces,
            resolverParams = TracePathResolveParams(
                limit = options.tracePathLimit,
                sourceToSinkInnerTraceResolutionLimit = 5,
                innerCallTraceResolveStrategy = InnerCallTaintTraceResolveStrategy
            ),
            timeout = timeout * 0.5,
            cancellationTimeout = 30.seconds
        )
    }

    interface AnalyzerCoverageReportTool<Method, U> {
        fun includeInReport(method: Method): Boolean
        fun methodInstructionCount(method: Method): Int
        fun groupingUnit(method: Method): U
        fun printUnit(key: U): String
        fun unitMethods(unit: U): List<Method>
    }

    open fun coverageReportTool(): AnalyzerCoverageReportTool<Method, *>? = null

    private fun TaintAnalysisUnitRunnerManager.reportCoverage(): String {
        return reportCoverage(coverageReportTool() ?: return "")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <U> TaintAnalysisUnitRunnerManager.reportCoverage(tool: AnalyzerCoverageReportTool<Method, U>) = buildString {
        val methodStats = collectMethodStats()
        val projectClassCoverage: Map<U, List<Pair<Method, MethodStats.Stats>>> = methodStats.stats.entries
            .filter { tool.includeInReport(it.key as Method) }
            .groupBy({ tool.groupingUnit(it.key as Method) }, { it.key as Method to it.value })

        appendLine("Project class coverage")
        projectClassCoverage.entries
            .sortedBy { tool.printUnit(it.key) }
            .forEach { (cls, methods) ->
                appendLine(tool.printUnit(cls))
                for ((method, cov) in methods.sortedBy { it.toString() }) {
                    val covPc = percentToString(cov.coveredInstructions.cardinality(), tool.methodInstructionCount(method))
                    appendLine("$method | $covPc")
                }

                val missedMethods = tool.unitMethods(cls) - methods.mapTo(hashSetOf()) { it.first }
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

        fun TaintSinkTracker.TaintVulnerabilityRuleNode.kind(): List<String> = when (this) {
            is TaintSinkTracker.TaintVulnerabilityRuleNode.Unconditional -> listOf("unconditional")
            is TaintSinkTracker.TaintVulnerabilityRuleNode.Fact -> listOf("fact")
            is TaintSinkTracker.TaintVulnerabilityRuleNode.WithRequirement -> requirement.values.flatMap { v ->
                v.kind().map { "end#${it}" }
            }
        }

        fun TaintSinkTracker.TaintVulnerability.vulnSummary(): List<VulnInfo> {
            val kinds = vulnerabilityRules.values.flatMap { it.kind() }.distinct()
            return kinds.map { VulnInfo("${statement.location}|${statement}", ruleId, it) }
        }

        val info = vulnerabilities.flatMapTo(mutableListOf()) { it.vulnSummary() }
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
    }
}
