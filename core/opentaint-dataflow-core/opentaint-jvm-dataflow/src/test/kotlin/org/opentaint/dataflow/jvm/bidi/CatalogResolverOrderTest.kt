package org.opentaint.dataflow.jvm.bidi

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.ResolvedPrescanCall
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy.AnyAccessorDisabled
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.dataflow.bidi.driver.IterationDriver
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.engine.SharedObjects
import org.opentaint.dataflow.bidi.interp.CallStage
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolutionContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.ifds.MethodUnit
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.util.analysis.ApplicationGraph
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Real bytecode and independently declared dispatch target sets; no cache reflection. */
class CatalogResolverOrderTest : BidiJIRTestUtils() {
    private companion object {
        const val SAMPLE = "sample.bidi.BidiCatalogResolverSample"
        const val BASE = "$SAMPLE\$Base"
        const val NARROW_LEAF = "$SAMPLE\$NarrowLeaf"
        const val SIBLING = "$SAMPLE\$Sibling"
    }

    private fun declared(owner: String) = method("getModel", owner)
    private fun expected(site: String): Set<JIRMethod> = when (site) {
        "broad" -> setOf(declared(BASE), declared(NARROW_LEAF), declared(SIBLING))
        "narrow" -> setOf(declared(BASE), declared(NARROW_LEAF))
        "empty" -> setOf(declared(BASE))
        else -> error("Unknown fixture site $site")
    }

    private fun units(): JIRUnitResolver = object : JIRUnitResolver {
        private val location = method("broad", SAMPLE).enclosingClass.declaration.location
        override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != location
        override fun resolve(method: JIRMethod) =
            if (method.enclosingClass.declaration.location == location) MethodUnit(method) else UnknownUnit
    }

    private fun call(site: String) = method(site, SAMPLE).instList.instructions.single {
        it.callExpr?.method?.method?.name == "getModel"
    }

    private fun targets(resolver: JIRCallResolver, site: String): Set<JIRMethod> {
        val at = call(site)
        val expression = checkNotNull(at.callExpr)
        // All three inherited references must resolve to one underlying Base method, so this
        // actually exercises cache reuse across DIFFERENT hierarchy roots for the SAME key method.
        assertEquals(declared(BASE), expression.method.method)
        val context = object : JIRCallResolutionContext {
            override val methodEntryPoint: MethodEntryPoint = key(method(site, SAMPLE))
            override val aliasAnalysis: JIRLocalAliasAnalysis? = null
        }
        val rows = resolver.resolve(expression, at, context)
        assertTrue(rows.all { it is JIRCallResolver.MethodResolutionResult.ConcreteMethod })
        return rows.map { assertIs<JIRCallResolver.MethodResolutionResult.ConcreteMethod>(it).method.method as JIRMethod }.toSet()
    }

    @Test
    fun `broad cache use never leaks a sibling getter into a narrow or empty receiver`(): Unit {
        val resolver = JIRCallResolver(cp, units())
        for (site in listOf("broad", "narrow", "empty", "broad", "narrow"))
            assertEquals(expected(site), targets(resolver, site), site)
    }

    @Test
    fun `narrow and empty cache use never hides broad overrides including public enumeration`(): Unit {
        for (first in listOf("narrow", "empty")) {
            val resolver = JIRCallResolver(cp, units())
            assertEquals(expected(first), targets(resolver, first), first)
            assertEquals(expected("broad"), targets(resolver, "broad"), "$first then broad")
            assertEquals(expected("broad"), resolver.allKnownOverridesOrNull(declared(BASE))?.toSet(),
                "Public override enumeration has the declaring class as its hierarchy root")
            assertEquals(expected(first), targets(resolver, first))
        }
    }

