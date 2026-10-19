package org.opentaint.dataflow.bidi.engine.historical

// Frozen a80 protocol law model. Never included in the production artifact.
import org.opentaint.dataflow.bidi.engine.*

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.forEachMark
import org.opentaint.dataflow.bidi.store.Record
import org.opentaint.dataflow.bidi.store.RequestMarkSetBuilder

/** Replay and delivery use one complete AP predicate after their candidate scans. */
fun matches(sub: Subscription, pub: Publication, member: Int, ops: ApOps, mode: ApMode): Facts? {
    ops.manager.cancellation.checkpoint()
    return if (sub.zeroOnly) sub.added.takeIf { pub.premise.isZero }
    else ops.satisfying(sub.added, pub.premise.member(member), mode)
}

/** Only notify runs on a publisher's thread; all subscription state is runner-owned. */
class SubscriptionManager(port: SubscriptionPort) {
    private fun historicalMode(port: SubscriptionPort) =
        ApMode(port.config.run1, port.config.direction, port.config.fieldLimit, MarkMode.FULL)

    @Volatile private var port: SubscriptionPort? = port
    private val byCallee = LinkedHashMap<MethodKey, CalleeSubscriptions>()

    private fun active(): SubscriptionPort = checkNotNull(port) { "The subscriptions were released" }.also {
        it.ops.manager.cancellation.checkpoint()
    }

    fun subscribe(sub: Subscription) {
        val port = active()
        val storage = port.summaryStorage(sub.callee)
        val entry = byCallee.getOrPut(sub.callee) {
            CalleeSubscriptions(port.ops, port.statistics).also { storage.addSubscriber(this) }
        }
        val admitted = entry.admit(sub) ?: return
        val statistics = port.statistics
        if (statistics == null) {
            port.steps.replay { replay(storage, admitted) }
            return
        }
        statistics.replayQueued()
        var entered = false
        try {
            port.steps.replay {
                entered = true
                try { replay(storage, admitted) }
                finally { statistics.replayFinished() }
            }
        } catch (error: Throwable) {
            // Inline failures release in the callback; admission failures release here.
            if (!entered) statistics.replayFinished()
            throw error
        }
    }

    private fun replay(storage: SummaryStorage, admitted: CalleeSubscriptions.Admission) {
        val port = active()
        val part = admitted.fresh
        for ((pub, member) in storage.candidates(part, historicalMode(port))) {
            port.ops.manager.cancellation.checkpoint()
            applyMatch(part, pub, member)
        }
        if (!part.zeroOnly && port.config.restricted) replayRecords(part)
        if (port.config.run1 && !part.zeroOnly) {
            // Registration precedes this read. FLOW joins its original accumulated
            // header; new TAINT is answered only by LinkIn at the callee owner.
            val current = admitted.current
            checkKinds(current)
            if (current.added is FlowTree && normalRun1Slot(current.ref.premise) != null) {
                port.abstractionStatistics?.freshFlowInputVisit(admitted.growth)
                val rows = storage.borrowMatchingRequests(current.added)
                joinRequests(rows) { visit -> visit(current) }
            }
        }
    }

    private data class ClimbTarget(val method: MethodKey, val slot: Run1Slot)
    private class ReplayCounts {
        var candidates = 0L
        var tests = 0L
        var answers = 0L
        var climbs = 0L
    }

    private fun checkKinds(part: Subscription) {
        check((part.added is FlowTree) == part.ref.premise.isFlow) {
            "K6: the conclusion kind follows its original premise"
        }
        check(part.ref.premise !is PremiseSet || part.added is TaintTree) {
            "A multiple-member input must be TAINT"
        }
    }

