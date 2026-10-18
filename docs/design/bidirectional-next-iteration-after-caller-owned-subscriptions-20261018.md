# Next iteration after caller-owned subscriptions

Date: 18 October 2026. Status: **proposal only; no runtime change selected or
implemented here**. This proposal uses corrected runtime
`63d5481e4e4b69ecf042c1149bb574ad40fd1624` and the new
[performance report](../reports/bidirectional-caller-owned-subscriptions-performance-20261018.md).
Earlier candidates' owner sizes and timing results are not used as current evidence.

Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**, conductor **run-1 / none / none**, ThingsBoard
**run-1 / none / none**, PDF **run-2 / run-1 / run-1**. All four miss the
60-second run-1 engine target. The next work must address run-1 first.
IR storage is out of scope. Existing reference managers and weak caches are
not changed by the current implementation.

## Current evidence and order

Conductor's run-1 heap has 224,619,895 physical objects and 7,611,768,576
bytes of reachable shallow heap. Its summary-store owner scope is
838,351,344 bytes. The unresolved-observer input scope is 664,392,968 bytes,
with 1,760,022 retained accumulator entries. Ordinary caller input scope is
80,341,472 bytes; P/A requests are 14,754,056 bytes; authoritative requests
are 5,014,504 bytes; shared view mark roots are 3,348,136 bytes. This supports
avoiding unused observer work and duplicate summary state before changing
request representation. These owner scopes overlap. Their sum is not a
removable-memory estimate. The IR owner is measured but excluded.

ThingsBoard did not produce an OOM heap. Its run-1 counters show
1,820,403,014 request mark scans and 324,655,547 offered request members.
Its normal CPU sample has 48.83% in caller-owned routing/reachability
contexts. JFR attributes 133,727,072,856 bytes of sampled allocation weight
to fixed-graph search scratch arrays. These are workload and allocation
observations, not retained-byte estimates.

PDF's later heap has a different pattern: run-2 statement-directory roots
retain 3,389,495,568 bytes, with 7,779,328 conclusion groups. Its completed
MAT output has the strict shutdown-inspection qualification in the report.
That result does not establish its run-1 peak or justify inserting a
run-2 storage redesign ahead of the selected run-1 work.

The current heap interpretations are under
`caller-owned-subscriptions-retest-20261018/heap-tools/interpretation/`.
The source review for the first two proposals is
`reviews/summary-union-observer-source-20261018T140812.195819Z/` under the
same evidence root. It binds 12 current source files. The CPU/request
source notes are in `reviews/cleaner-request-work-20261018T134422.620949Z/`.
These are design arguments and measurements, not completed proofs of the
new optimizations.

The table orders **expected run-1 memory benefit**, with confidence limits.
It is not a claim of measured savings or a required implementation order.
Algorithmic work avoidance comes first where the evidence supports it.

| Priority | Proposal | Current target and expected effect |
| --- | --- | --- |
| 1 | Disable unused unresolved-call observation | Avoid the measured 664.4 MB observer scope and observation-only scalar work when tracking is disabled. Shared payloads can limit the exclusive byte saving. |
| 2 | Use one run-1 summary union for raw and selected results | Avoid duplicate group/container state inside the 838.4 MB summary scope. Do not divide that scope by two: payloads and interners can be shared. |
| 3 | Make rebuildable derived rule-cache values reclaimable | Conditional memory opportunity in the rule-cache scope, whose first 80 classes contain at least 362.9 MB. Exact eligible values and exclusive retention need a separate design check. This trades rebuild time for memory. |
| 4 | Publish static interests once per source component and field | Avoid repeated view offers, mark-set rebuilds and child messages. Retained-memory benefit is uncertain; it adds a derived component union. ThingsBoard has no current heap for this estimate. |
| 5 | Union caller request destinations before mark expansion | Avoid repeated transient mark builders and scans for equal destination interests. Full caller inputs and per-site P/A remain necessary. No large retained-byte saving is established. |
| 6 | Cache ancestor sets for requested source components | Replace many search arrays and boxed pair-cache entries. Conductor's current pair-cache scope is only 7.0 MB; the strong target is allocation and CPU, not large retained memory. |
| 7 | Batch compatible plain FLOW cleaners | Avoid repeated local results, filtering and requests on OWASP. The profile supports a CPU/allocation target; no retained owner size is measured. |

