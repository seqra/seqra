# Run-1 mark request redesign

Date: 15 October 2026. Status: design proposal; no analyzer changes or new runs.
This replaces the request protocol proposed in P5, P6 and P9 of the
[previous proposal](bidirectional-next-iteration-27e22bb1e-20261015.md). It also
replaces the full-query, role and field-exclusion assumptions in the earlier
added-fact-removal request design. Other analyzer changes need separate selection.

The measured implementation is `27e22bb1ed871651143538783473cb0d4fcbcf3e`.
The current documentation HEAD has the same analyzer implementation.

| Project | Latest reached | Last completed engine | Accepted report | Run-1 engine seconds |
| --- | --- | --- | --- | ---: |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 | 331.657 |
| Conductor | run-2, backward; OOM | run-1, forward | run-1 | 60.210 |
| ThingsBoard | run-1, forward; OOM | None | None | 279.104; incomplete |
| Stirling-PDF | run-4, backward; OOM | run-3, forward | run-3 | 36.102 |

The target is a completed run-1 engine within 60 seconds. Prescan and reporting
are separate. The [current report](../reports/bidirectional-added-facts-removal-performance-20261015.md)
contains the fixed configurations, findings and capture limits.

The actor aggregation is `report-preparation/thingsboard-facts-versus-auxiliary-20261015/index.json`
under evidence root B defined in that report. The source review is
`report-preparation/grammar-state-cardinality-review-20261015/index.json`.
Both read the retained evidence for the measured implementation; neither is a new run.

## 1. Problem and selected scope

ThingsBoard has 5,803,728 stored caller-input groups at capture. Its terminal
counters record 155,573,488 request replay visits, 1,606,614,611 FLOW climb callbacks
and 1,470,265,587 duplicate request-mark admissions. These have different units and
time scopes; they do not count distinct facts or predict a speedup. Request replay
appears in 44.00% of sampled Java CPU and 40.15% of sampled allocation in run-1.

Current: requests are expanded into per-mark work before destination deduplication.
The abstraction and summary publisher also maintain separate cumulative request sets.

New: use a shared authoritative request store and a temporary per-method collector,
merge mark sets throughout, submit at method-queue-empty, and filter already admitted
requests before per-mark propagation. Skip upstream request propagation for concrete
initials. The abstraction reads only the shared store.

Why: repeated contributions become one pending set, and repeated admitted requests
need no transport or receiver work. The design retains the caller inputs required
for summary application and for replay when a request arrives later.

This is a proposal, not an implementation or a completed proof. The static/dynamic
coverage proofs specified in section 4 are required before implementation. No unit
barrier, new scheduler, buffer quota or overflow/rescan protocol is selected.

## 2. Request grammar and matching

Requests exist only in run-1 and request concrete marks only:

```text
StaticSlot(classAccessor, fieldAccessor)   // abstract initial (S, C.f, *, {}, *)
BaseSlot(nonStaticBase)                    // abstract initial (b, ., *, {}, *)

requests[full MethodKey][slot] -> MarkSet
```

The full MethodKey includes entry and context. Exclude zero and cleaner temporaries.
There is no separate full query, tail, field exclusion E, mark exclusion, rule role,
caller or call site in a request key. Requested marks are independent alternatives,
not an AND premise. Source/sink occurrence identities and proofs remain in their
existing rule stores.

Ignore incoming field exclusions E and FLOW mark exclusions during request matching
and propagation. A request has neither kind of exclusion. Excluding T already emits
a request for T on the original abstract initial. Forwarding T again therefore adds
only a duplicate to that initial's merged request set. Section 7 explains the union.
A FLOW supplier never gives a concrete answer; a concrete answer still requires an
incoming concrete fact carrying the requested mark.

Requests target abstract-mark initials only. A fact with a concrete-mark initial
does not generate or propagate a request to that initial. Its concrete analysis
already evaluates mark conditions directly. Use the actual fact's premise, including
the CallerRef premise for a call input; do not infer it from the enclosing method
analysis. A concrete current fact never propagates a request upstream. It can instead
answer a matching callee request with its concrete mark. Answering a request and
propagating it to an earlier abstract initial are separate operations.

