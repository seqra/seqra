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

## 1. Problem and intended change

ThingsBoard has 5,803,728 caller N2 groups at capture, but its terminal counters
record 155,573,488 question replay visits and 1,606,614,611 FLOW climb callbacks.
The receiver rejects 1,470,265,587 duplicate mark memberships. These counters have
different scopes; they are not counts of distinct facts or a speedup estimate.
The full run-1 profile places request replay in 44.00% of sampled Java CPU and
40.15% of sampled allocation. The actor census separately records 21,448,782 fact
processing steps at capture; it is not a unique-fact census.

Current: each question visits input groups, expands requested marks into callbacks,
builds a batch and sends it. Other visits can repeat the same destination request.
The destination rejects duplicates after that work. The emitter and publisher also
construct separate cumulative question sets.

New: keep one cumulative request relation, accumulate contributions before admission,
and join a batch of changed slots with inputs. Produce mark-set contributions for
distinct destinations. Subtract already admitted and locally pending marks before
scalar mark actions or transport. Notify callers only about newly admitted marks.

Why: this avoids repeated joins, callbacks, batches and cumulative state. It addresses
the auxiliary workload rather than assuming that each callback represents a new fact.
The design does not remove required N2 summary inputs or alter ND provenance.

## 2. Request grammar and matching

Requests exist only in run-1. There are only mark requests. A request is owned by
the full MethodKey, including entry and context, and has one of two slot forms:

```text
StaticSlot(classAccessor, fieldAccessor)
BaseSlot(nonStaticBase)

R[MethodKey][slot] = set of requested concrete marks
```

`StaticSlot(C,f)` represents the abstract initial `(S, C.f, *, {}, *)`.
`BaseSlot(b)` represents `(b, ., *, {}, *)`. Exclude zero and cleaner temporaries.
Every mark is an independent request; a set of marks is not an AND premise.

There is no separate query path, tail, E, mx, rule role, caller or call site in the
request key. The two forms provide all normal run-1 request geometry. A rule can
mention a longer path, but that original rule literal is not a new run-1 request
shape. Keep complete rule conditions, occurrence IDs and proofs in their own stores.

Request matching ignores incoming field exclusion E. The request carries no mark
exclusion. If an incoming FLOW excludes T, T is absent for that match and cannot
produce an answer or climb through that supplier. Other requested marks remain
eligible. Do not copy the supplier's mx into request identity.

| Request slot and supplier | Request result |
| --- | --- |
| Non-static slot b; nonempty supplier on b | Inspect that supplier's concrete marks, or route eligible FLOW marks |
| Static slot C.f; matching field leaf | Inspect that leaf, or route eligible FLOW marks |
| Static slot C.f; broad static root/class supplier | Use the broad supplier when it covers the slot; exact ancestor leaves do not cover descendants |
| Other base, class or field | No result |
| FLOW excluding requested mark T | No result for T |

Implement this as a small slot operation over valid run-1 trees. Do not create a
generic full-pattern cursor per slot/input pair. Tree breadth can still be large;
the depth limit does not make visiting a whole tree constant time.

Concrete answers keep their required distinction:

| Concrete supplier match | Emitted initial |
| --- | --- |
| Non-static exact leaf at the base root | Base root, concrete mark, EXACT tail |
| Other matching non-static leaf | Base root, concrete mark, STAR tail |
| Static exact leaf at C.f | C.f, concrete mark, EXACT tail |
| Static ANY leaf at C.f | C.f, concrete mark, ANY tail |
| Static broad leaf strictly above C.f | Requested C.f, concrete mark, STAR tail |

All emitted E is empty. No emitted initial has ANY_TAINT in run-1; if a lower-level
adapter exposes must-taint, normalize it to ANY. No FLOW is itself a concrete answer.
Define these cases in a pure projector before connecting the protocol.

The required `(S, ., *, ...)` summary seed remains special. It is not a third
normal request slot or `BaseSlot(STATIC)`. It skips rule evaluation and emits no
requests. Section 8 defines how static metadata supplies the required concretizations.

