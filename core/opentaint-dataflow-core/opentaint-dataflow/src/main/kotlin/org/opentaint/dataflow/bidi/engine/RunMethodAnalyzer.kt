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
    private var temporarySinceNanos = 0L
    internal var staticBatch = StaticBatch(); private set
    private val pointResumes = if (config.run1) Run1PointResume(this) else null
    private data class PointWork(val premise: PremiseKey, val facts: Facts,
                                 val site: ResumeSite, val state: CallResumeState?)
    private data class RuleItem(val premise: PremiseKey, val facts: Facts)
    private val resumeQueue = java.util.ArrayDeque<PointWork>()
    private val operationOwner = FormOwner()
    internal var abstraction: Run1Abstraction? = if (config.run1) Run1Abstraction(
        ops,
        answer = ::addInitial,
        borrowRequests = { input -> runnerPort.summaryStorage(key).borrowMatchingRequests(input) },
        collectRequests = ::collectRequests,
        statistics = context.shared.requestBatchStatistics,
        policyStatistics = context.shared.abstractionStatistics,
        owner = key,
    ) else null
        private set
    internal fun releaseAbstraction() {
        abstraction?.release(); abstraction = null
        if (!temporary.isEmpty) {
            context.shared.abstractionStatistics?.temporaryChanged(-temporary.dynamicRows.toLong(), -temporary.dynamicMembers)
            temporary = RequestStorage()
        }
        temporarySinceNanos = 0L
    }
    internal fun unchangedStaticInitial(premise: PremiseKey): InitialAp? =
        null // The selected design has no inherited static request suppression.

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
        // Static reads and effects start only at their reached operation.
    }

    fun addRootZero(): Unit = addInitial(ap.zero)

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
            check(initial.base != STATIC) { "Run-1 static descriptors resume at an operation, not method entry" }
        }
        check(forward || initial.tail != Tail.ANY_TAINT) { "A backward run has no must premise" }
        val fresh = initials.add(initial)
        context.shared.abstractionStatistics?.initial(fresh)
        if (!fresh) return
        if (!initial.isZero) nonZeroInitial = true
        val start = forceRun1Layer(initial, ops.startFact(initial, mode))
        for (node in forms.startNodes(key, zero = initial.isZero)) {
            ap.cancellation.checkpoint()
            startAt(initial, node, start)
        }
    }

    internal fun collect(premise: PremiseKey, input: Facts, sink: ResultSink): ApOut {
        return object : ApOut {
            override fun result(f: Facts) {
                val runtime = forceRun1Layer(premise, f)
                if (config.run1) ops.run1Cover(runtime) { sink(premise, it) } else sink(premise, runtime)
            }
            override fun markRequest(mark: TaintMark): Unit = raiseMark(premise, input, mark)
        }
    }

    internal fun raiseMark(premise: PremiseKey, input: Facts, mark: TaintMark) {
        check(config.run1) { "Requests exist only in run 1" }
        check(input is FlowTree && premise is InitialAp) { "A request needs a FLOW input premise" }
        check(premise.base != STATIC) { "A static-origin interest needs an exact operation input" }
        addMarkRequest(premise, mark)
    }

    internal fun operation(node: CommonInst, ordinal: Int = 0): OperationId =
        OperationId(FormKey(operationOwner, key.method, key, node), ordinal)

    internal fun forceRun1Layer(premise: PremiseKey, value: Facts): Facts {
        if (!config.run1) return value
        val runtime = ops.runtimeFact(value, mode)
        return if (runtime is TaintTree || Run1StaticService.hasStaticMember(premise)) runtime.withLayer(Layer.DEMAND) else runtime
    }

    internal fun collect(at: OperationInput, sink: ResultSink): ApOut {
        return object : ApOut {
            override fun result(f: Facts) {
                val value = forceRun1Layer(at.premise, f)
                if (config.run1) ops.run1Cover(value) { sink(at.premise, it) } else sink(at.premise, value)
            }
            override fun markRequest(mark: TaintMark) = needGroups(at, mark)
        }
    }

    internal fun needGroups(at: OperationInput, mark: TaintMark) {
        if (!config.run1) return
        val original = at.premise as? InitialAp ?: return
        if (at.facts !is FlowTree || original.mark !is MarkSlot.Star) return
        val atom = ap.marks.runtimeAtom(mark, mode.markMode)
        if (original.base == STATIC) checkNotNull(pointResumes).request(at, GroupSet.of(ap.marks.groupOf(atom)))
        else addMarkRequest(original, atom)
    }

    internal fun reachedOperations(summary: StatementSummary, at: OperationInput) {
        if (config.run1) checkNotNull(pointResumes).reached(summary, at)
    }

    internal fun emitOperationResult(at: OperationInput, operation: StaticOperation.Write?, value: Facts, sink: ResultSink) {
        val runtime = forceRun1Layer(at.premise, value)
        if (config.run1 && runtime.base == STATIC) {
            if (operation != null) {
                summaryDelta(at.premise, runtime.withLayer(Layer.DEMAND))
            }
            return
        }
        sink(at.premise, runtime)
    }

    fun onStaticEffect(effect: StaticOwnerEffect) {
        check(config.run1 && effect.owner == key)
        checkNotNull(pointResumes).onEffect(effect)
    }

    internal fun staticFacts(slice: RegionGroups): Facts = checkNotNull(runnerPort.staticService).facts(slice.region, slice.groups)
    internal fun staticSlices(facts: Facts): List<RegionGroups> = checkNotNull(runnerPort.staticService).slices(facts)
    internal fun staticCallInterest(part: Subscription, pub: Publication, member: Int) =
        checkNotNull(pointResumes).callInterest(part, pub, member)
    internal fun staticCallSupply(part: Subscription, pub: Publication, member: Int, supply: RegionGroups) =
        callServices.staticSupply(part, pub, member, supply)

    /** Apply a complete static tuple on this owner after releasing the service lock. */
    internal fun composeStaticSummary(premise: PremiseKey, value: Facts, inputs: List<RegionGroups>, emit: (Facts) -> Unit) {
        val out = object : ApOut {
            override fun result(f: Facts) {
                if (f is TaintTree) {
                    // Static effects leave the ordinary call suffix early. Apply
                    // the same field limit that suffix would apply to its result.
                    ops.limit(forceRun1Layer(premise, f), limit, collect(premise, f) { _, bounded -> emit(bounded) })
                }
            }
            override fun markRequest(mark: TaintMark) = Unit // summary application is not rule execution
        }
        when (premise) {
            is InitialAp -> if (premise.isZero) { if (value is TaintTree) emit(value.withLayer(Layer.DEMAND)) }
                else if (inputs.size == 1) {
                    val actual = staticFacts(inputs.single())
                    val matched = ops.satisfying(actual, premise, mode) ?: return
                    ops.applySummary(matched, premise, value, mode, out)
                }
            is PremiseSet -> if (inputs.size == premise.size && value is TaintTree) {
                val parts = inputs.mapIndexed { i, slice ->
                    ap.cancellation.checkpoint()
                    val initial = premise.member(i)
                    val matched = ops.satisfying(staticFacts(slice), initial, mode) ?: return
                    matched to initial
                }
                ops.applyCombination(parts, value, mode, out)
            }
        }
    }

    internal fun installPoint(premise: PremiseKey, value: Facts, site: ResumeSite, state: CallResumeState? = null) {
        check(config.run1)
        premise.forEachMember { validateRun1Initial(it, limit) }
        val runtime = forceRun1Layer(premise, value)
        checkKinds(premise, runtime)
        nonZeroInitial = nonZeroInitial || !premise.isZero
        resumeQueue.addLast(PointWork(premise, runtime, site, state))
        requestWork()
    }

    internal fun cut(point: Cut, premise: PremiseKey, facts: Facts, sink: ResultSink) {
        runnerPort.onCut?.invoke(point, facts)
        val out = collect(premise, facts, sink)
        if (config.run1) ops.run1Cover(facts) { ops.limit(it, limit, out) }
        else ops.limit(facts, limit, out)
    }

    private val hasPendingAbstractionWork: Boolean get() =
        !temporary.isEmpty || !staticBatch.isEmpty || resumeQueue.isNotEmpty()

    private fun commitRequests() {
        if (!config.run1 || temporary.isEmpty) return
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
            while (resumeQueue.isNotEmpty()) {
                if (processed >= quantum) return false
                processed++
                val resume = resumeQueue.removeFirst()
                when (val site = resume.site) {
                    is ResumeSite.StatementInput -> runStatement(EdgeDelta(resume.premise, site.operation.form.statement, resume.facts))
                    is ResumeSite.BoundaryInput -> if (site.atStart) startAt(resume.premise, site.operation.form.statement, resume.facts)
                        else endAt(resume.premise, site.operation.form.statement, resume.facts, summary = true)
                    is ResumeSite.ReadOutput -> installPoint(resume.premise, resume.facts, site.input, resume.state)
                    is ResumeSite.CallStageInput, is ResumeSite.CleanerInput -> callServices.resumePoint(resume.premise, resume.facts, site, resume.state)
                }
            }
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
            flushPublications()
            ap.cancellation.checkpoint()
            if (!worklist.isEmpty || pending.isNotEmpty()) continue
            commitRequests()
            if (config.run1 && !staticBatch.isEmpty) {
                val committed = staticBatch
                val effects = checkNotNull(runnerPort.staticService) { "Run-1 needs its static service" }.commit(committed)
                staticBatch = StaticBatch()
                for (effect in effects) check(runnerPort.sendStatic(effect)) { "Static effect delivery was rejected" }
            }
            ap.cancellation.checkpoint()
            // Retain the counted Work reservation through P commit/replacement and sends.
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
                Place(item.node, config.run1, sources = true, site = ResumeSite.StatementInput(operation(item.node))),
                sink = { premise, result ->
                    cut(Cut.STATEMENT, premise, result) { pr, facts -> emitAfter(pr, item.node, facts) }
                }, untouched = { _, _ -> emitUnchanged(item) })
        }
    }

    internal fun emitAfter(premise: PremiseKey, node: CommonInst, value: Facts) {
        if (config.run1 && value.base == STATIC) return // writes were retained at their logical operation output
        if (config.run1) ops.run1Cover(forceRun1Layer(premise, value)) { emitCovered(premise, node, it) }
        else emitCovered(premise, node, value)
    }

    private fun emitCovered(premise: PremiseKey, node: CommonInst, facts: Facts) {
        exitRulesAt(premise, node, facts)
        context.forEachSuccessor(node) { successor ->
            ap.cancellation.checkpoint()
            edges.add(successor, premise, facts)?.let { push(EdgeDelta(premise, successor, it)) }
        }
    }

    internal fun emitUnchanged(item: EdgeDelta) {
        if (config.run1 && item.facts.base == STATIC) return
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
        if (config.run1) when (facts) {
            is FlowTree -> check(facts.exclusion == ExclusionSet.Empty) { "Run-1 FLOW has Empty field E" }
            is TaintTree -> check(facts.exclusion == ExclusionSet.Empty) { "Run-1 TAINT has Empty field E" }
            is Reach -> Unit
        }
    }

    internal fun requestWork() { runnerPort.enqueue(this) }

    private inner class RuleWorklist(
        private val rules: RuleStatement,
        private val node: CommonInst,
        private val point: Cut,
    ) {
        private val items = LinkedHashMap<Pair<PremiseKey, Facts>, RuleItem>()
        private val add: ResultSink = { premise, facts ->
            cut(point, premise, facts) { pr, result ->
                val key = pr to result
                items.putIfAbsent(key, RuleItem(pr, result))
            }
        }

        fun input(premise: PremiseKey, facts: Facts) {
            applier.statement(rules.summary, premise, facts, Place(node, config.run1, sources = true,
                site = ResumeSite.BoundaryInput(operation(node), point == Cut.ENTRY_RULES)), sink = add)
        }

        fun drain(after: (PremiseKey, Facts, Triggers) -> Unit = { _, _, _ -> }) {
            for (item in items.values) {
                ap.cancellation.checkpoint()
                val premise = item.premise; val facts = item.facts
                val triggers = localServices.checkSinksAt(premise, node, facts, rules.sinks,
                    OperationInput(ResumeSite.BoundaryInput(operation(node), point == Cut.ENTRY_RULES), premise, facts))
                after(premise, facts, triggers)
            }
        }

        fun forEachResult(visit: (PremiseKey, Facts) -> Unit) {
            for (item in items.values) { visit(item.premise, item.facts) }
        }
    }

    private fun startAt(premise: PremiseKey, node: CommonInst, start: Facts) {
        forms.withStartRules(key, node) { rules ->
            val work = RuleWorklist(rules, node,
                if (forward) Cut.ENTRY_RULES else Cut.EXIT_RULES)
            work.input(premise, start)
            if (!forward && premise.isZero) localServices.fireSinkSeeds(node) { seed -> work.input(ap.zero, seed) }
            work.drain()
            work.forEachResult { pr, facts ->
                ap.cancellation.checkpoint()
                if (config.run1 && facts.base == STATIC) return@forEachResult
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
                if (config.run1 && value.base == STATIC) return@drain
                var result: Facts? = value
                if (exit.globalStateDrop && pr.isZero && value.base == AccessPathBase.ClassStatic)
                    for (part in triggers.parts) {
                        ap.cancellation.checkpoint()
                        result = result?.let { ops.without(it, part) }
                    }
                // Entry removal selects marks. Run-1 groups cannot be cleaned;
                // this actual group branch already carries its DEMAND repair.
                // A ZERO original has no ordinary Base request to propagate.
                if (!config.run1 && pr.isZero && value is TaintTree)
                    exit.entryMarkRemoval(value.base)?.let { marks -> result = result?.let { ops.withoutMarks(it, marks) } }
                result?.let { summaryDelta(pr, it) }
            }
        }
    }

    internal fun summaryDelta(premise: PremiseKey, value: Facts) {
        if (config.run1) ops.run1Cover(forceRun1Layer(premise, value)) { covered ->
            // Early static publication bypasses the ordinary successor cut.
            // Retain the existing bounded AP result before indexing the effect.
            ops.limit(covered, limit, collect(premise, covered, ::retainSummary))
        }
        else retainSummary(premise, value)
    }

    private fun retainSummary(premise: PremiseKey, facts: Facts) {
        checkKinds(premise, facts)
        if (!context.shared.interpreter.isSummaryBase(facts.base)) return
        val delta = summaries.add(premise, facts)
        if (delta == null) return
        edgeDeltas++
        if (delta.layer == Layer.DEMAND) demandLayerEdges++
        if (config.run1 && delta.base == STATIC) checkNotNull(pointResumes).summary(premise, delta)
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
    override fun applyStaticSummary(part: Subscription, pub: Publication, member: Int): Unit =
        callServices.applyStaticSummary(part, pub, member)
    override fun requestStaticGroups(part: Subscription, marks: RequestMarkSelection) {
        check(config.run1 && part.ref.premise.isFlow && part.added is FlowTree)
        val at = OperationInput(ResumeSite.CallStageInput(operation(part.ref.call, part.ref.stageOrdinal)),
            part.ref.premise, part.added, CallResumeState(part.ref.callerLayer))
        val cursor = marks.cursor()
        while (!cursor.isComplete) {
            ap.cancellation.checkpoint()
            cursor.step(256) { needGroups(at, it) }
        }
    }
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
            release { pointResumes?.clear() }

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
