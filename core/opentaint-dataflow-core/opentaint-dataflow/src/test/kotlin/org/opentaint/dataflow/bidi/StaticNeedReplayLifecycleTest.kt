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

/** These controls run the actual UnitRunner and completion counter. They do not
 * rely on the deterministic fixture's turn wrapper to trigger a replay. */
class StaticNeedReplayLifecycleTest {
    @Test fun `incomplete Work flushes before requeue and final Work secures its child before completion`() {
        val f = EngineShellFixtures()
        val field = field(f)
        val received = CopyOnWriteArrayList<Set<Int>>()
        val subscribers = ArrayList<SubscriptionManager>()
        lateinit var run: RunManager<ShellActor>
        f.shared().use { shared ->
            val handlers = ShellHandlers({ port, event -> when (event) {
                is RunEvent.Start -> {
                    subscribers += installSubscriber(port, event.root)
                    val actor = port.analyzer(event.root)
                    actor.add(2)
                    assertTrue(port.enqueue(actor))
                }
                is RunEvent.StaticNeed -> {
                    assertFalse(run.ended)
                    assertTrue(run.inFlight.pending >= 1L)
                    received += event.visit.marks.asIds()
                    subscribers.single().onStaticNeed(event.visit)
                }
                else -> error("Unexpected lifecycle event $event")
            } }, make = { key, port -> ShellActor(key, onWork = { actor, _ ->
                val router = checkNotNull(port.staticNeedRouter)
                val pending = checkNotNull(port.pendingNeedReplay)
                if (actor.steps == 0L) {
                    pending.sourcesCommitted(router.commitSourceUnions(key,
                        mapOf(field to RequestMarkSet.of(TaintMark(4)))))
                    repeat(3) { pending.graphChanged() }
                    assertTrue(received.isEmpty(), "Replay must wait for the end of this quantum")
                } else {
                    assertEquals(listOf(setOf(4)), received.toList(), "The incomplete quantum must already have flushed")
                    pending.sourcesCommitted(router.commitSourceUnions(key,
                        mapOf(field to RequestMarkSet.of(TaintMark(9)))))
                }
            }) })
            run = RunManager(f.config(), shared, handlers, quantum = 1)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(listOf(setOf(4), setOf(9)), received.toList())
            assertEquals(2L, result.analyzers.single().steps)
            assertEquals(5L, run.inFlight.handled, "Root batch, Start, one retained Work reservation, two visits")
            assertEquals(0L, run.inFlight.pending)
            assertTrue(handlers.ports.all { checkNotNull(it.pendingNeedReplay).isEmpty() })
        }
        subscribers.forEach(SubscriptionManager::freeze)
    }

