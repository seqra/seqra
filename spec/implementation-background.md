# Implementation background

For current run-1 request changes use [F78](run1-requests.md) and its
[implementation proposal](../docs/design/bidirectional-run1-mark-request-implementation-20261015.md).
The older request/memo designs below are migration history.

The selected [added-fact removal redesign](../docs/design/bidirectional-added-facts-removal-20261015.md)
removes the callee input store and its post-freeze Support consumer. Descriptions of those
components below are migration history. Caller-owned N2 remains. Use the
[new implementation proposal](../docs/design/bidirectional-added-facts-removal-implementation-20261015.md)
for the target input, request and candidate-report lifetimes.

This file describes existing code and migration context. It is not normative.
The current specs define the required behavior. Code here is historical unless
the current implementation proposals explicitly adopt it. In particular, the
crossability APIs, per-leaf demand selector, and `crossableLeaves` counter below
were removed by F77. Use the structural selector in [ap-impl.md](ap-impl.md)
and [analyzer-impl.md](analyzer-impl.md); do not port those historical interfaces.

## Analyzer code reuse

## 8. Code reuse

| Today | Decision | Note |
|---|---|---|
| `TaintAnalysisUnitRunnerManager`, `AnalysisUnitRunnerManager` | REFACTOR into `RunManager` | keep the unit routing, the runner spawn, the counter, the timeout, the memory guard and the progress log; remove `resetApManager`, the delayed units and the cross-run fields; add `RunConfig`, the per-run scope, the join of the runners and the map of the `SummaryStorage`s |
| `TaintAnalysisUnitRunner` | REFACTOR into `UnitRunner` | keep the channel, the priority queue, the quantum and `MethodAnalyzerStorage`; the events of §5.1 |
| `AnalysisRunner` | REPLACE by `RunnerPort` (§6.1) | |
| `MethodAnalyzerStorage` | REUSE with a factory | the `EmptyMethodContext` twin goes; the context cache shares the per-method parts (§4.8) |
| `MethodAnalyzer`, `NormalMethodAnalyzer` | REPLACE by `RunMethodAnalyzer` | §4; `TimedMethodAnalyzer` becomes a decorator of the new interface |
| `EmptyMethodAnalyzer` | REMOVE | an empty method is never analysed and never a callee (§4.4) |
| `MethodAnalyzerEdges`, `EdgeCollection` | REUSE the structure | the new keys of `ap.md` §8.1; the list of `EdgeCollection` becomes the `normal` queue of `DeltaWorklist` (§4.3) |
| `AccessPathBaseStorage`, `MethodAnalyzerEdges.EdgeStorage` | NOT USED, REPLACE | `AccessPathBaseStorage` rejects the zero base; the conclusion group of `ap-impl.md` §4.3 (one premise key, every base and kind) replaces `EdgeStorage` (`ap.md` §7.6) |
| `JIRLocalVariableReachability` (`isReachable`) | NOT USED by the core | no liveness check (§4.3); the alias analysis keeps it as its own input (§4.8) |
| `Edge` (`ZeroToZero`, `ZeroToFact`, `FactToFact`, `NDFactToFact`) | REPLACE | `ap.md` §7.6 |
| `SummaryEdgeStorageWithSubscribers`, `MethodSummariesUnitStorage` | REUSE the pattern | publications per premise key and layer; the lock of P3 (§5.2); one storage per method key in the `RunManager` |
| `SummaryEdgeSubscriptionManager`, `CommonAPSub`, the tree sub-storages | REUSE the pattern | the registration on the first `getOrPut`, the delta insert, the replay, the match at delivery; P4 with one `matches`; one manager per runner |
| side-effect requirements and summaries, `TaintMarkFieldUnfoldRequest`, `MethodSideEffectSummaryHandler`, `triggerSideEffectRequirement` | REMOVE | the requests over the links (§4.6) |
| fact-depth delay (`INITIAL_ALLOWED_FACT_DEPTH`, `MethodAnalysisDelayed`, `DelayedAnalysisResume`, `factLimit`) | REMOVE | the field limit (`ap.md` §4.4) |
| `InitialFactAbstraction` (tree, automata, cactus) | REMOVE | the policy and the emission (§4.4) |
| `MethodSummaryEdgeApplicationUtils`, `MethodCallSummaryHandler` | REPLACE | `applySummary` (`ap.md` §4.3); the rewriter moves to the interpreter |
| `TaintSinkTracker`, the vulnerability buckets of `TaintAnalysisUnitStorage` | REPLACE | the `VulnerabilityStore` (§4.7) and the conjunction store (`ap.md` §8.9); no lossy merge |
| `MethodCallResolver`, `JIRMethodCallResolver` | ADAPT | today it is typed to `TaintAnalysisUnitRunner` and calls back with a `MethodCallHandler` per edge kind; the new one gives the resolved callees to `callPlan`, with no empty method (§4.4) |
| `TrackerWithSubscriber`, `LambdaTracker` | REUSE as the source of the prescan values | no lambda event in the new core (§5.1) |
| `MethodEntrypointResolver`, `UnitResolver`, `LanguageManager` | REUSE | |
| `ApplicationGraph.reversed`, `MethodInstGraph` | REUSE | `JIRAnalysisManager` downcasts the graph to `JApplicationGraph`; ADAPT it to accept the reversed graph |
| `JIRBackwardExitWiringGraph` (`saloed/backward-main`) | PORT | with a cache per method (§4.4) |
| `StatementSummaryBuilder`, `buildReversed`, the JVM flow functions | ADAPT | the forward interpreter of §4.9 (`interpreter.md`); `buildReversed` becomes `StatementSummary.reversed` in the core |
| `JIRMethodAnalysisContext` | SPLIT | the cached part and the run part (§4.8) |
| `MemoryManager`, `Cancellation`, `UnitRunnerStats`, `MethodStats` | REUSE | one instance per run where it has run state; the `RunManager` activates the `Cancellation` in its constructor (§6.3); one more `MemoryManager` for each barrier, with the threshold of today's confirmation (§7.2 B4) |
| summary serialization (`storeSummaries`, `loadSummariesFromRunner`) | NOT USED | the records are the reuse between runs |
| `trace/*` | OUT OF SCOPE | no store of a run stays for it (§7.6); phase 3 gives every report entry, CONFIRMED and DEMAND, the simple trace `TracePathGenerationResult.Simple` (§9) |

