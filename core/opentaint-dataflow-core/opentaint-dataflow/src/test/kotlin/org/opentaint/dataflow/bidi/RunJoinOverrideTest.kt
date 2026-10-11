package org.opentaint.dataflow.bidi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RunJoinOverrideTest {
    @Test fun `failed join overrides complete quiescence and exposes no actor or witness before the worker stops`(): Unit {
        for (index in listOf(1, 2)) {
            val f = EngineShellFixtures()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val exited = CountDownLatch(1)
            val first = AtomicReference<RunStatus?>()
            val shared = f.shared(threads = 2, cancellationTimeout = 30.milliseconds)
            try {
                val factory = ShellHandlers({ port, event ->
                    port.analyzer(assertIs<RunEvent.Start>(event).root)
                })
                val run = RunManager(f.config(index = index), shared, factory)
                // Add a runner-owned coroutine that ignores cancellation until the test releases it.
                // It uses the real run scope and join list without creating an analysis event.
                val scope = run.javaClass.getDeclaredField("scope").apply { isAccessible = true }
                    .get(run) as CoroutineScope
                val worker = scope.launch {
                    entered.countDown()
                    try {
                        while (run.terminalStatus == null) LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(100))
                        first.set(run.terminalStatus)
                        check(release.await(5, TimeUnit.SECONDS))
                    } finally { exited.countDown() }
                }
                @Suppress("UNCHECKED_CAST")
                val jobs = run.javaClass.getDeclaredField("runnerJobs").apply { isAccessible = true }
                    .get(run) as MutableList<Job>
                jobs += worker
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val result = run.run(5.seconds)
                assertEquals(RunStatus.COMPLETE, first.get())
                assertEquals(RunStatus.FAILED, run.terminalStatus)
                assertEquals(RunStatus.FAILED, result.status)
                assertEquals(index, result.runIndex)
                assertTrue(result.analyzers.isEmpty())
                assertTrue(result.witnessesByKey().isEmpty())
                assertEquals(0L, result.demandLayerEdges)
                assertEquals(0L, result.recordCrossings)
                assertEquals(0, factory.created.single().freezeCount.get())
                assertEquals(1L, exited.count, "The failed join has not stopped this worker")
                assertFalse(f.cancellation.isActive())
            } finally {
                release.countDown()
                assertTrue(exited.await(5, TimeUnit.SECONDS))
                shared.close()
            }
        }
    }
}
