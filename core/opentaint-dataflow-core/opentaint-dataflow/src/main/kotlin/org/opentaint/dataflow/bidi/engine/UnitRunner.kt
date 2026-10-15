package org.opentaint.dataflow.bidi.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import mu.KLogging
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.Facts
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.util.Cancellation
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** One coroutine owns the actors, local queue and queued-work set of a unit. */
class UnitRunner<A : RunActor> internal constructor(
    val unit: UnitType,
    private val run: RunManager<A>,
    factory: RunnerHandlerFactory<A>,
    private val quantum: Int,
) : RunnerPort<A> {
    private val channel = Channel<RunEvent>(Channel.UNLIMITED)
    private val queue = EventQueue()
    private val actors = LinkedHashMap<MethodKey, A>()
    private val queued = Collections.newSetFromMap(IdentityHashMap<A, Boolean>())
    private val closed = AtomicBoolean()
    private val handler = factory.create(this)

    override val config get() = run.config
    override val shared get() = run.shared
    override val forms get() = run.forms
    override val ops get() = shared.ops
    override val steps get() = run.steps

    internal fun postEvent(event: RunEvent): Boolean = run.accept {
        !closed.get() && channel.trySend(event).isSuccess
    }

    override fun post(event: RunEvent.Delivery): Boolean = postEvent(event)
    override fun post(event: RunEvent.QuestionDelivery): Boolean = postEvent(event)
    override fun send(event: RunEvent): Boolean = run.route(event)

    override fun summaryStorage(key: MethodKey): SummaryStorage = run.summaryStorage(key)

    override fun subscribe(sub: Subscription) {
        check(!run.ended && !closed.get()) { "The runner is closed" }
        shared.cancellation.checkpoint()
        val subscriptions = handler as? SubscriptionHandler
            ?: error("The runner handler does not support method subscriptions")
        subscriptions.subscribe(sub)
    }

    override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts) {
        check(!run.ended && !closed.get()) { "The runner is closed" }
        shared.cancellation.checkpoint()
        val event = RunEvent.LinkIn(callee, ref, linkLayer, added)
        if (shared.unitResolver.resolve(callee.method) == unit) handler.handle(event)
        else send(event)
    }

    override fun analyzer(key: MethodKey): A {
        check(!run.ended && !closed.get()) { "The runner is closed" }
        check(shared.unitResolver.resolve(key.method) == unit) { "$key is not in $unit" }
        return actors.getOrPut(key) {
            handler.createActor(key).also { check(it.key == key) { "The actor has a different method key" } }
        }
    }

    override fun enqueue(analyzer: A): Boolean {
        if (run.ended || closed.get()) return false
        check(actors[analyzer.key] === analyzer) { "Work requires this runner's exact actor" }
        if (analyzer in queued) return false
        return run.accept {
            if (closed.get()) false
            else {
                check(queued.add(analyzer))
                try { queue.add(RunEvent.Work(analyzer)) }
                catch (e: Throwable) { queued.remove(analyzer); throw e }
                true
            }
        }
    }

    internal suspend fun runLoop() {
        try {
            coroutineScope {
                var events = 0
                while (isActive && !run.ended) {
                    shared.cancellation.checkpoint()
                    if (queue.isEmpty()) queue.add(channel.receive())
                    while (true) queue.add(channel.tryReceive().getOrNull() ?: break)
                    if (run.ended) break
                    val event = checkNotNull(queue.poll())
                    val complete = if (event is RunEvent.Work) {
                        check(actors[event.analyzer.key] === event.analyzer) { "Work belongs to another runner" }
                        event.analyzer.work(quantum)
                    } else {
                        handler.handle(event)
                        true
                    }
                    if (complete) {
                        if (event is RunEvent.Work) {
                            check(queued.remove(event.analyzer)) { "Work was not queued" }
                        }
                        run.afterHandler()
                    } else {
                        // The same reservation remains live; priority is sampled again.
                        queue.add(event)
                    }
                    if (event is RunEvent.Work || ++events >= quantum) {
                        events = 0
                        yield()
                    }
                }
            }
        } catch (e: Cancellation.Cancelled) {
            if (!run.ended) run.fail(RunStatus.FAILED)
        } catch (e: CancellationException) {
            if (!run.ended) run.fail(RunStatus.FAILED)
        } catch (e: Throwable) {
            logger.error(e) { "Runner $unit failed in run ${config.index}" }
            run.fail(RunStatus.FAILED)
        }
    }

    fun close() {
        closed.set(true)
        channel.cancel()
    }

    /** The manager reads and releases these collections only after a successful join. */
    internal fun analyzers(): List<A> = actors.values.toList()
    internal fun release() {
        try {
            (handler as? SubscriptionHandler)?.freeze()
        } finally {
            queue.clear()
            queued.clear()
            for (actor in actors.values) (actor as? RunMethodAnalyzer)?.releaseAbstraction()
            actors.clear()
        }
    }

    companion object : KLogging()
}
