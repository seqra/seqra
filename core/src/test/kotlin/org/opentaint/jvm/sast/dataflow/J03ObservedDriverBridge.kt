package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.JIRBidiAnalysis
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.jvm.graph.JApplicationGraph
import java.lang.reflect.InvocationTargetException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/** A literal keeps its exact premise and both its layer and decoded conclusion group. */
data class J03ObservedLiteral(
    val premise: Set<JvmObservedPath>,
    val layer: Layer,
    val facts: Set<JvmObservedConclusion>,
)

/** Captured after actual Support.confirm; no witness flag or mutable Facts is retained. */
data class J03ObservedWitness(
    val rule: RuleId,
    val statement: CommonInst,
    val method: MethodKey,
    val alternative: Int,
    val run: Int,
    val literals: List<J03ObservedLiteral>,
    val endFacts: List<JvmObservedPath>,
    val confirmed: Boolean,
)

/**
 * Test-only bridge. Public JIRBidiAnalysis construction stays unchanged.
 *
 * Internal production test seam: its internal eight-argument constructor adds
 * driverFactory: (IterationPolicy, SharedObjects, Duration) -> IterationDriver.
 * The internal IterationDriver constructor is already present. Reflection only
 * reaches those constructors, the actual barrier memory guard, and read-only
 * snapshots of indexes whose current public APIs do not enumerate all values.
 *
 * Terminal observation contract: CompleteJvmRun.outgoingDemand and
 * outgoingSeeds are nullable. Next.End discards HandOff, so its payload is not
 * observable through the agreed seam. Null is distinct from an observed empty
 * next-input store. Actual Frontier counts remain available on terminal runs.
 */
class J03ObservedDriverBridge {
    private val snapshots = ArrayList<CompleteJvmRun>()
    private val witnessSnapshots = ArrayList<J03ObservedWitness>()
    private val symbols = ConcurrentHashMap<String, Accessor>()
    private var driver: IterationDriver? = null
    private var pending: JoinedRun? = null

    val runs: List<CompleteJvmRun> get() = ownList(snapshots)
    val witnesses: List<J03ObservedWitness> get() = ownList(witnessSnapshots)
    val frontiers: List<Frontier> get() = ownList(checkNotNull(driver).frontiers)

    fun createEntry(
        cp: JIRClasspath,
        graph: JApplicationGraph,
        unitResolver: JIRUnitResolver,
        rules: TaintRulesProvider,
        params: JIRAnalysisManager.Params,
        policy: IterationPolicy,
        refManager: RefManager,
    ): JIRBidiAnalysis {
        val ctor = JIRBidiAnalysis::class.java.declaredConstructors.single { it.parameterCount == 8 }
        ctor.isAccessible = true
        val factory: (IterationPolicy, SharedObjects, Duration) -> IterationDriver = ::createDriver
        return unwrap { ctor.newInstance(cp, graph, unitResolver, rules, params, policy, refManager, factory) }
            as JIRBidiAnalysis
    }

    fun result(output: List<VulnerabilityWithTrace>, report: Report): BidiAcceptanceResult =
        BidiAcceptanceResult(ownList(output), report, runs, frontiers, witnesses)

    private fun createDriver(policy: IterationPolicy, shared: SharedObjects, budget: Duration): IterationDriver {
        check(driver == null) { "An observation bridge belongs to one real analysis" }
        val invoke: (RunConfig, SharedObjects, Duration) -> RunResult<RunMethodAnalyzer> = ::runEngine
        val guard: (SharedObjects, () -> Next) -> Next = ::barrier
        // Duration is an inline class; the exact JVM constructor takes its raw long.
        val rawDuration = Duration::class.java.getDeclaredMethod("unbox-impl").invoke(budget)
        val ctor = IterationDriver::class.java.declaredConstructors.single { it.parameterCount == 5 }
        ctor.isAccessible = true
        return (unwrap { ctor.newInstance(policy, shared, rawDuration, invoke, guard) } as IterationDriver)
            .also { driver = it }
    }

