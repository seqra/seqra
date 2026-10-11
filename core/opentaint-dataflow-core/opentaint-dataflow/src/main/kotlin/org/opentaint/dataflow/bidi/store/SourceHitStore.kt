package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.ir.api.common.cfg.CommonInst

/** Backward-run source hits, identified by the same forward micro edge in every run. */
class SourceHitStore {
    private val hits = HashSet<Triple<MethodKey, CommonInst, PathEdge>>()

    fun add(method: MethodKey, statement: CommonInst, forward: PathEdge): Boolean =
        hits.add(Triple(method, statement, forward))

    /** The driver reads these hits at the barrier to seed the next forward run. */
    fun entries(): Sequence<Triple<MethodKey, CommonInst, PathEdge>> = hits.asSequence()
}
