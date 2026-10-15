# Added-fact removal: implementation proposal

Date: 15 October 2026. Status: implemented as selected; core protocol checks pass, with eight strict application precision failures.
See the [implementation evidence](../reports/bidirectional-added-facts-removal-validation-20261015.md).
The [current project report](../reports/bidirectional-added-facts-removal-performance-20261015.md)
records the fixed scans and the heap/profile review status for `27e22bb1e`.
The measurements below belong to the pre-change core and are historical only.

This document implements the scope of the
[selected redesign](bidirectional-added-facts-removal-20261015.md). The revised
[AP specification](../../spec/ap.md) and
[core specification](../../spec/analyzer-core.md) define the behavior. The
[proof status](../../spec/proof-status.md) states what the constructive model proves.
The proposal does not authorize changes to the IR, reference manager, weak caches,
or other queue algorithms.

## 1. Goal and measurement baseline

Remove the persistent callee input relation. Keep the caller-owned N2 input relation
and summary join. After run-1, the emitter has no input history. In run-1, its additional
state consists of active questions and packed pending concrete answers. Existing
initial, method-edge, summary, record, source-hit and ND application stores remain.

The measured core is `85a7c0e367d9a2dc6360b03c2feb5e07b1583b66`.
This is a design and proof revision; no new project analysis ran.

| Project | Latest reached iteration | Last completed engine / accepted forward report | Measured run-1 engine |
| --- | --- | --- | --- |
| OWASP | run-3, forward, limit 3 | run-3 / run-3 | 349.964 s |
| Conductor | run-2, backward, limit 2; OOM | run-1 / run-1 | 69.931 s |
| ThingsBoard | run-1, forward, limit 1; OOM | None / None | 164.488 s; incomplete |
| Stirling-PDF | run-4, backward, limit 4; timeout | run-3 / run-3 | 30.914 s |

The target is a completed **run-1 engine within 60 seconds**. Setup and the report
barrier have separate measurements. Limits start at 1. These measurements do not show
that this proposal meets the target, or that its performance improves on the control.
See the [current performance report](../reports/bidirectional-run1-abstraction-demands-performance-20261015.md).

ThingsBoard has 3,723,565 callee input groups and 75,026 observation groups.
The default retained union for all added stores is 1,523,417,616 bytes. N2 caller
directories retain a separate 3,723,573 groups and a 1,247,295,120-byte union.
These are overlapping ownership measurements, not additive estimates of memory saved.
N2 remains after this change. The
[fact-growth report](../reports/bidirectional-run1-fact-explosion-20261015.md) supplies
the ownership and value counts.

## 2. Behavior changes

| Area | Current behavior | Proposed behavior | Expected effect |
| --- | --- | --- | --- |
| Restricted-run arrival | Accumulate and index the input, then emit its delta against demand | Apply the fixed demand to the incoming input and discard it after emission | Removes the callee input copy and its index in all later runs |
| Run-1 arrival | Retain caller-separated supplier groups for later questions | Emit the abstract initial, answer active questions, retain only eligible pending concrete answer shapes | Removes caller keys and full supplier payloads from emitter state |
| Late question | Search the callee's retained suppliers | Replay the question over the caller-owned N2 inputs | Uses the relation already needed for return application |
| Pending mark | Retain its suppliers even after answering | Emit the answer and remove that question's pending mark membership | Releases answered concrete state while keeping interest active |
| ND sink result | Use raw-link Support after the engine to attempt confirmation | Keep an unproved result as a DEMAND candidate | Removes a consumer of the deleted input relation |
| Unresolved-call observation | Use another added-fact store to gate a callback | Retain only the identity of an observation already delivered | Removes payload tries and caller histories used only as callback gates |

Caller N2 admission, summary matching, full ND premise sets, record application and
method-queue-empty summary batching retain their existing semantics. The earlier
callee-owned input/summary join proposal is superseded. Discarded bound sink cache
retirement remains a separate selected change; it is not part of this patch sequence.

## 3. Contract and state

### 3.1 Semantic history and retained state

