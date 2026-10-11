package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.EMPTY_PATH
import org.opentaint.dataflow.bidi.ap.Facts
import org.opentaint.dataflow.bidi.ap.GroupKey
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PathNode
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.PremiseKey
import org.opentaint.dataflow.bidi.ap.forEachLeafPosition
import org.opentaint.dataflow.bidi.ap.groupKey
import org.opentaint.dataflow.bidi.ap.mergeAddDelta
import org.opentaint.ir.api.common.cfg.CommonInst

/** The exact caller side of a link. */
data class CallerRef(val caller: MethodKey, val premise: PremiseKey, val callerLayer: Layer, val call: CommonInst)

/** One added leaf and its own layer, which can differ from the caller edge's layer. */
data class Link(val addedFact: Pattern, val linkLayer: Layer, val caller: CallerRef)

/** Exact leaf merges per caller reference and conclusion group; no added-link subsumption or annotation folding. */
class AddedFactStore(private val m: ApManager) {
    private data class Key(val ref: CallerRef, val group: GroupKey)
    private val values = LinkedHashMap<Key, Facts>()
    private val index = PathTrie<Key>(m.cancellation)
    private val interners = m.newInterners()
    private val ops = ApOps(m)

    /** The new links of event E2, including every leaf for a new caller of an existing added fact. */
    fun add(ref: CallerRef, linkLayer: Layer, added: Facts): Facts? {
        m.cancellation.checkpoint()
        check(linkLayer == added.layer) { "The link layer must be the added fact's layer" }
        val key = Key(ref, added.groupKey)
        val old = values[key]
        val delta = if (old == null) {
            values[key] = interners.internIfRequired(added)
            added
        } else {
            val (merged, fresh) = m.mergeAddDelta(old, added)
            values[key] = interners.internIfRequired(merged)
            fresh ?: return null
        }
        m.forEachLeafPosition(delta) { base, path -> index.add(base, path, key) }
        return delta
    }

    /** Candidate keys are deduplicated before reading only the overlapping leaves along and below the query path. */
    fun overlapping(base: AccessPathBase, path: PathNode?): Sequence<Link> {
        m.cancellation.checkpoint()
        val p = path?.toIntArray() ?: EMPTY_PATH
        return index.around(base, p).distinct().asSequence().flatMap { key ->
            m.cancellation.checkpoint()
            ops.leavesNear(values.getValue(key), p).map {
                m.cancellation.checkpoint()
                Link(it, key.group.layer, key.ref)
            }
        }
    }

    /** Exact raw links for support computation at the barrier. */
    fun links(): Sequence<Link> {
        m.cancellation.checkpoint()
        return values.keys.asSequence().flatMap { key ->
            m.cancellation.checkpoint()
            ops.leaves(values.getValue(key)).map {
                m.cancellation.checkpoint()
                Link(it, key.group.layer, key.ref)
            }
        }
    }
}