## 3. One cumulative owner

Current: `Run1Abstraction.questions` and `SummaryStorage.questions` each retain a
cumulative relation. The emitter also keeps checked concrete answers for future
requests.

New: `SummaryStorage` is the only cumulative owner of R. Its key is the complete
method key. The abstraction borrows its current immutable view for one input visit;
it does not keep a second root or rebuild cumulative sets locally. Ordinary nested
maps may group slots by base/class, with each request cell stored once. No separate
request positional index or global interner is needed.

Admission is one atomic operation per submitted target slot:

```text
merge(slot, offered):
    fresh = offered - R[slot]
    if fresh is empty: return no change
    R[slot] = R[slot] union fresh
    return fresh
```

Publish fresh deltas after releasing the storage lock. Install before notify.
Reserve all resulting delivery work before the submitting handler can finish.
Failure to secure required work while the run remains active is a failure, not
permission to forget an admitted request.

Temporary outboxes and delivery batches contain pending deltas. They are not second
cumulative request stores. Readers may borrow immutable values for a finite visit;
subscribers retain neither full old request views nor per-request seen-mark sets.

Keep R active after an answer. A later input or another caller can need another
concrete initial. Release the run's request state only after its work has joined.

Why: one owner removes duplicate cumulative-set construction and makes the same
admitted membership available for early filtering at every request producer.

## 4. Accumulate before submission

Each producer unit owns a temporary outbox:

```text
outbox[target MethodKey][original target slot] -> merged new marks
```

Merge contributions from different methods, call sites, source/sink conditions and
replay visits. Preserve those origins in ordinary fact/summary stores where needed;
they do not distinguish a request. Use the original caller initial to choose the
destination slot. For `C.g -> C.f`, a need discovered on the resulting f fact still
routes to the original g slot.

For each eligible FLOW contribution:

```text
useful = offeredMarks
         - incomingFLOW.mx
         - targetSummaryStorage.R[originalSlot]
         - unitOutbox[target][originalSlot]

unitOutbox[target][originalSlot] union= useful
```

Use set operations. Do not first call `onClimb(T)` for every mark and only then
discover that the target already requested it. A stale target view can cause extra
work; it must never omit an actually unadmitted mark. Suppression against the local
outbox is valid only while counted work owns its eventual commit.

Coalesce multiple FLOW groups with the same offered set and destination before
mark iteration when possible. Their eligible result is the union of allowed marks:

```text
union_j (offered - mx_j) = offered - intersection_j(mx_j)
```

Using the union of excluded marks would lose valid routes. This grouping is temporary
for one service frontier; it is not a persistent dependency graph or seen-pair table.
A sorted-set subtraction can still scan marks. Measure scans separately from avoided
callbacks; replacing a callback with a loop alone is not the intended improvement.

At submission, merge each accumulated slot once into the authoritative storage.
Concurrent units can submit overlapping proposals; atomic admission produces fresh
memberships once. An empty fresh result creates no notification.

Why: duplicate work is rejected before per-mark builders, events and receiver handling.
Cross-visit union reduces submissions even while target requests await admission.

## 5. Barrier and scheduling policy

| Barrier | Benefit | Problem |
| --- | --- | --- |
| Method fact queue empty | Simple and close to current summary batching | Other methods can submit the same targets separately; a busy method can delay requests |
| Unit ordinary work empty | Combines work from multiple methods | Continuous ordinary work can prevent the barrier |
| Global idle | Broad batching | Requests can be the work needed to create more facts; idle detection and request progress become dependent |
| Unit drain or finite unit slice | Broad batching with a progress bound | Needs explicit counted request work and a service budget |

Select the last policy. Service requests when the unit's ordinary ready work drains,
or after a positive bounded number of ordinary events/work steps while it remains
busy. Reuse the existing runner quantum as the initial policy setting. Count control
events too; a continuous event stream must not postpone service forever. No wall-clock
timer, global barrier or new whole-program phase is required.

