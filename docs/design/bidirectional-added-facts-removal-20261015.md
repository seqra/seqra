# Initial emission without an added-fact store

Date: 15 October 2026. Status: detailed redesign and implementation plan; no core changes.
The run-1 request sections are superseded by [F78](../../spec/run1-requests.md),
its [proofs](../../spec/run1-request-proofs.md) and the
[merged-request implementation proposal](bidirectional-run1-mark-request-implementation-20261015.md).
Full-query/role requests, exclusion-sensitive request joins and deferred answer
memo statements below describe the earlier design, not the next implementation.

Scope assumption: remove the callee/emitter `AddedFactStore`; retain the existing caller-side
N2 summary join. Removing N2's input storage too is a separate, larger design.

The target contracts are now written in [ap.md](../../spec/ap.md),
[analyzer-core.md](../../spec/analyzer-core.md) and [interpreter.md](../../spec/interpreter.md).
The [proof status](../../spec/proof-status.md) separates constructive local refinement from
the remaining engine obligations. The [implementation proposal](bidirectional-added-facts-removal-implementation-20261015.md)
defines the next steps and their test gates. These specification and proof changes do not
change the measured analyzer implementation.

This replaces the earlier raw-store-based callee-owned join proposal. Removing the callee
copy eliminates that duplication without moving the surviving summary join. Discarded
normal-sink cache retirement remains selected. IR storage and the reference manager remain
unchanged; no weak-identity cache is introduced.

## Measurements and target

Measured core: `85a7c0e367d9a2dc6360b03c2feb5e07b1583b66`. The earlier documentation commit
`c4e82f63677e72af72ddb2db9f5f8133dd725b9f` has the same core tree. No new engine scan
ran for this proposal. The target is a completed run-1 engine within 60 seconds. Limits
start at 1.

| Project | Latest reached | Last completed engine / accepted forward report | Run-1 engine |
| --- | --- | --- | --- |
| OWASP | run-3 / forward / limit 3 | run-3 / run-3 | 349.964 s |
| Conductor | run-2 / backward / limit 2; OOM | run-1 / run-1 | 69.931 s |
| ThingsBoard | run-1 / forward / limit 1; OOM | None / None | 164.488 s; incomplete |
| Stirling-PDF | run-4 / backward / limit 4; timeout | run-3 / run-3 | 30.914 s |

ThingsBoard has 3,723,565 callee input groups, 75,026 observation groups and 3,723,573
separate N2 groups. All added stores have a 1,523,417,616-byte default retained union;
caller N2 directories have a 1,247,295,120-byte union. These overlapping footprints are
not additive savings estimates. N2 remains a large owner after this redesign.

The [fact-growth investigation](../reports/bidirectional-run1-fact-explosion-20261015.md)
finds 38,456 groups but only 918 exact payload values in the largest incoming store.
Output marks already pack into sets. Avoiding an entire persistent input copy addresses
more than another mark-set representation change.

## Current and new behavior

| Area | Current | New | Why it helps |
| --- | --- | --- | --- |
| Initial emission after run-1 | Accumulate/index incoming facts, then emit against fixed demand | Intersect the incoming fact with demand, emit matching initials and discard it | Removes the callee input history in every later run |
| Run-1 emission | Retain full caller/header/payload history for future request joins | Answer active questions immediately; keep packed deferred concrete answer shapes | Removes full incoming tries, caller keys, positional indexes and COW views from the emitter |
| Late FLOW requests | Search callee raw suppliers and climb through their CallerRefs | Replay the question against existing N2 inputs and route each eligible climb | Reuses the input relation already required for summary application |
| ND analysis | Local joins pool emitted initials; raw links later supply confirmation evidence | Keep caller-free emission and full ND premises at caller-side application | Avoids emitter caller separation without erasing joint source/summary dependencies |
| ND findings | Raw-link Support confirms candidates before Simple export | Permit mixed-caller candidates; defer concrete trace filtering as requested | Removes the raw-link consumer; candidate output must not be described as trace-validated |

