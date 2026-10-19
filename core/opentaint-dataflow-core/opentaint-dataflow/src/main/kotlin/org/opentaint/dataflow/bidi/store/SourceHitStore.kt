package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.interp.ActionId
import org.opentaint.ir.api.common.cfg.CommonInst

/** Backward-run source hits, identified by the same forward micro edge in every run. */
class SourceHitStore {
    data class Hit(val method: MethodKey, val statement: CommonInst, val forward: PathEdge, val action: ActionId?)
    private val hits = LinkedHashSet<Hit>()

    fun add(method: MethodKey, statement: CommonInst, forward: PathEdge, action: ActionId? = null): Boolean {
        check(forward.from.base !is AccessPathBase.CleanerTemporary && forward.to.base !is AccessPathBase.CleanerTemporary) {
            "A field-action temporary cannot enter a source hit"
        }
        return hits.add(Hit(method, statement, forward, action))
    }

    /** The driver reads these hits at the barrier to seed the next forward run. */
    fun entries(): Sequence<Triple<MethodKey, CommonInst, PathEdge>> =
        hits.asSequence().map { Triple(it.method, it.statement, it.forward) }.distinct()

    /** Equal AP/mark tuples from distinct source actions remain distinct permissions. */
    fun occurrences(): Sequence<Hit> = hits.asSequence()
}
