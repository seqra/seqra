package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.*

/** One actor owns pending changes and at most one detached request-join phase. */
internal class RequestFrontier(
    private val ops: ApOps,
    private val requests: RequestStore,
    private val links: AddedFactStore,
    private val answer: (InitialAp) -> Unit,
    private val send: (MethodKey, InitialAp, RequestMarkSet) -> Boolean,
    private val statistics: RequestBatchStatistics?,
) {
    private class PendingMarks(val initial: InitialAp, val marks: RequestMarkSetBuilder = RequestMarkSetBuilder())
    private data class PendingLink(val ref: CallerRef, val group: GroupKey, val facts: Facts)
    private data class Target(val method: MethodKey, val initial: InitialAp)
    private var freshRequests = LinkedHashMap<InitialAp, PendingMarks>()
    private var freshLinks = LinkedHashMap<Int, PendingLink>()
    private var pendingMembers = 0L
    private var pendingCapacity = 0L
    private var firstPendingAt = 0L
    private var detachedHorizon = 0
    private var phase: Phase? = null
    private var released = false
    private var flushing = false

    val hasPending: Boolean get() = phase != null || freshRequests.isNotEmpty() || freshLinks.isNotEmpty()

    fun add(initial: InitialAp, fresh: TaintMark) {
        check(!released)
        markPendingTime()
        val row = freshRequests.getOrPut(initial) { PendingMarks(initial) }
        val oldCapacity = row.marks.primitiveCapacity
        check(row.marks.add(fresh)) { "The standing store must supply only fresh request memberships" }
        pendingMembers++
        pendingCapacity += row.marks.primitiveCapacity - oldCapacity
        observePending()
    }

    fun add(initial: InitialAp, fresh: RequestMarkSet) {
        check(!released)
        if (fresh.isEmpty) return
        markPendingTime()
        val row = freshRequests.getOrPut(initial) { PendingMarks(initial) }
        val oldCapacity = row.marks.primitiveCapacity
        row.marks.addAll(fresh)
        pendingMembers += fresh.size
        pendingCapacity += row.marks.primitiveCapacity - oldCapacity
        observePending()
    }

    fun add(update: AddedFactStore.Update) {
        check(!released)
        if (update.delta is Reach) return
        markPendingTime()
        val old = freshLinks[update.handle]
        freshLinks[update.handle] = PendingLink(update.ref, update.group,
            if (old == null) update.delta else links.mergePending(update.group, old.facts, update.delta))
        observePending()
    }

    /** Budgets include cursor preparation, group/member scans and outgoing snapshot construction. */
    fun flush(quantum: Int): Boolean {
        require(quantum > 0) { "The request-work quantum must be positive" }
        check(!released)
        check(!flushing) { "A request phase cannot recursively flush itself" }
        flushing = true
        val counts = statistics?.let { RequestBatchStatistics.Counts() }
        var remaining = quantum
        try {
            while (remaining > 0 && hasPending) {
                ops.manager.cancellation.checkpoint()
                var at = phase
                if (at == null) {
                    val horizon = requests.membershipHorizon
                    at = Phase(freshRequests, freshLinks, detachedHorizon,
                        links.beginSnapshot(), pendingMembers, pendingCapacity, firstPendingAt)
                    phase = at
                    freshRequests = LinkedHashMap()
                    freshLinks = LinkedHashMap()
                    pendingMembers = 0L
                    pendingCapacity = 0L
                    firstPendingAt = 0L
                    detachedHorizon = horizon
                    if (counts != null) counts.phases++
                    remaining--
                    observePending()
                    if (remaining == 0) break
                }
                val used = at.step(remaining, counts)
                check(used in 0..remaining)
                remaining -= used
                if (at.complete) {
                    at.close()
                    phase = null
                    observePending()
                } else if (used == 0) error("A request cursor must consume a positive budget before yielding")
            }
            if (hasPending && counts != null) counts.continuations++
            return !hasPending
        } finally {
            flushing = false
            if (counts != null) statistics?.record(counts)
            observePending()
        }
    }

    private fun markPendingTime() {
        if (firstPendingAt == 0L) firstPendingAt = System.nanoTime()
    }

    private fun observePending() {
        val at = phase
        statistics?.pending(pendingMembers + (at?.members ?: 0L),
            pendingCapacity + (at?.capacity ?: 0L),
            freshLinks.size.toLong() + (at?.linkCount ?: 0L),
            freshLinks.size.toLong() + (at?.linkCount ?: 0L), at?.capturedRoots ?: 0L)
    }

    /** No callback runs while taking the horizons. Callback admissions enter the next frontier. */
    private inner class Phase(
        private val requestChanges: LinkedHashMap<InitialAp, PendingMarks>,
        private val linkChanges: LinkedHashMap<Int, PendingLink>,
        private val oldRequestHorizon: Int,
        private val view: AddedFactStore.View,
        var members: Long,
        var capacity: Long,
        private val offeredAt: Long,
    ) {
        private val requestIterator = requestChanges.entries.iterator()
        private val linkIterator = linkChanges.entries.iterator()
        private var stage = 0
        private var initial: InitialAp? = null
        private var selected: RequestMarkSet? = null
        private var preparing: RequestMarkSnapshotCursor? = null
        private var preparingCapacity = 0
        private var groups: AddedFactStore.GroupCursor? = null
        private var currentLink: PendingLink? = null
        private var rows: RequestStore.RowCursor? = null
        private var joining: RequestGroupCursor? = null
        private val outgoing = LinkedHashMap<Target, RequestMarkSetBuilder>()
        private var outgoingIterator: MutableIterator<MutableMap.MutableEntry<Target, RequestMarkSetBuilder>>? = null
        private var outgoingTarget: Target? = null
        private var emitting: RequestMarkSnapshotCursor? = null
        private var emittingCapacity = 0
        var complete = false
            private set
        val linkCount: Long get() = linkChanges.size.toLong() + if (currentLink == null) 0 else 1
        val capturedRoots: Long get() = view.retainedSnapshotCount.toLong()

        fun step(budget: Int, counts: RequestBatchStatistics.Counts?): Int {
            var remaining = budget
            if (offeredAt != 0L) statistics?.serviceDelay(System.nanoTime() - offeredAt)
            while (remaining > 0 && !complete) {
                ops.manager.cancellation.checkpoint()
                val activeJoin = joining
                if (activeJoin != null) {
                    val oldScans = activeJoin.memberScans
                    val oldGeometry = activeJoin.geometryTests
                    val used = activeJoin.step(remaining,
                        onAnswer = {
                            if (counts != null) counts.answers++
                            answer(it)
                        }, onClimb = { mark ->
                            if (counts != null) counts.climbs++
                            val caller = checkNotNull(joinCaller)
                            val target = Target(caller.caller, caller.premise as InitialAp)
                            val marks = outgoing.getOrPut(target) { RequestMarkSetBuilder() }
                            val oldCapacity = marks.primitiveCapacity
                            if (marks.add(mark)) members++
                            else if (counts != null) counts.duplicateTargetUnions++
                            capacity += marks.primitiveCapacity - oldCapacity
                        })
                    if (counts != null) {
                        counts.memberScans += activeJoin.memberScans - oldScans
                        counts.groupGeometrySteps += activeJoin.geometryTests - oldGeometry
                    }
                    remaining -= used
                    if (activeJoin.isComplete) { joining = null; joinCaller = null }
                    else if (used == 0) error("A request group cursor did not progress")
                    continue
                }
                when (stage) {
                    0 -> {
                        val snapshot = preparing
                        if (snapshot != null) {
                            val used = snapshot.step(remaining)
                            capacity += snapshot.primitiveCapacity - preparingCapacity
                            preparingCapacity = snapshot.primitiveCapacity
                            remaining -= used
                            observePending()
                            if (snapshot.isComplete) {
                                val marks = snapshot.result
                                selected = marks
                                capacity += marks.primitiveCapacity - preparingCapacity
                                preparing = null
                                val rowInitial = checkNotNull(initial)
                                groups = view.groupsNear(rowInitial.base, rowInitial.path)
                            } else if (used == 0) error("A request snapshot cursor did not progress")
                        } else if (groups != null) {
                            val cursor = checkNotNull(groups)
                            if (cursor.isComplete) {
                                groups = null
                                members -= checkNotNull(selected).size
                                capacity -= checkNotNull(selected).primitiveCapacity
                                selected = null
                                initial = null
                                remaining--
                                continue
                            }
                            val used = cursor.step(1) { group ->
                                joinCaller = group.ref
                                if (counts != null) counts.groupGeometryTests++
                                joining = ops.requestGroup(checkNotNull(initial), checkNotNull(selected), group.facts, group.ref)
                            }
                            remaining -= used
                            if (cursor.isComplete && joining == null) {
                                groups = null
                                members -= checkNotNull(selected).size
                                capacity -= checkNotNull(selected).primitiveCapacity
                                selected = null
                                initial = null
                            } else if (used == 0 && joining == null) error("A request group directory did not progress")
                        } else if (requestIterator.hasNext()) {
                            val entry = requestIterator.next().value
                            requestIterator.remove()
                            initial = entry.initial
                            preparingCapacity = entry.marks.primitiveCapacity
                            preparing = entry.marks.snapshotCursor()
                            capacity += checkNotNull(preparing).primitiveCapacity - preparingCapacity
                            preparingCapacity = checkNotNull(preparing).primitiveCapacity
                            observePending()
                            remaining--
                        } else {
                            stage = 1
                            remaining--
                        }
                    }
                    1 -> {
                        val cursor = rows
                        if (cursor != null) {
                            if (cursor.isComplete) {
                                rows = null
                                currentLink = null
                                remaining--
                                continue
                            }
                            val link = checkNotNull(currentLink)
                            val used = cursor.step(1) { row, rowInitial ->
                                joinCaller = link.ref
                                if (counts != null) counts.groupGeometryTests++
                                joining = ops.requestGroup(rowInitial, requests.markMembers(row, oldRequestHorizon),
                                    link.facts, link.ref)
                            }
                            remaining -= used
                            if (cursor.isComplete && joining == null) { rows = null; currentLink = null }
                            else if (used == 0 && joining == null) error("A request row directory did not progress")
                        } else if (linkIterator.hasNext() && oldRequestHorizon > 0) {
                            currentLink = linkIterator.next().value
                            linkIterator.remove()
                            rows = requests.markRowsNear(checkNotNull(currentLink).facts, oldRequestHorizon)
                            remaining--
                        } else {
                            linkChanges.clear()
                            stage = 2
                            outgoingIterator = outgoing.entries.iterator()
                            remaining--
                        }
                    }
                    2 -> {
                        val snapshot = emitting
                        if (snapshot != null) {
                            if (snapshot.isComplete) {
                                val target = checkNotNull(outgoingTarget)
                                val marks = snapshot.result
                                emitting = null
                                outgoingTarget = null
                                members -= marks.size
                                capacity -= emittingCapacity
                                ops.manager.cancellation.checkpoint()
                                if (send(target.method, target.initial, marks) && counts != null) {
                                    counts.acceptedBatch(marks.size)
                                }
                                remaining--
                                continue
                            }
                            val used = snapshot.step(remaining)
                            capacity += snapshot.primitiveCapacity - emittingCapacity
                            emittingCapacity = snapshot.primitiveCapacity
                            remaining -= used
                            observePending()
                            if (!snapshot.isComplete && used == 0) error("An outgoing request snapshot did not progress")
                        } else if (checkNotNull(outgoingIterator).hasNext()) {
                            val entry = checkNotNull(outgoingIterator).next()
                            outgoingTarget = entry.key
                            emittingCapacity = entry.value.primitiveCapacity
                            emitting = entry.value.snapshotCursor()
                            capacity += checkNotNull(emitting).primitiveCapacity - emittingCapacity
                            emittingCapacity = checkNotNull(emitting).primitiveCapacity
                            checkNotNull(outgoingIterator).remove()
                            observePending()
                            remaining--
                        } else {
                            complete = true
                            remaining--
                        }
                    }
                }
            }
            return budget - remaining
        }

        private var joinCaller: CallerRef? = null

        fun close() {
            view.close()
            requestChanges.clear()
            linkChanges.clear()
            outgoing.clear()
            preparing = null
            emitting = null
            joining = null
            rows = null
            groups = null
            currentLink = null
            selected = null
            initial = null
            joinCaller = null
            outgoingTarget = null
            outgoingIterator = null
            members = 0L
            capacity = 0L
        }
    }

    fun release() {
        if (released) return
        released = true
        phase?.close()
        phase = null
        freshRequests.clear()
        freshLinks.clear()
        pendingMembers = 0L
        pendingCapacity = 0L
    }
}