Match the two slot forms directly. A non-static request matches a nonempty supplier
on its base. A static request matches its class/field or a broad ancestor supplier;
an exact ancestor leaf cannot cover a descendant. Reject other bases/classes/fields
before creating replay work. Tree breadth can still be large despite the depth limit.

| Concrete supplier match | Emitted concrete initial |
| --- | --- |
| Non-static exact leaf at base root | Base root, EXACT tail |
| Other matching non-static leaf | Base root, STAR tail |
| Static exact leaf at C.f | C.f, EXACT tail |
| Static ANY leaf at C.f | C.f, ANY tail |
| Static broad leaf strictly above C.f | Requested C.f, STAR tail |

Each answer carries the matched concrete mark and empty E. Run-1 emits no ANY_TAINT
initial. An adapter exposing must-taint must normalize it to ANY. Define and test this
projector independently of scheduling.

The special `(S, ., *, ..., *)` summary seed is separate from these slots. Keep its
ordinary code transfer and summary role, skip its rule evaluation, and emit no requests
from it. Do not suppress ordinary field branches packed into the same tree.

Assume that an exact tainted static base `(S, ., $, {}, T)` is impossible. This
assumption does not make broad root/class ANY suppliers impossible. Section 4 must
cover those suppliers without introducing a root request slot.

It also does not exclude an exact class-point fact `(S, C, $, {}, T)`. The current
rule language permits AssignMark on ClassStatic(C). That is a concrete input case,
not a third abstract-initial/request shape. It needs the direct preservation path
specified below; projecting an exact class point onto C.f would be incorrect.

## 3. Which needs are handled after prescan

Here, **precomputed** means derived from the complete prescan result. It does not mean
that every request whose current fact has a static base can be handled this way.

### 3.1 Build the static plan

Prescan has already resolved the complete call graph. Do not resolve calls again.
Inspect effective entry, statement, call and exit forms, including native models and
generated/lowered forms. Collect source and sink conditions, not sinks alone.

| Information collected per method | How it is used |
| --- | --- |
| Static code reads/writes and static rule/action positions | Select normal static-field abstract initials; no dynamic position requests |
| Concrete-mark leaves of static source/sink conditions, including conjunctions and effective NNF predicates | Record the field slot and required mark |
| Concrete static input-mark gates in source/pass/transfer microedges | Record the field slot and required input mark |
| Selected-mark static cleaners and generated rewriters | Record the required mark and affected static scope, including conservative wide needs imposed by the lowering |
| Concrete class-point source/sink/gate positions permitted by the rule language | Preserve their direct concrete handling; do not project an exact class point onto a field |

A deeper rule position is projected onto its run-1 class/field slot. Keep its actual
condition in the rule form; projection only requests analysis and does not prove the
condition. Merge repeated marks and merge source/sink/gate/cleaner contributions.
Do not expand NNF into cubes.

Compute transitive structural positions and mark needs on the existing call graph,
including recursive components. For ordinary unchanged static positions:

```text
used[M] = directUsed[M] union union(used[callee])
plannedMarks[M, C.f] = directMarks[M, C.f]
                      union union(plannedMarks[callee, C.f])
```

Take a conservative union over effective prescan contexts where needed. The plan is
frozen before run-1. Runtime methods/calls must belong to this prescan result.

Wide class/base needs remain immutable plan metadata until resolved onto relevant
normal field slots. They are not class/root request keys. Their expansion must cover
actual and later incoming fields as well as fields in the code/rule footprint.
For example, a cleaner on C.f with a whole-tree mark exclusion can require analysis
of an unrelated incoming C.g/T. A literal-f-only plan is not sufficient for that case.
Specify that conservative expansion and prove it under section 4. Do not silently
ignore a wide need or broadcast every mark to every project field.

### 3.2 Install and use the plan