One unit request-work reservation covers its pending outbox, inbound request deltas,
dirty FLOW inputs and resumable request visits. Give service a finite positive budget
for keys, candidate groups and mark work. Charge union, difference, sorting, snapshot
and admission preparation by the members inspected, not as one cheap key operation.
If work remains, retain the reservation and continue at another service opportunity.
Ordinary fact work must also get service. A single atomic immutable-set admission
can exceed the remaining service budget: finish that operation with cancellation
checks, charge its full work, and yield before the next operation. This policy does
not promise a hard wall-clock latency bound for one large set operation. Resumable
merge preparation is separate work if measurements show that this exception is costly.

At a service point:

1. Detach a finite frontier of inbound deltas and dirty FLOW inputs.
2. Join that frontier and merge resulting concrete answers and request contributions.
3. Submit accumulated target slots to their SummaryStorage objects.
4. Secure notifications and answer deliveries, then test for remaining work.

Callbacks cannot recursively mutate a visited N2 directory. All answers and replay
effects, including same-unit effects, are queued or deferred until the visit ends.
Work arriving during service belongs to the next frontier.

For bounded resumable scans, use a concrete horizon. N2 handles are already stable
append-only ordinals. Capture the current end handle H, visit handles below H once,
and re-read a handle's current value when visiting it. New handles and growth after
a visit enter the fresh-input frontier. For a resumable full-unit rescan, registrations
also need stable append ordinals; keep one directory reference per registration in
an append-only lane, capture its end, and use those existing directories as the work
source. This is progress metadata, not another request or supplier relation.

A cursor may retain one active immutable input value and a finite offered-mark view
while processing it in blocks. It does not retain old values for every handle or a
previous cumulative request root for every subscriber. New offers always mark the
next frontier; completing an older scan cannot clear them. Positional index traversal
may be used only with an equally explicit stable horizon. Do not resume an ordinary
mutable map iterator across handler interleavings.

The service latch covers queued and executing work. An offer before the final empty
check belongs to that reservation; an offer after the latch is cleared reserves a
new wake before exposing pending work. All reservations pass through the run's
admission/completion protocol. Waiting for a barrier must never make work invisible
to `InFlight`. Preserve zero-fact scheduling priority; request service does not run
new nonzero fact processing inline.

Why: batching reduces repeated submissions while the finite-slice backstop prevents
starvation and false completion. A low event priority alone is insufficient.

## 6. Join changed slots and inputs in batches

Current: a new question visits N2 inputs separately, and each accepted input update
loops over all published questions, including unrelated bases.

New: collect changed slots for one callee into one batch. Select each relevant N2
handle once for the batch, using the existing input directory. For a FLOW input,
union offered marks from matching slots, then accumulate useful marks for its
original caller slot. Do not create one replay context per question or expand a
slot/input/mark product before destination filtering.

| Trigger | Required arm |
| --- | --- |
| Newly admitted request marks | Fresh slot/mark batch against current N2 inputs, both TAINT and FLOW |
| New FLOW input or FLOW growth | Coalesced fresh geometry against current R, routed to original caller slots |
| New TAINT input | The method abstraction checks current R when handling LinkIn |

Preserve the original admitted FLOW header when accumulating fresh geometry. Union
new positions for the same current handle until its barrier service. Never rekey a
group from a normalized delta. If the fresh-geometry implementation is not proved,
a dirty-handle current-value visit is a safe fallback with more repeated work; mark
it explicitly in measurements. It is not equivalent in cost to processing a delta.

Keep one dirty entry per handle while pending, and no historical input snapshots.
The normal N2 relation and its full caller/call-site/layer/ND identity remain intact.
Only the request operation projects its result onto a minimal target slot.

Fresh request notifications go through a thread-safe per-unit mailbox, merged by
callee and slot. Queue at most one request wake per unit. The publisher never reads
or modifies another unit's N2 or actor maps. No cumulative request copy is kept by a
subscriber. Overlap between the two arrival arms is allowed; admission and initial
deduplication remove repeated results.

