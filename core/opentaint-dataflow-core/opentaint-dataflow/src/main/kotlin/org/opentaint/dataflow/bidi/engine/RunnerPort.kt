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
    fun send(event: RunEvent): Boolean
    fun post(event: RunEvent.Delivery): Boolean
    fun post(event: RunEvent.QuestionDelivery): Boolean = error("Question delivery needs its subscribed runner")
    fun enqueue(analyzer: A): Boolean
    fun analyzer(key: MethodKey): A
    fun summaryStorage(key: MethodKey): SummaryStorage
    fun subscribe(sub: Subscription)
    fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts)

    val onProcess: ((MethodKey, EdgeDelta) -> Unit)? get() = null
    val onCut: ((Cut, Facts) -> Unit)? get() = null
}