    /** Visit first, then queue every effect, including same-unit effects. */
    private fun joinRequests(rows: List<RequestedRow>, inputs: ((Subscription) -> Unit) -> Unit) {
        val port = active()
        check(port.config.run1) { "Mark requests exist only in run 1" }
        if (rows.isEmpty()) return
        val ap = port.ops.manager
        val statistics = port.abstractionStatistics
        val counts = statistics?.let { ReplayCounts() }
        val batchStatistics = port.requestBatchStatistics
        val batch = batchStatistics?.let { RequestBatchStatistics.Counts() }
        val started = if (statistics == null) 0L else System.nanoTime()
        val answers = LinkedHashMap<MethodKey, LinkedHashSet<InitialAp>>()
        val climbs = LinkedHashMap<ClimbTarget, RequestMarkSetBuilder>()
        try {
            inputs { part ->
                ap.cancellation.checkpoint()
                if (counts != null) counts.candidates++
                checkKinds(part) // K6 before eligibility guards
                if (!part.zeroOnly) when (part.added) {
                    is FlowTree -> {
                        val original = normalRun1Slot(part.ref.premise)
                        if (original != null) {
                            if (counts != null) counts.tests++
                            if (batch != null) batch.groupGeometryTests++
                            val offered = RequestMarkSetBuilder()
                            // Compute geometry once per row, then merge the matching
                            // marks before one target-R read and one destination union.
                            for (row in rows) {
                                ap.cancellation.checkpoint()
                                if (batch != null) batch.groupGeometrySteps++
                                if (!slotMatches(row.slot, part.added)) continue
                                if (original != row.slot || original !is Run1Slot.StaticField) {
                                    row.marks.precomputed.forEachMark(ap.cancellation::checkpoint) {
                                        ap.cancellation.checkpoint(); offered.add(it)
                                        if (batch != null) batch.memberScans++
                                    }
                                } else if (!row.marks.precomputed.isEmpty) statistics?.inheritedBaseRowSkipped()
                                row.marks.dynamic.forEachMark {
                                    ap.cancellation.checkpoint(); offered.add(it)
                                    if (batch != null) batch.memberScans++
                                }
                            }
                            if (!offered.isEmpty) {
                                val target = ClimbTarget(part.ref.caller, original)
                                val admitted = port.summaryStorage(target.method).borrowRequest(original)
                                val destination = climbs.getOrPut(target, ::RequestMarkSetBuilder)
                                offered.takeSnapshot().forEachMark { mark ->
                                    ap.cancellation.checkpoint()
                                    if (!admitted.containsId(mark.id)) {
                                        if (counts != null) counts.climbs++
                                        if (batch != null) batch.climbs++
                                        if (!destination.add(mark) && batch != null) batch.duplicateTargetUnions++
                                    }
                                }
                            }
                        }
                    }
                    is TaintTree -> {
                        if (counts != null) counts.tests++
                        if (batch != null) batch.groupGeometryTests++
                        forEachConcreteAnswer(ap, part.added, rows, batch) { initial ->
                            if (counts != null) counts.answers++
                            if (batch != null) batch.answers++
                            answers.getOrPut(part.callee, ::LinkedHashSet).add(initial)
                        }
                    }
                    is Reach -> Unit
                }
            }
            val requestsByMethod = LinkedHashMap<MethodKey, MutableMap<Run1Slot, org.opentaint.dataflow.bidi.store.RequestMarkSet>>()
            for ((target, marks) in climbs) {
                ap.cancellation.checkpoint()
                if (!marks.isEmpty) requestsByMethod.getOrPut(target.method, ::LinkedHashMap)[target.slot] = marks.takeSnapshot()
            }
            for ((method, rowsToSend) in requestsByMethod) {
                ap.cancellation.checkpoint()
                val accepted = port.send(RunEvent.MarkRequestsIn(method, java.util.Collections.unmodifiableMap(rowsToSend)))
                ap.cancellation.checkpoint()
                check(accepted) { "An active run rejected a request delivery" }
                if (batch != null) batch.acceptedBatch(rowsToSend.values.sumOf { it.size })
            }
            for ((method, values) in answers) {
                ap.cancellation.checkpoint()
                val accepted = port.send(RunEvent.InitialAnswersIn(method, values))
                ap.cancellation.checkpoint()
                check(accepted) { "An active run rejected a concrete answer delivery" }
            }
        } finally {
            if (counts != null) statistics?.replayVisit(counts.candidates, counts.tests, counts.answers,
                counts.climbs, System.nanoTime() - started)
            if (batch != null) batchStatistics?.record(batch)
        }
    }

