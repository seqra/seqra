# Run-1 static needs: batch graph replay and merge equal-mark traversals

Date: 17 October 2026. Status: **specification; runtime unchanged**.
Scope: run-1 FORWARD, field limit 1. This selects the first optimization in
the [next-iteration proposal](../docs/design/bidirectional-next-iteration-after-concrete-statics-20261017.md).
It changes scheduling and traversal sharing, not the static analysis rules.
Follow [proof-guide.md](proof-guide.md). The
[proof dossier](run1-static-need-replay-batching-proofs.md) separates checked
finite models from the required runtime refinements.

The measured runtime is `6b9291d7dd69ae5f6a1260c7f1021c7500fa56b8`.
Latest reached / completed / accepted forward: OWASP run-8 / run-7 / run-7;
conductor run-1 / none / none; ThingsBoard run-1 / none / none;
Stirling-PDF run-2 / run-1 / run-1. No new project run measures this design.
The [current report](../docs/reports/bidirectional-run1-bottom-up-concrete-static-20261017.md)
has the measurements and failed resource/acceptance gates.

## 1. Purpose and reference behavior

ThingsBoard run-1 retained 118 static need source rows, but 688,637 live
traversals and 21,306,795 StaticNeed event objects. Their visited/mark owner
union retained 3,273,456,144 bytes. The corresponding conductor run-1 union
retained 2,115,163,776 bytes. These scopes overlap queued payloads; do not add
them. The problem is repeated temporary work, not a large source catalog.

| Operation | Current | New | Expected benefit |
| --- | --- | --- | --- |
| Fresh call FLOW binding | Immediately replay every committed source | Record graph growth; replay once at the end of the runner turn | Several binding additions can share one replay |
| Source commit | Immediately start a traversal for its fresh marks | Commit now; collect its fresh marks for the same turn flush | Repeated source deltas can share one start |
| Equal selected mark sets | One traversal and visited set per source | One traversal from all equal-mark starts, with one visited set | Shared upstream routes are scanned once |
| Different mark sets | Separate traversals | Separate traversals | Preserve the exact endpoint/mark relation |
| Later graph growth | Fresh traversals | Fresh traversals in the later turn | Preserve late routes, including routes behind an old visited node |

The ordinary semantic reference is the expanded static-need propagation in
[concrete static propagation](run1-bottom-up-concrete-static-propagation.md),
sections 4, 5 and 8. A source's marks follow every matching actual abstract
FLOW input backward until an original nonstatic base. This reference uses
virtual intermediate request states; the runtime must not retain these states.

The immediate full-source replay in that document's section 5 is the
**scheduling reference**. This specification replaces only that section's
source-delta dispatch, graph-change dispatch and per-source traversal policy.
The need producer, actual-input geometry, source union, Base request protocol,
actual concrete transport, layers, summaries and observations keep their rules.

This contract does not select queue fairness changes, request-envelope
coalescing, a FLOW-only subscriber directory, a cache change, selective
edge-only replay, or a global joined replay epoch. Those need separate work.

## 2. Data and graph meaning

The call graph is resolved during prescan. The graph here is different:
analysis derives actual abstract FLOW input bindings at individual calls.

```kotlin
data class NeedNode(val method: MethodKey, val field: StaticField)
data class BaseEndpoint(val method: MethodKey, val base: NonStaticBase)
typealias SourceRows = Map<NeedNode, RequestMarkSet>
typealias LocalStaticRows = Map<StaticField, RequestMarkSet>

// The graph is logical. Existing caller-owned subscription directories hold it.
// A reverse edge starts at a callee's original static-field slot.
sealed interface NeedTarget {
    data class Static(val node: NeedNode) : NeedTarget
    data class Base(val endpoint: BaseEndpoint) : NeedTarget
}
// NeedNode(callee, f) -> NeedTarget(caller, original)
```

MethodKey includes method, entry and context. A stored binding retains full
caller/callee keys, call statement, call-plan stages, caller original premise,
current fact and layer/support. There is no second copied binding graph.
The snippets use semantic type names: NeedNode corresponds to the current
StaticNeedKey, and NeedWave to StaticNeedWave. Reuse those runtime types;
multi-start routing does not require a second node or wave representation.