After prescan, each method has a shared immutable structural/mark plan. When its
SummaryStorage is created, seed its authoritative RequestStorage from the resolved
field-slot part of that plan. Reuse immutable mark sets and plan structure where
possible; do not eagerly create stores for every possible MethodKey.
Initialize planned membership before exposing the storage to subscribers. Later
field activation uses ordinary temporary collection and commit-before-notify.

For needs whose field slot becomes relevant only when an input arrives, instantiate
the already-known plan contribution into that method's temporary collector. Submit
it at the same method barrier. Until then it is pending, not admitted. The late-request
replay in section 8 covers inputs that arrived before this submission.

For a new static input, the abstraction:

1. Emits the special abstract static root needed for summaries.
2. Emits normal field-slot abstract initials selected by the transitive structural plan.
3. Adds any newly instantiated precomputed field-mark needs to the temporary collector.
4. Reads admitted requests from SummaryStorage and emits matching concrete initials.

An exact concrete class-point input is already precise. Preserve it by directly
starting its concrete class-point initial, without a new abstract class initial or
class request key. The independent projector/coverage proof must include this path
and any broad supplier needed by an existing class-level condition. Static plan
metadata identifies class-level rule needs; it is not a third runtime request store.

An unchanged static mark need can be omitted from dynamic collection when its original
initial slot equals the checked/projected static slot and the required membership is
already in the authoritative store. A metadata declaration alone is insufficient.
Do not skip normal rule outputs or cleaner effects on ordinary field facts.

The transitive plan means callers already know unchanged static needs. Their stored
inputs still need inspection where a callee input came from a different original slot.
The plan does not establish that such an origin already carries the required mark.

### 3.3 Requests that remain dynamic

The dynamic routes below require an abstract-mark original initial. Check that
before collecting marks or doing an upstream request join.

| Situation | Required dynamic handling |
| --- | --- |
| A mark check, concrete input gate or selected cleaner needs T under a non-static original initial | Collect T for that original BaseSlot |
| Analysis started from C.g and reaches a check on C.f, with g different from f | Collect T for original StaticSlot(C,g) |
| A static input reaches a non-static checked position, or a non-static input reaches a static checked position | Keep the original initial slot; the current checked base is not the request origin |
| A callee request matches a caller FLOW input | Route eligible marks to the full caller MethodKey and its original normal slot |
| A runtime need is not covered by installed planned membership | Retain its normal dynamic route and merge it into the temporary collector |
| A fact has a concrete-mark initial, or the current fact is concrete | No upstream request; evaluate concrete marks directly and retain concrete answers |
| The special static-root summary seed | Skip rule evaluation; no root request |

Example:

```text
x = C.g
C.f = x
check ContainsMark(C.f, T)
```

The precomputed f/T need does not replace the dynamic g/T need discovered while
analyzing the g initial. The same principle applies across method boundaries.

Static and dynamic sets may overlap. Authoritative admission deduplicates them; the
coverage theorem requires their union to cover every required request, not a disjoint
classification of otherwise equal memberships.

## 4. Required proofs for the static/dynamic split

These proofs are implementation prerequisites. This proposal states the obligations;
it does not claim that the measured implementation or existing tests prove them.

Use these assumptions explicitly:

- Run-1 has only the two normal abstract-initial forms in section 2.
- The special abstract static root is a summary seed and performs no rule evaluation.
- Exact `(S, ., $, {}, T)` is impossible. Do not extend this to ANY/ANY_TAINT suppliers.
- Prescan resolves the complete call graph and visits every effective rule/model form.

The main theorem is:

```text
Every concrete normal-slot initial needed by a run-1 mark-dependent operation
is enabled by an admitted precomputed request or by a retained dynamic request,
under either ordering of request and incoming input.
```

Exact class-point inputs use the separate direct concrete path above; prove that
path's preservation alongside the request-coverage theorem.

Prove it through the following obligations:

