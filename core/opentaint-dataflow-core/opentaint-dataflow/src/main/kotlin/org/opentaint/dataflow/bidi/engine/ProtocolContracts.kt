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
}
