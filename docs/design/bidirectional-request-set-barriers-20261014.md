# Run-1 request sets and processing barriers

Date: 14 October 2026. Status: implemented; core validation and fresh fixed-project testing complete.
Implementation: `a23ad7fcd99553190c82557cff6076503959aec6`.

Use the two optimizations together: retain requests as mark sets and normally delay their expensive
joins until a method drain point. Add a bounded flush opportunity at a nonzero Work quantum boundary
for a method that stays busy. The first change reduces repeated work for each mark. The second gives
several requests and link updates time to form one batch. A unit-empty barrier is a possible later
policy, but it must not be the only way to release requests while other methods keep that unit busy.

This proposal follows the [run-1 investigation](../reports/bidirectional-run1-mark-propagation-20261014.md).
The user has selected request batching and the method barrier for implementation. This does not select the older N3
fairness, queue-layout or general mailbox proposal. N1/N2 are already implemented. IR and concrete
origin-vector propagation remain outside this change.

## Implementation validation

The implementation passes 1,370 common-core tests and 377 JVM tests. The Go, JVM SAST and root
test sources compile. The Lean build and full audit pass, with no unexpected axioms. The new local
proofs cover the request frontier and Work completion laws; they do not prove the whole engine.

The complete iteration fixture reaches and completes **run-3, forward, limit 2**, after limits
`[1, 1, 2]`. It compares full witnesses, support annotations, summaries, source hits, native records
and the final report with an independent scalar reference. The fresh main/control/optimized batch
is complete; see the [current performance report](../reports/bidirectional-request-set-barriers-performance-20261014.md).
The optimization preserves control output but does not improve completion. The next target is
run-1 engine completion within 60 seconds; only Stirling-PDF meets it in this batch. Latest reached
runs are OWASP run-3/forward/limit 3, Conductor run-2/backward/limit 2, ThingsBoard run-1/forward/
limit 1 and Stirling-PDF run-4/backward/limit 4. The table below remains historical selection evidence.

## Evidence used to select this change

The latest measurements use runtime `61fd97acd792f2200b2e4a5746185d740d87712b`. The documentation HEAD
at the start of this proposal is `38b076ae5e30d3f38f3860ed18943102fbf074ee`.

| Project | Latest reached iteration | Last complete engine | Last accepted forward report | Result |
| --- | --- | --- | --- | --- |
| OWASP | run-3, forward, limit 3 | run-3 | run-3 | COMPLETE |
| Conductor | run-2, backward, limit 2 | run-1 | run-1 | OOM |
| ThingsBoard | run-1, forward, limit 1 | None | None | OOM |
| Stirling-PDF | run-4, backward, limit 4 | run-3 | run-3 | TIMEOUT |

ThingsBoard **run-1, forward, limit 1** has about 419 million climb actions and 40.2 million standing
mark memberships. Its 107,231 mark rows average 375.08 requested marks. The guard heap contains
11,665,656 `RequestIn` objects; its physical queues contain 11,171,499 entries. The corresponding
9.95 million processed fact deltas use a different work unit and do not establish a CPU-time split.
The grouped request-store retained union is 2,485,698,088 bytes. Queue owner unions overlap that
measurement and other owners; do not add them to estimate savings.

The queue comparator accounts for 46.65% of ThingsBoard run-1 sampled allocation weight.
Reducing request events could reduce that traffic without changing the comparator. No percentage
of runtime or retained memory is claimed before implementation and retesting.

N1 already groups **stored** marks by origin. It still calls the existing-link join for each fresh mark
and sends one scalar event for each climb. This proposal changes the execution of the relation.
It cannot remove its required logical memberships merely by changing the event representation.

## Current versus new behavior

| Change | Current | Proposed | Why it should improve |
| --- | --- | --- | --- |
| R1: mark-set joins and sends | Each mark scans existing links; overlapping FLOW leaves generate separate scalar climbs | Scan a link group once for a requested set; combine climbs for the same target origin | Fewer geometry walks, Patterns, action objects, events and handlers |
| R2: method request barrier | New requests and new link leaves immediately invoke request joins | Admit changes promptly, then join their pending changes after the method's fact work and publications drain | Larger batches; combine repeated link updates and duplicate climb targets before sending |
| Unit-empty variant | Requests do not wait for unit idleness | Flush requests when ordinary unit work is locally drained, with another progress trigger if it stays busy | Potentially larger batches across methods, with more delay and more protocol state |

R1 and R2 are one recommended algorithmic change. A set-valued event sent immediately with one member
will achieve little grouping. A barrier that later expands everything into scalar events will retain
most transport cost. Their expected memory impact is mainly on transient request events and temporary
join data. The standing relation remains necessary. Batch widths, repeated targets and net buffer cost
are unmeasured, so 375 marks per standing row is not a promised 375-fold reduction.