Start the memory work with the first two proposals. For ThingsBoard's time
budget, evaluate component-level request publication and ancestor caching as
separate changes. OWASP needs a separate cleaner-path change; it has no
static demand views. A single static optimization cannot meet every target.

## Disable unused unresolved-call observation

**Current:** `SharedObjects.unresolvedObserver` casts the interpreter to the
observer interface. `JIRInterpreter` implements that interface even when its
`externalMethodTracker` is null. The engine therefore retains concrete
observer inputs before the callback returns without an effect. The packed
static call path can also select scalar processing solely because that
observer object is nonnull. Conductor's complete physical census contains
zero `ExternalMethodTracker` instances, while the observer-input scope above
is large.

**New:** expose whether the observer is active. Construct its accumulator and
use the observation-only scalar fallback only when it is active. Keep all
ordinary unresolved-call transfers, rule effects, ND fallback and enabled
observer behavior.

```kotlin
// Pseudocode: preserve the default for other observer implementations.
interface UnresolvedCallObserver {
    val enabled: Boolean get() = true
    fun reached(call: Call, base: Base, plan: CallPlan)
}

class JIRInterpreter(/* existing fields */) : UnresolvedCallObserver {
    override val enabled get() = externalMethodTracker != null
}

val unresolvedObserver = (interpreter as? UnresolvedCallObserver)
    ?.takeIf { it.enabled }

val observed = if (run1 && unresolvedObserver != null)
    AddedFactAccumulator(ap) else null

// The same capability guard controls observation-only scalarization.
if (unresolvedObserver != null && stageNeedsObservation(plan)) {
    visitConcreteEntriesForObservation()
} else {
    continueOrdinaryPackedCallProcessing()
}
```

**Why:** a disabled diagnostic has no semantic result. Avoid its retained
history and the work needed only to produce that history. Do not claim that
all packed scalar evaluations are observer work: other effects still need
scalar fallback.

Before implementation, prove that the disabled observer has no effects,
that each retained accumulator read serves only observation deduplication,
and that the packed path covers the same non-observer results. Check the
enabled path separately, including callback-count scope. Use a red control
with a disabled tracker and actual packed concrete static input; compare
all summary, request and rule-observation atoms, not just callback counts.

## Use one run-1 summary union

**Current:** `RunSummaryStore` has separate raw and selected unions. In
run-1, `published()` returns the entire retained delta and selection is
always true. The engine folds that delta into both unions. The conductor
summary scope contains 2,045,888 `ConclusionGroup` objects, 4,091,776
`HashMap` objects and 2,045,888 Boolean arrays. The census establishes
container cost; it does not prove that every object can be removed.

**New:** use the raw union as the selected result view for run-1. Later
restricted runs keep separate raw and selected unions.

```kotlin
class RunSummaryStore(ap: ApManager, selectAllRaw: Boolean) {
    private val raw = ConclusionUnion(ap)
    private val selected = if (selectAllRaw) raw else ConclusionUnion(ap)

    fun add(premise: Premise, facts: Facts) = raw.add(premise, facts)
    fun select(premise: Premise, facts: Facts) {
        if (selected !== raw) selected.add(premise, facts)
    }
    fun all() = raw.entries()
    fun demandEdges() = selected.entries()
}

// Only the run-1 policy selects all raw results.
val summaries = RunSummaryStore(ap, selectAllRaw = config.run1)
```

**Why:** avoid storing and merging a second relation whose contents are
already determined by the first. Keep the ordinary published-summary store,
caller replay, initial grammar, full premises, AP payloads and NORMAL
persistence unchanged.

Prove equality of the old raw and selected unions at the completed run-1
handoff. The old raw insertion can precede selected insertion at a
cancellation checkpoint; do not assert equality of every interrupted prefix.
Handoff and persistence must retain their COMPLETE guards. Inspect every
selected-result read: aliasing must not expose a new
semantic result between raw insertion and ordinary publication. Preserve
pending publication delivery and abnormal-run cancellation behavior. Later
restricted-run selection must remain separate. Tests must exercise late
summary growth, all layers, ND premises, static identity compaction, NORMAL
persistence and the handoff after a completed engine. No removal of raw
summaries is proposed for later runs.

## Reclaim rebuildable derived rule-cache values

