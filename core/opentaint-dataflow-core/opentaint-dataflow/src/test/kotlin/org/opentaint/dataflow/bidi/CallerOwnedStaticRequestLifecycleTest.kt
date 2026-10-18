package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Real UnitRunner ownership controls for the production static request
 * service. Receiver callbacks acquire ordinary counted queued deliveries. */
class CallerOwnedStaticRequestLifecycleTest {
    @Test fun `source commits during incomplete and final Work secure counted children`() {
        val f = EngineShellFixtures()
        val field = field(f)
        val received = CopyOnWriteArrayList<Set<Int>>()
        lateinit var run: RunManager<ShellActor>
        f.shared().use { shared ->
            val handlers = ShellHandlers({ port, event -> when (event) {
                is RunEvent.Start -> {
                    subscribe(port, event.root, field)
                    val actor = port.analyzer(event.root)
                    actor.add(2)
                    assertTrue(port.enqueue(actor))
                }
                is RunEvent.MarkRequestsIn -> {
                    assertFalse(run.ended)
                    assertTrue(run.inFlight.pending >= 1L)
                    received += event.rows.getValue(field).asIds()
                }
                else -> error("Unexpected lifecycle event $event")
            } }, make = { key, port -> ShellActor(key, onWork = { actor, _ ->
                if (actor.steps == 0L) {
                    commit(port, key, field, 4)
                    assertTrue(received.isEmpty(), "The child must be queued, not handled inline")
                } else {
                    assertEquals(listOf(setOf(4)), received.toList())
                    commit(port, key, field, 9)
                }
            }) })
            run = RunManager(f.config(), shared, handlers, quantum = 1)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(listOf(setOf(4), setOf(9)), received.toList())
            assertEquals(2L, result.analyzers.single().steps)
            assertEquals(5L, run.inFlight.handled, "Root batch, Start, retained Work reservation, two children")
            assertEquals(0L, run.inFlight.pending)
            assertEquals(0, assertNotNull(run.staticRequests).snapshot().sources)
            assertTrue(handlers.ports.all { it.staticNeedRouter == null && it.pendingNeedReplay == null })
        }
    }

    @Test fun `the last Start delivers its committed source without actor Work`() {
        val f = EngineShellFixtures()
        val field = field(f)
        var received = 0
        lateinit var run: RunManager<ShellActor>
        f.shared().use { shared ->
            val handlers = ShellHandlers({ port, event -> when (event) {
                is RunEvent.Start -> {
                    subscribe(port, event.root, field)
                    commit(port, event.root, field, 4)
                    assertEquals(0, received)
                }
                is RunEvent.MarkRequestsIn -> {
                    assertFalse(run.ended, "The parent cannot complete before its counted child")
                    assertEquals(setOf(4), event.rows.getValue(field).asIds())
                    received++
                }
                else -> error("Unexpected lifecycle event $event")
            } })
            run = RunManager(f.config(), shared, handlers)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertTrue(result.analyzers.isEmpty())
            assertEquals(1, received)
            assertEquals(3L, run.inFlight.handled)
            assertEquals(0L, run.inFlight.pending)
        }
    }

