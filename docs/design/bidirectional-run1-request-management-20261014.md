# Run-1 request management: workload and standing memory

Date: 14 October 2026. Status: investigation; the static request candidates are superseded by
the selected [abstraction-owned demand redesign](bidirectional-run1-abstraction-demands-20261014.md).
No runtime change is included here.
Measured runtime: `a23ad7fcd99553190c82557cff6076503959aec6`. Later commits change documentation only.
The [current performance report](../reports/bidirectional-request-set-barriers-performance-20261014.md)
binds the fixed inputs and evidence. The old optimization proposal remains historical.

The relevant ThingsBoard measurement reached **run-1, forward, limit 1**, and stopped with OOM.
It completed no engine iteration and accepted no report. The target is a complete run-1 engine
within 60 seconds. Prescan and the report barrier are outside that target. No new scan was run
for this investigation.

## What the 2,267,943,216 bytes mean

This number is a retained-set union for the standing N1 stores in the pre-cancel heap. It includes
mark and position request memberships, row objects, origin directories, query patterns, path
indexes, hash tables and backing arrays. It does not count 2.27 billion requests. It also does
not measure a queue of requests that await their first processing. Requests already processed
remain present so that a later added fact or a new caller of an existing fact can join them.
The other retained-owner rows overlap this selection and cannot be added to it.

The captured heap and later failed engine END counters show:

| Counter | Captured heap | Failed END | Interpretation |
| --- | ---: | ---: | --- |
| Mark memberships | 17,573,638 | 17,577,042 | Distinct local request-chain/mark pairs admitted |
| Mark rows | 188,247 | 188,260 | Request chains, each with a set of marks |
| Position memberships | 2,401,903 | 2,401,904 | Distinct local position/origin pairs admitted |
| Position rows | 2,401,903 | 2,401,904 | Distinct requested static positions per actor; all observed rows have one member |

The captured stores contain 19,975,541 memberships in 2,590,150 rows, across 76,943 stores.
Their origin directories hold 196,418 local entries; an initial used by several actors contributes
an entry to each actor. Marks account for 87.98% of memberships. Static positions account for
92.73% of rows. The mean captured mark row has 93.35 marks. Position paths are already cut to at
most two accessors. Thus the measured growth has two dimensions: many marks per request chain,
and many actor/static-position pairs. There is no isolated mark-versus-position byte partition.

Failed END also records 56,560,503 rejected duplicate admissions, 113,166,651 deferred mark climb
callbacks and 48,463,981 unions into an existing phase target. Duplicates are work, not extra
standing memberships. These later counters are not a decomposition of the captured bytes.

N1's selected retained set contains 956,987,168 bytes of `int[]` storage. Request frontiers and
active captured views have a separate 80,281,160-byte retained selection. The supplemental class
census also finds 3,622,850 request path nodes and 2,426,485 buckets. Those class populations are
global cohorts, not disjoint parts of the selected byte union. They establish substantial index
cost; they do not establish that all logical memberships are necessary.

## The request initial fact is already normalized

The user's simplification is valid: a request initial does not need a tail exclusion. The current
core already requires this shape in `RequestStore.add`, `addMarks`, `RequestGroupCursor` and
`actOnRequest`:

```text
request initial = (base, path, STAR tail, Empty field exclusion, STAR mark)
mark request    = (full MethodKey, base, path, requested mark)
```

`policyInitial` uses an empty path. A static position answer uses its static path. `ApManager`
interns these initials, so distinct exclusion or tail variants cannot create extra current rows.
Resetting this exclusion therefore saves no current memberships. A smaller request key can omit
the fixed fields, but that is a representation change.

The matched raw FLOW link still needs its field exclusion, layer and mark exclusion for eligibility.
The mark exclusion determines which requested marks can climb. These are separate from the empty
exclusion on the request initial and must remain in the raw added-fact store.

