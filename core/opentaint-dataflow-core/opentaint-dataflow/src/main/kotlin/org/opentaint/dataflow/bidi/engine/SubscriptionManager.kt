package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
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
        for ((pub, member) in storage.candidates(part, port.config.mode)) {
            port.ops.manager.cancellation.checkpoint()
            applyMatch(part, pub, member)
        }
        if (!part.zeroOnly && port.config.restricted) replayRecords(part)
        if (port.config.run1 && !part.zeroOnly) {
            // The question read follows registration and uses the original admitted header/value.
            // Summary replay above retains its existing fresh-delta contract.
            val current = admitted.current
            if (current.added !is Reach) for ((key, marks) in storage.questions()) {
                port.ops.manager.cancellation.checkpoint()
                replayQuestion(QuestionDelta(key, marks)) { visit -> visit(current) }
            }
        }
    }

    private data class ClimbTarget(val method: MethodKey, val initial: InitialAp)
    private class ReplayCounts {
        var candidates = 0L
        var tests = 0L
        var answers = 0L
        var climbs = 0L
    }

    /** A complete owner-local visit has no mutating callbacks. Only detached results leave it. */
    private fun replayQuestion(delta: QuestionDelta, inputs: ((Subscription) -> Unit) -> Unit) {
        val port = active()
        check(port.config.run1) { "Mark questions exist only in run 1" }
        if (delta.marks.isEmpty) return
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
                if (!part.zeroOnly && part.added !is Reach) {
                    if (counts != null) counts.tests++
                    if (batch != null) batch.groupGeometryTests++
                    val cursor = RequestGroupCursor(ap, delta.key.initial, delta.marks,
                        part.added, part.ref, delta.key.pattern)
                    try {
                        while (!cursor.isComplete) {
                            ap.cancellation.checkpoint()
                            cursor.step(QUESTION_TRAVERSAL_CHUNK, onAnswer = { initial ->
                                if (counts != null) counts.answers++
                                if (batch != null) batch.answers++
                                answers.getOrPut(part.callee, ::LinkedHashSet).add(initial)
                            }, onClimb = { mark ->
                                if (counts != null) counts.climbs++
                                if (batch != null) batch.climbs++
                                val initial = part.ref.premise as InitialAp
                                if (port.covered(part.ref.caller, initial, mark)) statistics?.covered()
                                else if (!climbs.getOrPut(ClimbTarget(part.ref.caller, initial),
                                        ::RequestMarkSetBuilder).add(mark) && batch != null) batch.duplicateTargetUnions++
                            })
                        }
                    } finally {
                        if (batch != null) {
                            batch.memberScans += cursor.memberScans
                            batch.groupGeometrySteps += cursor.geometryTests
                        }
                    }
                }
            }
            // All directory iteration has ended. send always queues, even for the local unit.
            for ((method, values) in answers) {
                ap.cancellation.checkpoint()
                val accepted = port.send(RunEvent.InitialAnswersIn(method, values))
                ap.cancellation.checkpoint()
                check(accepted) { "An active run rejected a concrete answer delivery" }
            }
            for ((target, marks) in climbs) {
                ap.cancellation.checkpoint()
                val selected = marks.takeSnapshot()
                val accepted = port.send(RunEvent.MarkRequestsIn(target.method, target.initial, selected))
                ap.cancellation.checkpoint()
                check(accepted) { "An active run rejected a climb delivery" }
                if (batch != null) batch.acceptedBatch(selected.size)
            }
        } finally {
            if (counts != null) statistics?.replayVisit(counts.candidates, counts.tests, counts.answers,
                counts.climbs, System.nanoTime() - started)
            if (batch != null) batchStatistics?.record(batch)
        }
    }

    /** Called by the publisher thread; it may not inspect byCallee. */
    fun notifyQuestions(callee: MethodKey, delta: QuestionDelta): Boolean {
        val port = active()
        check(port.config.run1) { "Mark questions exist only in run 1" }
        val accepted = port.post(RunEvent.QuestionDelivery(callee, delta))
        port.ops.manager.cancellation.checkpoint()
        check(accepted) { "An active run rejected a question delivery" }
        return accepted
    }

    /** The destination runner selects its current N2 groups when handling the notification. */
    fun onQuestions(callee: MethodKey, delta: QuestionDelta) {
        val port = active()
        check(port.config.run1) { "Mark questions exist only in run 1" }
        val entry = byCallee[callee]
        replayQuestion(delta) { visit -> entry?.visitQuestion(delta.key, visit) }
    }

    private fun applyMatch(part: Subscription, pub: Publication, member: Int) {
        val port = active()
        val added = matches(part, pub, member, port.ops, port.config.mode) ?: return
        port.ops.manager.cancellation.checkpoint()
        port.applier(part.caller).applySummary(part.copy(added = added), pub, member)
        port.ops.manager.cancellation.checkpoint()
    }

    /** Native records and transient reversed views remain reads of the previous-run store. */
    private fun replayRecords(part: Subscription) {
        val port = active()
        val records = port.config.records
        val direction = port.config.direction
        val mode = port.config.mode
        val ops = port.ops
        val cancellation = ops.manager.cancellation
        val applier = port.applier(part.caller)
        val seen = HashSet<Any>()
        fun applyRecord(record: Record) {
            cancellation.checkpoint()
            val added = ops.satisfying(part.added, record.premise, mode, record = true) ?: return
            cancellation.checkpoint()
            applier.applyRecord(part.copy(added = added), record)
            cancellation.checkpoint()
        }
        for (leaf in ops.leaves(part.added)) {
            cancellation.checkpoint()
            for (record in records.byEntry(part.callee, leaf)) {
                cancellation.checkpoint()
                if (record.direction == direction && seen.add(record)) applyRecord(record)
            }
            for (record in records.byExit(part.callee, leaf)) {
                cancellation.checkpoint()
                if (record.direction == direction) continue
                for (view in record.reversedAt(leaf)) {
                    cancellation.checkpoint()
                    if (!seen.add(view)) continue
                    val added = ops.satisfying(part.added, view.premise, mode, record = true) ?: continue
                    cancellation.checkpoint()
                    applier.applyRecordView(part.copy(added = added), view)
                    cancellation.checkpoint()
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
                for (part in entry.candidates(pub, member, port.config.mode)) {
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

    private companion object {
        const val QUESTION_TRAVERSAL_CHUNK = 256
    }
}
