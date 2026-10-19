package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ownedMap
import org.opentaint.dataflow.bidi.store.forEachMark
import org.opentaint.dataflow.bidi.store.RequestMarkCursor
import org.opentaint.dataflow.bidi.store.RequestMarkSelection
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.bidi.store.RequestMarkSetBuilder

/** Only non-static abstract origins use ordinary mark requests. Statics use the direct service. */
sealed interface Run1Slot {
    val base: AccessPathBase
    val pathData: IntArray

    data class Base(override val base: AccessPathBase) : Run1Slot {
        init {
            require(base != STATIC && base != AccessPathBase.Zero && base !is AccessPathBase.CleanerTemporary)
        }
        override val pathData: IntArray get() = IntArray(0)
    }

    fun initial(ap: ApManager): InitialAp = ap.initial(base, ap.path(pathData.toList()), Tail.STAR,
        ExclusionSet.Empty, MarkSlot.STAR)

    companion object {
        fun fromInitial(initial: InitialAp): Run1Slot? {
            if (initial.tail != Tail.STAR || initial.mark != MarkSlot.STAR ||
                initial.exclusion != ExclusionSet.Empty) return null
            val path = initial.pathData
            return if (initial.base != STATIC && path.isEmpty() && !initial.isZero &&
                initial.base !is AccessPathBase.CleanerTemporary) Base(initial.base) else null
        }
    }
}

typealias RequestRows = Map<Run1Slot, RequestMarkSet>

/** Borrowed merged mark set. No frozen or precomputed static segment is retained. */
data class RequestRowView(val marks: RequestMarkSet) : RequestMarkSelection {
    val isEmpty: Boolean get() = marks.isEmpty
    override fun containsId(id: Int): Boolean = marks.containsId(id)
    operator fun contains(mark: TaintMark): Boolean = mark in marks
    fun forEachMark(visit: (TaintMark) -> Unit) = marks.forEachMark(visit)
    fun countMarks(checkpoint: () -> Unit): Int { checkpoint(); return marks.size }
    override fun cursor(): RequestMarkCursor = marks.cursor()
}

data class RequestedRow(val slot: Run1Slot, val marks: RequestRowView)

fun RequestMarkSelection.minus(admitted: RequestMarkSelection, checkpoint: () -> Unit = {}): RequestMarkSet {
    return minus(admitted, null, checkpoint)
}

internal fun RequestMarkSelection.minus(admitted: RequestMarkSelection, counts: RequestAdmissionCounts?,
                                        checkpoint: () -> Unit): RequestMarkSet {
    val fresh = RequestMarkSetBuilder()
    val cursor = cursor()
    while (!cursor.isComplete) cursor.step(256) { mark ->
        checkpoint()
        if (counts != null) counts.offeredMembers++
        if (admitted.containsId(mark.id)) {
            if (counts != null) counts.authoritativeDuplicates++
        } else fresh.add(mark)
    }
    checkpoint()
    return fresh.takeSnapshot()
}

internal fun RequestMarkSelection.toRequestMarkSet(checkpoint: () -> Unit): RequestMarkSet {
    if (this is RequestMarkSet) return this
    val out = RequestMarkSetBuilder()
    val cursor = cursor()
    while (!cursor.isComplete) cursor.step(256) { checkpoint(); out.add(it) }
    checkpoint()
    return out.takeSnapshot()
}

internal fun unionRequestMarks(a: RequestMarkSet, b: RequestMarkSet, checkpoint: () -> Unit): RequestMarkSet {
    if (a.isEmpty) return b
    if (b.isEmpty || a === b) return a
    val larger = if (a.size >= b.size) a else b
    val other = if (larger === a) b else a
    var subset = true
    other.forEachMark { checkpoint(); if (it !in larger) subset = false }
    if (subset) return larger
    return RequestMarkSetBuilder().apply {
        larger.forEachMark { checkpoint(); add(it) }
        other.forEachMark { checkpoint(); add(it) }
    }.takeSnapshot()
}

/** One owner keeps merged non-static rows. Temporary and committed stores use the same type. */
class RequestStorage {
    private val additions = LinkedHashMap<Run1Slot, RequestMarkSet>()
    private var addedMembers = 0L
    val isEmpty: Boolean get() = additions.isEmpty()
    val dynamicRows: Int get() = additions.size
    val dynamicMembers: Long get() = addedMembers

    fun borrow(slot: Run1Slot): RequestRowView = RequestRowView(additions[slot] ?: RequestMarkSet.EMPTY)

    fun addAll(slot: Run1Slot, offered: RequestMarkSelection, checkpoint: () -> Unit = {}): RequestMarkSet {
        return addAll(slot, offered, null, checkpoint)
    }

    internal fun addAll(slot: Run1Slot, offered: RequestMarkSelection, counts: RequestAdmissionCounts?,
                        checkpoint: () -> Unit): RequestMarkSet {
        val old = borrow(slot)
        val fresh = RequestMarkSetBuilder()
        val cursor = offered.cursor()
        while (!cursor.isComplete) cursor.step(256) { mark ->
            checkpoint()
            if (mark in old) {
                if (counts != null) counts.temporaryDuplicates++
            } else fresh.add(mark)
        }
        if (fresh.isEmpty) return RequestMarkSet.EMPTY
        checkpoint()
        val delta = fresh.takeSnapshot()
        val prior = additions[slot]
        additions[slot] = if (prior == null) delta else RequestMarkSetBuilder().apply {
            prior.forEachMark { checkpoint(); add(it) }
            delta.forEachMark { checkpoint(); add(it) }
        }.takeSnapshot()
        addedMembers = Math.addExact(addedMembers, delta.size.toLong())
        checkpoint()
        return delta
    }

    /** Only the synchronous owner flusher may iterate this borrowed map. */
    fun pendingRows(): RequestRows {
        return java.util.Collections.unmodifiableMap(additions)
    }

    /** Hold SummaryStorage's lock until the complete descriptor list is built. No actor callbacks. */
    fun borrowMatchingRows(input: Facts, checkpoint: () -> Unit): List<RequestedRow> =
        borrowMatchingRows(input, checkpoint, null)

    fun borrowMatchingRows(input: Facts, checkpoint: () -> Unit, counts: RequestMatchCounts?): List<RequestedRow> = buildList {
        checkpoint()
        val root: TrieNode<*> = when (input) {
            is Reach -> return@buildList
            is FlowTree -> input.root
            is TaintTree -> input.root
        }
        if (root.isEmpty) return@buildList
        fun addSlot(slot: Run1Slot) {
            checkpoint()
            val row = borrow(slot)
            if (!row.isEmpty) add(RequestedRow(slot, row))
        }
        if (input.base == STATIC) return@buildList
        if (counts != null) counts.directLookups++
        addSlot(Run1Slot.Base(input.base))
    }

}

/** Per-call numeric aggregation; no atomic operation for individual candidate rows. */
class RequestMatchCounts {
    var directLookups: Long = 0
    var broadScans: Long = 0
    var scannedKeys: Long = 0
    var frozenSlotsExamined: Long = 0
    var dynamicKeysExamined: Long = 0
}

/** One actor collection attempt; populated only inside its existing R/P membership passes. */
internal class RequestAdmissionCounts {
    var offeredMembers = 0L
    var authoritativeDuplicates = 0L
    var temporaryDuplicates = 0L
    var freshTemporaryMembers = 0L
}

internal fun freezeRequestRows(rows: Map<Run1Slot, RequestMarkSet>): RequestRows = ownedMap(rows)