Why: one visit can serve multiple changed slots, many arrivals can become one input
frontier, and unrelated bases are rejected before allocating replay state.

## 7. Remove deferred concrete-answer storage

Current: the emitter scans concrete inputs for possible future requests and keeps
their checked answer shapes and marks. Caller N2 also retains the original inputs.

New: retain no deferred-answer memo in the callee. New concrete inputs read R; new
requests replay old inputs through N2. Remove the N2 fresh-input TAINT question arm
to avoid repeating the emitter's job. Keep ordinary summary replay unchanged.

Enforce one production `subscribeAndLink` operation:

```text
register the caller subscriber
insert/update the exact N2 input
deliver LinkIn to the callee
```

Let A be N2 admission, E the emitter's R read, Q request commit, and V its N2 replay.
Require `A < E` and `Q < V`. If Q precedes E, the emitter sees the request. Otherwise
N2 already contains the input when the late-request arm runs. Both arms cannot miss:
that would require the impossible cycle `A < E < Q < V < A`.

Remote LinkIn delay can produce two answers; `InitialFactStore` deduplicates them.
Every LinkIn and answer remains counted. Direct/manual incoming TAINT that bypasses
N2 invalidates this proof and must be routed through the paired operation. Internal
zero/source-generated facts are ordinary method transfers, not incoming suppliers.

The abstraction still emits abstract initials unconditionally, including the special
static summary seed. Requested concrete initials remain additive.

Why: future-answer availability already exists in N2. A second memo and speculative
concrete-mark scans are unnecessary under the explicit ordering law.

## 8. Static metadata and the special summary seed

Install precomputed static mark needs in the same R as dynamic requests. Activate
them lazily for active MethodKeys and relevant normal slots; retain shared immutable
metadata instead of eagerly expanding every method's transitive static footprint.
Project needs onto valid `StaticSlot(C,f)` keys and union marks across roles. Do not
retain the original deep rule pattern or manufacture a deeper run-1 initial. This
is request prefetching; source/sink conditions still evaluate normally on ordinary
facts. A metadata declaration is not an admitted request: only membership in R can
suppress another producer. Remove the per-mark profile-coverage callback from FLOW
propagation; metadata supplies R rather than acting as a second coverage relation.

Changed-static transfers still need dynamic requests. If code reads C.g, writes C.f
and checks f for T, the abstract analysis started at g must request T for g. A
precomputed f interest is not proof that the g input has T.

The static-root policy is explicit: skip rule evaluation for the special abstract
`(S, ., *, ..., *)` component, and emit no requests from that component. Retain its
ordinary code transfer and summary work. Apply the guard at the common rule-processing
boundary, including callbacks that could otherwise raise a mark request; checking
only sink predicates is insufficient. A packed tree can contain both a root flag
and field branches: suppressing the whole tree would incorrectly suppress those
ordinary branches. Do not confuse the root seed with a class-only fact, an ordinary
field slot or a concrete mark answer. Test the resulting static write and cleaner
summaries, not only absence of request callbacks.

Static metadata must enforce the normal field analyses for which root rule evaluation
was formerly used. Field-specific needs target their field slot. Root/class-wide
metadata must resolve onto relevant normal field slots, including actual incoming
fields and later-arriving fields; an unexpanded wide metadata row is not a request.
Do not introduce a root request key or broadcast marks to every field in the project.

Coverage also constrains summary application. Let Covered(M,C.f) mean that the
prescan code/rule footprint requires a normal abstraction for C.f. A covered input
component must use normal field summaries, not an unguarded special-root summary.
The root serves the remaining uncovered input components. Coverage is known before
root summaries can apply; waiting for a later request admission is too late to undo
an incorrect propagation. If a field analysis is still pending, do not temporarily
fall back to the root for that covered component. Normal summary delivery will resume
it when the required result exists. Coverage is structural metadata, not a second
mark-request table, and must conservatively include code and rule effects.