Do not copy the defects of today that Appendix A lists. Each one has its rule in this document: P3 and P4 (§5.3),
no edge post-processor (§4.3), the new engine per run (§2, §7.6), the per-run scope (§6.3), the fixed priority keys
(§6.1), and the correct interners of the `ApManager` (O4).

---


## Analyzer reference code at the cleaner checkpoint

## 10. Reference code

The types of the messages and the stores. `PathFact`, `Pattern`, `InitialAp`, `PremiseKey` (`size`, `member(k)`,
`isZero`), `PathNode`, `Facts` (`Reach`, `FlowTree`, `TaintTree`), `PathEdge`, `Direction`, `Record`, `RecordStore`
and the tests are those of `ap.md` §3.4, §4, §7 and §8.7. The premise key is the premise set of `ap.md` §4.6 and §8.1.

```kotlin
typealias MethodKey = MethodEntryPoint            // ap.md §1: context and forward entry statement

enum class Layer { NORMAL, DEMAND }

/* PremiseKey (ap.md §7.1): the InitialAp itself for one member; a PremiseSet for two or more. Interned. */

/** The caller side of a link (E-2). */
data class CallerRef(val caller: MethodKey, val premise: PremiseKey, val callerLayer: Layer, val call: CommonInst)

/** A link (ap.md §8.3): the added fact, its layer on the link, the caller edge. An added fact with the `[any-taint]`
 *  tail is normal on its link (ap.md W8) and holds its exclusion: the emission and the support read its tail and its
 *  exclusion (§4.4, §7.5). */
data class Link(val addedFact: Pattern, val linkLayer: Layer, val caller: CallerRef)

/** A subscription (ap.md §8.4). `zeroOnly`: the backward zero subscription (rule zret). Equality by value (E-3). */
data class Subscription(val callee: MethodKey, val addedFact: Pattern, val linkLayer: Layer, val ref: CallerRef,
                        val zeroOnly: Boolean = false) {
    val caller: MethodKey get() = ref.caller
}

/** A publication: a summary edge of the callee, after the restriction in a restricted run (the intersection with
 *  one demand pattern, in the locations and the marks: the premise lies inside `D-c` with its mark, and the
 *  conclusion mark meets the mark of `D-p`; ap.md §6.4; §4.6). Its layer is `conclusion.layer`, and its marks are
 *  those of the summary edge. `premise` holds the tail of each member and the exclusion of an `[any-taint]` member: an
 *  `[any-taint]` premise and an `[any]` premise of one path are two premise keys, so two publications (§4.6; Lean:
 *  the must flag and the exclusion of `pub` in `PipelineAnyTaintEx.sysDRX`). */
data class Publication(val premise: PremiseKey, val conclusion: Facts)

/** A request of run 1 (ap.md §4.5, §4.10). A position is an interned path of the new AP (ap.md §7.1). */
sealed interface RequestKind {
    data class Mark(val mark: TaintMark) : RequestKind
    data class Position(val path: PathNode) : RequestKind
}

/** A seed (§4.7). A sink seed (backward run): one requirement of a sink witness of a DEMAND vulnerability (§1, §7.3);
 *  a CONFIRMED vulnerability gives none in the hand-off. The backward run also makes sink seeds by itself: those of a
 *  sink alternative whose reversed end-fact edge applies to a requirement (§4.5 THE TRIGGER OF AN END FACT), once per
 *  (method key, statement, alternative); they are not in the `SeedIndex`. The requirement of an `[any]` sink
 *  pattern has the tail `[any]`, in the demand layer (the backward run has no `[any-taint]`, ap.md W8, §9.2). A
 *  source seed (forward restricted run): one unconditional source edge that the backward run reached, in its forward
 *  form; the method key, the statement and the edge identify it in both runs (ap.md §8.11). */
sealed interface Seed {
    val method: MethodKey
    val statement: CommonInst
    data class Sink(val rule: RuleId, override val method: MethodKey, override val statement: CommonInst,
                    val requirement: Pattern) : Seed
    data class Source(override val method: MethodKey, override val statement: CommonInst,
                      val edge: PathEdge) : Seed
}

/** The seeds per (method key, statement). */
class SeedIndex(private val byPlace: Map<Pair<MethodKey, CommonInst>, List<Seed>>) {
    fun at(method: MethodKey, statement: CommonInst): List<Seed> = byPlace[method to statement].orEmpty()
    companion object { val EMPTY = SeedIndex(emptyMap()) }
}

sealed interface RunEvent {
    data class Start(val root: MethodKey) : RunEvent
    data class LinkIn(val callee: MethodKey, val link: Link) : RunEvent
    data class ZeroIn(val callee: MethodKey) : RunEvent
    data class RequestIn(val method: MethodKey, val premise: InitialAp, val request: RequestKind) : RunEvent
    data class Delivery(val callee: MethodKey, val publications: List<Publication>) : RunEvent
    data class Work(val analyzer: RunMethodAnalyzer) : RunEvent
}

/** ap.md §8.10, §4.7: the key of a vulnerability record. `method` is the method of the method key, WITHOUT the
 *  context: one sink statement in several contexts is one vulnerability. */
data class VulnerabilityKey(val rule: RuleId, val method: CommonMethod, val statement: CommonInst)

/** ap.md §4.9: a sink edge (premise set, layer, sink facts). `facts`: REACH (the zero fact) for an unconditional sink,
 *  else a TAINT tree, so the sink facts of two witnesses of one entry merge (§4.7); as `ap-impl.md` §7.12. A normal
 *  TAINT tree can have `[any-taint]` leaves, with one exclusion for them in the tree key (ap.md §7.2): such a sink
 *  edge is normal and can be confirmed (§7.5). */
class SinkEdge(val premise: PremiseKey, val layer: Layer, val facts: Facts)

/** A sink witness (§4.7): one sink edge, or the sink edge set of a conjunctive sink (one edge per literal), of one sink
 *  alternative (`SinkRule.alternative`, §4.9) in one method key. It is confirmed as a whole (§7.5 step 2), with the
 *  support in `methodKey`. `endFacts`: the end facts of the sink (ap.md §8.10). The sink patterns of a witness are not
 *  stored: they are `SinkRule.patterns` of `alternative` of the rule at the statement in `methodKey` (the same in every
 *  run, interpreter.md I5). */
class SinkWitness(val alternative: Int, val methodKey: MethodKey, val edges: List<SinkEdge>, val run: Int,
                  val endFacts: List<PathFact> = emptyList()) {
    var confirmed: Boolean = false                                             // set only at a barrier (§7.5)
}

/** ap.md §8.10: the vulnerability records. One entry per (key, alternative, method key, run, shape); the shape is the
 *  (premise, layer, group key of the facts) list of the edges. Two witnesses of one entry merge their sink facts;
 *  witnesses of different alternatives or method keys never merge (§4.7). */
interface VulnerabilityStore {
    fun add(key: VulnerabilityKey, witness: SinkWitness)                         // concurrent (O4)
    fun witnessesOf(run: Int): Sequence<Pair<VulnerabilityKey, SinkWitness>>
}

/** §6.3. TIMEOUT: the timeout of the run. OOM: a memory guard (of the run, or of the barrier, §7.2 B4), never a JVM
 *  `OutOfMemoryError`. FAILED: a `Throwable` (an `Error` too) in a runner, in the code of the run or at the barrier, or a
 *  runner that does not stop at the join. No CANCELLED: every cancel has a known cause (§6.3). */
enum class RunStatus { COMPLETE, TIMEOUT, OOM, FAILED }

/** The result of one run. The driver reads its stores only if `status == COMPLETE` (§7.5). `demandLayerEdges`: the sum
 *  of the `counters` of its analyzers (§4.1): the demand-layer objects of the run, that is the deltas of `edges.add`
 *  and the summary deltas in the demand layer and the new demand links (§4.2 `addLink`, §4.6); 0 gives the stop rule
 *  NO_DEMAND_EDGE after a forward run (§7.1). `recordCrossings`: the record applications at a call (§4.2, §7.8). The
 *  DEMAND vulnerabilities are a state of the REPORT, not of one run: `ReportBuilder.hasDemandVulnerability`. */
class RunResult(val status: RunStatus, val analyzers: Sequence<RunMethodAnalyzer>, val runIndex: Int,
                val vulnerabilities: VulnerabilityStore, val demandLayerEdges: Long, val recordCrossings: Long)

/** §7.3, §7.4: what a complete run hands to the next run of the other direction. `demand`: the patterns of the demand
 *  edges only (the publications of the leaves that are not crossable, which the run stored, `summaries.demandEdges()`,
 *  §4.6; from a backward run also the zero-premise edges; the zero demand is implicit, §4.4); each pattern keeps the
 *  marks of its piece, with `*∖X` replaced by `*` (F72 R1), and the next run reads them (§7.3 THE MARKS OF A
 *  PATTERN). `seeds`: the sink seeds of
 *  the DEMAND vulnerabilities (forward run), or the source seeds (backward run). */
class HandOff(val demand: DemandStore, val seeds: SeedIndex)

/** §7.8: THE FRONTIER of one complete run, for the log and for `continueAfter`. Counts and method keys only, no edge.
 *  The vulnerability counts are 0 after a backward run. `demandByCause`: an option (a diagnostic), null if the AP does
 *  not count the demotions (§4.3). */
data class Frontier(
    val run: Int,
    val direction: Direction,
    val analysed: Set<MethodKey>,                    // the method keys with a non-zero initial fact
    val demandEdges: Map<MethodKey, Int>,            // the demand edges that the run hands off, per method key
    val crossableLeaves: Long,                       // the crossable summary leaves (records that replace an analysis)
    val recordCrossings: Long,                       // record applications at a call (forward and reversed records)
    val demandVulnerabilities: Int,                  // the DEMAND vulnerabilities after the run (forward)
    val confirmedVulnerabilities: Int,               // the CONFIRMED vulnerabilities after the run (forward)
    val seeds: Int,                                  // the seeds that the run hands off
    val zeroOnly: Set<MethodKey>,                    // the method keys analysed only from the zero fact (§11)
    val zeroOnlyEdges: Long,                         // the edges of those method keys: the work of the zero fact
    val demandByCause: Map<DemandCause, Long>? = null,
)

/** §4.3, §7.8: the operations that put a result into the demand layer (the option `Frontier.demandByCause`). */
enum class DemandCause { FIELD_LIMIT_CUT, MAY_TARGET, CLEANER_ROW, MUST_RECORD, DEMAND_INPUT }

/** §7.1: why the iteration ended. STOP_RULE: no DEMAND vulnerability after a complete forward run. NO_DEMAND_EDGE: a
 *  complete forward run with no demand-layer edge delta, summary delta or link (argued, §11). POLICY: `continueAfter`
 *  gave false. ABNORMAL: an
 *  incomplete run (its status), a throw in the guarded region of the driver (FAILED), or a hit of the memory guard of
 *  the barrier (OOM). */
enum class EndReason { STOP_RULE, NO_DEMAND_EDGE, POLICY, ABNORMAL }

/** §7.1: the end of the analysis. `run`, `direction`: the last run. */
data class AnalysisEnd(val status: RunStatus, val run: Int, val direction: Direction, val reason: EndReason)

enum class ReportState { CONFIRMED, DEMAND }

/** §7.5, ap.md §8.10: the report of the analysis. `Entry.run`: the run of the state (the run that confirmed it, or the
 *  latest complete forward run). `Entry.witnesses`: the witnesses of the key in that run, of every alternative and
 *  method key (the fields of ap.md §8.10: the alternative, the method key, the sink edges, `confirmed`, the end facts;
 *  §9 OUTPUT). The phase-3 output holds every entry, CONFIRMED and DEMAND (§9; ap-history.md F68). */
class Report(val entries: List<Entry>, val end: AnalysisEnd) {
    class Entry(val key: VulnerabilityKey, val state: ReportState, val run: Int, val witnesses: List<SinkWitness>)
}

/** §7.5: built from the COMPLETE forward runs only. */
class ReportBuilder {
    private val confirmed = LinkedHashMap<VulnerabilityKey, Report.Entry>()
    private var demand = LinkedHashMap<VulnerabilityKey, Report.Entry>()          // of the latest complete forward run

    /** A complete forward run, after its confirmation. Its demand set replaces the old one: refutation. */
    fun add(config: RunConfig, result: RunResult) {
        val byKey = result.vulnerabilities.witnessesOf(result.runIndex).groupBy({ it.first }, { it.second })
        val next = LinkedHashMap<VulnerabilityKey, Report.Entry>()
        for ((key, ws) in byKey)
            if (ws.any { it.confirmed }) confirmed.putIfAbsent(key, Report.Entry(key, ReportState.CONFIRMED, config.index, ws))
            else next[key] = Report.Entry(key, ReportState.DEMAND, config.index, ws)
        demand = next                                                               // one step (§7.5 step 3)
    }

    /** §1, §7.3: the DEMAND vulnerabilities after the latest complete forward run: its keys that NO complete forward
     *  run confirmed (a key that an earlier run confirmed is final, also if the latest run reports it only in the
     *  demand layer). Their witnesses of that run are the sink seeds. */
    fun demandEntries(): List<Report.Entry> = demand.values.filter { it.key !in confirmed }

    /** §7.1: false gives the stop rule STOP_RULE (no DEMAND vulnerability, so no seed). */
    fun hasDemandVulnerability(): Boolean = demandEntries().isNotEmpty()

    fun build(end: AnalysisEnd): Report = Report(confirmed.values + demandEntries(), end)
}

/** §4.3: an item of the worklist. The layer is `facts.layer`. */
data class EdgeDelta(val premise: PremiseKey, val node: CommonInst, val facts: Facts) {
    val zeroToZero: Boolean get() = premise.isZero && facts is Reach
}

/** §4.3: the worklist of one method analyzer: two queues. */
class DeltaWorklist {
    private val unchanged = ArrayDeque<EdgeDelta>()
    private var seen = HashSet<EdgeDelta>()                     // the set of `unchanged`: it discards the repetitions
    private var zeroUnchanged = 0                               // the zero-to-zero items in `unchanged`
    private val zero = ArrayDeque<EdgeDelta>()                  // `normal`: the zero-to-zero items first
    private val other = ArrayDeque<EdgeDelta>()                 // `normal`: then LIFO

    /** A delta of `edges.add`. */
    fun add(d: EdgeDelta) { if (d.zeroToZero) zero.addLast(d) else other.addLast(d) }

    /** An item of the unchanged path; false for a repeat. */
    fun addUnchanged(d: EdgeDelta): Boolean =
        seen.add(d).also { if (it) { unchanged.addLast(d); if (d.zeroToZero) zeroUnchanged++ } }

    /** `unchanged` first. When the step finds `unchanged` empty, the set goes, and a `normal` item comes next. */
    fun removeNext(): EdgeDelta {
        if (unchanged.isNotEmpty()) return unchanged.removeLast().also { if (it.zeroToZero) zeroUnchanged-- }
        if (seen.isNotEmpty()) seen = HashSet()
        return if (zero.isNotEmpty()) zero.removeLast() else other.removeLast()
    }

    val isEmpty: Boolean get() = unchanged.isEmpty() && zero.isEmpty() && other.isEmpty()
    /** A zero-to-zero item in either queue: the priority of the runner (§6.1). */
    val hasZeroWork: Boolean get() = zero.isNotEmpty() || zeroUnchanged > 0
    val size: Int get() = unchanged.size + zero.size + other.size
}

/** The counter of one run (§6.2). `onZero` is the quiescence: it sets the status by `compareAndSet(null, COMPLETE)`,
 *  so the first end of the run wins (§6.3). */
class InFlight(private val onZero: () -> Unit) {
    private val count = AtomicLong(0)
    fun beforeSend() { count.incrementAndGet() }                       // Q1
    fun afterHandler() { if (count.decrementAndGet() == 0L) onZero() } // Q2
}
```

