# Analyzer core — implementation proposal

This proposal implements phase 2 of [bidirectional-task.md](../bidirectional-task.md) for the JVM.
[analyzer-core.md](analyzer-core.md), [ap.md](ap.md) and [interpreter.md](interpreter.md) define the behavior.
[ap-impl.md](ap-impl.md) supplies the AP operations, stores and directed forms.

The snippets show the production algorithms and interfaces. Sections 4.1–4.11 are members of one
`RunMethodAnalyzer`, except the named top-level types. They are proposal code, not existing Kotlin files.
Use ASD-STE100 Simplified Technical English for new documentation.

Implement the current rules directly: forward conjunctions can be normal; backward reversal splits them into
independent demand branches; forward normal singletons and backward normal nonzero singletons become records
and give no callee demand; selected
publications keep their original layer. There is no crossability test. Requests exist only in run 1.

The local Lean results and open obligations are listed in §10. They do not prove the full current iteration.

## 0. Conventions and design decisions

### 0.1 Conventions

A bare `§n` names this document. Other references include the file name. Rule ids belong to
`analyzer-core.md`, except an id explicitly qualified by another file. `ap-impl.md` Part I is §0–§8;
Part II is §20–§34. Repository paths use these roots:

| Name | Repository path |
|---|---|
| `CORE` | `core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow` |
| `JVM` | `core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm` |
| `SAST` | `core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint` |
| `TEST` | `core/opentaint-dataflow-core/opentaint-dataflow/src/test/kotlin/org/opentaint/dataflow` |

### 0.2 Design decisions

These are implementation choices. The spec requirements appear in the right column.

| Id | Implementation choice | Required behavior |
|---|---|---|
| DD1 | Add `bidi.*` packages in the existing Gradle modules. | Reuse current utilities; preserve the prescan engine (`analyzer-core.md` §8). |
| DD2 | Process `EdgeDelta(premise, node, facts)`; `Facts` holds the kind and layer. | Store and process premise sets, conclusions and layers (`analyzer-core.md` §4.3). |
| DD3 | Carry a group of added facts per link key; return a delta on insertion. | Preserve each link, its added fact and caller reference; deduplicate exactly (`analyzer-core.md` §5.1). |
| DD4 | Use structural equality for facts and work items. | Repeated items do not restart a boundary or conjunction; the unchanged path terminates. |
| DD5 | Put E6 joins in the caller's `ConjunctionStore.ndJoin`. | One call must supply all premise members (`analyzer-core.md` §5.4). |
| DD6 | Use the interpreter's `MethodForms` cache and make `DirectedForms` per run. | Call plans and entry rules use their method key's context (`analyzer-core.md` §4.8). |
| DD7 | Dispatch REACH at a call separately. | Only `{zero} → zero` starts `zin`, `seed` and `zret`; other REACH edges only pass (`ap.md` §9.2). |
| DD8 | Apply the source filter and hit hook only at source-seed places. | End facts and replayed records are not source instructions (`analyzer-core.md` §4.7). |
| DD9 | Copy the prescan's fact type checker. | Apply forward type filters; omit them backward (`analyzer-core.md` §3). |
| DD10 | Compare with a per-path `NaiveClosure`; use a deterministic schedule fuzzer. | Validate all method, call, request and pipeline operations (§9). |
| DD11 | Use REACH, FLOW and TAINT types from `ap-impl.md`. | Preserve `ap.md` §7.2, W1, W2, W6 and W8. |
| DD12 | Share `FormApplier`, ordered actions, boundary work, cuts and matching. | Apply the same spec operation at every required site. Keep persistence R1 and demand selection separate. |
| DD13 | Keep tail conversion and exclusions in the AP facade. | `[any-taint]` exists only forward; reversed conjunction results are demand (`ap.md` §9.2). |

Queue ordering, pools, test hooks and index layouts do not define transfer semantics. Alternative layouts must
preserve the listed requirements. Add an optimization only after its result equals the direct concept and its
cost benefit is established for the intended scope.

## 1. Package map

```
CORE/bidi/engine/
  SharedObjects.kt, RunConfig.kt                  analysis objects and one-run constants
  RunManager.kt, RunStatus.kt, InFlight.kt        routing, lifetime, completion and join
  UnitRunner.kt, RunnerPort.kt, EventQueue.kt     one owner per unit; fixed queue keys
  RunEvent.kt, EventDispatch.kt                  events and their handlers
  RunMethodAnalyzer.kt, DeltaWorklist.kt         method state and transfer algorithms
  SummaryStorage.kt, SubscriptionManager.kt     callee publication and caller replay
  MethodContextCache.kt                         interpreter forms and method graphs
CORE/bidi/driver/
  IterationDriver.kt, IterationPolicy.kt         sequential runs and their barriers
  HandOff.kt, Seeds.kt                           next demand and seeds
  Support.kt, Report.kt, Frontier.kt             confirmation, report and counters
  BidiEntry.kt                                   phase-3 hook
JVM/bidi/
  JIRBidiAnalysis.kt, JIRMethodContextCache.kt    JVM integration
```

The reuse changes of §2.1 stay in their current modules. AP and interpreter packages are defined in `ap-impl.md`.

## 2. Reuse map and the names of the other documents

### 2.1 Reuse map

Use `analyzer-core.md` §8 for the full current-code reuse map. This proposal needs these changes:

| Current component | Proposed use |
|---|---|
| Unit routing, channels, runner quantum and memory guards | Adapt in `RunManager` and `UnitRunner`; use fresh objects per run. |
| `MethodAnalyzerStorage` | Keep its one-analyzer-per-key pattern in a runner-local map. |
| `NormalMethodAnalyzer`, old edges, side-effect and delayed events | Replace with the method analyzer, typed AP stores and current requests. |
| Unchanged propagation | Keep its queue and exact repeat set; pass the processed facts. |
| Summary storage with subscription | Keep callee storage, caller subscription, replay and notification. Add a linearizable read. |
| `UnitResolver`, `LanguageManager`, `RefManager`, `Cancellation`, fact type checker | Reuse. The new analysis has its own `Cancellation`. |
| Prescan managers | Add `releasePrescan`; copy required values before release (§8.1). |
| Empty-method analyzer and core liveness/depth gate | Omit. The interpreter removes empty callees; the AP field limit is the run bound. |

Do not add limits that discard links, requests, premise members or publications. Indexes select candidates; the
full AP test decides the match. The old engine remains available for the prescan.

### 2.2 Names from `ap-impl.md`

Import these contracts from `ap-impl.md`; do not copy their algorithms here.

| Contract | Provider |
|---|---|
| `MethodKey`, `PremiseKey`, `InitialAp`, `PremiseSet`, `Layer`, `Direction`, `Tail`, `ApMode` | §0, §3 |
| `Facts`, `Reach`, `FlowTree`, `TaintTree`, `ConclusionGroup` and structural equality | §4 |
| `ApManager`, `ApOut`, `ApOps.applyEdge`, `applySummary`, `applyRecordLeaf`, `applyCombination`, `satisfying` | §5.1–§5.4 |
| `filter`, `clean`, `limit`, `checkMark`, `without`, `withoutMarks`, `targetTree` | §5.5–§5.8 |
| `startFact`, `policy`, `emit`, `restrict`, `demandPart`, `requestAction`, `leaves` | §5.9–§5.11 |
| `Pattern`, `Conclusion`, `DemandPattern`, `RecordLeafView`, `inside`, `recordDemand`, `reverseRecordLeaf`, `recordLeafIdentity` | §6 |
| `PathTrie`, method/initial/link/summary/demand/record/request/conjunction/source-hit stores | §7.2–§7.11 |
| `SinkEdge`, `SinkWitness`, vulnerability key/store, `confirmableMember`, `supplies` | §7.12 |
| `Interpreter`, `MicroEdge`, statements, `FormApplier`, `FactAlgebra`, `Place`, boundary and sink rules | §23.1–§23.4 |
| `StageKind.identityCandidateOf`, `Guard.MemoryEffect.admits`, `CallStage`, `CallPlan`, directed forms | §23.5–§23.7 |
| `ReferenceAlgebra`, `FormsReference`, `PlanItem`, `PlanHooks` | §23.8 |
| JVM interpreter, call observation, binding/static exception, boundary rules and method-entry cache | §25, §28–§31 |

`MarkCheck.Holds` exposes the matched `facts` in their effective layer. A group key includes every shared annotation:
kind, base, layer, FLOW field/mark exclusions and the normal TAINT must-tail exclusion. Native initial facts and
transient record views are distinct types. A transient concrete STAR premise is never started or persisted.

### 2.3 Additions to `analyzer-core.md` §10

The engine uses the interfaces of `analyzer-core.md` §10, with these representation choices:

| Choice | Purpose |
|---|---|
| Grouped `LinkIn` and `Subscription.added: Facts` | One event/delta per link group (DD3). |
| `matches(...): Facts?` and a premise member index | Apply only the satisfying added leaves; join E6 in the caller. |
| `Publication(premise, conclusion)` | Carry the unchanged premise key and conclusion layer. |
| `RunResult.direction`, analyzer list and counters | Read one completed run at its barrier. |
| `HandOff.demandEdges`, `SeedIndex.size`, driver frontier log | Count accepted stored patterns and keep the required log. |
| `EngineAlgebra`, `RuleWorklist`, `Cut`, `ProtocolSteps` | Share operations and expose protocol boundaries to tests. |
| Optional `onProcess`, `onCut` hooks and `demandByCause` | Observe tests and diagnostics; no transfer rule reads them. |

The grouped form must decode to the per-leaf objects of the spec. It does not permit weaker deduplication or a
new demand rule.

## 3. Entities and lifetimes (`analyzer-core.md` §2)

### 3.1 `SharedObjects`, `RunConfig`

The driver creates `SharedObjects` once. Each run receives immutable `RunConfig` constants, read-only records,
demand and seeds. Odd runs are forward; run 1 is unrestricted; every later run is restricted.

```kotlin
package org.opentaint.dataflow.bidi.engine

class SharedObjects(
    val ap: ApManager,
    val ops: ApOps,
    val interpreter: Interpreter,
    val language: LanguageManager,
    val contexts: MethodContextCache,
    val records: RecordStore,
    val vulnerabilities: VulnerabilityStore,
    val unitResolver: UnitResolver<CommonMethod>,
    val refManager: RefManager,
    val cancellation: Cancellation,
    threads: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
    val cancellationTimeout: Duration = 30.seconds,
    val diagnostics: Boolean = false,
) : AutoCloseable {

    @OptIn(DelicateCoroutinesApi::class)
    val dispatcher: ExecutorCoroutineDispatcher = newFixedThreadPoolContext(threads, "bidi-worker")

    val unresolvedObserver: UnresolvedCallObserver? get() = interpreter as? UnresolvedCallObserver
    override fun close() = dispatcher.close()
}

class RunConfig(
    val index: Int,
    val fieldLimit: Int,
    val demand: DemandStore?,
    val records: RecordStore,
    val seeds: SeedIndex,
    val roots: List<MethodKey>,
) {
    val direction: Direction get() = if (index % 2 == 1) Direction.FORWARD else Direction.BACKWARD
    val run1: Boolean get() = index == 1
    val restricted: Boolean get() = index > 1
    val mode: ApMode = ApMode(run1 = index == 1, direction = direction, fieldLimit = fieldLimit)

    val seededSources: Boolean get() = restricted && direction == Direction.FORWARD

    init { require(run1 == (demand == null)) }
}
```

### 3.2 `RunManager`

Create one manager and coroutine scope per run. The manager routes methods to units and creates one summary
storage per method key. `UnknownUnit` drops the event before counting it.

The first terminal status wins. Set status before cancellation. Always join runners before returning; a failed join
overrides the status with FAILED and exposes no stores. Only COMPLETE results provide frozen analyzers.

```kotlin
class RunManager(val config: RunConfig, val shared: SharedObjects, val steps: ProtocolSteps = ProtocolSteps.Inline) {
    init { shared.cancellation.activate() }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(shared.dispatcher + job)
    private val runners = ConcurrentHashMap<UnitType, UnitRunner>()
    private val storages = ConcurrentHashMap<MethodKey, SummaryStorage>()
    private val completion = CompletableDeferred<RunStatus>()
    private val status = AtomicReference<RunStatus?>(null)
    val ended: Boolean get() = status.get() != null
    val inFlight = InFlight {
        if (status.compareAndSet(null, RunStatus.COMPLETE)) completion.complete(RunStatus.COMPLETE)
    }
    val forms: DirectedForms = shared.contexts.directed(config.direction)
    private val memory = MemoryManager(shared.refManager, OOM_THRESHOLD) { fail(RunStatus.OOM) }

    fun summaryStorage(key: MethodKey): SummaryStorage =
        storages.computeIfAbsent(key) { SummaryStorage(it, shared.ap, shared.ops, steps) }

    fun runnerOf(method: MethodKey): UnitRunner? {
        val unit = shared.unitResolver.resolve(method.method)
        if (unit == UnknownUnit) return null
        return runners.computeIfAbsent(unit) { UnitRunner(it, this).also(::start) }
    }

    fun route(event: RunEvent) { runnerOf(event.target())?.post(event) }

    fun run(timeout: Duration): RunResult = memory.runWithMemoryManager {
        val joined = runBlocking {
            val progress = launch {
                var last = 0L
                while (isActive) { delay(PROGRESS_PERIOD); last = logProgress(last) }
            }
            try {
                inFlight.beforeSend()
                if (status.get() == null) for (root in config.roots) route(RunEvent.Start(root))
                inFlight.afterHandler()
                withTimeoutOrNull(timeout) { completion.await() } ?: fail(RunStatus.TIMEOUT)
            } catch (e: Throwable) {
                logger.error(e) { "Run ${config.index}: the driver thread failed" }
                fail(RunStatus.FAILED)
            }
            progress.cancel()
            stopAndJoin()
        }
        val end = if (joined) status.get()!! else RunStatus.FAILED.also { logger.error { "Run ${config.index}: a runner did not stop" } }
        val analyzers = if (end == RunStatus.COMPLETE) runners.values.flatMap { it.analyzers() }.onEach { it.freeze() } else emptyList()
        runners.clear(); storages.clear()
        RunResult(end, analyzers, config.index, config.direction, shared.vulnerabilities)
    }

    fun fail(s: RunStatus) {
        if (!status.compareAndSet(null, s)) return
        shared.cancellation.cancel()
        completion.complete(s)
    }

    private fun logProgress(last: Long): Long {
        val handled = inFlight.handled
        logger.info { "Run ${config.index}: $handled events (+${handled - last}), ${inFlight.pending} in flight" }
        logger.info { "Memory usage: ${MemoryManager.currentMemoryUsage()}/${Runtime.getRuntime().maxMemory()}" }
        return handled
    }

    private fun start(r: UnitRunner) {
        val handler = CoroutineExceptionHandler { _, e ->
            logger.error(e) { "Runner ${r.unit} failed, run ${config.index} stops" }
            fail(RunStatus.FAILED)
        }
        scope.launch(handler) { r.runLoop() }
    }

    private suspend fun stopAndJoin(): Boolean {
        runners.values.forEach { it.close() }
        job.cancel()
        return withTimeoutOrNull(shared.cancellationTimeout) { job.children.toList().joinAll(); true } ?: false
    }

    companion object {
        const val OOM_THRESHOLD = 0.90
        val PROGRESS_PERIOD = 10.seconds
    }
}

fun RunEvent.target(): MethodKey = when (this) {
    is RunEvent.Start -> root
    is RunEvent.LinkIn -> callee
    is RunEvent.ZeroIn -> callee
    is RunEvent.RequestIn -> method
    is RunEvent.Delivery, is RunEvent.Work -> error("$this goes to a known runner, not by method")
}
```

