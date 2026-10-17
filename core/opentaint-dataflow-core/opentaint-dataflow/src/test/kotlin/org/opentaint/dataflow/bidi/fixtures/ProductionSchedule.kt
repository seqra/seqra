package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.store.*
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Random

enum class SchedulePolicy { FIFO, REPLAY_FIRST, NOTIFY_FIRST, SEEDED }

/** One thread selects real actor work and the real protocol's deferred callbacks. */
class ProductionSchedule(
    private val fixture: FiniteProgramFixtures,
    private val config: RunConfig,
    private val sameUnit: Boolean = false,
    private val policy: SchedulePolicy = SchedulePolicy.FIFO,
    private val seed: Long = 0L,
    private val quantum: Int = 1,
    private val duplicateDeliveries: Boolean = false,
    private val useStaticCatalog: Boolean = true,
) {
    init { require(quantum > 0) }

    private enum class Kind { EVENT, WORK, REPLAY, NOTIFY }
    private class Pending(val kind: Kind, val description: String, val action: () -> Unit)
    private val random = Random(seed)
    private val pool = ArrayList<Pending>()
    private var activeOwner: UnitPort? = null
    private fun turn(kind: Kind, description: String, owner: UnitPort, action: () -> Unit) =
        Pending(kind, description) {
            check(activeOwner == null) { "Deferred work needs its own admitted turn" }
            activeOwner = owner
            try {
                action()
                owner.finishNeedReplay()
            } finally { activeOwner = null }
        }
    private val log = ArrayList<String>()
    val trace: List<String> get() = log.toList()
    private val registered = ArrayList<Subscription>()
    val subscriptions: List<Subscription> get() = registered.toList()
    private val registeredConcrete = ArrayList<ConcreteStaticSubscription>()
    val concreteSubscriptions: List<ConcreteStaticSubscription> get() = registeredConcrete.toList()
    private val ordinaryRelations = LinkedHashSet<Pair<MethodKey, CallerRef>>()
    private data class StaticRelation(val callee: MethodKey, val caller: MethodKey,
        val call: org.opentaint.ir.api.common.cfg.CommonInst,
        val from: org.opentaint.dataflow.bidi.interp.CallPoint,
        val to: org.opentaint.dataflow.bidi.interp.CallPoint,
        val identityCandidate: Boolean? = null)
    private val staticRelations = LinkedHashMap<StaticRelation, FlowTree>()
    private val concreteStaticRelations = LinkedHashMap<StaticRelation, TaintTree>()
    /** Physical protocol relations, excluding the decoded test-only history in [subscriptions]. */
    val physicalOrdinarySubscriptionCount: Int get() = ordinaryRelations.size
    val physicalStaticRelationCount: Int get() = staticRelations.size
    val physicalConcreteStaticRelationCount: Int get() = concreteStaticRelations.size
    val physicalStaticEntryMembershipCount: Int get() = staticRelations.values.sumOf {
        StaticIdentityEntries(shared.ops).originals(it).count()
    }
    private var invoked = false
    private val processed = LinkedHashSet<ObservedEdge>()
    private val inputs = LinkedHashSet<ObservedLink>()
    private val requests = LinkedHashSet<ObservedRequest>()
    private val vulnerabilities = ConcurrentVulnerabilityStore(fixture.manager)
    private val shared = fixture.shared.let { source ->
        SharedObjects(source.ap, source.ops, source.interpreter, source.language, source.contexts,
            source.records, vulnerabilities, source.unitResolver, source.refManager,
            source.cancellation, threads = 1,
            run1StaticProfiles = if (useStaticCatalog) fixture.staticProfiles() else null)
    }
    private val units = LinkedHashMap<Any, UnitPort>()
    private val storages = LinkedHashMap<MethodKey, SummaryStorage>()
    private val needs = if (config.run1) StaticNeedRouter(shared.ap,
        dispatch = { visit -> storage(visit.key.method).notifyStaticNeed(visit) },
        onSourceChanged = { key, marks -> marks.forEachMark { mark ->
            requests += ObservedRequest(key.method, key.slot.initial(shared.ap).toPattern(), RequestKind.Mark(mark))
        } }) else null
    private val deferred = object : ProtocolSteps {
        override fun replay(step: () -> Unit) {
            pool += turn(Kind.REPLAY, "replay", checkNotNull(activeOwner), step)
        }
        override fun notify(step: () -> Unit) {
            pool += turn(Kind.NOTIFY, "notify", checkNotNull(activeOwner), step)
        }
    }
    private val factories = RunMethodHandlers(MethodLocalServicesFactory { actor ->
        val actual = RunMethodLocalServices(actor)
        object : MethodLocalServices by actual {
            override fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
                if (premise.base != STATIC)
                    requests += ObservedRequest(actor.key, premise.toPattern(), RequestKind.Mark(mark))
                actual.addMarkRequest(premise, mark)
            }
            override fun addMarkRequests(premise: InitialAp, marks: RequestMarkSet) {
                if (premise.base != STATIC) marks.forEachMark {
                    requests += ObservedRequest(actor.key, premise.toPattern(), RequestKind.Mark(it)) }
                actual.addMarkRequests(premise, marks)
            }
        }
    }, MethodCallServicesFactory(::RunMethodCallServices))

    private fun owner(key: MethodKey): UnitPort =
        units.getOrPut(if (sameUnit) Unit else key) { UnitPort() }

    private fun description(event: RunEvent): String = when (event) {
        is RunEvent.Start -> "Start(${event.root})"
        is RunEvent.LinkIn -> "Link(callee=${event.callee},caller=${event.ref.caller}," +
            "premise=${observedPremise(event.ref.premise)},callerLayer=${event.ref.callerLayer}," +
            "call=${event.ref.call},linkLayer=${event.linkLayer},added=${shared.ops.leaves(event.added).toList()})"
        is RunEvent.ZeroIn -> "Zero(${event.callee})"
        is RunEvent.StaticEntriesIn -> "StaticEntries(${event.callee},${shared.ops.leaves(event.entries).toList()})"
        is RunEvent.ConcreteStaticEntriesIn -> "ConcreteStaticEntries(${event.callee},${shared.ops.leaves(event.entries).toList()})"
        is RunEvent.StaticNeed -> "StaticNeed(${event.visit.key},${event.visit.marks})"
        is RunEvent.MarkRequestsIn -> "MarkRequests(${event.method},${event.rows})"
        is RunEvent.InitialAnswersIn -> "InitialAnswers(${event.method},${event.answers.map { it.toPattern() }})"
        is RunEvent.ChangedRequests -> "Requests(${event.callee},${event.rows})"
        is RunEvent.Delivery -> "Delivery(${event.callee},${event.publications.flatMap {
            observedEdges(shared.ops, event.callee, it.premise, it.conclusion)
        }})"
        is RunEvent.Work -> "Work(${event.analyzer.key})"
    }

    private inner class UnitPort : RunnerPort<RunMethodAnalyzer> {
        override val staticNeedRouter: StaticNeedRouter? get() = needs
        override val pendingNeedReplay = if (config.run1) PendingNeedReplay(shared.ap) else null
        override val config get() = this@ProductionSchedule.config
        override val shared get() = this@ProductionSchedule.shared
        override val forms = shared.contexts.directed(config.direction)
        override val ops get() = shared.ops
        override val steps get() = deferred
        val actors = LinkedHashMap<MethodKey, RunMethodAnalyzer>()
        private val queued = Collections.newSetFromMap(IdentityHashMap<RunMethodAnalyzer, Boolean>())
        val handler = factories.create(this)

        fun postEvent(event: RunEvent) {
            pool += turn(Kind.EVENT, description(event), this) {
                if (event is RunEvent.MarkRequestsIn) for ((slot, marks) in event.rows) {
                    if (slot is Run1Slot.Base) marks.forEachMark { requests += ObservedRequest(event.method,
                        slot.initial(shared.ap).toPattern(), RequestKind.Mark(it)) }
                }
                handler.handle(event)
            }
        }

        override fun send(event: RunEvent): Boolean {
            owner(event.target()).postEvent(event)
            return true
        }

        override fun post(event: RunEvent.Delivery): Boolean {
            postEvent(event)
            if (duplicateDeliveries) postEvent(RunEvent.Delivery(event.callee, event.publications))
            return true
        }

        override fun post(event: RunEvent.ChangedRequests): Boolean {
            postEvent(event)
            if (duplicateDeliveries) postEvent(RunEvent.ChangedRequests(event.callee, event.rows))
            return true
        }

        override fun post(event: RunEvent.StaticNeed): Boolean {
            postEvent(event)
            if (duplicateDeliveries) postEvent(RunEvent.StaticNeed(event.visit))
            return true
        }

        override fun enqueue(analyzer: RunMethodAnalyzer): Boolean {
            check(activeOwner === this)
            check(actors[analyzer.key] === analyzer)
            if (!queued.add(analyzer)) return false
            postWork(analyzer)
            return true
        }

        private fun postWork(actor: RunMethodAnalyzer) {
            pool += turn(Kind.WORK, "Work(${actor.key})", this) {
                val amount = if (policy == SchedulePolicy.SEEDED) 1 + random.nextInt(quantum) else quantum
                log += "quantum=$amount key=${actor.key}"
                val complete = actor.work(amount)
                finishNeedReplay()
                if (complete) check(queued.remove(actor))
                else postWork(actor)
            }
        }

        fun finishNeedReplay() { needs?.let { pendingNeedReplay?.flush(it) } }

        override fun analyzer(key: MethodKey): RunMethodAnalyzer {
            check(activeOwner === this)
            check(owner(key) === this)
            // Actor construction cannot queue work before this registration completes.
            return actors.getOrPut(key) { handler.createActor(key) }
        }

        override fun summaryStorage(key: MethodKey): SummaryStorage = storage(key)

        override fun subscribe(sub: Subscription) {
            check(activeOwner === this)
            ordinaryRelations += sub.callee to sub.ref
            registered += sub
            log += "subscribe callee=${sub.callee} caller=${sub.caller} " +
                "premise=${observedPremise(sub.ref.premise)},callerLayer=${sub.ref.callerLayer}," +
                "call=${sub.ref.call},linkLayer=${sub.linkLayer},zeroOnly=${sub.zeroOnly}," +
                "added=${shared.ops.leaves(sub.added).toList()}"
            (handler as SubscriptionHandler).subscribe(sub)
        }

        override fun subscribeStatic(sub: StaticSubscription) {
            check(activeOwner === this)
            val codec = StaticIdentityEntries(ops)
            val relation = StaticRelation(sub.callee, sub.caller, sub.call, sub.stageFrom, sub.stageTo)
            staticRelations[relation] = codec.merge(staticRelations[relation], sub.entries).first
            // Ghost expansion is observational only: production still gets one packed relation.
            for (initial in codec.originals(sub.entries)) {
                val facts = codec.single(initial)
                val ref = CallerRef(sub.caller, initial, Layer.DEMAND, sub.call)
                registered += Subscription(sub.callee, ref, Layer.DEMAND, facts)
                ops.leaves(facts).forEach { inputs += observedLink(sub.callee, Link(it, Layer.DEMAND, ref)) }
            }
            log += "subscribeStatic callee=${sub.callee} caller=${sub.caller} call=${sub.call} entries=${ops.leaves(sub.entries).toList()}"
            (handler as SubscriptionHandler).subscribeStatic(sub)
        }

        override fun subscribeConcreteStatic(sub: ConcreteStaticSubscription) {
            check(activeOwner === this)
            registeredConcrete += sub
            val codec = ConcreteStaticEntries(ops)
            val relation = StaticRelation(sub.callee, sub.caller, sub.call, sub.stageFrom, sub.stageTo, sub.identityCandidate)
            concreteStaticRelations[relation] = codec.merge(concreteStaticRelations[relation], sub.entries).first
            for (initial in codec.originals(sub.entries)) {
                val facts = codec.current(initial)
                val ref = CallerRef(sub.caller, initial, Layer.DEMAND, sub.call)
                registered += Subscription(sub.callee, ref, Layer.DEMAND, facts)
                ops.leaves(facts).forEach { inputs += observedLink(sub.callee, Link(it, Layer.DEMAND, ref)) }
            }
            log += "subscribeConcreteStatic callee=${sub.callee} caller=${sub.caller} call=${sub.call} " +
                "from=${sub.stageFrom} to=${sub.stageTo} identity=${sub.identityCandidate} " +
                "entries=${ops.leaves(sub.entries).toList()}"
            (handler as SubscriptionHandler).subscribeConcreteStatic(sub)
        }

        override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts) {
            check(activeOwner === this)
            ops.leaves(added).forEach { inputs += observedLink(callee, Link(it, linkLayer, ref)) }
            val destination = owner(callee)
            val event = RunEvent.LinkIn(callee, ref, linkLayer, added)
            if (destination === this) {
                log += "direct ${description(event)}"
                handler.handle(event)
            } else destination.postEvent(event)
        }

        override val onStaticProcess: (MethodKey, org.opentaint.ir.api.common.cfg.CommonInst, FlowTree) -> Unit = { method, node, entries ->
            val codec = StaticIdentityEntries(ops)
            for (initial in codec.originals(entries)) {
                val rows = observedEdges(ops, method, initial, codec.single(initial), node)
                processed += rows
                log += "static process $rows"
            }
        }

        override val onConcreteStaticProcess: (MethodKey, org.opentaint.ir.api.common.cfg.CommonInst, TaintTree) -> Unit = { method, node, entries ->
            val codec = ConcreteStaticEntries(ops)
            for (initial in codec.originals(entries)) {
                val rows = observedEdges(ops, method, initial, codec.current(initial), node)
                processed += rows
                log += "concrete static process $rows"
            }
        }

        override val onProcess: (MethodKey, EdgeDelta) -> Unit = { method, edge ->
            val rows = observedEdges(ops, method, edge.premise, edge.facts, edge.node)
            processed += rows
            log += "process $rows"
        }
    }

    private fun storage(key: MethodKey): SummaryStorage = storages.getOrPut(key) {
        SummaryStorage(key, shared.ap, shared.ops, deferred, EmptyFrozenRequestBase)
    }

    private fun next(): Int = when (policy) {
        SchedulePolicy.FIFO -> 0
        SchedulePolicy.REPLAY_FIRST -> pool.indexOfFirst { it.kind == Kind.REPLAY }.takeIf { it >= 0 } ?: 0
        SchedulePolicy.NOTIFY_FIRST -> pool.indexOfFirst { it.kind == Kind.NOTIFY }.takeIf { it >= 0 } ?: 0
        SchedulePolicy.SEEDED -> random.nextInt(pool.size)
    }

    fun run(): RunObservation {
        check(!invoked) { "A schedule is single-use" }
        invoked = true
        var closed = false
        try {
            for (root in config.roots) owner(root).postEvent(RunEvent.Start(root))
            var iterations = 0
            while (pool.isNotEmpty()) {
                check(++iterations <= 200_000) { "Finite schedule did not close: seed=$seed trace=$trace" }
                val item = pool.removeAt(next())
                log += item.description
                item.action()
            }
            closed = true
            return snapshot()
        } catch (failure: Throwable) {
            throw AssertionError("Production schedule failed: seed=$seed policy=$policy sameUnit=$sameUnit " +
                "quantum=$quantum duplicate=$duplicateDeliveries program=${fixture.describe()} trace=$trace", failure)
        } finally {
            // Successful closure has no replay, notify, event or local work left.
            for (unit in units.values) {
                if (closed) for (actor in unit.actors.values) actor.freeze()
                (unit.handler as SubscriptionHandler).freeze()
                unit.pendingNeedReplay?.clear()
                unit.actors.clear()
            }
            pool.clear()
            storages.clear()
            needs?.clear()
            shared.close()
        }
    }

    private fun snapshot(): RunObservation {
        val initials = LinkedHashSet<ObservedInitial>()
        val raw = LinkedHashSet<ObservedEdge>()
        val selected = LinkedHashSet<ObservedEdge>()
        val links = LinkedHashSet(inputs)
        val hits = LinkedHashSet<ObservedSourceHit>()
        for (unit in units.values) for ((method, actor) in unit.actors) {
            actor.initials.all().mapTo(initials) { ObservedInitial(method, it.toPattern()) }
            actor.staticEntryFacts()?.let { entries ->
                StaticIdentityEntries(shared.ops).originals(entries).mapTo(initials) {
                    ObservedInitial(method, it.toPattern())
                }
            }
            actor.concreteStaticEntryFacts()?.let { entries ->
                ConcreteStaticEntries(shared.ops).originals(entries).mapTo(initials) {
                    ObservedInitial(method, it.toPattern())
                }
            }
            for ((premise, facts) in actor.summaries.all()) raw += observedEdges(shared.ops, method, premise, facts)
            for ((premise, facts) in actor.summaries.demandEdges())
                selected += observedEdges(shared.ops, method, premise, facts)
            actor.sourceHits?.entries()?.mapTo(hits) { (key, statement, forward) ->
                ObservedSourceHit(key, statement, forward)
            }
        }
        val publications = LinkedHashSet<ObservedEdge>()
        for ((method, storage) in storages) {
            for ((premise, group) in publicationGroups(storage)) for (facts in group.all())
                publications += observedEdges(shared.ops, method, premise, facts)
        }
        val witnesses = vulnerabilities.witnessesOf(config.index).map { (key, witness) ->
            observedWitness(shared.ops, key, witness)
        }.toSet()
        return RunObservation(processed = processed.toSet(), initials = initials, rawSummaries = raw,
            publications = publications, selectedDemand = selected, links = links,
            requests = requests.toSet(), sourceHits = hits, witnesses = witnesses)
    }

    /** Read actual stored publications after all production callbacks have finished. */
    @Suppress("UNCHECKED_CAST")
    private fun publicationGroups(storage: SummaryStorage): Map<PremiseKey, ConclusionGroup> {
        val indexField = SummaryStorage::class.java.getDeclaredField("published").apply { isAccessible = true }
        val index = indexField.get(storage) as PublicationIndex
        val valuesField = PublicationIndex::class.java.getDeclaredField("merged").apply { isAccessible = true }
        return valuesField.get(index) as Map<PremiseKey, ConclusionGroup>
    }
}