## Exact request key and ownership

A mark request batch has the meaning:

```
(run owner, full target MethodKey, canonical requesting InitialAp) -> requested mark set
```

The full method key includes its analysis context and entry. The initial retains its base, complete
path, tail, exclusion and abstract mark. Current request guards require STAR, empty field exclusion
and unexcluded STAR mark. Keep that guard; do not reduce the key to the current conclusion's base/path.
The run and method are implicit in an actor-owned standing row.

The set `{P,Q}` means two independent refinement requests. It does not mean a conjunction or a
multi-mark initial. Each concrete answer still creates its own canonical singleton-mark initial.
Do not use `PremiseSet` as the batch container. Requests from source conditions, sink conditions,
ordinary edges and cleaners can share the same refinement row; their proofs and effects retain
their existing separate owners. Requests remain standing until the run ends, including after an
answer. A later link or caller can still require another answer or climb.

Keep position requests separate, with their full normalized static path and requesting origin.
Their handling and transport can remain scalar in this first change. They must still see new links.
Provide a position-only candidate visitor so preserving that path does not require enumerating all
standing mark memberships.

Only a method's owning unit mutates its standing rows and pending joins. An outgoing event owns an
immutable mark snapshot. Do not send a mutable builder or a live standing row. Do not intern every
pending mark-set version through `ApManager`: its strong canonical table would retain temporary
prefix sets. Use request-owned primitive storage and detach a snapshot when sending. Keep the original
managed-reference implementation; no new cache or reference-manager rewrite is part of this design.
An owned immutable snapshot can be shared by batches with different targets. For example, an empty
link mark exclusion needs no copied subtraction result. Mutable frontier builders cannot be shared.

## Join link groups, not individual marks

For a fresh request set `M` on initial `i`, query nearby raw link groups once. Keep every group's full
`CallerRef`, independent link layer, admitted group header and current Facts. These bindings remain
separate for support and subscriptions.

For a FLOW group `g`, the field exclusion, mark exclusion and layer apply to the whole group.
Every geometrically overlapping leaf produces the same scalar climb target for a given mark.
Therefore its complete climb set is:

```
if requestGeometryOverlaps(i, g):
    emitClimb(g.caller.method, g.caller.initial, M minus g.markExclusion)
```

An empty result emits nothing. The overlap test is existential: one admitted overlapping flag suffices.
For the required STAR/empty-E request, an admitted ancestor flag matches; a nonempty subtree at the
request path also matches. A normal ancestor must pass its own first-continuation exclusion test.
A DEMAND ancestor admits that continuation. An excluded ancestor must not hide explicit flags at or
below the query path. Walk the query spine and test the subtree without building every descendant
Pattern. Preserve cancellation checkpoints.

Do not reuse `MarkCheck.flowOverlaps` unchanged: its condition-literal tail rules differ from the
STAR request rule. Prove this dedicated predicate against scalar `leavesNear` plus `overlap`.
Position requests cannot use this existential collapse: different flags can give different position
answers or climbs.

For TAINT groups, visit each relevant path/tail once and intersect its concrete mark set with `M`.
For every selected mark, apply the existing scalar answer construction. The same mark can need both
an exact answer and a chain answer, or several static answer paths. Deduplicate only identical full
answer initials. Keep normal-exact versus demand-chain behavior and the run-1 must/static demotion.

For new links, query candidate request rows by geometry, deduplicate row handles, and read each row's
mark set once. Do not query the index separately for each concrete mark. Final overlap and exclusion
checks remain mandatory.

Combine outgoing climbs by the exact target `(MethodKey, caller InitialAp)` after checking each full
raw link. Different call sites or caller layers can project to the same request target; their marks
can share one event. Their raw links, layers and provenance remain distinct. Different target contexts
or initial paths cannot share a row.

Example: 32 requested marks and four overlapping FLOW flags for one caller can produce 128 scalar
climbs today. The new join can produce one 32-mark batch after one group-overlap test. This is a shape
example, not a measured ThingsBoard batch width.

## Accumulate changes on both sides

Admit a newly produced request into the standing relation immediately, using exact membership
deduplication. Add only its fresh mark memberships to the pending request frontier. F75 still calls
request production for every required same-base FLOW input, including excluded marks and disjoint
cleaner paths. The requesting input's exclusions cannot remove a standing demand.

Admit raw links and emit their policy initials as today. Defer only their mark-request join.
Accumulate one exact fresh delta per raw link-group handle until the next request phase. A new full
caller reference is a new group for this purpose. Keep only the current pending delta, not a list of
its historical versions. Position joins retain their existing scalar behavior.

Keep the admitted group header with that handle. An exact-only TAINT delta can have canonical empty E
even when its admitted group has nonempty E. It must not change the frontier key. Accumulate roots
under the admitted header and rebuild the appropriate delta wrapper; do not pass mismatched canonical
delta group keys to the existing exact-key merge. Keep FLOW E/mx correlation intact too.

