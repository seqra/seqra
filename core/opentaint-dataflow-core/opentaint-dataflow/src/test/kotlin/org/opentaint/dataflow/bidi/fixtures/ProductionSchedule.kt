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
    private val log = ArrayList<String>()
    val trace: List<String> get() = log.toList()
    private val registered = ArrayList<Subscription>()
    val subscriptions: List<Subscription> get() = registered.toList()
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
    private val deferred = object : ProtocolSteps {
        override fun replay(step: () -> Unit) { pool += Pending(Kind.REPLAY, "replay", step) }
        override fun notify(step: () -> Unit) { pool += Pending(Kind.NOTIFY, "notify", step) }
    }
    private val factories = RunMethodHandlers(MethodLocalServicesFactory { actor ->
        val actual = RunMethodLocalServices(actor)
        object : MethodLocalServices by actual {
            override fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
                requests += ObservedRequest(actor.key, premise.toPattern(), RequestKind.Mark(mark))
                actual.addMarkRequest(premise, mark)
            }
            override fun addMarkRequests(premise: InitialAp, marks: RequestMarkSet) {
                marks.forEachMark { requests += ObservedRequest(actor.key, premise.toPattern(), RequestKind.Mark(it)) }
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
        is RunEvent.MarkRequestsIn -> "MarkRequests(${event.method},${event.rows})"
        is RunEvent.InitialAnswersIn -> "InitialAnswers(${event.method},${event.answers.map { it.toPattern() }})"
        is RunEvent.ChangedRequests -> "Requests(${event.callee},${event.rows})"
        is RunEvent.Delivery -> "Delivery(${event.callee},${event.publications.flatMap {
            observedEdges(shared.ops, event.callee, it.premise, it.conclusion)
        }})"
        is RunEvent.Work -> "Work(${event.analyzer.key})"
    }

    private inner class UnitPort : RunnerPort<RunMethodAnalyzer> {
        override val config get() = this@ProductionSchedule.config
        override val shared get() = this@ProductionSchedule.shared
        override val forms = shared.contexts.directed(config.direction)
        override val ops get() = shared.ops
        override val steps get() = deferred
        val actors = LinkedHashMap<MethodKey, RunMethodAnalyzer>()
        private val queued = Collections.newSetFromMap(IdentityHashMap<RunMethodAnalyzer, Boolean>())
        val handler = factories.create(this)

        fun postEvent(event: RunEvent) {
            pool += Pending(Kind.EVENT, description(event)) {
                if (event is RunEvent.MarkRequestsIn) for ((slot, marks) in event.rows) {
                    marks.forEachMark { requests += ObservedRequest(event.method,
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

        override fun enqueue(analyzer: RunMethodAnalyzer): Boolean {
            check(actors[analyzer.key] === analyzer)
            if (!queued.add(analyzer)) return false
            postWork(analyzer)
            return true
        }

        private fun postWork(actor: RunMethodAnalyzer) {
            pool += Pending(Kind.WORK, "Work(${actor.key})") {
                val amount = if (policy == SchedulePolicy.SEEDED) 1 + random.nextInt(quantum) else quantum
                log += "quantum=$amount key=${actor.key}"
                if (actor.work(amount)) check(queued.remove(actor))
                else postWork(actor)
            }
        }

        override fun analyzer(key: MethodKey): RunMethodAnalyzer {
            check(owner(key) === this)
            // Actor construction cannot queue work before this registration completes.
            return actors.getOrPut(key) { handler.createActor(key) }
        }

        override fun summaryStorage(key: MethodKey): SummaryStorage =
            storages.getOrPut(key) { SummaryStorage(key, shared.ap, ops, deferred,
                if (config.run1) shared.run1StaticProfiles?.get(key.method)?.precomputedRequests ?: EmptyFrozenRequestBase else EmptyFrozenRequestBase) }

        override fun subscribe(sub: Subscription) {
            registered += sub
            log += "subscribe callee=${sub.callee} caller=${sub.caller} " +
                "premise=${observedPremise(sub.ref.premise)},callerLayer=${sub.ref.callerLayer}," +
                "call=${sub.ref.call},linkLayer=${sub.linkLayer},zeroOnly=${sub.zeroOnly}," +
                "added=${shared.ops.leaves(sub.added).toList()}"
            (handler as SubscriptionHandler).subscribe(sub)
        }

        override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts) {
            ops.leaves(added).forEach { inputs += observedLink(callee, Link(it, linkLayer, ref)) }
            val destination = owner(callee)
            val event = RunEvent.LinkIn(callee, ref, linkLayer, added)
            if (destination === this) {
                log += "direct ${description(event)}"
                handler.handle(event)
            } else destination.postEvent(event)
        }

        override val onProcess: (MethodKey, EdgeDelta) -> Unit = { method, edge ->
            val rows = observedEdges(ops, method, edge.premise, edge.facts, edge.node)
            processed += rows
            log += "process $rows"
        }
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
                unit.actors.clear()
            }
            pool.clear()
            storages.clear()
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