The spec and proofs may quantify over all inputs that have arrived. This is a
mathematical history used to define outputs. It is not an instruction to retain
those inputs at the callee. A test oracle may retain that history to check the
optimized protocol. Production must not retain the oracle.

| Owner | Retained state | Lifetime |
| --- | --- | --- |
| Callee, every run | Existing emitted-initial deduplication and method analysis state | Current run |
| Callee, run-1 only | Active question map and pending answer map | Current run; answered pending memberships are removed earlier |
| Shared callee publication | One immutable current question root; subscriber identities; short delivery deltas | Current run; no list of past roots |
| Caller N2 | Original admitted input header and current payload, full CallerRef, existing summary index and ND state | Current run |
| Replay handler | Current query, candidate handle deduplication and traversal state | One finite owner-local visit |
| Queues | Counted input, question, answer and climb events | Until processed or released on cancellation |
| Observer | Delivered observation identities | Existing observation lifetime, specified below |

No callee map, callback, event closure or publication may preserve a second supplier
relation after processing. Transient input events are required transport; they must
not become completed-delivery history. Do not replace the store with a full Facts
seen-set.

### 3.2 Question identity

Use a value with the following logical fields. Names are proposed interfaces.

```text
MarkQuestionKey = (abstract initial slot, full match pattern, operation role)
QuestionView   = immutable map<MarkQuestionKey, RequestMarkSet>
QuestionDelta  = map<MarkQuestionKey, newly requested marks>
AnswerShape    = emitted initial with its concrete mark removed
PendingAnswers = map<MarkQuestionKey, map<AnswerShape, RequestMarkSet>>
```

The abstract initial slot contains base and path, STAR tail, empty E and abstract mark.
The full pattern preserves any additional query path, tail and exclusion that the
eligibility operation needs. The role retains differences between dynamic,
catalog and cleaner answer operations. Two questions that share an initial slot but
match different paths or use different answer operations are distinct. A canonical
key may omit a redundant pattern or merge roles only when equality of the complete
matching, answer and climb behavior is established.

An AnswerShape preserves the base, path, tail, exclusion and any annotation needed to
recreate the exact emitted InitialAp. EXACT, ANY and STAR are distinct. Do not cache
the incoming GroupKey or CallerRef as the answer shape. Request mark sets represent
independent questions. Packing them does not create an AND premise.

Normalize request initial E to empty. Apply supplier path, tail, E, mark exclusion
and layer eligibility before projecting to an answer shape. A new query geometry
must use the actual N2 input; an answer to a broader query is not a supplier witness.

### 3.3 Exact N2 ownership

Keep `CalleeSubscriptions` and its current exact accumulator. Admit the original
incoming Facts under its original group header. An exact-only fresh TAINT delta may
clear E; it must not define the accumulator key or replace the original header for
later query matching.

Retain the full caller MethodKey, singleton initial for a FLOW origin, caller layer
and call instruction where N2 already needs them. Do not collapse contexts or entries.
Two callers can emit the same initial and still need distinct summary subscriptions
and late-request routes. Register each fresh origin even if initial emission is a
duplicate. This change removes caller separation only from the emitter.

## 4. Initial emission

### 4.1 After run-1

At the current `addLink` boundary:

1. Check input layer equality and reject cleaner temporary bases as today.
2. For REACH, emit the existing zero initial.
3. Otherwise find comparable demand patterns using the existing fixed DemandStore.
4. Apply the existing full AP emission operation to each candidate and the input.
5. Submit each result to InitialFactStore. Release the input when processing ends.

No operation in this path creates a mark question. Preserve forward and backward
initial legality, cuts, records, return adapters, source effects and native behavior.
Repeated input arrivals can repeat intersection work. InitialFactStore still prevents
duplicate method starts. Do not add persistent input deduplication at the callee.

### 4.2 Run-1 input

Always emit the abstract initial first. For statics, this includes `(S, ., *, ...)`;
structural refinements from the precomputed static profile are additional initials.
Known query geometries include the input's emitted policy slot and installed profile
queries, even when a slot has no active concrete-mark interest. For these geometries,
evaluate the actual supplier using the existing AP
eligibility and answer operations. Compute eligible concrete answer shapes with
packed marks:

