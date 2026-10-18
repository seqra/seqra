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
    val staticEntryObservations = ArrayList<Pair<MethodKey, FlowTree>>()
    val initialObservations = ArrayList<Pair<MethodKey, InitialAp>>()
    val initials = linkedSetOf<Pair<MethodKey, InitialAp>>()
    val climbs = linkedSetOf<Triple<MethodKey, Run1Slot, Int>>()
    val bases = LinkedHashMap<MethodKey, RequestRows>()
    val profiles = LinkedHashMap<MethodKey, Run1StaticProfile>()
    val frozenBases = LinkedHashMap<MethodKey, FrozenRequestBase>()
    private val stores = LinkedHashMap<MethodKey, SummaryStorage>()
    private val temporary = LinkedHashMap<MethodKey, RequestStorage>()
    private val sourceKeys = LinkedHashSet<StaticNeedKey>()
    var staticRequests = CallerOwnedStaticRequests(f.ap, FixedPrescanReachability.empty(f.ap.cancellation::checkpoint))
    fun needs(method: MethodKey, slot: Run1Slot.StaticField): RequestMarkSet =
        storage(method).borrowRequest(slot).dynamic
    fun sourceNeeds(): Map<StaticNeedKey, RequestMarkSet> = sourceKeys.associateWith {
        storage(it.method).borrowRequest(it.slot).dynamic
    }.filterValues { !it.isEmpty }
    private var submitted = false
    var acceptSends = true
    var acceptNotifications = true
    var cancelOnSend = false
    var rejectAnswerSends = false
    var cancelOnAnswerSend = false
    var onAnswer: (() -> Unit)? = null

    /** This fixture has identity forms. Declare its finite input field domain before emission. */
    fun declareIdentityStaticDomain(vararg fields: Run1Slot.StaticField, prescanCall: Boolean = false) {
        check(!submitted)
        check(stores.isEmpty()) { "Declare the immutable prescan graph before creating stores" }
        val catalog = Run1StaticProfiles.build(f.ap,
            mapOf(f.callee.method to if (prescanCall) listOf(f.callee.method) else emptyList()),
            mapOf(f.callee.method to Run1StaticLocalProfile.EMPTY),
            declaredFieldUniverse = fields.toSet(), declaredClassUniverse = fields.map { it.cls }.toSet())
        staticRequests.clear()
        staticRequests = CallerOwnedStaticRequests(f.ap, catalog.reachability)
        val profile = catalog[f.callee.method]
        profiles[f.callee] = profile
        profiles[f.caller] = profile
    }

    fun storage(method: MethodKey = f.callee): SummaryStorage = stores.getOrPut(method) {
        SummaryStorage(method, f.ap, f.ops, steps,
            frozenBases[method] ?: MapFrozenRequestBase(bases[method].orEmpty()),
            staticRequests = staticRequests)
    }

    private val port = object : SubscriptionPort by f.port {
        override val abstractionStatistics get() = this@Run1RequestPipelineFixtures.statistics
        override val requestBatchStatistics get() = batchStatistics
        override val staticRequests get() = this@Run1RequestPipelineFixtures.staticRequests
        override fun staticProfile(method: org.opentaint.ir.api.common.CommonMethod): Run1StaticProfile? =
            profiles.entries.firstOrNull { it.key.method == method }?.value
        override fun post(event: RunEvent.CallsiteRequests): Boolean {
            if (!acceptNotifications) return false
            events += event
            return true
        }
        override fun summaryStorage(key: MethodKey): SummaryStorage = storage(key)
        override fun post(event: RunEvent.Delivery): Boolean {
            if (!acceptNotifications) return false
            events += event
            return true
        }
        override fun post(event: RunEvent.ChangedRequests): Boolean {
            if (!acceptNotifications) return false
            events += event
            return true
        }
        override fun send(event: RunEvent): Boolean {
            if (cancelOnSend || (cancelOnAnswerSend && event is RunEvent.InitialAnswersIn)) f.ap.cancellation.cancel()
            if (rejectAnswerSends && event is RunEvent.InitialAnswersIn) return false
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

    private fun observeStatic(method: MethodKey, entries: FlowTree) {
        staticEntryObservations += method to entries
        StaticIdentityEntries(f.ops).originals(entries).forEach { observe(method, it) }
    }

    fun collect(method: MethodKey, slot: Run1Slot, marks: RequestMarkSelection) {
        if (slot is Run1Slot.StaticField) sourceKeys += StaticNeedKey(method, slot)
        val fresh = marks.minus(storage(method).borrowRequest(slot), f.ap.cancellation::checkpoint)
        if (!fresh.isEmpty) temporary.getOrPut(method, ::RequestStorage).addAll(slot, fresh)
    }

    /** The test explicitly chooses the method-empty barrier, not an alternate scheduler. */
    fun commit(method: MethodKey = f.callee) {
        temporary.remove(method)?.let { storage(method).commit(it) }
    }

    fun publish(slot: Run1Slot, vararg ids: Int, method: MethodKey = f.callee) {
        collect(method, slot, RequestMarkSet.fromIds(ids))
        commit(method)
    }

    /** Caller admission owns starts; no raw supplier is sent to a callee emitter. */
    fun submit(part: Subscription) {
        submitted = true
        subscriptions.subscribe(part)
    }

    fun submitStatic(part: StaticSubscription) {
        // SubscriptionManager queues the destination batch itself, after registration.
        submitted = true
        subscriptions.subscribeStatic(part)
    }

    fun drain() {
        var count = 0
        while (events.isNotEmpty()) {
            check(++count < 100_000) { "Finite request pipeline did not close" }
            when (val event = events.removeFirst()) {
                is RunEvent.ChangedRequests -> subscriptions.onRequests(event.callee, event.rows)
                is RunEvent.CallsiteRequests -> subscriptions.onCallsiteRequests(event.key, event.rows)
                is RunEvent.RequestDrain -> subscriptions.drainRequests(event.caller)
                is RunEvent.PreparedInitialsIn -> {
                    event.prepared.initials.forEach { observe(event.callee, it) }
                    event.prepared.staticEntries?.let { observeStatic(event.callee, it) }
                }
                is RunEvent.StaticEntriesIn -> observeStatic(event.callee, event.entries)
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
        subscriptions.freeze()
        staticRequests.clear()
        f.close()
    }
}
