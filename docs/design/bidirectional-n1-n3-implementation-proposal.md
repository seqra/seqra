# N1 and N2: implementation and validation plan

Status: N1 and N2 implemented; code checks pass; fresh project scans complete with three resource failures. N3 deferred.
Date: 13 October 2026. Runtime implementation ends at `314349718`.
This approved design refines N1 and N2 in the [historical memory proposal](bidirectional-core-optimization-20261013.md).
The [fresh report](../reports/bidirectional-n1-n2-performance-20261014.md) and
[new proposal](bidirectional-core-optimization-20261014.md) supply post-implementation evidence and priorities.
The [N3 design](bidirectional-n3-deferred-proposal.md) is retained for future reconsideration if the queue pattern persists.
The [analysis](bidirectional-n1-n3-analysis.md) explains why storage packing alone is insufficient.
The older O1/O2/O3 proposal is historical and does not set these priorities.

Use two changes: factor the standing request relation and make its joins selective; replace subscription
delta history with cumulative entries and stable E6 bindings. Compact the state that remains after these changes.
Keep request transport, reservation accounting, runner scheduling and queue layout unchanged in this iteration.

The changes preserve all logical requests, accepted subscription leaves and required analysis effects.
They remove redundant subscription history, request storage objects and unsuccessful join scans.
They do not merge caller contexts or cap valid state.
This document records the approved design. Its descriptions of the old/current layout refer to the
pre-iteration runtime below. The [implementation report](../reports/bidirectional-n1-n2-implementation-20261013.md)
records the implementation commits and verified checks. The fresh report gives current project measurements.

## Behavior changes at a glance

| Proposal | Current behavior | Proposed behavior | Why it should improve |
| --- | --- | --- | --- |
| N1: grouped standing requests and selective joins | Store each origin/request-kind pair in a seen set and a path index. Check geometrically overlapping requests even when their mark cannot produce an action. | Store marks under one full-origin row and position origins under one full-path row. Index each row once. A concrete mark probes only that mark; abstract marks skip excluded requests. Keep requests active for later links. | Fewer object/hash/index entries and fewer unsuccessful join checks. The benefit depends on members per row; the logical request count stays the same. |
| N2: cumulative subscriptions and stable E6 bindings | Retain each accepted growth delta and its index entries alongside cumulative groups. E6 retains matched Subscription values, including their Facts versions. | Keep one current cumulative entry per full caller reference and exact ordinary group, replay each fresh delta transiently, and use one positional directory. Remove the unused accumulator index. After full matching, E6 stores full caller-reference/link-layer bindings. Keep the separate zero-only policy. | Stop retaining subscription growth history and avoid moving it into E6. Storage follows exact groups, positions and distinct matched bindings rather than every growth step. |

For example, 100 requested marks for one origin become one row with 100 memberships in N1. If one
subscription group grows 100 times, N2 keeps its current value rather than 100 permanent delta records.
These are shape examples, not measured savings. N3 is excluded from this iteration.

Both selected changes aim to preserve complete analysis results and required effects. N1 preserves encounter
order in its first version. No scheduler change is included. The equivalence gates below must pass before
implementation can be accepted; no measured saving or unchanged incomplete report is promised.

## 1. Evidence and scope

The pre-iteration measurement runtime is `38a400c0d`; the scan manifest revision is `bd6810823`. Commit `23f25e8a8` changes
documentation only. The [rollback report](../reports/bidirectional-core-rollback-20261013.md) gives the
reviewed historical evidence and the fixed project settings. These prior results are not the fresh release
gate. Both prior and current batches reach the iterations below. Use the fresh report for the current
`61fd97acd` timings and owners; the owner estimates in this design remain pre-iteration evidence.

| Project | Latest reached iteration | Last complete engine | Last accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-3, forward, limit 3, COMPLETE | run-3 | run-3 |
| Conductor | run-2, backward, limit 2, OOM | run-1 | run-1 |
| ThingsBoard | run-1, forward, limit 1, OOM | None | None |
| Stirling-PDF | run-4, backward, limit 4, TIMEOUT | run-3 | run-3 |