**Current:** ordinary method/field rule caches retain derived rule data.
The conductor rule-cache retained histogram is capped at 80 classes and
contains at least 362,860,960 bytes in those classes. That is a lower bound
for the owner scope, not a saving estimate or the size of sink rules alone.

**New, conditional:** use the existing managed soft-reference mechanism for
exactly rebuildable derived method-rule values. Keep the original rule
catalog and all live semantic state strong. Reuse the existing manager;
introduce no `SoftReferenceManager` rewrite or `WeakIdentityCache`.

```kotlin
// Pseudocode; choose actual cache/value boundaries only after the audit.
fun methodRules(method: Method): Rules = derivedCache.get(method)?.get()
    ?: rebuildFromFixedRulesAndMethod(method).also { value ->
        derivedCache[method] = existingRefManager.manage(value)
    }
```

**Why:** memory pressure can release a cache value and rebuild it on a later
access. This is a representation/lifetime trade, not fewer semantic facts.
Rebuild cost may make it unsuitable for the hottest values.

First prove reproducibility, stable rule/mark/occurrence identities and
concurrent rebuild behavior. Confirm that keys, active forms and report
witnesses do not keep the whole target strongly reachable. Do not reclaim
source/sink observations, pending requests, premises or original rules.
Measure cache clears, rebuild counts, exclusive owner reduction and engine
time before selecting this option for implementation.

## Publish one static delta per source component and field

**Current:** the directory groups source references by SCC, but each full
method source still offers its committed delta to every reachable view.
A new view reads each source getter in a matching group. Equal interests
from methods in the same SCC can reach the same view repeatedly; duplicate
rejection then scans/rebuilds mark sets under the service lock. ThingsBoard
observed 105,153 source references in 3,044 field/SCC groups. Actual native
stacks show `publishSourceDelta`/`subscribeView` → `offer`, including monitor
entry and mark-set work.

**New:** retain authoritative requests in method summary storage. Maintain
one derived transport union for each `(source SCC, field)`, and publish only
its fresh delta. A late view reads each matching component union once.

```kotlin
fun sourceCommitted(key: MethodField, marks: MarkSet) {
    val group = componentSources[key.field, component(key.method)]
    val fresh = group.transportUnion.addAndDelta(marks)
    if (fresh.isEmpty()) return
    for (view in reachableViews(group)) view.offer(fresh)
}
```

**Why:** methods in one SCC have the same fixed-graph reachability relation
to a view entry. Union before fanout avoids repeated work for the same
transport interest. The derived union adds cache state; its memory cost
must be compared with lower traffic and temporary allocation.

Prove exact equality of every view's interest set to the current procedure,
including unknown-method fallback and full field identity. Preserve
registration-before-snapshot, late views, racing deltas, counted delivery
and teardown. Use the existing method-empty source commit; no new global
barrier or arbitrary pending-memory cap is needed. Do not merge full call
bindings, actual concrete proof, ND support or method contexts.

## Union caller destinations before mark expansion

**Current:** for each eligible FLOW input, `joinRequestGroups` expands all
matching row marks into a temporary builder. It then checks the destination
request store and unions by destination. Repeated input bindings can scan
the same immutable mark payload for the same original slot many times.
ThingsBoard's run-1 has 1.82 billion member scans and 93,685,420 target-climb
candidates. These are counts of work, not distinct requests.

**New:** perform the same geometry and coverage checks, but first group
matching immutable selections by `(full destination method, original slot)`.
Deduplicate identical selection payload references within that owner turn;
then take their union minus the destination's committed requests once.

```kotlin
val mapped = Map<Target, SelectionReferences>()
forEachEligibleFlowInput { input, rows ->
    for (row in rows) {
        if (!slotMatches(row.slot, input.current)) continue
        if (coveredSameFieldRelay(input, row.slot)) continue
        mapped[targetOf(input.original)].add(row.marks)
    }
}
for ((target, selections) in mapped) {
    val known = summaryStorage(target.method).borrowRequest(target.slot)
    emitRequests(target, unionDistinctSelectionsMinus(selections, known))
}
```

**Why:** compute the same outgoing set with fewer member visits and transient
sets. This grouping is temporary. It creates no retained input history or
cross-run delivered-pair cache. Actual concrete answers retain their separate
actual-T check; per-site P/A and full input rows remain unchanged.

