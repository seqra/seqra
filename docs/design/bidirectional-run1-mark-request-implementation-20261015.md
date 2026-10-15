# Merged run-1 mark requests: implementation proposal

Date: 15 October 2026. Status: proposed implementation; no analyzer code changed.

This proposal implements [F78](../../spec/run1-requests.md). The
[proof dossier](../../spec/run1-request-proofs.md) states the semantic arguments,
Lean results and their scope. The [original proposal](bidirectional-run1-mark-request-redesign-20261015.md)
records the measurements and selected changes. F78 replaces its proof-obligation
list with a precise contract and a required static-root safety clarification.

The measured analyzer is `27e22bb1ed871651143538783473cb0d4fcbcf3e`.
No new analyzer run was made for these specification and proof changes.

| Project | Latest reached | Last completed engine | Accepted report | Run-1 engine seconds |
| --- | --- | --- | --- | ---: |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 | 331.657 |
| Conductor | run-2, backward; OOM | run-1, forward | run-1 | 60.210 |
| ThingsBoard | run-1, forward; OOM | None | None | 279.104; incomplete |
| Stirling-PDF | run-4, backward; OOM | run-3, forward | run-3 | 36.102 |

The target is a completed run-1 engine within 60 seconds. Prescan and the report
barrier are measured separately. The [performance report](../reports/bidirectional-added-facts-removal-performance-20261015.md)
defines the fixed workloads and measurement limits. These proofs establish no
speedup or time bound.

## 1. Behavior and scope

| Area | Current measured implementation | Proposed implementation | Reason |
| --- | --- | --- | --- |
| Request identity | Initial, full query pattern and operation role | Full method key, one normal slot and a mark set | Merge equal initial needs from sources, sinks, gates and cleaners |
| Retained requests | Abstraction and publisher each retain cumulative interest | One cumulative store in SummaryStorage; one temporary method-owned store | Remove the duplicate cumulative relation |
| Submission | Scalar/query publications plus intermediate request service | Commit the temporary store when the method fact queue is empty; replace it | Accumulate independent marks before replay |
| FLOW routing | Per-mark callbacks and target accumulation | Abstract-origin guard, geometric match, set difference against admitted target marks, grouped send | Avoid repeated joins, callbacks and messages |
| Exclusions in requests | Full query and supplier exclusion tests | Ignore field and FLOW mark exclusions in request joins | Requests ask for analysis; exclusion already requests the same original mark |
| Concrete suppliers | Speculative answer scans and a deferred answer memo | Read admitted requests on arrival; replay late requests over retained caller inputs | Remove the callee memo and unrequested answer construction |
| Static needs | Full-query static catalog and runtime special cases | Post-prescan field/mark plan plus dynamic changed-origin requests | Avoid repeated discovery of known static needs |
| Static root | Existing root transfer and summary behavior | Keep the root seed, skip its rule evaluation; use explicit component ownership and an untouched frame at summary reads | Prevent root summaries from bypassing cleaning or losing untouched siblings |

There is no position-request transport, third abstract class slot, new caller
input index, request-by-input seen table, unit barrier, pending quota, rescan
fallback or scheduler replacement. Caller input storage, ordinary joint premises
and normal summary matching remain required. IR storage, reference management,
WeakIdentityCache and bound-rule retirement are outside this change.

## 2. Request types and ownership

Introduce a value type for normal request slots:

```text
Run1RequestSlot = BaseSlot(nonStaticBase)
               | StaticSlot(classAccessor, fieldAccessor)

RequestStorage.addAll(slot, immutableMarks)
RequestStorage.borrow(slot) -> immutableMarks
SummaryStorage.commit(temporary: RequestStorage)
```

A full MethodKey owns each SummaryStorage. It includes entry and context. Do not
include caller identity, call site, rule role, pattern tail, E or mark exclusions
in the request key. Preserve those dimensions in ordinary caller subscriptions
and proof stores. Mark-set union is an OR of interests, not an AND condition.

