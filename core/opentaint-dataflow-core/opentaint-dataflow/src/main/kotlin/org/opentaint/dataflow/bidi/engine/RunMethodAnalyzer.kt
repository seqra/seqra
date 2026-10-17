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
    private val currentQueries = if (config.restricted) StrongQueries(ops, mode) else null
    private var temporary = RequestStorage()
    private var temporaryStatic = LinkedHashMap<Run1Slot.StaticField, RequestMarkSetBuilder>()
    private var temporarySinceNanos = 0L
    internal val staticFlow = if (config.run1) Run1StaticFlow(this) else null
    internal val concreteStaticFlow = if (config.run1) Run1ConcreteStaticFlow(this) else null
    internal fun staticEntryFacts(): FlowTree? = staticFlow?.entryFacts()
    internal fun concreteStaticEntryFacts(): TaintTree? = concreteStaticFlow?.entryFacts()
    internal var abstraction: Run1Abstraction? = if (config.run1) Run1Abstraction(
        ops, context.shared.run1StaticProfiles?.get(key.method),
        answer = ::addInitial,
        borrowRequests = { input -> runnerPort.summaryStorage(key).borrowMatchingRequests(input) },
        collectRequests = ::collectRequests,
        statistics = context.shared.requestBatchStatistics,
        policyStatistics = context.shared.abstractionStatistics,
        owner = key,
        staticEntries = ::addStaticEntries,
    ) else null
        private set
    internal fun releaseAbstraction() {
        abstraction?.release(); abstraction = null
        staticFlow?.clear()
        concreteStaticFlow?.clear()
        temporaryStatic.clear()
        if (!temporary.isEmpty) {
            context.shared.abstractionStatistics?.temporaryChanged(-temporary.dynamicRows.toLong(), -temporary.dynamicMembers)
            temporary = RequestStorage()
        }
        temporarySinceNanos = 0L
    }
    internal fun unchangedStaticInitial(premise: PremiseKey): InitialAp? =
        null // Static interests now originate here; no inherited B justifies suppression.

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

    private var initialized = false

    /** Called only by the registered actor's owner; use the ordinary publication path. */
    private fun initialize() {
        if (initialized) return
        initialized = true
        if (!config.run1) return
        val identity = PathFact(STATIC, emptyList(), Tail.STAR, MarkSlot.STAR)
        val premise = ap.initial(Pattern(identity, ExclusionSet.Empty))
        summaryDelta(premise, ap.factsOf(identity, ExclusionSet.Empty, Layer.DEMAND))
    }

    fun addRootZero(): Unit = addInitial(ap.zero)

    internal fun addStaticEntries(entries: FlowTree) {
        initialize()
        nonZeroInitial = true
        checkNotNull(staticFlow).add(entries)
    }

    internal fun addConcreteStaticEntries(entries: TaintTree) {
        initialize()
        nonZeroInitial = true
        val lane = checkNotNull(concreteStaticFlow)
        // TAINT supplies geometry as well as actual T; bypassing LinkIn must
        // preserve the abstraction's independent abstract dependency lane.
        addStaticEntries(lane.entries.abstractEntries(entries))
        lane.add(entries)
    }

    internal fun startStaticScalar(initial: InitialAp, node: CommonInst) {
        val fresh = initials.add(initial)
        context.shared.abstractionStatistics?.initial(fresh)
        startAt(initial, node, normalizeFlow(initial, ops.startFact(initial)))
    }

    internal fun processStaticScalar(item: EdgeDelta) = runStatement(item)
    internal fun finishStaticScalar(initial: InitialAp, node: CommonInst, facts: FlowTree) =
        endAt(initial, node, facts, summary = context.isEnd(node))
    internal fun finishConcreteStaticScalar(initial: InitialAp, node: CommonInst, facts: TaintTree) =
        endAt(initial, node, facts, summary = context.isEnd(node))
    internal fun processStaticCall(node: CommonInst, entries: FlowTree) = callServices.processStatic(node, entries)
    internal fun resumeStaticCall(part: StaticSubscription, entries: FlowTree) = callServices.resumeStatic(part, entries)
    internal fun processConcreteStaticCall(node: CommonInst, entries: TaintTree) = callServices.processConcreteStatic(node, entries)

    fun addZeroEntry() {
        check(!forward) { "Only a backward callee has a direct zero entry" }
        addInitial(ap.zero)
    }

    fun addLink(ref: CallerRef, linkLayer: Layer, added: Facts) {
        ap.cancellation.checkpoint()
        initialize()
        check(added.base !is AccessPathBase.CleanerTemporary) { "A field-action temporary cannot enter an added link" }
        check(linkLayer == added.layer) { "The link layer must be the added fact's layer" }
        context.shared.abstractionStatistics?.arrival()
        // This is input presence for NO_DEMAND_EDGE, not a distinct retained-group count.
        if (added.layer == Layer.DEMAND && !sawDemandInput) { sawDemandInput = true; demandLayerEdges++ }
        if (config.run1) checkNotNull(abstraction).emit(ref, added) else {
            val guides = sequence {
                val found = LinkedHashSet<DemandPattern>()
                for (leaf in ops.leaves(added)) {
                    ap.cancellation.checkpoint()
                    found += checkNotNull(config.demand).near(key, leaf.fact.base, ap.path(leaf.fact.path))
                }
                for (guide in found) {
                    ap.cancellation.checkpoint()
                    context.shared.abstractionStatistics?.intersection()
                    yield(guide)
                }
            }
            val starts = checkNotNull(currentQueries).admit(added, guides)
            if (starts.isNotEmpty()) {
                // A prior guide may already have completed this premise. Its raw
                // summary remains available even if its old exit hint rejected it.
                for ((premise, facts) in summaries.all()) {
                    ap.cancellation.checkpoint()
                    if (currentQueries.permits(premise)) queuePublications(premise, facts)
                }
                starts.forEach(::addInitial)
            }
            // Concrete guide intersections refine actual queries. Abstract
            // guidance must not start a wider executable FLOW transfer here.
        }
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

    fun addMarkRequests(rows: RequestRows) {
        check(config.run1) { "Requests exist only in run 1" }
        for ((slot, marks) in rows) { ap.cancellation.checkpoint(); collectRequests(slot, marks) }
    }

    internal fun collectRequests(slot: Run1Slot, offered: RequestMarkSelection) {
        initialize()
        check(config.run1) { "Requests exist only in run 1" }
        if (slot is Run1Slot.StaticField) {
            val pending = temporaryStatic.getOrPut(slot, ::RequestMarkSetBuilder)
            val cursor = offered.cursor()
            while (!cursor.isComplete) {
                ap.cancellation.checkpoint()
                cursor.step(256) { pending.add(it) }
            }
            if (!pending.isEmpty) requestWork()
            return
        }
        val statistics = context.shared.abstractionStatistics
        val counts = statistics?.let { RequestAdmissionCounts() }
        val beforeRows = temporary.dynamicRows
        val beforeMembers = temporary.dynamicMembers
        try {
            val fresh = offered.minus(runnerPort.summaryStorage(key).borrowRequest(slot), counts, ap.cancellation::checkpoint)
            if (fresh.isEmpty) return
            val delta = temporary.addAll(slot, fresh, counts, ap.cancellation::checkpoint)
            if (!delta.isEmpty && temporarySinceNanos == 0L && context.shared.requestBatchStatistics != null)
                temporarySinceNanos = System.nanoTime()
            requestWork() // also schedules request-only actors; producers never read P
        } finally {
            if (counts != null) {
                counts.freshTemporaryMembers = temporary.dynamicMembers - beforeMembers
                statistics?.temporaryChanged((temporary.dynamicRows - beforeRows).toLong(), counts.freshTemporaryMembers)
                statistics?.requestOffer(counts)
            }
        }
    }

    internal fun addInitial(initial: InitialAp) {
        ap.cancellation.checkpoint()
        initialize()
        if (config.run1) {
            validateRun1Initial(initial, limit)
            check(initial.base != STATIC || context.shared.staticFootprint(key.method) != null) {
                "A run-1 static initial needs its frozen prescan footprint"
            }
        }
        check(forward || initial.tail != Tail.ANY_TAINT) { "A backward run has no must premise" }
        if (concreteStaticFlow?.entries?.accepts(initial) == true) {
            nonZeroInitial = true
            concreteStaticFlow.add(initial)
            return
        }
        val fresh = initials.add(initial)
        context.shared.abstractionStatistics?.initial(fresh)
        if (!fresh) return
        if (!initial.isZero) nonZeroInitial = true
        val start = normalizeFlow(initial, ops.startFact(initial))
        for (node in forms.startNodes(key, zero = initial.isZero)) {
            ap.cancellation.checkpoint()
            startAt(initial, node, start)
        }
    }

    internal fun collect(premise: PremiseKey, input: Facts, sink: ResultSink): ApOut = object : ApOut {
        override fun result(f: Facts) {
            val normalized = normalizeFlow(premise, f, input)
            if (config.run1) ops.run1Cover(normalized) { sink(premise, it) } else sink(premise, normalized)
        }
        override fun markRequest(mark: TaintMark): Unit = raiseMark(premise, input, mark)
    }

    internal fun normalizeFlow(premise: PremiseKey, facts: Facts, input: Facts? = null): Facts =
        if (config.run1) normalizeRun1StaticFlow(premise, facts, input) else facts

    internal fun raiseMark(premise: PremiseKey, input: Facts, mark: TaintMark) {
        check(config.run1) { "Requests exist only in run 1" }
        check(input is FlowTree && premise is InitialAp) { "A request needs a FLOW input premise" }
        addMarkRequest(premise, mark)
    }

    internal fun cut(point: Cut, premise: PremiseKey, facts: Facts, sink: ResultSink) {
        val normalized = normalizeFlow(premise, facts)
        runnerPort.onCut?.invoke(point, normalized)
        val out = collect(premise, normalized, sink)
        if (config.run1) ops.run1Cover(normalized) { ops.limit(it, limit, out) }
        else ops.limit(normalized, limit, out)
    }

    private val hasPendingAbstractionWork: Boolean get() =
        !temporary.isEmpty || temporaryStatic.isNotEmpty()

    private fun commitRequests() {
        if (!config.run1) return
        if (temporaryStatic.isNotEmpty()) {
            val rows = temporaryStatic.mapValues { it.value.takeSnapshot() }
            val fresh = checkNotNull(runnerPort.staticNeedRouter) { "Run-1 static needs require their counted router" }
                .commitSourceUnions(key, rows)
            checkNotNull(runnerPort.pendingNeedReplay) { "Static source replay needs its runner owner" }.sourcesCommitted(fresh)
            temporaryStatic = LinkedHashMap()
        }
        if (temporary.isEmpty) return
        if (temporarySinceNanos != 0L)
            context.shared.requestBatchStatistics?.serviceDelay(System.nanoTime() - temporarySinceNanos)
        runnerPort.summaryStorage(key).commit(temporary)
        context.shared.abstractionStatistics?.temporaryChanged(-temporary.dynamicRows.toLong(), -temporary.dynamicMembers)
        temporary = RequestStorage() // replace only after synchronous commit and secured sends
        temporarySinceNanos = 0L
    }

    override fun work(quantum: Int): Boolean {
        require(quantum > 0) { "The work quantum must be positive" }
        checkNotNull(port) { "The method is frozen" }
        val zeroAtStart = worklist.hasZeroWork
        var processed = 0
        while (true) {
            ap.cancellation.checkpoint()
            while (!worklist.isEmpty) {
                // Keep the zero phase separate. A quantum yield retains P without publishing it;
                // only the method fact-queue-empty boundary commits request memberships.
                if (worklist.hasZeroWork != zeroAtStart) return false
                if (processed >= quantum) {
                    return false
                }
                processed++
                process(worklist.removeNext())
                worklist.afterProcess()
            }
            while (staticFlow?.isEmpty == false) {
                if (zeroAtStart || processed >= quantum) return false
                processed++
                steps++
                staticFlow.work()
                if (!worklist.isEmpty) break
            }
            while (concreteStaticFlow?.isEmpty == false) {
                if (zeroAtStart || processed >= quantum) return false
                processed++
                steps++
                concreteStaticFlow.work()
                if (!worklist.isEmpty || staticFlow?.isEmpty == false) break
            }
            if (!worklist.isEmpty) continue
            if (staticFlow?.isEmpty == false) continue
            flushPublications()
            ap.cancellation.checkpoint()
            if (!worklist.isEmpty || pending.isNotEmpty()) continue
            commitRequests()
            ap.cancellation.checkpoint()
            // Retain the counted Work reservation through P commit/replacement and sends.
            if (worklist.isEmpty && pending.isEmpty() && !hasPendingAbstractionWork &&
                staticFlow?.isEmpty != false && concreteStaticFlow?.isEmpty != false) return true
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

    internal fun emitAfter(premise: PremiseKey, node: CommonInst, value: Facts) {
        val normalized = normalizeFlow(premise, value)
        if (config.run1) ops.run1Cover(normalized) { emitCovered(premise, node, it) }
        else emitCovered(premise, node, normalized)
    }

    private fun emitCovered(premise: PremiseKey, node: CommonInst, facts: Facts) {
        if (staticFlow?.capture(premise, node, facts) == true) return
        if (concreteStaticFlow?.capture(premise, node, facts) == true) return
        exitRulesAt(premise, node, facts)
        context.forEachSuccessor(node) { successor ->
            ap.cancellation.checkpoint()
            edges.add(successor, premise, facts)?.let { push(EdgeDelta(premise, successor, it)) }
        }
    }

    internal fun emitUnchanged(item: EdgeDelta) {
        val normalized = normalizeFlow(item.premise, item.facts)
        if (staticFlow?.capture(item.premise, item.node, normalized) == true) return
        if (concreteStaticFlow?.capture(item.premise, item.node, normalized) == true) return
        exitRulesAt(item.premise, item.node, normalized)
        context.forEachSuccessor(item.node) { successor ->
            ap.cancellation.checkpoint()
            if (worklist.addUnchanged(EdgeDelta(item.premise, successor, normalized))) requestWork()
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
        if (config.run1) when (facts) {
            is FlowTree -> {
                check(facts.exclusion == ExclusionSet.Empty) { "Run-1 FLOW has Empty field E" }
                check(normalizeRun1StaticFlow(premise, facts) === facts) { "Run-1 static FLOW is DEMAND before admission" }
            }
            is TaintTree -> {
                check(facts.exclusion == ExclusionSet.Empty) { "Run-1 TAINT has Empty field E" }
                check(normalizeRun1StaticFlow(premise, facts) === facts) { "Run-1 static TAINT is DEMAND before admission" }
            }
            is Reach -> Unit
        }
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

        fun input(premise: PremiseKey, facts: Facts) {
            applier.statement(rules.summary, premise, facts, Place(node, config.run1, sources = true), sink = add)
        }

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

    internal fun summaryDelta(premise: PremiseKey, value: Facts) {
        val normalized = normalizeFlow(premise, value)
        if (config.run1) ops.run1Cover(normalized) { retainSummary(premise, it) }
        else retainSummary(premise, normalized)
    }

    private fun retainSummary(premise: PremiseKey, facts: Facts) {
        checkKinds(premise, facts)
        if (!context.shared.interpreter.isSummaryBase(facts.base)) return
        val retained = if (config.run1) compactRun1StaticIdentity(ops, premise, facts) ?: return else facts
        val delta = summaries.add(premise, retained) ?: return
        edgeDeltas++
        if (delta.layer == Layer.DEMAND) demandLayerEdges++
        queuePublications(premise, delta)
    }

    private fun queuePublications(premise: PremiseKey, facts: Facts) {
        val selected = config.run1 || ops.demandPart(premise, facts, config.direction) != null
        val publications = published(premise, facts)
        for (piece in publications) {
            ap.cancellation.checkpoint()
            pending += Publication(premise, piece)
            if (selected) summaries.addDemand(premise, piece)
        }
        if (publications.isNotEmpty()) requestWork()
    }

    private fun published(premise: PremiseKey, facts: Facts): Collection<Facts> = when {
        config.run1 || (!forward && premise.isZero) || currentQueries?.permits(premise) == true -> listOf(facts)
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
    override fun applyStaticDefault(part: StaticSubscription, entries: FlowTree) = resumeStaticCall(part, entries)
    override fun applyStaticSummary(part: StaticSubscription, input: Subscription, pub: Publication) =
        callServices.applyStaticSummary(part, input, pub)
    override fun applyConcreteStaticDefault(part: ConcreteStaticSubscription, entries: TaintTree) =
        callServices.resumeConcreteStatic(part, entries)
    override fun applyConcreteStaticSummary(part: ConcreteStaticSubscription, input: Subscription, pub: Publication, member: Int) =
        callServices.applyConcreteStaticSummary(part, input, pub, member)
    internal fun countRecordCrossing() { recordCrossings++ }
    internal fun countTriggerSeed() { triggerSeeds++ }

    override fun freeze() {
        check(worklist.isEmpty && pending.isEmpty() && !hasPendingAbstractionWork && staticFlow?.isEmpty != false) {
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
            currentQueries?.clear()
            temporary = RequestStorage()
            staticFlow?.clear()
            concreteStaticFlow?.clear()
            temporaryStatic.clear()
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
