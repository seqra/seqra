# N3: deferred request transport and scheduling proposal

Status: deferred; excluded from the next optimization iteration. Date: 13 October 2026.
The next iteration contains N1 and N2 only. This document preserves the N3 design for possible later use.
It is not a prerequisite for the [active implementation proposal](bidirectional-n1-n3-implementation-proposal.md).

## Decision and reconsideration

The measured queue is large, but its size does not establish that N3 will materially improve a project run.
Mailbox grouping removes event wrappers while retaining distinct pending requests. Duplicate frequency and
the contribution of service delay remain unmeasured. The user has deferred this change.

After implementing and retesting N1/N2, reconsider N3 only if fresh heaps/profiles show the same substantial
pending-request or excess-capacity pattern. Record its owner size, logical pending count and capture run.
If the pattern persists, measure pending duplicate frequency and class service delay to assess the candidate.
Do not add mailbox, wake-accounting, scheduler or queue-layout changes to the N1/N2 iteration.

## Current versus proposed behavior

| Current | Possible future change | Expected benefit and limit |
| --- | --- | --- |
| Each remote send creates a request event; duplicates reach receiver-side deduplication. The runner drains incoming events before polling, and ordinary events precede nonzero Work. | Group pending/in-service requests per full destination, use one counted wake, claim FIFO batches, coalesce exact duplicates and use finite intake/service phases. Then compact remaining queue lanes. | Fewer event objects and duplicate handlers; finite Work service during request floods. Unique pending state remains, processing order changes, and a material project benefit is not established. |

For example, 100 distinct pending requests for one destination would use one live wake, but still need
100 stored keys. This is a shape example, not a measured saving.

## Latest evidence

The current runtime is `38a400c0d`; the scan manifest revision is `bd6810823`. Commit `23f25e8a8` changes
documentation only. The [rollback report](../reports/bidirectional-core-rollback-20261013.md) gives the
reviewed evidence and the fixed project settings.

| Project | Latest reached iteration | Last complete engine | Last accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-3, forward, limit 3, COMPLETE | run-3 | run-3 |
| Conductor | run-2, backward, limit 2, OOM | run-1 | run-1 |
| ThingsBoard | run-1, forward, limit 1, OOM | None | None |
| Stirling-PDF | run-4, backward, limit 4, TIMEOUT | run-3 | run-3 |

The fresh Conductor heap captures **run-2, backward, limit 2** before cancellation. The fresh ThingsBoard
heap captures **run-1, forward, limit 1** before cancellation. PDF's older checkpoint heaps capture run-3;
they do not measure its latest run-4. Run-1 starts at limit 1. There is no limit-0 case.

Fresh ThingsBoard run-1 has 15,764,679 queued entries, 51,339,702 backing cells and a
1,214,843,344-byte backing-root retained union. Fresh Conductor run-2 has only four queued events.
Retained unions overlap. The old bounded duplicate sample does not establish a global duplicate rate.

Keep the AP/NNF/cleaner and IR scope in the active proposal. Its owner-local membership rules can inform
a future mailbox layout, but the active implementation must not build shared mailbox infrastructure now.

## Preserved future design

### Mailbox key and ownership

Replace queued remote `RequestIn` payloads by a run-owned mailbox for each full destination `MethodKey`.
The exact pending key is `(run owner, destination MethodKey, full origin InitialAp identity, RequestKind)`.
The kind includes its mark ID or full normalized position path. The run and destination can be implicit in
the mailbox owner; their identity must still be part of the key's meaning.

Use the N1 grouping rules for pending payloads, with a separate ownership boundary. Producers can add under
a mailbox lock. The receiving runner claims a finite batch and calls the existing actor `addRequest` outside
that lock. Local `raise` remains synchronous even if the same remote key is pending. Same-unit remote Climb
uses the mailbox too; it must not become a re-entrant actor callback.

Keep keys while pending **and while in service**. Suppress exact repeats in either state. Retire the claimed
batch's in-service keys at its finish transition, after their callbacks complete. A later repeat can be
admitted again and the actor's N1 store
deduplicates it. There is no second lifetime seen set and no producer read of N1. This limits deduplication
to overlapping transport; do not claim it suppresses every repeated climb.

Claim keys in FIFO order of first pending admission across the whole mailbox, with primitive ordinal/order
storage. Coalesced duplicates keep their original position. A repeat admitted after completion goes to
the tail. Do not restart each claim at the smallest row/mark ID: repeated low-ID admissions could starve
an older key. Release consumed order pages and include their spare capacity in the measurements.

### One reservation for an active mailbox