1. **Producer completeness.** Enumerate all primitive request producers: source/sink/
   conjunction mark checks, concrete microedge input gates, and selected-mark cleaners
   including generated rewriters. Show that entry/exit/native/call/lowered forms use
   these cases. Propagation forwards their needs; it is not an omitted producer class.
   Show separately that zero-generation and remove-all cleaners add no mark requests.
2. **Precomputed coverage.** Show that every unchanged-static need of those producers
   is included in the post-prescan plan, including recursive call-graph closure,
   effective conditions, conservative wide cleaner needs and later field activation.
3. **Dynamic residual coverage.** For a producer not discharged by installed planned
   membership, show that the original normal slot and mark are collected dynamically.
   Include C.g -> C.f, non-static/static transfers and caller-origin changes.
4. **Static-root safety.** Under the stated impossible-exact-root assumption, show
   that omitting root rule evaluation loses no required field concretization or
   valid result. No root request key may be introduced as a shortcut. Root-origin
   FLOW links must be covered by the normal field/base analyses or a proved static
   identity route; do not guess an origin from the transformed output field.
5. **Broad supplier coverage.** Root/class ANY suppliers still need projection onto
   relevant normal field slots. Define the complete relevant-slot set and show that
   no valid concrete answer disappears. Ignoring exclusions in the request join and
   requiring actual concrete marks for answers must agree with the independent
   request matcher. Separately prove preservation of exact
   class-point taint, which is legal and is not excluded by the empty-root assumption.
   Neither discard it nor broaden it into field taint. Its direct concrete path must
   cover class-level conditions and subsequent caller/summary use.
6. **Summary safety.** Prove both removal of cleaned C.f/T and preservation of
   unaffected C.g/T. Additional concrete analyses alone do not prove that an
   unrestricted root summary cannot reintroduce a removed mark. Cover ordinary,
   native and opposite-direction persisted summary reads in later runs too.
7. **Arrival-order coverage.** Prove the input/request ordering law in section 8,
   including a precomputed need materialized after an earlier input.
8. **Concrete-initial safety.** Show that concrete-initial analyses evaluate their
   actual marks without requesting a new initial. Concrete current facts, including
   source-generated marks, must still answer matching callee
   requests. Show that skipping their upstream request propagation loses no answer
   or required analysis; do not discard their ordinary transfers or summaries.
   Preserve the existing K6 invariant: a FLOW fact has a single abstract-mark premise,
   and concrete facts retain their actual zero/concrete/multiple-concrete premise.
   An invalid premise/fact pairing remains an invariant failure, not an ignored input.
9. **Excluded-mark redundancy.** Show that excluding T already collects or admits
   T for the same full MethodKey and original slot. Include generated rewriters and
   exclusions carried through summaries and calls. Thus forwarding an excluded T
   leaves the eventual request union unchanged. Pending requests use the normal
   method barrier; no exclusion-specific tracking is needed.

Use an induction over request producers and call propagation to connect these
obligations. Independently check that no accepted transfer introduces the forbidden
exact tainted static base, or document it as the theorem's external assumption.
Do not claim a proof by comparing only with today's full-query implementation.

## 5. Two instances of the same request-storage class

For each method use the same `RequestStorage` class in two places:

| Instance | Owner and purpose | Permitted use |
| --- | --- | --- |
| Authoritative requests | Inside SummaryStorage; cumulative admitted requests | Union admission and immutable reads by abstraction/propagation |
| Temporary requests | Inside the method actor; contributions since last submission | Producer writes/merges, then commit and replace at the method barrier |

The temporary instance is write-only to producers. It is not queried as a second
membership oracle and does not retain previously submitted requests.

```text
RequestStorage.addAll(slot, MarkSet)
RequestStorage.borrow(slot) -> immutable MarkSet  // authoritative instance only
SummaryStorage.commit(requests: RequestStorage)
```

Use owner-only access for the temporary instance. SummaryStorage synchronizes the
authoritative instance. The same storage class need not own thread synchronization.
Keep RequestStorage free of callbacks. Independent marks always union.

