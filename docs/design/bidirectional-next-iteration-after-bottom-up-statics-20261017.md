# Next core iteration after packed static propagation

Date: 17 October 2026. Status: proposal only. No optimization in this document
is implemented or selected. It uses runtime `ab6c3cd00` and the
[current project report](../reports/bidirectional-run1-bottom-up-static-20261017.md).
Earlier runtime rankings are stale. IR storage is outside scope.

| Project | Latest reached / completed / accepted forward report | Run-1 engine |
| --- | --- | --- |
| OWASP | run-7 / run-6 / run-5 | 265.789 s, complete |
| conductor | run-2 / run-1 / run-1 | 44.385 s, complete |
| ThingsBoard | run-1 / none / none | 322.250 s, incomplete; includes 41.830 s dump |
| Stirling-PDF | run-2 / run-1 / run-1 | 39.951 s, complete |

## What the measurements change

The ThingsBoard run-1 heap has 2,981,381 ordinary caller groups. Static
originals account for 84.932%, but concrete static originals now outnumber
abstract static originals. The complete primary census finds only 2,401
exact same-field static FLOW groups with nonempty mark exclusion and the
otherwise canonical identity header. Another 383,206 include that field
plus other geometry. Extending pure identity suppression alone is therefore
not the main next step.

The caller position index retains 1.772 GB within a 2.483 GB caller-storage
union. It has 28,790,956 admitted postings. This is an auxiliary collection,
not a semantic fact count. Removing the index algorithmically is attractive,
but its selectivity may save substantial CPU. Small-directory scans have a
limited measured scope: directories with at most 16 groups account for only
1.13% of postings; limits of 64 and 256 cover 11.75% and 38.51%. Do not assume
that eliminating many small directories removes most index bytes.
This favors a representation change for the broad index footprint unless a
measured scan policy can avoid comparable state without excessive matching.
The request and cleaner proposals instead avoid work before it is repeated.

The scalar initial population also does not show hundreds of concrete marks
per exact geometry. Its 144,295 nonzero concrete memberships form 112,644
cohorts, with one to seven marks each. A current-TAINT diagonal check covers
only 115,302 rows before its separate node budget. Its result cannot be
generalized to the 1,496,975 unavailable rows. Sharing execution across
concrete originals remains a hypothesis, not a selected representation.

There are two distinct problems. Large persistent caller/conclusion/cache
owners cause memory pressure. Repeated request admission and cleaner work
cause large allocation volumes and consume engine time. One change need not
solve both problems.

## Rank by expected retained-memory impact

The order below uses current owner footprints and known structure. It is a
selection order, not a measured net saving. Owner unions overlap.
The first priority is ThingsBoard run-1; later-run heap evidence is separate.

| Rank | Proposal | Current → new | Expected effect and limit |
| --- | --- | --- | --- |
| 1 | Reduce caller position-index storage | Boxed ordered postings at every admitted path → avoid the index for measured small buckets; use primitive ordered postings where selectivity is needed | Targets the measured 1.772 GB index. Scan coverage and replacement capacity determine net saving |
| 2, conditional | Release reproducible bound-rule lists | Strong method-bound lists remain cached → preserve resolution and selection, then allow reproducible lists to be reclaimed through the existing manager | The normal call/entry/exit sink-cache union retains 1.092 GB in ThingsBoard run-1. Selected/shared/discarded fractions and rebuild cost still need separate measurement |
| 3 | Allocate conclusion containers only when used | Both kind maps, a REACH array and both layer lists exist eagerly → lazy kind/layer storage and inline reach bits | Avoids known unused shells across a 2.217 GB conclusion-owner union. That union is not removable in full |
| Mainly temporary allocation | Admit each request cell without intermediate sets | Every offer builds and checks intermediate selections → check existing pending/committed cells directly and schedule only fresh cells | 95.527% of ThingsBoard offered members are duplicates. Committed/pending stores retain only 0.144/0.014 GB; large persistent savings are not established |
| Mainly temporary allocation | Batch eligible plain FLOW cleaners | Repeated scalar cleaner steps → one proved equivalent operation for an eligible interval | OWASP cleaner chains occur in 71.151% of run-1 allocation weight and 31.395% of normal-window CPU. Eligible frequency and removable work are not measured |

For the 60-second run-1 engine target, request admission and cleaner work
deserve parallel design attention even though their persistent-memory rank
is lower. No option yet guarantees completion within 60 seconds.