```kotlin
enum class RunStatus { COMPLETE, TIMEOUT, OOM, FAILED }

class RunResult(
    val status: RunStatus,
    val analyzers: List<RunMethodAnalyzer>,
    val runIndex: Int,
    val direction: Direction,
    val vulnerabilities: VulnerabilityStore,
) {

    fun witnessesByKey(): Map<VulnerabilityKey, List<SinkWitness>> =
        vulnerabilities.witnessesOf(runIndex).groupBy({ it.first }, { it.second })

    val demandLayerEdges: Long get() = analyzers.sumOf { it.demandLayerEdges }
    val recordCrossings: Long get() = analyzers.sumOf { it.recordCrossings }
}
```

### 3.3 `UnitRunner`, `RunnerPort`, the analyzer table

Only a unit's runner accesses its analyzer and subscription maps. The driver reads them after the join.
The two allowed direct calls are caller-side summary application and same-unit `addLink`. All other communication
uses events. `ProtocolSteps` and observer hooks support testing; production uses inline protocol steps.

```kotlin
interface RunnerPort : SubscriptionPort {
    val shared: SharedObjects
    val forms: DirectedForms
    val subscriptions: SubscriptionManager
    fun send(event: RunEvent)
    fun enqueue(analyzer: RunMethodAnalyzer)
    fun analyzer(key: MethodKey): RunMethodAnalyzer
    fun subscribe(sub: Subscription)
    fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts)

    val onProcess: ((MethodKey, EdgeDelta) -> Unit)? get() = null

    val onCut: ((Cut, Facts) -> Unit)? get() = null
}

interface SubscriptionPort {
    val config: RunConfig
    val ops: ApOps
    val steps: ProtocolSteps
    fun summaryStorage(key: MethodKey): SummaryStorage
    fun applier(caller: MethodKey): SummaryApplier
    fun post(event: RunEvent.Delivery)
}

interface SummaryApplier {
    fun applySummary(part: Subscription, pub: Publication, member: Int)
    fun applyRecord(part: Subscription, record: Record)
    fun applyRecordView(part: Subscription, view: RecordLeafView)
}

interface ProtocolSteps {
    fun replay(step: () -> Unit)
    fun notify(step: () -> Unit)
    object Inline : ProtocolSteps {
        override fun replay(step: () -> Unit) = step()
        override fun notify(step: () -> Unit) = step()
    }
}
```

```kotlin
class UnitRunner(val unit: UnitType, private val run: RunManager) : RunnerPort {
    private val channel = Channel<RunEvent>(Channel.UNLIMITED)
    private val queue = EventQueue()
    private val analyzers = LinkedHashMap<MethodKey, RunMethodAnalyzer>()
    override val subscriptions = SubscriptionManager(this)
    override val config get() = run.config
    override val shared get() = run.shared
    override val forms get() = run.forms
    override val ops get() = run.shared.ops
    override val steps get() = run.steps

    fun post(event: RunEvent) { run.inFlight.beforeSend(); channel.trySend(event) }
    override fun post(event: RunEvent.Delivery) = post(event as RunEvent)
    override fun send(event: RunEvent) = run.route(event)
    override fun enqueue(analyzer: RunMethodAnalyzer) {
        run.inFlight.beforeSend()
        queue.add(RunEvent.Work(analyzer))
    }
    override fun analyzer(key: MethodKey): RunMethodAnalyzer = analyzers.getOrPut(key) {
        check(shared.unitResolver.resolve(key.method) == unit) { "$key is not in $unit" }
        RunMethodAnalyzer(key, this)
    }
    override fun applier(caller: MethodKey): SummaryApplier = analyzer(caller)
    override fun subscribe(sub: Subscription) = subscriptions.subscribe(sub)
    override fun summaryStorage(key: MethodKey) = run.summaryStorage(key)
    override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts) {
        if (shared.unitResolver.resolve(callee.method) == unit) analyzer(callee).addLink(ref, linkLayer, added)
        else send(RunEvent.LinkIn(callee, ref, linkLayer, added))
    }
    fun close() = channel.cancel()
    fun analyzers(): List<RunMethodAnalyzer> = analyzers.values.toList()
}
```

### 3.4 `MethodContextCache` (`analyzer-core.md` §4.8)

The existing interpreter cache owns forms and reversals. Call and entry forms are keyed by method context;
shared method graphs and exit forms remain in the method-entry cache. The run context selects the graph and forms
for its direction. A forward exceptional exit runs its rules but emits no summary.

```kotlin
interface MethodContextSource {
    fun forms(key: MethodKey): MethodForms
    fun graph(key: MethodKey, direction: Direction): MethodInstGraph
}

class MethodContextCache(val interpreter: Interpreter, private val source: MethodContextSource) {
    fun forms(key: MethodKey): MethodForms = source.forms(key)
    fun graph(key: MethodKey, direction: Direction): MethodInstGraph = source.graph(key, direction)

    fun directed(direction: Direction): DirectedForms = DirectedForms(interpreter, direction, ::forms)
}

class RunMethodContext(val key: MethodKey, val forms: DirectedForms, val config: RunConfig, val shared: SharedObjects) {

    private val graph: MethodInstGraph = shared.contexts.graph(key, config.direction)
    private val ends: Set<CommonInst> = forms.endNodes(key).toHashSet()

    private val exceptionalExits: Set<CommonInst> =
        if (config.direction != Direction.FORWARD) emptySet()
        else shared.interpreter.exitNodes(key).filter { it.exceptional }.mapTo(HashSet()) { it.node }
    val mode: ApMode get() = config.mode
    val ops: ApOps get() = shared.ops
    val ap: ApManager get() = shared.ap
    val vulnerabilities: VulnerabilityStore get() = shared.vulnerabilities
    fun isEnd(n: CommonInst): Boolean = n in ends
    fun isExceptionalExit(n: CommonInst): Boolean = n in exceptionalExits
    fun forEachSuccessor(n: CommonInst, body: (CommonInst) -> Unit) { graph.forEachSuccessor(shared.language, n, body) }
    fun callAt(n: CommonInst): CommonCallExpr? = shared.language.getCallExpr(n)
}
```

```kotlin
package org.opentaint.dataflow.jvm.bidi

class JIRMethodContextCache(private val interp: JIRInterpreter, private val entries: JIRMethodEntries) : MethodContextSource {
    override fun forms(key: MethodKey): MethodForms = entries.forms(interp, key)
    override fun graph(key: MethodKey, direction: Direction): MethodInstGraph = entries[key].graph(direction)
}
```

### 3.5 One call, in sequence

For a call across two units, the required sequence is:

```
Caller: bind in → sinks at BOUND → Actions → added facts
        register subscription → insert its delta → replay current publications and records
        send LinkIn(callee, caller reference, link layer, added delta)
Callee: insert exact link delta → emit initials → process method
        end rules → raw summary delta → reduced publication
        insert publication under lock → send Delivery
Caller: read current subscriptions → the same matches test as replay
        apply summary or full E6 combination
        Actions → bind back → aliases → CALL cut → successor work
```

A replay and delivery can apply the same publication. Exact stores and monotone joins absorb duplicates.
Registration before replay and insertion before notification ensure that no application is lost.

## 4. `RunMethodAnalyzer` (`analyzer-core.md` §4)

### 4.1 State (`analyzer-core.md` §4.1)

Keep raw summaries for R1 persistence and selected publications for hand-off as separate stores.
A premise key includes each member's tail, mark and exclusion; distinct must exclusions are distinct keys.
An ND edge has TAINT conclusions. Backward analysis has no conjunction or must initial fact.

Count every new demand-layer edge delta, summary delta and added-link delta. A demand link can be the only reason
that a normal sink witness lacks support, so it must prevent `NO_DEMAND_EDGE` from stopping the analysis.
`recordCrossings` counts record applications with a result. Optional cause counters do not affect behavior.

The unchanged queue runs before stored deltas and uses structural equality. Its repeat set survives Work-event
boundaries and resets only when the unchanged queue has drained. The normal queue gives zero work priority.

```kotlin
private typealias ResultSink = (PremiseKey, Facts) -> Unit

class RunMethodAnalyzer(val key: MethodKey, port: RunnerPort) : SummaryApplier {
    private var port: RunnerPort? = port
    private var rctx: RunMethodContext? = RunMethodContext(key, port.forms, port.config, port.shared)
    private val p: RunnerPort get() = port!!
    private val r: RunMethodContext get() = rctx!!
    private val config: RunConfig get() = r.config
    private val forms: DirectedForms get() = r.forms
    private val ops = port.shared.ops
    private val ap = port.shared.ap
    private val mode = port.config.mode
    private val limit = port.config.fieldLimit
    private val forward = port.config.direction == Direction.FORWARD

    private var edgeStore: MethodEdgeStore? = MethodEdgeStore(ap, key, port.shared.language, limit)
    private var initialStore: InitialFactStore? = InitialFactStore()
    private var linkStore: AddedFactStore? = AddedFactStore(ap)
    val edges: MethodEdgeStore get() = edgeStore!!
    val initials: InitialFactStore get() = initialStore!!
    val links: AddedFactStore get() = linkStore!!
    val summaries = RunSummaryStore(ap)
    val sourceHits: SourceHitStore? = if (forward) null else SourceHitStore()
    private var requests: RequestStore? = if (config.run1) RequestStore() else null
    private var conjunctions: ConjunctionStore? = ConjunctionStore(ap)
    private val triggered = HashSet<Pair<CommonInst, SinkRule>>()
    private val applier = FormApplier(EngineAlgebra())

    private var worklist = DeltaWorklist()
    private var pending = ArrayList<Publication>()
    var queued = false; private set
    var steps = 0L; private set
    val hasZeroWork: Boolean get() = worklist.hasZeroWork

    var edgeDeltas = 0L; private set
    var demandLayerEdges = 0L; private set
    var nonZeroInitial = false; private set
    var recordCrossings = 0L; private set
    var triggerSeeds = 0L; private set
    val demandByCause: LongArray? =
        if (port.shared.diagnostics) LongArray(DemandCause.entries.size) else null
```

```kotlin
data class EdgeDelta(val premise: PremiseKey, val node: CommonInst, val facts: Facts) {
    val zeroToZero: Boolean get() = premise.isZero && facts is Reach
}

class DeltaWorklist {
    private val unchanged = ArrayList<EdgeDelta>()
    private var seen = ObjectOpenHashSet<EdgeDelta>()
    private var zeroUnchanged = 0
    private val zero = ArrayList<EdgeDelta>()
    private val other = ArrayList<EdgeDelta>()

    val isEmpty: Boolean get() = unchanged.isEmpty() && zero.isEmpty() && other.isEmpty()

    val hasZeroWork: Boolean get() = zero.isNotEmpty() || zeroUnchanged > 0
    val size: Int get() = unchanged.size + zero.size + other.size

    fun add(d: EdgeDelta) { if (d.zeroToZero) zero += d else other += d }

    fun addUnchanged(d: EdgeDelta): Boolean {
        if (!seen.add(d)) return false
        unchanged += d
        if (d.zeroToZero) zeroUnchanged++
        return true
    }

    fun removeNext(): EdgeDelta {
        unchanged.removeLastOrNull()?.let { if (it.zeroToZero) zeroUnchanged--; return it }
        if (seen.isNotEmpty()) seen = ObjectOpenHashSet()
        return zero.removeLastOrNull() ?: other.removeLast()
    }
}
```

### 4.2 Handlers (`analyzer-core.md` §4.2)

Insert links, initials and requests with exact deduplication. In run 1, policy emission and the standing request
join can add initials. Restricted emission queries comparable demand entry paths and applies the full `emit`:
abstract patterns share a FLOW premise; concrete patterns reject abstract added marks and emit the intersection.
No restricted operation creates a request.

