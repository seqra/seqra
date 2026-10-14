package org.opentaint.dataflow.bidi.engine

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.*

/** Run-1 initial emission owns mark interests and bounded, completed deliveries.
 * Immutable policies share captured phases; no request rows or membership directories exist.
 */
internal class Run1Abstraction(
    private val ops: ApOps,
    private val links: AddedFactStore,
    private val profile: Run1StaticProfile?,
    private val answer: (InitialAp) -> Unit,
    private val send: (MethodKey, InitialAp, RequestMarkSet) -> Boolean,
    private val covered: (MethodKey, InitialAp, TaintMark) -> Boolean,
    private val statistics: RequestBatchStatistics?,
    private val policyStatistics: Run1AbstractionStatistics?,
) {
    private class PendingMarks(val initial: InitialAp, val marks: RequestMarkSetBuilder = RequestMarkSetBuilder())
    private data class PendingLink(val ref: CallerRef, val group: GroupKey, val facts: Facts)
    private data class Target(val method: MethodKey, val initial: InitialAp)
    private var freshRequests = LinkedHashMap<InitialAp, PendingMarks>()
    private var freshLinks = LinkedHashMap<Int, PendingLink>()
    private var pendingMembers = 0L
    private var pendingCapacity = 0L
    private var firstPendingAt = 0L
    private var policies: PersistentMap<AccessPathBase, PersistentMap<InitialAp, RequestMarkSet>> = persistentHashMapOf()
    private var committed: PersistentMap<AccessPathBase, PersistentMap<InitialAp, RequestMarkSet>> = persistentHashMapOf()
    private var phase: Phase? = null
    private var released = false
    private var flushing = false
    private var policyCount = 0L
    private var policyMembers = 0L

    val hasPending: Boolean get() = phase != null || freshRequests.isNotEmpty() || freshLinks.isNotEmpty()

    /** The canonical abstract root is always active; bounded supplier work adds refinements. */
    fun emit(delta: Facts) {
        ops.manager.cancellation.checkpoint()
        answer(ops.policy(delta))
    }

    fun requireMark(initial: InitialAp, mark: TaintMark) {
        validateInitial(initial)
        if (policies[initial.base]?.get(initial)?.contains(mark) == true || profile?.covers(initial, mark) == true) {
            policyStatistics?.admission(newPolicy = false, fresh = 0, duplicates = 1)
            return
        }
        requireMarks(initial, RequestMarkSet.of(mark))
    }

    private fun validateInitial(initial: InitialAp) {
        check(!released)
        ops.manager.cancellation.checkpoint()
        check(initial.tail == Tail.STAR && initial.exclusion == ExclusionSet.Empty && initial.mark == MarkSlot.STAR) {
            "A run-1 mark interest needs an abstract initial with an empty exclusion"
        }
    }

    /** Interest stays active for late suppliers, while each fresh delivery is released after joining. */
    fun requireMarks(initial: InitialAp, marks: RequestMarkSet) {
        validateInitial(initial)
        val basePolicies = policies[initial.base] ?: persistentHashMapOf()
        val old = basePolicies[initial] ?: RequestMarkSet.EMPTY
        var fresh: RequestMarkSetBuilder? = null
        marks.forEachMark { mark ->
            ops.manager.cancellation.checkpoint()
            if (mark !in old && !(profile?.covers(initial, mark) == true)) (fresh ?: RequestMarkSetBuilder().also { fresh = it }).add(mark)
        }
        val delta = fresh?.takeSnapshot() ?: RequestMarkSet.EMPTY
        policyStatistics?.admission(old.isEmpty && !delta.isEmpty, delta.size, marks.size - delta.size)
        if (delta.isEmpty) return
        if (old.isEmpty) policyCount++
        policyMembers += delta.size
        val merged = if (old.isEmpty) delta else RequestMarkSetBuilder().apply {
            addAll(old); addAll(delta)
        }.takeSnapshot()
        policies = policies.put(initial.base, basePolicies.put(initial, merged))
        markPendingTime()
        val row = freshRequests.getOrPut(initial) { PendingMarks(initial) }
        val oldCapacity = row.marks.primitiveCapacity
        row.marks.addAll(delta)
        pendingMembers += delta.size
        pendingCapacity += row.marks.primitiveCapacity - oldCapacity
        observePending()
    }

    fun add(update: AddedFactStore.Update) {
        check(!released)
        if (update.delta is Reach || (policies[update.delta.base].isNullOrEmpty() &&
            (update.delta.base != STATIC || profile == null ||
                (profile.abstractions.isEmpty() && profile.markInterests.isEmpty() && profile.cleanerMarkCount == 0)))) return
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
                    at = Phase(freshRequests, freshLinks, committed,
                        links.beginSnapshot(), pendingMembers, pendingCapacity, firstPendingAt)
                    phase = at
                    freshRequests = LinkedHashMap()
                    freshLinks = LinkedHashMap()
                    pendingMembers = 0L
                    pendingCapacity = 0L
                    firstPendingAt = 0L
                    committed = policies
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
        private val oldPolicies: PersistentMap<AccessPathBase, PersistentMap<InitialAp, RequestMarkSet>>,
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
        private var structure: StaticSupplierCursor? = null
        private var catalog: StaticSupplierCursor? = null
        private var catalogCleaner = false
        private var rows: Iterator<Map.Entry<InitialAp, RequestMarkSet>>? = null
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
                        }, onClimb = climb@ { mark ->
                            if (counts != null) counts.climbs++
                            val caller = checkNotNull(joinCaller)
                            val target = Target(caller.caller, caller.premise as InitialAp)
                            if (covered(target.method, target.initial, mark)) {
                                policyStatistics?.covered()
                                return@climb
                            }
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
                        val structural = structure
                        val cursor = rows
                        if (structural != null) {
                            val used = structural.step(remaining, position = { slot ->
                                policyStatistics?.structural()
                                answer(ops.manager.initial(STATIC, ops.manager.path(slot), Tail.STAR,
                                    ExclusionSet.Empty, MarkSlot.STAR))
                            }, interest = { error("A structural cursor cannot emit a mark interest") })
                            remaining -= used
                            if (counts != null) counts.groupGeometrySteps += used
                            if (structural.isComplete) structure = null
                            else if (used == 0) error("A structural cursor did not progress")
                        } else if (cursor != null && cursor.hasNext()) {
                            val row = cursor.next()
                            val link = checkNotNull(currentLink)
                            joinCaller = link.ref
                            if (counts != null) counts.groupGeometryTests++
                            joining = ops.requestGroup(row.key, row.value, link.facts, link.ref)
                            remaining--
                        } else if (catalog != null) {
                            val cursor = checkNotNull(catalog)
                            val used = cursor.step(1, position = { error("A mark cursor cannot emit a structural slot") },
                                interest = { interest ->
                                    val link = checkNotNull(currentLink)
                                    val initial = ops.manager.initial(STATIC, ops.manager.path(interest.slot),
                                        Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
                                    joinCaller = link.ref
                                    joining = RequestGroupCursor(ops.manager, initial, interest.marks,
                                        link.facts, link.ref, interest.pattern)
                                    if (counts != null) counts.groupGeometryTests++
                                })
                            remaining -= used
                            if (counts != null) counts.groupGeometrySteps += used
                            if (cursor.isComplete) catalog = null
                            else if (used == 0) error("A catalog cursor did not progress")
                        } else if (catalogCleaner) {
                            catalogCleaner = false
                            val link = checkNotNull(currentLink)
                            joinCaller = link.ref
                            joining = ops.requestGroup(ops.manager.initial(STATIC, null, Tail.STAR,
                                ExclusionSet.Empty, MarkSlot.STAR), checkNotNull(profile).cleanerMarks, link.facts, link.ref)
                            if (counts != null) counts.groupGeometryTests++
                            remaining--
                        } else if (linkIterator.hasNext()) {
                            currentLink = linkIterator.next().value
                            linkIterator.remove()
                            val facts = checkNotNull(currentLink).facts
                            rows = oldPolicies[facts.base]?.entries?.iterator()
                            structure = if (facts.base == STATIC && profile != null && profile.abstractions.isNotEmpty())
                                StaticSupplierCursor(facts, profile, ops.manager, positions = true) else null
                            catalog = if (facts.base == STATIC && profile != null && profile.markInterests.isNotEmpty())
                                StaticSupplierCursor(facts, profile, ops.manager, positions = false) else null
                            catalogCleaner = facts.base == STATIC && (profile?.cleanerMarkCount ?: 0) > 0
                            remaining--
                        } else {
                            rows = null
                            currentLink = null
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
            structure = null
            catalog = null
            catalogCleaner = false
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
        policyStatistics?.release(policyCount, policyMembers)
        policyCount = 0L
        policyMembers = 0L
        phase?.close()
        phase = null
        freshRequests.clear()
        freshLinks.clear()
        policies = persistentHashMapOf()
        committed = persistentHashMapOf()
        pendingMembers = 0L
        pendingCapacity = 0L
    }
}
