# N1–N3: stored relations, retained history and pending work

Status: analysis for detailed design, not an implementation result. Date: 13 October 2026.
Scope: N1, N2 and N3 from the [memory proposal](bidirectional-core-optimization-20261013.md).
Use the [detailed implementation proposal](bidirectional-n1-n3-implementation-proposal.md) for the changes.
IR objects, storage and providers remain outside the scope. No analyzer code changes in this analysis.

We need both algorithm changes and smaller storage. Start by removing redundant history and event objects,
and by making joins more selective. Then compact the state that the specified analysis still needs.
Packing the current collections alone keeps much of their cardinality and repeated work.

The phrase "huge unprocessed collections" describes N3. It does not describe all three owners.
N1 is processed request history that remains active. N2 includes processed delta history and cumulative state.
Deleting processed items can lose a later answer or publication. A collection is redundant only if another
representation still supplies every future effect.

## Latest measurements

Runtime commit: `38a400c0d`. Scan manifest revision: `bd6810823`. The subsequent `23f25e8a8` commit changes
documentation only. Source inspection for this analysis uses that unchanged runtime.
The [rollback report](../reports/bidirectional-core-rollback-20261013.md) records the native process settings,
query availability, raw snapshots and profile limits.

| Project | Latest reached | Last complete engine | Last accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-3, forward, limit 3, COMPLETE | run-3 | run-3 |
| Conductor | run-2, backward, limit 2, OOM | run-1 | run-1 |
| ThingsBoard | run-1, forward, limit 1, OOM | None | None |
| Stirling-PDF | run-4, backward, limit 4, TIMEOUT | run-3 | run-3 |

Conductor's new heap captures run-2 before cancellation. ThingsBoard's captures run-1 before cancellation.
Main has no bidi iteration. Run-1 starts at limit 1; limit 0 is impossible. No new workload or heap capture
is performed for this source analysis.

| Owner | What is stored | Evidence | Implication |
| --- | --- | --- | --- |
| N1, ThingsBoard run-1 | Seen requests plus a positional index | 24,981,269 distinct pairs; 3,095,473,728 retained-union bytes | Receiver deduplication already removed equal requests from this store. These are not 25 million pending messages. |
| N2, Conductor run-2 | Accepted delta parts plus cumulative exact groups and two indexes | 2,165,026 part-list entries; 679,558 exact groups; active-subscription union 1,538,287,888 bytes | Growth history is retained after replay. Investigate whether cumulative state can serve later delivery. |
| N3, ThingsBoard run-1 | Pending events and priority-queue capacity | 15,764,679 queued events; 51,339,702 backing cells; backing-root union 1,214,843,344 bytes | Producer-side coalescing and scheduling can reduce transport overhead. Unique pending obligations may still be large. |
| Conductor queues, run-2 | Pending transport | Four queued events; five pending reservations | Conductor's large subscription state persists even with almost no queue backlog. |

Retained unions overlap. Do not add them or treat them as recoverable savings. Different progress between
captures prevents an isolated savings claim. ThingsBoard has handled 197,740,423 events at capture;
Conductor has handled 2,723,788.

## N1: a standing relation, not a completed-work leak

[RequestStore](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/RequestStore.kt)
stores one `(InitialAp, RequestKind)` pair in both `seen` and the path index. The two structures share the
same Pair object. The repeated cost is hash/index entries and arrays. Pair bodies use 599,550,456 bytes;
linked-map entries use 999,250,760; hash-map nodes use 800,078,304; hash-map tables use 392,402,320.

[RunMethodLocalServices](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt)
implements a standing join. A new request checks existing links. A new link checks existing requests.
[Demand](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Demand.kt)
can answer a concrete mark, climb an abstract mark request, or answer/climb a static position request.
An answer does not close the request. A later deeper fact can produce a different answer; a new full
caller reference can require another climb. `RequestJoinTest` explicitly checks those cases.

Safe reduction: factor mark requests by their full initial, and position requests by their requested
static path. Store primitive membership within each group. Index the group once, instead of every mark
membership. A concrete `T` link needs only groups that contain `T`. An abstract `*\X` link needs the
requested marks outside `X`. Still perform the existing overlap and request-action checks.

This reduces physical entries and unnecessary scans. It does not reduce the set of required logical
requests. Initials are interned, but their IDs are scoped to their AP manager. Position requests retain
the complete already-normalized position path. An origin initial and a requested position are different keys.

Unsafe reductions: remove answered requests; merge origins by just base; treat a parent path as covering
every child request; filter the mark universe to marks currently present. These can lose future answers,
caller-specific support, or F75 cleaner requests.

Evidence gap: the existing complete census does not split mark/position requests or count distinct initials
per mark group. It does not identify the responsible rule family. Measure group density before choosing a
bitmap layout or assigning a savings percentage. Keep a compact singleton/sparse fallback.

## N2: cumulative state can replace accepted-delta history

[CalleeSubscriptions](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/CalleeSubscriptions.kt)
keeps an exact `AddedFactStore`, a list of accepted `Subscription` deltas, an index over their list IDs and
zero-only parts. [SubscriptionManager](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/SubscriptionManager.kt)
replays the newly returned delta directly. It does not read the retained part list for that replay.
The list and its index serve later publication delivery.