Other names: `SharedObjects` holds the shared objects of §2. `confirm` computes steps 1 and 2 of §7.5 on the links of
a complete forward run and sets `SinkWitness.confirmed`. `handOffOf(config, result, report)` gives the `HandOff` of a
complete run by §7.3 and §7.4: `demandOf` builds the `DemandStore` of the next run from `summaries.demandEdges()` of
every analyzer (the stored publications of the leaves that are not crossable, §4.6; a backward run also gives its
zero-premise edges), with every pattern mark `*∖X` replaced by `*` (the hand-off normalization, F72 R1, §7.3), and
`seedsOf` the `SeedIndex` (a forward run: the witnesses of `report.demandEntries()`; a
backward run: the `sourceHits`). `ApOps.demandPart` (`ap-impl.md` §5.9) gives the non-crossable part of a summary
value at each summary delta (§4.6); its per-leaf forms are `cross` and `crossReversed` (`ap-impl.md` §6; Lean
`Handoff.Cross`, `Handoff.CrossB`). `nextConfig` makes the `RunConfig` of the next run. `frontierOf(config, result, handOff, report)` makes the frontier of §7.8 in one pass over the `counters` of the
analyzers (§4.1), the hand-off and the report.
`RecordStore.view` gives the read-only view of a run; `RecordStore.persist` adds the records of `ap.md` §8.7 R1 at a
barrier. `CalleeSubscriptions` and `PublicationIndex` are the path tries of §5.3 and §5.2.
`MethodContextCache.forms(key: MethodKey)` gives the cached forms of §4.8 for a method key, in both directions: the
per-method forms that every context shares and the per-key forms of that context. `RunManager.fail(status)` is the
abnormal end of §6.3: if the compare-and-set of the run status from "no end" to `status` succeeds, it cancels the
`Cancellation` and completes the run; else (after the quiescence or an earlier end) it does nothing. The timeout of the
run (`TIMEOUT`), the memory guard of the run (`OOM`), a runner exception and a throw in the code of the run on the
caller thread (`FAILED`) call it. `RunManager.run(timeout)` joins the runners on every exit (§6.3).

