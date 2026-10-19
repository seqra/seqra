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
    private val supportRevisions = HashMap<Int, Long>()
    private data class SupportLeaf(val pattern: Pattern, val layer: Layer)
    /** Only guarded handles need semantic cofactor metadata. No old Facts roots are kept. */
    private val supported = HashMap<Int, LinkedHashMap<SupportLeaf, GroundingSupport>>()
    private var postings = 0L
    private var visitingQuestion = false

    /** A queued replay keeps the established fresh delta, not an old cumulative root. */
    internal inner class Admission(val fresh: Subscription, val handle: Int, val growth: Boolean = false,
                                   val supportOnly: Boolean = false,
                                   val replayParts: List<Subscription> = listOf(fresh)) {
        val currentParts: List<Subscription> get() = if (handle < 0) listOf(fresh) else cofactors(handle)
    }

    fun add(sub: Subscription): Subscription? = admit(sub)?.fresh

    internal fun borrow(row: N2InputRef): Facts? {
        ops.manager.cancellation.checkpoint()
        if (row.handle < 0 || row.handle >= values.size || callees[row.handle] != row.callee) return null
        val current = values.snapshot(row.handle)
        return current.facts.takeIf { current.ref == row.ref && current.ref.caller == row.owner }
    }

    internal fun borrowCofactors(row: N2InputRef): List<Subscription> {
        if (borrow(row) == null) return emptyList()
        return cofactors(row.handle)
    }

    private fun part(handle: Int, facts: Facts, support: GroundingSupport): Subscription {
        val value = values.snapshot(handle)
        return Subscription(callees[handle], value.ref, facts.layer, facts,
            supportRevision = supportRevisions[handle] ?: 0,
            inputRef = N2InputRef(value.ref.caller, callees[handle], handle, value.ref), grounding = support)
    }

    private fun cofactors(handle: Int): List<Subscription> {
        val rows = supported[handle]
        return if (rows == null) listOf(part(handle, values.snapshot(handle).facts, GroundingSupport.TRUE))
        else rows.map { (leaf, support) ->
            ops.manager.cancellation.checkpoint()
            part(handle, ops.manager.factsOf(leaf.pattern.fact, leaf.pattern.exclusion, leaf.layer), support)
        }
    }

    internal fun admit(sub: Subscription): Admission? {
        ops.manager.cancellation.checkpoint()
        check(!visitingQuestion) { "Question effects must be queued before changing N2 inputs" }
        if (sub.zeroOnly) {
            check(sub.ref.premise.isZero) { "A zero-only subscription needs a zero caller premise" }
            check(sub.added is Reach) { "A zero-only subscription needs REACH" }
            check(sub.linkLayer == sub.added.layer) { "The link layer must be the added fact's layer" }
            val old = zeroParts[sub.ref]
            if (old != null) {
                val support = old.grounding.or(sub.grounding)
                if (support == old.grounding && sub.supportRevision <= old.supportRevision) return null
                val fresh = old.copy(grounding = support,
                    supportRevision = maxOf(old.supportRevision + 1, sub.supportRevision))
                zeroParts[sub.ref] = fresh
                return Admission(fresh, -1, growth = true, supportOnly = true)
            }
            zeroParts[sub.ref] = sub
            statistics?.accepted(zeroOnly = true)
            return Admission(sub, -1)
        }
        val oldHandle = values.handleOf(sub.ref, sub.added)
        // Seed the guarded directory from the earlier unconditional relation.
        // The incoming guarded members must not inherit that TRUE support.
        val earlier = if (sub.grounding != GroundingSupport.TRUE && oldHandle >= 0 && oldHandle !in supported)
            values.snapshot(oldHandle).facts else null
        val update = values.add(sub.ref, sub.linkLayer, sub.added)
        val handle = update?.handle ?: oldHandle
        check(handle >= 0)
        if (update?.newGroup == true) callees += sub.callee
        check(callees[handle] == sub.callee) { "An N2 handle belongs to one callee" }
        val rows = supported[handle] ?: if (sub.grounding != GroundingSupport.TRUE) {
            LinkedHashMap<SupportLeaf, GroundingSupport>().also { selected ->
                if (earlier != null) for (leaf in ops.leaves(earlier)) {
                    ops.manager.cancellation.checkpoint()
                    selected[SupportLeaf(leaf, earlier.layer)] = GroundingSupport.TRUE
                }
                supported[handle] = selected
            }
        } else null
        val changed = ArrayList<SupportLeaf>()
        if (rows != null) for (leaf in ops.leaves(sub.added)) {
            ops.manager.cancellation.checkpoint()
            val key = SupportLeaf(leaf, sub.added.layer)
            val old = rows[key]
            val after = old?.or(sub.grounding) ?: sub.grounding
            if (old != after) { rows[key] = after; changed += key }
        }
        val oldRevision = supportRevisions[handle] ?: 0
        val revision = if (changed.isNotEmpty()) maxOf(oldRevision + 1, sub.supportRevision)
            else maxOf(oldRevision, sub.supportRevision)
        if (update == null && changed.isEmpty() && revision == oldRevision) return null
        if (revision != 0L) supportRevisions[handle] = revision
        if (update != null) {
            statistics?.accepted(newGroup = update.newGroup)
            ops.manager.forEachLeafPosition(update.delta) { base, path ->
                if (index.add(base, path, handle) && statistics != null) {
                    postings++
                    statistics.posted()
                }
            }
        }
        val fresh = sub.copy(added = update?.delta ?: sub.added, supportRevision = revision,
            inputRef = N2InputRef(sub.caller, sub.callee, handle, sub.ref))
        val parts = if (rows == null) listOf(fresh)
            else if (changed.isEmpty()) cofactors(handle)
            else changed.map { key ->
                part(handle, ops.manager.factsOf(key.pattern.fact, key.pattern.exclusion, key.layer), rows.getValue(key))
            }
        return Admission(fresh, handle, growth = update?.newGroup != true,
            supportOnly = update == null, replayParts = parts)
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
                        for (part in cofactors(handle)) visit(part)
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
            // Candidate ancestors are checked by the same full AP predicate
            // as replay. Exact ancestors and excluded components do not match.
            ids += index.lookupPrefixes(initial.base, initial.pathData)
        }
        else {
            ids += index.lookupPrefixes(initial.base, initial.pathData)
            if (initial.mark is MarkSlot.Star) ids += index.lookupExtensions(initial.base, initial.pathData)
        }
        // Capture every candidate value before callbacks can grow this directory.
        val snapshot = ArrayList<Subscription>(ids.size + if (pub.premise.isZero) zeroParts.size else 0)
        for (id in ids) {
            ops.manager.cancellation.checkpoint()
            snapshot.addAll(cofactors(id))
        }
        if (pub.premise.isZero) snapshot.addAll(zeroParts.values)
        for (sub in snapshot) {
            ops.manager.cancellation.checkpoint()
            yield(sub)
        }
    }

    /** A genuine forward ZERO control port is a view of an existing N2 row. */
    internal fun staticControlCandidates(): List<Subscription> {
        ops.manager.cancellation.checkpoint()
        val out = ArrayList<Subscription>()
        for (handle in index.lookupExtensions(org.opentaint.dataflow.ap.ifds.AccessPathBase.Zero, EMPTY_PATH)) {
            ops.manager.cancellation.checkpoint()
            val value = values.snapshot(handle)
            if (!value.ref.premise.isZero || value.facts !is Reach) continue
            out.addAll(cofactors(handle))
        }
        return out
    }

    /** Called only after the runner joins. */
    fun clear() {
        check(!visitingQuestion) { "A live question visit cannot release its input directory" }
        statistics?.releaseDirectory(values.size, zeroParts.size, postings)
        values.clear()
        callees.clear()
        zeroParts.clear()
        supportRevisions.clear()
        supported.clear()
        index = PathTrie(ops.manager.cancellation)
        postings = 0L
    }
}
