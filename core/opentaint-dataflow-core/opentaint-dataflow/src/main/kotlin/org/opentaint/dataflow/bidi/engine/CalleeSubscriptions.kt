package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.AddedFactAccumulator
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.PathTrie
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.bidi.interp.CallPoint
import org.opentaint.dataflow.bidi.store.RequestMarkSelection
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.ir.api.common.cfg.CommonInst

/** Structural ND/request scope. Original premise and contributor layer stay in inputs. */
data class CallSiteKey(val caller: MethodKey, val callee: MethodKey, val call: CommonInst,
                       val stageFrom: CallPoint, val stageTo: CallPoint)

internal val Subscription.callSiteKey: CallSiteKey
    get() = CallSiteKey(caller, callee, ref.call, stageFrom, stageTo)
internal val StaticSubscription.callSiteKey: CallSiteKey
    get() = CallSiteKey(caller, callee, call, stageFrom, stageTo)

/** The caller runner owns exact added groups, indexed as the transpose of replay. */
class CalleeSubscriptions(private val ops: ApOps, private val statistics: N2Statistics? = null) {
    private val values = AddedFactAccumulator(ops.manager)
    private data class Header(val callee: MethodKey, val from: CallPoint, val to: CallPoint,
                              val coveredStaticRelay: Boolean)
    private val headers = ArrayList<Header>()
    private var pending = RequestStorage()
    private var answered = RequestStorage()
    private val exactRequestSlots = LinkedHashSet<Run1Slot>()
    private val viewRequestSlots = LinkedHashSet<Run1Slot.StaticField>()
    internal fun addExactRequestSlot(slot: Run1Slot): Boolean = exactRequestSlots.add(slot)
    internal fun addViewRequestSlot(slot: Run1Slot.StaticField): Boolean = viewRequestSlots.add(slot)
    internal fun acceptsExactRequest(slot: Run1Slot): Boolean = slot in exactRequestSlots
    internal fun acceptsViewRequest(slot: Run1Slot): Boolean = slot in viewRequestSlots
    internal fun answeredRows(input: Facts): List<RequestedRow> =
        answered.borrowMatchingRows(input, ops.manager.cancellation::checkpoint)

    /** Only P is changed here. A stays fixed until the counted method-empty drain. */
    internal fun offerRequest(slot: Run1Slot, offered: RequestMarkSelection): Boolean {
        val fresh = offered.minus(answered.borrow(slot), ops.manager.cancellation::checkpoint)
        return !fresh.isEmpty && !pending.addAll(slot, fresh, ops.manager.cancellation::checkpoint).isEmpty
    }

    /** A is installed before callbacks can enqueue any outward work. */
    internal fun drainRequests(): RequestRows {
        val detached = pending
        pending = RequestStorage()
        return answered.commit(detached, ops.manager.cancellation::checkpoint)
    }

    internal data class RequestState(val pendingRows: Int, val pendingMembers: Long,
                                     val answeredRows: Int, val answeredMembers: Long)
    internal fun requestState(): RequestState = RequestState(pending.dynamicRows, pending.dynamicMembers,
        answered.dynamicRows, answered.dynamicMembers)

    private fun snapshot(handle: Int): Subscription {
        val value = values.snapshot(handle)
        val header = headers[handle]
        return Subscription(header.callee, value.ref, value.group.layer, value.facts,
            stageFrom = header.from, stageTo = header.to, coveredStaticRelay = header.coveredStaticRelay)
    }
    private var index = PathTrie<Int>(ops.manager.cancellation)
    private val zeroParts = LinkedHashMap<CallerRef, Subscription>()
    private var postings = 0L
    private var visitingQuestion = false

    /** A queued replay keeps the established fresh delta, not an old cumulative root. */
    internal inner class Admission(val fresh: Subscription, private val handle: Int, val growth: Boolean = false) {
        val current: Subscription get() {
            if (handle < 0) return fresh
            return snapshot(handle)
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
        if (update.newGroup) headers += Header(sub.callee, sub.stageFrom, sub.stageTo, sub.coveredStaticRelay)
        else {
            val header = headers[update.handle]
            check(header.callee == sub.callee && header.from == sub.stageFrom && header.to == sub.stageTo) {
                "Distinct call stages require distinct caller input directories"
            }
            if (header.coveredStaticRelay && !sub.coveredStaticRelay)
                headers[update.handle] = header.copy(coveredStaticRelay = false)
        }
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
                        visit(snapshot(handle))
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
        val snapshots = ArrayList<Subscription>(ids.size + if (pub.premise.isZero) zeroParts.size else 0)
        for (id in ids) {
            ops.manager.cancellation.checkpoint()
            snapshots += snapshot(id)
        }
        if (pub.premise.isZero) snapshots.addAll(zeroParts.values)
        for (sub in snapshots) {
            ops.manager.cancellation.checkpoint()
            yield(sub)
        }
    }

    /** Called only after the runner joins. */
    fun clear() {
        check(!visitingQuestion) { "A live question visit cannot release its input directory" }
        statistics?.releaseDirectory(values.size, zeroParts.size, postings)
        values.clear()
        headers.clear()
        pending = RequestStorage()
        answered = RequestStorage()
        // A and P are owned by this directory and released with it.
        exactRequestSlots.clear()
        viewRequestSlots.clear()
        zeroParts.clear()
        index = PathTrie(ops.manager.cancellation)
        postings = 0L
    }
}