    private fun runEngine(config: RunConfig, shared: SharedObjects, timeout: Duration): RunResult<RunMethodAnalyzer> {
        check(pending == null) { "The previous barrier must release its run before the next engine" }
        val live = LiveRun(config, shared)
        val local = MethodLocalServicesFactory { core ->
            val actual = RunMethodLocalServices(core)
            object : MethodLocalServices by actual {
                override fun addRequest(premise: InitialAp, kind: RequestKind) {
                    // Captures both local requests and climbed requests delivered to this actor.
                    actual.addRequest(premise, kind)
                    live.requests += request(shared.ap, core.key, premise, kind)
                }

                override fun freeze() {
                    // RunManager has joined every runner. The actor releases these stores only
                    // after this hook returns; handler.freeze would be too late.
                    for (initial in core.initials.all())
                        live.initials += JvmObservedInitial(core.key, path(shared.ap, initial.toPattern()))
                    for (rawLink in core.links.links()) live.links += link(shared.ap, core.key, rawLink)
                    actual.freeze()
                }
            }
        }
        val actualHandlers = RunMethodHandlers(local, MethodCallServicesFactory(::RunMethodCallServices))
        val factory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
            val observed = object : RunnerPort<RunMethodAnalyzer> by port {
                override val onProcess: ((MethodKey, EdgeDelta) -> Unit) = { key, delta ->
                    live.processed.addAll(edges(shared.ops, key, delta.premise, delta.facts, delta.node))
                    port.onProcess?.invoke(key, delta)
                }
            }
            // Return the real handler unchanged so its SubscriptionHandler identity is preserved.
            actualHandlers.create(observed)
        }
        val result = RunManager(config, shared, factory).run(timeout)
        val joined = JoinedRun(live, result)
        // An incomplete result has no complete barrier or retained stores; return its actual
        // status unchanged. In particular, do not decode cancellation-sensitive indexes here.
        if (result.status == RunStatus.COMPLETE) pending = joined
        return result
    }

    private fun barrier(shared: SharedObjects, body: () -> Next): Next {
        val joined = checkNotNull(pending) { "A barrier requires the actual joined complete run" }
        try {
            val guardClass = Class.forName("org.opentaint.dataflow.bidi.driver.IterationDriverKt")
            val actualGuard = guardClass.declaredMethods.single { it.name == "withBarrierGuard" }
            actualGuard.isAccessible = true
            // Invoke actual Support/report/R1/HandOff/policy inside the existing memory guard.
            val next = unwrap { actualGuard.invoke(null, shared, body) } as Next
            if (shared.cancellation.isActive()) {
                for ((key, values) in joined.result.witnessesByKey()) for (rawWitness in values)
                    witnessSnapshots += witness(shared.ops, key, rawWitness)
                snapshots += snapshot(joined, (next as? Next.Run)?.config, recordSnapshot(shared))
            }
            return next
        } finally {
            // Retain only owned scalar observations, never an actor, port, result or run context.
            pending = null
        }
    }

    private class LiveRun(val config: RunConfig, val shared: SharedObjects) {
        val processed = ConcurrentHashMap.newKeySet<JvmObservedEdge>()
        val initials = ConcurrentHashMap.newKeySet<JvmObservedInitial>()
        val links = ConcurrentHashMap.newKeySet<JvmObservedLink>()
        val requests = ConcurrentHashMap.newKeySet<JvmObservedRequest>()
    }
    private class JoinedRun(val live: LiveRun, val result: RunResult<RunMethodAnalyzer>)

    private fun snapshot(joined: JoinedRun, outgoing: RunConfig?, records: Set<JvmObservedRecord>): CompleteJvmRun {
        val live = joined.live
        val config = live.config
        val ops = live.shared.ops
        val raw = LinkedHashSet<JvmObservedEdge>()
        val selected = LinkedHashSet<JvmObservedEdge>()
        val hits = LinkedHashSet<JvmObservedSourceHit>()
        val counters = LinkedHashMap<MethodKey, JvmObservedMethodCounters>()
        for (actor in joined.result.analyzers) {
            counters[actor.key] = JvmObservedMethodCounters(actor.steps, actor.edgeDeltas, actor.nonZeroInitial,
                actor.demandLayerEdges, actor.recordCrossings, actor.triggerSeeds)
            for ((premise, facts) in actor.summaries.all()) raw += edges(ops, actor.key, premise, facts)
            for ((premise, facts) in actor.summaries.demandEdges()) selected += edges(ops, actor.key, premise, facts)
            actor.sourceHits?.entries()?.forEach { (key, statement, forward) ->
                hits += JvmObservedSourceHit(key, statement,
                    path(ops.manager, Pattern(forward.from, forward.fromExclusion)),
                    path(ops.manager, Pattern(forward.to, forward.toExclusion)),
                    exclusion(ops.manager, forward.exclusion))
            }
        }
        return CompleteJvmRun(config.index, config.fieldLimit, config.direction, joined.result.status,
            ownSet(live.processed), ownSet(live.initials), ownSet(raw), ownSet(selected), ownSet(live.links),
            ownSet(live.requests), ownSet(hits), records, outgoing?.let { demandSnapshot(ops.manager, it.demand) },
            seedSnapshot(config.seeds), outgoing?.let { seedSnapshot(it.seeds) }, ownMap(counters),
            counters.values.sumOf { it.steps }, counters.values.sumOf { it.edgeDeltas },
            joined.result.demandLayerEdges, joined.result.recordCrossings, counters.values.sumOf { it.triggerSeeds },
            ownSet(counters.filterValues { it.nonZeroInitial }.keys))
    }

    private fun edges(ops: ApOps, key: MethodKey, premise: PremiseKey, facts: Facts,
                      node: CommonInst? = null): List<JvmObservedEdge> =
        ops.leaves(facts).map { leaf ->
            JvmObservedEdge(key, premise(ops.manager, premise),
                JvmObservedConclusion(path(ops.manager, leaf), facts.layer), node)
        }.toList()

    private fun premise(ap: ApManager, premise: PremiseKey): Set<JvmObservedPath> =
        ownSet(premise.members.map { path(ap, it.toPattern()) })

    private fun link(ap: ApManager, callee: MethodKey, link: Link): JvmObservedLink =
        JvmObservedLink(callee, link.caller.caller, link.caller.call, premise(ap, link.caller.premise),
            link.caller.callerLayer, link.linkLayer, path(ap, link.addedFact))

    private fun request(ap: ApManager, key: MethodKey, premise: InitialAp, kind: RequestKind): JvmObservedRequest =
        when (kind) {
            is RequestKind.Mark -> JvmObservedRequest(key, path(ap, premise.toPattern()), mark = ap.marks.name(kind.mark))
            is RequestKind.Position -> JvmObservedRequest(key, path(ap, premise.toPattern()),
                position = ownList(kind.path.toIntArray().map { accessor(ap, it) }))
        }

    private fun witness(ops: ApOps, key: VulnerabilityKey, witness: SinkWitness): J03ObservedWitness =
        J03ObservedWitness(key.rule, key.statement, witness.methodKey, witness.alternative, witness.run,
            ownList(witness.edges.map { edge -> J03ObservedLiteral(premise(ops.manager, edge.premise), edge.layer,
                ownSet(ops.leaves(edge.facts).map {
                    JvmObservedConclusion(path(ops.manager, it), edge.facts.layer)
                }.toList())) }), ownList(witness.endFacts.map {
                path(ops.manager, Pattern(it, ExclusionSet.Empty))
            }), witness.confirmed)

    private fun path(ap: ApManager, p: Pattern): JvmObservedPath {
        val mark = p.fact.mark
        // The readable fixture label '*' for the canonical zero is unambiguous because
        // Zero has one legal concrete EXACT shape; all nonzero marks use their actual names.
        val label = when {
            p.fact.base == AccessPathBase.Zero -> "*"
            mark is MarkSlot.Concrete -> ap.marks.name(mark.mark)
            else -> "*"
        }
        val excluded = if (mark is MarkSlot.Star)
            ownSet(mark.excluded.ids.map { ap.marks.name(TaintMark(it)) }) else emptySet()
        return JvmObservedPath(p.fact.base, ownList(p.fact.path.map { accessor(ap, it) }), p.fact.tail,
            label, exclusion(ap, p.exclusion), excluded)
    }

    private fun exclusion(ap: ApManager, e: ExclusionSet): Set<String> = ownSet(
        (e as? ExclusionSet.Concrete)?.ids?.map { accessor(ap, it) }.orEmpty())

    private fun accessor(ap: ApManager, index: Int): String {
        val value = ap.accessors.accessor(index)
        val label = when (value) {
            is FieldAccessor -> value.fieldName
            is ClassStaticAccessor -> "<static>(${value.typeName})"
            ElementAccessor -> "[e]"
            else -> error("Unexpected path accessor: $value")
        }
        val old = symbols.putIfAbsent(label, value)
        check(old == null || old == value) {
            "Fixture symbol '$label' conflates full accessors $old and $value; qualify that test's labels"
        }
        return label
    }

    /** Snapshot complete index payloads, never query selector applicability for an observation. */
    @Suppress("UNCHECKED_CAST")
    private fun recordSnapshot(shared: SharedObjects): Set<JvmObservedRecord> {
        val byMethod = field(shared.records, "entry") as Map<MethodKey, PathTrie<Record>>
        val out = LinkedHashSet<JvmObservedRecord>()
        for ((key, trie) in byMethod) for (record in trie.all()) for (leaf in shared.ops.leaves(record.conclusion))
            out += JvmObservedRecord(key, record.direction, path(shared.ap, record.premise.toPattern()),
                JvmObservedConclusion(path(shared.ap, leaf), record.conclusion.layer))
        return ownSet(out)
    }

    @Suppress("UNCHECKED_CAST")
    private fun demandSnapshot(ap: ApManager, demand: DemandStore?): Set<JvmObservedDemand> {
        if (demand == null) return emptySet()
        val byMethod = field(demand, "byMethod") as Map<MethodKey, PathTrie<DemandPattern>>
        val out = LinkedHashSet<JvmObservedDemand>()
        for ((key, trie) in byMethod) for (d in trie.all())
            out += JvmObservedDemand(key, path(ap, d.entry), d.exit?.let { path(ap, it) })
        return ownSet(out)
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedSnapshot(seeds: SeedIndex): Set<Seed> {
        val byPlace = field(seeds, "byPlace") as Map<Pair<MethodKey, CommonInst>, List<Seed>>
        return ownSet(byPlace.values.flatten().map { seed -> when (seed) {
            is Seed.Sink -> Seed.Sink(seed.rule, seed.method, seed.statement, ownPattern(seed.requirement))
            // This immutable value names a complete condition without retaining its compiled DAG.
            // Keep one physical seed and its original site/context: expanding its leaves would
            // make outgoingSeeds.size disagree with the actual Frontier.seeds counter. The bridge
            // observes MethodKeys/CommonInsts directly, so the occurrence needs no rebasing.
            is Seed.PredicateSink -> Seed.PredicateSink(seed.occurrence, seed.method, seed.statement)
            is Seed.Source -> Seed.Source(seed.method, seed.statement, seed.edge.copy(
                from = seed.edge.from.copy(path = ownList(seed.edge.from.path)),
                to = seed.edge.to.copy(path = ownList(seed.edge.to.path))))
        } })
    }

    private fun ownPattern(p: Pattern) = p.copy(fact = p.fact.copy(path = ownList(p.fact.path)))
    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let {
        it.isAccessible = true
        checkNotNull(it.get(owner))
    }
    private fun <T> ownList(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
    private fun <T> ownSet(values: Collection<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))
    private fun <K, V> ownMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))
    private inline fun <T> unwrap(body: () -> T): T = try { body() }
    catch (e: InvocationTargetException) { throw (e.cause ?: e) }
}
