package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.engine.historical.Run1Slot as HistoricalRun1Slot
import org.opentaint.dataflow.bidi.engine.historical.Run1StaticProfiles
import org.opentaint.dataflow.bidi.engine.historical.Run1StaticProfile
import org.opentaint.dataflow.bidi.engine.historical.Run1StaticLocalProfile
import org.opentaint.dataflow.bidi.engine.historical.Run1StaticFootprint
import org.opentaint.dataflow.bidi.engine.historical.StaticMarkInterest
import org.opentaint.dataflow.bidi.engine.historical.StaticMarkRole
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.CommonMethod

/** Protocol timing remains test-owned; all indexing and matching use production code. */
class PipelineFixtures(index: Int = 1, limit: Int = maxOf(1, index - 1),
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
    data class Summary(val caller: MethodKey, val part: Subscription, val publication: Publication, val member: Int)
    data class Native(val caller: MethodKey, val part: Subscription, val record: Record)
    data class View(val caller: MethodKey, val part: Subscription, val view: RecordLeafView)
    val summaries = ArrayList<Summary>()
    val native = ArrayList<Native>()
    val views = ArrayList<View>()
    var onSummary: ((Summary) -> Unit)? = null
    var onRecord: ((Native) -> Unit)? = null
    var onView: ((View) -> Unit)? = null
    var onPost: ((RunEvent.Delivery) -> Unit)? = null

    fun storage(key: MethodKey = callee): SummaryStorage = storages.getOrPut(key) {
        SummaryStorage(key, ap, ops, protocol)
    }

    val port = object : SubscriptionPort {
        override val config get() = this@PipelineFixtures.config
        override val ops get() = this@PipelineFixtures.ops
        override val steps get() = protocol
        override fun summaryStorage(key: MethodKey): SummaryStorage = storage(key)
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
            override fun applyRecordView(part: Subscription, view: RecordLeafView) {
                val application = View(caller, part, view)
                views += application
                onView?.invoke(application)
            }
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
        while (deliveries.isNotEmpty()) {
            val event = deliveries.removeAt(0)
            subscriptions.onDelivery(event.callee, event.publications)
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