```kotlin
    fun addRootZero() = addInitial(ap.zero)

    fun addZeroEntry() { check(!forward); addInitial(ap.zero) }

    fun addLink(ref: CallerRef, linkLayer: Layer, added: Facts) {
        val delta = links.add(ref, linkLayer, added) ?: return
        if (delta.layer == Layer.DEMAND) demandLayerEdges++
        emit(delta)
        val join = requestJoin ?: return
        for (leaf in ops.leaves(delta)) join.newB(Link(leaf, linkLayer, ref))
    }

    fun addRequest(premise: InitialAp, kind: RequestKind) {
        val join = checkNotNull(requestJoin) { "a request in a restricted run (ap.md §6.1 rule 4)" }
        if (requests!!.add(premise, kind)) join.newA(premise to kind)
    }

    private fun emit(delta: Facts) {
        if (config.run1) { addInitial(ops.policy(delta)); return }
        if (delta is Reach) { addInitial(ap.zero); return }
        val demand = config.demand!!
        val found = LinkedHashSet<DemandPattern>()
        for (leaf in ops.leaves(delta)) found += demand.near(key, leaf.fact.base, ap.path(leaf.fact.path))
        for (d in found) ops.emit(d, delta).forEach(::addInitial)
    }

    private fun addInitial(j: InitialAp) {
        check(!config.run1 || j.tail != Tail.ANY_TAINT) { "run 1 has no must-premise (ap.md W8 (c))" }
        check(forward || j.tail != Tail.ANY_TAINT) { "the backward run has no `[any-taint]` (ap.md W8 (d))" }
        if (!initials.add(j)) return
        if (!j.isZero) nonZeroInitial = true
        val start = ops.startFact(j)
        for (n in forms.startNodes(key, zero = j.isZero)) startAt(j, n, start)
    }
```

### 4.3 The application modes, the field limit, the step (`analyzer-core.md` §4.3)

`FormApplier` owns statement, stage and GEN application modes. `EngineAlgebra` supplies AP operations and
source hooks. The forward `MicroEdge` identifies unconditional sources in both directions. Its `may` flag forces
every result into DEMAND for a may target or a reversed conjunction branch.

Forward conjunctions store each effective literal contribution. All normal contributions give NORMAL; one demand
contribution gives DEMAND. Both literals remain required even if they have the same caller premise. Union drops
zero except when all inputs are zero. A normal multi-premise result stays normal when selected for hand-off.

The AP owns cleaners, mark exclusions, tail/layer normalization and their demotions. A selected-mark cleaner on
an abstract same-base fact excludes T before testing paths; in run 1 it also requests T, including a mark already
excluded. Requests always name the incoming FLOW premise.

Run the field cut after result filters at each spec point:

| Result | Cut site |
|---|---|
| Statement, read source and lowered field action read/write | `STATEMENT` |
| All call paths after Actions, binding back and aliases; E6 result | `CALL` |
| Entry rules forward; reversed entry rules backward | `ENTRY_RULES` |
| Exit rules forward; reversed exit rules backward | `EXIT_RULES` |
| Hand-off or end-fact trigger requirement | `SEED` |

An unchanged fact takes no postprocessor. No core liveness test drops a fact.

```kotlin
    private inner class EngineAlgebra : FactAlgebra<PremiseKey, Facts> {
        override fun base(c: Facts) = c.base
        override fun filter(c: Facts, filter: TypeFilter) = ops.filter(c, filter)
        override fun applyEdge(me: MicroEdge, c: Facts, premise: PremiseKey, statementEdge: Boolean, out: ResultSink) =
            ops.applyEdge(c, premise, me.edge, statementEdge, mode, collect(premise, c, out), may = me.may)
        override fun conjunction(cj: ConjunctiveEdge, premise: PremiseKey, c: Facts, node: CommonInst, out: ResultSink) =
            this@RunMethodAnalyzer.conjunction(cj, premise, c, node, out)
        override fun allowsSource(node: CommonInst, me: MicroEdge) =
            !config.seededSources || config.seeds.allowsSource(key, node, me.forward)
        override fun sourceHit(node: CommonInst, me: MicroEdge) { sourceHits?.add(key, node, me.forward) }
    }

    private fun collect(premise: PremiseKey, c: Facts, sink: ResultSink): ApOut = object : ApOut {
        override fun result(f: Facts) = sink(premise, f)
        override fun markRequest(mark: TaintMark) = raise(premise, c, RequestKind.Mark(mark))
        override fun positionRequest(position: PathNode) = raise(premise, c, RequestKind.Position(position))
    }

    private fun raise(premise: PremiseKey, input: Facts, kind: RequestKind) {
        check(config.run1) { "requests exist only in run 1" }
        check(input is FlowTree && premise is InitialAp) { "a request needs a FLOW fact (ap.md §7.2)" }
        addRequest(premise, kind)
    }

    private fun cut(point: Cut, premise: PremiseKey, f: Facts, sink: ResultSink) {
        p.onCut?.invoke(point, f)
        ops.limit(f, limit, collect(premise, f, sink))
    }
```

```kotlin
enum class Cut { STATEMENT, CALL, ENTRY_RULES, EXIT_RULES, SEED }
```

```kotlin
    private fun conjunction(cj: ConjunctiveEdge, premise: PremiseKey, c: Facts, node: CommonInst, sink: ResultSink) {
        val store = conjunctions!!
        for ((k, lit) in cj.literals.withIndex()) {
            if (lit.fact.base != c.base) continue
            val holds = when (val m = ops.checkMark(c, lit, mode)) {
                MarkCheck.None -> continue
                is MarkCheck.Request -> { raise(premise, c, RequestKind.Mark(m.mark)); continue }
                is MarkCheck.Holds -> m
            }
            for (comb in store.add(cj, node, cj.literals.size, k,
                ConjunctionStore.Input(premise, holds.facts.layer)))
                sink(comb.premise, ops.targetTree(cj.target, comb.layer))
        }
    }
```

```kotlin
    fun work(quantum: Int): Boolean {
        val zeroAtStart = worklist.hasZeroWork
        var n = 0
        while (!worklist.isEmpty) {
            if (n++ >= quantum || worklist.hasZeroWork != zeroAtStart) return false
            process(worklist.removeNext())
        }
        flushPublications()
        queued = false
        return true
    }

    private fun process(item: EdgeDelta) {
        steps++
        p.onProcess?.invoke(key, item)
        if (r.callAt(item.node) != null) runCall(item) else runStatement(item)
    }

    private fun runStatement(item: EdgeDelta) {
        applier.statement(forms.statement(key, item.node), item.premise, item.facts, Place(item.node, config.run1, sources = true),
            sink = { pr, x -> cut(Cut.STATEMENT, pr, x) { p2, t -> emitAfter(p2, item.node, t) } },
            untouched = { _, _ -> emitUnchanged(item) })
    }

    private fun emitAfter(premise: PremiseKey, node: CommonInst, t: Facts) {
        exitRulesAt(premise, node, t)
        r.forEachSuccessor(node) { s -> edges.add(s, premise, t)?.let { push(EdgeDelta(premise, s, it)) } }
    }

    private fun emitUnchanged(item: EdgeDelta) {
        exitRulesAt(item.premise, item.node, item.facts)
        r.forEachSuccessor(item.node) { s -> if (worklist.addUnchanged(EdgeDelta(item.premise, s, item.facts))) requestWork() }
    }

    private fun exitRulesAt(premise: PremiseKey, node: CommonInst, t: Facts) {
        if (r.isEnd(node)) endAt(premise, node, t, summary = true)
        else if (r.isExceptionalExit(node)) endAt(premise, node, t, summary = false)
    }

    private fun push(d: EdgeDelta) {
        checkKinds(d.premise, d.facts)
        edgeDeltas++
        if (d.facts.layer == Layer.DEMAND) demandLayerEdges++
        worklist.add(d); requestWork()
    }

    private fun checkKinds(premise: PremiseKey, f: Facts) {
        check(f !is FlowTree || (premise is InitialAp && premise.mark is MarkSlot.Star))
        check(premise !is PremiseSet || f is TaintTree)
    }
    private fun requestWork() { if (!queued) { queued = true; p.enqueue(this) } }
```

### 4.4 The rule order of a boundary; start nodes and start rules (`analyzer-core.md` §4.4)

Use one boundary worklist. Apply the rule statement, then sinks and triggered end facts; newly generated facts
re-enter the worklist. Backward, a reversed end fact that applies also fires its alternative's trigger seeds.
Deduplicate `(premise, Facts)` structurally, and fire trigger seeds once per statement and alternative.

Forward starts use entry rules. Backward zero starts use both normal and exceptional exits; nonzero starts use
normal exits only. An exit sink seed enters before the reversed rules of that exit.

```kotlin
    private inner class RuleWorklist(private val rules: RuleStatement, private val node: CommonInst, private val point: Cut) {
        val items = ArrayList<Pair<PremiseKey, Facts>>()
        private val seen = HashSet<Pair<PremiseKey, Facts>>()
        private val add: ResultSink = { pr, x -> cut(point, pr, x) { p2, t -> if (seen.add(p2 to t)) items += p2 to t } }

        private val reversedEnds: List<Pair<SinkRule, List<MicroEdge>>> = if (forward) emptyList() else
            rules.sinks.mapNotNull { s -> rules.endFacts.edges.filter { me -> s.endFacts.any { it.forward == me.forward } }
                .takeIf { it.isNotEmpty() }?.let { s to it } }

        fun input(premise: PremiseKey, f: Facts) =
            applier.statement(rules.summary, premise, f, Place(node, config.run1, sources = true), sink = add)

        fun drain(after: (PremiseKey, Facts, Triggers) -> Unit = { _, _, _ -> }) {
            var k = 0
            while (k < items.size) {
                val (pr, t) = items[k++]
                val tr = checkSinks(pr, node, t, rules.sinks)
                for ((sink, layer) in tr.fired) applier.gen(sink.endFacts, ap.zero, Reach.of(layer), add)
                for ((s, rev) in reversedEnds) {
                    var applied = false
                    applier.gen(rev, pr, t) { p2, x -> applied = true; add(p2, x) }
                    if (applied) fireTriggerSeeds(node, s) { seed -> input(ap.zero, seed) }
                }
                after(pr, t, tr)
            }
        }
    }

    private fun startAt(premise: PremiseKey, n: CommonInst, start: Facts) {
        val w = RuleWorklist(forms.startRules(key, n), n, if (forward) Cut.ENTRY_RULES else Cut.EXIT_RULES)
        w.input(premise, start)
        if (!forward && premise.isZero) fireSinkSeeds(n) { seed -> w.input(ap.zero, seed) }
        w.drain()
        for ((pr, t) in w.items) edges.add(n, pr, t)?.let { push(EdgeDelta(pr, n, it)) }
    }
```

### 4.5 The call plan runner (`analyzer-core.md` §4.5)

Walk the directed call-plan DAG in either direction. At BOUND, forward sinks supply the guards of end-fact
stages. `Actions(from, to, steps)` applies its ordered cleaners and rewriters. A field action performs ordinary
read, clean and write statements, with their cuts, then removes its temporary.

`identityCandidate` is nullable before classification. Sources and end facts set false; default identity and
constructor pass-over set true; nonidentity pass results set false. Bindings and actions preserve it. Forward
aliases admit every current DEMAND result and every result with a false candidate flag. Reversal removes guards
and filters, so backward transfer does not consult the flag.

Subscribe and link each resolved callee. Later summary/record results resume at that stage's end point. A reversed
END_FACTS stage carries its sink alternative as `trigger` and fires its seeds when it produces a result.