---


## Appendix A. Today's analyzer

The analysis of the current core (`core/opentaint-dataflow-core`, the code of `origin/main`). The design of this
document starts from it.

ENTITIES.

* `TaintAnalysisUnitRunnerManager` (one per analysis): spawns one `TaintAnalysisUnitRunner` per unit (a package; an
  unknown unit is not analyzed). It routes the work between the units and counts the events. It ends a run at
  quiescence, at a timeout or at the memory guard. The prescan and the full scan reuse it after `resetApManager`.
* `TaintAnalysisUnitRunner` (one coroutine per unit): a channel and a priority queue. The events:
  `MethodWithContext`, `ExternalInputFact` (zero, fact, side-effect requirement), `MethodAnalyzer`,
  `NewSummaryEdgeEvent`, `NewSideEffectRequirementEvent`, `NewSideEffectSummaryEvent`, `LambdaResolvedEvent`,
  `MethodAnalysisDelayed`, `DelayedAnalysisResume`.
* `NormalMethodAnalyzer` (one per `MethodEntryPoint`): the tabulation over four edge kinds (zero-to-zero,
  zero-to-fact, fact-to-fact, ND); a LIFO worklist; `MethodAnalyzerEdges` with merge and subsumption; the initial-fact
  abstraction; the fact-depth delay. The summaries, the side-effect requirements and the side-effect summaries wait
  until the worklist is empty. `EmptyMethodAnalyzer` publishes the identity summaries of the most abstract facts.
