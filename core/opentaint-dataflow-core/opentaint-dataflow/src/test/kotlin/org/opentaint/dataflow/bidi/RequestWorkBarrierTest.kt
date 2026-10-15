package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.util.Cancellation
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** These controls use actual actor P, method work and synchronous SummaryStorage commit. */
class RequestWorkBarrierTest {
    private val slot = Run1Slot.Base(AccessPathBase.Argument(0))
    private fun actor(f: MethodAnalysisFixtures) =
        f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))

    @Test fun `request-only actor schedules real Work and cannot freeze before its method-empty commit`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = actor(f)
            val marks = (0 until 3).map { f.mark("T$it") }
            marks.forEach { actor.addMarkRequest(slot.initial(f.manager), it) }
            assertTrue(actor in f.enqueued)
            assertEquals(0L, actor.steps)
            assertTrue(f.requestRow(actor, slot).isEmpty)
            assertFails { actor.freeze() }
            assertTrue(actor.work(1), "With no facts, the method-empty boundary commits the entire merged P")
            assertEquals(3, f.requestRow(actor, slot).size)
            assertEquals(0L, actor.steps, "Request commits are not fact steps")
            actor.freeze()
        }
    }

    @Test fun `late answer stays queued after commit and schedules fact work only when delivered`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = actor(f)
            val t = f.mark("T")
            val input = f.fact(mark = MarkSlot.Concrete(t))
            val ref = CallerRef(f.store.key("supplier"), f.manager.zero, Layer.NORMAL, f.inst(1))
            f.admit(actor, ref, Layer.NORMAL, input); f.drain(actor)
            val before = actor.steps
            actor.addMarkRequest(slot.initial(f.manager), t)
            assertTrue(actor.work(1))
            val answer = f.manager.initial(slot.base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertFalse(answer in actor.initials.all())
            assertTrue(f.pumpQuestions(), "Commit secures queued replay/answer ownership before its Work ends")
            assertTrue(answer in actor.initials.all())
            f.drain(actor, quantum = 1)
            assertTrue(actor.steps > before)
        }
    }

    @Test fun `a finite fact quantum does not submit P while the method fact queue is busy`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = actor(f)
            val t = f.mark("T")
            actor.addInitial(f.manager.initial(slot.base, null, Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(t)))
            actor.addMarkRequest(slot.initial(f.manager), t)
            assertFalse(actor.work(1))
            assertEquals(1L, actor.steps)
            assertTrue(f.requestRow(actor, slot).isEmpty, "There is no extra quantum/backstop request barrier")
            f.drain(actor, quantum = 1)
            assertTrue(t in f.requestRow(actor, slot))
        }
    }

    @Test fun `zero work retains priority and requests commit only after that method drains`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = actor(f)
            val t = f.mark("T")
            actor.addRootZero()
            actor.addMarkRequest(slot.initial(f.manager), t)
            assertFalse(actor.work(1))
            assertTrue(actor.hasZeroWork)
            assertTrue(f.requestRow(actor, slot).isEmpty)
            f.drain(actor, quantum = 1)
            assertTrue(t in f.requestRow(actor, slot))
        }
    }

    @Test fun `cancellation after commit queues a notification but never certifies complete`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = actor(f)
            val initial = slot.initial(f.manager)
            val ref = CallerRef(f.store.key("caller"), initial, Layer.NORMAL, f.inst(1))
            f.admit(actor, ref, Layer.NORMAL, f.fact(tail = Tail.STAR, mark = MarkSlot.STAR))
            f.drain(actor)
            val t = f.mark("T")
            f.onRequestNotification = { f.cancellation.cancel() }
            actor.addMarkRequest(initial, t)
            assertFailsWith<Cancellation.Cancelled> { actor.work(1) }
            f.cancellation.activate()
            assertTrue(t in f.requestRow(actor, slot), "An installed monotone admission is not rolled back on cancellation")
        }
    }

    @Test fun `last remote batch reserves request-only Work before its delivery reservation ends`(): Unit {
        val f = EngineShellFixtures()
        f.shared(threads = 2).use { shared ->
            lateinit var run: RunManager<RunMethodAnalyzer>
            val enqueues = AtomicInteger()
            val handlers = RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
                MethodCallServicesFactory(::RunMethodCallServices))
            val factory = RunnerHandlerFactory<RunMethodAnalyzer> { owner ->
                val observed = object : RunnerPort<RunMethodAnalyzer> by owner {
                    override fun enqueue(analyzer: RunMethodAnalyzer): Boolean {
                        if (analyzer.key == f.b) {
                            assertNull(run.terminalStatus)
                            assertTrue(run.inFlight.pending > 0)
                            enqueues.incrementAndGet()
                        }
                        return owner.enqueue(analyzer)
                    }
                }
                val delegate = handlers.create(observed)
                object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                    override fun createActor(key: MethodKey) = delegate.createActor(key)
                    override fun subscribe(sub: Subscription) = (delegate as SubscriptionHandler).subscribe(sub)
                    override fun freeze() = (delegate as SubscriptionHandler).freeze()
                    override fun handle(event: RunEvent) {
                        if (event is RunEvent.Start) {
                            assertTrue(observed.send(RunEvent.MarkRequestsIn(f.b, mapOf(slot to
                                RequestMarkSet.fromIds(intArrayOf(f.manager.marks.mark("T").id,
                                    f.manager.marks.mark("U").id))))))
                        } else delegate.handle(event)
                    }
                }
            }
            run = RunManager(f.config(), shared, factory, quantum = 1)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertTrue(enqueues.get() > 0)
            assertEquals(0L, run.inFlight.pending)
            assertEquals(0L, result.analyzers.single { it.key == f.b }.steps)
            assertEquals(4L, run.inFlight.handled, "Root reservation, Start, remote batch and request-only Work")
        }
    }
}