```kotlin
    private class PlanFact(val premise: PremiseKey, val facts: Facts, val identityCandidate: Boolean?)

    private class CallCtx(val node: CommonInst, val call: CommonCallExpr, val plan: CallPlan, val callerLayer: Layer)

    private val CallPlan.callees: CallStage.Callees? get() = stages.firstNotNullOfOrNull { it as? CallStage.Callees }

    private fun runCall(item: EdgeDelta) {
        val call = r.callAt(item.node)!!
        val ctx = CallCtx(item.node, call, forms.call(key, item.node, call), item.facts.layer)
        val c = item.facts
        if (c is Reach) { zeroAtCall(item, ctx); return }
        if (c.base !in ctx.plan.touched) { emitUnchanged(item); return }
        flow(ctx, ctx.plan.entry, listOf(PlanFact(item.premise, c, null)))
    }

    private fun flow(ctx: CallCtx, point: CallPoint, facts: List<PlanFact>) {
        if (point == ctx.plan.exit) {
            for (f in facts) cut(Cut.CALL, f.premise, f.facts) { pr, t -> emitAfter(pr, ctx.node, t) }
            return
        }
        val triggers = if (forward && point == CallPoint.BOUND)
            facts.fold(Triggers.NONE) { acc, f -> acc + checkSinks(f.premise, ctx.node, f.facts, ctx.plan.sinks) }
        else Triggers.NONE
        if (config.run1 && point == CallPoint.ADDED) observeUnresolved(ctx, facts)
        for (stage in ctx.plan.stagesFrom[point].orEmpty()) {
            val out = applyStage(ctx, stage, facts, triggers)
            if (out.isNotEmpty()) flow(ctx, stage.to, out)
        }
    }

    private fun applyStage(ctx: CallCtx, stage: CallStage, facts: List<PlanFact>, triggers: Triggers): List<PlanFact> {
        val out = ArrayList<PlanFact>()
        when (stage) {
            is CallStage.Edges -> {
                when (val guard = stage.guard) {
                    is Guard.SinkTriggered ->
                        for ((sink, layer) in triggers.fired) if (sink === guard.sink) {
                            val at = Place(ctx.node, statementEdge = false, sources = false)
                            applier.stage(stage.summary, ap.zero, Reach.of(layer), at) { pr, x, me -> out += PlanFact(pr, x, stage.kind.identityCandidateOf(me, null)) }
                        }
                    else -> for (f in facts) {
                        if (guard == Guard.MemoryEffect && !Guard.MemoryEffect.admits(checkNotNull(f.identityCandidate), f.facts.layer)) continue
                        val at = Place(ctx.node,
                            statementEdge = config.run1 && stage.kind.statementEdges,
                            sources = stage.kind == StageKind.SOURCES)
                        applier.stage(stage.summary, f.premise, f.facts, at) { pr, x, me -> out += PlanFact(pr, x, stage.kind.identityCandidateOf(me, f.identityCandidate)) }
                    }
                }
                val s = stage.trigger
                if (s != null && out.isNotEmpty()) fireTriggerSeeds(ctx.node, s) { t -> out += PlanFact(ap.zero, t, null) }
            }
            is CallStage.Actions -> for (f in facts) cleanChain(stage.steps, f, ctx.node) { out += it }
            is CallStage.Callees -> for (f in facts) enterCallees(ctx, stage, f)
        }
        return out
    }

    private fun cleanChain(steps: List<CleanStep>, f: PlanFact, node: CommonInst, emit: (PlanFact) -> Unit) {
        fun statement(s: StatementSummary, x: PlanFact): List<PlanFact> = buildList {
            applier.statement(s, x.premise, x.facts, Place(node, config.run1, sources = false),
                sink = { pr, t -> cut(Cut.STATEMENT, pr, t) { p2, y -> add(PlanFact(p2, y, x.identityCandidate)) } },
                untouched = { pr, t -> add(PlanFact(pr, t, x.identityCandidate)) })
        }
        var cur = listOf(f)
        for (step in steps) {
            val next = ArrayList<PlanFact>()
            for (x in cur) when (step) {
                is CleanStep.Clean -> ops.clean(x.facts, x.premise, step.cleaner, mode, collect(x.premise, x.facts) { pr, t -> next += PlanFact(pr, t, x.identityCandidate) })
                is CleanStep.Kill -> applier.statement(step.keepEdges, x.premise, x.facts,
                    Place(node, config.run1, sources = false),
                    sink = { pr, t -> next += PlanFact(pr, t, x.identityCandidate) })
                is CleanStep.Field -> for (a in statement(step.read, x))
                    ops.clean(a.facts, a.premise, step.cleaner, mode, collect(a.premise, a.facts) { pr, t ->
                        next += statement(step.write, PlanFact(pr, t, a.identityCandidate)).filter { it.facts.base != step.temporary }
                    })
            }
            cur = next
        }
        cur.forEach(emit)
    }

    private fun observeUnresolved(ctx: CallCtx, facts: List<PlanFact>) {
        val observer = r.shared.unresolvedObserver ?: return
        if (ctx.plan.stagesFrom[CallPoint.ADDED].orEmpty().none { it is CallStage.Edges && it.kind == StageKind.UNRESOLVED }) return
        for (f in facts) if (f.facts !is Reach) observer.reached(ctx.call, f.facts.base, ctx.plan)
    }

    private fun enterCallees(ctx: CallCtx, stage: CallStage.Callees, a: PlanFact) {
        val ref = CallerRef(key, a.premise, ctx.callerLayer, ctx.node)
        for (m in stage.callees) {
            p.subscribe(Subscription(m, ref, a.facts.layer, a.facts))
            p.link(m, ref, a.facts.layer, a.facts)
        }
    }
```

### 4.6 The results of the callees stage (`analyzer-core.md` §4.2 `applySummary`, `applyRecord`)

One callee result adapter handles summaries, records, transient record views and E6 combinations. `matches`
already selected satisfying added leaves. The backward zero-only return copies the published conclusion directly.
A FLOW added fact cannot satisfy a concrete summary premise and does not raise a caller-side request.

For aliases, split native NORMAL singleton summary deltas into their exact identity leaf and effects. Equality
includes the complete group key and exclusions. Zero-premise and demand results have a false candidate flag.
Use `recordLeafIdentity` for a transient view; do not call `startFact` on its premise.

Native same-direction must records divide added leaves into inside and applicable-only parts. The latter results
are DEMAND with must exclusions removed. Opposite-direction views are supplied by `reverseRecordLeaf`: a must
conclusion becomes a concrete STAR/E premise; a must forward premise becomes a may ANY demand result. Keep
all eligibility, mark and exclusion guards in the AP reader.

The native must-record code below still raises the applicable-only result before later call actions. This timing
is under review against `ap.md` §4.3; it must not be accepted merely because its intermediate results are a superset.
The final implementation must use the timing selected by that review.

```kotlin
    override fun applySummary(part: Subscription, pub: Publication, member: Int) {
        val j = when (val pk = pub.premise) {
            is PremiseSet -> { ndMatch(part, pub, pk, member); return }
            is InitialAp -> pk
        }
        fromCallees(part.ref) { results ->
            if (part.zeroOnly) results += PlanFact(part.ref.premise, pub.conclusion, false)
            else applyParts(part, j, pub.conclusion, results)
        }
    }

    override fun applyRecord(part: Subscription, record: Record) = fromCallees(part.ref) { results ->
        val j = record.premise
        if (j.tail != Tail.ANY_TAINT) applyParts(part, j, record.conclusion, results)
        else {
            check(forward) { "a must record is only forward (ap.md §8.7 R3)" }
            val inside = ops.satisfying(part.added, j, mode)
            inside?.let { applyParts(part.copy(added = it), j, record.conclusion, results) }
            val only = if (inside == null) part.added else ops.without(part.added, inside)
            if (only != null) {
                val demoted = ArrayList<PlanFact>()
                applyParts(part.copy(added = only), j, record.conclusion, demoted)
                for (x in demoted) results += PlanFact(x.premise, toDemand(x.facts), false)
            }
        }
        if (results.isNotEmpty()) recordCrossings++
    }

    override fun applyRecordView(part: Subscription, view: RecordLeafView) = fromCallees(part.ref) { results ->
        val identityCandidate = recordLeafIdentity(view)
        ops.applyRecordLeaf(part.added, view, mode, collect(part.ref.premise, part.added) { pr, t ->
            results += PlanFact(pr, t, identityCandidate)
        })
        if (results.isNotEmpty()) recordCrossings++
    }

    private fun toDemand(f: Facts): Facts = when (f) {
        is TaintTree -> if (f.layer == Layer.DEMAND) f else ap.taintTree(f.base, Layer.DEMAND, f.root)
        is Reach -> Reach.of(Layer.DEMAND)
        is FlowTree -> error("a must record has TAINT results (ap.md §7.2)")
    }

    private inline fun fromCallees(ref: CallerRef, fill: (MutableList<PlanFact>) -> Unit) {
        val ctx = ctxOf(ref)
        val results = ArrayList<PlanFact>()
        fill(results)
        flow(ctx, ctx.plan.callees!!.to, results)
    }

    private fun applyParts(part: Subscription, j: InitialAp, g: Facts, results: MutableList<PlanFact>) {
        for ((gp, identityCandidate) in summaryParts(j, g))
            ops.applySummary(part.added, j, gp, mode, collect(part.ref.premise, part.added) { pr, t -> results += PlanFact(pr, t, identityCandidate) })
    }

    private fun summaryParts(j: InitialAp, g: Facts): List<Pair<Facts, Boolean>> {
        val id = ops.startFact(j)
        if (j.isZero || g.layer != Layer.NORMAL || g.groupKey != id.groupKey) return listOf(g to false)
        val effect = ops.without(g, id)
        val identity = if (effect == null) g else ops.without(g, effect)
        return listOfNotNull(effect?.let { it to false }, identity?.let { it to true })
    }

    private fun ctxOf(ref: CallerRef): CallCtx {
        val call = r.callAt(ref.call)!!
        return CallCtx(ref.call, call, forms.call(key, ref.call, call), ref.callerLayer)
    }
```

### 4.7 End rules, summary edges, publications (`analyzer-core.md` §4.6)

At a normal end: apply rule statements, sinks and end facts; remove evaluated zero-premise global state;
remove all entry-mark leaves on `this`/arguments for zero-premise results; emit summary deltas. At an exceptional
forward exit, stop after sinks and end facts: no removal and no summary.

For every raw delta, store it before reduction. Select callee demand from the raw header only: demand layer,
multiple premises, or backward zero premise. Publish run-1 and backward zero summaries unchanged. Reduce other
publications against each member's demand patterns, using full mark/location intersection. Reuse the selected
publications as stored demand edges, preserving their layer. Forward normal singletons and backward normal
nonzero singletons give no callee demand, including must records whose views later produce DEMAND in the caller.

```kotlin
    private fun endAt(premise: PremiseKey, node: CommonInst, f: Facts, summary: Boolean) {
        val er = forms.endRules(key, node)
        val w = RuleWorklist(er.rules, node, if (forward) Cut.EXIT_RULES else Cut.ENTRY_RULES)
        w.input(premise, f)
        w.drain { pr, t, tr ->
            if (!summary) return@drain
            var g: Facts? = t
            if (er.globalStateDrop && pr.isZero && t.base == AccessPathBase.ClassStatic)
                for (part in tr.parts) g = g?.let { ops.without(it, part) }
            if (pr.isZero && t is TaintTree)
                er.entryMarkRemoval(t.base)?.let { marks ->
                    g = g?.let { ops.withoutMarks(it, marks) } }
            g?.let { summaryDelta(pr, it) }
        }
    }

    private fun summaryDelta(premise: PremiseKey, g: Facts) {
        checkKinds(premise, g)
        if (!r.shared.interpreter.isSummaryBase(g.base)) return
        val delta = summaries.add(premise, g) ?: return
        edgeDeltas++
        if (delta.layer == Layer.DEMAND) demandLayerEdges++
        val part = ops.demandPart(premise, delta, config.direction)
        val pubs = published(premise, delta)
        for (x in pubs) pending += Publication(premise, x)
        if (part != null) for (x in pubs) summaries.addDemand(premise, x)
        if (pubs.isNotEmpty()) requestWork()
    }

    private fun published(premise: PremiseKey, g: Facts): Collection<Facts> = when {
        config.run1 -> listOf(g)
        !forward && premise.isZero -> listOf(g)
        else -> LinkedHashSet<Facts>().also { out -> premise.forEachMember { j -> out += restrictBy(ops, config.demand!!, key, j, g) } }
    }

    private fun flushPublications() {
        if (pending.isEmpty()) return
        p.summaryStorage(key).publish(pending)
        pending = ArrayList()
    }
```

```kotlin
fun restrictBy(ops: ApOps, demand: DemandStore, key: MethodKey, j: InitialAp, g: Facts): Sequence<Facts> =
    demand.covering(key, j.base, j.path).asSequence().flatMap { d -> ops.restrict(j, g, d) }
```

### 4.8 Requests (`analyzer-core.md` §4.6; `ap.md` §4.5, §4.10)

Requests and links form a standing join in run 1. Either arrival order executes every matching pair. The stores
deduplicate the sides and their indexes return all candidates. `requestAction` decides answer, climb or no action.
Freeze drops the join and request store.

```kotlin
    private var requestJoin: StandingJoin<Pair<InitialAp, RequestKind>, Link>? = if (!config.run1) null else StandingJoin(
        nearB = { (i, kind) -> when (kind) {
            is RequestKind.Mark -> links.overlapping(i.base, i.path)
            is RequestKind.Position -> links.overlapping(AccessPathBase.ClassStatic, kind.path)
        } },
        nearA = { link -> requests!!.overlapping(link.addedFact) },
        meet = { (i, kind), link -> act(i, kind, link.addedFact, link.caller) })

    private fun act(i: InitialAp, kind: RequestKind, a: Pattern, caller: CallerRef) =
        when (val x = ops.requestAction(i, kind, a, caller)) {
            is RequestAction.Answer -> addInitial(x.initial)
            is RequestAction.Climb -> p.send(RunEvent.RequestIn(caller.caller, x.premise, x.request))
            RequestAction.None -> Unit
        }
```

### 4.9 Sinks, witnesses, seeds, source hits (`analyzer-core.md` §4.7)

Forward sinks use `checkMark`. Store every evaluated literal part, including an incomplete conjunctive sink's
inputs. At the normal exit only zero-premise evaluated statics are removed; caller-set statics remain.
A conjunctive witness preserves its separate input premise sets and effective layers.

The vulnerability key has no context, but each witness keeps its method key and alternative. Merge only the
lossless witness shapes defined by the AP store. Confirmation uses that witness's method key (§7.5).

Backward has no sink check. Zero reachability fires hand-off sink seeds. Any applied reversed end-fact edge fires
its alternative's seeds once, even when the triggering vulnerability was confirmed. End facts are not source
instructions. Record a source hit whenever its reversed unconditional source produces a result, before store
deduplication; replayed records and views do not create synthetic hits.

