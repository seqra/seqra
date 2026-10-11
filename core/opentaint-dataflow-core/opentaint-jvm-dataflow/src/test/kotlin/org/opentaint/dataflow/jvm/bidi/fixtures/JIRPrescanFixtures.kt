package org.opentaint.dataflow.jvm.bidi.fixtures

import kotlinx.coroutines.Job
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy.AnyAccessorDisabled
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.ap.ifds.taint.CommonTaintAnalysisContext
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisUnitStorage
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodEntrypointResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Real JVM prescan owners. Reflection observes retained contexts and injects owned termination Jobs only. */
internal class JIRPrescanFixtures(
    val cp: JIRClasspath,
    val graph: JApplicationSingleExitGraph,
    val rules: TaintRulesProvider = NoTaintRules,
    aliases: Boolean = false,
) : AutoCloseable {
    val refManager = RefManager()
    val cancellation = Cancellation().also { it.activate() }
    val external = ExternalMethodTracker()
    val params = JIRAnalysisManager.Params(aliasAnalysisParams = JIRLocalAliasAnalysis.Params(
        useAliasAnalysis = aliases, aliasAnalysisInterProcCallDepth = 0))
    val manager = JIRAnalysisManager(cp, refManager, rules, external, params)
    @Volatile var beforeResolve: (() -> Unit)? = null
    val units = object : JIRUnitResolver {
        override fun resolve(method: JIRMethod): UnitType {
            beforeResolve?.invoke()
            return if (method.enclosingClass.name.startsWith("sample.bidi.") ||
                method.enclosingClass is LambdaAnonymousClassFeature.JIRLambdaClass) SingletonUnit else UnknownUnit
        }
        override fun locationIsUnknown(loc: RegisteredLocation): Boolean =
            loc != method("normal").enclosingClass.declaration.location
    }
    @Suppress("UNCHECKED_CAST")
    val engine = TaintAnalysisUnitRunnerManager(refManager, cancellation, manager,
        graph as org.opentaint.util.analysis.ApplicationGraph<CommonMethod, org.opentaint.ir.api.common.cfg.CommonInst>,
        units as UnitResolver<CommonMethod>, NoStoredSummaries, null)

    init {
        manager.selectPhase(TaintAnalysisManager.Phase.Prescan)
        engine.resetApManager(TreeApManager(AnyAccessorDisabled, refManager, cancellation))
    }

    fun method(name: String): JIRMethod = cp.findClassOrNull(SAMPLE)!!.declaredMethods.single { it.name == name }
    fun entry(method: JIRMethod, context: MethodContext = EmptyMethodContext): MethodEntryPoint =
        MethodEntryPoint(context, JIRMethodEntrypointResolver(graph).resolveEntryPoints(method, context).single())
    fun call(method: JIRMethod): JIRInst = method.instList.instructions.first { it.callExpr != null &&
        it.callExpr!!.method.method.name == "apply" }
    fun lambda(name: String): LambdaAnonymousClassFeature.JIRLambdaClass =
        method(name).instList.instructions.mapNotNull(
            LambdaExpressionToAnonymousClassTransformerFeature::findLambdaAllocation).single()
    fun implementation(name: String): JIRMethod = lambda(name).declaredMethods.single { it.name == "apply" }

    fun run(vararg names: String, timeout: Duration = 5.seconds, cancellationTimeout: Duration = 5.seconds) {
        engine.runAnalysis(names.map { MethodWithContext(method(it), EmptyMethodContext) }, timeout, cancellationTimeout)
    }

    fun runner(): TaintAnalysisUnitRunner = checkNotNull(engine.findUnitRunner(SingletonUnit))

    /** Register through the real manager after the finite run established its real resolver/runner. */
    @Suppress("UNCHECKED_CAST")
    fun context(method: JIRMethod, context: MethodContext = EmptyMethodContext): JIRMethodAnalysisContext =
        manager.getMethodAnalysisContext(entry(method, context),
            graph as org.opentaint.util.analysis.ApplicationGraph<CommonMethod, org.opentaint.ir.api.common.cfg.CommonInst>,
            runner().methodCallResolver,
            CommonTaintAnalysisContext(TaintSinkTracker(TaintAnalysisUnitStorage(engine.apManager, manager))),
            contextForEmptyMethod = null) as JIRMethodAnalysisContext

    @Suppress("UNCHECKED_CAST")
    fun contexts(): List<JIRMethodAnalysisContext> = JIRAnalysisManager::class.java.getDeclaredField("contexts")
        .also { it.isAccessible = true }.get(manager).let { (it as Collection<JIRMethodAnalysisContext>).toList() }

    /** A real active Job, not a fake joined state. Parent explicitly authorizes this test-only seam. */
    @Suppress("UNCHECKED_CAST")
    fun ownJob(job: Job) {
        val jobs = TaintAnalysisUnitRunnerManager::class.java.getDeclaredField("runnerJobs")
            .also { it.isAccessible = true }.get(engine) as ConcurrentLinkedQueue<Job>
        jobs.add(job)
    }

    override fun close() { engine.close() }

    companion object { const val SAMPLE = "sample.bidi.BidiPrescanSample" }

    private object NoStoredSummaries : SummarySerializationContext {
        override fun loadSummaries(method: CommonMethod): ByteArray? = null
        override fun getIdByMethod(method: CommonMethod): Long = error("This fixture does not serialize summaries")
        override fun getIdByAccessor(accessor: Accessor): Long = error("This fixture does not serialize summaries")
        override fun getMethodById(id: Long): CommonMethod = error("This fixture has no serialized summaries")
        override fun getAccessorById(id: Long): Accessor = error("This fixture has no serialized summaries")
        override fun storeSummaries(method: CommonMethod, summaries: ByteArray): Unit = error("Unexpected serialization")
        override fun flush() = Unit
    }
}