The pre-iteration Conductor heap captures **run-2, backward, limit 2** before cancellation. The pre-iteration ThingsBoard
heap captures **run-1, forward, limit 1** before cancellation. PDF's older checkpoint heaps capture run-3;
they do not measure its latest run-4. Run-1 starts at limit 1. There is no limit-0 case.

| Priority | Measured owner | Design target | Limit of the estimate |
| --- | --- | --- | --- |
| N1 | ThingsBoard run-1: 24,981,269 distinct standing pairs; 3,095,473,728 retained-union bytes | Remove boxed membership/index objects; index headers once; avoid impossible mark joins | Mark/position split and row density are not yet measured |
| N2 | Conductor run-2: 2,165,026 accepted-part entries; 679,558 cumulative groups; 1,538,287,888 active-subscription union bytes | Stop retaining growth history and its index; remove the unused accumulator index | Stable group storage and necessary AP graphs remain |

These unions overlap. They are owner sizes, not additive savings. Different progress between captures also
prevents an isolated byte comparison. The older small duplicate sample does not give a global duplicate rate.

Keep the AP exclusion table, D23, must/exact separation, full layers, support, proof scope and hand-offs.
Keep NNF for **source and sink** rules, signed folding including `P AND NOT P -> false`, and ordered cleaner
normalization from `saloed/cleaner-action-normalisation`. The IR and its storage/providers are out of scope.
Keep the restored `SoftReferenceManager` and direct validation. N11/N12 remain optional future designs;
none of these changes requires a manager rewrite, weak identity cache or global interner.

## 2. Shared design rules

Each method actor owns N1. Each caller runner owns N2. Keep those ownership boundaries.
Do not let producers read or mutate actor-owned request stores.
Use run-owned or store-owned directories. Release them with their owners after the existing join/freeze.

Use exact keys first. A coarse path index selects candidates; the current full AP matcher decides effects.
An `InitialAp` ID is scoped to its AP manager. Do not use it as a process-wide identity or allocate an array
up to the largest ID. Use a dense local handle directory with one strong initial reference per distinct
header. Path keys use full canonical paths under the current AP ownership. Do not add another truncation.

Use primitive mutable memberships with empty, singleton and sparse forms. Promote only when needed.
Choose the dense form after the census. Mark IDs need not be positive or dense: a bitmap needs bounded
local ordinals. Do not call immutable `MarkSet.plus` and intern a new set on every mutable insertion.
Account for object headers, capacity, lookup tables and promotion copies when comparing layouts.

Keep cancellation checkpoints in long scans, matching and combination expansion. Callbacks can cause local
re-entry. Capture the candidate handles and the required immutable values before calling them; do not expose
live mutable iterators to callbacks. This iteration introduces no shared request mailbox.

## 3. N1 — one grouped standing relation

### 3.1 Current behavior that must remain

[RequestStore](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/RequestStore.kt)
shares each Pair between a seen hash set and its positional index. The large cost is the Pair, hash entries,
linked entries and capacity. This is processed standing state, not a queue.
[RunMethodLocalServices](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt)
joins a new request with old links and a new link with old requests.

Preserve both arrival orders and the exact `add` result. Keep a request after an answer. Later deeper facts,
static answers and new full caller references can give new effects. Preserve the current initial validation
and the separate mark and position cases in
[Demand](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Demand.kt).

### 3.2 Stored layout and internal API

| Row | Exact membership | Positional index key |
| --- | --- | --- |
| Mark row, keyed by full origin `InitialAp` | Requested mark ID -> primitive member handle | Origin base and origin path |
| Position row, keyed by full requested path | Full origin initial -> dense local member handle | `STATIC` and requested path |