```kotlin
    private class Triggers(val fired: List<Pair<SinkRule, Layer>>, val parts: List<Facts>) {
        operator fun plus(o: Triggers) = Triggers((fired + o.fired).distinct(), parts + o.parts)
        companion object { val NONE = Triggers(emptyList(), emptyList()) }
    }

    private fun checkSinks(premise: PremiseKey, node: CommonInst, c: Facts, sinks: List<SinkRule>): Triggers {
        if (!forward || sinks.isEmpty()) return Triggers.NONE
        val fired = ArrayList<Pair<SinkRule, Layer>>()
        val parts = ArrayList<Facts>()
        for (s in sinks) for ((k, lit) in s.patterns.withIndex()) when (val m = ops.checkMark(c, lit, mode)) {
            MarkCheck.None -> Unit
            is MarkCheck.Request -> raise(premise, c, RequestKind.Mark(m.mark))
            is MarkCheck.Holds -> {
                parts += m.facts
                if (!s.conjunctive) {
                    witness(s, node, listOf(SinkEdge(premise, m.facts.layer, m.facts)))
                    fired += s to m.facts.layer
                } else for (comb in conjunctions!!.add(s, node, s.patterns.size, k,
                        ConjunctionStore.Input(premise, m.facts.layer, m.facts))) {
                    witness(s, node, comb.inputs.map { SinkEdge(it.premise, it.layer, it.facts!!) })
                    fired += s to comb.layer
                }
            }
        }
        return Triggers(fired.distinct(), parts)
    }

    private fun witness(s: SinkRule, node: CommonInst, edges: List<SinkEdge>) =
        r.vulnerabilities.add(VulnerabilityKey(s.rule, key.method, node),
            SinkWitness(s.alternative, key, edges, config.index, s.endFacts.map { it.edge.to }))

    private fun fireSinkSeeds(node: CommonInst, emit: (Facts) -> Unit) {
        for (seed in config.seeds.at(key, node)) if (seed is Seed.Sink) seedFact(seed.requirement, emit)
    }

    private fun fireTriggerSeeds(node: CommonInst, s: SinkRule, emit: (Facts) -> Unit) {
        if (!triggered.add(node to s)) return
        for (lit in s.seedPatterns()) { triggerSeeds++; seedFact(lit, emit) }
    }

    private fun seedFact(p: Pattern, emit: (Facts) -> Unit) =
        cut(Cut.SEED, ap.zero, ops.targetTree(p.fact, Layer.NORMAL)) { _, t -> emit(t) }
```

### 4.10 The zero fact at a call (`analyzer-core.md` §4.5; rules `zpass`, `zin`, `zret`)

Every REACH edge passes the call. Only `{zero} → zero` runs other zero rules. Forward, it enters the plan to
trigger sources and sinks. Backward, it injects sink seeds at BOUND, subscribes for unrestricted zero returns
(`zret`) and sends `ZeroIn` to every resolved callee (`zin`). `{jb} → zero` runs none of these extra actions.

```kotlin
    private fun zeroAtCall(item: EdgeDelta, ctx: CallCtx) {
        emitUnchanged(item)
        if (!item.premise.isZero) { check(!forward); return }
        if (forward) {
            flow(ctx, ctx.plan.entry, listOf(PlanFact(item.premise, item.facts, null)))
            return
        }
        val seeds = ArrayList<PlanFact>()
        fireSinkSeeds(item.node) { t -> seeds += PlanFact(ap.zero, t, null) }
        if (seeds.isNotEmpty()) flow(ctx, CallPoint.BOUND, seeds)
        val ref = CallerRef(key, ap.zero, ctx.callerLayer, item.node)
        for (m in ctx.plan.callees?.callees.orEmpty()) {
            p.subscribe(Subscription(m, ref, ctx.callerLayer, item.facts, zeroOnly = true))
            p.send(RunEvent.ZeroIn(m))
        }
    }
```

### 4.11 Summaries with several premises (E6, `analyzer-core.md` §5.4)

Join E6 only forward, at one caller call statement. Store one part under every premise member it satisfies.
A newly supplied part meets stored conclusions; a new conclusion meets every complete input combination.
Zero subscriptions cannot satisfy nonzero members. The full result uses the union of caller premises and joins
all effective layers; it is cut at the plan exit.

Freeze runs only after the join. Keep forward links until confirmation, raw summaries until persistence/hand-off
and backward hits until source-seed construction. Drop all other run stores.

```kotlin
    private fun ndMatch(part: Subscription, pub: Publication, premise: PremiseSet, m: Int) {
        check(!part.zeroOnly && part.added !is Reach && !premise.member(m).isZero)
        val conclusion = checkNotNull(pub.conclusion as? TaintTree) { "an ND edge is TAINT (ap.md §4.6)" }
        val join = conjunctions!!.ndJoin<Subscription>(ConjunctionStore.NdKey(part.callee, premise, pub.layer, part.ref.call))
        for ((combo, g) in join.addSubscription(m, part)) applyCombination(combo, premise, g)
        for ((combo, g) in join.addConclusion(conclusion)) applyCombination(combo, premise, g)
    }

    private fun applyCombination(parts: List<Subscription>, premise: PremiseSet, g: TaintTree) {
        val union = parts.map { it.ref.premise }.reduce(ap::union)
        fromCallees(parts[0].ref) { results ->
            ops.applyCombination(parts.mapIndexed { k, s -> s.added to premise.member(k) }, g, mode,
                collect(union, g) { pr, t -> results += PlanFact(pr, t, false) })
        }
    }

    fun freeze() {
        port = null; rctx = null; worklist = DeltaWorklist(); pending = ArrayList()
        requests = null; requestJoin = null; conjunctions = null; triggered.clear()
        edgeStore = null; initialStore = null; if (!forward) linkStore = null
    }
}
```

## 5. The pipeline (`analyzer-core.md` §5)

### 5.1 Events

Handlers run only on the destination runner. Duplicate events are valid; AP stores and joins deduplicate results.
`Work` is a counted local event. Deferred replay/notify steps are test hooks, not additional production events.

```kotlin
sealed interface RunEvent {
    data class Start(val root: MethodKey) : RunEvent
    data class LinkIn(val callee: MethodKey, val ref: CallerRef, val linkLayer: Layer, val added: Facts) : RunEvent
    data class ZeroIn(val callee: MethodKey) : RunEvent
    data class RequestIn(val method: MethodKey, val premise: InitialAp, val request: RequestKind) : RunEvent
    data class Delivery(val callee: MethodKey, val publications: List<Publication>) : RunEvent
    class Work(val analyzer: RunMethodAnalyzer) : RunEvent
}

internal fun RunnerPort.handle(event: RunEvent): Boolean {
    when (event) {
        is RunEvent.Start -> analyzer(event.root).addRootZero()
        is RunEvent.LinkIn -> analyzer(event.callee).addLink(event.ref, event.linkLayer, event.added)
        is RunEvent.ZeroIn -> analyzer(event.callee).addZeroEntry()
        is RunEvent.RequestIn -> analyzer(event.method).addRequest(event.premise, event.request)
        is RunEvent.Delivery -> subscriptions.onDelivery(event.callee, event.publications)
        is RunEvent.Work -> return event.analyzer.work(RUNNER_STEPS_QUANT)
    }
    return true
}

const val RUNNER_STEPS_QUANT = 1000
```

### 5.2 `SummaryStorage` and `PublicationIndex` (callee side)

A callee summary storage has one writer and potentially many replay readers. Protect insertion and snapshot
reads with one lock. Match outside the lock. Register subscribers before replay and publish deltas before notifying.
An immutable snapshot with a volatile write is an optional equivalent implementation of P3.

Index each premise member by base/path, retaining its full premise key. Replay returns merged conclusion groups
once per member; delivery sends newly inserted deltas. The index query is only a candidate scan. Run 1 scans
premise prefixes. Restricted scans premise extensions plus FLOW prefixes; the complete `matches` test rejects
marks, tails and exclusions that do not satisfy the premise.

```kotlin
data class Publication(val premise: PremiseKey, val conclusion: Facts) {
    val layer: Layer get() = conclusion.layer
}

class SummaryStorage(val method: MethodKey, ap: ApManager, ops: ApOps, private val steps: ProtocolSteps) {
    private val lock = Any()
    private val published = PublicationIndex(ap, ops)
    private val subscribers = ConcurrentLinkedQueue<SubscriptionManager>()

    fun publish(pubs: List<Publication>) {
        val delta = synchronized(lock) { published.addAll(pubs) }
        if (delta.isEmpty()) return
        steps.notify { for (s in subscribers) s.notify(method, delta) }
    }

    fun addSubscriber(s: SubscriptionManager) { subscribers.add(s) }

    fun candidates(part: Subscription, mode: ApMode): List<Pair<Publication, Int>> =
        synchronized(lock) { published.candidates(part, mode) }
}

class PublicationIndex(private val ap: ApManager, private val ops: ApOps) {
    private data class Member(val premise: PremiseKey, val index: Int)
    private val interners = ap.newInterners()
    private val merged = HashMap<PremiseKey, ConclusionGroup>()
    private val byMember = PathTrie<Member>()

    fun addAll(pubs: List<Publication>): List<Publication> {
        val out = ArrayList<Publication>()
        for (pub in pubs) {
            val group = merged.getOrPut(pub.premise) {
                for (m in 0 until pub.premise.size) pub.premise.member(m).let { j -> byMember.add(j.base, j.pathArray, Member(pub.premise, m)) }
                ConclusionGroup(ap, interners)
            }
            val d = group.add(pub.conclusion) ?: continue
            out += Publication(pub.premise, d)
        }
        return out
    }

    fun candidates(part: Subscription, mode: ApMode): List<Pair<Publication, Int>> {
        val members = LinkedHashSet<Member>()
        if (part.zeroOnly) byMember.lookupExtensions(AccessPathBase.Zero, IntArray(0)).filterTo(members) { it.premise.isZero }
        else for (leaf in ops.leaves(part.added)) {
            val path = leaf.fact.path.toIntArray()
            if (mode.run1) members += byMember.lookupPrefixes(leaf.fact.base, path)
            else {
                members += byMember.lookupExtensions(leaf.fact.base, path)
                byMember.lookupPrefixes(leaf.fact.base, path).filterTo(members) {
                    it.premise.member(it.index).mark is MarkSlot.Star
                }
            }
        }
        return members.flatMap { m -> merged.getValue(m.premise).all().map { Publication(m.premise, it) to m.index }.toList() }
    }
}
```

### 5.3 `SubscriptionManager` and `CalleeSubscriptions` (caller side)

The same `matches` function handles replay and delivery. In restricted mode canonical FLOW satisfaction is
location overlap; other FLOW shapes retain inside/applicable; concrete and must premises use inside. The AP
operation reads exclusions on both sides. Zero-only return bypasses this test only for a zero premise.

Register once per callee and subscribed runner, then insert an exact link delta and replay. Records are read-only
and need replay only. Native same-direction records and transient opposite-direction views match by inside or
applicable. Deduplicate repeated record/view candidates; do not create a new callee demand from the result layer.

Delivery reads subscriptions when the destination runner handles it. Its path query is the transpose of replay:
added extensions in run 1; added prefixes plus FLOW extensions when restricted. Both paths run `matches` on each
candidate group. All subscriptions and publications remain until the run ends.

```kotlin
data class Subscription(
    val callee: MethodKey,
    val ref: CallerRef,
    val linkLayer: Layer,
    val added: Facts,
    val zeroOnly: Boolean = false,
) {
    val caller: MethodKey get() = ref.caller
}

fun matches(sub: Subscription, pub: Publication, m: Int, ops: ApOps, mode: ApMode): Facts? =
    if (sub.zeroOnly) sub.added.takeIf { pub.premise.isZero }
    else ops.satisfying(sub.added, pub.premise.member(m), mode)
```

```kotlin
class SubscriptionManager(private val port: SubscriptionPort) {
    private val byCallee = HashMap<MethodKey, CalleeSubscriptions>()
    private val mode get() = port.config.mode
    private val ops get() = port.ops

    fun subscribe(sub: Subscription) {
        val storage = port.summaryStorage(sub.callee)
        val entry = byCallee.getOrPut(sub.callee) {
            CalleeSubscriptions(ops).also { storage.addSubscriber(this) }
        }
        val part = entry.add(sub) ?: return
        port.steps.replay { replay(storage, part) }
    }

    private fun replay(storage: SummaryStorage, part: Subscription) {
        for ((pub, m) in storage.candidates(part, mode)) applyMatch(part, pub, m)
        if (!part.zeroOnly && port.config.restricted) replayRecords(part)
    }

    private fun applyMatch(part: Subscription, pub: Publication, m: Int) =
        matches(part, pub, m, ops, mode)?.let { port.applier(part.caller).applySummary(part.copy(added = it), pub, m) }

    private fun replayRecords(part: Subscription) {
        val records = port.config.records
        val direction = port.config.direction
        val applier = port.applier(part.caller)
        val seen = HashSet<Any>()
        fun applyRec(rec: Record) =
            ops.satisfying(part.added, rec.premise, mode, record = true)?.let { applier.applyRecord(part.copy(added = it), rec) }
        for (leaf in ops.leaves(part.added)) {
            for (rec in records.byEntry(part.callee, leaf))
                if (rec.direction == direction && seen.add(rec)) applyRec(rec)
            for (rec in records.byExit(part.callee, leaf))
                if (rec.direction != direction)
                    for (view in rec.reversedAt(leaf))
                        if (seen.add(view)) ops.satisfying(part.added, view.premise, mode, record = true)?.let {
                            applier.applyRecordView(part.copy(added = it), view)
                        }
        }
    }

    fun notify(callee: MethodKey, pubs: List<Publication>) = port.post(RunEvent.Delivery(callee, pubs))

    fun onDelivery(callee: MethodKey, pubs: List<Publication>) {
        val entry = byCallee[callee] ?: return
        for (pub in pubs) for (m in 0 until pub.premise.size)
            for (part in entry.candidates(pub, m, mode)) applyMatch(part, pub, m)
    }
}
```

```kotlin
class CalleeSubscriptions(private val ops: ApOps) {
    private val trees = AddedFactStore(ops.manager)
    private val parts = ArrayList<Subscription>()
    private val index = PathTrie<Int>()
    private val zeroParts = LinkedHashMap<CallerRef, Int>()

    fun add(sub: Subscription): Subscription? {
        if (sub.zeroOnly) {
            if (sub.ref in zeroParts) return null
            zeroParts[sub.ref] = parts.size; parts += sub
            return sub
        }
        val delta = trees.add(sub.ref, sub.linkLayer, sub.added) ?: return null
        val part = sub.copy(added = delta)
        val id = parts.size; parts += part
        for (leaf in ops.leaves(delta)) index.add(leaf.fact.base, leaf.fact.path.toIntArray(), id)
        return part
    }

    fun candidates(pub: Publication, m: Int, mode: ApMode): Sequence<Subscription> {
        val j = pub.premise.member(m)
        val ids = LinkedHashSet<Int>()
        if (mode.run1) ids += index.lookupExtensions(j.base, j.pathArray)
        else {
            ids += index.lookupPrefixes(j.base, j.pathArray)
            if (j.mark is MarkSlot.Star) ids += index.lookupExtensions(j.base, j.pathArray)
        }
        val zero = if (pub.premise.isZero) zeroParts.values else emptyList()
        return (ids + zero).distinct().asSequence().map { parts[it] }
    }
}
```