An edge exists only if an actually admitted abstract FLOW input geometrically
supplies the callee field. An original static g yields a continuation at g;
an original nonstatic b yields a terminal Base endpoint b. Packed static
identity relations supply the same-field case. Include ordinary cross-field
and nonstatic-to-static FLOW bindings. Concrete TAINT, ZERO and killed inputs
are not reverse request routes. A binding that supplies several legal fields
must cover all those fields. Requests ignore field E and FLOW mx.

The route relation grows within this run. Local kills decide which inputs
are actually admitted; they do not delete old admitted call bindings. Release
the directories only after the run's owners join. If an implementation needs
to retract an admitted route, this grow-only protocol is insufficient.

The only authoritative static interest map is:

```kotlin
sources: Map<NeedNode, RequestMarkSet> // cumulative union at each source
```

RequestMarkSet is an immutable, normalized set of concrete mark IDs. Equality
means equal contents, not equal object identity or equal hash alone. The
existing sorted/deduplicated representation and content equals/hashCode can
serve this requirement. Do not intern temporary sets in the AP manager.

All run-1 initial facts retain the nine-family grammar in the concrete-static
contract. No S-root initial, abstract class initial, [any] initial, concrete
static-root initial or nonempty initial E/mx is introduced. All static-related
FLOW and concrete propagation stays DEMAND. A need is interest, never actual
T, a satisfied source/sink condition or an ND witness.

## 3. Two distinct barriers

A **method-empty barrier** commits that method's accumulated requests and
static source marks. A **runner turn** handles one admitted event, or executes
one Work quantum. A unit can own several methods. The two barriers differ.

```text
method operations
    -> method queue becomes empty
    -> commit static source unions and ordinary Base requests
    -> finish current runner event or Work quantum
    -> flush pending static replay
    -> release the event reservation, or requeue incomplete Work
```

Do not commit source marks early just because a Work quantum ended. An
incomplete quantum can still register many bindings and must flush their
replay before its Work event is requeued. Waiting for a method, unit or run
queue to become empty is not the graph-replay barrier selected here.

Each runner has one mutable, owner-local accumulator:

```kotlin
class PendingNeedReplay {
    var graphGrew = false
    val sourceDeltas = LinkedHashMap<NeedNode, RequestMarkSet>()
    fun isEmpty() = !graphGrew && sourceDeltas.isEmpty()
}
```

This is temporary dispatch work. It is not another authoritative source
store, an inherited static request store, or ordinary RequestStorage. Reset
it at the turn flush. It can hold at most one union per source changed in
that turn, plus one graph-growth flag. No arbitrary pending-memory cap is
part of the contract.

## 4. Registration and commit protocol

### 4.1 Actual binding admission

Run binding admission under its existing caller owner. Mark the owner dirty
only after a fresh relevant binding component is visible in its directory.
Duplicate admission does not require another dirty notification.

```kotlin
fun admitBinding(input: Input) {
    val fresh = existingDirectory.admit(input) ?: return
    // Preserve existing actual-entry delivery and summary replay.
    if (freshAddsStaticNeedRoute(fresh)) {
        runner.pendingNeedReplay.graphGrew = true
    }
    existingInputProcessing(fresh)
}
```

For packed abstract static relations, every nonempty fresh relation delta
can add a route. For ordinary subscriptions, preserve the current eligibility:
fresh current FLOW with static base and a legal abstract original run-1 slot.
New marks in concrete static inputs do not alone change this mark-independent
graph. Keep actual call stages and broad/class geometry in the existing
binding matcher. Do not replace it with a prescan call edge.

A fresh directory component can repeat a logical node-to-node edge already
supported by another call port. It can still mark the turn dirty. No new
global index is needed to recognize unique logical graph edges. The required
rule is that every newly available route causes a flush, not that every
dirty notification proves a new distinct node pair.

### 4.2 Static source commit

At the existing method-empty barrier, union offered marks into the source
map under the router lock. Return only the fresh marks. Then record these
fresh marks in the current runner's accumulator. Do not dispatch yet.

```kotlin
fun commitStaticSources(method: MethodKey, offered: LocalStaticRows) {
    val freshRows = router.commitSourceUnions(method, offered)
    // The source map is committed before these deltas become pending.
    for ((source, fresh) in freshRows) {
        runner.pendingNeedReplay.sourceDeltas.union(source, fresh)
    }
}

// Under the source-map lock, for each source:
val fresh = offeredMarks - oldMarks
if (!fresh.isEmpty) sources[source] = oldMarks union fresh
// Return no row for an empty fresh set. Dispatch only after releasing the lock.
```

