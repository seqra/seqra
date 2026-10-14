package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class RequestWorkBarrierTest {
    /** A finite deferred service isolates the Work contract from the set-join cursor. */
    private class Deferred(private val core: RunMethodAnalyzer) : MethodLocalServices by RunMethodLocalServices(core) {
        var remaining = 0
        var flushes = 0
        var answer: InitialAp? = null
        var duringFlush: (() -> Unit)? = null
        override val hasPendingRequests get() = remaining > 0
        override fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
            remaining++
            core.requestWork()
        }
        override fun flushRequests(quantum: Int): Boolean {
            require(quantum > 0)
            if (remaining == 0) return true
            flushes++
            remaining = (remaining - quantum).coerceAtLeast(0)
            answer?.let { answer = null; core.addInitial(it) }
            duringFlush?.invoke()
            return remaining == 0
        }
    }

    @Test fun `a request-only actor is queued and partial request work cannot finish`(): Unit {
        MethodAnalysisFixtures().use { f ->
            lateinit var service: Deferred
            val actor = f.core(localFactory = MethodLocalServicesFactory { Deferred(it).also { service = it } })
            val policy = f.manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            repeat(3) { actor.addMarkRequest(policy, f.mark("T$it")) }
            assertTrue(actor in f.enqueued)
            assertEquals(0L, actor.steps)
            assertFails { actor.freeze() }
            assertFalse(actor.work(1))
            assertFalse(actor.work(1))
            assertTrue(actor.work(1))
            assertEquals(3, service.flushes)
            assertEquals(0L, actor.steps, "Request scans are not fact steps")
            actor.freeze()
        }
    }

    @Test fun `request answer facts keep the current Work token live through the zero phase transition`(): Unit {
        MethodAnalysisFixtures().use { f ->
            lateinit var service: Deferred
            val actor = f.core(localFactory = MethodLocalServicesFactory { Deferred(it).also { service = it } })
            val policy = f.manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            service.answer = f.manager.zero
            actor.addMarkRequest(policy, f.mark("T"))
            assertFalse(actor.work(1), "The answer added zero work after the phase's completion")
            assertTrue(actor.hasZeroWork)
            assertEquals(0L, actor.steps)
            f.drain(actor, quantum = 1)
            assertTrue(actor.steps > 0)
            assertTrue(f.publications.isNotEmpty())
        }
    }

    @Test fun `nonzero quantum services requests while the method fact queue remains busy`(): Unit {
        MethodAnalysisFixtures().use { f ->
            lateinit var service: Deferred
            val actor = f.core(localFactory = MethodLocalServicesFactory { Deferred(it).also { service = it } })
            val policy = f.manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            val concrete = f.manager.initial(AccessPathBase.Argument(0), null, Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(f.mark("T")))
            actor.addInitial(concrete)
            actor.addMarkRequest(policy, f.mark("T"))
            assertFalse(actor.work(1))
            assertEquals(1, service.flushes)
            assertEquals(1L, actor.steps)
            assertFalse(service.hasPendingRequests)
            f.drain(actor, quantum = 1)
        }
    }

    @Test fun `zero quantum boundary preserves zero priority before request service`(): Unit {
        MethodAnalysisFixtures().use { f ->
            lateinit var service: Deferred
            val actor = f.core(localFactory = MethodLocalServicesFactory { Deferred(it).also { service = it } })
            actor.addRootZero()
            val policy = f.manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            actor.addMarkRequest(policy, f.mark("T"))
            assertFalse(actor.work(1))
            assertEquals(0, service.flushes)
            assertTrue(actor.hasZeroWork)
            f.drain(actor, quantum = 1)
            assertEquals(1, service.flushes)
        }
    }

    @Test fun `cancellation at the last request callback cannot return complete`(): Unit {
        MethodAnalysisFixtures().use { f ->
            lateinit var service: Deferred
            val actor = f.core(localFactory = MethodLocalServicesFactory { Deferred(it).also { service = it } })
            val policy = f.manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            actor.addMarkRequest(policy, f.mark("T"))
            service.duringFlush = { f.cancellation.cancel() }
            assertFailsWith<Cancellation.Cancelled> { actor.work(1) }
        }
    }

    @Test fun `last remote batch schedules request-only Work before its reservation ends`(): Unit {
        val f = EngineShellFixtures()
        f.shared(threads = 2).use { shared ->
            lateinit var run: RunManager<RunMethodAnalyzer>
            val policy = f.manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            var requestServices = 0
            val handlers = RunMethodHandlers(MethodLocalServicesFactory { actor ->
                val actual = RunMethodLocalServices(actor)
                object : MethodLocalServices by actual {
                    override fun flushRequests(quantum: Int): Boolean {
                        if (actor.key == f.b && actual.hasPendingRequests) {
                            assertNull(run.terminalStatus)
                            assertTrue(run.inFlight.pending > 0)
                            requestServices++
                        }
                        return actual.flushRequests(quantum)
                    }
                }
            }, MethodCallServicesFactory(::RunMethodCallServices))
            val factory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
                val delegate = handlers.create(port)
                object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                    override fun createActor(key: MethodKey) = delegate.createActor(key)
                    override fun subscribe(sub: Subscription) = (delegate as SubscriptionHandler).subscribe(sub)
                    override fun freeze() = (delegate as SubscriptionHandler).freeze()
                    override fun handle(event: RunEvent) {
                        if (event is RunEvent.Start) {
                            assertTrue(port.send(RunEvent.MarkRequestsIn(f.b, policy,
                                RequestMarkSet.fromIds(intArrayOf(f.manager.marks.mark("T").id,
                                    f.manager.marks.mark("U").id)))))
                        } else delegate.handle(event)
                    }
                }
            }
            run = RunManager(f.config(), shared, factory, quantum = 1)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertTrue(requestServices > 0)
            assertEquals(0L, run.inFlight.pending)
            assertEquals(0L, result.analyzers.single { it.key == f.b }.steps)
            assertEquals(4L, run.inFlight.handled, "Root reservation, Start, remote batch and request-only Work")
        }
    }
}