Prove union/filter distributivity for each full target, exact destination
mapping and immutable payload lifetime. Distinct wrappers may share one
underlying selection; use a stable descriptor rather than assume wrapper
identity is enough. Never suppress a request based on FLOW mark exclusion.
Check mixed Base/static batches, late input, both P/A frontiers and known
requests arriving during a turn. Measure actual saved scans, not an assumed
1.82-billion saving.

## Cache ancestors for requested source components

**Current:** each new `(entry SCC, source SCC)` pair runs a graph search with
fresh arrays sized to the entire DAG. The cache stores boxed pair answers.
ThingsBoard has 48,000 components and 704,682 observed cached pairs.

**New:** build the reverse DAG once. On first need for a source component,
compute its complete ancestor set. Reuse that set for every entry/field
query for that source component.

```kotlin
fun isReachable(entry: Method, source: Method): Boolean {
    if (entry == source) return true
    val from = component(entry) ?: return false
    val to = component(source) ?: return false
    if (from == to) return true
    return ancestors.computeIfAbsent(to) { reverseReachability(to) }[from]
}
```

**Why:** one traversal per queried source component replaces many traversals
per pair. A bit set or another measured set representation replaces boxed
pair keys. The graph, rather than fields or marks, determines the answer.
Keep direct-edge certification separate from transitive reachability.

Prove `entry in ancestors(source)` iff the current fixed-DAG search accepts,
including cycles through the existing SCC quotient, reflexivity and unknown
methods. Compare cost as `distinct queried sources × graph size` versus
`distinct queried pairs × graph size`; do not claim a strict win for a single
query. Measure retained ancestor-set bytes as well as allocations. Avoid an
eager all-component transitive-closure table. Cancellation and concurrent
first construction must retain their current guarantees.

## Batch compatible plain FLOW cleaner actions

**Current:** OWASP's normal run-1 sample has 33.78% in cleaner contexts and
13.01% in type-filter contexts. It offers 61,284,189 request members, of
which 60,948,808 are already temporary duplicates. These aggregate counters
do not identify every offer as cleaner work. The source path repeats
cleaner evaluation, local results and request calls for compatible actions.

**New:** combine only adjacent plain selected-mark nonstatic FLOW actions
with the same complete input and geometry. Apply the union of selected
marks once, and emit the same original-owner request union. Stop at every
field action, kill, guard, source/sink observation or ND boundary.

```kotlin
for (interval in splitAtSemanticBoundaries(cleaners)) {
    if (interval.isCompatiblePlainNonstaticFlow(input)) {
        cleanSelectedMarks(input, interval.selectedMarkUnion)
        emitOriginalRequests(input.original, interval.selectedMarkUnion)
    } else {
        ordinaryCleanChain(interval, input)
    }
}
```

**Why:** avoid repeated work in a narrow algebraic interval. Do not reorder
conditional rules or treat STATIC selected-static behavior as this branch.
Keep every request even when a value is killed. A separate call-local pure
filtered-root memo is a later option only if filtering is reproducible and
all edge/request/cut callbacks still execute; no global cache is proposed.

Prove exact result, mark-exclusion and request unions for the eligible
interval. Preserve full headers, support and layers. Add counterexamples for
field actions, cleaners with guards, mixed tails, source/sink observations
and ND rules, then use the ordinary chain as a differential oracle. Do not
promise the whole 33.78% as a saving.

## Required gates for any selected change

Follow [proof-guide.md](../../spec/proof-guide.md): construct the restricted
reference and optimized models, prove the required equivalence or coverage
and scoped cost law, then develop red/green runtime controls. Keep full AP
and ND semantics, the nine initial families, DEMAND policy and Empty-E
writes. Reuse the existing cleaner normalization.

Fix or separately design report-location and rule-variant grouping before
claiming the project no-loss/occurrence gates pass. The two PDF location
losses, OWASP's 5,067 extra occurrences and nine application precision
failures remain open acceptance issues. These are not waived by memory wins.

After a selected implementation is committed, repeat only the prescribed
project settings with fresh source/input bindings. Record each numbered
engine and barrier, latest reached/completed/accepted report, strict
identities and occurrences, both profile windows where reachable, and each
memory-guard capture. Measure actual exclusive owner and allocation changes;
do not add overlapping retained scopes or reuse this runtime's sizes as the
next runtime's results. No IR storage optimization is proposed.
