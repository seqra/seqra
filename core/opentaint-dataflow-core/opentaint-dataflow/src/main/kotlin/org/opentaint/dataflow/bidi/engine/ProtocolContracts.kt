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
data class EdgeDelta(val premise: PremiseKey, val node: CommonInst, val facts: Facts,
                     val grounding: GroundingSupport = GroundingSupport.TRUE) {
    val zeroToZero: Boolean get() = premise.isZero && facts is Reach

    override fun equals(other: Any?): Boolean = this === other || other is EdgeDelta &&
        premise == other.premise && node == other.node && facts == other.facts
    override fun hashCode(): Int = (premise.hashCode() * 31 + node.hashCode()) * 31 + facts.hashCode()
}

enum class Cut { STATEMENT, CALL, ENTRY_RULES, EXIT_RULES, SEED }

/** Published summary content keeps its canonical keys and the unchanged fact object. */
data class Publication(val premise: PremiseKey, val conclusion: Facts,
                       val grounding: GroundingSupport = GroundingSupport.TRUE) {
    val layer: Layer get() = conclusion.layer
}

/** The caller layer is independent of the common added-fact and link layer. */
data class Subscription(
    val callee: MethodKey,
    val ref: CallerRef,
    val linkLayer: Layer,
    val added: Facts,
    val zeroOnly: Boolean = false,
    val supportRevision: Long = 0,
    val inputRef: N2InputRef? = null,
    val grounding: GroundingSupport = GroundingSupport.TRUE,
) {
    val caller: MethodKey get() = ref.caller

    /** Owner metadata does not change the semantic subscription identity. */
    override fun equals(other: Any?): Boolean = this === other || other is Subscription &&
        callee == other.callee && ref == other.ref && linkLayer == other.linkLayer &&
        added == other.added && zeroOnly == other.zeroOnly

    override fun hashCode(): Int = (((callee.hashCode() * 31 + ref.hashCode()) * 31 +
        linkLayer.hashCode()) * 31 + added.hashCode()) * 31 + zeroOnly.hashCode()
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
    fun registerStaticInput(row: N2InputRef, added: Facts) = Unit
    fun requestStaticGroups(part: Subscription, marks: org.opentaint.dataflow.bidi.store.RequestMarkSelection) {
        error("This summary applier does not support static point requests")
    }
    fun applySummary(part: Subscription, pub: Publication, member: Int)
    /** Run-1 control admits a conditional static relation; it does not supply its mark. */
    fun applyStaticSummary(part: Subscription, pub: Publication, member: Int) {
        error("This summary applier does not support run-1 conditional statics")
    }
    fun applyRecord(part: Subscription, record: Record)
    fun applyRecordView(part: Subscription, view: RecordLeafView)
}
