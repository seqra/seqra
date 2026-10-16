package org.opentaint.dataflow.jvm.bidi

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.ResolvedPrescanGraph
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.driver.BidiEntry
import org.opentaint.dataflow.bidi.driver.IterationDriver
import org.opentaint.dataflow.bidi.driver.IterationPolicy
import org.opentaint.dataflow.bidi.driver.Report
import org.opentaint.dataflow.bidi.driver.ReportState
import org.opentaint.dataflow.bidi.engine.MethodContextCache
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.engine.Run1StaticProfiles
import org.opentaint.dataflow.bidi.engine.SharedObjects
import org.opentaint.dataflow.bidi.store.ConcurrentVulnerabilityStore
import org.opentaint.dataflow.bidi.store.PersistentRecordStore
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodEntrypointResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.interp.JIRInterpreter
import org.opentaint.dataflow.jvm.bidi.interp.JIRStaticMetadata
import org.opentaint.dataflow.jvm.bidi.interp.JIRStaticMetadataCollector
import org.opentaint.dataflow.jvm.bidi.interp.MetadataAliases
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.bidi.interp.JIRMethodEntries
import org.opentaint.dataflow.jvm.bidi.interp.PrescanLambdas
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.graph.JApplicationGraph
import kotlin.time.Duration
import kotlin.time.TimeSource