## Reduce caller position-index storage

**Current:** `CalleeSubscriptions` stores exact inputs in
`AddedFactAccumulator`. A separate `PathTrie<Int>` stores ordered boxed
handles for every admitted leaf position. New publications and request rows
use it to choose candidates, then the full AP kernel checks each candidate.
The index grows monotonically from deltas; current values can change without
removing earlier positions. The number of stale postings is not measured.

**New:** keep exact inputs and the matching kernel. First use measured fan-in
and query counts to choose where a same-base scan can replace the index.
Keep an index where a scan would cause too much matching work. For those
buckets, use a private primitive ordered handle representation. Do not change
the generic trie used elsewhere.

```kotlin
fun candidates(query: Query): List<Subscription> {
    val handles = when (val route = routes[query.base]) {
        is ScanBucket -> route.handlesInAdmissionOrder()
        is IndexedBucket -> route.lookupWithExistingPrefixRules(query)
        null -> emptyHandles()
    }
    val result = snapshotCurrentValues(handles.distinctInOrder()).toMutableList()
    if (query.isZeroPublication) result.addAll(snapshotZeroParts())
    return result
} // take snapshots before callbacks can grow the owner

// The complete existing AP/mark/premise checks still decide applicability.
```

Primitive buckets must support empty, singleton and growing states, stable
encounter order and duplicate-free admission. Retain the same run-1
prefix/extension rules, later-run mark-dependent lookup, whole request-batch
deduplication, ZERO handling and full caller/callee/call/stage identity.
The request visitor must still prevent mutation until all candidate effects
have been queued. Preserve cancellation checks during large visits.

**Why:** a scan can avoid an auxiliary relation entirely. Primitive postings
can remove per-handle objects when the relation is needed. The latter has a
broader measured surface than a small-directory-only policy. These are
alternatives for the same bytes; their gross targets must not be added.

Before implementation, prove the candidate-superset law against a full scan
and equal successful match sets after the unchanged kernel. Prove promotion
from scanning to indexing against current values, including broad ancestors
and request matching. Test both replay orders, growth during queued replay,
whole ND support and recursive calls. Measure postings/capacities, candidate
visits, actual matches, snapshots and bytes per bucket. A retained-memory
gain that makes run-1 matching much slower is not sufficient.

## Release reproducible bound-rule lists

**Current:** `TaintConfiguration.TaintRulesStorage.methodItems` strongly caches
method-bound lists. Outer providers can override rules, and forms can reject
bound rules. The normal call/entry/exit sink caches alone retain 1.092 GB in
the ThingsBoard run-1 heap. This does not identify a discard-only fraction.

**New:** retain immutable rule definitions and the same resolution behavior.
Make reproducible bound-list values reclaimable through the existing
`SoftReferenceManager`. Keep selected compiled forms under their current
ownership. Preserve the full resolution/validation/combination path on a
cache miss. A later selection-aware cache is a separate change and needs a
proof that selection is stable for its complete key.
Limit the first patch to the three measured normal sink-cache roles; measure
the other source/sink views before extending its ownership policy.

```kotlin
fun boundRules(method: Method, role: Role, allRelevant: Boolean): List<Rule> {
    val key = existingCompleteKey(method, role, allRelevant)
    return cache[key]?.get() ?: resolveExactlyAsToday(key).also { rules ->
        val ref = existingRefs.createRef(rules)
        if (ref == null) cache.remove(key) else cache[key] = ref
    }
}
```

The code is an ownership sketch, not a new cache API. Keep the existing
synchronization, empty-result behavior, ordered rule ordinals, configuration
lifetime and combination modes. Do not merge normal and `allRelevant` views.
Wire the existing analysis reference manager into the same provider before
prescan populates its lists; do not change only later cache misses. While
the manager is disabled, `createRef` can return null. Return the freshly
resolved strong local without caching it; do not add a strong fallback or a
new global manager.
Both source and sink rules can have complex mark conditions; preserve NNF
and mark-wise evaluation. No cube expansion or rule omission is proposed.
Measure source, entry-source and exit-source caches separately before
assigning them the sink-cache footprint.

