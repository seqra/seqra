package org.opentaint.dataflow.bidi.store

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
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

/** Exact accumulation has no positional index and retains no historical delta values. */
internal class AddedFactAccumulator(private val m: ApManager) {
    private data class Key(val ref: CallerRef, val group: GroupKey)
    private class Entry(val key: Key, var facts: Facts)
    internal data class Snapshot(val ref: CallerRef, val group: GroupKey, val facts: Facts)
    internal data class Update(val handle: Int, val snapshot: Snapshot, val delta: Facts, val newGroup: Boolean)

    private val handles = Object2IntOpenHashMap<Key>().apply { defaultReturnValue(-1) }
    private val entries = ArrayList<Entry>()
    private val interners = m.newInterners()
    val size: Int get() = entries.size

    fun add(ref: CallerRef, linkLayer: Layer, added: Facts): Update? {
        m.cancellation.checkpoint()
        check(added.base !is AccessPathBase.CleanerTemporary) { "A field-action temporary cannot enter an added link" }
        check(linkLayer == added.layer) { "The link layer must be the added fact's layer" }
        // Exact-only deltas can clear E. The key belongs to the admitted input, never to its delta.
        val key = Key(ref, added.groupKey)
        val handle = handles.getInt(key)
        if (handle < 0) {
            val entry = Entry(key, interners.internIfRequired(added))
            val id = entries.size
            entries += entry
            handles.put(key, id)
            return Update(id, Snapshot(key.ref, key.group, entry.facts), added, newGroup = true)
        }
        val entry = entries[handle]
        val (merged, fresh) = m.mergeAddDelta(entry.facts, added)
        if (fresh == null) return null
        entry.facts = interners.internIfRequired(merged)
        return Update(handle, Snapshot(entry.key.ref, entry.key.group, entry.facts), fresh, newGroup = false)
    }

    fun snapshot(handle: Int): Snapshot {
        m.cancellation.checkpoint()
        val entry = entries[handle]
        return Snapshot(entry.key.ref, entry.key.group, entry.facts)
    }

    fun clear() {
        handles.clear()
        entries.clear()
    }
}

/** Indexed raw links share the exact accumulator used by cumulative subscriptions. */
class AddedFactStore(private val m: ApManager) {
    private val values = AddedFactAccumulator(m)
    private val index = PathTrie<Int>(m.cancellation)
    private val ops = ApOps(m)

    /** The new links of event E2, including every leaf for a new caller of an existing added fact. */
    fun add(ref: CallerRef, linkLayer: Layer, added: Facts): Facts? {
        val update = values.add(ref, linkLayer, added) ?: return null
        m.forEachLeafPosition(update.delta) { base, path -> index.add(base, path, update.handle) }
        return update.delta
    }

    /** Candidate keys are deduplicated before reading only the overlapping leaves along and below the query path. */
    fun overlapping(base: AccessPathBase, path: PathNode?): Sequence<Link> {
        m.cancellation.checkpoint()
        val p = path?.toIntArray() ?: EMPTY_PATH
        return index.around(base, p).distinct().asSequence().flatMap { handle ->
            m.cancellation.checkpoint()
            val snapshot = values.snapshot(handle)
            ops.leavesNear(snapshot.facts, p).map {
                m.cancellation.checkpoint()
                Link(it, snapshot.group.layer, snapshot.ref)
            }
        }
    }

    /** Exact raw links for support computation at the barrier. */
    fun links(): Sequence<Link> {
        m.cancellation.checkpoint()
        return (0 until values.size).asSequence().flatMap { handle ->
            m.cancellation.checkpoint()
            val snapshot = values.snapshot(handle)
            ops.leaves(snapshot.facts).map {
                m.cancellation.checkpoint()
                Link(it, snapshot.group.layer, snapshot.ref)
            }
        }
    }
}