/** Phase 3 copies prescan values and returns simple sink traces for both report states. */
class JIRBidiAnalysis internal constructor(
    private val cp: JIRClasspath,
    private val graph: JApplicationGraph,
    private val unitResolver: JIRUnitResolver,
    private val taintConfig: TaintRulesProvider,
    private val params: JIRAnalysisManager.Params,
    private val policy: IterationPolicy,
    private val refManager: RefManager,
    private val driverFactory: (IterationPolicy, SharedObjects, Duration) -> IterationDriver,
) : BidiEntry {
    constructor(
        cp: JIRClasspath,
        graph: JApplicationGraph,
        unitResolver: JIRUnitResolver,
        taintConfig: TaintRulesProvider,
        params: JIRAnalysisManager.Params,
        policy: IterationPolicy,
        refManager: RefManager,
    ) : this(cp, graph, unitResolver, taintConfig, params, policy, refManager, ::IterationDriver)

    private val cancellation = Cancellation()

    private class PrescanResult(
        val ruleIds: Set<String>,
        val lambdas: PrescanLambdas,
        val roots: List<MethodEntryPoint>,
        val checker: JIRFactTypeChecker,
        val externalMethodTracker: ExternalMethodTracker?,
        var calls: ResolvedPrescanGraph,
        var staticLocals: Map<CommonMethod, JIRStaticMetadata>,
    )

    private var prescan: PrescanResult? = null

    override fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>) {
        error("JVM bidi gathering requires the successfully joined resolved prescan graph")
    }

    /** Preparation consumes the caller total budget; the caller passes its remaining budget to run. */
    override fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>,
                        graph: ResolvedPrescanGraph) {
        val setupStarted = TimeSource.Monotonic.markNow()
        val source = manager as JIRAnalysisManager
        val resolver = JIRMethodEntrypointResolver(this.graph)
        val roots = startMethods.flatMap { start ->
            resolver.resolveEntryPoints(start.method, start.ctx).map { MethodEntryPoint(start.ctx, it) }
        }
        val ids = source.prescanRuleIds()
        val lambdas = source.prescanLambdas()
        taintConfig.selectRules(ids)
        // These managers and forward forms are temporary. Only neutral value descriptors escape.
        val scratch = ApManager(cancellation)
        val callResolver = JIRCallResolver(cp, unitResolver)
        val entries = JIRMethodEntries(this.graph, JIRLanguageManager(cp), callResolver, taintConfig,
            JIRLocalAliasAnalysis.Params(useAliasAnalysis = false), cancellation, lambdas, formStatistics = null)
        val compiler = JIRInterpreter(scratch, taintConfig, source.factTypeChecker, callResolver, resolver,
            entries, params.defaultGetModel, externalMethodTracker = null)
        // Scratch forms disable AA construction. Metadata knowledge follows the real
        // runtime policy, including methods with no retained prescan context/AA owner.
        val aliasKnowledge = MetadataAliases.forRuntime(params.aliasAnalysisParams.useAliasAnalysis)
        val collector = JIRStaticMetadataCollector(compiler, aliasKnowledge)
        val locals = LinkedHashMap<CommonMethod, JIRStaticMetadata>()
        var unknownAliasContexts = 0L
        var graphOnlyMethods = 0L
        source.forEachPrescanContext { context ->
            cancellation.checkpoint()
            val key = context.methodEntryPoint
            if (aliasKnowledge == MetadataAliases.Unknown) unknownAliasContexts++
            val metadata = collector.collect(key)
            locals[key.method] = locals[key.method]?.union(metadata) ?: metadata
        }
        for (method in graph.methods + roots.map { it.method }) {
            cancellation.checkpoint()
            if (method in locals) continue
            graphOnlyMethods++
            val jir = method as JIRMethod
            if (jir.instList.size == 0) { locals[method] = JIRStaticMetadata.EMPTY; continue }
            val keys = resolver.resolveEntryPoints(jir, EmptyMethodContext)
            for (node in keys) {
                val metadata = collector.collect(MethodEntryPoint(EmptyMethodContext, node))
                locals[method] = locals[method]?.union(metadata) ?: metadata
            }
        }
        prescan = PrescanResult(ids, lambdas, roots, source.factTypeChecker,
            source.externalMethodTracker, graph, java.util.Collections.unmodifiableMap(locals))
        logger.info { "Bidi static metadata setup: elapsed=${setupStarted.elapsedNow()}, methods=${locals.size}, " +
            "resolvedCalls=${graph.calls.size}, localAccesses=${locals.values.sumOf { it.accesses.size.toLong() }}, " +
            "localAbstractions=${locals.values.sumOf { it.abstractions.size.toLong() }}, " +
            "declaredMarks=${locals.values.sumOf { it.marks.size.toLong() }}, " +
            "potentialPassInputGates=${locals.values.sumOf { local -> local.marks.count { it.role == org.opentaint.dataflow.bidi.engine.StaticMarkRole.INPUT_GATE }.toLong() }}, " +
            "metadataAliasPolicy=$aliasKnowledge, contextsUnderUnknownAliasPolicy=$unknownAliasContexts, " +
            "graphOnlyMethods=$graphOnlyMethods, baseWideCleanerMarks=${locals.values.sumOf { it.cleanerMarks.size.toLong() }}; " +
            "pass needs conservatively include potential unresolved branches" }
    }

    override fun run(budget: Duration): Report {
        val start = TimeSource.Monotonic.markNow()
        val prepared = prepareRun()
        prepared.shared.use { shared ->
            try {
                return driverFactory(policy, shared, budget - start.elapsedNow()).analyze(prepared.roots)
            } finally {
                shared.formStatistics?.let {
                    logger.info { "Bidi form statistics: ${it.snapshot()}" }
                }
            }
        }
    }

    private class PreparedRun(val roots: List<MethodEntryPoint>, val shared: SharedObjects)

    /** No PrescanResult or its duplicate lambda/rule-ID snapshots survive on the engine call stack. */
    private fun prepareRun(): PreparedRun {
        val copied = checkNotNull(prescan) { "gather() comes first" }.also { prescan = null }
        return PreparedRun(copied.roots, prepareShared(copied))
    }

    /** The preparation frame exits before the driver, so temporary metadata cannot pin run-1 profiles. */
    private fun prepareShared(copied: PrescanResult): SharedObjects {
        val ap = ApManager(cancellation, refManager)
        val language = JIRLanguageManager(cp)
        val callResolver = JIRCallResolver(cp, unitResolver)
        val formReferences = refManager.softRefManager("bidi-method-forms")
        val entries = JIRMethodEntries(graph, language, callResolver, taintConfig, params.aliasAnalysisParams,
            cancellation, copied.lambdas, formReferences, run1Adjacency = copied.calls.adjacency)
        val interpreter = JIRInterpreter(ap, taintConfig, copied.checker, callResolver,
            JIRMethodEntrypointResolver(graph), entries, params.defaultGetModel, copied.externalMethodTracker)
        val closureStarted = TimeSource.Monotonic.markNow()
        val staticProfiles = Run1StaticProfiles.build(ap, copied.calls.adjacency,
            copied.staticLocals.mapValues { (_, local) -> local.compile(ap) })
        logger.info { "Bidi static catalog setup: elapsed=${closureStarted.elapsedNow()}, ${staticProfiles.statistics}" }
        copied.calls = ResolvedPrescanGraph.EMPTY
        copied.staticLocals = emptyMap()
        val contexts = MethodContextCache(interpreter, JIRMethodContextCache(interpreter, entries))
        @Suppress("UNCHECKED_CAST")
        return SharedObjects(ap, ApOps(ap), interpreter, language, contexts, PersistentRecordStore(ap),
            ConcurrentVulnerabilityStore(ap), unitResolver as UnitResolver<CommonMethod>, refManager, cancellation,
            formStatistics = entries.formStatistics, run1StaticProfiles = staticProfiles,
            releaseRun1CatalogCoverage = entries::releaseRun1CatalogCoverage)
    }

    override fun toVulnerabilities(report: Report): List<VulnerabilityWithTrace> {
        val counts = report.entries.groupingBy { it.state }.eachCount()
        logger.info { "Bidi analysis: ${counts[ReportState.CONFIRMED] ?: 0} CONFIRMED, ${counts[ReportState.DEMAND] ?: 0} DEMAND vulnerabilities" }
        return report.entries.map { entry ->
            val rule = entry.key.rule
            val witness = entry.witnesses.firstOrNull { it.confirmed } ?: entry.witnesses.first()
            val node = TaintSinkTracker.TaintVulnerabilityRuleNode.Unconditional(witness.methodKey)
            VulnerabilityWithTrace(TaintSinkTracker.TaintVulnerability(entry.key.statement, rule.id,
                hashMapOf(rule to node)), TracePathGenerationResult.Simple)
        }
    }

    override fun status(report: Report): TaintAnalysisUnitRunnerManager.Status = when (report.end.status) {
        RunStatus.COMPLETE -> TaintAnalysisUnitRunnerManager.Status.OK
        RunStatus.TIMEOUT -> TaintAnalysisUnitRunnerManager.Status.TIMEOUT
        RunStatus.OOM -> TaintAnalysisUnitRunnerManager.Status.OOM
        RunStatus.FAILED -> TaintAnalysisUnitRunnerManager.Status.EXCEPTION
    }

    companion object : KLogging()
}
