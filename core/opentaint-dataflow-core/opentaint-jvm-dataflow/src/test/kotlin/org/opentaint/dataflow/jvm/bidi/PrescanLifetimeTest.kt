package org.opentaint.dataflow.jvm.bidi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy.AnyAccessorDisabled
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.interp.CallStage
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRPrescanFixtures
import org.opentaint.dataflow.jvm.bidi.interp.PrescanLambdas
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import java.lang.ref.WeakReference
import kotlin.test.*
import kotlin.time.Duration

/** Join before copy/release; keep intentional application owners alive during collection checks. */
class PrescanLifetimeTest : BidiJIRTestUtils() {
    @Test
    fun `successful joined prescan releases all engine stores while copied values remain usable`(): Unit {
        JIRPrescanFixtures(cp, graph, aliases = true).use { f ->
            f.run("rootFirst", "rootSecond", "alias")
            f.engine.requirePrescanJoined()
            assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, f.engine.status.get())
            assertTrue(f.engine.allUnits().isNotEmpty())
            assertTrue(f.contexts().any { it.aliasAnalysis != null })
            val caller = f.method("invoke")
            val s = f.call(caller)
            val saved = f.manager.prescanLambdas()
            val ids = f.manager.prescanRuleIds()
            f.engine.registerMethodCallFromUnit(caller, SingletonUnit)
            assertEquals(setOf(SingletonUnit), f.engine.methodCallers(caller))
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            assertTrue(f.contexts().isEmpty())
            assertTrue(f.engine.allUnits().isEmpty())
            assertTrue(f.engine.methodCallers(caller).isEmpty())
            assertFailsWith<IllegalStateException> { f.engine.apManager }
            assertTrue(ids.isEmpty())
            val p = interp(prescanLambdas = saved).forms(key(caller))
                .call(Direction.FORWARD, s, checkNotNull(s.callExpr))
            assertEquals(setOf(f.implementation("first"), f.implementation("second")),
                p.stages.filterIsInstance<CallStage.Callees>().single().callees.map { it.method }.toSet())
        }
    }

    @Test
    fun `active and cancelled unfinished owned jobs forbid release without clearing retained owners`(): Unit = runBlocking {
        JIRPrescanFixtures(cp, graph).use { f ->
            f.run("normal")
            f.engine.requirePrescanJoined()
            val oldAp = f.engine.apManager
            val oldRunner = f.runner()
            val owners = f.engine.allUnits().toSet()
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val job = scope.launch {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
            }
            try {
                withTimeout(5000) { entered.await() }
                f.ownJob(job)
                assertFalse(job.isCompleted)
                assertFailsWith<IllegalStateException> { f.run() }
                assertFailsWith<IllegalStateException> {
                    f.engine.resetApManager(TreeApManager(AnyAccessorDisabled, f.refManager, f.cancellation))
                }
                assertFailsWith<IllegalStateException> { f.engine.requirePrescanJoined() }
                assertFailsWith<IllegalStateException> { f.engine.releasePrescan() }
                assertSame(oldAp, f.engine.apManager)
                assertSame(oldRunner, f.runner())
                assertEquals(owners, f.engine.allUnits())
                job.cancel()
                assertFalse(job.isCompleted, "Cancellation requested is not completed termination")
                assertFailsWith<IllegalStateException> { f.engine.requirePrescanJoined() }
                assertFailsWith<IllegalStateException> { f.engine.releasePrescan() }
                assertSame(oldAp, f.engine.apManager)
            } finally {
                release.complete(Unit)
                withTimeout(5000) { job.join() }
                scope.cancel()
            }
            f.engine.requirePrescanJoined()
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            assertTrue(f.engine.allUnits().isEmpty())
        }
    }

    @Test
    fun `expired bounded termination never grants copy or release while a real owned job is alive`(): Unit = runBlocking {
        JIRPrescanFixtures(cp, graph).use { f ->
            f.run("normal")
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val job = scope.launch {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
            }
            try {
                withTimeout(5000) { entered.await() }
                f.engine.resetApManager(TreeApManager(AnyAccessorDisabled, f.refManager, f.cancellation))
                f.beforeResolve = { f.beforeResolve = null; f.ownJob(job) }
                // Add the real unfinished job inside this run's setup, then expire the real termination wait.
                runCatching { f.run("normal", cancellationTimeout = Duration.ZERO) }
                assertEquals(TaintAnalysisUnitRunnerManager.Status.EXCEPTION, f.engine.status.get())
                assertFalse(job.isActive, "Cancellation must happen before the bounded join")
                assertFalse(job.isCompleted)
                assertFailsWith<IllegalStateException> { f.engine.requirePrescanJoined() }
                assertFailsWith<IllegalStateException> { f.engine.releasePrescan() }
                assertTrue(f.engine.allUnits().isNotEmpty(), "Do not hide an unjoined job by clearing owners")
                assertNotNull(f.engine.apManager)
            } finally {
                release.complete(Unit)
                withTimeout(5000) { job.join() }
                scope.cancel()
            }
        }
    }

    @Test
    fun `unreleased old full scan reuses lambda trackers and replaces its AP manager normally`(): Unit {
        JIRPrescanFixtures(cp, graph).use { f ->
            f.run("rootFirst", "rootSecond")
            f.engine.requirePrescanJoined()
            val caller = f.method("invoke")
            val index = f.call(caller).location.index
            val expected = setOf(f.implementation("first"), f.implementation("second"))
            assertEquals(expected, f.manager.prescanLambdas().getValue(caller)[index])
            val originalContext = f.contexts().first { it.methodEntryPoint.method == caller }
            val nextAp = TreeApManager(AnyAccessorDisabled, f.refManager, f.cancellation)
            f.manager.selectPhase(TaintAnalysisManager.Phase.FullScan)
            f.engine.resetApManager(nextAp)
            f.run("rootFirst", "rootSecond")
            f.engine.requirePrescanJoined()
            assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, f.engine.status.get())
            assertSame(nextAp, f.engine.apManager)
            assertTrue(f.contexts().any { it === originalContext })
            assertEquals(expected, f.manager.prescanLambdas().getValue(caller)[index])
            assertTrue(f.engine.allUnits().isNotEmpty())
            assertTrue(f.engine.findZeroSummaryEdges(f.entry(f.method("rootFirst"))).isNotEmpty(),
                "The legacy path ran again and retains its ordinary summary storage")
        }
    }

    @Test
    fun `retained copied values and shared graph do not retain joined prescan AP runner context or aliases`(): Unit {
        JIRPrescanFixtures(cp, graph, aliases = true).use { f ->
            val released = captureAndRelease(f)
            awaitCollected(released.references)
            assertSame(f.manager.factTypeChecker, released.checker)
            assertSame(f.external, released.external)
            val caller = f.method("invoke")
            val s = f.call(caller)
            val owner = interp(prescanLambdas = released.lambdas, externalMethodTracker = released.external)
            val p = owner.forms(key(caller)).call(Direction.FORWARD, s, checkNotNull(s.callExpr))
            assertEquals(setOf(f.implementation("first"), f.implementation("second")),
                p.stages.filterIsInstance<CallStage.Callees>().single().callees.map { it.method }.toSet())
            assertTrue(graph.methodGraph(caller).entryPoints().any())
        }
    }

    @Test
    fun `activating the new cancellation cannot reactivate the released prescan cancellation`(): Unit {
        JIRPrescanFixtures(cp, graph).use { f ->
            f.run("normal")
            f.engine.requirePrescanJoined()
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            f.cancellation.cancel()
            val newCancellation = Cancellation()
            val newAp = ApManager(newCancellation, f.refManager)
            newCancellation.activate()
            newAp.cancellation.checkpoint()
            assertNotSame(f.cancellation, newAp.cancellation)
            assertFalse(f.cancellation.isActive())
            assertFailsWith<Cancellation.Cancelled> { f.cancellation.checkpoint() }
            assertTrue(f.engine.allUnits().isEmpty())
        }
    }

    private data class Released(
        val references: Map<String, WeakReference<Any>>,
        val lambdas: PrescanLambdas,
        val checker: org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker,
        val external: org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker,
    )

    /** The helper frame drops every strong old AP/runner/context/alias reference before GC polling. */
    private fun captureAndRelease(f: JIRPrescanFixtures): Released {
        f.run("rootFirst", "rootSecond", "alias")
        f.engine.requirePrescanJoined()
        val context = f.contexts().first { it.methodEntryPoint.method == f.method("alias") }
        val alias = assertNotNull(context.aliasAnalysis)
        assertTrue(f.engine.findZeroSummaryEdges(f.entry(f.method("rootFirst"))).isNotEmpty())
        val refs = mapOf(
            "prescan AP manager" to WeakReference<Any>(f.engine.apManager),
            "prescan runner" to WeakReference<Any>(f.runner()),
            "prescan context" to WeakReference<Any>(context),
            "prescan alias analysis" to WeakReference<Any>(alias),
        )
        val snapshot = f.manager.prescanLambdas()
        val checker = f.manager.factTypeChecker
        val external = f.external
        f.manager.releasePrescan()
        f.engine.releasePrescan()
        return Released(refs, snapshot, checker, external)
    }

    private fun awaitCollected(references: Map<String, WeakReference<Any>>) {
        repeat(20) {
            System.gc()
            System.runFinalization()
            if (references.values.all { it.get() == null }) return
            Thread.sleep(20)
        }
        assertEquals(emptySet(), references.filterValues { it.get() != null }.keys,
            "Joined prescan owners retained while copied checker, tracker, classpath and graph are alive")
    }
}
