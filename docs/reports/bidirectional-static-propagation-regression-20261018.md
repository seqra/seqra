# Static propagation regression: root-cause analysis

Date: 18 October 2026. Scope: conductor and ThingsBoard, run-1 FORWARD,
field limit 1. This is an investigation, not an implementation change.
Historical runtimes are compared by their recorded commits. The latest measured
runtime is `f18b1c5bb58e5856e4b15b3a71e09e3708dff0d1`; documentation HEAD
`a03ff2e62940f13ab1de9d194dfa524d39904a6e` has identical runtime sources.

Latest reached / completed / accepted forward on that runtime:
**conductor run-1 / none / none; ThingsBoard run-1 / none / none**.
The 60-second run-1 engine target remains failed on both.

## Finding

Source comparison and the production-code probe identify a repeated-work
regression. Historical measurements show corresponding event and traversal-
frontier growth. Static request storage became smaller, but incremental
request/input joins were replaced by repeated global graph traversals. In addition, concrete static admission became
eager and packed concrete summary delivery lost its field/path candidate filter.
The change therefore does more work in some cases, even with one mark and an
unchanged logical request edge. Smaller stored facts do not compensate for that
extra computation and its queued traversal state.

The nearest measured loss of conductor's ability to finish run-1 is
**`ab6c3cd00` → `6b9291d7d`**. The abstract FLOW packing stage completed
run-1 in 44.385 seconds. The subsequent concrete-static/need-router stage
failed in run-1. Turn batching at `f18b1c5bb` retained that failure mode.
It did not introduce the first loss of completion.

This investigation proves a repeated-work mechanism with production routing
code and connects it to measured event and heap growth. It does not assign an
exact number of project seconds to each mechanism.

## 1. Revision and workload comparison

| Stage | Runtime | Conductor run-1 engine | Conductor reached / completed / accepted forward | ThingsBoard run-1 engine | ThingsBoard reached / completed / accepted forward |
| --- | --- | --- | --- | --- | --- |
| Added-fact removal | `27e22bb1e` | 60.210 s, COMPLETE | run-2 / run-1 / run-1 | 279.104 s, OOM | run-1 / none / none |
| Request-gated statics and selective preservation | `28a123f0e` | 49.567 s, COMPLETE | run-2 / run-1 / run-1 | 176.913 s, OOM | run-1 / none / none |
| Packed abstract static FLOW | `ab6c3cd00` | 44.385 s, COMPLETE | run-2 / run-1 / run-1 | 322.250 s, OOM | run-1 / none / none |
| Concrete statics and source-owned need waves | `6b9291d7d` | 164.524 s, OOM | run-1 / none / none | 202.996 s, OOM | run-1 / none / none |
| Turn batching and equal-mark grouping | `f18b1c5bb` | 774.693 s, OOM | run-1 / none / none | 193.875 s, OOM | run-1 / none / none |

The full eight-stage evidence matrix also includes merged requests, its static-
domain correction, and frozen-plan factoring. Those stages have their own
exceptions or OOMs; the history is not a monotonic deterioration from `27e22`.
The table isolates the later loss after conductor had recovered completion.

The old and latest input audit endpoints have **41 equal dataset hashes**,
including project sources/classes, dependencies, models, extension rules and
toolchains. All 16 reviewed stage/project manifests have the same fixed
configuration, model hash, test revision and normalized runner arguments as
the old invocation. Conductor uses 8 GiB/1,080 s; ThingsBoard uses
12 GiB/1,080 s; both have the 1,200-second outer limit and direct Gradle
`runProjectAnalyzer` task. A changed project or larger heap does not explain
the difference.

These are historical diagnostic executions, not a paired throughput experiment.
Host load, form-statistics instrumentation and profile timing differ. Failed
engine times include their heap captures. In particular, current conductor's
774.693 seconds includes 16.304 seconds of capture; its remaining failed prefix
still greatly exceeds the earlier completed engine. ThingsBoard never completes
run-1 in these stages, so its shorter latest OOM time is not a speedup.

Old conductor's heap and most of its normal native CPU window belong to **run-2**.
They must not be compared with the latest run-1 heap as if they were the same
lifetime. Old ThingsBoard's heap does belong to run-1.

Primary reports: [added-fact removal](bidirectional-added-facts-removal-performance-20261015.md),
[selective preservation](bidirectional-run1-selective-preservation-20261016.md),
[abstract static FLOW](bidirectional-run1-bottom-up-static-20261017.md),
[concrete statics](bidirectional-run1-bottom-up-concrete-static-20261017.md),
and [latest batching](bidirectional-static-need-replay-batching-20261017.md).

## 2. Incremental joins became repeated global closure

Before `6b`, stored static requests provided an incremental join:

```text
fresh request delta at callee B
    -> visit matching existing caller inputs for B

fresh caller FLOW input for B
    -> join that input with B's existing requests
```

Request sets still had duplication and storage costs. However, adding one
input did not restart every static need in the program. The `ab6` implementation
of `SubscriptionManager.replay` joins the newly admitted input with the
callee's matching rows. `joinStaticRequests` similarly uses the fresh packed
relation delta. Source-history excerpts bind these old functions.

The current path is:

```text
fresh relevant call binding
    -> PendingNeedReplay.graphChanged()
    -> owner-turn flush selects ALL committed static sources
    -> one fresh wave for each equal-mark group
    -> traverse reachable static nodes and all subscriber owners again
    -> offer ordinary Base requests at the endpoints
```

See [PendingNeedReplay](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/PendingNeedReplay.kt),
[StaticNeedRouter](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/StaticNeedRouter.kt),
and [SubscriptionManager](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/SubscriptionManager.kt).
The visited set belongs to a wave. It suppresses a cycle within that wave,
but supplies no node/mark memory to the next wave. Equal-mark sharing is
limited to one flush. Full selection remains global across methods and runners.

Also, graph growth means fresh physical binding components, not a minimal
change in the logical reverse request relation. Distinct caller/call/stage ports
can map to the same original-slot endpoint. The new port is real and must retain
its transfer provenance, but its admission still restarts unrelated sources.
FLOW header changes can matter to ordinary transfer while E/mx are ignored by
request matching. No measured redundancy fraction for those header changes is
claimed here.

For a stable source and successive input additions, the changed work can be:

```text
incremental input join:       1 + 1 + ... + 1 = n
full rescan after each turn:  1 + 2 + ... + n = n(n + 1) / 2
```

More generally, repeated full selection pays for all sources and their reachable
routes at each dirty flush. Removing intermediate request rows removed a place
where the earlier solver remembered and filtered reached interest. The current
replacement pays to rediscover it. Source-once storage does not give source-once
processing.

### Production-code reproducer

An external test source runs against the current production router, pending
accumulator, subscription directories and SummaryStorage. The fixture uses legal
DEMAND static FLOW inputs and full caller keys. A separate scalar adjacency-list
oracle checks the final endpoint/mark relation. There are **three passing test
methods and ten fixed scenarios**; no canonical analyzer source changed.

| Case, one concrete request mark | Current measured result | Semantic result |
| --- | --- | --- |
| Add 64 distinct input endpoints in separate turns; do not commit endpoint R | 2,080 binding scans and 2,080 deliveries | 64 endpoints |
| Same updates; commit each endpoint to R immediately | 2,080 scans and 64 deliveries | Same 64 endpoints |
| Add 64 distinct full call ports, all with the same logical endpoint; commit R immediately | 64 graph-change records, 2,080 scans, 64 Base offers, one delivery | One endpoint, one mark |
| Add 32 disconnected bindings after a source is settled | 32 extra scans of the old source, no new delivery | No new endpoint or mark |

Sizes 8/16/32/64 give 36/136/528/2,080 scans in both endpoint-commit modes.
The quadratic scan count remains when each turn fully drains and R is committed
immediately. Thus it is independent of outstanding-wave overlap, delayed P commit,
a wide mark set, or a large project. The old `n`-scan cofactor is derived from the
old input-delta join, not an executed old-engine benchmark.

This is a protocol-level test of admitted bindings. It does not execute a JVM
CFG producer or the actual UnitRunner scheduler. Those limits do not weaken its
positive counterexample to a general processing-improvement claim.

## 3. Temporary traversal work became a major memory owner

The stable event-accounting source `InFlight.kt` is byte-identical at the old and
latest endpoints. Its handled count measures completed accepted event/Work
reservations; it does not count incomplete Work quanta or individual facts.

| Run-1 periodic observation | Old `27e22`, conductor | Latest `f18b`, conductor | Old `27e22`, ThingsBoard | Latest `f18b`, ThingsBoard |
| --- | ---: | ---: | ---: | ---: |
| Last logged handled count | 1,977,418 | 423,101,697 | 19,661,642 | 122,670,042 |
| Maximum logged in-flight count | 3,274 | 2,007,299 | 78,788 | 19,309,236 |

These are sampled observations, not exact terminal totals or true maxima. The
mix of events changed. They establish an event-volume expansion in a stable
unit, not a per-fact execution-time ratio.

The latest conductor run-1 records 59,412 full-source selections, 46,785,863
selected source rows, 4,769,287 waves, 333,253,162 static-node entries and
407,180,714 subscriber deliveries. Its 1,605 authoritative source rows retain
574,352 bytes; wave marks/visited state retains 403,418,672 bytes. Only 219
runner accumulators exist, and their pending flags/maps are empty at capture.
Detached and queued work has already left those accumulators.