The emitter's only additional run-1 state is active questions and deferred concrete answers.
Existing method facts, initial deduplication, summaries, records, source hits, N2 subscriptions
and necessary ND bindings remain. This is an algorithmic removal of the callee input relation,
not a claim that all analyzer storage becomes small.

## Direct emission after run-1

Replace raw admission followed by `emit(delta)` with the existing demand intersection and
initial emission on the incoming fact. The demand is fixed for the run. InitialFactStore
still deduplicates method starts. No arrival-history table is needed for the emitter.
Keep the link-layer and no-cleaner-temporary boundary checks when raw admission is removed.

The caller's summary/record matching retains the actual incoming input and original premise
in N2. Its continuation and native/reversed-record behavior stay unchanged. Do not confuse
removing callee storage with removing the caller's return-application inputs.

## Run-1 concrete answers

Always emit the abstract `*` initial, including `(S, ., *, ...)` for statics. Structural
static abstractions still come from the precomputed profile and the actual input. Concrete
answers are additive. Interest membership is not evidence that a mark is present.

Represent pending answers as:

```text
request geometry -> emitted initial shape without its concrete mark -> MarkSet
```

There is no CallerRef, caller context, call instruction or original caller premise in this
memo. Equal answer shapes merge their marks. Keep exact, any and STAR shapes distinct.
Request geometry includes the full pattern where it differs from the abstract initial slot.

For an eligible concrete mark T:

1. If the matching question already requests T, emit its concrete initial immediately.
2. Otherwise keep its possible concrete answer shape and T in the memo.
3. When the question requests T, emit those answers and remove T from those memo rows.
4. Keep the question active so later suppliers answer immediately.

"Requested" is scoped to the matching geometry. A request for `C.f:T` does not consume a
pending `C.g:T` answer. Answering one question must not erase T for unrelated questions.
Requests for source and sink conditions use the same mechanism; requested marks remain
independent alternatives, not an AND premise.

Compute an answer with the existing supplier eligibility/answer operation before discarding
supplier exclusions. Query initial E can be empty as already specified. This does not allow
a concrete supplier to be broadened to the generic policy root before matching.

Two controls are essential:

- An exact supplier at `C.g:T` cannot answer `C.f:T`.
- An ancestor supplier excluding f cannot answer a new f question merely because an earlier
  root question produced a broader emitted initial.

Cache answers under the question for which eligibility was evaluated. When a new question
shape appears, use N2 replay to evaluate older inputs with their actual geometry/E/mx.
Do not construct a question-by-mark-by-supplier product or preserve raw suppliers in the memo.
The expected memo is small, but measure its distinct shapes and mark memberships rather than
claiming a universal grammar bound.

## Late questions through existing N2

An emitted abstract initial has erased input path/E/mx/layer information. It cannot by itself
identify which original caller initial should receive a late FLOW mark request. Existing
`CalleeSubscriptions` already retains the bound Facts and CallerRef needed for that routing.
Reuse it; do not build another callee supplier index.

Publish a callee's immutable question view/deltas to the units already subscribed to its
summaries. Use the same register/replay plus notify structure as summary delivery:

| Arrival order | Required operation |
| --- | --- |
| Question after input | Replay the new question against current N2 inputs |
| Input after question | Match the new subscription delta against current callee questions |
| Subscriber after question | Register, then replay the current question view |
| Duplicate question/input | Existing interest, subscription and initial deduplication suppress new state |

Retain one authoritative question view. Sharing an immutable root is sufficient; do not copy
the complete question/mark relation into every subscriber. Publish precomputed static checks,
cleaner mark interests and dynamic questions under their complete query identities.

Add a request-specific N2 candidate visitor. The current summary candidate iterator searches
only extensions in run-1. Mark questions also need proper-ancestor suppliers. Query prefixes
and extensions around the full query path, deduplicate handles, then run the complete existing
geometry/E/mx predicate. Candidate lookup alone does not prove eligibility.

TAINT matches emit concrete initials. FLOW matches route packed marks to the original full
caller MethodKey and singleton InitialAp. Keep caller identity here as continuation routing,
not as an emitter storage dimension. Use the original incoming input for N2 admission;
an exact-only fresh delta may have cleared E.