Multiple fresh deltas for the same source in one turn merge by set union.
Keep source-change diagnostic hooks once per actual fresh commit; equal-mark
grouping must not replace source identity or lose those observations. Hooks
must not run actor logic inline.

Ordinary Base requests still use temporary P, then
`summaryStorage.commit(temporary); temporary = RequestStorage()` at the
method-empty barrier. P is not a committed answer store. Static-origin
reentry from an ordinary Base join goes to the local static source builder,
then follows this same commit/flush protocol. It never becomes an ordinary
static request row.

### 4.3 Turn flush and selection

```kotlin
fun finishRunnerTurn() {
    val batch = pendingNeedReplay
    pendingNeedReplay = PendingNeedReplay()
    if (!batch.graphGrew && batch.sourceDeltas.isEmpty()) return

    val starts: Map<NeedNode, RequestMarkSet> =
        if (batch.graphGrew) router.committedSourcesSnapshot()
        else batch.sourceDeltas

    router.replayEqualMarkGroups(starts)
    check(pendingNeedReplay.isEmpty()) // replay only queues child visits
}

// Place this after either handle(event) or actor.work(quantum):
val complete = handleEventOrWorkQuantum(event)
finishRunnerTurn()
if (complete) releaseParentReservation()
else requeueWorkWithItsExistingReservation(event)
```

If the graph grew, the cumulative source snapshot already includes this
turn's committed deltas. Do not replay those deltas a second time. If the
graph did not grow, select only this turn's unioned fresh deltas. An empty
source map needs no traversal even if the graph grew; a later source delta
will traverse the stored routes.

The source snapshot is atomic under the source-map lock and contains
immutable mark-set values. Queue root visits after releasing that lock.
Actual bindings are read **live by their owners**; no atomic global graph
snapshot is required. Each runner owns its own resettable flag and deltas.
One runner cannot clear another runner's pending work. Router APIs must take
an explicit owner recorder or return deltas to the owner; do not infer the
owner from a source MethodKey or coroutine thread identity.

Inline same-unit LinkIn handling belongs to the enclosing turn. Cross-unit
handling has its own admitted turn. Root replay only snapshots metadata and
posts visits; it must not execute those visits or commit sources inline.
Thus the reset accumulator remains empty during the flush. If future code
introduces reentrant mutations, it must provide a proved drain protocol before
the parent may complete; the check is not permission to discard new work.

## 5. Exact equal-mark grouping and traversal

Group the **selected** starts by mark-set content. In a graph-growth batch
these are cumulative sets. In a source-only batch these are unioned deltas.

```kotlin
fun replayEqualMarkGroups(starts: Map<NeedNode, RequestMarkSet>) {
    val groups = LinkedHashMap<RequestMarkSet, MutableList<NeedNode>>()
    for ((source, marks) in starts) {
        cancellation.checkpoint()
        if (!marks.isEmpty) groups.getOrPut(marks) { ArrayList() }.add(source)
    }
    for ((marks, roots) in groups) {
        cancellation.checkpoint()
        val wave = NeedWave(marks) // one immutable set; fresh shared visited
        for (root in roots) forward(wave, root)
    }
}

fun forward(wave: NeedWave, node: NeedNode) {
    cancellation.checkpoint()
    if (wave.enterOnce(node)) {
        summaryStorage(node.method).notifyStaticNeed(Visit(node, wave))
    }
}
```

`enterOnce` is atomic across runners. Its key is the full MethodKey and
original static field. The successful entry dispatches to **all** current
subscribed owner runners through the existing SummaryStorage subscriber
snapshot. Do not apply the shared visited check separately at each receiver:
that would let the first subscriber suppress the others.

Each recipient scans its actual matching FLOW bindings, preserving the
current full-port checks. For each original target:

```kotlin
when (target) {
    is StaticOriginal -> forward(visit.wave, target.node)
    is BaseOriginal -> offerOrdinaryBaseRequest(target.endpoint, visit.marks)
}
```

Keep the current terminal protocol, including subtraction of already
committed R and counted queued delivery to the Base owner. Different static
nodes can offer the same Base endpoint more than once; this change does not
add a global endpoint deduplication table. R/P unions remain authoritative.

Different mark sets always have different waves, even if their routes overlap.
For example, disconnected A/{T} and B/{U} cannot become starts A,B/{T,U}:
that would invent A/U and B/T interests. Equal hashes also do not permit
sharing. Equal immutable contents do, even if represented by different objects.