Latest ThingsBoard has 1,349 source rows retaining 292,016 bytes, but 172,785
physical waves, 19,145,155 StaticNeed event headers and 9,170,898 visit objects.
Wave marks/visited state retains **3,258,710,824 bytes**. The queue/worklist view
retains 3,554,709,312 bytes; the static-event view retains 4,075,308,816 bytes.
These selections overlap and must not be added.

The latest capture has smaller selected persistent-store views. For ThingsBoard,
the older actor/runner/subscription selection is 4.933 GB and caller N2 is 1.714 GB;
the latest actor/caller selection is 0.809 GB and all conclusion/kind-group
bodies are 0.301 GB. These are independently defined, overlapping owner views
at different interrupted prefixes, not a paired net-saving calculation.
They show why a smaller persistent representation can coexist with a worse
execution: much of the new pressure is in the unprocessed traversal frontier.

The old ThingsBoard queue/channel selection is 12,756,016 bytes. The latest
queue/worklist selection has a broader root definition, so no exact byte ratio
is asserted. The large new StaticNeed population and sampled in-flight counts
independently support the queue-growth diagnosis.

Two stable contexts help exclude unrelated explanations. ThingsBoard's normal
bound sink-cache selection is 1,092,020,512 bytes old and 1,092,022,048 bytes
latest. Its IR context is 1,619,116,064 and 1,620,144,632 bytes. Its observer
selection falls from 67,253,096 to 49,484,624 bytes. These views overlap, but
neither a newly huge sink cache nor newly huge IR storage explains this project's
new wave/event population. IR optimization remains outside scope.

The current normal CPU windows place static replay/routing on 24.53% of conductor
samples and 34.90% of ThingsBoard samples; named GC stacks occur in 51.27% and
36.57%. Categories overlap and are not wall-time shares. Together with the owner
census and work counters, they support a routing/GC pressure mechanism rather
than an inference based only on elapsed time.

ThingsBoard's latest heap evidence is limited to 15 preserved tables; 38 results
were lost at tool shutdown and one auxiliary query was interrupted. All numbers
used here are in that preserved subset. No lost geometry result is reconstructed.

## 4. Delayed commit amplifies repeated endpoint traffic

Ordinary Base requests have owner-local temporary P and committed R. Static
routing checks R before sending. It cannot read another actor's mutable P.
When the same mark is already in P, the receiving owner rejects it as a duplicate,
but routing, message allocation, handling and membership checks have already run.

The unchanged EventQueue gives all non-Work events rank 1 and ordinary Work
rank 2. The new StaticNeed events therefore take priority over the Work needed
to process facts and reach method-empty. P commits to R at that method-empty
barrier. An old policy change matters here: `27e22` serviced deferred requests
at a bounded nonzero Work quantum; merged requests changed this to method-empty.
The new wave fan-out magnifies that later-commit policy.

```text
more dirty turns -> more global waves -> more rank-1 visits/Base deliveries
    -> ordinary Work is delayed -> P remains outside R longer
    -> waves see the same absent R member -> more duplicate Base deliveries
```

This is an amplification mechanism, not a proof of infinite starvation. The
production reproducer shows that fixing P latency alone would still leave
quadratic rescanning.

The closest successful stage `ab6` completed with 21,720,875 offered request mark
members and 13,614,418 P duplicates, counting both Base and static slots.
The latest failed conductor prefix records **1,243,884,931 Base-only offers**
and **1,241,699,440 Base-only P duplicates**. The per-offered-mark and old-P
membership counter increments are unchanged. Since the older total includes
Base, these imply lower bounds of **more than 57.2 times the older Base-offer
attempts**, and **more than 91 times its Base P-duplicate attempts**.
They are not runtime ratios or counts of distinct logical requests.

The current Base offer partition is exact:

```text
1,243,884,931 offered
  = 1,812,440 already in R
  + 1,241,699,440 already in P
  + 373,051 fresh P members
```

These counts include local cleaner/rule producers as well as delivered requests.
Do not attribute every P duplicate to static routing. Static-to-Base delivery is
one new amplification path; the source-checked partition proves repeated
admission work at the common boundary.

## 5. Concrete static work also expanded

This is a separate, deliberate behavioral change at `6b`, not only a packed
representation of the old workload:

- Previously, a concrete static field initial needed an actually supplied mark
  and a matching static mark request. The new emitter admits every actually
  supplied legal static mark, without that request test. It does not invent T,
  but unrequested actual marks can now run through methods and calls.
- Static-related concrete propagation now stays DEMAND. The older policy only
  made static FLOW uniformly DEMAND. Layer-sensitive callbacks and summary
  populations therefore need not be equal to the older engine.
