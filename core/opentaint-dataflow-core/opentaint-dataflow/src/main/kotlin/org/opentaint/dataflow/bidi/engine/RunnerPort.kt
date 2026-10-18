package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.Facts
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.interp.DirectedForms
import org.opentaint.dataflow.bidi.store.CallerRef

/** Only post can be called by a publisher on another unit's thread. */
interface RunnerPort<A : RunActor> {
    val config: RunConfig
    val shared: SharedObjects
    val forms: DirectedForms
    val ops: ApOps
    val steps: ProtocolSteps
    val staticNeedRouter: StaticNeedRouter? get() = null
    val staticRequests: CallerOwnedStaticRequests? get() = null
    /** The current runner owns this accumulator; never resolve ownership from a source key. */
    val pendingNeedReplay: PendingNeedReplay? get() = null
    fun send(event: RunEvent): Boolean
    fun post(event: RunEvent.Delivery): Boolean
    fun post(event: RunEvent.ChangedRequests): Boolean = error("Request delivery needs its subscribed runner")
    fun post(event: RunEvent.StaticNeed): Boolean = error("Static need delivery needs its subscribed runner")
    fun post(event: RunEvent.CallsiteRequests): Boolean = error("Callsite requests need their subscribed runner")
    fun enqueue(analyzer: A): Boolean
    fun analyzer(key: MethodKey): A
    fun summaryStorage(key: MethodKey): SummaryStorage
    fun subscribe(sub: Subscription)
    fun subscribeStatic(sub: StaticSubscription): Unit = error("Static identity transport is unavailable")
    fun subscribeConcreteStatic(sub: ConcreteStaticSubscription): Unit = error("Concrete static transport is unavailable")
    fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts)

    val onProcess: ((MethodKey, EdgeDelta) -> Unit)? get() = null
    /** Optional test decoder; production retains no per-field identity history. */
    val onStaticProcess: ((MethodKey, org.opentaint.ir.api.common.cfg.CommonInst, org.opentaint.dataflow.bidi.ap.FlowTree) -> Unit)? get() = null
    val onConcreteStaticProcess: ((MethodKey, org.opentaint.ir.api.common.cfg.CommonInst, org.opentaint.dataflow.bidi.ap.TaintTree) -> Unit)? get() = null
    val onCut: ((Cut, Facts) -> Unit)? get() = null
}