At the method barrier:

```text
summaryStorage.commit(temporary)
temporary = new RequestStorage()

commit(requests):
    fresh[slot] = requests[slot] - authoritative[slot]
    authoritative[slot] union= fresh[slot]
    enqueue notifications for nonempty fresh sets after releasing the lock
```

Commit reads the submitted instance synchronously. It stores immutable admitted sets
and queues one immutable grouped slot/mark batch per subscribed caller runner. It does
not retain the mutable temporary store. Empty fresh results produce no notification.

Notifications use the existing queued delivery, including local delivery. They do
not reenter the method actor during commit. Only that actor writes its temporary
store, so it can replace the instance immediately after commit returns. Contributions
from later events go into the new instance. No takeBatch or destructive-drain API is
needed.

Only authoritative requests are read by the abstraction and early propagation filter.
This intentionally permits equal proposals before admission. They merge in the temporary
store; final admission still creates each cumulative membership once.

## 6. Use the method-queue-empty barrier

A method's fact queue becoming empty is a valid default barrier. It already matches
normal summary batching. No evidence here establishes that a unit barrier is better.

The previous argument about other methods submitting the same targets was insufficient.
For example, two callees can discover needs that both map to one caller's original slot.
Those contributions can merge in that caller method's temporary RequestStorage. If its
barrier occurs between arrivals, there can be two submissions; authoritative admission
handles overlap. That alone is neither a correctness issue nor a demonstrated reason
for a unit-wide barrier.

Select this simple policy:

1. Local rule evaluation merges requests into its method's temporary instance.
2. A request for another method is a normal counted batch event to its owner. Replay
   effects remain queued even within the same unit; do not reenter an input-store visit.
3. The receiver merges into the target method's temporary instance and schedules its
   existing method Work. A request-only method needs no synthetic fact or zero seed.
4. When the fact queue is empty, flush normal summary publications, call
   `summaryStorage.commit(temporary)`, then set `temporary = new RequestStorage()`.
   Commit secures its queued notification deliveries before returning.
5. If queued effects create facts, resume fact work. If temporary requests remain, submit
   another batch. Report method completion only after all method-owned work is empty.

Keep the existing Work reservation through commit and replacement, and arrange all
child events before releasing it. This uses the current work/completion mechanism;
it needs no new mailbox, unit wake latch, registration lane, version cursor or
scheduler priority.

The policy does not promise a maximum request delay while a method remains busy. Measure
that delay and temporary-store sizes. An earlier barrier is a later option only if those
measurements show a problem; ordinary scheduling quanta and cancellation remain in place.

## 7. Reduce propagation before expanding marks

Current: FLOW replay invokes one climb callback per eligible mark and often queues
requests already admitted by the destination. The proposal did not explicitly state
the early concrete-initial guard.

New: first inspect the originating initial's mark. If it is concrete, skip upstream
request propagation before slot matching, mark scans or target lookups. If the current
fact is concrete, it can supply an answer but also needs no upstream request. Only
FLOW facts under abstract-mark initials enter the propagation path.
Use the actual edge/input premise. A source fact created while a method is processing
an abstract initial still keeps its own actual premise; the method's active analysis
does not make that concrete fact an abstract dependency. Preserve the K6 checks.

The old notation `mx` meant `FlowTree.markExclusion`: marks excluded from the current
abstract FLOW fact. It does not belong in this request join. When an operation
excludes T, it immediately requests T on the original abstract initial. A later
request can therefore include T without changing the eventual stored request set:

```text
request emitted when excluding T:  {T}
later propagated request:         {T,U}
merged requests for the initial:  {T,U}
```

Subtracting excluded marks from the later request gives the same union. Omit that
subtraction and its per-FLOW set operations. Destination admission handles duplicates,
including duplicates of a request still waiting in the temporary collector.

After these guards and slot eligibility, compute the contribution for the original
abstract initial:

```text
validate the existing premise/fact invariant

// Concrete-answer handling is independent of the upstream branch below.
if incomingFact is TAINT:
    handle matching concrete answers

// Upstream request propagation only:
if originalPremise is not InitialAp or originalPremise.mark is Concrete:
    skip upstream request propagation
if incomingFact is not FLOW:
    skip upstream request propagation

useful = offeredMarks - targetAuthoritativeRequests[originalSlot]
```

`offeredMarks` is the callee's requested mark set for the matched slot. For
`offeredMarks = {T,U,V}` and already admitted `{T,U}`, only V needs a new upstream
contribution. If T is still pending locally, a forwarded T merges with it at the
owner. Neither field exclusions E nor FLOW mark exclusions are consulted.

Read only the authoritative store. Do not query the write-only temporary collector.
A stale read can cause an extra proposal, which union/admission handles safely.

Merge contributions for the same destination method/slot before sending a batch.
Do not first expand T/U/V into separate callbacks and events. Union their requested
marks directly, regardless of differences in FLOW mark exclusions. Set operations
still inspect members; count those scans separately from avoided callbacks/transport.
Use no persistent request-by-input seen-pair table or dependency cache.

Batch changed slots for one callee and visit each relevant stored caller-input group
once for that batch. Union marks from matching slots before routing the result. A
fresh FLOW input/growth under an abstract initial also checks current authoritative
requests. Keep the ordinary input-store summary application and its full
caller/call-site/layer/joint-premise identity unchanged. Ordinary summary matching
and rule evaluation still use their original exclusion rules. Ignoring exclusions
for requests never makes a FLOW fact evidence that a concrete mark is present.

## 8. Handle late requests without a deferred-answer memo

The **caller input store** means the existing exact inputs retained by caller-owned
summary subscriptions (`CalleeSubscriptions` in the implementation). It holds actual
call inputs and provenance, not another request relation.

| Arrival | Request-specific handling |
| --- | --- |
| New concrete input | Abstraction reads authoritative requests and emits matching concrete initials |
| Newly admitted request marks | Concrete inputs answer; FLOW inputs propagate only under abstract-mark caller initials |
| New FLOW input/growth under an abstract-mark caller initial | Join with current admitted requests and route useful marks to original caller slots |
| Concrete-mark caller initial | Skip upstream request propagation; preserve concrete answers and ordinary summary work |

A TAINT input supplies a mark to the callee; it does not request a mark from its
caller. Preserve this answer path even when the caller initial is concrete. Apply
the initial-mark guard to upstream propagation, not to the concrete-answer path.

Enforce this production ordering:

```text
register the summary subscriber
insert/update its caller input store
deliver the incoming input to the callee abstraction
```

Request commit precedes notification. Let A be input admission, E the abstraction's
request read, Q request commit, and V late-request replay. Require `A < E` and `Q < V`.
If Q precedes E, the abstraction answers. Otherwise the caller input store already
contains the input when the late-request notification runs. Both arms cannot miss:
that would require `A < E < Q < V < A`.

Thus the callee needs no pending concrete-answer memo or supplier history. Omit the
redundant fresh-input TAINT request replay in the caller; keep its normal summary
replay. Remote delivery can answer twice, and InitialFactStore deduplicates those
initials. Direct/manual incoming inputs must not bypass the ordering contract.

Keep authoritative requests after an answer so later suppliers and other callers can
answer too. Always emit the abstract initial; concrete answers are additive. Ordinary
zero/source-generated internal facts remain normal transfers, not incoming suppliers.

## 9. Memory policy and expected impact

Do not add hard pending limits, emergency admission, backpressure, resumable scans or
rescan-all fallbacks in this iteration. The measurements do not establish a need for
them. They would complicate the request protocol before measuring this simpler design.

The temporary collector stores one merged mark set per slot and is replaced after
method submission. Track its peak slots, mark cells and time before submission.
This is a measurement requirement, not a fixed resource guarantee. If it becomes
a demonstrated problem, propose a targeted change later.

