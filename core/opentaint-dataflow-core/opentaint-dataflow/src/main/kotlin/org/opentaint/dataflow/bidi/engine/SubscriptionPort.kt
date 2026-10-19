package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.MethodKey

/** Notification posts to this subscribed runner, independently of the callee's unit. */
interface SubscriptionPort {
    val config: RunConfig
    val ops: ApOps
    val steps: ProtocolSteps
    val statistics: N2Statistics? get() = null
    val abstractionStatistics: Run1AbstractionStatistics? get() = null
    val requestBatchStatistics: RequestBatchStatistics? get() = null
    val staticService: Run1StaticService? get() = null
    fun summaryStorage(key: MethodKey): SummaryStorage
    fun applier(key: MethodKey): SummaryApplier
    fun post(event: RunEvent.Delivery): Boolean
    fun post(event: RunEvent.ChangedRequests): Boolean = error("Request delivery needs its subscribed runner")
    /** Must enqueue, including same-unit answers/climbs; never invoke an actor inline during a visit. */
    fun send(event: RunEvent): Boolean = error("Question results need counted queued delivery")
}

/** The actual method handler supplies subscriptions and releases them after joining. */
interface SubscriptionHandler {
    fun subscribe(sub: Subscription)
    fun borrowN2(row: N2InputRef): org.opentaint.dataflow.bidi.ap.Facts? = null
    fun borrowN2Cofactors(row: N2InputRef): List<Subscription> = emptyList()
    fun freeze()
}
