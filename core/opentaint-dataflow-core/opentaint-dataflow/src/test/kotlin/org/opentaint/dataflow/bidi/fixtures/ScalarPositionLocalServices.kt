package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.ap.ReferenceRequestAction as RequestAction
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.store.*

internal data class ReferenceRequestDelivery(val method: MethodKey, val premise: InitialAp, val request: RequestKind)

/** Test-only old Position protocol. It is not available to production actors. */
internal class ScalarPositionLocalServices(
    private val core: RunMethodAnalyzer,
    private val onClimb: (ReferenceRequestDelivery) -> Unit = {},
    private val delegate: RunMethodLocalServices = RunMethodLocalServices(core),
) : MethodLocalServices by delegate {
    private val positions = RequestStore(core.ap.cancellation)

    fun addPositionRequest(premise: InitialAp, kind: RequestKind.Position) {
        if (!positions.add(premise, kind)) return
        val query = positions.query(premise, kind)
        for (link in core.links.overlapping(STATIC, kind.path)) {
            if (overlap(link.addedFact, query)) act(premise, kind, link.addedFact, link.caller)
        }
    }

    override fun onAddedLink(update: AddedFactStore.Update) {
        for (leaf in core.ops.leaves(update.delta)) {
            positions.visitPositionCandidates(leaf) { initial, kind -> act(initial, kind, leaf, update.ref) }
        }
        delegate.onAddedLink(update)
    }

    private fun act(initial: InitialAp, kind: RequestKind.Position, added: Pattern, ref: CallerRef) {
        when (val action = referenceRequestAction(core.ap, initial, kind, added, ref)) {
            RequestAction.None -> Unit
            is RequestAction.Answer -> core.addInitial(action.initial)
            is RequestAction.Climb -> onClimb(ReferenceRequestDelivery(ref.caller, action.premise, kind))
        }
    }

    override fun freeze() { positions.release(); delegate.freeze() }
}
