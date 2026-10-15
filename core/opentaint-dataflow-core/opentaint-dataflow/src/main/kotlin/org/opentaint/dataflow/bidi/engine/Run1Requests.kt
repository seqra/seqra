package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ownedMap
import org.opentaint.dataflow.bidi.store.RequestMarkCursor
import org.opentaint.dataflow.bidi.store.RequestMarkSelection
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.bidi.store.RequestMarkSetBuilder

/** F78 has two request slots. Full MethodKey ownership belongs to SummaryStorage. */
sealed interface Run1Slot {
    val base: AccessPathBase
    val pathData: IntArray

    data class Base(override val base: AccessPathBase) : Run1Slot {
        init {
            require(base != STATIC && base != AccessPathBase.Zero && base !is AccessPathBase.CleanerTemporary)
        }
        override val pathData: IntArray get() = IntArray(0)
    }

    data class StaticField(val cls: Int, val field: Int) : Run1Slot {
        init { require(cls.isClass() && field.isFieldAccessor()) }
        override val base: AccessPathBase get() = STATIC
        override val pathData: IntArray get() = intArrayOf(cls, this.field)
    }

    fun initial(ap: ApManager): InitialAp = ap.initial(base, ap.path(pathData.toList()), Tail.STAR,
        ExclusionSet.Empty, MarkSlot.STAR)

    companion object {
        fun fromInitial(initial: InitialAp): Run1Slot? {
            if (initial.tail != Tail.STAR || initial.mark != MarkSlot.STAR ||
                initial.exclusion != ExclusionSet.Empty) return null
            val path = initial.pathData
            return if (initial.base == STATIC) {
                if (path.size == 2 && path[0].isClass() && path[1].isFieldAccessor()) StaticField(path[0], path[1]) else null
            } else if (path.isEmpty() && !initial.isZero && initial.base !is AccessPathBase.CleanerTemporary)
                Base(initial.base) else null
        }
    }
}

typealias RequestRows = Map<Run1Slot, RequestMarkSet>

/** Borrowed immutable segments, never a materialized copy of cumulative B union D. */
data class RequestRowView(val precomputed: RequestMarkSet, val dynamic: RequestMarkSet) : RequestMarkSelection {
    val isEmpty: Boolean get() = precomputed.isEmpty && dynamic.isEmpty
    val size: Int get() = Math.addExact(precomputed.size, dynamic.size)
    override fun containsId(id: Int): Boolean = precomputed.containsId(id) || dynamic.containsId(id)
    operator fun contains(mark: TaintMark): Boolean = containsId(mark.id)
    fun forEachMark(visit: (TaintMark) -> Unit) { precomputed.forEachMark(visit); dynamic.forEachMark(visit) }
    override fun cursor(): RequestMarkCursor = object : RequestMarkCursor {
        private val first = precomputed.cursor()
        private val second = dynamic.cursor()
        override val isComplete: Boolean get() = first.isComplete && second.isComplete
        override fun step(budget: Int, visit: (TaintMark) -> Unit): Int {
            require(budget > 0)
            val used = if (first.isComplete) 0 else first.step(budget, visit)
            return used + if (used < budget) second.step(budget - used, visit) else 0
        }
    }
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

/** One writer. R borrows a frozen base; P uses this same class with the empty base. */
class RequestStorage(private val sharedBase: RequestRows = emptyMap()) {
    private val additions = LinkedHashMap<Run1Slot, RequestMarkSet>()
    private var addedMembers = 0L
    val isEmpty: Boolean get() = sharedBase.isEmpty() && additions.isEmpty()
    val dynamicRows: Int get() = additions.size
    val dynamicMembers: Long get() = addedMembers
    val precomputedRows: Int get() = sharedBase.size
    val precomputedMembers: Long get() = sharedBase.values.sumOf { it.size.toLong() }

    fun borrow(slot: Run1Slot): RequestRowView = RequestRowView(
        sharedBase[slot] ?: RequestMarkSet.EMPTY, additions[slot] ?: RequestMarkSet.EMPTY)

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
        check(sharedBase.isEmpty()) { "Only empty-base temporary storage can be committed" }
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
        if (input.base != STATIC) { if (counts != null) counts.directLookups++; addSlot(Run1Slot.Base(input.base)); return@buildList }
        if (root.hasRequestBroadLeaf()) {
            forEachStaticSlot(checkpoint, counts) { addSlot(it) }
            return@buildList
        }
        var broadClass = false
        root.requestChildren(checkpoint) { cls, node ->
            if (node.hasRequestBroadLeaf()) broadClass = true
            else node.requestChildren(checkpoint) { field, child ->
                if (!child.isEmpty) { if (counts != null) counts.directLookups++; addSlot(Run1Slot.StaticField(cls, field)) }
            }
        }
        if (broadClass) forEachStaticSlot(checkpoint, counts) { slot ->
            if (root.child(slot.cls)?.hasRequestBroadLeaf() == true) addSlot(slot)
        }
    }

    private fun forEachStaticSlot(checkpoint: () -> Unit, counts: RequestMatchCounts?, visit: (Run1Slot.StaticField) -> Unit) {
        if (counts != null) counts.broadScans++
        for (slot in sharedBase.keys) { checkpoint(); if (counts != null) counts.scannedKeys++; if (slot is Run1Slot.StaticField) visit(slot) }
        for (slot in additions.keys) {
            checkpoint()
            if (counts != null) counts.scannedKeys++
            if (slot is Run1Slot.StaticField && slot !in sharedBase) visit(slot)
        }
    }
}

/** Per-call numeric aggregation; no atomic operation for individual candidate rows. */
class RequestMatchCounts {
    var directLookups: Long = 0
    var broadScans: Long = 0
    var scannedKeys: Long = 0
}

/** One actor collection attempt; populated only inside its existing R/P membership passes. */
internal class RequestAdmissionCounts {
    var offeredMembers = 0L
    var authoritativeDuplicates = 0L
    var temporaryDuplicates = 0L
    var freshTemporaryMembers = 0L
}

internal fun TrieNode<*>.hasRequestBroadLeaf(): Boolean = when (val value = leaf) {
    is FlowLeaf -> !value.isEmpty
    is TaintLeaves -> !value.any.isEmpty
    else -> error("Unexpected request supplier leaf")
}

internal inline fun TrieNode<*>.requestChildren(checkpoint: () -> Unit, visit: (Int, TrieNode<*>) -> Unit) {
    val keys = accessorData ?: return
    val nodes = childData!!
    for (offset in keys.indices) { checkpoint(); visit(keys[offset], nodes[offset]) }
}

internal fun freezeRequestRows(rows: Map<Run1Slot, RequestMarkSet>): RequestRows = ownedMap(rows)