A membership is the canonical representation of `(origin, kind)`. It is the seen relation; do not retain a
second Pair hash set. A position row must retain each origin, even though those origins share an index path.
Assign stable row handles. Index a row once. Store the directory, primitive member data and exact membership
lookup inside `RequestStore`. Keep mutable membership sets local; do not make them an analysis-wide cache.

Keep the existing external `add` and raw `overlapping` views for compatibility and the direct oracle.
They can create a Pair when a caller asks for the raw relation. The engine uses a new internal visitor,
for example `visitActionCandidates(added, visit)`, that passes the initial and request kind separately.
Avoid a complete intermediate list of request Pairs in this path.

Preserve current positional traversal and per-bucket encounter order in the first version. Use primitive
member handles in an encounter lane, with an insertion ordinal per member. For selective lookup, obtain
matching handles from the row membership tables and merge/order them by these ordinals within the same
positional bucket. Raw enumeration can walk the lane. Thus selective lookup need not scan the entire lane
to find one concrete mark. Include this lane and ordinal cost in the layout measurement; grouping is not
free. A later version that drops the lane is a separate scheduling change.

Use a dedicated row-handle path index or a narrow primitive specialization. Do not rewrite every generic
`PathTrie` consumer as part of N1. Its lookups should visit matching buckets without building a list of all
logical request values. Before the first callback, capture matching bucket handles and one query-entry
insertion horizon covering row and member ordinals. Every later bucket visit uses that same horizon.
A request inserted during a link callback is joined through the existing new-request path; do not include
it again through a later bucket snapshot in this first version.

### 3.3 Selective standing join

For a concrete added mark `T`, probe `T` in each geometrically eligible mark row. Other requested marks
cannot produce a mark action. For an abstract added mark `*\X`, walk the encounter lane and skip marks in `X`.
Do not gather and sort the whole selected relation for an abstract link. Use selective membership lookup
and ordinal merge for concrete marks.
Position rows have their own geometry and action rules; do not apply the mark test to them.

Every candidate still passes full `overlap` and `actOnRequest`. Keep the same filter when a newly inserted
mark request reads existing links. Link values keep full `CallerRef` and link layer. Do not suppress a link
merely because another caller emitted an equal request. Transport coalescing is deferred with N3.

The selective test is local to the request/link join. In particular, F75 can raise a selected cleaner mark
despite disjoint geometry or an excluded mark. Do not apply this test to source/cleaner request production.
Do not remove requests without a current link or infer coverage from a shorter path.

### 3.4 Invariant and acceptance

The decoded grouped relation must equal the old exact pair set after every insertion. The grouped index
must select every pair the old index could select. Filtering may remove only pairs whose current request
action is `None`. Compare the resulting Answer and Climb sets with the old join for both arrival orders.
No state is retired before actor freeze.

Use `RequestStoreTest`, `RequestJoinTest` and `RequestActionTest`, plus an independent pair-set reference.
Cover mixed mark/position encounter order, callback insertion into a later unvisited bucket, deeper/static
late answers, a new caller for an existing fact,
independent caller/link layers, excluded abstract marks, F75, manager-ID collisions and cancellation/release.
Measure row density before choosing a dense representation. Worst-case one-member rows still need a small
fallback; they must not cost more than a full mutable map per request.

## 4. N2 — cumulative subscriptions and stable matched bindings

### 4.1 Separate exact accumulation from positional lookup

Split [AddedFactStore](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/AddedFacts.kt)
into an exact accumulator and its indexed link-store wrapper. The exact accumulator owns immutable cumulative
values under `(full CallerRef, admitted GroupKey)` and computes the existing `mergeAddDelta` result.
The wrapper maintains positional lookup and the raw full links needed by requests and Support.

`CalleeSubscriptions` uses the exact accumulator without the wrapper's path index. Give its non-zero-only
entry a stable primitive handle, full caller reference, original link layer, admitted group key and current
immutable cumulative Facts. Return an internal update containing the handle, cumulative value and fresh
delta. A no-growth insertion returns no update.

