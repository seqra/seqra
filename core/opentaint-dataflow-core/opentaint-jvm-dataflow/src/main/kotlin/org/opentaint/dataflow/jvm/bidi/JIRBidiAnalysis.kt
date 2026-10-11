package org.opentaint.dataflow.jvm.bidi

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
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
import org.opentaint.dataflow.jvm.bidi.interp.JIRMethodEntries
import org.opentaint.dataflow.jvm.bidi.interp.PrescanLambdas
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRClasspath
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
    )

    private var prescan: PrescanResult? = null

    override fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>) {
        val source = manager as JIRAnalysisManager
        val resolver = JIRMethodEntrypointResolver(graph)
        val roots = startMethods.flatMap { start ->
            resolver.resolveEntryPoints(start.method, start.ctx).map { MethodEntryPoint(start.ctx, it) }
        }
        prescan = PrescanResult(source.prescanRuleIds(), source.prescanLambdas(), roots,
            source.factTypeChecker, source.externalMethodTracker)
    }

    override fun run(budget: Duration): Report {
        val start = TimeSource.Monotonic.markNow()
        val copied = checkNotNull(prescan) { "gather() comes first" }.also { prescan = null }
        taintConfig.selectRules(copied.ruleIds)
        val ap = ApManager(cancellation, refManager)
        val language = JIRLanguageManager(cp)
        val callResolver = JIRCallResolver(cp, unitResolver)
        val entries = JIRMethodEntries(graph, language, callResolver, taintConfig, params.aliasAnalysisParams,
            cancellation, copied.lambdas)
        val interpreter = JIRInterpreter(ap, taintConfig, copied.checker, callResolver,
            JIRMethodEntrypointResolver(graph), entries, params.defaultGetModel, copied.externalMethodTracker)
        val contexts = MethodContextCache(interpreter, JIRMethodContextCache(interpreter, entries))
        @Suppress("UNCHECKED_CAST")
        SharedObjects(ap, ApOps(ap), interpreter, language, contexts, PersistentRecordStore(ap),
            ConcurrentVulnerabilityStore(ap), unitResolver as UnitResolver<CommonMethod>, refManager, cancellation).use { shared ->
            return driverFactory(policy, shared, budget - start.elapsedNow()).analyze(copied.roots)
        }
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