- For non-root static premise members, packed concrete summary delivery iterates
  publications × premise members × full packed ports × every original in that
  port, then performs full matching. Nonstatic members return early; the universal
  S-root default has a whole-port fast path. Old ordinary delivery used
  CalleeSubscriptions' field/path candidate index before full matching. Large
  packed carriers can therefore be cheap to store and expensive to decode
  repeatedly.

See [Run1Abstraction](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1Abstraction.kt),
[ConcreteStaticSubscriptions](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/ConcreteStaticSubscriptions.kt),
and [the selected concrete-static contract](../../spec/run1-bottom-up-concrete-static-propagation.md).
The contract itself states that eager admission can increase work and defines
equality against a new ordinary reference, not the old admission/layer policy.
The project-wide eligible/unrequested fraction and concrete matching-loop counts
are not measured, so their exact contribution to the slowdown remains open.

The full unresolved-call observer accumulator existed before these refactors.
Its latest conductor footprint, 508 MB, is not proof of a newly introduced store.
What is new is the packed ADDED/UNRESOLVED path that decodes all originals when
the observer interface is non-null, even with disabled JVM tracking. That can
undo packing at a boundary and feed the older auxiliary store. Disabling unused
observation is useful, but it is not a complete explanation or repair of this
regression.

## 6. Why earlier proofs did not establish the expected improvement

The proofs establish specified endpoint/mark equivalence and selected finite
cost reductions. They do not establish strict improvement over `27e22` or `ab6`.

The concrete-static contract changed admission/layer behavior, then compared
packing with an ordinary expansion of that new behavior. The batching contract
compares owner-turn replay and equal-mark groups with immediate per-binding,
per-source replay. It reduces work relative to that new, expensive reference.
It does not compare global replays with the earlier incremental request join.
A fixed-graph union-of-reachability saving cannot bound the number of future
full-graph restarts. Likewise, lossless decoding does not bound how often all
entries are decoded for publications that do not match them.

Thus semantic preservation in the selected scope was insufficient as the
optimization acceptance test. The tested 64-port case preserves exactly one
endpoint/mark while increasing scans from the old input-delta cofactor's 64
to the current 2,080. That is a constructive failure of a general no-more-work
expectation, even before project scheduling and GC are involved.

## 7. Consequence for the next design

The main correction should be algorithmic:

1. Restore delta processing for late bindings and fresh interests. A new binding
   should join relevant known interest, without restarting unrelated sources.
   This requires a deliberate compact reached-interest relation, route-specific
   replay, or another proved incremental structure. Removing all intermediate
   state and then recomputing its closure is the current tradeoff to correct.
2. Preserve demand control for concrete static execution, or prove and measure
   that eager execution costs less than the request rendezvous it replaces.
   Changing admission again requires a spec change; it is not implemented here.
3. Match a concrete summary premise against the packed field tree before decoding
   unrelated originals. Use the carrier's own geometry; this does not require
   restoring the old large generic positional index.
4. Give Base admission/commit bounded progress and avoid duplicate endpoint
   delivery while P waits. Keep ownership, full caller support and completion
   accounting intact.

The proposed single-active replay cohort can contain live wave memory and merge
some turns. It is not, by itself, an incremental-work repair: the drained,
immediate-R reproducer already has quadratic scans. Its acceptance tests must
include binding-scan growth, not only a lower active-wave maximum.

Managed bound-rule values and the disabled-observer gate can recover memory
headroom. The stable ThingsBoard sink-cache/IR context shows why those changes
must not substitute for fixing the repeated routing algorithm.

Before another full project experiment, use the small protocol controls to
compare actual decoded results and processing counts for: growing input sets,
parallel ports with one endpoint, disconnected graph changes, late sources and
late edges, repeated marks, cycles, ND support and P/R handoff. Then test one
controlled implementation change at a time under the existing project settings.
No rollback, analyzer patch, new project scan or configuration variation was made
in this investigation.

## Evidence and checks

Evidence root, called N:
`/drive-testcomp/opentaint-go-rules/static-propagation-regression-20261018/`.

- `measurement-timeline/` binds the eight-stage × two-project matrix, 41 equal
  content manifests, 16 fixed configurations, periodic event counters and limits.
- `source-history-contracts/` binds 41 source versions across eight commits,
  plus the request-counter lower-bound argument.
- `probe/` contains the external Kotlin source, Gradle init script, exact command,
  Java 17 environment, source hashes, console, XML results and review.
- `probe/reviews/independent-probe-review.json` checks the legal DEMAND setup,
  three controls/ten scenarios, source equivalence and counter interpretation.

The analysis-only probe passes with zero failures/errors/skips. Its full before/
after core source maps are equal. A prior NORMAL-layer fixture control and an
initial wrapper-path failure are retained separately; neither is used as the
final legal run-1 reproduction. No analyzer process started in that wrapper-path
failure. Existing primary captures and historical results were preserved.