Every nonempty group in every flush gets fresh visited state. Never reuse a
visited set from an earlier flush, including an active flush on another
runner with an equal mark set. A batch ID is diagnostic `(runner, turn)`
identity, not a global graph version. Existing traversals can overlap; this
proposal does not cancel them or join them into a permanent global wave.

## 6. Incremental cases and examples

| Arrival pattern | Required action |
| --- | --- |
| Several bindings in one turn | Store all; one dirty flag; one full-source selection at flush |
| Sources and bindings in the same turn | Commit sources; select the full snapshot once |
| Several source commits, no graph change | Union fresh marks per source; group equal resulting deltas |
| Source exists before binding | Binding's turn replays committed sources with fresh visited sets |
| Binding exists before source | Source's delta batch traverses the existing route |
| Source commits after another runner's snapshot | The source's own turn schedules its delta; it was not cleared by the other runner |
| Binding arrives behind an already visited node | Binding's own turn schedules a fresh full-source replay |
| Subscriber appears after a node's recipient snapshot | Its actual binding admission marks its owner dirty and starts a later replay |
| Two runners flush concurrently | Both batches remain valid; duplicate interest can merge at R/P |
| Cycle or self-loop | One entry per static node per wave; later batches can revisit it |
| Incomplete Work quantum | Flush now, then requeue the existing Work reservation |
| No actual concrete T | Requests can grow; concrete checks remain unsatisfied |

### Bootstrap

```java
A(p) { C.f = p; B(); }
B()  { check ContainsMark(C.f, T); }
```

```text
1. A derives actual abstract p -> C.f and registers that input at B's call.
2. A records graph growth in its current runner turn.
3. B's reached FLOW check records a local static need for f/T.
4. At B's method-empty barrier, commit source (B,f)/{T}.
5. A graph-growth flush sees that source, or B's later delta flush sees
   A's binding. If an old wave already passed B, the new wave is fresh.
6. Reverse routing reaches A's original p and offers ordinary p/T.
7. Ordinary request propagation/concretization can supply actual p/T.
   Only that actual fact can supply C.f/T and satisfy B's check.
```

### Shared route

```text
source A/{T,U} --+--> X --> Y --> Base(p)
source B/{T,U} --+
source C/{T}   ------> X
```

The selected A and B starts share one wave; X and Y are visited once in
that wave. C keeps its own wave. A/B source records stay separate. If a
later binding adds `Y -> Base(q)`, a fresh graph-growth batch reaches q.
An old visited(Y) cannot suppress it.

## 7. Reservations, completion, failure and release

Pending replay is covered by the reservation of the currently executing
turn. It needs no separate replay event or independent work token, because
the turn always flushes synchronously before release or requeue. Taking the
accumulator into a local variable does not end that ownership.

Every root notification, subsequent static visit and terminal Base delivery
must secure its child reservation before its parent releases. A node can
have no subscribers; then no child is necessary. If a later binding creates
a subscriber/route, that binding's still-counted turn starts a fresh replay.

Normal completion requires all admitted turns, pending replay batches,
deferred notifications, route visits, Base request work and actual replays
to be covered until they finish. Production ProtocolSteps is inline. Test
adapters that defer callbacks must treat them as pending work and flush any
owner turn they execute. An empty actor or unit queue alone is insufficient.

On cancellation, timeout, OOM, rejected required delivery or exception, do
not declare COMPLETE. Preserve the existing terminal-status rules. Do not
flush from a `finally` block after cancellation, or silently clear a failed
batch and then complete. Keep cancellation checkpoints in source snapshots,
grouping, root dispatch, visit scans and endpoint loops.

After owners successfully join, clear pending accumulators, queues,
subscription directories and the router source map under the existing run
release policy. A detached batch and its grouping lists need no durable
owner after root dispatch; waves stay reachable only through their queued
or executing visits. No grouping cache or completed-wave history survives
the batch. Run-2 and later create no run-1 need router.

## 8. Equality target and proof obligations

For a finite admitted route graph G and committed source map S, define:

```text
Need(G,S) = { (endpoint, T) |
    there is a source s with T in S[s], and
    a finite reverse-binding path from s to endpoint }
```

The path can have zero static relay edges, then an actual terminal binding.
It cannot invent a Base endpoint, mark, call binding or concrete fact.