Use the same RequestStorage class for both instances. Producers can only add to
the temporary instance. They cannot query its membership. The authoritative
instance supports immutable reads. Keep synchronization in SummaryStorage;
temporary access belongs to its method actor. Reuse the existing packed mark-set
operations where they meet this contract. There is no need to redesign interning.

Start with a slot map of immutable packed mark sets. `addAll` computes the set
union only for the touched row and reuses the old set when no mark is fresh.
`borrow` returns that immutable row without rebuilding it. P and R use the same
operations; a commit does not clone the whole map or reconstruct every old row.
Batch incoming sets before these unions. This is a concrete initial layout, not
a new persistent mirror or per-input cache; representation changes need measurements.

Store only canonical slot descriptors and mark IDs. No store entry may retain a
compiled rule, form, caller reference or incoming Facts tree. Existing source/sink
occurrence and conjunction stores still own their semantic identities.

## 3. Prescan plan and static components

Update `JIRStaticMetadataCollector`, its neutral metadata and `Run1StaticProfiles`:

1. Read all effective entry, statement, call, exit, native and generated forms.
   Read NNF leaves directly. Include source and sink conditions, concrete input
   gates, selected cleaners, generated rewriters and all static action positions.
   Include every excluded mark of a generic abstract microedge target as an
   exclusion-creator need with the corresponding static scope.
   A proved-false condition contributes nothing. Do not expand conditions into cubes.
2. Collect the finite canonical class/field universe from the prescan program,
   rules and models. Check that transfer and binding constructors use this universe.
   This is an existing-program well-formedness condition, not a guessed list of
   runtime leaves. Keep its descriptors immutable and share them.
3. Build direct structural footprints and field/mark needs. Retain wide class/base
   scope descriptors until they can be resolved against relevant fields. An empty
   static root or bare class point is not a normal request slot.
4. Compute the least transitive closure over the already resolved call graph,
   including recursive components. Use the existing SCC infrastructure. Union
   effective prescan contexts conservatively. Do not resolve calls again.
5. Freeze the plan before run-1. Seed a SummaryStorage's resolved field requests
   before exposing it to subscribers. Create stores lazily. A later activated wide
   contribution enters the temporary store and follows the normal method barrier.

Keep static suppression narrow: omit a dynamic request only when the actual
original slot is the checked slot and its mark is already admitted. Metadata alone
does not discharge a pending request. In `x=C.g; C.f=x; check(C.f,T)`, keep g/T.

For normal field suppliers, resolve matching actual fields and the method's
relevant structural slots. For broad root/class suppliers, partition using the
complete finite field domain and the plan's scopes; preserve a compact untouched
residual. Do not broadcast every concrete mark to every field. Only admitted needs
select concrete field initials. Exact class-point taint uses its direct concrete
path; a broad possible class-point contribution retains conservative MAY/DEMAND
semantics. Neither case creates a class request key.

Prescan metadata must not retain compiled forms or alias-analysis environments.
Keep only the compact static footprint needed by later native/reversed root-record
readers for the lifetime of the analysis. Release run-1 mark-interest state after
run-1 joins. This does not change the stored IR or introduce a new IR cache.

## 4. Slot matcher and initial emission

Replace request-specific use of the general `RequestGroupCursor` with a two-slot
matcher. Keep a scalar reference matcher in tests. It must be independent of the
optimized trie walk and of the previous full-query matcher.

The production matcher:

- Rejects unrelated bases/classes/fields before scanning marks.
- Treats a nonempty descendant or a broad ancestor as a positional match.
- Rejects an exact ancestor as a supplier for a descendant field.
- Ignores incoming E and FLOW mark exclusions for request work.
- Requires an actual matching concrete leaf for a concrete answer.
- Produces the F78 exact/STAR/ANY answer shape and preserves the required layer.

`Run1Abstraction.emit` always emits the normal abstract initials selected by the
structural plan, including the special static root. For concrete input, intersect
its actual marks with admitted request sets and submit only matching concrete
initials. Do not first collect all possible answer shapes or unrequested marks.
InitialFactStore still deduplicates starts.

An exact class point is preserved directly at its own position. Broad suppliers
must use the conservative direct class-point case specified by F78; they cannot
become exact/normal evidence just because a class-level rule exists.