Position request handling is independent of the requesting initial after the shape check. It
matches the requested static path, emits that path as an answer, or climbs on the matched raw
caller's premise. A `(full MethodKey, requested position)` key can therefore remove irrelevant
request-origin distinctions from this operation. In this measurement every position row already
has one origin, so this change alone does not reduce the 2.40 million logical position requests.

## First algorithm candidate: combine equivalent FLOW routes before scanning marks

**Current.** For each geometrically eligible raw FLOW group/caller, the core scans the requested
marks, tests the link's mark exclusion, and emits one climb callback per eligible mark. The phase
then unions those callbacks by `(full caller MethodKey, original caller InitialAp)`. Different
raw links can produce the same result. The 48,463,981 repeated unions show that this occurs within
phases; they do not prove a corresponding number of avoidable stored requests or seconds.

**New.** Build a request-routing projection with one entry per exact outgoing target. For a
particular query, first perform the original geometry check. If the eligible links to target `t`
have blocked mark sets `X1 ... Xk`, retain the combined blocked set:

```text
Xt = intersection(X1 ... Xk)
marks sent to t = requested marks minus Xt
```

This is exact because a mark can climb if at least one eligible link permits it. An empty blocked
set permits all requested marks. Absence of a route must be represented separately from an
existing route with an empty blocked set.

For a policy-root query, every nonempty same-base FLOW tree passes the geometry check. A projection
per actor/base can therefore share routing across the raw paths, exclusions and layers. For a
positioned static mark query, geometry still depends on its path and the raw link's exclusion and
layer. Use the current join there until a query-specific projection is separately designed.

Update the projection incrementally:

1. New requested marks `D` use `D minus Xt` once per target.
2. A newly eligible target uses all standing requested marks permitted by its gate.
3. When a new eligible link changes `Xold` to `Xnew`, route only standing marks in
   `Xold minus Xnew`.
4. A raw geometry update that leaves a policy-root target and its combined gate unchanged causes
   no further mark-routing work for that target.

**Why.** This removes repeated mark/link intersections, callbacks and target unions before they
occur. It can reduce temporary work and allocation. It preserves the admitted logical demand
set, so it does not by itself remove the 17.58 million standing mark memberships.

Keep every original raw link and caller reference for summaries, source support and witnesses.
Only the final request action is projected. The actual dependency still connects full method
contexts and original caller initials. Do not combine different targets or reset a raw link's
exclusions. The projection needs captured horizons, bounded service and next-phase handling for
reentrant admissions, as the current frontier does. It needs no extra per-target history of every
sent mark: fresh-demand and gate-relaxation deltas determine the required sends.

Before selection, measure eligible raw groups versus distinct projected targets, gate changes,
policy-root versus positioned mark rows, and projected storage cost. A new index can cost more
than it saves when most routes are unique. The existing duplicate-union total also includes
different request rows that reach the same target; it does not isolate reuse within one projected
row. First consider a phase-local projection to bound retained state. A persistent projection
needs an explicit memory budget and a measured benefit. Validate the final request and answer closure against
the scalar algorithm under both arrival orders, late callers and reentrant updates. Then validate
sources, sinks, support and findings; callback counts will intentionally change.

## Main memory hypothesis: share demand across equivalent request dependencies

**Current.** A routed mark is admitted and stored in every receiving actor's request chain. A
static position is also stored and indexed per actor. Even when many actors end with the same
demand, exact local deduplication cannot remove the copies between actors.

**New, subject to measurement.** Treat request routing as a separate demand graph. Its nodes keep
the full actor context and exact query chain. A FLOW route with no blocked marks is a transparent
dependency. In a strongly connected component made only of such dependencies, each node has the
same requested mark set at closure. A component could own that set once. Each original node must
still react to concrete answers and late links with its own initial, summaries and support.