Derive the key from the admitted input/cumulative group, **not from the returned delta**. An exact-only delta
can have empty exclusion metadata even when it came from a must group with nonempty `E`. Keep full group
kind, layer, base, exclusion and mark exclusion. Preserve existing validation that link layer equals the
added fact layer; caller layer remains an independent part of the full reference.

Keep `zeroOnly` in a separate directory keyed **only by full CallerRef**, as current code does. Validate the
zero premise and Reach payload, then retain the first accepted payload/link layer. A repeated zero-only
reference returns no update. Do not add group/layer variants to this branch in a storage optimization.

### 4.2 Retain groups, replay deltas transiently

Replace `parts` and its part-ID index with stable cumulative entries and one positional directory over
their handles. Index a handle at every relevant position when its accepted value grows. Deduplicate a
handle within each position and within each candidate query. Never rebuild a separate index for each delta.

Preserve register-before-replay. Once an update is accepted, replay its **fresh immutable delta**, directly,
as today. A delayed `ProtocolSteps.replay` callback must capture that delta, not fetch the entry's later
cumulative value. The callback can release the delta after its effects complete. No permanent history list
is needed for this replay.

Later delivery visits the positional directory with the current mode's extension/prefix rules, captures
each candidate's cumulative immutable value, then performs the existing full matching and transfer.
Include the separate zero-only entries for zero publications. Capture candidates before applying callbacks:
callbacks may add subscriptions and change the directory. P6 still observes the subscriptions present at
the delivery read; it does not become a publisher-side subscriber snapshot.

### 4.3 Equivalence gate for cumulative delivery

State the representation invariant: each stable nonzero entry represents the exact cumulative union of
all leaves accepted for its full key. The positional directory represents their union of indexed positions.
The transient replay represents the newly accepted delta. Zero-only entries retain their existing policy.

For each publication/member/mode, prove that selecting and transferring the cumulative entry gives the
same closure effects as selecting and transferring all historical deltas. Check run-1 and restricted
matching, zero/nonzero premises, normal/demand layers, must and exact leaves, inherited exclusions,
mark exclusions, return mapping, aliases, ordered returned cleaners, CALL cuts and late must demotion.
Duplicate effects may change; distinct effects, support and required seeds may not.

This is a release gate, not an assumption that union is distributive through every AP operation. Use small
exhaustive AP fixtures and the independent closure oracle. If a transfer needs more than cumulative Facts,
retain its required semantic state or stop this stage; do not restore a hidden full delta log elsewhere.

P1–P4 and P6 keep their ordering requirements. P5 needs an explicit logical-state refinement: accepted leaves
never disappear, although historical delta objects do. Update the protocol model's subscription encoding
and prove the representation simulation. Existing proofs for a permanent part list do not prove this layout.
List any remaining AP proof obligations under the existing proof-status rules.

### 4.4 Normalize forward E6 before changing delivery

The current [NdSummaryJoin](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/ConjunctionStore.kt)
retains complete `Subscription` inputs and full combinations. Feeding it successive cumulative snapshots
would retain history again. Change only the E6 member value to a checked binding:

```kotlin
data class NdBinding(val ref: CallerRef, val linkLayer: Layer)
```

Create the binding **after** `matches`, the nonzero-premise guard and the TaintTree input guard. Keep the
outer `NdKey(callee, full publication premise set, publication layer, call)`, ordered member slots and every
distinct full reference/link-layer binding. A later root expansion that satisfies an already-present member
does not add a new binding. A newly satisfied member or a different full reference/layer does.

[Summary](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Summary.kt)
uses the matched input layers, rather than their roots, for E6 combination transfer. Extract a checked
internal helper for this transfer. Union every full caller premise. The AP result layer is
`join(publication.layer, bindings.linkLayer)`; `CallerRef.callerLayer` stays in the binding and ordered
resume context and is not added to that result-layer join. Resume through the same ordered reference.
Do not fabricate placeholder
Facts to use an old signature. Keep the generic `KaryJoin` and source/sink predicate joins unchanged.