Prove this composition before changing the root guard. Both controls are required:
a method cleaning C.f/T must remove incoming C.f/T, and must preserve incoming C.g/T
when g differs from f. Starting an additional concrete f analysis alone does not
prevent an unmasked root identity summary from reintroducing f/T. Conversely, the
old root cleaner's whole-tree mx must not erase an unaffected g/T that has no normal
field analysis. The covered/uncovered partition must resolve both cases.

Broad suppliers, conservative root/class-wide effects and future inputs need the
same partition argument. Specify its AP operations and unknown-effect behavior in
the static adapter/summary composition contract, retain ordinary summary E rules,
and test it independently. The request match itself still ignores E. This is a
first implementation proof/control gate; the proposal does not claim it already
exists in the measured engine.

The implementation must close four concrete parts of that gate:

- Partition by covered positions, not identical annotated leaves. The current
  `without` operation is not this partition. Adding f to E at the static root does
  not express exclusion of the full C.f prefix, and DEMAND ANY cannot carry E.
- Keep structural coverage stable before replay. Lazy slot activation can add work,
  but cannot change an already root-served field into a covered field after outputs
  have propagated. Conservative wide coverage can be known symbolically in advance.
- Handle root-origin N2 FLOW links. A normal callee field question can still match
  a caller link whose original premise is S-root. The local rule guard alone does
  not prevent that route. Prove that normal field/base-origin analyses replace it,
  retaining the question until their inputs arrive; or specify a proven static
  identity projection. Never use the transformed output field as a guessed origin.
- Apply the same ownership split to persisted native and opposite-direction record
  reads in later runs. Do not persist an unrestricted root identity and later replay
  it without coverage. Retain only required immutable structural coverage beyond
  run-1, or lower the restricted relation into an existing valid representation.
  R itself still belongs only to run-1. Measure any retained coverage metadata.

This user-selected policy resolves the root's role. The gate is the coverage and
summary-composition proof, not a choice to restore a root request variant. Keep
ordinary rule/cleaner effects on normal facts unchanged.

## 9. Pending memory must be bounded

One cumulative owner does not make every transient buffer small. Specify positive
limits for pending mark cells, target keys and candidate handles. Use measured byte
and cardinality counters, not only an event count.

For a local outbox at capacity, force an early service point or pause the resumable
producer until its detached proposals commit. Never discard an unadmitted mark.
This is an earlier barrier, not a second persistent store. A producer must be able
to stop within its finite mark/group budget rather than build an unbounded snapshot.
If an ordinary rule callback cannot suspend, an admission-only emergency flush may
commit a bounded outbox before accepting more contributions. It queues notifications
but does not service incoming work, run fact processing or reenter an N2 visit.

For an inbound notification mailbox at capacity, replace retained deltas for an
affected callee with a `rescan current R` marker. Current R already owns every admitted
mark. On service, replay that callee's current request view over its current N2, in
bounded chunks. Concurrent offers enter the next mailbox frontier. A detached rescan
must not clear those newer offers. A generation can avoid unchanged repeat work,
but a version number alone cannot reconstruct missing mark deltas.

Per-callee rescan markers alone do not bound the number of pending keys. If that
key limit is reached, collapse them to one unit-wide `RESCAN_ALL_REGISTERED` marker
and discard their transient delta representations. Traverse the existing registration
lane and N2 using the captured horizons above, replaying current R. A new offer
during this scan marks the next frontier, even when its callee was already visited.
Dirty FLOW overflow uses the same bounded unit-wide fallback; selecting both kinds
of inputs is safe when a full request rescan subsumes a FLOW-only rescan.

The fallback can repeat work, but cannot lose a request. Count every fallback and
its cost. Retain no old cumulative request roots as subscriber history and no
input-by-request seen-pair matrix. Bound temporary answer batches too; flush them
as counted deliveries before a visit continues.

## 10. Expected impact and validation