The new visitor must not allocate one full candidate snapshot list. An owner can process a
finite current-value visit with cancellation checks and enqueue answers/climbs, including
local results, without invoking callbacks that mutate the directory. A resumable visitor
needs a bounded snapshot contract; do not reintroduce the deleted COW history by accident.

Batch question deltas and outgoing marks. Keep method-queue-empty summary publication and
the current bounded request service opportunities. Count registration/replay, question,
answer and climb work before visibility; reserve follow-up work before completing its parent.
Prove both arrival orders, reentry, cancellation and release. Empty queues must not hide a
late replay or allow false COMPLETE.

N2-based replay can also supply concrete answers for a new question geometry. This is the
correct fallback when an older memo entry does not prove that question's eligibility. It
requires no added-fact history in the callee.

## ND handling and confirmation

Caller separation is unnecessary in the emitter. Local conjunction processing already joins
emitted initials without a CallerRef dimension. Keep full joint premises on ND source and
summary results. Existing caller-side ND matching must satisfy all members at one caller/
call scope before return application. Do not combine marks into a new AND premise merely
to pack storage.

Permit an ND sink candidate whose members came from different callers, as requested. Preserve
its rule occurrence, NNF proof choices, premise members and layers for later validation.
Preserve existing source-hit and seed identities separately; current SinkWitness has no
witness-to-source map. Do not add a caller-separated callee store for confirmation.

Current `Support` directly scans raw links after the forward run. Current JVM export returns
`Simple`; it has no concrete trace filter. Removing the raw store must therefore remove or
replace the Support scan explicitly. This iteration may retain the allowed ND candidates
without implementing a complete trace engine. Report candidate versus confirmed state and
do not claim that deferred trace filtering has already happened.

Use the existing state contract: an unverified witness keeps `confirmed = false` and its
new report entry is `DEMAND`. Do not set CONFIRMED from pooled initial availability or
NORMAL layers alone. Only a valid support query or later concrete validation can promote
it. Keep existing sticky confirmed entries, STOP_RULE, NO_DEMAND_EDGE and continuation
policy behavior; do not force extra runs until a deferred trace filter becomes available.
Test that the new DEMAND candidate participates in the correct handoff and that termination
can still return an explicitly unverified report.

If exact confirmation is required during migration, expose a joined read-only query window
over existing N2 before subscription freeze. Current lifecycle clears N2 before driver
Support, so a later read of released subscriptions is not valid. Prefer targeted support
questions to expanding all supplier links into another global collection. This preserves
an optional exact-confirmation path without a second input history.

The later trace filter must check guards on derived facts too. Current sink triggers retain
only `(sink, layer)` and generate end facts under zero. With site A supplying only x:T and
site B only y:U, a mixed `x:T AND y:U` sink can generate `Return:V` under zero. Caller ND
matching cannot reject that zero-premise summary. This is already possible in the current
analysis and is not caused by deleting raw storage.

Thus the candidate overapproximation can include facts derived from an ND sink end action,
not only the sink's own location. Later validation must reconstruct that guarded generation
under one concrete caller, or use an explicit derivation obligation. Keep the specified
zero-GEN analysis semantics; do not silently replace them as a memory optimization.

## Duplicate input work and observation

Raw admission currently rejects duplicate arrivals before emission. Removing it can expose
repeated intersection work even though InitialFactStore suppresses duplicate starts. Measure
arrivals, answer scans and emitted-new initials separately.

Reuse N2's fresh-subscription delta where it safely controls transport/replay. Preserve
fresh origin registration even if the emitted initial already exists: that caller still
needs summaries and late-question routing. Do not introduce a full payload seen-set at the
callee to recover the old delta gate. Repeated emission work is a CPU concern to measure
against the 60-second target.

The unresolved-call observer owns another `AddedFactStore`. Its callback receives only the
call, base and plan. Replace its fact history with a small observation-identity set after
checking callback timing/rebuild semantics. This is not a supplier store and needs no tries,
marks, CallerRefs or full Facts values.