At a flush, detach the current request and link frontiers before callbacks. For standing relations
`R` and `L`, the new pairs are:

```
Rnew = Rold union deltaR
Lnew = Lold union deltaL
new pairs = (deltaR join Lnew) union (Rold join deltaL)
```

This includes pairs whose two sides arrive in the same phase. The two arms must not both use only
old state. Existing admission ordinals can distinguish old request members from the detached new
members; a second lifetime copy of `Rold` is unnecessary. Raw link deltas retain their own headers
and full caller bindings.

Callbacks and concurrent incoming messages belong to the next frontier. A partial flush keeps its
continuation and captured horizons until completion. Newly accepted rows or groups cannot slip into
an iterator whose snapshot excludes their matching side. Every accepted new pair must be processed
in this phase or a later one.

An alternative uses dirty group handles and rescans their full current trees. It uses less frontier
payload, but repeats joins with old leaves. It is safe only with complete standing joins and output
deduplication. It must not be called an exact delta join or assumed faster. Compare its memory and
scan cost in fixtures before choosing it over one pending delta per group.

## Method drain barrier: recommended first policy

Distinguish **flush eligibility** from **method completion**:

- A request flush is eligible when ordinary fact deltas are empty and current publications are flushed.
- Work is complete only when fact deltas, publications, pending request/link joins, active join
  continuations and outgoing request buffers are all empty.

Extend the existing counted actor Work token to cover deferred request work. A request-only arrival
or a new link that needs a deferred join must enqueue that actor before its current handler ends.
An actor already queued or executing keeps its existing token; no token is needed for each mark.
`hasZeroWork` continues to describe fact work, and fact `steps` must not silently count request members.

At a method drain point:

1. Flush publications and recheck ordinary fact work. A synchronous callback can create more work.
2. Detach a finite request phase and process the group joins with a positive request-work budget.
3. Combine and send owned outgoing mark-set batches. Schedule all new answer work before completing
   the current Work token.
4. If answers or callbacks added facts, resume ordinary work. If a join continuation remains, return
   incomplete Work and requeue the same counted token. Otherwise check all buffers again.

Answer callbacks can enqueue facts while the actor is already queued. Keep the Work token live until
those facts are drained or the token is requeued. Returning complete after such a callback would
remove the only token that covers them. Preserve existing priority resampling on requeue, zero-work
handling, source overrides and ordered cleaner effects.

Use the existing positive runner quantum to bound chunks; count request work separately from fact
steps. Bound member scans as well as rows, so one very wide set does not bypass the budget. Resume
partially processed groups without repeating their geometry test. Check cancellation and the run
deadline between chunks. Release consumed frontier storage promptly.

An empty-only method barrier can delay requests behind a very large fact frontier. Include a fallback:
at the end of a selected nonzero Work quantum, process at most one bounded request chunk even if facts
remain, then requeue the counted Work token. Keep the zero-work phase transition and existing queue
priority/resampling rules. This trades some batch width for progress within a busy actor and needs
explicit ordering tests. The bound is per selected Work opportunity, not milliseconds or a guarantee
that the current event priorities promptly select every actor. A separate intake or service policy
would need its own design; do not include the whole old N3 scheduler implicitly.

## Unit-empty barrier: possible alternative

A unit contains several method actors and owns one event channel and priority queue. An empty method
worklist does not imply unit idleness. Unit idleness does not imply that other units have finished.

| Boundary | Benefit | Limitation |
| --- | --- | --- |
| Method fact/publication drain | Groups one method's producers and links; releases requests without waiting for other methods | Smaller batches across methods; a large method can delay its requests |
| Unit ordinary-work drain | Can combine targets produced by different methods | Unrelated work or incoming events can delay all requests |
| Bounded local checkpoint | Gives pending batches another chance to progress | Smaller batches; deliberately changes request versus fact service order |

If the unit variant is selected, give its pending request phase a counted continuation. Park that
continuation outside the ordinary event heap while waiting for the local barrier. Requeueing a flush
marker repeatedly would either prevent the queue from becoming empty or consume service ahead of
the fact work needed to reach the barrier.

Define unit flush eligibility over ordinary work, excluding the parked request obligation itself.
Inspect local buffered requests before blocking on `channel.receive`. Add a bounded local checkpoint
as a fallback; otherwise a busy unit can retain the whole pending request relation until timeout.
An intake bound is also required if continuous channel traffic can prevent reaching that checkpoint.
Do not wait for another unit or for global `inFlight == 0`: the request continuation itself must stay
counted, and its answers may be needed to reach quiescence.

This needs more runner and accounting changes than the method policy. Keep it a separate validation
step. It does not require the old N3 queue-layout or service-lane redesign.

