package org.opentaint.jvm.sast.dataflow

import kotlinx.coroutines.runBlocking
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
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.automata.AutomataApManager
import org.opentaint.dataflow.ap.ifds.access.cactus.CactusApManager
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntryAction.TraceSummaryEdge
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.jvm.TaintSinkMeta
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.JIRSummarySerializationContext
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
import org.opentaint.jvm.sast.dataflow.DataFlowApproximationLoader.isApproximation
import org.opentaint.util.analysis.ApplicationGraph
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class JIRTaintAnalyzer(
    val cp: JIRClasspath,
    val taintConfiguration: TaintRulesProvider,
    val projectClasses: ClassLocationChecker,
    val options: TaintAnalyzerOptions,
    val analysisUnit: JIRUnitResolver = PackageUnitResolver(projectClasses),
    private val externalMethodTracker: ExternalMethodTracker? = null,
): AutoCloseable {

    private val ifdsAnalysisGraph by lazy {
        val usages = runBlocking { cp.usagesExt() }
        val mainGraph = JApplicationGraphImpl(cp, usages)
        val explicitExceptionsOnlyGraph = JExplicitExceptionsOnlyApplicationGraph(mainGraph)
        JIRSafeApplicationGraph(explicitExceptionsOnlyGraph)
    }

    val ifdsEngine by lazy { createIfdsEngine() }

    fun analyzeWithIfds(entryPoints: List<JIRMethod>): List<VulnerabilityWithTrace> {
        return analyzeTaintWithIfdsEngine(entryPoints)
    }

    private object UnrollStrategy : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean = when (accessor) {
            is ElementAccessor -> true
            is FieldAccessor -> accessor.fieldName != "<rule-storage>"
            is ClassStaticAccessor,
            is AnyAccessor,
            is FinalAccessor,
            is TaintMarkAccessor -> false
            is ValueAccessor -> error("Unexpected accessor to unroll: $accessor")
        }
    }

    private val apManager by lazy {
        when (options.ifdsApMode) {
            ApMode.Tree -> TreeApManager(UnrollStrategy)
            ApMode.Cactus -> CactusApManager(UnrollStrategy)
            ApMode.Automata -> AutomataApManager(UnrollStrategy)
        }
    }

    private val analysisParams get() = JIRAnalysisManager.Params(
        aliasAnalysisParams = JIRLocalAliasAnalysis.Params(
            aliasAnalysisInterProcCallDepth = options.experimentalAAInterProcCallDepth
        )
    )

    private val summarySerializationContext by lazy {
        if (options.storeSummaries) JIRSummarySerializationContext(cp) else DummySerializationContext
    }

    @Suppress("UNCHECKED_CAST")
    private fun createIfdsEngine() = TaintAnalysisUnitRunnerManager(
        JIRAnalysisManager(cp, analysisParams),
        ifdsAnalysisGraph as ApplicationGraph<CommonMethod, CommonInst>,
        analysisUnit as UnitResolver<CommonMethod>,
        taintConfig,
        summarySerializationContext,
        apManager,
        options.debugOptions?.taintRulesStatsSamplingPeriod,
        externalMethodTracker,
    )

    private fun analyzeTaintWithIfdsEngine(
        entryPoints: List<JIRMethod>,
    ): List<VulnerabilityWithTrace> {
        val analysisStart = TimeSource.Monotonic.markNow()

        val analysisTimeout = options.ifdsTimeout * 0.95 // Reserve 5% of time for report creation
        val startMethods = entryPoints.map { MethodWithContext(it, EmptyMethodContext) }
        runCatching { ifdsEngine.runAnalysis(startMethods, timeout = analysisTimeout, cancellationTimeout = 30.seconds) }
            .onFailure { logger.error(it) { "Ifds engine failed" } }

        if (options.debugOptions?.enableIfdsCoverage == true) {
            logger.debug {
                ifdsEngine.reportCoverage()
            }
        }

        if (options.storeSummaries) {
            logger.info { "Storing summaries" }
            ifdsEngine.storeSummaries()
        }

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
        val traceResolutionTimeout = options.ifdsTimeout - analysisStart.elapsedNow()
        if (!traceResolutionTimeout.isPositive()) {
            logger.warn { "No time remaining for trace resolution" }
            return emptyList()
        }

        val vulnerabilitiesWithTraces = ifdsEngine.generateTraces(entryPoints, vulnerabilities, traceResolutionTimeout)
            .also { logger.info { "Finish trace generation" } }

        val filteredVulnerabilities = vulnerabilitiesWithTraces.filterVulnWithoutTrace()
        if (filteredVulnerabilities.size != vulnerabilitiesWithTraces.size) {
            val delta = vulnerabilitiesWithTraces.size - filteredVulnerabilities.size
            logger.info { "Filter out $delta vulnerabilities without traces" }
        }
        return filteredVulnerabilities
    }

    private object InnerCallTraceResolveStrategy : TraceResolver.InnerCallTraceResolveStrategy {
        override fun innerCallSummaryEdgeIsRelevant(summaryEdge: TraceSummaryEdge): Boolean {
            if (summaryEdge.edge.fact.base is AccessPathBase.ClassStatic) return false
            return super.innerCallSummaryEdgeIsRelevant(summaryEdge)
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
                resolveEntryPointToStartTrace = options.symbolicExecutionEnabled,
                startToSourceTraceResolutionLimit = 100,
                startToSinkTraceResolutionLimit = 100,
                sourceToSinkInnerTraceResolutionLimit = 5,
                innerCallTraceResolveStrategy = InnerCallTraceResolveStrategy
            ),
            timeout = timeout,
            cancellationTimeout = 30.seconds
        )
    }

    private fun List<VulnerabilityWithTrace>.filterVulnWithoutTrace(): List<VulnerabilityWithTrace> =
        filter { it.hasValidTrace() }

    private fun VulnerabilityWithTrace.hasValidTrace(): Boolean {
        val trace = trace ?: return false
        return trace.sourceToSinkTrace.startNodes.isNotEmpty()
    }

    private val taintConfig: TaintRulesProvider by lazy {
        StringConcatRuleProvider(taintConfiguration)
    }

    private fun TaintAnalysisUnitRunnerManager.reportCoverage() = buildString {
        val methodStats = collectMethodStats()
        val projectClassCoverage = methodStats.stats.entries
            .groupBy({ (it.key as JIRMethod).enclosingClass }, { it.key as JIRMethod to it.value })
            .filterKeys { it !is LambdaAnonymousClassFeature.JIRLambdaClass }
            .filterKeys { projectClasses.isProjectClass(it) }

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

        class PackageUnitResolver(private val projectLocations: ClassLocationChecker) : JIRUnitResolver {
            override fun resolve(method: JIRMethod): UnitType {
                if (!projectLocations.isProjectClass(method.enclosingClass) && !isApproximation(method)) {
                    return UnknownUnit
                }

                return PackageUnit(method.enclosingClass.packageName)
            }

            override fun locationIsUnknown(loc: RegisteredLocation): Boolean =
                !projectLocations.isProjectLocation(loc)
        }
    }
}