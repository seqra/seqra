package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.interp.ownedMap
import org.opentaint.dataflow.bidi.store.*

/** One shared immutable B. Logical cardinalities are computed at freeze, never by a row getter. */
interface FrozenRequestBase {
    val isEmpty: Boolean
    val logicalRows: Long
    val logicalMembers: Long
    fun hasRow(slot: Run1Slot): Boolean
    fun borrow(slot: Run1Slot): ImmutableRequestMarks
    /** Checkpoint once per examined stored slot, including an empty selected row. */
    fun forEachStaticRow(checkpoint: () -> Unit, visit: (Run1Slot.StaticField) -> Unit)
}

object EmptyFrozenRequestBase : FrozenRequestBase {
    override val isEmpty: Boolean get() = true
    override val logicalRows: Long get() = 0
    override val logicalMembers: Long get() = 0
    override fun hasRow(slot: Run1Slot): Boolean = false
    override fun borrow(slot: Run1Slot): ImmutableRequestMarks = RequestMarkSet.EMPTY
    override fun forEachStaticRow(checkpoint: () -> Unit, visit: (Run1Slot.StaticField) -> Unit) = Unit
}

/** Reference/fixture adapter. The runtime profile builder never constructs this expanded base. */
class MapFrozenRequestBase(rows: RequestRows) : FrozenRequestBase {
    private val rows = ownedMap(rows.filterValues { !it.isEmpty })
    override val logicalRows: Long = this.rows.size.toLong()
    override val logicalMembers: Long = this.rows.values.fold(0L) { count, marks -> Math.addExact(count, marks.size.toLong()) }
    override val isEmpty: Boolean get() = rows.isEmpty()
    override fun hasRow(slot: Run1Slot): Boolean = slot in rows
    override fun borrow(slot: Run1Slot): ImmutableRequestMarks = rows[slot] ?: RequestMarkSet.EMPTY
    override fun forEachStaticRow(checkpoint: () -> Unit, visit: (Run1Slot.StaticField) -> Unit) {
        for (slot in rows.keys) { checkpoint(); if (slot is Run1Slot.StaticField) visit(slot) }
    }
}

/** Symbolic mark scopes over the legal coordinate domain. The domain is shared
 * independently of local readers; no field-request or transitive read set exists. */
internal class FrozenStaticRequests private constructor(
    internal val domainSlots: Set<Run1Slot.StaticField>,
    private val root: RequestMarkSet,
    internal val commonByClass: Map<Int, RequestMarkSet>,
    internal val byField: Map<Run1Slot.StaticField, RequestMarkSet>,
    override val logicalRows: Long,
    override val logicalMembers: Long,
    private val checkpoint: () -> Unit,
) : FrozenRequestBase {
    private fun common(cls: Int): RequestMarkSet = commonByClass[cls] ?: root
    override val isEmpty: Boolean get() = false
    override fun hasRow(slot: Run1Slot): Boolean = slot is Run1Slot.StaticField && slot in domainSlots &&
        (!common(slot.cls).isEmpty || byField[slot]?.isEmpty == false)
    override fun borrow(slot: Run1Slot): ImmutableRequestMarks {
        if (slot !is Run1Slot.StaticField || !hasRow(slot)) return RequestMarkSet.EMPTY
        return distinctUnionView(common(slot.cls), byField[slot] ?: RequestMarkSet.EMPTY, checkpoint)
    }
    override fun forEachStaticRow(checkpoint: () -> Unit, visit: (Run1Slot.StaticField) -> Unit) {
        for (slot in domainSlots) { checkpoint(); if (hasRow(slot)) visit(slot) }
    }
    internal fun visitOwnedMarks(visit: (RequestMarkSet) -> Unit) {
        visit(root)
        commonByClass.values.forEach(visit)
        byField.values.forEach(visit)
    }

    companion object {
        /** The input overlay is mutable closure scratch. Freeze its nonredundant entries exactly once. */
        fun freeze(footprint: Run1StaticFootprint, root: RequestMarkSet,
                   commonByClass: Map<Int, RequestMarkSet>, fieldMarks: Map<Run1Slot.StaticField, RequestMarkSet>,
                   checkpoint: () -> Unit): FrozenRequestBase =
            freeze(footprint.fieldUniverse, root, commonByClass, fieldMarks, checkpoint)

        fun freeze(domain: Set<Run1Slot.StaticField>, root: RequestMarkSet,
                   commonByClass: Map<Int, RequestMarkSet>, fieldMarks: Map<Run1Slot.StaticField, RequestMarkSet>,
                   checkpoint: () -> Unit): FrozenRequestBase {
            // Already-owned maps are shared; mutable constructor scratch is detached once.
            val frozenCommon = ownedMap(commonByClass)
            var rows = 0L
            var members = 0L
            val fieldsByClass = domain.groupBy { it.cls }
            for ((cls, fields) in fieldsByClass) {
                checkpoint()
                val common = frozenCommon[cls] ?: root
                if (!common.isEmpty) rows = Math.addExact(rows, fields.size.toLong())
                members = Math.addExact(members, Math.multiplyExact(fields.size.toLong(), common.size.toLong()))
            }
            val sparse = LinkedHashMap<Run1Slot.StaticField, RequestMarkSet>()
            for ((slot, marks) in fieldMarks) {
                checkpoint()
                check(slot in domain)
                val common = frozenCommon[slot.cls] ?: root
                var additional = 0L
                marks.forEachId { id -> checkpoint(); if (!common.containsId(id)) additional++ }
                if (additional != 0L) {
                    sparse[slot] = marks // partial overlaps remain views; no residual array
                    members = Math.addExact(members, additional)
                    if (common.isEmpty) rows = Math.addExact(rows, 1L)
                }
            }
            checkpoint()
            if (rows == 0L) return EmptyFrozenRequestBase
            return FrozenStaticRequests(org.opentaint.dataflow.bidi.interp.ownedSet(domain), root, frozenCommon, ownedMap(sparse),
                rows, members, checkpoint)
        }
    }
}