```text
for each eligible (question Q, answer shape A, concrete marks M):
    emit A with each mark in M intersect activeQuestions[Q]
    pending[Q][A] |= M minus activeQuestions[Q]
```

Do not enumerate all marks times all supplier origins. Project concrete leaves into
answer shapes as they are read and merge equal shapes. Questions not yet known do
not require speculative memo rows; their later admission replays N2. A memo entry
only certifies the query geometry against which it was evaluated.

Precomputed static mark checks and cleaner interests use the same admission path.
They are active interest, not evidence that an input contains a mark. Source and sink
NNF predicates use the same mechanism. Keep the unchanged-static suppression rule:
no dynamic static-base question is needed when initial and final fact match. Transfers
such as `x=C.g; C.f=x; check(C.f,T)` must still request T and refine `C.g` through the
actual supplier and request route.

### 4.3 New question marks

Compute `fresh = requested minus activeQuestions[Q]`. If empty, stop. Otherwise:

1. Add fresh marks to the authoritative active map.
2. For each matching pending shape, emit marks in its intersection with fresh.
3. Remove only those consumed memberships. Remove empty mark sets and empty shape maps.
4. Publish the fresh question delta for N2 replay.
5. Keep all requested marks active for future suppliers.

A request for `C.f:T` cannot consume `C.g:T`. A second request for T can still matter
when its query geometry differs. Replaying an already answered pair is harmless at
the initial deduplication boundary. Do not retain answered pairs to suppress replay.

## 5. N2 question replay

### 5.1 Publication and both arrival orders

Extend the existing callee publication channel with a current immutable question
root and question-delta notification. `SummaryStorage` is the integration point; a
small separate question publisher is also valid if it uses the same subscriber and
counted-delivery protocol. Keep one current authoritative root. Subscriber entries
must not copy the full question/mark relation.

Question keys and answer shapes are owned immutable descriptors; they do not retain
compiled forms or binding environments. Read the current root under the publication
boundary and match outside that boundary. A delivery contains its fresh delta, not
a retained list of prior complete views.

| Event | Required work |
| --- | --- |
| Fresh question marks | Publish before notifying; each subscribed caller owner queries its current N2 inputs |
| Fresh N2 input or origin | Admit original input, then replay current questions as well as existing summaries |
| First subscriber for a callee | Register before reading the current view and scheduling replay |
| Duplicate notification | Re-evaluate safely; active-question, N2 and initial deduplication prevent new semantic state |

Registration and publication must have a defined linearization order. A racing pair
must be covered by replay, notification, or both. A question may be published before
any caller subscribes; its current root must remain readable. Subscriber notification
must not read caller-owned maps from the publisher thread.

### 5.2 Request candidate visitor

Add an N2 visitor specifically for requests. The existing run-1 summary lookup uses
extensions only. A request also needs proper-ancestor suppliers, so it must query
prefixes and extensions around the full match pattern and deduplicate group handles.
Apply full geometry, E, mx and layer checks after candidate selection. An index hit
is not an answer.

For the first implementation, process one finite current-value visit on the N2 owner.
Visit index postings without building a list of all candidate Subscription/Facts
snapshots. A temporary handle set is permitted; measure its peak size. Read each
current accumulator value only when visiting it. Check cancellation during index,
group, path and mark traversal. Queue answers and climbs, including local results;
do not invoke callbacks that can mutate the directory during the visit.

Keep batching and bounded service opportunities between replay visits. A finite visit
is not a proof of a fixed replay latency or a strict leaf-work quantum. Record the
largest visit and service delay. If this prevents fair service or the run-1 time gate,
design and prove a resumable visitor with an explicit horizon before introducing it.
Do not retain old input roots or full candidate snapshots across yields as an implicit
replacement for the removed COW store. This CPU risk remains visible in acceptance.

### 5.3 Results

For TAINT, enqueue a concrete initial answer addressed to the full callee MethodKey.
For FLOW, enqueue packed mark requests addressed to the original caller MethodKey
and its singleton InitialAp. Respect incoming mark exclusions. Keep query initial E
empty, while the N2 eligibility check uses the original supplier E.

