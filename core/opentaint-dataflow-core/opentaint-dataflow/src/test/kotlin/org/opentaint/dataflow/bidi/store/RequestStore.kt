package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind

import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.Facts
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.PathNode
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.STATIC
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.ap.overlap
import org.opentaint.dataflow.util.Cancellation

/** Actor-owned exact standing relation. Rows, not logical memberships, enter the positional index. */
class RequestStore(
    private val cancellation: Cancellation? = null,
    private val statistics: N1Statistics? = null,
) {
    private val census = statistics?.newCensus()
    private val origins = RequestOrigins(census, cancellation)
    private val rows = RequestDirectory<Row>(census)
    private val positions = HashMap<PathNode, Int>()
    private val memberRows = RequestIntLane(census)
    private val memberKeys = RequestIntLane(census)
    private val index = RequestPathIndex(cancellation, census)
    private var released = false
    val membershipHorizon: Int get() { active(); return memberRows.size }

    data class MarkAdmission(val row: Int, val fresh: RequestMarkSet, val horizon: Int)

    /** Batch admission remains prompt and exact. Only fresh members enter the next mark frontier. */
    fun addMarks(premise: InitialAp, marks: RequestMarkSet): MarkAdmission {
        active()
        check(premise.tail == Tail.STAR && premise.exclusion == ExclusionSet.Empty && premise.mark == MarkSlot.STAR) {
            "A request needs a policy initial or position answer"
        }
        val fresh = RequestMarkSetBuilder()
        marks.forEachMark { if (add(premise, RequestKind.Mark(it))) fresh.add(it) }
        return MarkAdmission(markRow(premise), fresh.build(), memberRows.size)
    }

    fun markRow(premise: InitialAp): Int {
        active()
        val origin = origins.find(premise)
        return if (origin < 0) -1 else origins.markRow(origin)
    }

    /** A read view of the admitted prefix. Concrete TAINT marks probe it without scanning the row. */
    fun markMembers(row: Int, before: Int = membershipHorizon): RequestMarkSelection {
        active()
        require(before in 0..memberRows.size)
        val value = rows[row]
        check(value.position == null) { "A position row does not hold mark requests" }
        return value.members.before(before)
    }

    fun markRowsNear(facts: Facts, before: Int = membershipHorizon): RowCursor {
        active()
        require(before in 0..memberRows.size)
        return RowCursor(index.around(facts), rows.size, before)
    }

    inner class RowCursor internal constructor(
        private val buckets: RequestPathIndex.BucketCursor,
        private val rowHorizon: Int,
        private val memberHorizon: Int,
    ) {
        private var bucket: RequestBucket? = null
        private var offset = 0
        private var bound = 0
        val isComplete: Boolean get() = bucket == null && buckets.isComplete
        fun step(budget: Int, visit: (Int, InitialAp) -> Unit): Int {
            require(budget > 0)
            var used = 0
            while (used < budget && !isComplete) {
                active()
                val current = bucket
                if (current == null) {
                    used += buckets.step(1) { next ->
                        bucket = next; offset = 0; bound = next.rows.countBefore(rowHorizon)
                    }
                } else if (offset < bound) {
                    val handle = current.rows[offset++]
                    val row = rows[handle]
                    // The first mark membership is the first member of this row. All later members
                    // have larger ordinals. An empty old horizon needs no row hash scan.
                    if (row.position == null && row.members.firstOrdinal < memberHorizon)
                        visit(handle, origins[row.origin])
                    used++
                } else bucket = null
            }
            return used
        }
    }

    private class Row(
        val origin: Int,
        val position: RequestKind.Position?,
        val query: Pattern,
        val bucket: RequestBucket,
        census: N1Statistics.Census?,
        cancellation: Cancellation?,
    ) {
        val members = RequestMembers(census, cancellation)
    }

    private fun active() {
        check(!released) { "The request store was released" }
        cancellation?.checkpoint()
    }

    fun add(premise: InitialAp, kind: RequestKind): Boolean {
        active()
        check(premise.tail == Tail.STAR && premise.exclusion == ExclusionSet.Empty && premise.mark == MarkSlot.STAR) {
            "A request needs a policy initial or position answer"
        }
        val origin = origins.getOrAdd(premise)
        val previous = when (kind) {
            is RequestKind.Mark -> origins.markRow(origin)
            is RequestKind.Position -> positions[kind.path] ?: -1
        }
        val key = when (kind) {
            is RequestKind.Mark -> kind.mark.id
            is RequestKind.Position -> origin
        }
        if (previous >= 0 && rows[previous].members[key] >= 0) {
            statistics?.duplicate()
            return false
        }
        // All cancellation checks precede the exact membership commit. An interrupted path build
        // can leave empty topology, but never a seen member missing from its encounter lane.
        val rowHandle = if (previous >= 0) previous else {
            val query = when (kind) {
                is RequestKind.Mark -> premise.toPattern()
                is RequestKind.Position -> Pattern(PathFact(STATIC, kind.path.toList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            }
            val bucket = index.bucket(query.fact.base, query.fact.path.toIntArray())
            val handle = rows.add(Row(origin, kind as? RequestKind.Position, query, bucket, census, cancellation))
            bucket.rows.add(handle)
            when (kind) {
                is RequestKind.Mark -> origins.setMarkRow(origin, handle)
                is RequestKind.Position -> positions[kind.path] = handle
            }
            census?.row(kind is RequestKind.Mark)
            handle
        }
        cancellation?.checkpoint()
        val row = rows[rowHandle]
        val member = memberRows.size
        val previousSize = row.members.size
        row.members.put(key, member)
        memberRows.add(rowHandle)
        memberKeys.add(key)
        row.bucket.lane.add(member)
        if (row.position != null) row.bucket.positions.add(member)
        census?.member(kind is RequestKind.Mark, previousSize)
        return true
    }

    /** The raw geometric relation, including marks that cannot act. Snapshot is taken at this call. */
    fun overlapping(a: Pattern): Sequence<Pair<InitialAp, RequestKind>> {
        active()
        val horizon = memberRows.size
        val buckets = index.around(a.fact.base, a.fact.path)
        return sequence {
            for (bucket in buckets) {
                val bound = bucket.lane.countBefore(horizon)
                for (offset in 0 until bound) {
                    active()
                    val member = bucket.lane[offset]
                    val row = rows[memberRows[member]]
                    if (overlap(a, row.query)) yield(initial(row, member) to kind(row, member))
                }
            }
        }
    }

    /** Only action-eligible mark memberships, and all geometric position memberships, in raw order.
     *  One horizon and one bucket snapshot cover the whole query, including later callback visits. */
    internal fun visitActionCandidates(a: Pattern, visit: (InitialAp, RequestKind) -> Unit) {
        active()
        val horizon = memberRows.size
        val rowHorizon = rows.size
        val buckets = index.around(a.fact.base, a.fact.path)
        val counts = if (statistics == null) null else N1Statistics.Candidates()
        fun candidate(member: Int) {
            active()
            if (counts != null) counts.selected++
            val row = rows[memberRows[member]]
            if (!overlap(a, row.query)) return
            if (counts != null) counts.overlapping++
            visit(initial(row, member), kind(row, member))
        }
        try {
            for (bucket in buckets) {
                active()
                if (counts != null) counts.coarse += bucket.lane.countBefore(horizon)
                when (val mark = a.fact.mark) {
                    is MarkSlot.Star -> {
                        val bound = bucket.lane.countBefore(horizon)
                        for (offset in 0 until bound) {
                            active()
                            val member = bucket.lane[offset]
                            val row = rows[memberRows[member]]
                            if (row.position != null || TaintMark(memberKeys[member]) !in mark.excluded) candidate(member)
                        }
                    }
                    is MarkSlot.Concrete -> {
                        // Mark rows contribute at most one membership each. Position memberships have
                        // their own ordered lane; merge it without scanning unrelated requested marks.
                        var singleton = -1
                        var selected: RequestIntLane? = null
                        val rowBound = bucket.rows.countBefore(rowHorizon)
                        for (offset in 0 until rowBound) {
                            active()
                            val row = rows[bucket.rows[offset]]
                            if (row.position != null) continue
                            val member = row.members[mark.mark.id]
                            if (member >= 0 && member < horizon) {
                                if (singleton < 0) singleton = member
                                else {
                                    val lane = selected ?: RequestIntLane(null).also { it.add(singleton); selected = it }
                                    lane.add(member)
                                }
                            }
                        }
                        val markMembers = selected?.sorted()
                        val markCount = markMembers?.size ?: if (singleton >= 0) 1 else 0
                        val positionBound = bucket.positions.countBefore(horizon)
                        var markOffset = 0
                        var positionOffset = 0
                        while (markOffset < markCount || positionOffset < positionBound) {
                            val markMember = if (markOffset < markCount) markMembers?.get(markOffset) ?: singleton else Int.MAX_VALUE
                            val positionMember = if (positionOffset < positionBound) bucket.positions[positionOffset] else Int.MAX_VALUE
                            if (markMember < positionMember) { candidate(markMember); markOffset++ }
                            else { candidate(positionMember); positionOffset++ }
                        }
                    }
                }
            }
        } finally {
            if (counts != null) statistics?.candidates(counts)
        }
    }

    /** Position joins stay prompt, without scanning or expanding unrelated mark requests. */
    fun visitPositionCandidates(a: Pattern, visit: (InitialAp, RequestKind.Position) -> Unit) {
        active()
        val horizon = memberRows.size
        val buckets = index.around(a.fact.base, a.fact.path)
        for (bucket in buckets) {
            val bound = bucket.positions.countBefore(horizon)
            for (offset in 0 until bound) {
                active()
                val member = bucket.positions[offset]
                val row = rows[memberRows[member]]
                if (overlap(a, row.query)) visit(initial(row, member), checkNotNull(row.position))
            }
        }
    }

    internal fun query(premise: InitialAp, kind: RequestKind): Pattern {
        active()
        val handle = when (kind) {
            is RequestKind.Mark -> origins.markRow(origins.find(premise))
            is RequestKind.Position -> positions.getValue(kind.path)
        }
        return rows[handle].query
    }

    private fun initial(row: Row, member: Int): InitialAp = origins[if (row.position == null) row.origin else memberKeys[member]]
    private fun kind(row: Row, member: Int): RequestKind = row.position ?: RequestKind.Mark(TaintMark(memberKeys[member]))

    /** Called only after the actor's run has joined. No standing member is retired during analysis. */
    internal fun release() {
        if (released) return
        released = true
        origins.clear(); rows.clear(); positions.clear(); memberRows.clear(); memberKeys.clear(); index.clear()
        census?.release()
    }
}
