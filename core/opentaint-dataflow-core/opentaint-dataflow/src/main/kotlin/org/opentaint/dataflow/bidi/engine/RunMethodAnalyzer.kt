package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.Collections

/** One runner owns this method's work and stores for one fresh run. */
class RunMethodAnalyzer(
    override val key: MethodKey,
    port: RunnerPort<RunMethodAnalyzer>,
    publisher: PublicationSink,
    localFactory: MethodLocalServicesFactory,
    callFactory: MethodCallServicesFactory,
) : RunActor, SummaryApplier {
    private var port: RunnerPort<RunMethodAnalyzer>? = port
    private var runContext: RunMethodContext? = RunMethodContext(key, port.forms, port.config, port.shared)
    internal val runnerPort: RunnerPort<RunMethodAnalyzer> get() = checkNotNull(port) { "The method is frozen" }
    internal val context: RunMethodContext get() = checkNotNull(runContext) { "The method is frozen" }
    internal val config: RunConfig get() = context.config
    internal val forms: DirectedForms get() = context.forms
    internal val ap: ApManager = port.shared.ap
    internal val ops: ApOps = port.shared.ops
    internal val mode: ApMode = port.config.mode
    internal val forward: Boolean = port.config.direction == Direction.FORWARD
    private val limit = port.config.fieldLimit

    private var edgeStore: MethodEdgeStore? = MethodEdgeStore(ap, key, port.shared.language, limit)
    private var initialStore: InitialFactStore? = InitialFactStore()
    val edges: MethodEdgeStore get() = checkNotNull(edgeStore) { "The method edges were released" }
    val initials: InitialFactStore get() = checkNotNull(initialStore) { "The initial facts were released" }
    val summaries = RunSummaryStore(ap)
    val sourceHits: SourceHitStore? = if (forward) null else SourceHitStore()
    internal var abstraction: Run1Abstraction? = if (config.run1) Run1Abstraction(
        ops, context.shared.run1StaticProfiles?.get(key.method),
        answer = ::addInitial,
        publish = { delta -> runnerPort.summaryStorage(key).publishQuestions(delta) },
        statistics = context.shared.requestBatchStatistics,
        policyStatistics = context.shared.abstractionStatistics,
    ) else null
        private set
    internal fun releaseAbstraction() { abstraction?.release(); abstraction = null }
    internal fun unchangedStaticInitial(premise: PremiseKey): InitialAp? =
        if (config.run1 && context.shared.run1StaticProfiles != null) premise as? InitialAp else null

    private var publicationSink: PublicationSink? = publisher
    private var local: MethodLocalServices? = localFactory.create(this)
    private var calls: MethodCallServices? = callFactory.create(this)
    internal val localServices: MethodLocalServices get() = checkNotNull(local) { "The local services were released" }
    private val callServices: MethodCallServices get() = checkNotNull(calls) { "The call services were released" }
    internal val applier = FormApplier(EngineAlgebra(this))
    private val worklist = DeltaWorklist()
    private var pending = ArrayList<Publication>()

    override var steps: Long = 0; private set
    override val hasZeroWork: Boolean get() = worklist.hasZeroWork
    var edgeDeltas: Long = 0; private set
    override var demandLayerEdges: Long = 0; private set
    var nonZeroInitial: Boolean = false; private set
    override var recordCrossings: Long = 0; private set
    var triggerSeeds: Long = 0; private set

    fun addRootZero(): Unit = addInitial(ap.zero)

    fun addZeroEntry() {
        check(!forward) { "Only a backward callee has a direct zero entry" }
        addInitial(ap.zero)
    }

    fun addLink(ref: CallerRef, linkLayer: Layer, added: Facts) {
        ap.cancellation.checkpoint()
        check(added.base !is AccessPathBase.CleanerTemporary) { "A field-action temporary cannot enter an added link" }
        check(linkLayer == added.layer) { "The link layer must be the added fact's layer" }
        context.shared.abstractionStatistics?.arrival()
        // This is input presence for NO_DEMAND_EDGE, not a distinct retained-group count.
        if (added.layer == Layer.DEMAND && !sawDemandInput) { sawDemandInput = true; demandLayerEdges++ }
        if (config.run1) checkNotNull(abstraction).emit(ref, added) else emit(added)
        if (abstraction?.hasPending == true) requestWork()
    }

    private var sawDemandInput = false

    fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
        check(config.run1) { "Requests exist only in run 1" }
        localServices.addMarkRequest(premise, mark)
    }

    fun addMarkRequests(premise: InitialAp, marks: RequestMarkSet) {
        check(config.run1) { "Requests exist only in run 1" }
        localServices.addMarkRequests(premise, marks)
    }

    private fun emit(delta: Facts) {
        ap.cancellation.checkpoint()
        if (delta is Reach) {
            addInitial(ap.zero)
            return
        }
        val demand = checkNotNull(config.demand)
        val found = LinkedHashSet<DemandPattern>()
        for (leaf in ops.leaves(delta)) {
            ap.cancellation.checkpoint()
            found += demand.near(key, leaf.fact.base, ap.path(leaf.fact.path))
        }
        for (pattern in found) {
            ap.cancellation.checkpoint()
            context.shared.abstractionStatistics?.intersection()
            for (initial in ops.emit(pattern, delta)) addInitial(initial)
        }
    }

    internal fun addInitial(initial: InitialAp) {
        ap.cancellation.checkpoint()
        check(!config.run1 || initial.tail != Tail.ANY_TAINT) { "Run 1 has no must premise" }
        check(forward || initial.tail != Tail.ANY_TAINT) { "A backward run has no must premise" }
        val fresh = initials.add(initial)
        context.shared.abstractionStatistics?.initial(fresh)
        if (!fresh) return
        if (!initial.isZero) nonZeroInitial = true
        val start = ops.startFact(initial)
        for (node in forms.startNodes(key, zero = initial.isZero)) {
            ap.cancellation.checkpoint()
            startAt(initial, node, start)
        }
    }

    internal fun collect(premise: PremiseKey, input: Facts, sink: ResultSink): ApOut = object : ApOut {
        override fun result(f: Facts): Unit = sink(premise, f)
        override fun markRequest(mark: TaintMark): Unit = raiseMark(premise, input, mark)
    }

    internal fun raiseMark(premise: PremiseKey, input: Facts, mark: TaintMark) {
        check(config.run1) { "Requests exist only in run 1" }
        check(input is FlowTree && premise is InitialAp) { "A request needs a FLOW input premise" }
        addMarkRequest(premise, mark)
    }

    internal fun cut(point: Cut, premise: PremiseKey, facts: Facts, sink: ResultSink) {
        runnerPort.onCut?.invoke(point, facts)
        ops.limit(facts, limit, collect(premise, facts, sink))
    }

    private val hasPendingAbstractionWork: Boolean get() =
        abstraction?.hasPending == true || localServices.hasPendingRequests

    private fun flushAbstractionWork(quantum: Int): Boolean {
        if (!localServices.flushRequests(quantum)) return false
        return abstraction?.flush(quantum) ?: true
    }

    override fun work(quantum: Int): Boolean {
        require(quantum > 0) { "The work quantum must be positive" }
        checkNotNull(port) { "The method is frozen" }
        val zeroAtStart = worklist.hasZeroWork
        var processed = 0
        while (true) {
            ap.cancellation.checkpoint()
            while (!worklist.isEmpty) {
                // Keep the zero phase separate. A selected nonzero quantum also gives deferred
                // requests one bounded service opportunity when this method's fact queue stays busy.
                if (worklist.hasZeroWork != zeroAtStart) return false
                if (processed >= quantum) {
                    if (!zeroAtStart) flushAbstractionWork(quantum)
                    return false
                }
                processed++
                process(worklist.removeNext())
                worklist.afterProcess()
            }
            flushPublications()
            ap.cancellation.checkpoint()
            if (!worklist.isEmpty || pending.isNotEmpty()) continue
            val requestsComplete = flushAbstractionWork(quantum)
            ap.cancellation.checkpoint()
            // Answers and sends can synchronously create facts, publications or a new request
            // frontier. Retain the existing counted Work token until all of them are complete.
            if (!requestsComplete) return false
            if (worklist.isEmpty && pending.isEmpty() && !hasPendingAbstractionWork) return true
        }
    }

    private fun process(item: EdgeDelta) {
        ap.cancellation.checkpoint()
        steps++
        runnerPort.onProcess?.invoke(key, item)
        if (context.callAt(item.node) != null) callServices.process(item) else runStatement(item)
    }

    private fun runStatement(item: EdgeDelta) {
        forms.withStatement(key, item.node) { summary ->
            applier.statement(summary, item.premise, item.facts,
                Place(item.node, config.run1, sources = true),
                sink = { premise, result ->
                    cut(Cut.STATEMENT, premise, result) { pr, facts -> emitAfter(pr, item.node, facts) }
                }, untouched = { _, _ -> emitUnchanged(item) })
        }
    }

    internal fun emitAfter(premise: PremiseKey, node: CommonInst, facts: Facts) {
        exitRulesAt(premise, node, facts)
        context.forEachSuccessor(node) { successor ->
            ap.cancellation.checkpoint()
            edges.add(successor, premise, facts)?.let { push(EdgeDelta(premise, successor, it)) }
        }
    }

    internal fun emitUnchanged(item: EdgeDelta) {
        exitRulesAt(item.premise, item.node, item.facts)
        context.forEachSuccessor(item.node) { successor ->
            ap.cancellation.checkpoint()
            if (worklist.addUnchanged(EdgeDelta(item.premise, successor, item.facts))) requestWork()
        }
    }

    private fun exitRulesAt(premise: PremiseKey, node: CommonInst, facts: Facts) {
        if (context.isEnd(node)) endAt(premise, node, facts, summary = true)
        else if (context.isExceptionalExit(node)) endAt(premise, node, facts, summary = false)
    }

    private fun push(delta: EdgeDelta) {
        checkKinds(delta.premise, delta.facts)
        edgeDeltas++
        if (delta.facts.layer == Layer.DEMAND) demandLayerEdges++
        worklist.add(delta)
        requestWork()
    }

    internal fun checkKinds(premise: PremiseKey, facts: Facts) {
        check((facts is FlowTree) == premise.isFlow) { "K6: the conclusion kind follows its premise" }
        check(premise !is PremiseSet || facts is TaintTree) { "A multi-premise edge must be TAINT" }
    }

    internal fun requestWork() { runnerPort.enqueue(this) }

    private inner class RuleWorklist(
        private val rules: RuleStatement,
        private val node: CommonInst,
        private val point: Cut,
    ) {
        val items = ArrayList<Pair<PremiseKey, Facts>>()
        private val seen = HashSet<Pair<PremiseKey, Facts>>()
        private val add: ResultSink = { premise, facts ->
            cut(point, premise, facts) { pr, result -> if (seen.add(pr to result)) items += pr to result }
        }
        private val reversedEnds: List<Pair<SinkRule, List<MicroEdge>>> =
            if (forward) emptyList() else rules.sinks.mapNotNull { sink ->
                ap.cancellation.checkpoint()
                rules.endFacts.edges.filter { edge ->
                    ap.cancellation.checkpoint()
                    sink.endFacts.any {
                        ap.cancellation.checkpoint()
                        it.forward == edge.forward
                    }
                }
                    .takeIf { it.isNotEmpty() }?.let { sink to it }
            }

        fun input(premise: PremiseKey, facts: Facts): Unit =
            applier.statement(rules.summary, premise, facts, Place(node, config.run1, sources = true), sink = add)

        fun drain(after: (PremiseKey, Facts, Triggers) -> Unit = { _, _, _ -> }) {
            var index = 0
            while (index < items.size) {
                ap.cancellation.checkpoint()
                val (premise, facts) = items[index++]
                val triggers = localServices.checkSinks(premise, node, facts, rules.sinks)
                for ((sink, layer) in triggers.fired) {
                    ap.cancellation.checkpoint()
                    applier.gen(sink.endFacts, ap.zero, Reach.of(layer), add)
                }
                for ((sink, edges) in reversedEnds) {
                    ap.cancellation.checkpoint()
                    var applied = false
                    applier.gen(edges, premise, facts) { pr, result -> applied = true; add(pr, result) }
                    if (applied) localServices.fireTriggerSeeds(node, sink) { seed -> input(ap.zero, seed) }
                }
                after(premise, facts, triggers)
            }
        }
    }

    private fun startAt(premise: PremiseKey, node: CommonInst, start: Facts) {
        forms.withStartRules(key, node) { rules ->
            val work = RuleWorklist(rules, node,
                if (forward) Cut.ENTRY_RULES else Cut.EXIT_RULES)
            work.input(premise, start)
            if (!forward && premise.isZero) localServices.fireSinkSeeds(node) { seed -> work.input(ap.zero, seed) }
            work.drain()
            for ((pr, facts) in work.items) {
                ap.cancellation.checkpoint()
                edges.add(node, pr, facts)?.let { push(EdgeDelta(pr, node, it)) }
            }
        }
    }

    private fun endAt(premise: PremiseKey, node: CommonInst, facts: Facts, summary: Boolean) {
        forms.withEndRules(key, node) { exit ->
            val work = RuleWorklist(exit.rules, node, if (forward) Cut.EXIT_RULES else Cut.ENTRY_RULES)
            work.input(premise, facts)
            work.drain { pr, value, triggers ->
                if (!summary) return@drain
                var result: Facts? = value
                if (exit.globalStateDrop && pr.isZero && value.base == AccessPathBase.ClassStatic)
                    for (part in triggers.parts) {
                        ap.cancellation.checkpoint()
                        result = result?.let { ops.without(it, part) }
                    }
                if (pr.isZero && value is TaintTree)
                    exit.entryMarkRemoval(value.base)?.let { marks -> result = result?.let { ops.withoutMarks(it, marks) } }
                result?.let { summaryDelta(pr, it) }
            }
        }
    }

    internal fun summaryDelta(premise: PremiseKey, facts: Facts) {
        checkKinds(premise, facts)
        if (!context.shared.interpreter.isSummaryBase(facts.base)) return
        val delta = summaries.add(premise, facts) ?: return
        edgeDeltas++
        if (delta.layer == Layer.DEMAND) demandLayerEdges++
        val selected = ops.demandPart(premise, delta, config.direction) != null
        val publications = published(premise, delta)
        for (piece in publications) {
            ap.cancellation.checkpoint()
            pending += Publication(premise, piece)
            if (selected) summaries.addDemand(premise, piece)
        }
        if (publications.isNotEmpty()) requestWork()
    }

    private fun published(premise: PremiseKey, facts: Facts): Collection<Facts> = when {
        config.run1 || (!forward && premise.isZero) -> listOf(facts)
        else -> LinkedHashSet<Facts>().also { out ->
            premise.forEachMember { member ->
                ap.cancellation.checkpoint()
                out += restrictBy(ops, checkNotNull(config.demand), key, member, facts)
            }
        }
    }

    private fun flushPublications() {
        if (pending.isEmpty()) return
        val detached = pending
        pending = ArrayList()
        val batch: List<Publication> = Collections.unmodifiableList(detached)
        try {
            checkNotNull(publicationSink).publish(key, batch)
        } catch (error: Throwable) {
            pending.addAll(0, detached)
            throw error
        }
    }

    override fun applySummary(part: Subscription, pub: Publication, member: Int): Unit =
        callServices.applySummary(part, pub, member)
    override fun applyRecord(part: Subscription, record: Record): Unit = callServices.applyRecord(part, record)
    override fun applyRecordView(part: Subscription, view: RecordLeafView): Unit = callServices.applyRecordView(part, view)
    internal fun countRecordCrossing() { recordCrossings++ }
    internal fun countTriggerSeed() { triggerSeeds++ }

    override fun freeze() {
        check(worklist.isEmpty && pending.isEmpty() && !hasPendingAbstractionWork) {
            "A method cannot freeze with unfinished work"
        }
        var failure: Throwable? = null
        fun release(action: () -> Unit) {
            try { action() }
            catch (error: Throwable) {
                val first = failure
                if (first == null) failure = error
                else if (error !== first) first.addSuppressed(error)
            }
        }
        try {
            release { releaseAbstraction() }
            release { localServices.freeze() }
            release { callServices.freeze() }
        } finally {
            local = null
            calls = null
            publicationSink = null
            port = null
            runContext = null
            worklist.clear()
            pending = ArrayList()
            edgeStore = null
            initialStore = null
        }
        failure?.let { throw it }
    }
}

/** Index candidates do not replace AP's complete premise and mark/location test. */
fun restrictBy(ops: ApOps, demand: DemandStore, key: MethodKey, initial: InitialAp, facts: Facts): Sequence<Facts> =
    demand.covering(key, initial.base, initial.path).asSequence().flatMap { pattern ->
        ops.manager.cancellation.checkpoint()
        ops.restrict(initial, facts, pattern)
    }