An answer carries only the resulting InitialAp and destination, not its supplier
payload. A climb carries only its destination initial and mark set. Merge outgoing
marks by the complete destination key. Question geometry remains available where
matching requires more than that destination initial.

Reserve counted replay/notification/answer/climb work before making it visible.
Reserve children before releasing the parent. A failed enqueue must release its
reservation exactly once. Empty method queues do not imply COMPLETE while replay
or delivery work remains. Preserve method-empty summary publication; questions use
the existing request service opportunities and do not wait for global quiescence.

## 6. ND candidates and report behavior

Local analysis can join contributions from different callers. Keep full joint
PremiseSets on source and summary results. Existing caller-side E6 application must
satisfy every member at one caller/call scope, with the correct contexts, entries and
layers. Independent requested marks never become a synthetic ND premise.

Remove the post-freeze raw-link `Support` scan. For the minimal implementation,
unproved witnesses keep `confirmed=false`; new report entries use `DEMAND`.
NORMAL sink layers and pooled availability are insufficient to set CONFIRMED.
Preserve an existing confirmed key, complete-forward replacement of the demand set,
incomplete-run report behavior, source-hit identities and handoff seed identities.
SinkWitness does not itself map every witness to its source hits.

The current JVM exporter returns Simple sink traces. This proposal does not add a
concrete trace validator. A DEMAND candidate may remain when NO_DEMAND_EDGE, policy,
timeout or OOM ends iteration. STOP_RULE applies only when no DEMAND entries remain.
Keep those rules; do not force new runs to wait for a future trace engine. Test the candidate's existing handoff
and seed path as well as its exported status.

ND sink end facts need an explicit acceptance case. If different callers supply
`x:T` and `y:U`, a pooled sink guard can generate `Return:V` under zero. Caller-side
ND matching cannot reject that zero-premise summary. Keep the specified zero-GEN
analysis semantics. Treat such derived results as candidates too. Later concrete
validation must check the guard's derivation at one caller, not only the final sink.
The local emission/replay proof does not establish a valid concrete execution for
this overapproximation.

Exact confirmation is a separate migration option, not a fallback callee store.
It would need targeted read-only support queries over N2 after runners join and
before subscription freeze. Today freeze clears N2 before driver Support runs.
Do not read released N2 or expand it into another global raw-link collection.
The minimal patch does not claim this optional confirmation mode is implemented.

## 7. Observation and counters

Replace the unresolved-call observer's added-fact store only after a focused oracle
states its observable contract. Proposed identity is `(full caller MethodKey,
call instruction, fact base, plan generation)`; the actor can omit its own MethodKey
from physical keys. Notify once per such identity at the existing unresolved TAINT
point. Keep the identity through any form rebuild that denotes the same plan; a
new generation must not accidentally suppress a distinct observation. No mark set,
Facts tree or caller premise belongs in this set. If repeated callback multiplicity
is observable, preserve it or isolate this cleanup until its contract is resolved.

Raw-store admission currently supplies a duplicate gate and demand-link statistics.
Do not continue to label arrival counts as distinct stored groups. Keep the semantic
fact that at least one DEMAND input was seen for the NO_DEMAND_EDGE guard; its presence
can be a flag folded into the existing demand-presence result, without an input set.
Keep existing distinct edge/summary counters separate. Verify that duplicates cannot
alter termination. Introduce these diagnostics where the work occurs:

| Metric | Purpose |
| --- | --- |
| Input arrivals, N2 fresh groups/leaves/origins | Distinguish transport duplication from useful new input |
| Restricted intersections; emitted candidates / new initials | Detect repeated work after deleting raw deduplication |
| Active question geometries and mark memberships | Show the size of persistent request interest |
| Pending shapes, mark memberships, consumed memberships; high-water values | Verify answer packing and consume-on-request |
| N2 replay visits, candidate handles, eligibility tests, answers and climbs | Detect a request-by-input work product |
| Maximum visit work/time; request service delay; queued answer bytes/counts | Detect starvation and transient memory migration |
| Observation identities and callbacks | Check the smaller callback gate |

