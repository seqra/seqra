package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.AddedFactAccumulator
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.PathTrie
import it.unimi.dsi.fastutil.ints.IntOpenHashSet

/** The caller runner owns exact added groups, indexed as the transpose of replay. */
class CalleeSubscriptions(private val ops: ApOps, private val statistics: N2Statistics? = null) {
    private val values = AddedFactAccumulator(ops.manager)
    private val callees = ArrayList<MethodKey>()
    private var index = PathTrie<Int>(ops.manager.cancellation)
    private val zeroParts = LinkedHashMap<CallerRef, Subscription>()
    private var postings = 0L
    private var visitingQuestion = false

    /** A queued replay keeps the established fresh delta, not an old cumulative root. */
    internal inner class Admission(val fresh: Subscription, private val handle: Int, val growth: Boolean = false) {
        val current: Subscription get() {
            if (handle < 0) return fresh
            val value = values.snapshot(handle)
            return Subscription(callees[handle], value.ref, value.group.layer, value.facts)
        }
    }

    fun add(sub: Subscription): Subscription? = admit(sub)?.fresh

    internal fun admit(sub: Subscription): Admission? {
        ops.manager.cancellation.checkpoint()
        check(!visitingQuestion) { "Question effects must be queued before changing N2 inputs" }
        if (sub.zeroOnly) {
            check(sub.ref.premise.isZero) { "A zero-only subscription needs a zero caller premise" }
            check(sub.added is Reach) { "A zero-only subscription needs REACH" }
            check(sub.linkLayer == sub.added.layer) { "The link layer must be the added fact's layer" }
            if (sub.ref in zeroParts) return null
            zeroParts[sub.ref] = sub
            statistics?.accepted(zeroOnly = true)
            return Admission(sub, -1)
        }
        val update = values.add(sub.ref, sub.linkLayer, sub.added) ?: return null
        if (update.newGroup) callees += sub.callee
        statistics?.accepted(newGroup = update.newGroup)
        ops.manager.forEachLeafPosition(update.delta) { base, path ->
            if (index.add(base, path, update.handle) && statistics != null) {
                postings++
                statistics.posted()
            }
        }
        // This immutable delta is retained only by the pending replay callback.
        return Admission(sub.copy(added = update.delta), update.handle, growth = !update.newGroup)
    }

    /** Request overlap differs from run-1 summary applicability: ancestors are candidates too.
     *  The owner must queue every effect, including local effects, until this finite visit ends. */
    internal fun visitRequestBatch(rows: List<RequestedRow>, visit: (Subscription) -> Unit) {
        ops.manager.cancellation.checkpoint()
        check(!visitingQuestion) { "A request visit cannot recursively enter the caller directory" }
        visitingQuestion = true
        try {
            // One set for the whole batch, not one set per requested slot.
            val seen = IntOpenHashSet()
            for (row in rows) {
                ops.manager.cancellation.checkpoint()
                index.visitAround(row.slot.base, row.slot.pathData) { handle ->
                    ops.manager.cancellation.checkpoint()
                    if (seen.add(handle)) {
                        val value = values.snapshot(handle)
                        visit(Subscription(callees[handle], value.ref, value.group.layer, value.facts))
                    }
                }
            }
        } finally { visitingQuestion = false }
    }

    fun candidates(pub: Publication, member: Int, mode: ApMode): Sequence<Subscription> = sequence {
        ops.manager.cancellation.checkpoint()
        val initial = pub.premise.member(member)
        val ids = LinkedHashSet<Int>()
        if (mode.run1) {
            ids += index.lookupExtensions(initial.base, initial.pathData)
            // The component reader can project a broad root/class supplier into
            // an owned static field before full ordinary satisfaction.
            if (initial.base == STATIC && initial.pathData.size == 2) {
                ids += index.lookupPrefixes(initial.base, initial.pathData)
            }
        }
        else {
            ids += index.lookupPrefixes(initial.base, initial.pathData)
            if (initial.mark is MarkSlot.Star) ids += index.lookupExtensions(initial.base, initial.pathData)
        }
        // Capture every candidate value before callbacks can grow this directory.
        val snapshot = ArrayList<Subscription>(ids.size + if (pub.premise.isZero) zeroParts.size else 0)
        for (id in ids) {
            ops.manager.cancellation.checkpoint()
            val value = values.snapshot(id)
            snapshot += Subscription(callees[id], value.ref, value.group.layer, value.facts)
        }
        if (pub.premise.isZero) snapshot.addAll(zeroParts.values)
        for (sub in snapshot) {
            ops.manager.cancellation.checkpoint()
            yield(sub)
        }
    }

    /** Called only after the runner joins. */
    fun clear() {
        check(!visitingQuestion) { "A live question visit cannot release its input directory" }
        statistics?.releaseDirectory(values.size, zeroParts.size, postings)
        values.clear()
        callees.clear()
        zeroParts.clear()
        index = PathTrie(ops.manager.cancellation)
        postings = 0L
    }
}
