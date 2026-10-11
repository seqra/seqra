package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.graph.CompactGraph
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import java.util.BitSet
import java.util.Collections
import java.util.IdentityHashMap

/** Finite, non-call method services. Unsupported protocol operations fail explicitly. */
class MethodAnalysisFixtures(
    successors: List<List<Int>> = listOf(listOf(1), listOf(2), emptyList()),
    val normalExit: Int = 2,
    val exceptionalExit: Int? = null,
) : AutoCloseable {
    val cancellation = Cancellation()
    val refManager = RefManager()
    val store = StoreFixtures(cancellation, successors.lastIndex, refManager)
    val manager = store.manager
    val ops = store.ops
    val key = store.method
    val callPlans = mutableMapOf<Int, CallPlan>()
    val callExpressions = mutableMapOf<Int, CommonCallExpr>()
    val callExpression = object : CommonCallExpr {
        override val typeName = "Box"
        override val args: List<CommonValue> = emptyList()
    }
    val language = object : LanguageManager by store.lm {
        override fun getCallExpr(inst: CommonInst): CommonCallExpr? = callExpressions[inst.location.index]
    }
    val records = PersistentRecordStore(manager)
    val vulnerabilities = ConcurrentVulnerabilityStore(manager)
    private val adjacency = successors.map { it.toList() }
    val statements = mutableMapOf<Int, StatementSummary>()
    var entryRules: RuleStatement = RuleStatement.EMPTY
    val exitRules = mutableMapOf<Int, ExitRules>()
    private val reversedStatements = IdentityHashMap<StatementSummary, StatementSummary>()
    private val reversedEntries = IdentityHashMap<RuleStatement, RuleStatement>()
    private val reversedExits = IdentityHashMap<ExitRules, ExitRules>()
    private val reversedCalls = IdentityHashMap<CallPlan, CallPlan>()
    val unresolvedCalls = ArrayList<Triple<CommonCallExpr, AccessPathBase, CallPlan>>()

    init {
        require(normalExit in adjacency.indices)
        require(exceptionalExit == null || exceptionalExit in adjacency.indices)
        require(adjacency.all { next -> next.all { it in adjacency.indices } })
    }

    fun inst(index: Int): CommonInst = store.inst(index)
    fun statement(index: Int, summary: StatementSummary) { statements[index] = summary }
    fun entryRules(value: RuleStatement) { entryRules = value }
    fun exitRules(index: Int, value: ExitRules) { exitRules[index] = value }
    fun call(index: Int, plan: CallPlan, expression: CommonCallExpr = callExpression) {
        callPlans[index] = plan
        callExpressions[index] = expression
    }

    private val forms = object : MethodForms {
        override fun statement(direction: Direction, s: CommonInst): StatementSummary {
            val value = statements[s.location.index] ?: StatementSummary.EMPTY
            return if (direction == Direction.FORWARD) value
            else reversedStatements.getOrPut(value) { value.reversed() }
        }
        override fun entryRules(direction: Direction): RuleStatement =
            if (direction == Direction.FORWARD) entryRules
            else reversedEntries.getOrPut(entryRules) { entryRules.reversed() }
        override fun exitRules(direction: Direction, exit: CommonInst): ExitRules {
            val value = exitRules[exit.location.index] ?: ExitRules.EMPTY
            return if (direction == Direction.FORWARD) value
            else reversedExits.getOrPut(value) { ExitRules(value.reversed(), false, emptySet()) }
        }
        override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan {
            val plan = checkNotNull(callPlans[s.location.index]) { "The finite call has no plan" }
            return if (direction == Direction.FORWARD) plan else reversedCalls.getOrPut(plan) { plan.reversed() }
        }
    }

    private fun graph(direction: Direction): MethodInstGraph {
        val next = if (direction == Direction.FORWARD) adjacency else
            List(adjacency.size) { target -> adjacency.indices.filter { target in adjacency[it] } }
        return MethodInstGraph(Array(adjacency.size, ::inst),
            CompactGraph.build(adjacency.size) { index -> BitSet().apply { next[index].forEach(::set) } },
            BitSet().apply {
                if (direction == Direction.FORWARD) {
                    set(normalExit)
                    exceptionalExit?.let(::set)
                } else set(0)
            })
    }

    val interpreter: Interpreter = object : Interpreter, UnresolvedCallObserver {
        override fun reached(call: CommonCallExpr, position: AccessPathBase, plan: CallPlan) {
            unresolvedCalls += Triple(call, position, plan)
        }
        override fun entryNode(method: MethodKey): CommonInst = inst(0)
        override fun exitNodes(method: MethodKey): List<ExitNode> = buildList {
            add(ExitNode(inst(normalExit), false))
            exceptionalExit?.let { add(ExitNode(inst(it), true)) }
        }
        override fun entryRules(method: MethodKey): RuleStatement = forms.entryRules(Direction.FORWARD)
        override fun exitRules(method: MethodKey, exit: CommonInst): ExitRules = forms.exitRules(Direction.FORWARD, exit)
        override fun statementSummary(method: MethodKey, statement: CommonInst): StatementSummary =
            forms.statement(Direction.FORWARD, statement)
        override fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr): CallPlan =
            forms.call(Direction.FORWARD, statement, call)
        override fun isSummaryBase(base: AccessPathBase): Boolean =
            base !is AccessPathBase.LocalVar && base !is AccessPathBase.CleanerTemporary
    }
    val contexts = MethodContextCache(interpreter, object : MethodContextSource {
        override fun forms(key: MethodKey): MethodForms = forms
        override fun graph(key: MethodKey, direction: Direction): MethodInstGraph = graph(direction)
    })
    val shared = SharedObjects(manager, ops, interpreter, language, contexts, records, vulnerabilities,
        UnitResolver { UnknownUnit }, refManager, cancellation, threads = 1)

    val processed = ArrayList<EdgeDelta>()
    val cuts = ArrayList<Pair<Cut, Facts>>()
    val requests = ArrayList<Pair<InitialAp, RequestKind>>()
    val addedLinks = ArrayList<Triple<CallerRef, Layer, Facts>>()
    val published = ArrayList<List<Publication>>()
    val publications: List<List<Publication>> get() = published
    val enqueued = ArrayList<RunMethodAnalyzer>()
    val events = ArrayList<RunEvent>()
    val subscriptions = ArrayList<Subscription>()
    val sentLinks = ArrayList<RunEvent.LinkIn>()
    var onSubscribe: ((Subscription) -> Unit)? = null
    var onLink: ((RunEvent.LinkIn) -> Unit)? = null
    var onPublish: ((MethodKey, List<Publication>) -> Unit)? = null
    var onPublishInput: ((MethodKey, List<Publication>) -> Unit)? = null
    var onProcess: ((MethodKey, EdgeDelta) -> Unit)? = null
    var onCut: ((Cut, Facts) -> Unit)? = null
    var sinkCheck: ((PremiseKey, CommonInst, Facts, List<SinkRule>) -> Triggers)? = null
    val triggerSeedCalls = ArrayList<Pair<CommonInst, SinkRule>>()
    val sinkSeedCalls = ArrayList<CommonInst>()
    val sinkEvaluations = ArrayList<Pair<SinkRule, Layer>>()

    fun config(index: Int = 1, L: Int = 1,
               demand: DemandStore? = if (index == 1) null else DemandStore.Builder(manager).build(),
               seeds: SeedIndex = SeedIndex.EMPTY): RunConfig =
        RunConfig(index, L, demand, records, seeds, listOf(key))

    fun core(index: Int = 1, L: Int = 1,
             demand: DemandStore? = if (index == 1) null else DemandStore.Builder(manager).build(),
             seeds: SeedIndex = SeedIndex.EMPTY,
             localFactory: MethodLocalServicesFactory? = null,
             callFactory: MethodCallServicesFactory? = null,
             key: MethodKey = this.key): RunMethodAnalyzer =
        core(config(index, L, demand, seeds), localFactory, callFactory, key)

    fun core(config: RunConfig, localFactory: MethodLocalServicesFactory? = null,
             callFactory: MethodCallServicesFactory? = null,
             key: MethodKey = this.key): RunMethodAnalyzer {
        lateinit var actor: RunMethodAnalyzer
        val port = object : RunnerPort<RunMethodAnalyzer> {
            override val config = config
            override val shared = this@MethodAnalysisFixtures.shared
            override val forms = contexts.directed(config.direction)
            override val ops = this@MethodAnalysisFixtures.ops
            override val steps = ProtocolSteps.Inline
            override fun send(event: RunEvent): Boolean { events += event; return true }
            override fun post(event: RunEvent.Delivery): Boolean { events += event; return true }
            override fun summaryStorage(key: MethodKey): SummaryStorage =
                error("Summary storage requires an explicit protocol fixture")
            override fun subscribe(sub: Subscription) {
                val receive = checkNotNull(onSubscribe) { "Subscriptions require an explicit protocol fixture" }
                subscriptions += sub
                receive(sub)
            }
            override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts) {
                val receive = checkNotNull(onLink) { "Links require an explicit protocol fixture" }
                val event = RunEvent.LinkIn(callee, ref, linkLayer, added)
                sentLinks += event
                receive(event)
            }
            override fun enqueue(analyzer: RunMethodAnalyzer): Boolean { enqueued += analyzer; return true }
            override fun analyzer(key: MethodKey): RunMethodAnalyzer {
                check(key == actor.key) { "The finite port owns one method actor" }
                return actor
            }
            override val onProcess: (MethodKey, EdgeDelta) -> Unit = { key, edge ->
                processed += edge
                this@MethodAnalysisFixtures.onProcess?.invoke(key, edge)
            }
            override val onCut: (Cut, Facts) -> Unit = { point, facts ->
                cuts += point to facts
                this@MethodAnalysisFixtures.onCut?.invoke(point, facts)
            }
        }
        val publisher = PublicationSink { method, batch ->
            onPublishInput?.invoke(method, batch)
            val owned: List<Publication> = Collections.unmodifiableList(ArrayList(batch))
            published += owned
            onPublish?.invoke(method, owned)
        }
        actor = RunMethodAnalyzer(key, port, publisher,
            localFactory ?: MethodLocalServicesFactory(::LocalServices),
            callFactory ?: MethodCallServicesFactory { NoCalls })
        return actor
    }

    private inner class LocalServices(private val core: RunMethodAnalyzer) : MethodLocalServices {
        private val key get() = core.key
        override val conjunctions: ConjunctionStore? get() =
            error("Standing conjunction storage requires an explicit test service")
        private val triggered = HashSet<Pair<CommonInst, SinkRule>>()
        override fun addRequest(premise: InitialAp, kind: RequestKind) { requests += premise to kind }
        override fun onAddedLink(ref: CallerRef, linkLayer: Layer, delta: Facts) {
            addedLinks += Triple(ref, linkLayer, delta)
        }
        override fun conjunction(edge: ConjunctiveEdge, premise: PremiseKey, facts: Facts, node: CommonInst,
                                 sink: (PremiseKey, Facts) -> Unit): Unit =
            error("Standing conjunctions require an explicit test service")
        override fun checkSinks(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>): Triggers {
            sinkCheck?.let { check ->
                return check(premise, node, facts, sinks).also { sinkEvaluations += it.fired }
            }
            if (!core.forward) return Triggers.NONE
            val fired = ArrayList<Pair<SinkRule, Layer>>()
            val parts = ArrayList<Facts>()
            for (sink in sinks) {
                cancellation.checkpoint()
                check(!sink.conjunctive) { "Conjunctive sinks require an explicit test service" }
                for (literal in sink.patterns) {
                    cancellation.checkpoint()
                    when (val result = ops.checkMark(facts, literal, core.mode)) {
                        MarkCheck.None -> Unit
                        is MarkCheck.Request -> core.collect(premise, facts) { _, _ -> }.markRequest(result.mark)
                        is MarkCheck.Holds -> {
                            parts += result.facts
                            fired += sink to result.facts.layer
                            vulnerabilities.add(VulnerabilityKey(sink.rule, key.method, node),
                                SinkWitness(sink.alternative, key,
                                    listOf(SinkEdge(premise, result.facts.layer, result.facts)), config.index,
                                    sink.endFacts.map { it.edge.to }))
                        }
                    }
                }
            }
            return Triggers(fired.distinct(), parts).also { sinkEvaluations += it.fired }
        }
        private val config get() = core.config
        override fun fireSinkSeeds(node: CommonInst, emit: (Facts) -> Unit) {
            sinkSeedCalls += node
            for (seed in config.seeds.at(key, node)) {
                cancellation.checkpoint()
                if (seed is Seed.Sink) seedFact(seed.requirement, emit)
            }
        }
        override fun fireTriggerSeeds(node: CommonInst, sink: SinkRule, emit: (Facts) -> Unit) {
            if (!triggered.add(node to sink)) return
            triggerSeedCalls += node to sink
            for (literal in sink.seedPatterns()) {
                cancellation.checkpoint()
                seedFact(literal, emit)
            }
        }
        private fun seedFact(pattern: Pattern, emit: (Facts) -> Unit) =
            core.cut(Cut.SEED, manager.zero, ops.targetTree(pattern.fact, Layer.NORMAL)) { _, facts -> emit(facts) }
        override fun freeze() { triggered.clear() }
    }

    private object NoCalls : MethodCallServices {
        override fun process(item: EdgeDelta): Unit = error("Calls require an explicit test service")
        override fun applySummary(part: Subscription, pub: Publication, member: Int): Unit =
            error("Summary application requires an explicit test service")
        override fun applyRecord(part: Subscription, record: Record): Unit =
            error("Record application requires an explicit test service")
        override fun applyRecordView(part: Subscription, view: RecordLeafView): Unit =
            error("Record views require an explicit test service")
        override fun freeze() = Unit
    }

    /** Each completed item remains in the actor until its real work method processes it. */
    fun drain(core: RunMethodAnalyzer, quantum: Int = 1000): Int {
        var attempts = 0
        while (true) {
            cancellation.checkpoint()
            check(++attempts <= 10000) { "The finite method fixture did not reach closure" }
            if (core.work(quantum)) return attempts
        }
    }
    fun field(name: String): Int = store.field(name)
    fun mark(name: String): TaintMark = store.mark(name)
    fun pattern(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
                tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
                exclusion: ExclusionSet = ExclusionSet.Empty): Pattern = store.pattern(base, path, tail, mark, exclusion)
    fun fact(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
             tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
             exclusion: ExclusionSet = ExclusionSet.Empty, layer: Layer = Layer.NORMAL): Facts =
        store.fact(base, path, tail, mark, exclusion, layer)
    override fun close(): Unit = shared.close()
}