Assess position demands separately. An abstract static ROOT leaf with an empty field exclusion,
and a canonical abstract caller initial on the static base (STAR tail, Empty field exclusion,
STAR mark), forwards every nonempty requested position to that caller. The root is above the
requested position, so this operation climbs rather than answers;
its mark exclusion does not affect a position request. If a proven component of these universal
position routes exists, its position-demand set and query geometry could be shared instead of
creating every actor/position row. Keep a shared set and version with actor-specific watchers;
expanding the shared set back into every actor's directory would lose the memory benefit. Here
the position graph has full MethodKeys as nodes and positions as labels, after removing the unused
request origin. Creating a separate graph node per actor/position would retain the product we
want to avoid. Mark graph nodes instead keep the full MethodKey and exact request initial.
Request edges point from callee to caller. A method call-graph component alone is insufficient:
request geometry, caller premise and exclusion checks define the actual dependency. Partial
static routes must remain separate. A class-position answer cannot replace its field-position
answers; every requested position still needs its own local effect.

**Why.** This can remove repeated standing demand sets and internal propagation, which directly
addresses the large memberships and position indexes. Its benefit depends on actual equivalent
dependencies. Acyclic routes, blocked marks and different queries limit sharing. The current
evidence has no component-size or duplicated-set census, so it does not support a byte estimate
or selection for implementation. Actor-specific answers and their method analysis still run;
sharing demand does not remove those required effects.

First measure transparent dependency counts, component sizes, repeated mark sets and position
sets, and the storage that a graph would add. A detailed design must cover new links that merge
components, committed horizons, actor ownership, late answers and final run closure. A newly
attached local watcher must join the existing shared demand, and each actor must emit its own
required initials. Ordinary mark exclusions cannot be ignored to create larger components.

## Priority and boundaries

For run-1 time, investigate FLOW routing projection first: the source equivalence is clear and
the counters show repeated routing. For standing memory, measure demand sharing first: it is the
candidate that can remove copies of the logical relation rather than only shrink its containers.
Keep the existing sparse N1 storage proposal as a fallback if the demand cannot be shared enough.
Its 533.30 MB model remains a representation estimate, not a measured saving.

Do not retire a request after its first answer. Do not prune it merely because no concrete source
mark has appeared yet. Conditional sources can produce marks later and can themselves depend on
requests. Any future capability filter needs a conservative proof over these dependencies and
late links. Such a filter must include possible conditional source actions, mark conversions and
all other mark generators; an unresolved caller remains unknown. It could avoid impossible joins
only after a closed upper bound is proved, with the logical obligation still represented if the
request-closure contract requires it. No such bound was measured here.

Preserve F75: selected-mark cleaners still request their mark for every same-base FLOW
input, including excluded marks and disjoint paths. Admission of an already standing obligation
can be idempotent; suppressing a new required obligation changes the contract.

This investigation selects no core change. IR storage, SoftReferenceManager rewrites and
WeakIdentityCache remain outside it. Broad N3 queue changes remain deferred. Any selected design
must pass the fixed-project comparisons and record the latest reached, completed and accepted
run-N as well as run-1 engine time.

## Source and evidence

Source checks use [request admission and row keys](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/RequestStore.kt),
[request construction and scalar actions](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Demand.kt),
[grouped geometry and mark scans](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/RequestGroupCursor.kt),
and [outgoing target unions](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RequestFrontier.kt).
The [AP specification](../../spec/ap.md), sections 4.5, 4.10 and 8.8, defines standing and late-link behavior.

Small saved evidence, under the evidence root named in the current performance report:

- `heap-review/primary-comparison-1/comparison.json`: complete selected-owner and store census.
- `heap-review/supplemental-components-review-1/evidence.json`: closed component tables and replacement census.
- `projects/thingsboard/bidi`: primary analyzer log and failed END counters.

The existing duplicate-union counters do not identify all equivalent routing targets or graph
components. Those measurements remain the next investigation, rather than a claimed result.