* `SummaryEdgeStorageWithSubscribers` (callee side, one per entry point, in the unit storage): one writer (the runner
  of the callee) under a monitor, and the subscribers in a `ConcurrentLinkedQueue`. The new part of an insert goes to
  every subscriber as an event.
* `SummaryEdgeSubscriptionManager` (caller side, two per runner: internal and external): registers its handler on the
  first subscription to a callee, inserts the caller fact (delta), replays the callee storage synchronously, and
  matches every delivered summary against its subscriptions when the event runs.

PIPELINES.

* A caller fact goes to the callee as an initial fact: synchronously in the same unit, as an `ExternalInputFact`
  event across units. The subscription and its replay come first.
* A summary goes from the exit of the callee to its storage, then as an event to every subscribed runner, then to the
  caller analyzers whose subscriptions match.
* The refinements travel up as side-effect requirements (the exclusion of an initial fact) and side-effect summaries
  (the mark-unfold request after `[any]`). A zero-to-fact caller with a concrete fact answers the unfold request
  directly to the method that asked.
* The counter: increment before the send, decrement after the event. Zero ends the run, or starts a deepening round
  when analyzers are delayed.

DEFECTS FOUND (none of them is in the design above).

* P3: the replay read of a callee storage takes no lock. It reads fastutil maps and plain fields while the writer
  changes them (`SummaryEdgeStorageWithSubscribers.factEdges` and the other finders; the
  `ConcurrentReadSafeInt2ObjectMap` reads). This works on x86 and HotSpot, but the Java memory model does not guarantee
  it: a replay can miss an old summary, and no notification gives it again.
* P4: the delivery index (`AccessTreeIndex.findStartsWith`, `MethodTreeAccessPathSubscription.kt`) follows literal
  accessors and misses a caller fact with `[any]` above the summary premise; the replay treats `[any]` as a wildcard.
  When a sub-storage has 10 or more entries, a summary published after the subscription is lost for that fact. The
  summaries with several premises (by base on the replay, by prefix on the delivery) and the requirements (exact
  children on the replay) also use different filters on the two paths.
* The tree summary storage does not notify when only the exclusion of a summary grows; today the requirement channel
  covers it. The new core has no such channel, so every growth must be a delta.
* `handleUnchangedStatementEdge`: the unchanged edge normally goes through `addSequentialUnchangedEdge`, which is
  correct. When the edge post-processor returns a NEW edge object, the code propagates the input edge (as a changed
  edge) and drops the processed one. `JIRMethodSummaryEdgeProcessor` returns a new object for every fact-to-fact edge
  at an exit. So a PARTIAL exit compatibility filter is lost for a fact that is unchanged at the exit; a full
  rejection (an empty list) works. Precision only. The new core has no post-processor (`interpreter.md` D14).
* Across runs: `analyzerEnqueued`, the runner `factLimit`, the sticky `status` and the counters survive. A failed
  runner cancels the shared scope. A runner that does not stop can run into the next run.
* `EventComparator` reads mutable keys. `ConcurrentReadSafeObject2IntMap` can hang the interner (non-volatile reads in
  a retry loop).

## Interpreter migration differences

## 6. Deviations from today's code

The columns "Today" use today's notation (§0).

For JVM cleaners, D20 and D23 apply after the shared resolver's action normalization
(`interpreter.md` §4.2). The normalization from `saloed/cleaner-action-normalisation`
was added on 12 October 2026. A same-position exact removal can lose its own mark
condition. A non-AnyField broad removal retains that condition. These rules do not
change the primitive cleaner or its exclusion table.

