package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.CommonMethod

/** Protocol timing remains test-owned; all indexing and matching use production code. */
class PipelineFixtures(index: Int = 1, limit: Int = 3,
                       val protocol: ProtocolSteps = ProtocolSteps.Inline) : AutoCloseable {
    val method = MethodAnalysisFixtures()
    val store = method.store
    val ap = method.manager
    val ops = method.ops
    val caller = method.key
    val callee = store.key(context = "callee")
    val records = method.records
    val config = method.config(index = index, L = limit)
    val storages = LinkedHashMap<MethodKey, SummaryStorage>()
    val staticFootprints = LinkedHashMap<CommonMethod, Run1StaticFootprint>()
    val deliveries = ArrayList<RunEvent.Delivery>()
    val protocolEvents = ArrayDeque<RunEvent>()
    val staticEntries = ArrayList<RunEvent.StaticEntriesIn>()
    val initialAnswers = ArrayList<RunEvent.InitialAnswersIn>()
    data class Summary(val caller: MethodKey, val part: Subscription, val publication: Publication, val member: Int)
    data class Native(val caller: MethodKey, val part: Subscription, val record: Record)
    data class View(val caller: MethodKey, val part: Subscription, val view: RecordLeafView)
    data class StaticDefault(val caller: MethodKey, val part: StaticSubscription, val entries: FlowTree)
    val summaries = ArrayList<Summary>()
    val native = ArrayList<Native>()
    val views = ArrayList<View>()
    val staticDefaults = ArrayList<StaticDefault>()
    var onSummary: ((Summary) -> Unit)? = null
    var onRecord: ((Native) -> Unit)? = null
    var onView: ((View) -> Unit)? = null
    var onPost: ((RunEvent.Delivery) -> Unit)? = null
    var onStaticDefault: ((StaticDefault) -> Unit)? = null
    var onStaticEntries: ((RunEvent.StaticEntriesIn) -> Unit)? = null
    var onInitialAnswers: ((RunEvent.InitialAnswersIn) -> Unit)? = null

    fun storage(key: MethodKey = callee): SummaryStorage = storages.getOrPut(key) {
        SummaryStorage(key, ap, ops, protocol)
    }

    val port = object : SubscriptionPort {
        override val config get() = this@PipelineFixtures.config
        override val ops get() = this@PipelineFixtures.ops
        override val steps get() = protocol
        override fun summaryStorage(key: MethodKey): SummaryStorage = storage(key)
        override fun staticFootprint(method: CommonMethod): Run1StaticFootprint? = staticFootprints[method]
        override fun applier(caller: MethodKey): SummaryApplier = object : SummaryApplier {
            override fun applySummary(part: Subscription, pub: Publication, member: Int) {
                val application = Summary(caller, part, pub, member)
                summaries += application
                onSummary?.invoke(application)
            }
            override fun applyRecord(part: Subscription, record: Record) {
                val application = Native(caller, part, record)
                native += application
                onRecord?.invoke(application)
            }
            override fun applyStaticDefault(part: StaticSubscription, entries: FlowTree) {
                val application = StaticDefault(caller, part, entries)
                staticDefaults += application
                onStaticDefault?.invoke(application)
            }
            override fun applyRecordView(part: Subscription, view: RecordLeafView) {
                val application = View(caller, part, view)
                views += application
                onView?.invoke(application)
            }
        }
        override fun send(event: RunEvent): Boolean {
            protocolEvents += event
            return true
        }
        override fun post(event: RunEvent.ChangedRequests): Boolean {
            protocolEvents += event
            return true
        }
        override fun post(event: RunEvent.Delivery): Boolean {
            deliveries += event
            onPost?.invoke(event)
            return true
        }
    }
    val subscriptions = SubscriptionManager(port)

    fun subscription(added: Facts = store.fact(), premise: PremiseKey =
        if (added is FlowTree) ops.policy(added) else ap.zero,
                     callerLayer: Layer = Layer.NORMAL, call: Int = 1,
                     caller: MethodKey = this.caller, callee: MethodKey = this.callee,
                     zeroOnly: Boolean = false): Subscription =
        Subscription(callee, CallerRef(caller, premise, callerLayer, store.inst(call)), added.layer, added, zeroOnly)

    fun deliverAll() {
        while (deliveries.isNotEmpty() || protocolEvents.isNotEmpty()) {
            if (deliveries.isNotEmpty()) {
                val event = deliveries.removeAt(0)
                subscriptions.onDelivery(event.callee, event.publications)
            } else when (val event = protocolEvents.removeFirst()) {
                is RunEvent.ChangedRequests -> subscriptions.onRequests(event.callee, event.rows)
                is RunEvent.StaticEntriesIn -> {
                    staticEntries += event
                    onStaticEntries?.invoke(event)
                }
                is RunEvent.InitialAnswersIn -> {
                    initialAnswers += event
                    onInitialAnswers?.invoke(event)
                }
                else -> error("Unexpected protocol fixture event: $event")
            }
        }
    }

    override fun close() { subscriptions.freeze(); method.close() }
}

/** Queued closures let tests place publication between registration and replay. */
class QueuedProtocolSteps : ProtocolSteps {
    var delayReplay = true
    var delayNotify = true
    val replays = ArrayDeque<() -> Unit>()
    val notifications = ArrayDeque<() -> Unit>()
    override fun replay(step: () -> Unit) { if (delayReplay) replays.addLast(step) else step() }
    override fun notify(step: () -> Unit) { if (delayNotify) notifications.addLast(step) else step() }
    fun replayAll() { while (replays.isNotEmpty()) replays.removeFirst()() }
    fun runNotifications() { while (notifications.isNotEmpty()) notifications.removeFirst()() }
}