**Why:** the manager can release reproducible bindings under memory pressure
instead of keeping every discarded or inactive binding. Selected forms and
other strong owners can limit the gain. Rebuilding may increase prescan or
engine time. Prove equal ordered bound rules and semantic observations after
arbitrary cache loss, including validation failures and overrides. Measure
hits, rebuilds, selected/discarded retention and the complete owner union.

`FormsCache` already uses managed soft references. Introducing them there is
not a new optimization. This proposal does not rewrite the reference manager
or add `WeakIdentityCache`.

## Allocate conclusion containers only when used

**Current:** every `ConclusionGroup` owns both kind maps and `BooleanArray(2)`.
Every FLOW/TAINT base group owns an array with two eagerly allocated layer
lists. Many groups use one kind and one layer.

**New:** keep the same logical group and delta/subsumption algorithms. Create
a kind map and layer list on first use; store the two reach flags in bits.

```kotlin
private var reachBits = 0
private var flow: MutableMap<Base, FlowGroup>? = null
private var taint: MutableMap<Base, TaintGroup>? = null

fun addReach(layer: Layer): Reach? {
    val bit = 1 shl layer.ordinal
    if (reachBits and bit != 0) return null
    reachBits = reachBits or bit
    return Reach.of(layer)
}
// Missing kind maps and missing layer lists mean empty, not unknown.
```

**Why:** required semantic state remains, but unused containers disappear.
This is a representation change with a direct structural target; it does not
assume that caller facts can be merged further. Preserve encounter order,
canonicalization, layer separation and whole-delta behavior when exclusions
change. Check the new JVM layout and promotion cost before claiming bytes.
Use current full-group sequences, not a test that only mirrors null checks.

In ThingsBoard run-1, the source invariant and class counts imply at least
2,611,579 unused kind-map shells, or 125.356 MB. Its 3,109,529 conclusion
groups also own 74.629 MB of two-flag arrays. These are gross structural
targets; check replacement layout. Layer-list shells and backings occupy
351.991 MB in the conclusion retained selection, but their unused fraction
is not measured. Shared stored trees do not supply a valid list-occupancy
denominator.
Conductor run-2 has a corresponding 209.615 MB lower bound for unused kind
maps and 105.332 MB of two-flag arrays. Its conclusion union retains
1.917 GB, versus 0.346 GB for caller storage. This supports the container
change across phases without applying run-1 static percentages to run-2.
PDF run-2 has 10,006,416 conclusion groups. Its unused kind-map shell lower
bound is 474.675 MB, and its two-flag arrays occupy 240.154 MB. Its conclusion
union retains 4.317 GB versus 0.342 GB for caller storage. For these later
phases, container work has a larger measured scope than caller-index work.

## Admit request cells without intermediate sets

**Current:** `collectRequests` borrows committed R, constructs `offered - R`,
then checks temporary P and can call `requestWork` for an all-P-duplicate
offer. The work queue deduplicates the scheduled work, but each offer still
does admission work. Existing queue-empty commits already batch propagation.

**New:** preserve R and P, the current barrier and request semantics. For a
singleton, check P first and borrow R only if needed. For a set, traverse it
once and build only the genuinely fresh delta. Reuse the owner’s existing
summary object rather than repeat its global directory lookup. The actor
does not currently retain that handle: adding it requires a full-MethodKey
binding and explicit clearing on freeze, failure and release, so completed
actors cannot pin old publications, request plans or subscribers.

```kotlin
fun offer(slot: Slot, mark: Mark) {
    if (temporary.contains(slot, mark)) return
    if (ownSummary.containsRequest(slot, mark)) return
    temporary.addFresh(slot, mark)
    requestWork()
}

fun flushRequests() {
    ownSummary.commit(temporary)
    temporary = RequestStorage()
}
```

**Why:** avoid builders, row views and repeated queue/directory operations
for already secured interests. This changes neither the final request union
nor fact transfer. Field exclusions and FLOW mark exclusions do not filter
request admission. Concrete-mark facts still do not propagate requests.

Prove `newDelta = offered - (R union P)` and that a nonempty P always has a
scheduled or active owner that must commit it before quiescence. Preserve
P-first reads only as an owner admission check; insert into P only after
checking R. A P-hit return relies on that already secured counted owner.
Preserve request-only actors, late concrete answers, input/request arrival orders and
original-slot routing. A duplicate request may be omitted; its associated
fact outputs, mark exclusions and source/sink effects must still execute.
Keep numeric counters allocation-free where possible. Batch-dependent
diagnostic counts may change under the selected observation contract.

