package org.opentaint.dataflow.jvm.bidi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy.AnyAccessorDisabled
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRPrescanFixtures
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.util.analysis.ApplicationGraph
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Additional J01 controls; the root owns installation and execution. */
class PrescanJoinRegressionTest : BidiJIRTestUtils() {
    @Test
    fun `active and cancelled unfinished progress job forbids copy and release`(): Unit = runBlocking {
        JIRPrescanFixtures(cp, graph).use { f ->
            assertFailsWith<IllegalStateException> { f.engine.requirePrescanJoined() }
            f.run("normal")
            f.engine.requirePrescanJoined()
            val oldAp = f.engine.apManager
            val oldRunner = f.runner()
            val owners = f.engine.allUnits().toSet()
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val progress = scope.launch {
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            try {
                withTimeout(5000) { entered.await() }
                TaintAnalysisUnitRunnerManager::class.java.getDeclaredField("progressJob")
                    .also { it.isAccessible = true }.set(f.engine, progress)
                assertFalse(progress.isCompleted)
                assertFailsWith<IllegalStateException> {
                    f.engine.resetApManager(TreeApManager(AnyAccessorDisabled, f.refManager, f.cancellation))
                }
                assertFailsWith<IllegalStateException> { f.run() }
                assertFailsWith<IllegalStateException> { f.engine.requirePrescanJoined() }
                assertFailsWith<IllegalStateException> { f.engine.releasePrescan() }
                assertSame(oldAp, f.engine.apManager)
                assertSame(oldRunner, f.runner())
                assertEquals(owners, f.engine.allUnits())
                progress.cancel()
                assertFalse(progress.isActive)
                assertFalse(progress.isCompleted)
                assertFailsWith<IllegalStateException> { f.engine.requirePrescanJoined() }
                assertFailsWith<IllegalStateException> { f.engine.releasePrescan() }
                assertSame(oldAp, f.engine.apManager)
            } finally {
                release.complete(Unit)
                withTimeout(5000) { progress.join() }
                scope.cancel()
            }
            f.engine.requirePrescanJoined()
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            assertTrue(f.engine.allUnits().isEmpty())
        }
    }

    @Test
    fun `setup failure after retained runner restart still cancels and joins the owned jobs`(): Unit {
        JIRPrescanFixtures(cp, graph).use { f ->
            var failResolve = false
            val setupFailure = IllegalStateException("resolver setup failure")
            val restartedJobs = arrayListOf<Job>()
            lateinit var engine: TaintAnalysisUnitRunnerManager
            val resolver = object : JIRUnitResolver {
                override fun locationIsUnknown(loc: RegisteredLocation) = f.units.locationIsUnknown(loc)
                override fun resolve(method: JIRMethod): UnitType {
                    if (failResolve) {
                        restartedJobs += ownedJobs(engine).toList()
                        throw setupFailure
                    }
                    return f.units.resolve(method)
                }
            }
            @Suppress("UNCHECKED_CAST")
            val commonGraph = graph as ApplicationGraph<CommonMethod, CommonInst>
            engine = TaintAnalysisUnitRunnerManager(f.refManager, f.cancellation, f.manager,
                commonGraph, resolver as UnitResolver<CommonMethod>, NoStoredSummaries, null)
            try {
                engine.resetApManager(TreeApManager(AnyAccessorDisabled, f.refManager, f.cancellation))
                val roots = listOf(MethodWithContext(f.method("normal"), EmptyMethodContext))
                engine.runAnalysis(roots, 5.seconds, 5.seconds)
                engine.requirePrescanJoined()
                val oldRunner = assertNotNull(engine.findUnitRunner(SingletonUnit))
                // A fresh queue makes the restarted runner wait, so it needs actual cancellation.
                val nextAp = TreeApManager(AnyAccessorDisabled, f.refManager, f.cancellation)
                engine.resetApManager(nextAp)
                failResolve = true
                assertSame(setupFailure, assertFailsWith<IllegalStateException> {
                    engine.runAnalysis(roots, 5.seconds, 5.seconds)
                })
                assertTrue(restartedJobs.isNotEmpty(), "Setup failed after a retained runner was started")
                assertTrue(restartedJobs.all { it.isCompleted }, "Setup failure must terminate every started job")
                engine.requirePrescanJoined()
                assertEquals(TaintAnalysisUnitRunnerManager.Status.EXCEPTION, engine.status.get())
                assertSame(oldRunner, engine.findUnitRunner(SingletonUnit))
                assertSame(nextAp, engine.apManager)
                f.manager.releasePrescan()
                engine.releasePrescan()
                assertTrue(engine.allUnits().isEmpty())
            } finally {
                ownedJobs(engine).forEach { it.cancel() }
                runBlocking { withTimeout(5000) { ownedJobs(engine).forEach { it.join() } } }
                engine.close()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ownedJobs(engine: TaintAnalysisUnitRunnerManager): ConcurrentLinkedQueue<Job> =
        TaintAnalysisUnitRunnerManager::class.java.getDeclaredField("runnerJobs")
            .also { it.isAccessible = true }.get(engine) as ConcurrentLinkedQueue<Job>

    private object NoStoredSummaries : SummarySerializationContext {
        override fun loadSummaries(method: CommonMethod): ByteArray? = null
        override fun getIdByMethod(method: CommonMethod): Long = error("No serialization in this fixture")
        override fun getIdByAccessor(accessor: Accessor): Long = error("No serialization in this fixture")
        override fun getMethodById(id: Long): CommonMethod = error("No stored summaries")
        override fun getAccessorById(id: Long): Accessor = error("No stored summaries")
        override fun storeSummaries(method: CommonMethod, summaries: ByteArray): Unit = error("No serialization")
        override fun flush() = Unit
    }
}