### 5.4 P1–P6 in the code

| Rule | Required implementation point |
|---|---|
| P1 | Register before the replay read. |
| P2 | Insert publication before notification. |
| P3 | Linearizable snapshot read. |
| P4 | One match function and complete candidates on both paths. |
| P5 | No removal during a run. |
| P6 | Read current subscriptions at delivery. |
| E-3 | Exact link/request/initial/subscription deduplication; only edges also use spec subsumption. |

The pipeline permits duplicate application. It must never omit an applicable part or join member.

## 6. Scheduling (`analyzer-core.md` §6)

### 6.1 The runner loop

Run the channel/local-queue loop on the unit coroutine. A Work event yields after its quantum or when the
analyzer's zero-work state changes. It remains counted when requeued. Completed handlers decrement only after
all their sends and local work requests.

A `Cancellation.Cancelled` checkpoint stops the runner with the status already selected by the manager. Other
throwables call `fail(FAILED)`. Cancellation is never a separate report status.

```kotlin
    suspend fun runLoop() = coroutineScope {
        var events = 0
        while (isActive) {
            if (queue.isEmpty()) queue.add(channel.receive())
            while (true) queue.add(channel.tryReceive().getOrNull() ?: break)
            val event = queue.poll()!!
            val ended = try { handle(event) }
                catch (e: Cancellation.Cancelled) {
                    check(run.ended) { "a cancel with no end" }
                    return@coroutineScope
                }
            if (ended) run.inFlight.afterHandler()
            else queue.add(event)
            if (event is RunEvent.Work || ++events >= RUNNER_STEPS_QUANT) { events = 0; yield() }
        }
    }
```

### 6.2 The local queue with fixed keys

Queue keys are immutable snapshots made on insertion: zero work first, non-Work events second, then analyzers
with fewer completed steps. Requeued Work gets a new snapshot. This order is a scheduling choice; fairness and
protocol correctness must not depend on reading a mutable key in the heap.

```kotlin
class EventQueue {
    private class Entry(val event: RunEvent, val rank: Int, val steps: Long, val seq: Long)
    private val heap = PriorityQueue(compareBy<Entry>({ it.rank }, { it.steps }, { it.seq }))
    private var seq = 0L

    fun add(e: RunEvent) {
        val (rank, steps) = when (e) {
            is RunEvent.Work -> (if (e.analyzer.hasZeroWork) 0 else 2) to e.analyzer.steps
            else -> 1 to 0L
        }
        heap.add(Entry(e, rank, steps, seq++))
    }
    fun poll(): RunEvent? = heap.poll()?.event
    fun isEmpty(): Boolean = heap.isEmpty()
}
```

### 6.3 `InFlight` and local work

Use one in-flight counter per run. Increment before each accepted event send/local Work enqueue (Q1); decrement
after a complete handler (Q2). Hold a phantom count while start events are sent (Q3). Old runs are joined before
a new counter is used (Q4).

`queued` prevents multiple Work events for an analyzer (W1). Requeue without decrement on a partial step (W2).
End Work only after both queues are empty and publications are flushed (W3).

```kotlin
class InFlight(private val onZero: () -> Unit) {
    private val count = AtomicLong(0)
    private val handledCount = AtomicLong(0)
    val pending: Long get() = count.get()
    val handled: Long get() = handledCount.get()
    fun beforeSend() { count.incrementAndGet() }
    fun afterHandler() { handledCount.incrementAndGet(); if (count.decrementAndGet() == 0L) onZero() }
}
```

### 6.4 Abnormal end, the run scope, the join

| End condition | Required action |
|---|---|
| Quiescence | Select COMPLETE by compare-and-set. |
| Run timeout or memory guard | Select TIMEOUT or OOM, then cancel and complete. |
| Runner/caller-thread throwable | Select FAILED, then cancel and complete. A JVM `OutOfMemoryError` is FAILED. |
| Any exit | Join all runners. A failed join is FAILED and exposes no run stores. |
| Incomplete run | Add no report or record changes; start no later run. |
| Barrier memory guard | Stop with OOM and the report already completed by earlier barriers. |

Activation occurs when the run is created, before any runner or manager publication. No later activation can
undo a cancellation. A late failure after a terminal status cannot replace it, except for the required failed-join
override. The driver catches throwables over construction, run and barrier.

## 7. The driver (`analyzer-core.md` §7)

### 7.1 `IterationDriver`, `IterationPolicy`

The analysis has one time budget. The policy selects nondecreasing field limits with run 1 at least 1, and gives
each run a timeout from the remaining budget. Only complete forward runs ask the stop policy; a complete backward
run proceeds to its next forward run.

At a complete barrier, after all runners join: confirm and update the forward report; persist raw R1 records;
build demand and seeds; log the frontier; then check stop rules. After forward, stop for no demand vulnerability,
no demand-layer objects, or policy. Run the barrier after the final complete run too.

The barrier has its own memory guard and AP checkpoints. It has no separate deadline; its elapsed time consumes
the remaining budget for the next run. An incomplete run adds and refutes nothing.

```kotlin
package org.opentaint.dataflow.bidi.driver

interface IterationPolicy {
    fun fieldLimit(runIndex: Int): Int

    fun continueAfter(run: RunConfig, result: RunResult, frontiers: List<Frontier>): Boolean
    fun timeout(run: RunConfig, remaining: Duration): Duration = remaining
}

class FixedLimits(private val limits: List<Int>) : IterationPolicy {
    override fun fieldLimit(runIndex: Int) = limits[runIndex - 1]
    override fun continueAfter(run: RunConfig, result: RunResult, frontiers: List<Frontier>) =
        run.index + 2 <= limits.size
}

sealed interface Next {
    class Run(val config: RunConfig) : Next
    class End(val status: RunStatus, val reason: EndReason = EndReason.ABNORMAL) : Next
}

class IterationDriver(private val policy: IterationPolicy, private val shared: SharedObjects, private val budget: Duration) {

    val frontiers: List<Frontier> get() = frontierLog
    private val frontierLog = ArrayList<Frontier>()

    fun analyze(roots: List<MethodKey>): Report {
        val start = TimeSource.Monotonic.markNow()
        val report = ReportBuilder()
        var index = 1
        fun end(status: RunStatus, reason: EndReason) = report.build(AnalysisEnd(status, index,
            if (index % 2 == 1) Direction.FORWARD else Direction.BACKWARD, reason))
        try {
            require(policy.fieldLimit(1) >= 1) { "ap.md S12 (d): run 1 needs a field limit of at least 1" }
            var config = RunConfig(1, policy.fieldLimit(1), demand = null, records = shared.records.view(),
                seeds = SeedIndex.EMPTY, roots = roots)
            while (true) {
                index = config.index
                when (val next = runOnce(config, report, policy.timeout(config, budget - start.elapsedNow()))) {
                    is Next.Run -> config = next.config
                    is Next.End -> return end(next.status, next.reason)
                }
            }
        } catch (e: Throwable) {
            logger.error(e) { "Run $index failed; the analysis ends with the report so far" }
            return end(RunStatus.FAILED, EndReason.ABNORMAL)
        }
    }

    private fun runOnce(config: RunConfig, report: ReportBuilder, timeout: Duration): Next {
        val result = RunManager(config, shared).run(timeout)
        if (result.status != RunStatus.COMPLETE)
            return Next.End(result.status)
        val guard = MemoryManager(shared.refManager, BARRIER_MEMORY_THRESHOLD) { shared.cancellation.cancel() }
        val next = try {
            guard.runWithMemoryManager { barrier(config, result, report) }
        } catch (e: Cancellation.Cancelled) {
            Next.End(RunStatus.OOM)
        }
        if (shared.cancellation.isActive()) return next
        logger.error { "Run ${config.index}: the barrier memory guard fired; the analysis ends with the report so far" }
        return Next.End(RunStatus.OOM)
    }

    private fun barrier(config: RunConfig, result: RunResult, report: ReportBuilder): Next {
        val forward = config.direction == Direction.FORWARD
        if (forward) {
            Support(result, config.roots, config.mode, shared).confirm()
            report.add(config, result)
        }
        shared.records.persist(config.direction,
            result.analyzers.asSequence().map { it.key to it.summaries })
        val handOff = HandOff.of(config, result, report, shared)
        val frontier = frontierOf(config, result, handOff, report)
        frontierLog += frontier
        frontier.log()
        if (forward && !report.hasDemandVulnerability())
            return Next.End(RunStatus.COMPLETE, EndReason.STOP_RULE)
        if (forward && result.demandLayerEdges == 0L)
            return Next.End(RunStatus.COMPLETE, EndReason.NO_DEMAND_EDGE)
        if (forward && !policy.continueAfter(config, result, frontiers))
            return Next.End(RunStatus.COMPLETE, EndReason.POLICY)
        return Next.Run(nextConfig(config, handOff))
    }

    private fun nextConfig(config: RunConfig, handOff: HandOff): RunConfig {
        val limit = policy.fieldLimit(config.index + 1)
        require(limit >= config.fieldLimit) { "ap.md W3: the field limit must not decrease" }
        return RunConfig(config.index + 1, limit, handOff.demand, shared.records.view(), handOff.seeds, config.roots)
    }

    companion object { const val BARRIER_MEMORY_THRESHOLD = 0.99 }
}
```

### 7.2 Forward run `n` → backward run `n + 1` (`analyzer-core.md` §7.3)

Read stored selected publications, not all summaries and not newly persisted records. Reverse every selected
conclusion/member pair for the next backward demand. Convert forward must tails to their backward location
view, then normalize abstract mark exclusions to `*` on both sides; preserve concrete marks and other field
exclusions. Builder insertion rejects duplicates and the implicit zero demand; count successful insertions only.

Seed the report's DEMAND vulnerabilities using each witness's method key and every alternative of its rule at the
sink statement. Confirmed keys need no hand-off seed; end-fact trigger seeds still come from the forms.

```kotlin
class HandOff(val demand: DemandStore, val seeds: SeedIndex, val demandEdges: Map<MethodKey, Int>) {
  companion object {

    fun of(config: RunConfig, result: RunResult, report: ReportBuilder, shared: SharedObjects): HandOff {
        val counts = LinkedHashMap<MethodKey, Int>()
        val (demand, seeds) =
            if (config.direction == Direction.FORWARD) toBackward(result, report, shared, counts)
            else toForward(result, shared, counts)
        return HandOff(demand, seeds, counts)
    }

    private inline fun forEachDemandPiece(result: RunResult, shared: SharedObjects, body: (MethodKey, PremiseKey, Pattern) -> Unit) {
        for (a in result.analyzers) {
            shared.cancellation.checkpoint()
            for ((premise, g) in a.summaries.demandEdges()) for (leaf in shared.ops.leaves(g)) body(a.key, premise, leaf)
        }
    }

    private fun toBackward(result: RunResult, report: ReportBuilder, shared: SharedObjects,
                           counts: MutableMap<MethodKey, Int>): Pair<DemandStore, SeedIndex> {
        val demand = DemandStore.Builder(shared.ap)
        forEachDemandPiece(result, shared) { m, premise, piece ->
            premise.forEachMember { j ->
                if (demand.add(m, DemandPattern(entry = normalizeDemandMark(located(piece)),
                    exit = normalizeDemandMark(located(j.toPattern()))))) counts.merge(m, 1, Int::plus)
            }
        }
        val seeds = ArrayList<Seed>()
        for (entry in report.demandEntries())
            for (mk in entry.witnesses.mapTo(LinkedHashSet()) { it.methodKey })
                for (sink in sinksAt(shared, mk, entry.key.statement)) if (sink.rule == entry.key.rule)
                    for (lit in sink.seedPatterns())
                        seeds += Seed.Sink(entry.key.rule, mk, entry.key.statement, lit)
        return demand.build() to SeedIndex.of(seeds.distinct())
    }

    private fun located(p: Pattern): Pattern =
        if (p.fact.tail != Tail.ANY_TAINT) p else Pattern(p.fact.copy(tail = Tail.ANY), ExclusionSet.Empty)

    private fun normalizeDemandMark(p: Pattern): Pattern =
        if (p.fact.mark !is MarkSlot.Star || p.fact.mark.excluded.isEmpty) p
        else p.copy(fact = p.fact.copy(mark = MarkSlot.STAR))

    private fun sinksAt(shared: SharedObjects, key: MethodKey, s: CommonInst): List<SinkRule> {
        val forms = shared.contexts.forms(key)
        shared.language.getCallExpr(s)?.let { return forms.call(Direction.FORWARD, s, it).sinks }
        return if (shared.interpreter.exitNodes(key).any { it.node == s })
            forms.exitRules(Direction.FORWARD, s).rules.sinks else emptyList()
    }
```

### 7.3 Backward run `n + 1` → forward run `n + 2` (`analyzer-core.md` §7.4)

Backward has singleton premises and no must tail. A selected zero-premise conclusion gives `(gb, none)`;
a selected nonzero-premise conclusion gives `(gb, jb)`. Normalize excluded abstract marks on both sides and count
successful Builder insertions. Source hits become forward source seeds.

A demand `jb → zero` gives `(zero, jb)` and restricts forward source publications. A NORMAL nonzero singleton
`jb → zero` is a backward record instead: its reversal supplies `{zero} → jb` at the caller, with no callee demand.
The implicit `(zero, none)` publishes nothing in a restricted forward run.