Use a wake event with one physical reservation for an active mailbox. Its reservation stays live while the
token is in the channel, local queue, handler or continuation. New buffered keys use that live reservation;
they do not call the current one-event `accept` path. Preserve existing one-event accounting for other events
and the root phantom reservation.

Add a narrow request-admission operation to `RunManager`, under its existing admission lock. Internally
distinguish `NEW_WAKE`, `BUFFERED`, `DUPLICATE` and `REJECTED`. The first three mean logical acceptance;
only `NEW_WAKE` increments in-flight. Existing Boolean send callers must receive true for a coalesced
request, rather than interpret zero new reservations as rejection.

Resolve the destination unit before taking admission, then recheck admitting, deadline, terminal status
and runner closure under admission for every result, including `BUFFERED` and `DUPLICATE`. Do not return
success for a closed runner merely because its old mailbox still has a wake. Use a distinct `RequestWake`
event; it is not actor Work and must not use the queued-actor identity set.

| State/operation | Transition | Count effect |
| --- | --- | --- |
| Idle + valid request | Insert key; reserve; publish wake; become Active | +1 before visibility |
| Active + new key | Add to pending relation | None |
| Active + pending/in-service key | Accept duplicate without another insertion | None |
| Claim batch | Move selected pending keys to in-service atomically | None |
| Finish batch, more keys remain | Retire completed claims; enqueue same wake with fresh queue sequence | None |
| Finish batch, no pending/claimed keys | Clear wake; become Idle; release reservation | -1 exactly once |
| Invalid route, terminal run or rejected first publication | Reject and roll back unpublished insertion/wake | Undo initial reservation if made |
| Cancellation/failure | Select terminal status; close; join; discard remaining state | Do not pretend unhandled keys completed |

The lock order is **admission, then mailbox**. Finish takes this order, retires completed claims and chooses
More versus Empty atomically. On Empty it clears the wake and releases its count while admission remains
held. A racing producer either joins the still-active mailbox or creates a new counted wake. It cannot
insert accepted work between the empty check and selection of COMPLETE.

Claiming may take only the mailbox lock. Move the complete key to in-service before clearing pending
membership; otherwise a duplicate can slip into the gap. Detach owned batch data rather than handing a
live mark bitmap to the consumer. Producer inserts during processing must survive the next claim.
No mailbox lock is held during actor mutation, joins, sends, AP operations or callbacks.

Child sends and actor Work reservations happen before the mailbox releases its last count. A partial wake
is requeued with the same count and fresh immutable ordering metadata. If first publication fails, admission
prevents other producers from joining an unpublished wake; roll back that new mailbox state and reservation.
If handling or continuation insertion throws, fail the run. Do not drop a batch and select COMPLETE.

Serialize the runner's closed transition with admission, including a direct `UnitRunner.close` call.
Normal `stopAndJoin` first disables admission, as today. Close rejects new admission before cancellation.
Discard remaining pending/claimed state and tear down the mailbox owner only after the receiving runner
is joined, with the existing failed-join policy. During the run, reclaim consumed pages and empty headers
under normal mailbox ownership as defined below. Document the changed interpretation of
`inFlight.handled`: it counts completed physical reservations, not logical requests. Add separate logical
request counters for comparisons with the baseline's event counts.

### Scheduler: finite intake and work service

Current [UnitRunner](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/UnitRunner.kt)
drains its channel without a bound before polling. Its strict rank order can delay nonzero Work behind
ordinary events. The source proves these mechanisms; existing heaps do not establish their contribution
to the observed backlog.

Use the existing positive runner quantum `q` (default 1,000) for bounded intake and request batches.
No new project configuration or field limit is needed. A proposed service cycle is:

1. Receive when no local event is available, then admit at most `q` channel events into local lanes,
   including that initial receive in the intake count.
2. Serve one quantum from the captured zero-Work lane if it is nonempty.
3. Serve at most `q` ordinary handlers, with an additional shared budget of `q` claimed requests. A wake
   counts as one ordinary handler and consumes the remaining request budget. At zero request budget,
   leave the next wake queued and end the ordinary phase. If a wake has more keys, requeue it and end
   this phase. Do not make a zero-sized claim.
4. Serve one quantum from the captured nonzero-Work lane if it is nonempty. Skip empty lanes and yield
   between cycles.

Preserve one queued Work token per exact actor, its existing work quantum, and priority keys captured once
at enqueue/requeue. Within each Work lane use captured steps then sequence; ordinary events use FIFO.
Requeue samples the actor again. A queued nonzero token that receives zero work keeps its captured lane
until requeue. A zero token can requeue into the nonzero lane and run there later in the same cycle.
Class service is bounded in cycles; individual actor service also depends on steps ordering and finite work.
A partial mailbox wake goes to the ordinary lane's tail with a fresh
sequence. Each claimed batch follows the mailbox's FIFO admission order.