    @Test fun `the last ordinary event flushes its source even without actor Work`() {
        val f = EngineShellFixtures()
        val field = field(f)
        var received = 0
        val subscribers = ArrayList<SubscriptionManager>()
        lateinit var run: RunManager<ShellActor>
        f.shared().use { shared ->
            val handlers = ShellHandlers({ port, event -> when (event) {
                is RunEvent.Start -> {
                    subscribers += installSubscriber(port, event.root)
                    val router = checkNotNull(port.staticNeedRouter)
                    checkNotNull(port.pendingNeedReplay).sourcesCommitted(router.commitSourceUnions(event.root,
                        mapOf(field to RequestMarkSet.of(TaintMark(4)))))
                    assertEquals(0, received)
                }
                is RunEvent.StaticNeed -> {
                    assertFalse(run.ended, "The parent cannot complete before this counted child")
                    received++
                    subscribers.single().onStaticNeed(event.visit)
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
        subscribers.forEach(SubscriptionManager::freeze)
    }

    @Test fun `two live runners retain their own source deltas when one flushes first`() {
        val f = EngineShellFixtures()
        val field = field(f)
        val bothCommitted = CountDownLatch(2)
        val firstDelivered = CountDownLatch(1)
        val received = CopyOnWriteArrayList<StaticNeedKey>()
        val subscribers = CopyOnWriteArrayList<SubscriptionManager>()
        val byMethod = java.util.concurrent.ConcurrentHashMap<MethodKey, SubscriptionManager>()
        lateinit var run: RunManager<ShellActor>
        f.shared(threads = 2).use { shared ->
            val handlers = ShellHandlers({ port, event -> when (event) {
                is RunEvent.Start -> {
                    val subscriber = installSubscriber(port, event.root)
                    subscribers += subscriber
                    byMethod[event.root] = subscriber
                    val router = checkNotNull(port.staticNeedRouter)
                    val pending = checkNotNull(port.pendingNeedReplay)
                    pending.sourcesCommitted(router.commitSourceUnions(event.root,
                        mapOf(field to RequestMarkSet.of(TaintMark(if (event.root == f.a) 4 else 9)))))
                    bothCommitted.countDown()
                    assertTrue(bothCommitted.await(5, TimeUnit.SECONDS))
                    if (event.root == f.b) {
                        assertTrue(firstDelivered.await(5, TimeUnit.SECONDS))
                        assertFalse(pending.isEmpty(), "Another runner's flush cannot take this owner's delta")
                        assertNull(run.terminalStatus)
                    }
                }
                is RunEvent.StaticNeed -> {
                    received += event.visit.key
                    if (event.visit.key.method == f.a) firstDelivered.countDown()
                    byMethod.getValue(event.visit.key.method).onStaticNeed(event.visit)
                }
                else -> error("Unexpected lifecycle event $event")
            } })
            run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, handlers)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(setOf(StaticNeedKey(f.a, field), StaticNeedKey(f.b, field)), received.toSet())
            assertEquals(2, received.size)
            assertEquals(2, handlers.ports.size)
            assertEquals(0L, run.inFlight.pending)
        }
        subscribers.forEach(SubscriptionManager::freeze)
    }

    @Test fun `rejected child and cancellation during flush fail instead of completing and release pending state`() {
        for (cancel in listOf(false, true)) {
            val f = EngineShellFixtures()
            val field = field(f)
            val subscribers = ArrayList<SubscriptionManager>()
            f.shared().use { shared ->
                val handlers = ShellHandlers({ port, event ->
                    val start = assertIs<RunEvent.Start>(event)
                    subscribers += installSubscriber(port, start.root) {
                        if (cancel) f.cancellation.cancel()
                        false
                    }
                    val router = checkNotNull(port.staticNeedRouter)
                    checkNotNull(port.pendingNeedReplay).sourcesCommitted(router.commitSourceUnions(start.root,
                        mapOf(field to RequestMarkSet.of(TaintMark(4)))))
                })
                val run = RunManager(f.config(), shared, handlers)
                val result = run.run(5.seconds)
                assertEquals(RunStatus.FAILED, result.status)
                assertTrue(result.analyzers.isEmpty())
                assertTrue(handlers.ports.all { checkNotNull(it.pendingNeedReplay).isEmpty() })
                assertTrue(checkNotNull(run.staticNeedRouter).sourcesSnapshot().isEmpty())
            }
            subscribers.forEach(SubscriptionManager::freeze)
        }
    }

    @Test fun `run2 executes work with no run1 router or pending replay on the same shared objects`() {
        val f = EngineShellFixtures()
        val field = field(f)
        f.shared().use { shared ->
            val firstHandlers = ShellHandlers({ port, event ->
                val start = assertIs<RunEvent.Start>(event)
                port.analyzer(start.root)
                val router = assertNotNull(port.staticNeedRouter)
                val pending = assertNotNull(port.pendingNeedReplay)
                pending.sourcesCommitted(router.commitSourceUnions(start.root,
                    mapOf(field to RequestMarkSet.of(TaintMark(4)))))
                assertFalse(pending.isEmpty())
                // No subscribers: the accepted Start still has to flush its batch.
            }, make = { key, port -> ShellActor(key, onFreeze = {
                assertTrue(assertNotNull(port.pendingNeedReplay).isEmpty(),
                    "The turn flushed before joined cleanup can clear its accumulator")
                assertEquals(setOf(4), assertNotNull(port.staticNeedRouter).sourcesSnapshot()
                    .getValue(StaticNeedKey(key, field)).asIds())
            }) })
            val first = RunManager(f.config(index = 1), shared, firstHandlers)
            assertEquals(RunStatus.COMPLETE, first.run(5.seconds).status)
            assertEquals(0L, first.inFlight.pending)
            assertTrue(assertNotNull(first.staticNeedRouter).sourcesSnapshot().isEmpty())
            assertTrue(assertNotNull(firstHandlers.ports.single().pendingNeedReplay).isEmpty())

            lateinit var second: RunManager<ShellActor>
            val secondHandlers = ShellHandlers({ port, event ->
                val start = assertIs<RunEvent.Start>(event)
                assertNull(port.staticNeedRouter)
                assertNull(port.pendingNeedReplay)
                val actor = port.analyzer(start.root)
                actor.add(3)
                assertTrue(port.enqueue(actor))
            }, make = { key, port -> ShellActor(key, onWork = { _, _ ->
                assertNull(port.staticNeedRouter)
                assertNull(port.pendingNeedReplay)
                assertFalse(second.ended)
                assertTrue(second.inFlight.pending >= 1L)
            }) })
            second = RunManager(f.config(index = 2, L = 2), shared, secondHandlers, quantum = 1)
            val result = second.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertNull(second.staticNeedRouter)
            assertEquals(3L, result.analyzers.single().steps)
            assertEquals(3, result.analyzers.single().quanta.size)
            assertEquals(3L, second.inFlight.handled, "Root batch, Start and one retained Work reservation")
            assertEquals(0L, second.inFlight.pending)
            assertTrue(assertNotNull(firstHandlers.ports.single().pendingNeedReplay).isEmpty())
        }
    }

    private fun field(f: EngineShellFixtures): Run1Slot.StaticField =
        Run1Slot.StaticField(f.manager.accessors.index(ClassStaticAccessor("Box")), f.field("f"))

    private fun installSubscriber(port: RunnerPort<ShellActor>, key: MethodKey,
                                  post: ((RunEvent.StaticNeed) -> Boolean)? = null): SubscriptionManager {
        val subscriber = SubscriptionManager(object : SubscriptionPort {
            override val config get() = port.config
            override val ops get() = port.ops
            override val steps get() = port.steps
            override val staticNeedRouter get() = port.staticNeedRouter
            override val pendingNeedReplay get() = port.pendingNeedReplay
            override fun summaryStorage(key: MethodKey): SummaryStorage = port.summaryStorage(key)
            override fun applier(key: MethodKey): SummaryApplier = error("No summary application in shell control")
            override fun post(event: RunEvent.Delivery): Boolean = port.post(event)
            override fun post(event: RunEvent.StaticNeed): Boolean = post?.invoke(event) ?: port.post(event)
            override fun send(event: RunEvent): Boolean = port.send(event)
        })
        port.summaryStorage(key).addSubscriber(subscriber)
        return subscriber
    }
}

private fun RequestMarkSet.asIds(): Set<Int> = buildSet { forEachId { add(it) } }