```kotlin
    private fun toForward(result: RunResult, shared: SharedObjects,
                          counts: MutableMap<MethodKey, Int>): Pair<DemandStore, SeedIndex> {
        val demand = DemandStore.Builder(shared.ap)
        forEachDemandPiece(result, shared) { m, jb, piece ->
            val pattern = when (jb) {
                is InitialAp -> if (jb.isZero) DemandPattern(normalizeDemandMark(piece), null)
                                else DemandPattern(normalizeDemandMark(piece), normalizeDemandMark(jb.toPattern()))
                is PremiseSet -> error("the backward run has no ND summary (ap.md §9.2)")
            }
            if (demand.add(m, pattern)) counts.merge(m, 1, Int::plus)
        }
        val seeds = ArrayList<Seed>()
        for (a in result.analyzers) for ((m, s, e) in a.sourceHits!!.entries()) seeds += Seed.Source(m, s, e)
        return demand.build() to SeedIndex.of(seeds)
    }
  }
}
```

### 7.4 `Seed`, `SeedIndex`

Index seeds by method key and statement. Source lookup includes the forward edge to distinguish instructions.
No method key or context is lost when deriving seeds. Sink literals give requirements; unconditional sinks have
no positive literal and produce no seed.

```kotlin
sealed interface Seed {
    val method: MethodKey
    val statement: CommonInst
    data class Sink(val rule: RuleId, override val method: MethodKey, override val statement: CommonInst,
                    val requirement: Pattern) : Seed
    data class Source(override val method: MethodKey, override val statement: CommonInst, val edge: PathEdge) : Seed
}

class SeedIndex(private val byPlace: Map<Pair<MethodKey, CommonInst>, List<Seed>>) {
    private val sources: Set<Triple<MethodKey, CommonInst, PathEdge>> =
        byPlace.values.flatten().filterIsInstance<Seed.Source>().mapTo(HashSet()) { Triple(it.method, it.statement, it.edge) }

    fun at(method: MethodKey, statement: CommonInst): List<Seed> = byPlace[method to statement].orEmpty()
    fun allowsSource(method: MethodKey, statement: CommonInst, forward: PathEdge): Boolean =
        Triple(method, statement, forward) in sources
    val size: Int get() = byPlace.values.sumOf { it.size }

    fun sinkMethods(): Set<MethodKey> = byPlace.values.flatten().filterIsInstance<Seed.Sink>().mapTo(HashSet()) { it.method }

    companion object {
        val EMPTY = SeedIndex(emptyMap())
        fun of(seeds: List<Seed>) = SeedIndex(seeds.groupBy { it.method to it.statement })
    }
}
```

### 7.5 `Support` and the confirmation (`analyzer-core.md` §7.5)

Support is the least fixed point over normal links at one call. Root zero is the only root base case. Every
member must be confirmable and have a supplier whose caller premise set is already supported. All members use
the same call site; they can use different caller edges at that site.

Use the AP's `confirmableMember` and `supplies` helpers for their local tests. They retain concrete-mark and
restricted must-tail/exclusion guards. A normal link alone is insufficient: its caller edge must also be NORMAL.
The direct list scan below is the concept. A per-site exact/must index is an optional optimization and requires
an equivalence check; it does not belong in the transfer contract.

Confirm a witness only when all its sink-edge layers are NORMAL and its joint support is true. Support already
checks member admissibility, so do not repeat that test at the final confirmation.

```kotlin
class Support(private val result: RunResult, roots: List<MethodKey>, private val mode: ApMode,
              private val shared: SharedObjects) {
    private data class Site(val callee: MethodKey, val caller: MethodKey, val call: CommonInst)
    private val roots = roots.toHashSet()
    private val ap = shared.ap
    private val atSite = HashMap<Site, MutableList<Link>>()
    private val sitesOf = HashMap<MethodKey, MutableSet<Site>>()
    private val fedBy = HashMap<Pair<MethodKey, PremiseKey>, MutableSet<Site>>()
    private val questions = HashMap<MethodKey, MutableSet<PremiseKey>>()
    private val supported = HashSet<Pair<MethodKey, PremiseKey>>()

    init {
        check(result.direction == Direction.FORWARD)
        for (callee in result.analyzers) {
            shared.cancellation.checkpoint()
            for (link in callee.links.links()) {
                val ref = link.caller
                if (link.linkLayer != Layer.NORMAL || ref.callerLayer != Layer.NORMAL) continue
                val site = Site(callee.key, ref.caller, ref.call)
                atSite.getOrPut(site, ::ArrayList) += link
                sitesOf.getOrPut(callee.key, ::HashSet) += site
                fedBy.getOrPut(ref.caller to ref.premise, ::HashSet) += site
                questions.getOrPut(ref.caller, ::HashSet) += ref.premise
            }
        }
        val work = ArrayDeque<Pair<MethodKey, PremiseKey>>()
        for (root in this.roots) if (supported.add(root to ap.zero)) work += root to ap.zero
        while (work.isNotEmpty()) {
            shared.cancellation.checkpoint()
            val known = work.removeFirst()
            for (site in fedBy[known].orEmpty()) for (premise in questions[site.callee].orEmpty())
                if ((site.callee to premise) !in supported && suppliedAt(site, premise.members)) {
                    supported += site.callee to premise
                    work += site.callee to premise
                }
        }
    }

    private fun suppliedAt(site: Site, members: Collection<InitialAp>): Boolean =
        members.all { j -> confirmableMember(j, mode) && atSite[site].orEmpty().any { link ->
            supplies(j, link, mode) && (site.caller to link.caller.premise) in supported
        } }

    fun isSupported(method: MethodKey, members: Collection<InitialAp>): Boolean =
        (method in roots && members.all { it.isZero }) ||
            sitesOf[method].orEmpty().any { suppliedAt(it, members) }

    fun confirm() {
        for ((_, witness) in result.vulnerabilities.witnessesOf(result.runIndex))
            witness.confirmed = witness.edges.all { it.layer == Layer.NORMAL } &&
                isSupported(witness.methodKey, witness.supportPremise(ap).members)
    }
}
```

### 7.6 `Report`

Confirmed keys are final. The demand set comes from the latest complete forward run and excludes every key
already confirmed. A complete run can refute an earlier DEMAND key by not reporting it. Incomplete runs leave the
report unchanged. Return both CONFIRMED and remaining DEMAND entries, with their run and complete witnesses.

```kotlin
enum class ReportState { CONFIRMED, DEMAND }

enum class EndReason { STOP_RULE, NO_DEMAND_EDGE, POLICY, ABNORMAL }

data class AnalysisEnd(val status: RunStatus, val run: Int, val direction: Direction, val reason: EndReason)

class Report(val entries: List<Entry>, val end: AnalysisEnd) {
    class Entry(val key: VulnerabilityKey, val state: ReportState, val run: Int, val witnesses: List<SinkWitness>)
}

class ReportBuilder {
    private val confirmed = LinkedHashMap<VulnerabilityKey, Report.Entry>()
    private var demand = LinkedHashMap<VulnerabilityKey, Report.Entry>()

    fun add(config: RunConfig, result: RunResult) {
        val next = LinkedHashMap<VulnerabilityKey, Report.Entry>()
        for ((key, ws) in result.witnessesByKey())
            if (ws.any { it.confirmed }) confirmed.putIfAbsent(key, Report.Entry(key, ReportState.CONFIRMED, config.index, ws))
            else next[key] = Report.Entry(key, ReportState.DEMAND, config.index, ws)
        demand = next
    }

    fun demandEntries(): List<Report.Entry> = demand.values.filter { it.key !in confirmed }

    fun hasDemandVulnerability(): Boolean = demandEntries().isNotEmpty()

    val confirmedCount: Int get() = confirmed.size

    fun build(end: AnalysisEnd): Report = Report(confirmed.values + demandEntries(), end)
}
```

### 7.7 What stays after a run, as code (`analyzer-core.md` §7.6)

| Data | Release point |
|---|---|
| Edges, initials, backward links, requests, conjunctions, work queues, ports and run context | `freeze`, after every runner joined. |
| Forward links | After confirmation at the barrier. |
| Raw summaries, stored demand publications and backward source hits | After persistence and hand-off. |
| Incomplete run stores | End of the run; never expose them to a barrier. |
| Runner maps, shared run storages and subscription managers | End of the run. |
| Report witnesses, records, AP manager and method/forms cache | Analysis lifetime. |
| Frontier entries | Analysis lifetime; counts and method keys only. |

`runOnce` keeps its result only until the barrier returns, before constructing the next manager. Trace resolution
does not retain run stores (§8.2).

### 7.8 The frontier log (`analyzer-core.md` §7.8)

After every complete run, record nonzero analyzed method keys, accepted hand-off patterns per method, record
crossings, seeds, forward report counts and work on zero-only methods. Optional cause counts diagnose demotions;
no stop rule reads them. Do not assert exact narrowing in marks: hand-off normalization can enlarge abstract marks.
The frontier log measures completed and remaining work; it is not a new stop theorem.

```kotlin
package org.opentaint.dataflow.bidi.driver

fun frontierOf(config: RunConfig, result: RunResult, handOff: HandOff, report: ReportBuilder): Frontier {
    val forward = config.direction == Direction.FORWARD
    val a = result.analyzers
    val zeroOnly = a.filter { !it.nonZeroInitial }
    return Frontier(config.index, config.direction,
        analysed = a.filter { it.nonZeroInitial }.mapTo(LinkedHashSet()) { it.key },
        demandEdges = handOff.demandEdges,
        recordCrossings = result.recordCrossings,
        demandVulnerabilities = if (forward) report.demandEntries().size else 0,
        confirmedVulnerabilities = if (forward) report.confirmedCount else 0,
        seeds = handOff.seeds.size,
        zeroOnly = zeroOnly.mapTo(LinkedHashSet()) { it.key },
        zeroOnlyEdges = zeroOnly.sumOf { it.edgeDeltas },
        demandByCause = a.mapNotNull { it.demandByCause }.takeIf { it.isNotEmpty() }
            ?.let { arrays -> DemandCause.entries.associateWith { c -> arrays.sumOf { it[c.ordinal] } } })
}

fun Frontier.log() {
    logger.info { "Run $run ($direction) frontier: analysed ${analysed.size}, demand edges ${demandEdges.values.sum()} " +
        "in ${demandEdges.size} method keys, left ${(analysed - demandEdges.keys).size}, " +
        "record crossings $recordCrossings, DEMAND $demandVulnerabilities, CONFIRMED " +
        "$confirmedVulnerabilities, seeds $seeds, zero only ${zeroOnly.size} ($zeroOnlyEdges edges)" }
    logger.debug { "Run $run frontier: analysed $analysed; demand edges $demandEdges" }
    demandByCause?.let { logger.info { "Run $run demand-layer results per cause: $it" } }
}
```

## 8. Phase 3 and phase 5

### 8.1 The phase-3 entry: `JIRBidiAnalysis`

Copy prescan rule ids, lambda resolutions, root entry points, fact type checker and external-method tracker;
then release the whole prescan engine/context state. Use the copied values to build the interpreter, AP manager,
method cache and shared driver objects. The new analysis has its own Cancellation; reactivating it cannot restart
a prescan runner.

Add the release members below to the existing interfaces/classes. In the prescan runner manager,
`activeApManager` becomes nullable and its existing `apManager` getter requires a nonnull value. Old callers never
call release. `JIRTaintAnalyzer` supplies the optional `bidiEntry`; null retains its current full-scan path.

The entry returns every report entry with a simple sink-only trace. Choose a confirmed witness's method key
when one exists, otherwise the first witness. Map COMPLETE/TIMEOUT/OOM/FAILED to current analysis status and
apply the existing CWE output filter. Trace status is OK because this phase resolves no full trace.

```kotlin
    fun releasePrescan() {}

    fun prescanRuleIds(): Set<String> = relevantRuleIds.toSet()

    override fun releasePrescan() = contexts.clear()

    fun releasePrescan() {
        runnerForUnit.clear()
        unitStorage.clear()
        methodDependencies.clear()
        activeApManager = null
    }
```

```kotlin
package org.opentaint.dataflow.bidi.driver

interface BidiEntry {
    fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>)
    fun run(budget: Duration): Report
    fun toVulnerabilities(report: Report): List<VulnerabilityWithTrace>
    fun status(report: Report): TaintAnalysisUnitRunnerManager.Status
}
```