Remove `Run1Abstraction.questions`, the deferred `pending` answer map and their
role/query-specific state after the replacement controls pass. The abstraction
borrows authoritative requests from SummaryStorage. It retains no supplier history,
answer memo or second cumulative request map.

## 5. Commit and method barrier

The method actor owns `temporary`. At its fact-queue-empty barrier:

```text
flushNormalSummaries()
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Within `commit`, under the existing storage lock, compute fresh marks per slot and
union them into the authoritative store. After releasing the lock, queue one
immutable changed-slot batch per subscribed caller runner. Empty differences need
no notification. Read the submitted store synchronously; do not retain it.

Notifications, including local notifications, use normal counted queued delivery.
They must not call back into this method actor inline. Events that arrive during
commit wait until the actor has replaced `temporary`, then write to the new store.
No takeBatch, destructive drain or mutable batch alias is needed.

Extend the existing `hasPendingRequests`/Work conditions to include a nonempty
temporary collector and pending answer/notification transport. A request-only
method schedules existing Work without a synthetic fact. Keep the Work reservation
through commit, replacement and secured child sends. If normal summary handling
created fact work, process it before completing the method. An interrupted or failed
submission cannot certify completion.

Keep quantum scheduling and cancellation. A quantum yield is not an extra request
publication barrier. There is no hard request-delay or temporary-memory guarantee;
measure delay and peak size before proposing another barrier policy.

## 6. Caller replay and batched propagation

Keep `CalleeSubscriptions` as the exact caller input owner. Keep its original
admitted header, full CallerRef, call, layer and joint-premise semantics. Its
existing candidate directory can select request candidates; change the exact
predicate, not the caller-input identity. Do not add a second request index.

`SubscriptionManager` handles two separate effects:

```text
TAINT input + matching requested concrete mark -> concrete callee initial
FLOW input + abstract original initial         -> upstream request mark set
```

Validate existing premise/fact invariants first. At both local collection and
upstream routing, skip requests whose actual original initial is concrete. Do not
infer the origin from the currently active method analysis. Concrete source and
summary results keep their own zero/concrete/joint provenance. The special static
root remains prohibited as a request target.

For an eligible abstract-origin FLOW input:

```text
useful = offeredMarks - targetAuthoritativeRequests[originalSlot]
```

Ignore E and FLOW mark exclusions. The exclusion-producing action already emitted
the same original-slot request, so any later duplicate disappears by union. Cover
all exclusion creators, including generic lowered targets, generated rewriters
and inherited summary exclusions. Do not use exclusion provenance as a new runtime
cache or request key.

The measured generic MarkGate only updates the FLOW header for an abstract target
with excluded marks. Add the F78 request side effect at the exclusion-producing
operation, using the pre-operation actual origin, if lowering has not already
provided it. Keep legal generic edges. The side effect must occur before the
excluded output is published; it is not a later scan of all mx headers.

Union effects for the same full destination MethodKey and slot before sending.
Handle all changed slots from a callee in one replay visit where practical. Match
each retained input group once per batch, then route mark sets. Do not call a scalar
climb callback once per mark before grouping. A stale authoritative read can send
an extra proposal; it cannot suppress an unadmitted need.

Keep concrete answers from concrete-initial suppliers, including late answers.
The origin guard removes upstream interest, not the concrete-answer branch.

## 7. Arrival ordering without an answer memo

Enforce this order on every ordinary, native and manually delivered call input:

```text
register caller runner as a summary/request subscriber
admit the input in the retained caller input store
deliver that input to the callee abstraction
```

The callee abstraction reads authoritative requests on arrival. A fresh request
commit notifies subscribed callers, which replay over current retained inputs.
New FLOW inputs also read standing requests and route upstream needs. Concrete
input arrival needs no duplicate caller-side request-answer replay because the
callee abstraction supplies that arm.

The proof uses admission A, abstraction read E, request commit Q and replay V.
With A<E and Q<V, either Q≤E and the abstraction sees the request, or E<Q and
A<V so replay sees the input. Subscriber registration-before-read closes the
registration race. Initial deduplication handles repeated answers.

Check every direct/manual input path. A producer that bypasses retained caller
admission cannot use this no-memo theorem; route it through the contract or prove
its finite direct-emission case separately before removing the old memo.

## 8. Static-root summary safety clarification

The proof review found two counterexamples to treating raw root summaries as
ordinary concrete-field summaries:

1. Skipping root rule evaluation can publish an identity that restores cleaned f/T.
2. A root strong-write summary can exclude an entire class C and drop untouched g/T.

F78 therefore separates field ownership from the root's structural role. Fields
in the transitive semantic footprint use their normal field analyses and summaries.
The untouched complement has a proved identity frame. Its effect is defined by
noninterference, not by an unrestricted root identity or a coarse root exclusion.
Keep the special root seed and its structural summaries, as requested.

Implement this as read-time selection/projection using immutable plan metadata.
Partition the actual input into owned field components, direct class-point cases
and the untouched residual. Apply normal field summaries to owned components;
pass the untouched residual through the caller continuation as the frame requires.
Gate this frame by the callee's actual normal-return/control derivation; do not
invent a return for a non-returning call or apply it on an exceptional-only path.
No raw root-derived nonidentity result may invent a flow from the untouched input.
Preserve each input's ordinary spatial exclusions and layer in this partition;
ignoring exclusions is specific to the request join.

Apply the same domain guards to ordinary summary reads, native records, opposite-
direction views, demand selection and persisted-record reuse. A root record must
not escape the partition by being read in a later iteration. The stored record
representation need not change: the reader derives root eligibility from its
premise and the retained method footprint. Prove the reader refinement before
enabling root rule suppression.

Use the existing normal-exit publication path as the live return-evidence owner:
`RunMethodAnalyzer.endAt(..., summary=true)` produces the special-root summary
through `summaryDelta`, and SummaryStorage publishes it normally. A frame reader
can recognize this publication as control evidence while rejecting its raw payload
as unrestricted field semantics. Cache at most the resulting return-enabled state
with that method's existing summary state. Notify/replay through the existing
publication channel; method creation, a pending request and method completion are
not return evidence. A no-return or exceptional-only fixture must produce no frame.

For persisted use, an eligible native root record already carries its full method,
direction and reached normal publication. Recognize that provenance in the guarded
record reader before constructing an opposite-direction view. An imported model
must explicitly supply its normal-return contract. It cannot obtain frame evidence
from an arbitrary synthetic identity. The implementation gate must also show that
every permitted normal-return root execution exposes its control publication; the
finite proof does not infer that property from today's publication index.

This is a necessary correctness clarification discovered while proving the
proposal. It is not an independent performance optimization or an implemented fix.

## 9. Change sequence and controls

| Step | Proposed change | Required evidence before the next step |
| --- | --- | --- |
| 1 | Add independent finite slot/projector and effective-form plan controls | F78 cases, recursive closure, broad suppliers, exact/direct class points and complete producer inventory agree with the checked model |
| 2 | Add component-aware root readers and untouched frame | Both root counterexamples fail under the old read and pass under the new read; cleaned f/T disappears, g/T and f/U survive, later/reversed reads agree |
| 3 | Introduce the same RequestStorage class at the authoritative and temporary owners | Union/difference, immutable reads, empty commit, repeated commit and commit-then-replace match the protocol model |
| 4 | Wire method-empty commit and grouped counted delivery | Request-only work, local queued delivery, arrivals during commit, cancellation and final completion preserve reservations |
| 5 | Enforce caller admission/delivery ordering; replace emitter memo with authoritative reads | Both arrival orders, late callers, input growth and direct/native paths produce the same supplier-backed answers |
| 6 | Merge FLOW propagation before mark expansion | Concrete-origin guard, excluded-mark duplicates, stale target reads and batched changed slots preserve logical membership |
| 7 | Remove old role/query/cumulative/memo code and obsolete counters | No completed request message or callee state retains caller inputs; normal summary and ND premises remain intact |
| 8 | Run focused regressions, then the fixed project comparison | Strict finding locations, latest reached/completed/accepted runs, run-1 engine time and request-owner measurements are reported |

Keep each step reviewable. Do not activate an incomplete combination: deleting
memo state before ordering is enforced, or skipping root rules before guarded
readers exist, would violate the new contract. This document does not authorize
unrelated analyzer changes.

Use existing tests such as `Run1AbstractionTest`, `SummaryStorageProtocolTest`,
the static-profile/prescan fixtures and the request/scheduler fixtures. Add behavior
controls for the cases above. Scalar oracles must use their own small set/slot model;
do not compare the new matcher only with today's full-query implementation.

## 10. Measurement and acceptance

Report request mark memberships proposed/admitted, duplicate admissions, changed-
slot batches, caller-input group visits, marks scanned, concrete-origin skips,
concrete answers and initial starts. Record temporary peak slots/marks, submission
delay, authoritative owner bytes and total request-related retained union. Separate
the costs of prescan closure, root readers and ordinary summary application.

Use the same OWASP, Conductor, ThingsBoard and Stirling-PDF configurations and a
fresh main comparison. Run through `runProjectAnalyzer` with their configured
memory/time limits. Do not substitute a smaller input, heap or timeout. Always
state the latest reached run iteration and whether it completed or supplied the
accepted report. An earlier OOM, fewer findings in an incomplete report or a
smaller isolated owner does not establish improvement.

Performance acceptance requires completed run-1 engine time and preserved strict
findings. The checked finite proofs support the design; they do not certify future
Kotlin code, the full alternating driver, pooled ND confirmation or a universal
60-second guarantee.

## 11. Proposed source changes

The paths below are relative to the bidirectional packages in the dataflow/JVM
modules. They name future changes; none is made by this documentation revision.

| Component | Required change |
| --- | --- |
| `engine/MarkQuestions.kt`, new `store/RequestStorage.kt` | Replace role/full-query keys with normal slots and immutable slot/mark batches; implement the shared storage class |
| `engine/SummaryStorage.kt` | Own R, seed resolved static needs, commit P synchronously, expose immutable rows and queue grouped fresh deliveries |
| `engine/Run1Abstraction.kt` | Stream plan-selected abstract initials and actual R-backed answers; direct class cases; remove local cumulative questions and answer memo |
| `engine/Run1StaticProfiles.kt`, JVM `interp/JIRStaticMetadata.kt` and `JIRBidiAnalysis.kt` | Complete effective-form producer/footprint extraction, generic exclusions, finite domain and SCC closure; share compact reader metadata |
| `ap/RequestGroupCursor.kt` or its replacement | Implement the independent two-slot request matcher and packed answers; remove query role/E/mx request filtering |
| `ap/MarkGate.kt`, `ap/EdgeApplication.kt`, `ap/Clean.kt` | Separate actual result guards from interest collection; issue same-origin requests for all exclusion creators; preserve ordinary cleaning |
| `engine/RunMethodAnalyzer.kt`, `RunMethodLocalServices.kt` and `interp/FormApplier.kt` | Guard root rule evaluation and concrete-origin interest; own P; commit-and-replace at method empty; preserve normal-exit control publication |
| `engine/SubscriptionManager.kt`, `CalleeSubscriptions.kt` and `RunMethodCallServices.kt` | Enforce admission-before-LinkIn, read/replay R in both orders, batch FLOW routes and preserve separate TAINT answers |
| `engine/PublicationIndex.kt`, `ap/Summary.kt`, `store/RecordStore.kt` and a shared static-component reader | Project owned components, gate the untouched frame by normal-return evidence, and preserve domain guards through native/reversed/later reads |
| `engine/RunEvent.kt`, handler/runner/subscription ports | Carry immutable grouped slot/mark changes using existing counted queued events, including local delivery |
| `engine/Run1AbstractionStatistics.kt`, `RequestBatchStatistics.kt` | Replace obsolete query/memo counters with the acceptance measurements above |

Use no direct compiler-form cache, alternate SoftReferenceManager, weak identity
cache, stored-IR format change or broad scheduler refactor in these steps.