## Batch eligible plain FLOW cleaners

**Current:** `cleanChain` creates per-step lists and result wrappers. Plain
selected-mark cleaners repeatedly update the same FLOW geometry and mark
exclusion. Field cleaners also execute typed read/clean/write chains.

**New:** initially group only consecutive plain selected-mark cleaners with
identical base, full path and reach. For eligible run-1 FLOW inputs with a
singleton original, perform the same geometry partition once, union the
actual selected marks once and produce the same final headers and requests.
Compile immutable interval metadata in the existing form lifetime.

```kotlin
if (interval.supports(input)) {
    emitEquivalentRequests(interval, input)
    emitEquivalentFinalFlow(interval, input)
} else {
    evaluateOriginalOrderedSteps(interval.steps, input)
}
```

**Why:** avoid repeated transfers and intermediate containers, rather than
only making each wrapper smaller. The inclusive hotspot shares do not show
how many steps qualify. Measure interval lengths, qualified inputs and
removed intermediate outputs before predicting a speedup.

Prove equality with ordered scalar composition, including already excluded
marks, all tails/layers, original support and static partial/disjoint matches.
Nonstatic selected FLOW cleaners still issue their required requests for
already excluded marks. Static cleaners retain their current spatial rule.
Generic excluded-target edges issue requests before geometry checks; they
are outside this shortcut. Prescan must still see every effective selected
mark. Do not cross Field/Kill operations, filters, sources, sinks, triggers,
ND support, call stages or observer boundaries. Keep these on fallback.

A direct streaming evaluator for typed Field read/clean/write is a possible
later step. It must preserve read filters, cuts, strong writeback, demotion
and temporary erasure; an absent mark alone does not prove identity.

The first interval implementation must use the engine-owned collector with
no escaping or reentrant callbacks. Keep original-step cancellation checks
and secure the required request effects before the final output checkpoint.
Prove failure/cancellation behavior as well as equal successful unions.
Custom consumers stay on scalar fallback until their callback-prefix
contract has a separate proof.

## Deferred work and correctness priority

Do not redesign the publication barrier just because pending lists exist.
The current heap has 103,676 pending fragments, whose `Publication` shells
occupy about 2.49 MB. Their payload retained union is not measured. Prove a
compatible-header coalescing law and measure complete payload ownership
before promoting this as a major memory optimization.
There is a separate time opportunity: current publication storage merges
cumulative state but still delivers fresh fragments individually. Combining
fragments with the same full premise/base/kind/layer/tail/exclusions before
replay could avoid repeated joins against large caller inputs. First measure
compatible-header counts and prove equal requests, ND support and semantic
observations. Distinct-premise counts alone do not prove that these fragments
can be combined.

Factoring mixed static FLOW, nonempty mark exclusions or concrete-premise
diagonals needs a new exact codec and client/observation proofs. The current
census gives useful cohorts, not permission to drop their nonidentity part.
Reference-manager or weak-identity-cache redesign remains a possible future
optimization, with no current measured registry-saving estimate. It requires
its own design and must not be introduced as incidental cleanup.

The two PDF exact-location losses also need a separate correctness change:
retain actual return provenance through synthetic exits and trace/report
selection. Current evidence proves the last-return display mechanism, not
equal underlying paths. Preserve source/sink identities and rule occurrences;
diagnostic callback counts may vary. This report fix is not a memory proposal.

## Plan after selection

1. Select a memory option and a run-1 time option from this document. Pin their
   exact behavior and reference relation before implementation. Follow
   [proof-guide.md](../../spec/proof-guide.md); separate algebraic proofs from
   runtime adapter and measurement evidence.
2. Implement each selected change in a separate reviewable patch. Keep full
   ordinary fallbacks and add meaningful differential/representation checks.
   Do not combine index, rule-cache and cleaner changes into one causal claim.
3. Run the required common/JVM/consumer and application checks. Record known
   strict precision failures explicitly. Commit before project measurements.
4. Repeat only the fixed four project configurations against fresh main.
   Record run-1 engine time, all reached/completed/accepted iterations, exact
   findings/locations, native/JFR profiles and guard heaps as applicable.
5. Accept performance claims only with equal completed work or explicit
   incomplete outcomes. Recheck retained owners and request/candidate work;
   do not substitute allocation volume for live memory.