Do not collect per-pair diagnostic histories in production. Counters must not recreate
the removed memory owner. Report N2 ownership separately from callee memo ownership.

## 8. Implementation sequence and gates

These steps define the implementation sequence and its acceptance gates. The linked validation report records executed checks.

| Step | Change | Required evidence before the next step |
| --- | --- | --- |
| AR0 | Accept revised specs and constructive local refinement model | Lean build and axiom audit; explicit proof scope; executable geometry and ordering controls |
| AR1 | Add a small independent history-based test oracle and observation oracle | Oracle uses scalar AP operations, not optimized memo/visitor code; expected outputs cover both orders |
| AR2 | Add question publication, caller replay and counted result delivery while the old path remains an oracle | Old/new emitted initial and climb sets agree for finite fixtures; registration races, duplicate/reentrant delivery and cancellation pass |
| AR3 | Add the question-specific packed pending memo and direct run-1 emission | Request-first/supplier-first and late supplier tests pass; consumed marks are removed; unrelated rows remain |
| AR4 | Stream restricted inputs; remove callee raw accumulation and Update callbacks | No callee input/history/index/COW owner remains; fixed-config F1/B2/F3 AP and record results agree under the same candidate policy |
| AR5 | Remove raw Support consumer; enforce DEMAND candidate policy; replace observer gate if its oracle passes | Report, seed, source-hit, end-GEN and termination tests pass; no reads after N2 freeze |
| AR6 | Remove dead APIs and representation tests; run common/JVM/shared-consumer gates | Keep AddedFactAccumulator for N2; no accidental N2 or transfer change; all new tests are discovered |
| AR7 | Repeat the fixed real-project gate and inspect owned heap/profile evidence | Location comparison, latest reached/completed/accepted iterations, run-1 engine time, OOM/timeout stage, memo/N2 ownership and replay cost are recorded |

AR2 dual execution is a test-only migration technique. Production must not keep both
input relations to support an always-on comparison. AR4 and AR5 form one releasable
change: a build that removes storage but leaves Support reading it is not acceptable.
Every intermediate experiment must identify its test mode and avoid benchmark claims.

Use the same confirmation policy for the history oracle and optimized engine. Removing
raw Support can turn a formerly CONFIRMED key into DEMAND, which can seed more keys
and change the next run's demand/configuration. Fixed-config transfer equivalence is
separate from comparison of those whole-driver sequences. The new policy's complete
driver must agree with its own reference, while project findings still face the
strict main comparison. Do not require unchanged later frontiers from an old
support-enabled driver and also require unproved witnesses to stay DEMAND.

### 8.1 Source change map

All paths below are under `org/opentaint/dataflow/bidi` in the dataflow module.

| Current source | Intended change |
| --- | --- |
| `engine/RunMethodAnalyzer.kt` | Remove linkStore and exposed links; stream emission; retain input guards, initial deduplication and demand-presence semantics |
| `engine/Run1Abstraction.kt` | Remove AddedFactStore dependency, freshLinks and captured phases; own active questions, compact pending answers and question publication |
| `engine/CalleeSubscriptions.kt` | Keep original input accumulation; add request-specific current-value candidate visit |
| `engine/SubscriptionManager.kt` | Replay questions on fresh input and first registration; receive question notifications on the caller owner |
| `engine/SummaryStorage.kt` | Publish one immutable current question view and deltas with registration/replay ordering |
| `engine/RunEvent.kt`, ports, handlers | Add counted question/answer delivery; retain full routing identity without supplier captures |
| `engine/MethodServices.kt`, `RunMethodLocalServices.kt` | Remove AddedFactStore.Update callbacks; keep mark admission, source/sink predicate evaluation and request service |
| `engine/RunMethodCallServices.kt` | Keep N2 admission and ND matching; replace the observation gate after its oracle passes |
| `engine/RunManager.kt`, `UnitRunner.kt`, protocol helpers | Integrate work accounting and release; keep summary batching and joined teardown |
| `driver/Support.kt`, `IterationDriver.kt`, `Report.kt` | Remove raw input consumer and preserve explicit candidate/confirmed, handoff and termination contracts |
| `store/AddedFacts.kt` | Retain CallerRef and exact accumulator for N2; remove callee/observer store, snapshot and index APIs once all consumers are gone |