    /** Publisher thread: post to the subscriber runner without reading its directory. */
    fun notifyRequests(callee: MethodKey, rows: RequestRows): Boolean {
        val port = active()
        check(port.config.run1) { "Mark requests exist only in run 1" }
        val accepted = port.post(RunEvent.ChangedRequests(callee, rows))
        port.ops.manager.cancellation.checkpoint()
        check(accepted) { "An active run rejected a request notification" }
        return accepted
    }

    fun onRequests(callee: MethodKey, rows: RequestRows) {
        val port = active()
        check(port.config.run1) { "Mark requests exist only in run 1" }
        val entry = byCallee[callee] ?: return
        val selected = rows.map { (slot, marks) ->
            RequestedRow(slot, RequestRowView(org.opentaint.dataflow.bidi.store.RequestMarkSet.EMPTY, marks))
        }
        joinRequests(selected) { visit -> entry.visitRequestBatch(selected, visit) }
    }

    private fun applyMatch(part: Subscription, pub: Publication, member: Int) {
        val port = active()
        val added = matches(part, pub, member, port.ops, historicalMode(port)) ?: return
        port.ops.manager.cancellation.checkpoint()
        port.applier(part.caller).applySummary(part.copy(added = added), pub, member)
        port.ops.manager.cancellation.checkpoint()
    }

    /** Native records and transient reversed views remain reads of the previous-run store. */
    private fun replayRecords(part: Subscription) {
        val port = active()
        val records = port.config.records
        val direction = port.config.direction
        val mode = historicalMode(port)
        val ops = port.ops
        val cancellation = ops.manager.cancellation
        val applier = port.applier(part.caller)
        val seen = HashSet<Any>()
        for (leaf in ops.leaves(part.added)) {
            cancellation.checkpoint()
            for (record in records.byEntry(part.callee, leaf)) {
                cancellation.checkpoint()
                if (record.direction != direction || !seen.add(record)) continue
                val added = ops.satisfying(part.added, record.premise, mode, record = true) ?: continue
                applier.applyRecord(part.copy(added = added), record)
            }
            for (record in records.byExit(part.callee, leaf)) {
                cancellation.checkpoint()
                if (record.direction == direction) continue
                for (view in record.reversedAt(leaf)) {
                    cancellation.checkpoint()
                    if (!seen.add(view)) continue
                    val added = ops.satisfying(part.added, view.premise, mode, record = true) ?: continue
                    applier.applyRecordView(part.copy(added = added), view)
                }
            }
        }
    }

    /** P2's notification reads no caller-local subscriptions. */
    fun notify(callee: MethodKey, pubs: List<Publication>): Boolean {
        val port = active()
        val accepted = port.post(RunEvent.Delivery(callee, pubs))
        port.ops.manager.cancellation.checkpoint()
        return accepted
    }

    /** P6 queries the subscriptions present when the destination runner handles delivery. */
    fun onDelivery(callee: MethodKey, pubs: List<Publication>) {
        val port = active()
        val entry = byCallee[callee] ?: return
        for (pub in pubs) {
            port.ops.manager.cancellation.checkpoint()
            for (member in 0 until pub.premise.size) {
                port.ops.manager.cancellation.checkpoint()
                for (part in entry.candidates(pub, member, historicalMode(port))) {
                    port.ops.manager.cancellation.checkpoint()
                    applyMatch(part, pub, member)
                }
            }
        }
    }

    /** Joining ends all notifications before the owning runner calls this method. */
    fun freeze() {
        byCallee.values.forEach(CalleeSubscriptions::clear)
        byCallee.clear()
        port = null
    }

}
