package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.Facts
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PremiseKey
import org.opentaint.dataflow.bidi.ap.Reach
import org.opentaint.dataflow.bidi.ap.RecordLeafView
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.Record
import org.opentaint.ir.api.common.cfg.CommonInst

/** New propagation content preserves its exact premise, position and fact group. */
data class EdgeDelta(val premise: PremiseKey, val node: CommonInst, val facts: Facts) {
    val zeroToZero: Boolean get() = premise.isZero && facts is Reach
}

enum class Cut { STATEMENT, CALL, ENTRY_RULES, EXIT_RULES, SEED }

/** Published summary content keeps its canonical keys and the unchanged fact object. */
data class Publication(val premise: PremiseKey, val conclusion: Facts) {
    val layer: Layer get() = conclusion.layer
}

/** The caller layer is independent of the common added-fact and link layer. */
data class Subscription(
    val callee: MethodKey,
    val ref: CallerRef,
    val linkLayer: Layer,
    val added: Facts,
    val zeroOnly: Boolean = false,
) {
    val caller: MethodKey get() = ref.caller
}

/** A shared exact call-stage relation; each field supplies its own original. */
data class StaticSubscription(
    val callee: MethodKey,
    val caller: MethodKey,
    val call: CommonInst,
    val entries: org.opentaint.dataflow.bidi.ap.FlowTree,
    val stageFrom: org.opentaint.dataflow.bidi.interp.CallPoint = org.opentaint.dataflow.bidi.interp.CallPoint.ADDED,
    val stageTo: org.opentaint.dataflow.bidi.interp.CallPoint = org.opentaint.dataflow.bidi.interp.CallPoint.RETURNED,
)

/** Field/mark/kind diagonals share the full call port, never their originals. */
data class ConcreteStaticSubscription(
    val callee: MethodKey,
    val caller: MethodKey,
    val call: CommonInst,
    val entries: org.opentaint.dataflow.bidi.ap.TaintTree,
    val stageFrom: org.opentaint.dataflow.bidi.interp.CallPoint,
    val stageTo: org.opentaint.dataflow.bidi.interp.CallPoint,
    val identityCandidate: Boolean? = null,
)

/** Tests can control replay and notification timing without changing protocol operations. */
interface ProtocolSteps {
    fun replay(step: () -> Unit)
    fun notify(step: () -> Unit)

    object Inline : ProtocolSteps {
        override fun replay(step: () -> Unit): Unit = step()
        override fun notify(step: () -> Unit): Unit = step()
    }
}

/** Native records and opposite-direction leaf views have separate application boundaries. */
interface SummaryApplier {
    fun applySummary(part: Subscription, pub: Publication, member: Int)
    fun applyRecord(part: Subscription, record: Record)
    fun applyRecordView(part: Subscription, view: RecordLeafView)
    fun applyStaticDefault(part: StaticSubscription, entries: org.opentaint.dataflow.bidi.ap.FlowTree): Unit =
        error("Static identity transport is unavailable")
    fun applyStaticSummary(part: StaticSubscription, input: Subscription, pub: Publication): Unit =
        applySummary(input, pub, 0)
    fun applyConcreteStaticDefault(part: ConcreteStaticSubscription, entries: org.opentaint.dataflow.bidi.ap.TaintTree): Unit =
        error("Concrete static transport is unavailable")
    fun applyConcreteStaticSummary(part: ConcreteStaticSubscription, input: Subscription, pub: Publication, member: Int): Unit =
        applySummary(input, pub, member)
}