## Completion and failure invariants

For the method policy, retain Q1/Q2 event reservations and extend W1–W3 to include deferred request
work. Reserve each outgoing batch before it becomes visible. A batch containing several logical
requests is one physical event. Receiver admission schedules counted Work before releasing that event.
The root reservation and per-run isolation remain unchanged.

For the unit variant, extend the quiescence model to include parked request continuations. Admission
and the final empty/release decision use the existing RunManager admission lock. A producer either
joins an active continuation or reserves a new one before publishing accepted work. It cannot insert
accepted marks between the empty test and selection of COMPLETE. No lock is held across AP operations,
actor callbacks or sends.

Rejected sends release only their unpublished reservation. A failed partial flush selects FAILED.
OOM, timeout and cancellation keep the existing first-terminal-status and join policy; do not force
remaining request batches through after termination or count them as handled. Release pending owners
after their runners join. A failed join must not permit reuse in a later run.

The scheduling change must preserve completed logical closure and support. It does not promise the
same event trace, matched-fact version history, duplicate report occurrence count or incomplete prefix.
Both source and sink NNF conditions, signed folding, all relevant marks, F75, D23 and concrete origin
dependencies retain their existing semantics. Requests still exist only in run-1, starting at limit 1.

## Validation and implementation sequence

An external scalar model passed 60 finite schedule trials and eight FLOW-group controls. It checks
diamond/cycle grouping, late arrivals, preserved answer shapes and several invalid barrier protocols.
Its admission occurs at flush time, unlike the prompt-admission protocol proposed here. It therefore
supports the set/group laws and barrier examples, not the exact frontier implementation or engine
closure. The production validation below remains required. Artifacts are under
`request-batching-scalar/{README.md,results.json,manifest.json,simulate_requests.py}` in the same fresh
batch directory as the investigation report.

1. Specify mark-set expansion and the FLOW existential-overlap equivalence. Add an independent scalar
   request-action oracle for exclusions, descendants, admitted ancestors, exact/chain/static answers
   and separate position requests. Compare full bindings, not only result counts.
2. Add batched standing-row APIs and raw link-group visitors. Check partitioned and permuted mark sets,
   duplicate subsets, new callers/layers and crossed request/link arrivals. Keep the existing scalar
   action functions as the reference.
3. Add one pending request/link frontier per actor and mark-set outgoing batches. Check reentrant
   admissions, partial continuations, snapshot ownership and no history retention. Raw link and support
   tests remain unchanged; scalar-event list tests gain an expanded logical-request comparison.
4. Integrate the method drain barrier with Work. Test idle request-only actors, last-handler arrivals,
   late links/publications, recursive calls and cross-unit cycles. Assert no early COMPLETE, no lost
   answer work, zero-priority resampling, cancellation and fresh-run isolation.
5. Prove and test complete closure for source/sink conditions and support under several legal schedules.
   Compare full scopes, layers, publications, valid witnesses and final sink classifications. Do not
   weaken the oracle to make a batched schedule pass. Evaluate a unit/checkpoint variant separately.
6. After selection and implementation, run the required core/JVM checks and the same fixed OWASP,
   Conductor, ThingsBoard and Stirling-PDF configurations through `runProjectAnalyzer`. Compare with
   both the current control and main. Keep existing strict missing findings visible. Capture profiles
   and OOM heaps as required; distinguish each capture's run from latest reached progress.

Measure logical offered/fresh/duplicate marks, new pending link deltas, group-overlap tests, marked
member scans, join phases, partial continuations, concrete initial admissions, outgoing batch widths,
duplicate targets combined, physical events and handled reservations. Record pending/in-service
member and capacity high-water counts, frontier AP ownership and request service delay. Keep diagnostics
as numeric counters and bounded histograms rather than a retained request/fact history.

Every project report must include latest reached `run-N`, direction and positive limit, last complete
engine and last accepted forward report. Compare logical work separately from physical event counts.
Accept the optimization only if it reduces measured work or net memory without losing the complete
request closure, support or required findings.

## Source anchors

Current code: `store/RequestStore.kt`, `store/AddedFacts.kt`, `ap/LeafViews.kt`, `ap/Demand.kt`,
`ap/Reference.kt`, `engine/RunMethodLocalServices.kt`, `engine/RunMethodAnalyzer.kt`, `engine/RunEvent.kt`,
`engine/UnitRunner.kt`, `engine/RunManager.kt` and `engine/InFlight.kt` under the common bidi source tree.
Contracts to amend during implementation: `spec/analyzer-core.md` §§4.6, 5.1 and 6; `spec/ap-impl.md`
for request-store/visitor representation. AP request, answer and exclusion tables remain the oracle.
The older [N3 document](bidirectional-n3-deferred-proposal.md) is historical design context, not current
measurement evidence or authority to implement its other changes.