| Priority | Current versus new | Expected effect and limit |
| --- | --- | --- |
| 1: repeated CPU/transport | Per-mark climbs before destination dedup -> set contributions minus admitted/pending target marks | Avoid much repeated enumeration and transport; the exact eligible share is unmeasured |
| 2: join frequency | Per-question replay -> changed-slot and input frontiers at bounded unit barriers | Fewer visits and repeated geometry; batching latency and rescan cost must be measured |
| 3: persistent request memory | Emitter plus publisher cumulative sets -> one R | Remove duplicate cumulative construction; role/shape merge rate needs a census |
| 4: answer work/state | Speculative concrete scans and pending memo -> new-input/current-R plus late-request/N2 | Remove the memo and redundant TAINT question replay under the ordering law |
| 5: generic machinery | Full pattern/role cursors -> two-form slot operations | Fewer keys, objects and traversals; do not confuse a new loop with avoided work |

ThingsBoard's old local request union is 575,705,904 bytes, shared request union
510,728,744 bytes, pending-answer union 98,508,808 bytes and immutable request arrays
910,444,952 shallow bytes. These selections overlap; they cannot be summed as savings.
Compare whole heap and combined request owners, including outboxes and old readers.
The redesign still retains logically needed method/slot/mark memberships and N2.

Required independent controls:

- Both input/request orders, and every register/commit/read/notify interleaving.
- Same-unit and remote input delivery, late subscribers and cumulative input growth.
- Multiple changed slots matching one input; multiple calls reaching one original slot.
- Diamonds, recursion and request-only progress, including quantum 1.
- Static C.g -> C.f, different classes/fields, broad suppliers and the root-seed gate.
- Exact and ANY concrete answers, both layers, ignored E and absent/excluded marks.
- An already-active target, an uncommitted local proposal and a stale borrowed view.
- Independent requested marks and unchanged joint-premise/proof handling.
- Busy units, bounded fairness, overflow/rescan, offers during wake completion.
- Cancellation/rejected work after admission, joined release and a fresh next run.
- A negative control that rejects incoming TAINT which bypasses N2 registration.

Build a scalar oracle for this newly specified request policy. Do not treat today's
full-query/E behavior as its semantic authority, or claim exact old-engine closure
equivalence after deliberate policy changes. R membership never establishes a
source/sink predicate or candidate confirmation.

Measure admitted memberships, admission calls, pending peak cells, scalar mark scans,
pre-enumeration skips, slot/input visits, coalesced targets, submissions, physical
wakes, fallback rescans, answer deliveries and new initial admissions. Classify cold
setup separately. Report engine/barrier times and reached/completed/accepted runs.
Compare strict findings; an earlier OOM or smaller incomplete report is not a gain.
The current evidence does not establish that this design will reach 60 seconds.

## 11. Implementation sequence

1. Prove static metadata coverage for the root rule-evaluation guard. Specify the
   two-form projector and scalar oracle, including the new E-independent policy
   and absent-mark rule. Keep the root summary seed and its normal code processing.
2. Add the sole R admission/read interface in SummaryStorage. Install static and
   dynamic needs through it; remove local cumulative question state and query roles.
3. Enforce paired N2 admission and LinkIn. Connect new TAINT inputs to current R and
   late requests to N2. Remove the callee pending-answer memo and redundant fresh-input
   TAINT question replay after the arrival-order controls pass.
4. Add merged unit outboxes, fresh-delta mailboxes and one counted request-work latch.
   Implement unit-drain/finite-slice service, cancellation, bounds and overflow paths.
5. Replace scalar FLOW climbs with batch slot/input joins, destination filtering and
   set contributions. Keep normal N2 summary/ND matching unchanged.
6. Remove obsolete request events/cursors and counters after their replacements have
   independent tests. Do not hide a second cumulative relation in compatibility code.
7. Run focused protocol/grammar/ordering controls, then the fixed OWASP, Conductor,
   ThingsBoard and PDF configurations with a fresh main comparison. Start by checking
   ThingsBoard run-1 work reduction; include all reached iterations in the report.

IR storage, cache/reference managers, WeakIdentityCache, normal bound-rule retirement,
fact representation and a broad scheduler rewrite remain outside this request design.