| # | Topic | Today | New | Why |
|---|---|---|---|---|
| D1 | read `x = y.f` | `y\{f} → y`, `y.f → y.f`, `y.f → x` (keep-except) | `y.* → y.*`, `y.f.* → x.*` | a read never changes an exclusion (I3) |
| D2 | self read `x = x.f` | `x\{f} → null` (refine only), `x.f → x` | `x.f.* → x.*` | no refinement |
| D3 | a fact above a micro-edge premise | the premise is refined (`SideEffectRequirement`, `refineInitial`) | unguarded `concat`: an `[any]` result in the demand layer (an `[any-taint]` fact keeps its layer, I2) | no refinement (I2) |
| D4 | mark readers at sinks, sources, cleaners, exits | `FactReader` refinement, any-field unfold requests | the request, run 1 only (§5.4) | ap.md §4.5 |
| D5 | summary application | delta with refinement (`tryApplySummaryEdge`) | guarded by satisfaction | ap.md §4.3 |
| D6 | call bindings | rebase functions (`mapMethodCallToStartFlowFact`, `mapMethodExitToReturnFlowFact`) | binding micro edges (§3.1) | one operation for every flow (I4); same mapping |
| D7 | Go cleaners | only the summary rewriter, only user-defined `RemoveMark` | `clean` at the call site for every cleaner rule, plus the rewriter | the same cleaner placement in both languages |
| D8 | selected-mark cleaner on an abstract fact | `DeepAccessorExclusion` claims; a refinement request on abstract nodes | same-base `clean`: `*∖(X ∪ {T})` and a run-1 request T, for every path and reach (F75) | ap.md §4.7 |
| D9 | reach `ExactAndAnyField` | cleans only through an `[any]` at the position | `atAndBelow` | `[any]` means "any continuation" in the new AP. The cleaner removes a mark only from a fact that lies inside the cleaned locations (ap.md §4.7), so it over-approximates |
| D10 | `RemoveAllMarks(P.AnyField)` | removes only an `[any]` child | `(P, below, all)` | the same meaning of `[any]` |
| D11 | type filter form | `FactTypeChecker` over the tree (Accept, Reject, FilterNext) | `filter(base, may)`, prefix-closed, tails kept, a separate mark policy | the ap.md §4.8 primitive; same predicate |
| D12 | `[any]` or `[any-taint]` fact with a primitive-tracking mark on a primitive base | kept as `$` | kept with its tail (`[any]` or `[any-taint]`) | the filter never changes a tail (precision only: the locations below a primitive value are not valid, I13) |
| D13 | filter of the caller content under `*` in the summary application | `AccessTree.concat` filters by the path type | none | the filter acts on bases at fixed points; precision only (Q3) |
| D14 | exit compatibility filter (`JIRMethodSummaryEdgeProcessor`) | removes `*` at incompatible fields | none | the same (Q3) |
| D16 | depth gate, `[any]` depth charge in the step | `INITIAL_ALLOWED_FACT_DEPTH`, `+10,000` | the field limit only | ap.md §4.4 |
| D17 | rules on a static position (JVM) | applied as written | the mapping of §1.4: an `[any]` or `[any-taint]` target on a class position, a pass rule from or to a class position, a `ContainsMarkOnAnyField` literal in a rule with a class target, and `RemoveAllMarks(P.AnyField)` on `S` are rule errors; `RemoveAllMarks` on a class, a static field or deeper is the kill of a strong write; the generated Spring cleanup form directly supplies the whole-base cleaner `(S, [], atAndBelow, all)` (D27) | a static access always names a field, a pass rule is a handcrafted summary, and the construction rules of I12 must hold |
| D18 | pass rule with an `AnyField` position | the content below the any-field node of `P` is copied below `Q` | an `AnyField` target (`CopyAllMarks(P → Q.AnyField)`, `CopyMark(T, P → Q.AnyField)`): the `[any]` target (§4.1), a MAY: an uncorrelated result in the demand layer (W6); in the backward run every result of its reversal is in the demand layer, also a `$` result (§4.9). It is not the `[any-taint]` target of a source (I14, D32). An `AnyField` position on the premise side: a rule error (D33) | the field that the rule writes is not known, so a correlated edge does not cover the flow; precision only |
| D19 | a `Lambda` result of the call resolver (§3.9) | a resolution failure (the unresolved path) beside the lambda methods (`JIRMethodCallResolver.kt:184-209`) | the lambda methods of the prescan only; a resolution failure only if the prescan knows no lambda | the prescan resolves every lambda before run 1 (analyzer-core.md §9); fewer findings are possible (user decision, 2026-10-07) |
| D20 | a cleaner with a mark literal in its condition (§4.2) | the condition is evaluated on the fact (`TaintFactAwareConditionEvaluator`): a negated literal counts as true, and a literal can hold on the cleaned fact | the cleaner does not act | only an unconditional cleaner is sound for a fact-local engine; more findings are possible (user decision, 2026-10-07) |
| D21 | exit sinks (§4.7) | the production rule provider applies them only on zero-premise edges (`JIRMethodExitRuleProvider.kt:18-19`) | every fact at the exit | the sink check of ap.md §4.9 on every fact; more findings are possible (user decision, 2026-10-07) |
| D22 | an unconditional exit sink | no effect (`applyUnconditionalSinks` is a stub, `JIRMethodSequentFlowFunction.kt:191-200`) | it fires on the zero fact at both exits (ap.md §4.9; D26) | the same rule as every unconditional sink; more findings are possible (user decision, 2026-10-07) |
| D23 | the summary rewriter (§3.7, §5.2) | the rule selection: every user-defined source rule and every user-defined cleaner rule whose condition is not statically false (`JIRMethodCallRuleBasedSummaryRewriter.kt:67-85`); not applied on a zero-premise summary result and on the default identity of an unresolved callee (`JIRMethodCallSummaryHandler.kt:29-38, 71-90`) | the rule selection: every user-defined source rule whose condition is not statically false, and every user-defined cleaner rule whose condition is statically true; applied on every summary result and unresolved result | rule-guided flow: the rule overrides the callee for its marks; one rule for every result of the call; a conditional cleaner does not act (as D20). Fewer findings are possible (the results), more are possible (the cleaner selection) (user decisions, 2026-10-07 and `ap-history.md` F67) |
| D24 | a pass rule (`CopyAllMarks`, `CopyMark`) with a mark literal in its condition (§4.2) | the condition is evaluated on the fact | a rule error, logged once and not rejected (§1.3); the rule applies without its mark literals; a pass rule makes no ND edge | a pass rule has no mark-dependent condition (user decision, 2026-10-08); no exact form (gap G11) |
| D25 | liveness (§2.1 step 1) | the method analyzer drops an edge whose fact is on a local that is dead at the statement, also at a call (`MethodAnalyzer.kt:296`; JVM `JIRLocalVariableReachability`; Go: no drop) | no liveness step: the statement step keeps a fact on a dead local; the alias analysis keeps its own inputs | today's liveness goes backward from the exits of the forward graph, so in code that reaches no exit (a worker loop that never returns) every local is dead and its facts go. A dead local is not read again, so elsewhere the effect is more facts only; more findings are possible (`ap-history.md` F67) |
| D26 | the unconditional exit rules at the exceptional exit (§4.7) | the unconditional exit sources fire only at the normal exit (`JIRMethodSequentFlowFunction.kt:228-233`) | the unconditional exit sources and the unconditional exit sinks fire on the zero fact at both exits; an unconditional exit sink can report at both exits (accepted) | the exit rules act at both exits: the two exits are the one virtual exit of the model (ap.md S4); expected, more findings are possible (user decision, `ap-history.md` F67) |
| D27 | the Spring dispatcher cleanup (`__cleanup__`, §1.4, §5.2) | a fact-dependent cleaner: the rule provider builds it from the fact, for a fact on `S` the action `RemoveAllMarks(ClassStatic(C))` for each class of the fact except the registry class `__spring_registry__` (`SpringRuleProvider.kt:104-143`) | the generated dispatcher saves and restores the registry around each `__cleanup__()` call: for each static field `f` of `__spring_registry__` (one field per component, `SpringWebProject.kt:160-200`), `%reg_f = __spring_registry__.f` before the call and `__spring_registry__.f = %reg_f` after it (`ndMethodDispatch`, `SpringWebProject.kt:311`). The generated `__cleanup__` method supplies the fact-free primitive cleaner `(S, [], atAndBelow, all)` through `CallCleaners`; the call builder adds it directly to the Actions stage. The current fact-based rule provider stays unchanged for the prescan and old full scan | no rule of the new analysis needs the fact; the effect is the same: the static content outside the registry goes, and the registry stays (`ap-history.md` F67) |
| D28 | an empty method (I8) | JVM: an empty method has no instruction, so no entry statement and no method key; the call enters nothing for it, and its bound facts are lost (`JMethodBoundaryInstFeature.kt:13`). Go: a resolved callee; `EmptyMethodAnalyzer` publishes the identity summary of the most abstract premise `(b, [], *, {}, *)` (`MethodAnalyzerStorage.kt:19-31`) | never a callee: the resolver drops it; a call whose every resolution result is an empty method is unresolved (§3.7: the default identity and the pass rules) | an empty method is not analysable; the unresolved path is the summary of a callee with no body; more findings are possible (an all-empty call takes the unresolved path, and the pass rules apply); a mixed call: G12 (`ap-history.md` F67) |
| D29 | a rule position with an inner or a repeated `AnyField` (§1.3) | handled by the fact readers (`FactReaderUtils.kt:54-138`) | a rule error: the interpreter rejects the whole rule (no form at any place, and the rewriter does not select it, §1.3, §5.2) and logs it once | `[any]` is a tail only (ap.md W4); fewer findings are possible (`ap-history.md` F67) |
| D30 | the global-state rule (§4.7 step 3, G2) | the exit sinks run only on zero-premise edges (`JIRMethodExitRuleProvider.kt:18-19`); the evaluated `S` facts of a REACHED exit sink are dropped (`JIRSequentTaintUtil.kt:76-85`, `JIRMethodSequentFlowFunction.kt:186-188`) | for an item whose premise is the zero fact, the evaluated `S` part goes also when a conjunctive exit sink is not complete, and stays the stored input of its literal; a caller-set `S` fact is evaluated (D21) but not dropped: it returns to the caller through the callee summary | `ap-history.md` F67 (5), F68 (3); fewer facts in the callers of the method that sets the state; the FP shapes of a caller-set state: G2 |
| D31 | an exit source with two or more positive literals in one alternative of its condition (§4.7 step 1, §5.3) | evaluated fact-locally from stored assumptions with an empty precondition (`JIRMethodSequentFlowFunction.kt:211-219`, `TaintUtil.kt:97-104, 203-207`): it fires under the premise of the fact that completes the combination | an ND edge at the exit, as at a call: each literal stores its input; a full combination is an exit item with the union of the premise sets, and at the normal exit an ND summary (E6); not a rule error | the correct premise set (today the result belongs to one fact); the findings of today stay (`ap-history.md` F68 (4)) |
| D32 | a source with an `AnyField` target (§4.1, I14): `AssignMarkOnAnyAccessor` (Go `AnyAccessor`), or `AssignMark` on `PositionWithAccess(P, AnyField)` (for example the DTO argument of a Spring entry point, `SpringRuleProvider.kt:61-76`); at a call, at the method start, at an exit or at a read, plain or conjunctive | the source makes a fact with an `[any]` accessor below `P` (`Source.kt:26`). In the new AP before F69 this fact had the `[any]` tail, so W6 put every result of it in the demand layer: a vulnerability whose taint came only from it was never confirmed (a DEMAND entry) | the target tail `[any-taint]`, a MUST, in the forward runs only (ap.md W8). Its results keep their layer, so a normal edge with it is complete, and such a vulnerability can be CONFIRMED: in run 1 when the sink reads the tainted object in the method of the source (`AnyTaintExCases2.PassRule.source_confirmed`), or after a callee whose FLOW summary keeps the whole object (program I, ap.md §6.2; `AnyTaintExCases2.I.run1_confirmed`); in a restricted run through a getter (program G, `AnyTaintExCases2.G.run3_confirmed`) and with the sink in the callee (program C, `AnyTaintExCases2.C.run3_confirmed`). These results are of the closures `AnyTaintEx.D6X` (run 1, a spec closure) and `AnyTaintEx.DRXs` (run 3 with the earlier restriction and the earlier hand-off: the record of the earlier design, ap.md §10.11); with the intersection and the hand-off of the demand edges (the spec closures `AnyTaintEx.DRX` with `HandoffX.restrictIX`) the run-3 results of programs G and C are argued (ap.md §11.2). The round-1 results of the same programs, in `AnyTaintCases`, are of `AnyTaint.D6T` and `DRT`. A strong write into the object keeps it exact with an EXCLUSION: after the setter `dto.setName(c)` the object is `(dto, ., [any-taint], {name}, T)`, normal, so `sink(dto.name)` is not reported and `sink(dto.email)` is CONFIRMED in run 1 (§2.4; program S, `AnyTaintExCases.S.run1_dto_ann`, `S.run1_name_not_reported`, `S.run1_email_confirmed`). A read through an excluded accessor gives nothing (program R), and the cleaners `atAndBelow` and `below` one accessor below the object add the accessor to the exclusion (§5.2; program CL). Only these operations make it `[any]` in the demand layer, with no exclusion (ap.md §2.2): the field-limit cut (`AnyTaintExCases.CUT.cut_reports`); a primitive AP cleaner `part` row other than the `atAndBelow` and `below` rows one accessor below the object, that is the primitive `exact` cleaner at the path of the object or below it, and a primitive cleaner two or more accessors below it (an excluded path cleans nothing; §5.2; `CL.exact_result`); F74 named field actions apply that primitive to the temporary and keep normal outside-field facts (`FieldCleanerX`); a may target (the `[any]` target of a pass rule, D18); a demand input (a demand fact, summary or record); and the must-record demotion (ap.md §4.3; `AnyTaintEx.recLayerX`). A weak update keeps the object whole (§0.1). A pass rule with an `AnyField` target keeps `[any]` (D18). The backward run has no `[any-taint]` (§4.9) | the `[any]` target of a source is a must, so W6 lost precision on it; the demotion at an exclusion (the first F69 text) lost it again at every setter (`AnyTaintExCases.S.run1T_not_confirmed`). The normal edges of run 1 are exact; in a restricted run a normal edge of a must-premise is END-EXACT; a confirmed vulnerability is real (ap.md §10.11). Every complete forward run reports every real vulnerability that no earlier forward run confirmed, in some layer (`HandoffXIter.iteration_generalNX`, with the seeds of the DEMAND entries of the report, §4.9; with the seeds of every reported vulnerability and the hand-off before F70: `AnyTaintExCov.iteration_reportsX`). The exclusion removes the reports of the excluded locations, and these are not real (`AnyTaintExCases.S.name_not_real`, `X.locations_exact`). More confirmed entries, fewer demand entries (`ap-history.md` F69) |
| D33 | a pass rule with an `AnyField` position on its premise side (§1.3, §4.1): `CopyAllMarks(P.AnyField → Q)`, `CopyMark(T, P.AnyField → Q)`, also with an `AnyField` target; Go `CopyData`, `CopyTaintMark` with `AnyAccessor` on the from position | the content below the any-field node of `P` is copied below `Q` (D18) | a rule error: the interpreter rejects the whole rule and logs it once (§1.3, as D29) | the rule reads one field that it does not know, so its result is a may; but a `$` result of an `[any]` premise keeps its layer (ap.md §4.1), so W6 cannot keep the may out of the normal layer, and a finding that rests on it could be CONFIRMED. Today's rule base has no such rule: the only `AnyField` in a pass rule is a target (Go `json.Unmarshal`, `arg(0) → arg(1).*`) (`ap-history.md` F69) |
| D34 | the summary rewriter on an `AnyField` action position of a selected source (§5.2): `AssignMarkOnAnyAccessor` on `P`, `AssignMark` on `PositionWithAccess(P, AnyField)` (Go `AnyAccessor`) | the rewriter cleans every action position with `RemoveMark(T, position, Exact)` (`JIRMethodCallRuleBasedSummaryRewriter.kt:105`); on `PositionWithAccess(P, AnyField)` that is the `below` row of §5.2. The text of §5.2 before F69 gave `clean(P, exact, T)` | `clean(P, atAndBelow, T)` | the source marks every location at or below `P` (a must, I14), so these are the rule positions that the rewriter overrides. The `exact` cleaner at `P` keeps the marks of the callee below `P`, and on an `[any-taint]` result at `P` it gives a `part` result in the demand layer (§5.2); today's `below` row keeps the mark of the callee at `P` itself (`ap-history.md` F69) |
| D35 | the removal of the entry marks at the normal exit (§4.7 step 4, G2), on a zero-premise fact on `this` or `arg(i)` | `TaintMarkRemover` (`JIRMethodSequentFlowFunction.kt:301-314`, applied at `:156`) rejects every mark accessor of the entry-mark set that it reads, but the filter reads only the children of the root node: a non-mark accessor gets `Accept`, and `Accept` keeps the whole subtree below it (`AccessTree.kt:1031-1056`, the tree form of the default `ApMode.Tree`); the mark is the last accessor of a fact path (`AccessPathCreationUtils.kt:12-21`). So only `b.$ (m)` goes; `b.f.$ (m)` and the any child `b.[any] (m)` (the `AnyField` part of the Spring DTO source) stay | every leaf with an entry mark goes, at any depth, with both tails: `(b, p, $, m)` for every path `p`, and `(b, p, [any-taint], E, m)` with its exclusion | an entry-point source must not leak into the callers in any part (G2). Since F69 the `AnyField` part of the source is a normal `[any-taint]` fact (D32), so a leak of it gives CONFIRMED false positives in the callers. Fewer findings are possible in the callers of an entry point (`ap-history.md` F69) |

Kept as today (no deviation): the rule order at entry, call and exit; the lhs kill; the alias analyses and their use;
the constructor rule; the exception rule; the conditional exit rules at both exits, with `Result` read as `exc` at the
exceptional exit (§4.7; the unconditional exit rules: D26); the set of the entry marks (§4.3; their removal at the
exit: D35); the array elements of a call sink
argument (§4.2); the primitive mark policy at the root and below each `[e]` (§5.1); the Go pointer model; no Go type
filter; unconditional Go pass rules; the summary rewriter (except D23 and D34); the `<string-bytes>` rule; the default
getter rules; no type filter in the backward run.

---