```kotlin
package org.opentaint.dataflow.jvm.bidi

class JIRBidiAnalysis(
    private val cp: JIRClasspath,
    private val graph: JApplicationGraph,
    private val unitResolver: JIRUnitResolver,
    private val taintConfig: TaintRulesProvider,
    private val params: JIRAnalysisManager.Params,
    private val policy: IterationPolicy,
    private val refManager: RefManager,
) : BidiEntry {
    private val cancellation = Cancellation()

    class PrescanResult(
        val ruleIds: Set<String>,
        val lambdas: PrescanLambdas,
        val roots: List<MethodEntryPoint>,
        val checker: JIRFactTypeChecker,
        val externalMethodTracker: ExternalMethodTracker?,
    )

    private var prescan: PrescanResult? = null

    override fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>) {
        val m = manager as JIRAnalysisManager
        val resolver = JIRMethodEntrypointResolver(graph)
        val roots = startMethods.flatMap { s ->
            resolver.resolveEntryPoints(s.method, s.ctx).map { MethodEntryPoint(s.ctx, it) }
        }
        prescan = PrescanResult(m.prescanRuleIds(), m.prescanLambdas(), roots, m.factTypeChecker, m.externalMethodTracker)
    }

    override fun run(budget: Duration): Report {
        val pre = checkNotNull(prescan) { "gather() comes first" }.also { prescan = null }
        taintConfig.selectRules(pre.ruleIds)
        val ap = ApManager(cancellation, refManager)
        val language = JIRLanguageManager(cp)
        val callResolver = JIRCallResolver(cp, unitResolver)
        val entries = JIRMethodEntries(graph, language, callResolver, taintConfig, params.aliasAnalysisParams,
            cancellation, pre.lambdas)
        val interpreter = JIRInterpreter(ap, taintConfig, pre.checker, callResolver,
            JIRMethodEntrypointResolver(graph), entries, params.defaultGetModel, pre.externalMethodTracker)
        val contexts = MethodContextCache(interpreter, JIRMethodContextCache(interpreter, entries))
        @Suppress("UNCHECKED_CAST")
        SharedObjects(ap, ApOps(ap), interpreter, language, contexts, PersistentRecordStore(ap),
            ConcurrentVulnerabilityStore(ap), unitResolver as UnitResolver<CommonMethod>, refManager, cancellation).use { shared ->
            return IterationDriver(policy, shared, budget).analyze(pre.roots)
        }
    }

    override fun toVulnerabilities(report: Report): List<VulnerabilityWithTrace> {
        val count = report.entries.groupingBy { it.state }.eachCount()
        logger.info { "Bidi analysis: ${count[ReportState.CONFIRMED] ?: 0} CONFIRMED, ${count[ReportState.DEMAND] ?: 0} DEMAND vulnerabilities" }
        return report.entries.map { e ->
            val rule = e.key.rule
            val witness = e.witnesses.firstOrNull { it.confirmed } ?: e.witnesses.first()
            val node = TaintSinkTracker.TaintVulnerabilityRuleNode.Unconditional(witness.methodKey)
            VulnerabilityWithTrace(TaintSinkTracker.TaintVulnerability(e.key.statement, rule.id, hashMapOf(rule to node)),
                TracePathGenerationResult.Simple)
        }
    }

    override fun status(report: Report): TaintAnalysisUnitRunnerManager.Status = when (report.end.status) {
        RunStatus.COMPLETE -> Status.OK
        RunStatus.TIMEOUT -> Status.TIMEOUT
        RunStatus.OOM -> Status.OOM
        RunStatus.FAILED -> Status.EXCEPTION
    }
}
```

```kotlin
    open fun bidiEntry(): BidiEntry? = null

    private fun analyzeStaged(entryPoints: List<Method>): Pair<List<VulnerabilityWithTrace>, Status> {
        val analysisStart = TimeSource.Monotonic.markNow()
        val startMethods = entryPoints.map { MethodWithContext(it, EmptyMethodContext) }
        prescan(startMethods)
        val bidi = bidiEntry() ?: return fullScan(analysisStart, entryPoints, startMethods)
        val report = runCatching {
            bidi.gather(analysisManager, startMethods)
            analysisManager.releasePrescan()
            ifdsEngine.releasePrescan()
            bidi.run(options.ifdsTimeout - analysisStart.elapsedNow())
        }.getOrElse { e ->
            logger.error(e) { "Bidi analysis failed" }
            return emptyList<VulnerabilityWithTrace>() to
                Status(TaintAnalysisUnitRunnerManager.Status.EXCEPTION, TaintAnalysisUnitRunnerManager.Status.OK)
        }
        var vulnerabilities = bidi.toVulnerabilities(report)
        if (options.analysisCwe != null) vulnerabilities = vulnerabilities.filter {
            val cwe = (it.vulnerability.rule.meta as TaintSinkMeta).cwe
            cwe?.intersect(options.analysisCwe)?.isNotEmpty() ?: true
        }
        return vulnerabilities to Status(bidi.status(report), TaintAnalysisUnitRunnerManager.Status.OK)
    }

    override fun bidiEntry(): BidiEntry? = bidiPolicy?.let { policy ->
        JIRBidiAnalysis(cp, ifdsAnalysisGraph as JApplicationGraph, analysisUnit, taintConfig, analysisParams, policy,
            refManager)
    }
```

### 8.2 The phase-5 read API: out of scope

Full trace resolution is out of scope. Phase 3 returns the spec report with simple traces; it does not retain
run edge stores for a trace resolver. Known trace/end-fact limitations remain listed in `analyzer-core.md` §11
and `ap.md` §11.1. An eventual resolver needs its own proposal and proof/validation scope.

## 9. Test plan (`validation-plan.md` → Analyzer tests → §13)

### 9.1 Test classes and the TDD order

Use [validation-plan.md](validation-plan.md) for the complete validation inventory. This table defines the
implementation test boundaries and order without another copy of fixture code. Row numbers are stable for
existing references. Engine/driver tests use `kotlin.test` in `TEST/bidi`; JVM tests use the JVM module.

Fixtures must cache stable forms per method key. Preserve full tails, exclusions, marks and layers; do not compare
only finding counts. Lowered JVM field actions use read/clean/write; multi-field toy statements remain AP fixtures.

| Order | Test boundary | Required observation |
|---|---|---|
| 1 | `InFlightTest` | Q1–Q4; no zero while an event, handler or start batch is live. |
| 2 | `EventQueueTest`, `DeltaWorklistTest` | Immutable priority keys, unchanged-loop termination across Work events, no liveness drop. |
| 3 | `EngineAlgebraTest` | All three form modes agree with per-path AP results; source hooks occur at seed places only. |
| 4 | `SummaryStorageProtocolTest` | Broken P1–P4 mocks lose the expected summary; correct schedules retain it. |
| 5 | `IndexCompletenessTest` | Replay and delivery candidate scans include every accepted complete-list match. |
| 6 | `AnyDeliveryTest` | Must, may and distinct exclusions at one path remain separate premise keys and matches. |
| 7 | `RecordReplayTest` | Native direction, transient views, mark/exclusion matching and zero-source record replay. |
| 8 | `NdJoinAdapterTest` | Replay/delivery in either order; every member required; zero never joins E6. |
| 9 | `CallPlanTest` | Actions order, binding, aliases, rewriter and both directed plan walks. |
| 10 | `ModesTest` | No restricted requests or backward sinks; zero rules only on `{zero} → zero`. |
| 11 | `FactKindsTest` | FLOW/TAINT matching, ND TAINT, drop-zero premise union and backward REACH. |
| 12 | `ExitRulesTest` | Both exits, boundary termination, evaluated static scope and removal of all entry-mark leaves. |
| 13 | `CutPointTest` | Every required cut, filtered results, W3 and lowered field actions. |
| 14 | `RequestJoinTest` | Request-first/link-first, answer, climb and later new caller link. |
| 15 | `NaiveClosureTest` | Context-local sink/conjunction joins and reference finite-program vectors. |
| 16 | `ScheduleFuzzerTest` | Quiescent engine observations equal direct per-path closure (§9.2). |
| 17 | `HandOffTest` | Both reversals, zero cases, mark normalization and seed origin. |
| 18 | `SupportTest` | Same-call joint support; no mixing sites or contexts; unsupported cycles do not confirm. |
| 19 | `ReportTest` | Confirmed final; latest complete demand replaces earlier demand; incomplete run changes nothing. |
| 20 | `DriverTest` | Run sequence, limit/budget guards, barrier ordering and all stop reasons. |
| 21 | `DemandLinkTest` (DLINK) | A demand link alone prevents the stop; precise later descendants can become supported. |
| 22 | `RunFailureTest` | Status before cancellation, first end, failed join, all throwable/guard paths. |
| 23 | `LifetimeTest`, JVM phase-3 test | Prescan release, fresh run machinery, read-only records and both report states in output. |
| 24 | `AnyTaintTest` | Must source/transfer exclusions, cleaner/cut/may demotions and no normal TAINT absorption. |
| 25 | `MustPremiseTest` | Concrete restricted emission, exclusions, must starts; run1/backward reject must initials. |
| 26 | `RecordDemotionTest` | Native inside/applicable-only partition, late timing, factored view guards and no synthetic hits. |
| 27 | `AnyTaintSupportTest` | Normal must links supply admitted precise members with the same concrete mark; demand links do not. |
| 28 | `AnyTaintIterationTest` | Getter/source/identity/setter finite programs with current engine expectations. |
| 29 | `DemandEdgeTest` | Raw R1 persistence independent of selection; eligible normal singletons omitted; backward zero selected; NORMAL ND preserved. |
| 30 | `FrontierPropertyTest` | Accepted Builder insertion counts, implicit-zero exclusion, complete-only logs. |
| 31 | `EndFactTriggerTest` (END) | A reversed end fact reseeds its trigger even when that vulnerability is confirmed. |
| 32 | `ConjunctionReversalTest` (CONJ) | Independent demand branches cannot become a one-literal forward record. |
| 33 | `MarkRestrictionTest` | Full premise and exit marks; raw summary retained when publication is rejected. |
| 34 | `NormalizedHandOffTest` | Both excluded abstract marks normalize; concrete marks/field exclusions stay; duplicate counts. |

DLINK uses a root EXACT cleaner, not a named field action. With limits 1, 2, 3, 4, 5, the restricted run can
have normal sink edges supplied only by a demand link; it remains DEMAND and continues. Later exact descendants
survive root cleaning on a normal link and can confirm. `CurrentDemandLink` proves only its local transfer vector.

END makes `sinkCall` assign an end mark U; a later may wrapper sends U to another sink. The first sink can be
confirmed in run 1, yet reversal of its end fact must seed the first sink again to retain the later finding.

CONJ has `{p.$(A), q.$(B)} → ret.$(C)`. Backward `ret.$(C) → p.$(A)` and
`ret.$(C) → q.$(B)` are independent DEMAND edges. A later call with only A must not return C through a persisted
one-literal record. Test normal and mixed forward literal layers separately.

### 9.2 The schedule fuzzer

Keep the schedule fuzzer test-only. On one thread, run the production analyzer, storage and subscriptions with
one event pool. Randomly select a pending event, partial Work step, deferred replay or deferred notify. Give each
method key its own unit so cross-unit communication is always observable; also test same-unit direct calls.
Every seed must reproduce its schedule. Record all processed facts, including the unchanged path.

`NaiveClosure` is a direct per-path least fixed point. It has no trie, tree, subsumption or asynchronous delivery.
Use `FormApplier` with `ReferenceAlgebra`, and `FormsReference.run` with explicit hooks for calls, actions,
boundaries, seeds and triggers. Read demand/record candidates by complete list scans in index-equivalence tests;
using the production index as the only oracle would hide a missing candidate. Apply the full current per-leaf
emission, satisfaction, reduction, cleaner, view and support contracts.

Compare raw per-path operation conclusions exactly before store merging. After stores, compare the unions of
decoded locations and marks in both directions per method, statement, premise and layer. A merged tree can have
the same denotation without pairwise leaf coverage; use the union comparison for stored observations.
Also compare raw summaries, publications, selected demand edges, links, requests, source hits, witness support and
both hand-offs. An equal vulnerability count is insufficient. Test replay-first, publish-first, duplicate delivery
and a partial combination completed by the other path.

Sharing form-mode code checks AP and protocol integration. Lean contracts and explicit regression vectors check
that shared code's semantics; agreement between two users of the same helper is not a separate soundness proof.

### 9.3 Proof-first map

For each change: first state the per-leaf concept and guards in Lean, prove the required invariant constructively,
then compute an executable witness. Compare the direct concept with each index/tree optimization before Kotlin
TDD. Use the regression rows of §9.1 as implementation tests; retain the full requirements of `validation-plan.md`.

Current expectations come from the simplified contracts, not historical concrete-only emission, obsolete
crossability tests or exact mark narrowing. Where the full proof is open, state the restricted local scope and
verify the missing integration separately. Do not label historical whole-sequence fixtures as current proofs.

## 10. Verification scope

[proof-status.md](proof-status.md) defines the exact guards of each result. The implementation must retain those
guards, even when its common case has a shorter algorithm.

| Proposal operation | Normative contract | Local Lean evidence / remaining integration |
|---|---|---|
| Typed facts and initials | `ap.md` §7.2, W1/W2/W6/W8 | `CurrentKinds`; arbitrary excluded-mark demands need the full reference representation. |
| Emission, summary reduction and demand indexes | `ap.md` §6.3–§6.4 | `CurrentDemand`, `CurrentDemandClosure`, `CurrentTreeReduction`; optimized tree and engine comparison pending. |
| Canonical FLOW matching | `ap.md` §4.3 | `CurrentSimplifications.Flow.canonical_flow_satisfaction_X`; noncanonical guards remain. |
| Raw summary selection | `ap.md` §8.5, §8.7 R1 | `CurrentSummarySelection`; do not combine R1 persistence with demand selection. |
| Must record views | `ap.md` §8.7 R3–R4 | `CurrentMustReverse`, `CurrentSimplifications.MustViews`; same-direction demotion timing review remains. |
| Forward conjunctions and independent reversal | `ap.md` §4.6, §9.2 | `CurrentConjunction`; full restricted annotated ND integration remains open. |
| Ordered Actions and candidate flag | `interpreter.md` §4.5, AC3/AC4 | `CurrentSimplifications.Analyzer`; keep stage endpoints and current-layer alias guard. |
| Support and confirmation | `ap.md` §4.9 | `CurrentSimplifications.Confirmation`; global support fixed-point integration remains open. |
| Sink context isolation | `ap.md` §8.9–§8.10 | `CurrentSinkJoin`; engine/oracle context regression required. |
| Demand links and frontier counts | `analyzer-core.md` §4.1, §7.8 | `CurrentDemandLink`, `CurrentDemandFrontier`; full program/driver checks required. |
| Publication protocol and completion | `analyzer-core.md` §5–§6 | `PipelineProofs`, `PipelineStore` verify local protocol models; current combined pipeline remains open. |
| Hand-off, seeds, reporting, JVM integration and lifetimes | `analyzer-core.md` §7–§9 | Current local vectors plus §9 integration tests; no full F72/F75 iteration proof. |

This is an implementation proposal. Full current iteration coverage, combined must-tail/exclusion semantics,
interpreter integration, optimized tree equivalence and concurrent pipeline correctness remain proof obligations.
The rewrite does not close them by removing implementation detail.
