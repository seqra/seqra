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
    private val inputs = ArrayList<Pair<CallerRef, Pattern>>()

    fun addPositionRequest(premise: InitialAp, kind: RequestKind.Position) {
        if (!positions.add(premise, kind)) return
        val query = positions.query(premise, kind)
        for ((ref, leaf) in inputs) if (overlap(leaf, query)) act(premise, kind, leaf, ref)
    }

    fun onInput(ref: CallerRef, facts: Facts) {
        for (leaf in core.ops.leaves(facts)) {
            if (inputs.any { it.first == ref && it.second == leaf }) continue
            inputs += ref to leaf
            positions.visitPositionCandidates(leaf) { initial, kind -> act(initial, kind, leaf, ref) }
        }
    }

    private fun act(initial: InitialAp, kind: RequestKind.Position, added: Pattern, ref: CallerRef) {
        when (val action = referenceRequestAction(core.ap, initial, kind, added, ref)) {
            RequestAction.None -> Unit
            is RequestAction.Answer -> core.addInitial(action.initial)
            is RequestAction.Climb -> onClimb(ReferenceRequestDelivery(ref.caller, action.premise, kind))
        }
    }

    override fun freeze() { positions.release(); inputs.clear(); delegate.freeze() }
}
