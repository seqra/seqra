package org.opentaint.dataflow.bidi.engine

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
    @Volatile private var port: SubscriptionPort? = port
    private val byCallee = LinkedHashMap<MethodKey, LinkedHashMap<CallSiteKey, CalleeSubscriptions>>()
    private val pendingSitesByCaller = LinkedHashMap<MethodKey, LinkedHashSet<CallSiteKey>>()
    private val staticByCallee = LinkedHashMap<MethodKey, LinkedHashMap<CallSiteKey, StaticSubscription>>()
    private val concreteStatic = ConcreteStaticSubscriptions(port, this)

    fun subscribeConcreteStatic(sub: ConcreteStaticSubscription) = concreteStatic.subscribe(sub)

    fun subscribeStatic(sub: StaticSubscription) {
        val port = active()
        check(port.config.run1)
        val entries = StaticIdentityEntries(port.ops)
        entries.validate(sub.entries)
        val storage = port.summaryStorage(sub.callee)
        val relations = staticByCallee.getOrPut(sub.callee) {
            LinkedHashMap<CallSiteKey, StaticSubscription>()
        }
        val key = sub.callSiteKey
        val entry = directory(key)
        val previous = relations[key]
        val (stored, delta) = entries.merge(previous?.entries, sub.entries)
        if (delta == null) return
        port.abstractionStatistics?.staticRelation(staticEntryMemberships(delta), key !in relations)
        // Certification for one fresh component cannot certify older unrelated
        // components in this same packed relation. Uncertainty keeps the fallback.
        val certified = sub.coveredStaticRelay && (previous?.coveredStaticRelay ?: true)
        relations[key] = sub.copy(entries = stored, coveredStaticRelay = certified)
        val fresh = sub.copy(entries = delta, coveredStaticRelay = certified)
        // Register the exact relation before either side's current-state read.
        port.steps.replay {
            val probe = entries.originals(delta).first()
            val ref = org.opentaint.dataflow.bidi.store.CallerRef(sub.caller, probe, Layer.DEMAND, sub.call)
            for ((pub, _) in storage.candidates(Subscription(sub.callee, ref, Layer.DEMAND, delta), port.config.mode))
                applyStatic(fresh, pub)
        }
        check(port.send(RunEvent.StaticEntriesIn(sub.callee, delta))) { "A run rejected static entry delivery" }
        if (!coveredRelay(key, certified)) {
            for (initial in entries.originals(delta))
                ensureRequestStream(key, entry, Run1Slot.fromInitial(initial) as Run1Slot.StaticField)
        }
        // Old A joins only the newly admitted descriptor geometry.
        val answered = entry.answeredRows(delta)
        joinStaticRequests(fresh, answered)
    }

    private fun applyStatic(part: StaticSubscription, pub: Publication) {
        val port = active()
        val initial = pub.premise as? InitialAp ?: return
        if (!initial.isFlow || initial.base != STATIC) return
        if (isSpecialStaticRoot(initial)) {
            val expected = port.ops.startFact(initial).withLayer(Layer.DEMAND)
            check(pub.conclusion == expected) { "The only run-1 root FLOW is the universal default" }
            port.abstractionStatistics?.staticDefault()
            port.applier(part.caller).applyStaticDefault(part, part.entries)
            return
        }
        val entries = StaticIdentityEntries(port.ops)
        if (!entries.contains(part.entries, initial)) return
        val ref = org.opentaint.dataflow.bidi.store.CallerRef(part.caller, initial, Layer.DEMAND, part.call)
        val input = Subscription(part.callee, ref, Layer.DEMAND, entries.single(initial),
            stageFrom = part.stageFrom, stageTo = part.stageTo, coveredStaticRelay = part.coveredStaticRelay)
        val matched = matches(input, pub, 0, port.ops, port.config.mode) ?: return
        port.applier(part.caller).applyStaticSummary(part, input.copy(added = matched), pub)
    }

    private fun active(): SubscriptionPort = checkNotNull(port) { "The subscriptions were released" }.also {
        it.ops.manager.cancellation.checkpoint()
    }

    private fun directory(key: CallSiteKey): CalleeSubscriptions {
        val port = active()
        val sites = byCallee.getOrPut(key.callee) {
            LinkedHashMap<CallSiteKey, CalleeSubscriptions>().also {
                port.summaryStorage(key.callee).addSubscriber(this)
            }
        }
        return sites.getOrPut(key) { CalleeSubscriptions(port.ops, port.statistics) }
    }

    private fun coveredRelay(key: CallSiteKey, certified: Boolean): Boolean = certified &&
        active().staticRequests?.containsPrescanEdge(key.caller.method, key.callee.method) == true

    private fun coveredSameFieldRelay(part: Subscription, slot: Run1Slot.StaticField): Boolean =
        part.added is FlowTree && normalRun1Slot(part.ref.premise) == slot &&
            coveredRelay(part.callSiteKey, part.coveredStaticRelay)

    private fun ensureRequestStream(key: CallSiteKey, entry: CalleeSubscriptions, slot: Run1Slot) {
        val port = active()
        val service = port.staticRequests
        if (slot is Run1Slot.StaticField && service != null) {
            if (!entry.addViewRequestSlot(slot)) return
            service.subscribeView(StaticDemandViewKey(key.callee, slot), key) { marks ->
                val accepted = port.post(RunEvent.CallsiteRequests(key, mapOf(slot to marks)))
                port.ops.manager.cancellation.checkpoint()
                check(accepted) { "An active run rejected a static-view request delivery" }
            }
        } else {
            if (!entry.addExactRequestSlot(slot)) return
            val storage = port.summaryStorage(key.callee)
            storage.addRequestSubscriber(this, slot)
            // Exact stream registration precedes the current-state read.
            offerRequests(key, entry, mapOf(slot to storage.borrowRequest(slot)
                .toRequestMarkSet(port.ops.manager.cancellation::checkpoint)))
        }
    }

    private fun ensureRequestStreams(part: Subscription, entry: CalleeSubscriptions,
                                     prepared: Run1PreparedInitials) {
        val port = active()
        if (part.added is Reach) return
        if (part.added.base != STATIC) {
            ensureRequestStream(part.callSiteKey, entry, Run1Slot.Base(part.added.base))
            return
        }
        if (part.added !is FlowTree) return // actual static TAINT is eager, not request dependent
        val geometry = prepared.staticEntries ?: return
        for (initial in StaticIdentityEntries(port.ops).originals(geometry)) {
            val slot = Run1Slot.fromInitial(initial) as Run1Slot.StaticField
            if (!coveredSameFieldRelay(part, slot)) ensureRequestStream(part.callSiteKey, entry, slot)
        }
    }

    fun subscribe(sub: Subscription) {
        val port = active()
        val storage = port.summaryStorage(sub.callee)
        val entry = directory(sub.callSiteKey)
        val admitted = entry.admit(sub) ?: return
        // Capture A NOW: deferred replay must not accidentally see requests that
        // were still pending when this input arrived and are joined by the other frontier.
        val answered = if (port.config.run1 && !sub.zeroOnly) entry.answeredRows(admitted.fresh.added) else emptyList()
        if (port.config.run1) {
            val fresh = admitted.fresh
            val prepared = prepareRun1Initials(port.ops, port.staticProfile(fresh.callee.method),
                fresh.ref, fresh.added, port.requestBatchStatistics, port.abstractionStatistics, fresh.callee)
            ensureRequestStreams(fresh, entry, prepared)
            check(port.send(RunEvent.PreparedInitialsIn(fresh.callee, prepared))) {
                "An active run rejected canonical initial delivery"
            }
        }
        val statistics = port.statistics
        if (statistics == null) {
            port.steps.replay { replay(storage, admitted, answered) }
            return
        }
        statistics.replayQueued()
        var entered = false
        try {
            port.steps.replay {
                entered = true
                try { replay(storage, admitted, answered) }
                finally { statistics.replayFinished() }
            }
        } catch (error: Throwable) {
            if (!entered) statistics.replayFinished()
            throw error
        }
    }

    private fun replay(storage: SummaryStorage, admitted: CalleeSubscriptions.Admission,
                       answered: List<RequestedRow>) {
        val port = active()
        val part = admitted.fresh
        for ((pub, member) in storage.candidates(part, port.config.mode)) {
            port.ops.manager.cancellation.checkpoint()
            applyMatch(part, pub, member)
        }
        if (!part.zeroOnly && port.config.restricted) replayRecords(part)
        if (port.config.run1 && !part.zeroOnly) {
            checkKinds(part)
            if (part.added is FlowTree) port.abstractionStatistics?.freshFlowInputVisit(admitted.growth)
            joinRequests(answered) { visit -> visit(part) }
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

    private fun joinRequests(rows: List<RequestedRow>, inputs: ((Subscription) -> Unit) -> Unit) {
        if (rows.isEmpty()) return
        joinRequestGroups { visit -> inputs { part -> visit(part, rows) } }
    }

    /** One method-empty buffer across its sites; each visit keeps that site's
     * exact request rows. No cross-owner envelope or delivered-pair history. */
    private fun joinRequestGroups(inputs: ((Subscription, List<RequestedRow>) -> Unit) -> Unit) {
        val port = active()
        check(port.config.run1) { "Mark requests exist only in run 1" }
        val ap = port.ops.manager
        val statistics = port.abstractionStatistics
        val counts = statistics?.let { ReplayCounts() }
        val batchStatistics = port.requestBatchStatistics
        val batch = batchStatistics?.let { RequestBatchStatistics.Counts() }
        val started = if (statistics == null) 0L else System.nanoTime()
        val answers = LinkedHashMap<MethodKey, LinkedHashSet<InitialAp>>()
        val climbs = LinkedHashMap<ClimbTarget, RequestMarkSetBuilder>()
        try {
            inputs { part, rows ->
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
                                if (row.slot is Run1Slot.StaticField && coveredSameFieldRelay(part, row.slot)) continue
                                row.marks.forEachMark {
                                    ap.cancellation.checkpoint(); offered.add(it)
                                    if (batch != null) batch.memberScans++
                                }
                            }
                            if (!offered.isEmpty) {
                                val target = ClimbTarget(part.ref.caller, original)
                                // Base and static originals share the destination
                                // owner's authoritative outgoing request table.
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

    private fun offerRequests(key: CallSiteKey, entry: CalleeSubscriptions, rows: RequestRows) {
        val port = active()
        var changed = false
        for ((slot, marks) in rows) {
            port.ops.manager.cancellation.checkpoint()
            changed = entry.offerRequest(slot, marks) || changed
        }
        if (changed) {
            val first = key.caller !in pendingSitesByCaller
            pendingSitesByCaller.getOrPut(key.caller, ::LinkedHashSet).add(key)
            if (first) check(port.send(RunEvent.RequestDrain(key.caller))) {
                "An active run rejected a request drain"
            }
        }
    }

    fun onRequests(callee: MethodKey, rows: RequestRows) {
        val port = active()
        check(port.config.run1)
        for ((key, entry) in byCallee[callee].orEmpty()) {
            val selected = rows.filterKeys(entry::acceptsExactRequest)
            if (selected.isNotEmpty()) offerRequests(key, entry, selected)
        }
    }

    fun onCallsiteRequests(key: CallSiteKey, rows: RequestRows) {
        val port = active()
        check(port.config.run1)
        val entry = byCallee[key.callee]?.get(key) ?: return
        val selected = rows.filterKeys(entry::acceptsViewRequest)
        if (selected.isNotEmpty()) offerRequests(key, entry, selected)
    }

    /** Called at method-empty, or by the one counted wake when this owner is idle. */
    fun drainRequests(caller: MethodKey) {
        val port = active()
        val pendingSites = pendingSitesByCaller.remove(caller) ?: return
        val work = ArrayList<Pair<CallSiteKey, Pair<CalleeSubscriptions, List<RequestedRow>>>>()
        for (key in pendingSites) {
            val entry = checkNotNull(byCallee[key.callee]?.get(key)) { "A pending site lost its input owner" }
            val delta = entry.drainRequests() // install every site's A before any queued effect
            if (delta.isEmpty()) continue
            val rows = delta.map { (slot, marks) -> RequestedRow(slot,
                RequestRowView(org.opentaint.dataflow.bidi.store.RequestMarkSet.EMPTY, marks)) }
            work += key to (entry to rows)
        }
        joinRequestGroups { visit ->
            for ((_, group) in work) {
                val (entry, rows) = group
                entry.visitRequestBatch(rows) { part -> visit(part, rows) }
            }
        }
        for ((key, group) in work)
            staticByCallee[key.callee]?.get(key)?.let { joinStaticRequests(it, group.second) }
        port.ops.manager.cancellation.checkpoint()
    }

    private fun joinStaticRequests(part: StaticSubscription, rows: List<RequestedRow>) {
        val port = active()
        if (rows.isEmpty() || coveredRelay(part.callSiteKey, part.coveredStaticRelay)) return
        val codec = StaticIdentityEntries(port.ops)
        val offered = LinkedHashMap<Run1Slot, org.opentaint.dataflow.bidi.store.RequestMarkSet>()
        for (row in rows) {
            val slot = row.slot as? Run1Slot.StaticField ?: continue
            if (!codec.contains(part.entries, slot.initial(port.ops.manager))) continue
            val fresh = row.marks.minus(port.summaryStorage(part.caller).borrowRequest(slot),
                port.ops.manager.cancellation::checkpoint)
            if (!fresh.isEmpty) offered[slot] = fresh
        }
        if (offered.isNotEmpty()) check(port.send(RunEvent.MarkRequestsIn(part.caller, freezeRequestRows(offered)))) {
            "An active run rejected a packed static need"
        }
    }

    /** Publisher-side notification contains no read of this caller-owned graph. */
    fun notifyStaticNeed(visit: StaticNeedVisit): Boolean {
        val port = active()
        visit.wave.queuedWork()
        var accepted = false
        try {
            accepted = port.post(RunEvent.StaticNeed(visit))
            if (accepted) port.staticNeedRouter?.statistics?.subscriberDelivery()
            port.ops.manager.cancellation.checkpoint()
            check(accepted) { "An active run rejected a static need visit" }
            return accepted
        } finally {
            // A successful post transfers telemetry ownership even if the next
            // checkpoint cancels; only its receiver or joined clear releases it.
            if (!accepted) visit.wave.finishedWork()
        }
    }

    /** Only the subscribed runner visits its actual current FLOW inputs. All
     * effects, including same-unit routes, are secured after the finite visit. */
    fun onStaticNeed(visit: StaticNeedVisit) {
        try {
            onStaticNeedOwned(visit)
        } finally {
            visit.wave.finishedWork()
        }
    }

    private fun onStaticNeedOwned(visit: StaticNeedVisit) {
        val port = active()
        check(port.config.run1)
        val router = checkNotNull(port.staticNeedRouter)
        val slot = visit.key.slot
        val endpoints = LinkedHashSet<ClimbTarget>()
        val row = RequestedRow(slot, RequestRowView(
            org.opentaint.dataflow.bidi.store.RequestMarkSet.EMPTY, visit.marks))
        var ordinaryScans = 0L
        var packedScans = 0L
        try {
            byCallee[visit.key.method]?.values?.forEach { entry -> entry.visitRequestBatch(listOf(row)) { part ->
                port.ops.manager.cancellation.checkpoint()
                ordinaryScans++
                checkKinds(part)
                if (!part.zeroOnly && part.added is FlowTree && slotMatches(slot, part.added)) {
                    normalRun1Slot(part.ref.premise)?.let { original ->
                        endpoints += ClimbTarget(part.ref.caller, original)
                    }
                }
            }
            }
            // Exact diagonal originals keep all full call/stage keys.
            for (part in staticByCallee[visit.key.method]?.values?.toList().orEmpty()) {
                port.ops.manager.cancellation.checkpoint()
                packedScans++
                if (part.entries.root.child(slot.cls)?.child(slot.field)?.leaf == FlowLeaf.LEAF)
                    endpoints += ClimbTarget(part.caller, slot)
            }
            for (target in endpoints) {
                port.ops.manager.cancellation.checkpoint()
                when (val original = target.slot) {
                    is Run1Slot.StaticField -> router.forward(visit, target.method, original)
                    is Run1Slot.Base -> {
                        router.statistics?.baseOffer(visit.marks.size)
                        val fresh = visit.marks.minus(port.summaryStorage(target.method).borrowRequest(original),
                            port.ops.manager.cancellation::checkpoint)
                        if (!fresh.isEmpty) {
                            check(port.send(RunEvent.MarkRequestsIn(target.method, mapOf(original to fresh)))) {
                                "A run rejected a BaseSlot need"
                            }
                            router.statistics?.baseDelivery(fresh.size)
                            router.statistics?.requestEnvelope(1, 0)
                        }
                    }
                }
            }
        } finally {
            router.statistics?.bindingScans(ordinaryScans, packedScans)
        }
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
        val entries = byCallee[callee]?.values?.toList().orEmpty()
        val packed = staticByCallee[callee]?.values?.toList().orEmpty()
        for (pub in pubs) {
            port.ops.manager.cancellation.checkpoint()
            // Capture ALL sites' values before the first callback can grow any
            // directory. This is one publication's transient candidate snapshot.
            val candidates = ArrayList<Pair<Subscription, Int>>()
            for (entry in entries) for (member in 0 until pub.premise.size) {
                for (part in entry.candidates(pub, member, port.config.mode)) {
                    port.ops.manager.cancellation.checkpoint()
                    candidates += part to member
                }
            }
            concreteStatic.onDelivery(callee, listOf(pub))
            for (part in packed) applyStatic(part, pub)
            for ((part, member) in candidates) {
                port.ops.manager.cancellation.checkpoint()
                applyMatch(part, pub, member)
            }
        }
    }

    internal fun requestState(key: CallSiteKey): CalleeSubscriptions.RequestState? =
        byCallee[key.callee]?.get(key)?.requestState()

    data class RequestSnapshot(val callsites: Int, val pendingRows: Long, val pendingMembers: Long,
                               val answeredRows: Long, val answeredMembers: Long)

    /** Owner/join-time gauges; no per-mark atomics or retained delivered-pair history. */
    fun requestSnapshot(): RequestSnapshot {
        var sites = 0
        var pendingRows = 0L
        var pendingMembers = 0L
        var answeredRows = 0L
        var answeredMembers = 0L
        for (entries in byCallee.values) for (entry in entries.values) {
            val state = entry.requestState()
            sites++
            pendingRows = Math.addExact(pendingRows, state.pendingRows.toLong())
            pendingMembers = Math.addExact(pendingMembers, state.pendingMembers)
            answeredRows = Math.addExact(answeredRows, state.answeredRows.toLong())
            answeredMembers = Math.addExact(answeredMembers, state.answeredMembers)
        }
        return RequestSnapshot(sites, pendingRows, pendingMembers, answeredRows, answeredMembers)
    }

    /** Joining ends all notifications before the owning runner calls this method. */
    fun freeze() {
        concreteStatic.clear()
        byCallee.values.forEach { sites -> sites.values.forEach(CalleeSubscriptions::clear) }
        byCallee.clear()
        pendingSitesByCaller.clear()
        staticByCallee.clear()
        port = null
    }

}
