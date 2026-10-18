package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.CallerRef
import java.util.Collections

/** Canonical entry descriptors. The caller keeps the complete actual input. */
data class Run1PreparedInitials(
    val initials: List<InitialAp>,
    val staticEntries: FlowTree?,
    val demandPresence: Boolean,
)

/** Run the existing coordinate normalization before the descriptor boundary.
 * Requested nonstatic marks have a separate caller-owned request join. */
fun prepareRun1Initials(
    ops: ApOps,
    profile: Run1StaticProfile?,
    ref: CallerRef,
    input: Facts,
    statistics: RequestBatchStatistics? = null,
    policyStatistics: Run1AbstractionStatistics? = null,
    owner: MethodKey? = null,
): Run1PreparedInitials {
    val initials = LinkedHashSet<InitialAp>()
    var statics: FlowTree? = null
    val preparation = Run1Abstraction(
        ops, profile,
        answer = { initials.add(it) },
        borrowRequests = { emptyList() },
        collectRequests = { _, _ -> error("Input preparation cannot produce requests") },
        statistics = statistics,
        policyStatistics = policyStatistics,
        owner = owner,
        staticEntries = { statics = it },
    )
    try { preparation.emit(ref, input) }
    finally { preparation.release() }
    return Run1PreparedInitials(Collections.unmodifiableList(ArrayList(initials)), statics,
        input.layer == Layer.DEMAND)
}