    @Test fun `two runners keep both source streams live until their counted children finish`() {
        val f = EngineShellFixtures()
        val field = field(f)
        val bothCommitted = CountDownLatch(2)
        val firstDelivered = CountDownLatch(1)
        val received = CopyOnWriteArrayList<MethodKey>()
        lateinit var run: RunManager<ShellActor>
        f.shared(threads = 2).use { shared ->
            val handlers = ShellHandlers({ port, event -> when (event) {
                is RunEvent.Start -> {
                    subscribe(port, event.root, field)
                    commit(port, event.root, field, if (event.root == f.a) 4 else 9)
                    bothCommitted.countDown()
                    assertTrue(bothCommitted.await(5, TimeUnit.SECONDS))
                    if (event.root == f.b) {
                        assertTrue(firstDelivered.await(5, TimeUnit.SECONDS))
                        assertEquals(setOf(9), assertNotNull(port.staticRequests)
                            .viewMarks(StaticDemandViewKey(f.b, field)).asIds())
                        assertNull(run.terminalStatus)
                    }
                }
                is RunEvent.MarkRequestsIn -> {
                    received += event.method
                    if (event.method == f.a) firstDelivered.countDown()
                }
                else -> error("Unexpected lifecycle event $event")
            } })
            run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, handlers)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(setOf(f.a, f.b), received.toSet())
            assertEquals(2, received.size)
            assertEquals(2, handlers.ports.size)
            assertEquals(0L, run.inFlight.pending)
        }
    }

    @Test fun `rejected callback or cancellation fails instead of completing and releases the directory`() {
        for (cancel in listOf(false, true)) {
            val f = EngineShellFixtures()
            val field = field(f)
            f.shared().use { shared ->
                val handlers = ShellHandlers({ port, event ->
                    val start = assertIs<RunEvent.Start>(event)
                    subscribe(port, start.root, field) {
                        if (cancel) f.cancellation.cancel()
                        false
                    }
                    commit(port, start.root, field, 4)
                })
                val run = RunManager(f.config(), shared, handlers)
                val result = run.run(5.seconds)
                assertEquals(RunStatus.FAILED, result.status)
                assertTrue(result.analyzers.isEmpty())
                assertEquals(0, assertNotNull(run.staticRequests).snapshot().sources)
                assertEquals(0, assertNotNull(run.staticRequests).snapshot().subscribers)
            }
        }
    }

    @Test fun `run2 has no static request service and run1 state is released only after joining`() {
        val f = EngineShellFixtures()
        val field = field(f)
        val sourceStorages = java.util.concurrent.ConcurrentHashMap<MethodKey, SummaryStorage>()
        f.shared().use { shared ->
            val firstHandlers = ShellHandlers({ port, event ->
                val start = assertIs<RunEvent.Start>(event)
                port.analyzer(start.root)
                sourceStorages[start.root] = port.summaryStorage(start.root)
                commit(port, start.root, field, 4)
                assertEquals(1, assertNotNull(port.staticRequests).snapshot().sources)
            }, make = { key, port -> ShellActor(key, onFreeze = {
                assertEquals(setOf(4), sourceStorages.getValue(key).borrowRequest(field).dynamic.asIds())
                assertEquals(1, assertNotNull(port.staticRequests).snapshot().sources,
                    "The source must remain available until all actors join")
            }) })
            val first = RunManager(f.config(index = 1), shared, firstHandlers)
            assertEquals(RunStatus.COMPLETE, first.run(5.seconds).status)
            assertEquals(0L, first.inFlight.pending)
            assertEquals(0, assertNotNull(first.staticRequests).snapshot().sources)

            lateinit var second: RunManager<ShellActor>
            val secondHandlers = ShellHandlers({ port, event ->
                val start = assertIs<RunEvent.Start>(event)
                assertNull(port.staticRequests)
                assertNull(port.staticNeedRouter)
                assertNull(port.pendingNeedReplay)
                val actor = port.analyzer(start.root)
                actor.add(3)
                assertTrue(port.enqueue(actor))
            }, make = { key, port -> ShellActor(key, onWork = { _, _ ->
                assertNull(port.staticRequests)
                assertFalse(second.ended)
                assertTrue(second.inFlight.pending >= 1L)
            }) })
            second = RunManager(f.config(index = 2, L = 2), shared, secondHandlers, quantum = 1)
            val result = second.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertNull(second.staticRequests)
            assertEquals(3L, result.analyzers.single().steps)
            assertEquals(3, result.analyzers.single().quanta.size)
            assertEquals(3L, second.inFlight.handled)
            assertEquals(0L, second.inFlight.pending)
        }
    }

    private fun field(f: EngineShellFixtures) =
        Run1Slot.StaticField(f.manager.accessors.index(ClassStaticAccessor("Box")), f.field("f"))

    private fun subscribe(port: RunnerPort<ShellActor>, method: MethodKey, field: Run1Slot.StaticField,
                          post: ((RunEvent.MarkRequestsIn) -> Boolean)? = null) {
        assertNotNull(port.staticRequests).subscribeView(StaticDemandViewKey(method, field), method) { marks ->
            val event = RunEvent.MarkRequestsIn(method, mapOf(field to marks))
            check(post?.invoke(event) ?: port.send(event)) { "A run rejected its counted static request child" }
        }
    }

    private fun commit(port: RunnerPort<ShellActor>, method: MethodKey, field: Run1Slot.StaticField, vararg ids: Int) {
        val storage = port.summaryStorage(method)
        val offered = RequestMarkSet.fromIds(ids)
        val temporary = RequestStorage()
        temporary.addAll(field, offered)
        assertNotNull(port.staticRequests)
        storage.commit(temporary)
    }
}

private fun RequestMarkSet.asIds(): Set<Int> = buildSet { forEachId { add(it) } }