For ordinary subscriptions, store one stable entry per full caller reference and exact admitted fact group.
Keep the zero-only branch separate: its key is full caller reference, and its first accepted Reach/link-layer
payload wins. Replay each newly accepted delta transiently. Later delivery captures the complete immutable
candidate snapshots before callbacks and applies the same full matcher. This removes a retained object/index
entry for every growth step. Split the exact accumulator
from its indexed wrapper: this subscription consumer does not query the wrapper's second path index.
The callee link store still needs that index and must retain its full links for requests and Support.

There are two correctness requirements beyond ordinary storage packing:

1. Selection and transfer over a cumulative exact union must equal the union of the previous per-delta
   effects. Verify full exclusions, mark exclusions, must/exact leaves, layers, returned actions and cuts.
   Merely obtaining the same final finding count is insufficient.
2. Forward E6 must not retain every new cumulative snapshot in another store.
   [NdSummaryJoin](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/ConjunctionStore.kt)
   currently keys member inputs by complete `Subscription` values, including added Facts. Normalize a
   successfully matched member to its full `CallerRef` and link layer. The E6 transfer reads these headers
   and the publication target, not the matched input roots. Keep the outer callee/premise-set/publication-
   layer/call key, ordered member slots, every distinct binding combination and all growing conclusions.

The stable entry key comes from the admitted input, not the delta. An exact-only delta can have empty `E`
while its cumulative must group still has nonempty `E`. Merging by the delta header would join wrong groups.

P5 must preserve logical subscriptions and all their accepted leaves, not require one permanent object per
historical delta. This refinement needs a stated representation invariant and revised protocol encoding.
Do not claim that the existing proof for the part-list representation already proves the new encoding.

This is an algorithmic reduction of retained history, followed by representation work. It does not remove
distinct caller contexts or valid subscription groups. In forward E6, genuinely different full bindings can
still have many combinations; no combination cap is proposed.

## N3: coalesce transport and service work fairly

[UnitRunner](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/UnitRunner.kt)
drains an unlimited channel into the local queue before polling. `EventQueue` ranks zero Work first,
ordinary events second and nonzero Work third. A large stream of ordinary requests can delay nonzero Work.
A continuous incoming stream can also make the drain loop perform a long intake phase.
These are concrete mechanisms in the code; the current snapshots do not prove either is the main cause
of the measured backlog. Fairness must be measured as service delay, not inferred from one heap.

Use a grouped request mailbox per complete destination method key. Keep unique pending/in-service requests
in primitive groups. Queue one wake token per active mailbox. Process a finite batch and requeue the same
counted token if more requests remain. Suppress exact duplicates while a request is pending or in service.
Local `raise` continues to update the actor's standing relation synchronously.

Add bounded intake per runner turn and a finite service interval for nonzero Work. Keep immutable Work
priority snapshots and the existing actor quantum. This changes the scheduling choice, so the existing
strict rank-order test is a baseline test, not a requirement to preserve starvation. Validate complete
closure and termination under the new schedule. Incomplete reports and timings can change with scheduling.

Do not block producers on a bounded cyclic channel. A runner can produce work for itself or another runner;
blocking all producers can prevent the consumers from progressing. Batch size is a service quantum, not
a cap on requests. Do not drop overflow or declare COMPLETE while a mailbox has work.

One wake token per active destination bounds request event objects, even when all pending keys differ.
It does not bound the number of unique pending requests. These still occupy a compressed relation.
Pending-only dedup also does not suppress every repeat after completion. A lifetime admission cache would
duplicate N1 history unless it shares a carefully designed catalog; it is outside the first implementation.

The older b0 sample of 48 queue cells with 43 structural keys proves some repeats, but gives no global rate.
Do not use it to predict a percentage saving from deduplication. Mailbox grouping removes per-event wrappers
even without duplicates. Source grouping, scheduling and queue capacity are separate effects to measure.

## Decision and remaining limits

| Area | First action | Technical work that follows | What remains necessary |
| --- | --- | --- | --- |
| N1 | Factor the relation; make mark joins selective | Primitive memberships and group indexes | All standing logical requests until freeze |
| N2 | Replace retained delta history with cumulative entries; normalize E6 bindings | Stable handles and one positional index | Exact groups, full callers, matched member bindings and conclusions |
| N3 | Group/coalesce pending requests; ensure finite intake/work service intervals | Primitive mailbox pages and a smaller queue layout | Unique pending obligations and counted work |

Do not start by designing a packed heap for fifteen million unchanged request-event objects. Remove that
transport shape first. A FIFO ordinary-event lane and primitive Work comparator are then small representation
changes over the remaining scheduler entries. They do not establish that the analysis has fewer logical states.

A stronger algorithm that removes genuine request or context fan-out needs separate evidence and a new
semantic design. The current measurements do not justify SCC-local retirement, dropping marks, path-based
request subsumption, caller-context merging or reduced field limits. Large unique state may remain after
these changes. The detailed proposal therefore uses explicit equivalence and measurement gates rather
than promising that all OOMs disappear.