This keeps a zero-Work service phase and gives nonzero Work a finite class service interval. It intentionally
changes strict global rank ordering, including absolute zero-Work precedence across phases. Amend the
scheduler specification and its ordering tests only when implementing this design. Do not claim identical
traces, time-bounded prefixes or incomplete reports.
A finite cycle is a bound in handlers/quanta, not milliseconds: an AP handler can be expensive, and fairness
also depends on the existing finite-handler and coroutine execution assumptions.

Do not use a blocking bounded channel, request cap or overflow drop. Cyclic sends and self-sends must remain
nonblocking. Mailbox grouping bounds physical request wake tokens by active destinations; it does not bound
unique pending keys. Measure both quantities. Under a finite valid workload, service every accepted unique
key and reach the same closure; a resource failure remains an incomplete run.

### Representation after transport reduction

Use a chunked primitive membership store for mailboxes so a large pending relation does not require Pair,
RequestIn and priority-entry objects per member. Track allocated capacity and return empty pages promptly.
Use bounded reusable batch buffers; do not retain a second full copy of pending state. In-service data is
bounded by the claim quantum per handler.

Remove or recycle empty row/header entries when both their pending and claimed memberships are empty.
Do not recycle a handle still referenced by a claimed batch. Otherwise the directory becomes another
lifetime history. An idle mailbox shell can be retained until run release, but measure and disclose its
cost per ever-activated destination separately from the active wake count.

Use an ordinary FIFO lane and primitive Work priority storage only after the mailbox/scheduler contracts
pass. The Work comparator has captured primitive steps/sequence and actor references. It must not read
mutable actor state during comparison. Shrink/release queue capacity under explicit ownership; clearing
references alone does not prove that oversized backing arrays are released.

### Protocol and acceptance

Refine Q1/Q2: each nonempty/claimed mailbox has exactly one live wake reservation, counted before token
visibility and released after all represented requests and child sends. Q3/Q4 and first-terminal-status
selection remain. Refine reliable-event assumptions to separate exactly-once wake delivery from logical
request coalescing. Prove a simulation from accepted logical requests to the actor's standing relation;
duplicate handling has no additional closure effect because actor insertion is exact-idempotent.

Use `InFlightTest`, `EventQueueTest` and engine lifecycle/protocol fixtures. Add deterministic race controls
for reserve-before-publish, duplicate while pending, duplicate while claimed, concurrent insertion at final
empty check, first publish rejection, handler failure, partial continuation, deadline, cancellation and join.
Test self-sends, cross-unit cycles, same-unit remote Climb and synchronous local raise. Verify no early
COMPLETE, no negative/double count and no accepted unique request loss. Include allocation failures in
membership/directory growth, batch creation and continuation insertion: every Throwable selects FAILED;
roll back an unpublished reservation exactly once when needed.

Test sustained intake and ordinary-event floods with both captured Work classes, including lane changes
on requeue and zero work added to an already queued nonzero actor. Test continual readmission of low-ID
keys beside an older pending key. Assert bounded class service in
cycles and eventual actor progress. Compare full closure under several schedules and both unit layouts.
Do not weaken the semantic oracle to accommodate the new ordering.

## Bounds and measurements for a future selection

Let `U` count unique pending plus in-service keys, `D` active mailboxes, `M` ever-activated destination
shells and `W` queued actor Work tokens. The proposed state has `O(D)` wake tokens, compressed `O(U)`
membership, `O(M)` idle shells and `O(W)` Work. Delivery/link events are not coalesced here.
Include path/header ownership, order storage, spare capacity and lock contention in measurements.

If N3 is selected later, measure offered requests; new/buffered/duplicate/rejected admissions; logical keys
served; pending/claimed high-water counts; active wakes and idle shells; capacities; per-class service delay;
and intake size. Physical handled counts change under batching and cannot be compared directly with the
old event count. Keep bounded aggregate diagnostics rather than a retained key/event history.

Potential source changes are `engine/RunEvent.kt`, an internal `engine/RequestMailbox.kt`,
`engine/RunManager.kt`, `engine/RunnerPort.kt`, `engine/UnitRunner.kt` and `engine/EventQueue.kt`.
A future implementation requires separate admission/accounting, lifecycle, scheduling and closure gates.
These files and the Q1/Q2/scheduler amendments are outside the next N1/N2 iteration.

Every future report must include latest reached `run-N`, direction and positive limit, last complete engine
and last accepted forward report. State heap/profile capture iterations separately. Run-1 uses limit 1;
limit 0 is impossible. Any future implementation must use the same fixed project settings and output
comparison rules as the active proposal.