No AP transfer table changes are intended. If an eligibility helper is extracted,
keep an independent scalar oracle and preserve arbitrary admitted headers. Delete
old store tests only where they test a representation with no remaining consumer.

### 8.2 Semantic test matrix

Use the [validation plan](../../spec/validation-plan.md) as the full inventory.
At minimum, compare exact sets of initials, climb destinations and requested marks,
not only finding counts. Include:

- No request, request first, supplier first, late supplier, duplicate request and
  request for a new geometry with the same mark.
- Exact, ANY and STAR tails; ancestor exclusions; exact-only deltas that clear E;
  FLOW mx; NORMAL/DEMAND layers; deep paths and cancellation inside traversal.
- Always-abstract static emission, direct static checks, `C.g` to `C.f` transfer,
  static request suppression only when initial equals final, and conditional sources
  as well as sinks and cleaners.
- Publication before registration, registration before publication, new origin for
  an old emitted initial, cross-unit and local delivery, and a callback that enqueues
  another question without mutating a live N2 traversal.
- Full ND source/summary premises, split callers and call sites, distinct contexts
  and entries, pooled sink candidates and zero-GEN derived candidates.
- F1/B2/F3 native/reversed records, seeds, source hits, sticky CONFIRMED entries,
  latest complete forward DEMAND replacement, and incomplete barriers.
- No lost work at method-empty summary flush, request replay, cancellation, failed
  enqueue or final join; every reservation released exactly once.

### 8.3 Project and memory gates

Use `runProjectAnalyzer` directly with the existing OWASP CI and selected Conductor,
ThingsBoard and Stirling-PDF settings. Do not change memory, timeouts, field limits
or entry configuration to obtain a passing result. Compare strict locations to main
and separate allowed unverified candidates from confirmed traces. Existing location
losses in the latest measurements remain open; this redesign does not resolve them
by changing the acceptance label.

For every project report, include latest reached iteration and direction, last
completed engine, last accepted forward report, each run's engine time, and the
failure stage. Compare runs at matched iteration/report frontiers and identify
instrumentation overhead. Keep run-1 time separate from setup and report barriers.

Capture profiles under load and an owned OOM snapshot when required by task.md.
Clean up obsolete owned captures before new tests. Verify no strong callee supplier
store, COW view, pending supplier collection or callback capture remains. Measure
active/pending memo and delivery queues alongside surviving N2, rather than claiming
the old retained union as saved bytes. Do not infer a universal small-state bound or
a 60-second guarantee from the local proof or one method census.

## 9. Proof boundary and implementation risks

The constructive [emission and replay model](../../spec/lean/ApSpec/AddedFactsRemoval.lean)
and [packed memo model](../../spec/lean/ApSpec/AddedFactsRemovalMemo.lean) check the finite
emission and request protocol against a history-based concept. The explicit exact-replay premise represents retained N2
inputs. Concrete adapters and witnesses connect the protocol to the AP operations;
the proof status records their restricted scope. The model does not verify Kotlin
ownership, concurrent publication, a full annotated engine, ND trace validity,
heap bytes or wall-clock time. Those require the gates above.

Checked on 15 October 2026 with `lake build` and `lake env lean audit.lean`
from `spec/lean`: 120 build jobs passed; 19,879 declarations had zero unexpected
axioms; all executable certificates passed. The 45 explicit new theorem audits
use only `propext`, `Quot.sound`, or no axioms. No analyzer implementation or
project measurements changed during this check.

The main risks are repeated intersections after removing the old duplicate gate,
request-by-input replay cost, an unexpectedly large pending-answer relation, and
transient queues replacing persistent storage. Finite owner-local visits avoid
input snapshots but can have long latency. These risks require measurements and
separate design if they dominate. They do not justify restoring a hidden callee
input history or changing the reference manager in this iteration.
