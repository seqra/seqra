package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.bidi.ap.TaintMark

/** Notification posts to this subscribed runner, independently of the callee's unit. */
interface SubscriptionPort {
    val config: RunConfig
    val ops: ApOps
    val steps: ProtocolSteps
    val statistics: N2Statistics? get() = null
    val abstractionStatistics: Run1AbstractionStatistics? get() = null
    val requestBatchStatistics: RequestBatchStatistics? get() = null
    fun summaryStorage(key: MethodKey): SummaryStorage
    fun applier(key: MethodKey): SummaryApplier
    fun post(event: RunEvent.Delivery): Boolean
    fun post(event: RunEvent.QuestionDelivery): Boolean = error("Question delivery needs its subscribed runner")
    /** Must enqueue, including same-unit answers/climbs; never invoke an actor inline during a visit. */
    fun send(event: RunEvent): Boolean = error("Question results need counted queued delivery")
    /** Only exact installed coverage can replace transport; it must keep observing future suppliers. */
    fun covered(method: MethodKey, initial: InitialAp, mark: TaintMark): Boolean = false
}

/** The actual method handler supplies subscriptions and releases them after joining. */
interface SubscriptionHandler {
    fun subscribe(sub: Subscription)
    fun freeze()
}
