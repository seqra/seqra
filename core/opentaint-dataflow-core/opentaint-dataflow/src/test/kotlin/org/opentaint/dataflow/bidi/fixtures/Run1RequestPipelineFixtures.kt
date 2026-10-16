package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.store.RequestMarkSelection
import org.opentaint.dataflow.bidi.store.RequestMarkSet

/** Test-owned scheduling only. Storage, caller replay and input emission are production code. */
internal class Run1RequestPipelineFixtures(val steps: ProtocolSteps = ProtocolSteps.Inline) : AutoCloseable {
    val f = PipelineFixtures(limit = 1, protocol = steps)
    val statistics = Run1AbstractionStatistics()
    val batchStatistics = RequestBatchStatistics()
    val events = ArrayDeque<RunEvent>()
    val sent = ArrayList<RunEvent>()
    val initialObservations = ArrayList<Pair<MethodKey, InitialAp>>()
    val initials = linkedSetOf<Pair<MethodKey, InitialAp>>()
    val climbs = linkedSetOf<Triple<MethodKey, Run1Slot, Int>>()
    val bases = LinkedHashMap<MethodKey, RequestRows>()
    val profiles = LinkedHashMap<MethodKey, Run1StaticProfile>()
    val frozenBases = LinkedHashMap<MethodKey, FrozenRequestBase>()
    private val stores = LinkedHashMap<MethodKey, SummaryStorage>()
    private val temporary = LinkedHashMap<MethodKey, RequestStorage>()
    private val emitters = LinkedHashMap<MethodKey, Run1Abstraction>()
    var acceptSends = true
    var acceptNotifications = true
    var cancelOnSend = false
    var onAnswer: (() -> Unit)? = null

    /** This fixture has identity forms. Declare its finite input field domain before emission. */
    fun declareIdentityStaticDomain(vararg fields: Run1Slot.StaticField) {
        check(emitters.isEmpty())
        val profile = Run1StaticProfiles.build(f.ap, mapOf(f.callee.method to emptyList()),
            mapOf(f.callee.method to Run1StaticLocalProfile.EMPTY),
            declaredFieldUniverse = fields.toSet(), declaredClassUniverse = fields.map { it.cls }.toSet())[f.callee.method]
        profiles[f.callee] = profile
        profiles[f.caller] = profile
    }

    fun storage(method: MethodKey = f.callee): SummaryStorage = stores.getOrPut(method) {
        SummaryStorage(method, f.ap, f.ops, steps, frozenBases[method] ?: MapFrozenRequestBase(bases[method].orEmpty()))
    }

    private val port = object : SubscriptionPort by f.port {
        override val abstractionStatistics get() = this@Run1RequestPipelineFixtures.statistics
        override val requestBatchStatistics get() = batchStatistics
        override fun summaryStorage(key: MethodKey): SummaryStorage = storage(key)
        override fun post(event: RunEvent.ChangedRequests): Boolean {
            if (!acceptNotifications) return false
            events += event
            return true
        }
        override fun send(event: RunEvent): Boolean {
            if (cancelOnSend) f.ap.cancellation.cancel()
            if (!acceptSends) return false
            sent += event
            events += event
            return true
        }
    }
    val subscriptions = SubscriptionManager(port)

    private fun observe(method: MethodKey, initial: InitialAp) {
        initialObservations += method to initial
        initials += method to initial
    }

    private fun emitter(method: MethodKey): Run1Abstraction = emitters.getOrPut(method) {
        Run1Abstraction(f.ops, profiles[method], { observe(method, it) },
            storage(method)::borrowMatchingRequests, { slot, marks -> collect(method, slot, marks) })
    }

    fun collect(method: MethodKey, slot: Run1Slot, marks: RequestMarkSelection) {
        val fresh = marks.minus(storage(method).borrowRequest(slot), f.ap.cancellation::checkpoint)
        if (!fresh.isEmpty) temporary.getOrPut(method, ::RequestStorage).addAll(slot, fresh)
    }

    /** The test explicitly chooses the method-empty barrier, not an alternate scheduler. */
    fun commit(method: MethodKey = f.callee) {
        val pending = temporary[method] ?: return
        storage(method).commit(pending)
        temporary[method] = RequestStorage()
    }

    fun publish(slot: Run1Slot, vararg ids: Int, method: MethodKey = f.callee) {
        collect(method, slot, RequestMarkSet.fromIds(ids))
        commit(method)
    }

    /** Real caller registration/admission precedes its queued callee LinkIn. */
    fun submit(part: Subscription) {
        subscriptions.subscribe(part)
        events += RunEvent.LinkIn(part.callee, part.ref, part.linkLayer, part.added)
    }

    fun drain() {
        var count = 0
        while (events.isNotEmpty()) {
            check(++count < 100_000) { "Finite request pipeline did not close" }
            when (val event = events.removeFirst()) {
                is RunEvent.ChangedRequests -> subscriptions.onRequests(event.callee, event.rows)
                is RunEvent.LinkIn -> emitter(event.callee).emit(event.ref, event.added)
                is RunEvent.InitialAnswersIn -> {
                    event.answers.forEach { observe(event.method, it) }
                    onAnswer?.invoke()
                }
                is RunEvent.MarkRequestsIn -> for ((slot, marks) in event.rows) {
                    marks.forEachId { climbs += Triple(event.method, slot, it) }
                    collect(event.method, slot, marks)
                }
                is RunEvent.Delivery -> subscriptions.onDelivery(event.callee, event.publications)
                else -> error("Unexpected request pipeline event: $event")
            }
        }
    }

    override fun close() {
        emitters.values.forEach(Run1Abstraction::release)
        subscriptions.freeze()
        f.close()
    }
}