| Priority | Current versus new | Expected effect and limit |
| --- | --- | --- |
| 1 | Per-mark climbs before destination dedup -> abstract-initial guard, then set contributions minus admitted marks | Skip ineligible propagation early and avoid repeated callbacks and transport; eligible fraction needs measurement |
| 2 | Per-question replay -> batched changed slots and method submissions | Fewer repeated visits and messages; no assumed unit-barrier benefit |
| 3 | Two cumulative request copies -> one authoritative copy plus temporary writes | Avoid duplicate cumulative reconstruction; temporary data is pending only |
| 4 | Speculative concrete scans/memo -> new-input read plus late-request input replay | Remove the deferred-answer memo under the ordering proof |
| 5 | Generic full query/role matching -> two-form slot matching | Fewer keys and cursor objects; static/dynamic coverage must first be proved |

The old local request union is 575,705,904 bytes, shared request union 510,728,744,
pending-answer union 98,508,808, and immutable request arrays 910,444,952 shallow bytes
in ThingsBoard. These selections overlap; do not sum them as savings. Compare whole
and combined request owners, including temporary data. Required admitted memberships
and caller inputs remain. No speedup or 60-second success is established yet.

## 10. Implementation sequence and validation

1. Specify and prove the post-prescan plan, static/dynamic coverage theorem and root
   rule-evaluation policy under the exact-static-root assumption. Define the independent
   two-slot matcher/projector, including broad suppliers, concrete answers and the
   redundancy of requests for excluded marks.
2. Use the same RequestStorage class for authoritative SummaryStorage requests and
   the write-only per-method temporary collector. Seed the shared store from the plan;
   remove separate cumulative abstraction requests and request roles/full patterns.
3. Connect temporary collection and method-queue-empty commit-and-replace to existing
   method Work, grouped queued notifications and completion accounting. Add no unit
   barrier.
4. Enforce summary-subscription/input admission before callee input delivery. Read only
   authoritative requests in abstraction. Remove the deferred-answer memo and duplicate
   fresh-concrete-input request replay after the arrival-order controls pass.
5. Skip upstream propagation for concrete initials and concrete current facts before
   doing request joins. For abstract-initial FLOW inputs, replace scalar expansion
   with early admitted-set filtering, merged contributions and batched
   slot/input visits. Preserve concrete answers and ordinary summary/proof logic.
   Ignore field and FLOW mark exclusions in request joins and propagation.
6. Remove obsolete request code and counters. Verify that the temporary instance is
   replaced after commit, not a hidden cumulative mirror. Use descriptive names in
   new APIs/reports. Count concrete-initial skips and retained concrete answers.
7. Run focused tests and then the same four fixed project configurations with a fresh
   main comparison. First assess ThingsBoard run-1 work reduction; report every reached,
   completed and accepted iteration and strict findings. No runtime tests are added or
   rerun merely for this documentation revision.

Required controls include source/sink AND/OR predicates, microedge gates, cleaners and
rewriters; recursive call-graph closure; C.g -> C.f and argument/static transfers;
root/class ANY suppliers; ignored E and FLOW mark exclusions; missing concrete marks
not producing answers; cleaned f/T versus unchanged g/T; later persisted summary
reads; both input/request orders; late callers; input
growth; exact class-point taint and class-level rules; concrete-initial propagation
skips with preserved late concrete answers; actual source/summary/call premises and
K6 checks; exclusion-emitted requests merging with later T/U requests under either
commit order; two FLOW routes with different excluded marks; request-only method
Work; queued events after commit-and-replace; duplicate temporary unions; stale
authoritative reads; local queued effects; cancellation/rejected sends; completion
immediately after a final flush; and fresh-run isolation.

Use a scalar oracle for the new policy. Request membership proves neither a rule
condition nor candidate confirmation. Compare strict finding locations and actual
engine/barrier times; an earlier OOM or smaller incomplete report is not an improvement.

IR storage, cache/reference managers, WeakIdentityCache, normal bound-rule retirement,
fact representation and a broad scheduler rewrite remain outside this request design.