Define the router's candidate relation **before** subtraction of already
committed R at Base endpoints. A batch can represent those candidates
symbolically; it need not enumerate a message for each mark. At a successfully
joined complete run-1, the union of these logical candidates must equal
Need(Gfinal,Sfinal), as must the immediate per-source reference. This is set
equality for complete owner/base/mark keys, not equal message counts. Actual
deliveries may omit candidates already in R. Final R also contains unrelated
ordinary Base needs, so neither deliveries nor final R are equated to Need.
Subtracting an R member is a semantic stutter: the requested member is already
committed and can answer through the unchanged protocol.
Under the unchanged ordinary transfer/commit interfaces, both have the same
least coupled closure of Base requests, actual initials, source interests,
summaries and semantic source/sink observations. Preserve semantic rule
occurrences and their full program identities. Only batching-dependent
diagnostic callback counts, work counts, order and timing may differ.
Do not use this allowance to ignore a report-location or rule-occurrence
regression. Timeout/OOM prefixes need not have equal partial output.

The required constructive arguments are:

1. **Source exactness:** commit returns exactly offered-minus-old, cumulative
   source membership is old-or-offered, and per-turn delta union invents no mark.
2. **Selection coverage:** full selection subsumes the same turn's deltas;
   source-only selection covers every fresh mark once modulo set union.
3. **Multi-start equivalence:** for one exact mark set M, reachability from
   a set of starts is the union of per-start reachability. Produce a source
   and finite binding path for every emitted endpoint/mark and conversely.
4. **Equal-set grouping:** partition by content equality preserves the entire
   endpoint/mark relation. Give a counterexample for grouping unequal sets.
5. **Live graph soundness:** every visited edge is an actual admitted binding,
   so each output has a path in the final grow-only graph.
6. **Late-update completeness:** for any required finite path and source mark,
   choose the latest insertion among its edges and that mark. Its turn's
   flush starts a wave after all those inputs exist, with that mark. Every
   recipient and static continuation is serviced, so the endpoint is reached.
   Include sources after snapshots, new subscribers, cycles and overlapping
   batches. Do not assume an atomic global graph snapshot.
7. **Accounted completion:** pending replay retains its parent reservation;
   all children are admitted before release. Incomplete Work also flushes.
   A failed send/cancellation cannot select a successful completion.
8. **Semantic composition:** routing produces interest only. Ordinary Base
   reentry, actual concretization, strong kills, NNF source/sink evaluation,
   whole ND support, sticky DEMAND and NORMAL persistence are unchanged.
9. **Finite traversal:** each static node enters a wave at most once; every
   finite path has an executable witness. This does not prove termination
   of an arbitrary whole-project analyzer run.
10. **Cost:** prove a scoped work reduction, with explicit no-gain cases;
    distinguish finite graph visits from JVM bytes and elapsed engine time.

The proof dossier must identify checked theorems and remaining adapters.
Do not call a snapshot/set theorem a proof of Kotlin queue scheduling.

## 9. Cost model and expected improvement

For one fixed finite graph, let selected source s carry M(s), and let V(s)
be its reachable static nodes. A node weight w(v) can represent its subscriber
fan-out plus actual binding-scan cost. Let D be the number of distinct nonempty
selected mark sets, and S the number of nonempty selected source rows.

```text
per-source waves: S
grouped waves:    D <= S

per-source node work = sum_s sum_(v in V(s)) w(v)
grouped node work    = sum_M sum_(v in union_(s:M(s)=M) V(s)) w(v)
grouped node work   <= per-source node work
```

Strict improvement requires equal selected mark sets and shared routes with
positive weight. Distinct mark sets or disjoint routes can give no node-scan
saving. Grouping itself has map-operation and equality costs. The existing
sets cache their content hash, but equality can scan compared mark contents.
Charge the number of comparisons times their actual compared lengths;
unequal colliding hashes can cause many comparisons. No linear worst-case
grouping bound is claimed for an arbitrary hash-map collision pattern.

For g relevant binding additions handled in b owner turns, graph-growth
replay selections fall from g to b, with b <= g. With a stable nonempty source
map and a common already reachable route of weight W, repeating that common
work falls from g*W to b*W, before equal-mark sharing. This is a practical
cost family; if every addition has its own turn, b = g and batching gives
no reduction. Source-only delta union can also scan a shared route once
instead of once per commit within that turn.

