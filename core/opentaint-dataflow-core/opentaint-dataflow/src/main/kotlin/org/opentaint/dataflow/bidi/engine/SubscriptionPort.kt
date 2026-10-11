package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.MethodKey

/** Notification posts to this subscribed runner, independently of the callee's unit. */
interface SubscriptionPort {
    val config: RunConfig
    val ops: ApOps
    val steps: ProtocolSteps
    fun summaryStorage(key: MethodKey): SummaryStorage
    fun applier(key: MethodKey): SummaryApplier
    fun post(event: RunEvent.Delivery): Boolean
}

/** The actual method handler supplies subscriptions and releases them after joining. */
interface SubscriptionHandler {
    fun subscribe(sub: Subscription)
    fun freeze()
}
