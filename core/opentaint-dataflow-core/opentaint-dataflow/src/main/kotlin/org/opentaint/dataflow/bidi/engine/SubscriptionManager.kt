package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.Record

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
        val part = entry.add(sub) ?: return
        val statistics = port.statistics
        if (statistics == null) {
            port.steps.replay { replay(storage, part) }
            return
        }
        statistics.replayQueued()
        var entered = false
        try {
            port.steps.replay {
                entered = true
                try { replay(storage, part) }
                finally { statistics.replayFinished() }
            }
        } catch (error: Throwable) {
            // Inline failures release in the callback; admission failures release here.
            if (!entered) statistics.replayFinished()
            throw error
        }
    }

    private fun replay(storage: SummaryStorage, part: Subscription) {
        val port = active()
        for ((pub, member) in storage.candidates(part, port.config.mode)) {
            port.ops.manager.cancellation.checkpoint()
            applyMatch(part, pub, member)
        }
        if (!part.zeroOnly && port.config.restricted) replayRecords(part)
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
}