    @Test
    fun `empty override subtree remains cached independently of populated hierarchy roots`(): Unit {
        val resolver = JIRCallResolver(cp, units())
        // EmptyLeaf inherits Base.getModel and has no descendants/overrides. Its public
        // result is Base alone; the cached override list, rather than the whole result, is empty.
        for (site in listOf("empty", "empty", "narrow", "empty", "broad", "empty"))
            assertEquals(expected(site), targets(resolver, site), site)
    }

    private fun planTargets(shared: SharedObjects, site: String): Set<JIRMethod> {
        val caller = method(site, SAMPLE)
        val at = call(site)
        return shared.contexts.forms(key(caller)).withCall(Direction.FORWARD, at, checkNotNull(at.callExpr)) { plan ->
            plan.stages.filterIsInstance<CallStage.Callees>().flatMap { it.callees }.map { it.method as JIRMethod }.toSet()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `real per unit prescan graph supports either fresh shared resolver warm order after release`(): Unit {
        for (order in listOf(listOf("broad", "narrow", "empty"), listOf("empty", "narrow", "broad"))) {
            val refs = RefManager()
            val cancellation = Cancellation().also { it.activate() }
            val rules = NoTaintRules
            val params = JIRAnalysisManager.Params(aliasAnalysisParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = false))
            val legacy = JIRAnalysisManager(cp, refs, rules, ExternalMethodTracker(), params)
            legacy.selectPhase(TaintAnalysisManager.Phase.Prescan)
            val units = units()
            val commonGraph = graph as ApplicationGraph<CommonMethod, CommonInst>
            val engine = TaintAnalysisUnitRunnerManager(refs, cancellation, legacy, commonGraph,
                units as UnitResolver<CommonMethod>, NoStoredSummaries, null)
            engine.use {
                engine.resetApManager(TreeApManager(AnyAccessorDisabled, refs, cancellation))
                val roots = listOf("broad", "narrow", "empty").map { MethodWithContext(method(it, SAMPLE), EmptyMethodContext) }
                engine.runAnalysis(roots, timeout = 5.seconds, cancellationTimeout = 5.seconds)
                assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, engine.status.get())
                engine.requirePrescanJoined()
                val frozen = engine.prescanResolvedGraph()
                for (site in listOf("broad", "narrow", "empty")) {
                    val rows = frozen.calls.filter { it.caller == method(site, SAMPLE) && it.statement == call(site) }
                    assertEquals(expected(site), rows.map { it.callee }.toSet(), "actual joined prescan $site")
                    for (target in expected(site)) assertContains(frozen.calls,
                        ResolvedPrescanCall(method(site, SAMPLE), call(site), target))
                }
                var warmed = false
                val bidi = JIRBidiAnalysis(cp, graph, units, rules, params, FixedLimits(listOf(1, 2, 3)), refs,
                    driverFactory = { policy, shared, remaining ->
                        // Reachable call sites are warmed deterministically; graph edges are NEVER injected.
                        for (site in order) assertEquals(expected(site), planTargets(shared, site), "fresh plan $site")
                        warmed = true
                        IterationDriver(policy, shared, remaining)
                    })
                bidi.gather(legacy, roots, frozen)
                legacy.releasePrescan()
                engine.releasePrescan()
                val report = bidi.run(10.seconds)
                assertTrue(warmed)
                assertEquals(RunStatus.COMPLETE, report.end.status)
                assertEquals(1, report.end.run)
                assertTrue(report.entries.isEmpty())
            }
        }
    }

    private object NoStoredSummaries : SummarySerializationContext {
        override fun loadSummaries(method: CommonMethod): ByteArray? = null
        override fun getIdByMethod(method: CommonMethod): Long = error("No summary serialization")
        override fun getIdByAccessor(accessor: Accessor): Long = error("No summary serialization")
        override fun getMethodById(id: Long): CommonMethod = error("No serialized summaries")
        override fun getAccessorById(id: Long): Accessor = error("No serialized summaries")
        override fun storeSummaries(method: CommonMethod, summaries: ByteArray): Unit = error("Unexpected serialization")
        override fun flush() = Unit
    }
}