## Implementation plan

Use a history-based reference with the same candidate/confirmation policy. Compare AP and
record results under fixed run configurations. Removing raw Support may leave more DEMAND
entries and thus change later seeds/frontiers under the existing handoff rules; equality to
the old support-enabled whole-driver sequence is not an emission-equivalence claim.

1. **Define contracts and independent oracles.** Specify query identity, answer-shape
   equality, consume-on-request, the permitted ND candidate state and joined-query lifetime.
   Cover no-request/request-first/supplier-first, static g-to-f transfer, exact/any tails,
   excluded ancestors, mx, new question shapes and normal/demand layers.
2. **N2 question replay.** Add immutable question publication, request-specific prefix+
   extension candidates and counted answer/climb events. Test old input/new question,
   old question/new input, subscriber registration races, duplicates, reentry and cancellation.
   Preserve original caller premises and admitted input headers.
3. **Caller-free run-1 emitter.** Add the packed deferred answer memo and immediate active
   question handling. Keep abstract static emission and additive concrete refinements.
   New geometries use N2 replay; consume only matching answer rows.
4. **Remove callee accumulation.** Wire incoming inputs directly to run-1 emission or fixed
   demand emission. Delete raw groups, positional index and COW phases, including service
   callbacks/counters that require an AddedFactStore.Update. Preserve ordinary initials,
   method facts, N2, summaries, records and native/restricted-run behavior.
5. **Confirmation and cleanup.** Replace/remove the raw-link Support consumer under the
   explicit DEMAND candidate policy; retain confirmed behavior only where a real support
   proof remains. If retaining exact confirmation, add its joined N2 window before release.
   Test sticky CONFIRMED state and handoff/termination. Replace the observation memo,
   then remove dead classes and tests that enforce
   the superseded representation. Keep semantic AP/demand/ND/hand-off tests.
6. **Validate complete behavior.** Compare full F1/B2/F3 summaries, ND source premises,
   records, seeds, source hits and report locations. Check split callers, split call sites,
   context/entry distinctions and the zero-GEN derived-candidate case. Confirm that allowed
   ND candidates are not counted as validated traces.
7. **Retest fixed projects.** Use the existing direct Gradle task and unchanged OWASP,
   Conductor, ThingsBoard and Stirling-PDF settings. Record latest reached/completed/accepted
   runs, engine time, strict main location comparison, OOM/timeout phase, profiles and guard
   heap ownership. Measure N2 replay work and pending-answer geometry/mark memberships.

The memory gate is no retained callee supplier/history/index, including callback captures.
N2 remains measured and visible; its large input relation is not renamed or claimed removed.
Keeping the discarded normal-sink cache retirement as an independent change makes its effect
reviewable. No IR, reference-manager or unrelated queue changes belong to this iteration.

## Separate future option: remove N2 inputs too

This needs reconstruction of actual bound call-stage inputs from caller method state, plus
lightweight continuation descriptors. `MethodEdgeStore.edgesAt(call)` is before binding,
sources and cleaners, so it is not an existing replacement. A pure call-prefix closure must
preserve NNF joins, effects, records, layers and cuts without repeating observable callbacks.
It needs its own closure oracle, lifetime, batching and CPU/memory design. An archived draft
is in the external `design-variants/full-input-reconstruction-draft.md` packet; this wider
option is not silently included in the callee-store removal.

## Source evidence

The current batch is
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-abstraction-demands-v2/20261015T004517.956676Z`.
Read-only source-bound reviews are:

- `report-preparation/run1-fact-explosion-20261015/added-facts-removal-late-flow-review`
- `report-preparation/added-store-removal-source-review`
- `report-preparation/remove-added-fact-store-20261015/support-trace-handoff-review`

Relevant consumers are
[request joins](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/RequestGroupCursor.kt),
[N2 subscriptions](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/CalleeSubscriptions.kt),
[call execution](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodCallServices.kt),
and the former `driver/Support.kt` consumer (removed by that implementation).