Keep all growing TAINT conclusions, including whole merged deltas required by T2/T4. New bindings meet
stored conclusions; new conclusion deltas meet stored binding combinations. Normalize only the redundant
matched input payload. It does not authorize dropping full combinations or equal-path caller references.

Prove equal E6 outputs for two matched subscriptions with the same full reference/link layer and different
roots, and unequal-key separation for different references/layers. Include the independent-layer control
`callerLayer=DEMAND, linkLayer=NORMAL, publicationLayer=NORMAL`. Land this change before cumulative
delivery so snapshot history cannot migrate to the conjunction store.

### 4.5 Acceptance

Extend `AddedFactStoreTest`, `SummaryStorageProtocolTest`, `NdJoinAdapterTest` and `RecordDemotionTest`.
Cover replay-before/after-growth, registration/publication interleavings, delivery with later subscriptions,
the delta-header trap, both matching modes, first-wins zero-only, different contexts/layers and growing E6
conclusions. Compare summaries, stored edges, witnesses, records, seeds and hand-offs with the independent
closure reference. A finding-count comparison alone is not an equivalence test.

## 5. Bounds, measurements and remaining risks

Let `R` be logical standing requests, `G` their grouped headers and `I` their path-index nodes/postings.
Let `H` be historical accepted subscription parts, `C` cumulative exact subscription groups, `Z` zero-only
full-reference entries and `J` subscription path-index nodes/postings. These are counts per relevant owner
or their reported sum.

| Area | Old growth driver | New retained state | Remaining worst case |
| --- | --- | --- | --- |
| N1 | Object membership/index entries per request | `O(G + I)` headers/index plus `O(R)` primitive membership/order data | `G = R`; distinct logical requests and path topology remain |
| N2 | `O(H)` retained parts and part-position index, alongside cumulative groups | `O(C + Z + J)` headers/index, current graphs and transient replay deltas | Distinct groups, unique indexed positions and E6 binding combinations remain large |

No exact percentage saving is justified yet. In N2, immutable graphs can remain reachable through other
legitimate owners; removing the history list does not free their full retained union. Stable entries also
need a new index. Include N1 order lanes and all directory/capacity costs. No stronger context or path
subsumption algorithm is selected.

First add bounded aggregate counters, rather than per-entity diagnostic objects or retained event logs:

| Area | Required measurements |
| --- | --- |
| N1 | Mark/position counts; distinct row headers; members/row distribution; ID density; capacities; coarse candidates, selected candidates, actions; new versus repeated insertion |
| N2 | Accepted deltas; cumulative groups; group-position postings; queued replay deltas; current graph bytes; E6 member bindings/full combinations; avoided repeated bindings |

Use small fixed histograms or scalar high-water values. Derive bytes from reviewed heaps and class/layout
censuses. Do not add a lifetime key set solely to count repeats after completion. Trace samples, if needed,
must be bounded and explicitly attributed; they cannot establish a global duplicate rate.

Compare logical states/effects at equivalent completed iterations and at deterministic fixture prefixes.
Physical event accounting keeps its current meaning; no wake batching is introduced. Compare
resource-failed project captures with their actual progress disclosed. Keep retained memory, allocation,
CPU samples and elapsed time separate. Record all query failures as unavailable rather than zero.

Every report must include latest reached `run-N`, direction and positive limit, last complete engine and
last accepted forward report. Give heap/profile capture iterations separately, including failed runs.

## 6. Implementation sequence and release gates

The implemented iteration contains N1 and N2, in that memory-priority order. The integration order below
records their dependencies and reviewable commits. No stage changes scheduling or imports N3–N12.
Code checks and all eight fixed scans are complete. The fresh report records the remaining project failures.

The main source changes are in the existing bidi packages:

| Area | Files and intended responsibility |
| --- | --- |
| N1 | `store/RequestStore.kt`: grouped exact relation, row index and visitors; `engine/RunMethodLocalServices.kt`: selective standing joins. Keep `ap/Demand.kt` as the action oracle. |
| N2 | `store/AddedFacts.kt`: exact accumulator and indexed wrapper; `engine/CalleeSubscriptions.kt`: stable entries and directory; `engine/SubscriptionManager.kt`: transient replay and captured delivery candidates; `store/ConjunctionStore.kt`, `engine/RunMethodCallServices.kt` and `ap/Summary.kt`: checked E6 bindings and helper. |
| Contracts | `spec/analyzer-core.md`, `spec/ap-impl.md` and the relevant protocol model/proof files: representation, subscription protocol and E6 refinements. Keep event accounting and scheduler contracts unchanged. Record proof scope explicitly. |

Use narrowly scoped actor-owned primitive membership/order storage for N1. Do not build shared mailbox
infrastructure for the deferred N3 design. Do not introduce a general cache library or change unrelated trie users.

| Stage | Deliverable | Gate before proceeding |
| --- | --- | --- |
| 1 | Add aggregate census and direct/reference fixtures; state representation and protocol refinements | Baseline output and current source contracts reproduced; no growing diagnostic owner |
| 2 | Factor N1 rows, exact primitive membership and order lanes; add selective visitors | Exact decoded relation and both standing-join arrival orders agree with independent reference |
| 3 | Split exact accumulator from indexed link wrapper; retain current subscription delivery initially | AddedFactStore/link/Support behavior unchanged; unused subscription accumulator index removed |
| 4 | Normalize matched E6 member bindings and extract checked transfer helper | Full reference/layer/member/conclusion equivalence; no Facts-version member history |
| 5 | Replace subscription part history by stable entries and one positional directory | Cumulative selection/transfer and replay/delivery simulations pass; P5 encoding/proof updated |
| 6 | Run required common/JVM checks and fixed project comparisons; review heaps/profiles | Output and resource evidence, including the latest failed run, is sufficient for a release decision |

Run the independent closure/reference fixtures at positive limits 1, 2 and 3, in forward and backward
directions, with both unit layouts and varied arrival orders. Include source/sink NNF, repeated/shared
leaves, signed false conditions, cleaner requests/actions and exclusion/must changes. Compare facts,
summaries, witnesses/proofs, records, seeds, hand-offs and report identities. Keep the oracle independent
of the new data structures. Do not introduce implementation-mirroring tests in place of it.

For the eventual project retest, use Gradle `runProjectAnalyzer` directly and the existing fixed inputs,
rules/model, Temurin 17, batch 2,000 and disabled multi-release loading. Use OWASP 8 GiB/1,000 seconds,
Conductor 8 GiB/1,080 seconds, ThingsBoard 12 GiB/1,080 seconds and PDF 8 GiB/1,080 seconds, with the same
outer harness budgets. Keep the disclosed OWASP cached release-8 CI-analysis input. Do not try other
configurations to make the gate pass.

Compare against main with strict rule ID, sink fingerprint and full method signature identities, and check
physical/logical occurrence spans separately.
Also compare against the current rollback output to detect a new loss. Preserve existing unresolved main
differences in the report; equal-to-rollback is not a no-FN claim. Evaluate additions under their complete or
incomplete provenance. Do not change exclusion or report policy to force parity.

Capture normal and near-limit profiles and fresh OOM heaps as specified in `task.md`. Review N1/N2 owners,
logical cardinalities and graph history at the actual capture iteration. Also inspect existing queue owners
and capacity to see whether the N3 pattern persists; this observation does not authorize N3 implementation.
A project reaching a later run is useful progress, but is not proof of lower bytes at the earlier frontier. Report the measured
trade-off in memory, allocation and service time. If large unique relations still dominate, use that evidence
for a separate algorithm proposal instead of adding an unreviewed cap or cache.

The implementation and bounded equivalence checks are complete. The common/JVM suites and constructive
Lean audit pass. Full current AP/protocol proofs retain the limits in `spec/proof-status.md`. Fresh fixed-project
output, profile and memory checks remain open; no resource saving is certified before those measurements.