An upper bound for the same finite graph-growth cohort uses the final graph
and final source map for each replay. It is not a claim that every live
interleaving has lower total cost than another interleaving. Batching can
change when requests enable other analyzer work.

This design does not cap simultaneously live batches. In the worst case,
each turn changes one binding and equal sets have little overlap; repeated
full-source replay remains expensive. No whole-project asymptotic bound,
peak-memory saving or 60-second guarantee follows from these local theorems.
Measure the result before selecting a global epoch or an edge-delta index.

## 10. Runtime refinement and implementation order

The following is an implementation plan, not an analyzer patch:

1. Add the constructive finite model and negative controls. Check the
   equal-mark multi-start law, update batching, late arrivals and scoped costs.
2. Add owner-local PendingNeedReplay to UnitRunner and an explicit owner
   recorder to its ports. Keep one shared authoritative source map in the
   router. Split source union from dispatch so commit can return fresh rows.
3. Replace the two immediate graphChanged calls in SubscriptionManager with
   owner-local graph-growth recording after fresh ordinary/packed admission.
   Preserve actual-input replay/delivery and duplicate admission behavior.
4. Record source deltas from RunMethodAnalyzer's existing method-empty source
   commit. Flush once at UnitRunner.runLoop's common point after event/Work
   handling, before both afterHandler and incomplete-Work requeue.
5. Group selected sets by RequestMarkSet content and start one shared wave per
   group. Keep the shared enter-once check before SummaryStorage fan-out.
   Preserve counted owner messages and the current terminal request path.
6. Update deterministic fixtures to represent turns and pending flush work.
   Compare against an independently expanded scalar request oracle. Do not
   make the oracle call the optimized selection/grouping helper.
7. Run the focused pipeline, scheduling, common/JVM and application checks.
   Resolve semantic regressions before fresh project measurements. Then use
   the existing project configurations and runProjectAnalyzer task.

| Required case | Expected evidence |
| --- | --- |
| Duplicate binding/source offer | No new source delta or replay flag for a pure duplicate |
| Many bindings in one Work quantum | One full-source selection, after every binding is registered |
| Incomplete quantum | Replay is posted before Work requeue; reservation stays live |
| Source plus graph growth in one turn | One full selection; no extra delta wave |
| Same-source deltas in one turn | Exact union before grouping |
| Equal sets in distinct objects; hash collision | First shares a wave; unequal colliding sets stay separate |
| Unequal overlapping sets | No source/mark cross product |
| Multiple subscriber runners at one node | Every subscribed owner scans its bindings |
| Late binding behind visited; late subscriber | Fresh later batch reaches the new endpoint |
| Two owner turns interleave snapshot/commit/reset | Neither owner's pending updates are lost |
| Cycles, diamonds and several roots | Exact endpoint union; one static entry per node per wave |
| W bootstrap and Base-to-static reentry | Same required nonstatic requests; actual T still required |
| Excluded/cleaned/killed T; source and sink NNF; ND | Interests do not bypass ordinary evidence checks |
| Final turn; rejected child; cancellation during flush | No premature COMPLETE; joined release clears transient owners |
| Run-2 | No new run-1 replay state or behavior |

## 11. Measurement and acceptance

Collect separate run-1 counters for fresh binding changes, dirty turns,
source commits/deltas, full-source versus delta selections, selected rows,
distinct mark-set groups, roots, waves, live-wave maximum, static node entries,
actual binding scans, subscriber deliveries, Base offers and committed Base
members. Count request/source-reentry envelopes separately from Base-only
rows. Include grouping cost and first-pending-to-flush latency; do not confuse
this with method-empty source-commit latency. Counters must not retain per-wave
history. Batch IDs alone must not keep waves alive.

Check exact complete-run endpoint and semantic observations against the
reference. Measure the same OWASP, conductor, ThingsBoard and Stirling-PDF
configurations. Every report must include latest reached, completed and
accepted forward iteration, run-1 engine time, terminal status and source
revision. Keep prescan, report barriers and heap-dump time distinct. Reuse
the baseline's location and semantic occurrence checks; permit only the
already agreed batching-dependent diagnostic-count changes.

The performance target remains **run-1 engine completion within 60 seconds**.
The model does not establish that target. Compare live waves, queue owners,
visited sets, allocation and CPU with the current measured runtime. A lower
source-row count alone cannot validate this optimization.
