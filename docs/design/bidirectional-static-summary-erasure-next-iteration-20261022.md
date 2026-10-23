# Remove repeated run-1 work

Date: 23 October 2026. Status: **proposal; paired measurements and
representative heap analysis are complete**. No proposal below is implemented.
Measured candidate: `b68b3a1c9389c8d1b8ab6ad513af0c586a546895`.
Read the [current report](../reports/bidirectional-static-summary-erasure-performance-20261022.md)
and [proof guide](../../spec/proof-guide.md).

## Evidence and recommended order

Thingsboard GROUP completes run-1 in 155.560 s, 177.971 s and 131.923 s.
All three commands then reach run-2 and end with OOM. Their latest COMPLETE
and accepted forward iteration is run-1. FULL ends with run-1 OOM in all three repetitions,
with no COMPLETE or accepted forward iteration. There is no completed
Thingsboard GROUP/FULL time ratio.

Conductor's first closed profiled pair completes run-1 in 18.213 s with
GROUP and 14.549 s with FULL. Both later reach run-2 OOM; their latest
COMPLETE and accepted forward iteration is run-1. GROUP admits more call
inputs and provisional findings despite using fewer mark identities.
This is a measured reason to count avoided work and precision effects,
not assume that smaller mark sets always make the engine faster.

The first Thingsboard GROUP run-1 has 3,469,025 input arrivals, 4,975,498 initial
candidates and 321,765 fresh initials. Its peak sampled unfinished-event
count is 73,671. The three GROUP run-2 peaks are 958,627, 959,149 and 962,338 accepted
unfinished-event reservations. Those counts include running work. They
are not exact queued-fact counts or byte sizes.

The first FULL run-1 heap has 8,806 LinkIn event objects, while method
execution stores retain 1.77 GB and static service/saved-point owners retain
1.30 GB in separate overlapping union views. Input events are not the main
retained run-1 owner in that snapshot. The end-list census finds only
102.7 MB across all three sink roles, with a 480-byte retained union for
nonempty lists. The 1.29 GB rule-provider subtree is not an ignored-action
saving. Do not present queued-input batching as a proven solution for
that run-1 heap.

The first GROUP run-2 profile attributes 25.35% of Java CPU samples and
25.11% of allocation weight to `RunMethodAnalyzer.addLink`. Publication
index insertion appears in 23.22% and 23.70%, respectively. These contexts
can include required work. Do not sum them or assume all insertion work
comes from duplicate backfill.

| Run-1 priority | Proposal | Expected benefit | Evidence still needed |
| --- | --- | --- | --- |
| 1 | Merge temporary static supplies before retaining them | Remove duplicate pending payloads and service offers; at least 2.59x pending-record reduction in the FULL run-1 snapshot; owner scope 521.8 MB | Two large FULL batches each have 13 complete region keys; full atom memberships and GROUP peak remain to be measured |
| 2 | Skip no-op GROUP cleaning and repeated normalization | Remove selected-cleaner walks that cannot delete TAINT marks, plus repeated projection walks. Fresh GROUP run-1 projection scope: Thingsboard 72.735 GB allocation weight; OWASP 104.452 GB | Selected-cleaner frequency; redundant versus required projection share; complete internal producer-closure proof |
| 3, measure before selection | Merge pending callee inputs before delivery | May remove repeated receiver admission; use the exact smaller run-1 input projection | Pending-key multiplicity and input-admission execution share; event payloads are not a dominant measured owner |
| 4, defer | Select the end-action-free rule view through Spring before binding | Avoid small ignored list construction; measured global end-list scope is 102.7 MB, mostly empty lists | Engine-time effect is unmeasured; nonempty end-list retained union is only 480 bytes |

Recommend the first two entries for the next implementation. Keep input
batching as an investigation until pending-key multiplicity is measured.
Defer the Spring view change: it does not address a large measured owner.
The four entries concern run-1. Backfill of newly permitted backward
queries is a separate **run-2** proposal in section 5. It cannot improve
run-1.

Retained bytes and allocation work have different units. The GROUP
projection scope is not a retained-heap saving. Neither profile
percentages nor duplicate initial counts prove a 2x
engine speedup. The proposals remove whole computations. They do not
make each existing operation slightly faster.

## 1. Merge temporary static supplies before retaining them

### Current versus new

Current: each computed static result appends RegionGroups objects to
`StaticBatch.supplies`. The method retains every item until its fact
queue is empty. Only then does the global service remove repeated region
and mark memberships.

New: complete the existing producer binding first. Then keep one temporary
mark-set row for each equal complete static output region. Union marks
inside that row. Submit the union at the same method-queue barrier and
replace the temporary batch. Do not keep a completed-supply archive.

```java
void copyTwice() {
    C.out = C.in;
    C.out = C.in;
}
```

```text
Actual field supply: C.in / * / G
Each reached copy derives: C.in / * / G -> C.out / [any] / G, DEMAND
Existing FLOW-writer admission and its deduplication keys stay unchanged.

Before the barrier:
  supplies = [(C.out/[any], {G}), (C.out/[any], {G})]
After the proposed admission:
  supplies = { C.out/[any] -> {G} }
At commit: one equal semantic group supply; no S-to-S summary is retained.
```

The snippet has two distinct write points. It does not assume the analyzer
unrolls a loop once per runtime iteration. Real repeated results can also
come from several reached bindings and transfer pieces.

```kotlin
interface SupplyDelta {
    fun offer(region: Pattern, atoms: MarkSet)
    fun isEmpty(): Boolean
    fun forEach(visit: (Pattern, MarkSet) -> Unit)
}

// Use only for the selected run-1 grammar proved below.
class MergedSupplies : SupplyDelta {
    private val pending = LinkedHashMap<Pattern, MarkSet>()

    override fun offer(region: Pattern, atoms: MarkSet) {
        val old = pending[region] ?: MarkSet.EMPTY
        val fresh = atoms - old
        if (!fresh.isEmpty) pending[region] = old + fresh
    }

    override fun isEmpty() = pending.isEmpty()
    override fun forEach(visit: (Pattern, MarkSet) -> Unit) {
        pending.forEach { (region, atoms) -> visit(region, atoms) }
    }
}

// Compatibility path for the broader raw service API.
class OrderedFreshSupplies : SupplyDelta {
    private val seen = HashMap<Pattern, MarkSet>()
    private val packets = ArrayList<RegionGroups>()

    override fun offer(region: Pattern, atoms: MarkSet) {
        val old = seen[region] ?: MarkSet.EMPTY
        val fresh = atoms - old
        if (fresh.isEmpty) return
        seen[region] = old + fresh
        packets += RegionGroups(region, fresh)
    }

    override fun isEmpty() = packets.isEmpty()
    override fun forEach(visit: (Pattern, MarkSet) -> Unit) {
        packets.forEach { visit(it.region, it.groups) }
    }
}

class StaticBatch(profile: SupplyProfile) {
    // Choose once for the WHOLE batch. Do not mix per-entry policies.
    val supplies: SupplyDelta = when (profile) {
        SELECTED_RUN1 -> MergedSupplies()
        GENERAL_RAW_API -> OrderedFreshSupplies()
    }

    fun supply(actual: RegionGroups) {
        // Existing binding and field-limit logic must have run already.
        val region = Run1StaticService.region(actual.region)
        supplies.offer(region, actual.groups)
    }
}

// Existing owning-actor barrier and counted-work protocol:
val submitted = staticBatch
val effects = staticService.commit(submitted)
staticBatch = StaticBatch(supplyProfile)
secureAllEffectDeliveries(effects)
```

The constructor above is pseudocode. The owner must carry the same
profile into each replacement batch. Keep the other batch lanes and
their existing commit order. Adapt the current list consumer and empty
test to `SupplyDelta.forEach` and `SupplyDelta.isEmpty`. Preserve the
existing cancellation checkpoints while visiting leaves and committing.

Use this admission at all three current producer seams in
`Run1PointResume`: a composed known-static-input result, a ZERO-premise
result, and `publishMatched` after a complete conditional binding. Each
seam still computes its actual bounded result before calling
`staticBatch.supply`. Call summary application still creates no local
write point. Writer registration and its deduplication keys stay unchanged.

The key includes the full base, path, tail and field exclusion. It clears
only the concrete output mark, which moves into that row's MarkSet. Do
not merge different fields, different tails, or class markers with field
regions. Do not add a premise/result relation table. Only already computed
output supplies enter this temporary delta.

The implementation should avoid constructing a RegionGroups wrapper for
a rejected duplicate where the existing AP interface permits that. Such
an allocation change is secondary. The main change removes retained list
entries and their later service-admission calls.

### Evidence and semantic boundary

In the FULL run-1 OOM heap, StaticBatch owners retain **521,812,144 bytes**
and 2,841,377 RegionGroups objects. The two largest actor batches retain
168,545,936 and 136,931,120 bytes. Almost all of each is its supply list.
This is direct temporary-payload evidence. It is not a GROUP run-1 peak
measurement. The two largest batches have 974,206 and 794,986 entries but only 13
complete region keys each. The query preserves base, full path, tail and
exclusion, and source inspection proves coverage of its path-list filter.
Each region row must still keep every distinct selected atom. Complete
per-region atom membership and other batches remain unmeasured.

A same-heap bound already proves substantial duplication. The atom interner
keeps all assigned keys; the complete census has 934 Full keys and one
ControlZero. All supply records are singleton slices. Thus each 13-region
batch has at most 12,155 distinct region/atom memberships. At least
1,744,882 records in these two batches are duplicates. Out of all
2,841,377 pending supply records, at most 1,096,495 would remain after
removing just those duplicates: at least a 2.59x reduction for this work
population. More than 98% of each large batch is duplicate membership.
The atom sets must still preserve every distinct mark. This is not a
whole-engine speedup or a measured GROUP peak-memory result.

For a fixed complete region P, replacing offers `(P,M1), (P,M2)` with
`(P,M1 union M2)` adds the same writes and concrete memberships. Existing
field replies use the canonical named-field region, so group admission
commutes. The global `req/reach/processed` split and writer notifications
must receive the same memberships. Keep ordinary actual-input binding,
write effects and erased-row producer effects before this admission.

Exact class-marker replies need a separate check. The selected run-1
profile has empty field exclusions and exact class markers. General raw
service inputs can describe broader class supplies; first matching
geometry can affect their callbacks. For those inputs, use an order-
preserving fresh-only fallback instead of moving later atoms into an
earlier, different region. Never silently erase exclusions to make keys
match. The detailed source proof must pin this domain before code changes.

Work stays counted through commit and secured sends. A duplicate must not
cancel the Work token for the first pending membership. Preserve the
existing cancellation checks, locking and temporary-batch replacement.
Do not add arbitrary batch-size limits or change the barrier policy.

This can remove many operations if equal supplies repeat within a batch.
The 2.59x pending-work bound does not prove a 2x whole-engine saving.
Measure submitted offers, temporary fresh memberships, committed calls,
allocation and run-1 engine time in the next selected experiment.

## 2. Skip no-op GROUP cleaning and repeated normalization

### Current versus new

Current: `ApManager.runtimeFact` can return the same canonical GROUP fact,
but it first calls `mapCanonicalLeaves`. This visits the TAINT DAG and
allocates scratch maps, frames and child lists. The scalar and leaf fast
paths do not remove this walk. Several internal callbacks repeat it even
though their immediate producer has already projected the same payload.

New: keep the required projection at each input or producer boundary
without a proved canonical-payload postcondition. Reuse
the proved postcondition at its internal consumers. Keep header handling,
field cuts, covers, requests, original-based DEMAND rules and store
admission. Remove repeated checks of an unchanged payload. Do not add a
cache, support label or standing set of old roots.

```java
void copy(Box x) {
    Box y = x;
    use(y);
}
```

```text
Original: arg0/*/G
Bounded output: {y.left/[any]/G, y.right/./G}, DEMAND, E={}

Current ordinary transfer path:
  AP run1Policy             project payload
  generic engine collect    project same payload
  emitOperationResult       project same payload
  cut's generic collect     project same payload
  emitAfter                 project same payload

Proposed path after the producer-closure proof:
  required AP boundary      project payload
  internal consumers        reuse canonical payload; keep header/field policy
  MethodEdgeStore           use existing admission
```

The five-pass example is a single-result nonstatic transfer with no
payload-changing result filter or overflow split. It is a current source
path, not a measured average for all calls or cleaners.
For `k` distinct DAG nodes it can visit `5k` nodes instead of `k`. The
proposal removes whole repeated walks. It does not claim a fivefold
whole-engine improvement.

The fresh Thingsboard GROUP run-1 profile attributes 4.17% of Java CPU
samples and 12.30% of allocation weight, 72.735 GB, to canonical GROUP
projection. OWASP GROUP run-1 attributes 16.32% and 27.97%, 104.452 GB.
These are inclusive scopes. They overlap call and cleaner processing and
include required projections. They are not measured avoidable totals.
Thingsboard's latest reached iteration is run-2 OOM, with run-1 COMPLETE
and accepted. OWASP's is run-4 TIMEOUT, with run-3 COMPLETE and accepted.

### Local change: skip a selected GROUP cleaner's TAINT walk

For a selected-mark cleaner, `TaintClean.gone` returns EMPTY in GROUP
mode. It cannot remove a TAINT mark. The current code still visits the
cleaner spine and descendants and builds Results. Skip that body in
run-1, but keep its existing AP output policy:

```kotlin
// In cleanFacts, after the unchanged base-mismatch check.
// out is still the run1Policy wrapper installed by ApOps.clean.
if (mode.run1 && mode.markMode == GROUP &&
    cl.mark != null && c is TaintTree) {
    out.result(c)
    return
}
```

Keep FLOW cleaning and its requests, RemoveAllMarks, FULL cleaning and
non-run-1 paths. The wrapper still projects new or foreign inputs,
normalizes headers and performs the current cover. Do not bypass the
wrapper or return directly to the engine. Do not skip a whole
`CleanStep.Field`: its read and write phases remain required.

```text
Input:  arg0/*/G -> x.left/[any]/G, DEMAND
Action: selected-mark cleaner at x.left, in run-1 GROUP
Before: visit the TAINT trie; remove no marks; normalize and emit it
After:  normalize and emit it; no cleaner trie visit
```

There is no TAINT mark-request callback in the skipped body. The current
cleaner emits one result with the same denotation. An empty TAINT tree is
constructor-invalid; an empty root leaf with nonempty children is valid
and must still pass through the output policy. Include that control and
MUST/E cover, foreign inputs and request callback controls. The OWASP
`cleanChain` profile is a larger scope: 33.16% of Java CPU and 47.76% of
allocation weight, 178.360 GB. It overlaps projection and includes field
read/write stages. It is not an estimate of removable cleaner work.

### Immediate callback change: reuse the AP policy contract

`collectAfterRun1Policy` already exists for unary source transfer. Regular
transfers and cleaner phases still use the generic collector. Both
`ops.applyEdge` and `ops.clean` apply the AP policy before their result
callback. The existing collector's source comment names `applyEdge`
only. Extend that documented contract deliberately after proving the
cleaner wrapper, then select it at each proved call site:

```kotlin
// Current cleaner callback: AP projection, then another engine projection.
ops.clean(input, premise, cleaner, mode, core.collect(at, sink))

// Proposed immediate callback: reuse the AP projection.
ops.clean(input, premise, cleaner, mode,
    core.collectAfterRun1Policy(at, sink))
```

Keep the exact saved `OperationInput` and `needGroups` routing. The overload
without a saved point must retain `raiseMark`. Keep original-based layer
adjustment and its cover. This first change removes one repeated walk at
the chosen callback; it does not establish the full five-to-one change.

### Larger change: prove closure through the internal pipeline

Use a logical contract, not a new runtime fact type or support field:

```text
CanonicalPayload(ap, root):
  every atom is a fixed point of ap.marks.runtimeAtom(atom, GROUP);
  every leaf is the receiving AP manager's canonical representative;
  every reused child has the same property.
```

Prove this contract through the chosen primitive transfer, filter,
field-limit, cover, subset, union and unchanged-child paths. Then use an
internal header-only equivalent of `runtimeFact` at their consumers.
Preserve the current `root.hasAny`, ANY_TAINT tail, exclusion normalization
and header interning before the unchanged `forceRun1Layer` predicate.
`withLayer(DEMAND)` alone is not that general replacement. Keep FLOW
normalization initially; it does not walk a TAINT DAG.

Do not replace all generic collectors or cuts. For example, `seedFact`
calls `targetTree` without a mode and uses its generic cut for the first
projection. `applyMatchedCombination` does not install this AP wrapper.
Cached FULL actions, mixed payloads and foreign-manager
inputs also need a defensive boundary. GROUP mode, DEMAND layer, an
interned root or empty exclusions alone do not prove the contract.

Required controls compare decoded facts, request callbacks, contributing
origins and actual summary/handoff pairs. Include shared DAG children,
foreign numeric IDs, FULL targets, GEN seeds, cleaners, type filters,
ANY/MUST/EXACT tails, field cuts and later runs. Object-identity tests
alone cannot prove that a walk was removed. No source/guard admission or
field semantics may change.

The fresh source review and 21 runtime source hashes are in
`validation/current-group-projection-boundary-analysis.md` and its JSON
receipt. Its 18 checks are source controls, not executable replacement
tests or the required constructive closure proof. No change in this
section is part of the current measured binary.

## 3. Deferred: select the end-action-free rule view through Spring

### Current versus new

Current: the production stack adds `SpringRuleProvider` over the exit-rule
and configuration providers. Spring does not implement the explicit
bidirectional-view factory. View selection stops there, including before
prescan. The lower provider resolves normal sink rules with their ignored
end actions. The engine removes those actions after delegate lookup.
The lower strong configuration caches can retain the discarded work.

New: the known Spring wrapper implements that factory and wraps the
selected base with the same Spring context. Select it before prescan.
Keep all existing Spring overrides. Sink conditions and active roles stay
in place. The lower selected cache does not construct end actions that
the selected analyzer policy already treats as empty.

```kotlin
class SpringRuleProvider(
    private val base: TaintRulesProvider,
    private val springCtx: SpringWebProjectContext,
) : TaintRulesProvider by base, BidirectionalRulesProviderFactory {
    override fun bidirectionalRulesView(): TaintRulesProvider =
        SpringRuleProvider(base.forBidirectionalCore(), springCtx)

    // Keep all existing role overrides unchanged.
}
```

```java
// Code under analysis: the controller annotation still selects the sink.
@RestController
class PageController {
    @GetMapping("/page")
    String page(String input) { return input; }
}
```

```text
Selected rule:
  exit condition CM(result, T)
  ignored end actions [AssignMark(E1, result), ...]

Before:
  bind condition and end-action positions
  keep the full resolved rule in a normal per-method cache
  copy it later with an empty end-action list

After:
  bind the same condition through the early selected view
  keep the selected rule with no end actions
  perform the same controller result-field expansion and mark check
```

The FULL run-1 heap strongly retains 1,293,254,376 bytes below Spring,
almost all through its exit-provider delegate. That is an upper owner
scope, **not** a predicted saving. Conditions, rules, metadata and other
configuration state also contribute. The parsed heap has 3,737,697 bound
method-exit sink objects; those are rule objects, not emitted findings.
The completed end-list census supplies the narrower scope below.

The closed FULL run-1 partition census now finds only four call-rule
owners with nonempty end lists. Another 483,359 call-rule owners refer to
allocated empty lists; one owner uses the shared empty list. All 57,796 entry
rules and 3,737,697 exit rules have empty lists. These are owner counts;
several owners can share one list. The complete retained union of allocated
end lists is 102,679,536 bytes: 102,679,056 bytes for empty lists and 480
bytes for nonempty lists and their owned data. This is about 0.9% of the
FULL run-1 live heap. Shared/interner-owned marks are outside that retained
scope. This result does not support removing most of the Spring owner as
ignored action data. Defer this change instead of selecting it as a major
memory optimization.

The measured receipt is
`validation/full1-end-action-measured/end-action-findings.json`. All ten
byte queries passed. The two empty nonempty-list partitions use a
separate complete-complement control; the original generic validation
failure is preserved. The older fourteen failed supply-census sections
remain invalid and supply no counts.

The resolver already uses an empty action list when it creates sink
condition-specialization contexts. This change therefore does not reduce
those contexts or the required bound sink count. It does not remove the
3.7 million exit-sink objects merely by selecting the early view. The
resolved-mark interner also keeps mark keys strongly; removing an action's
mark reference does not necessarily release that mark. Count ignored
action objects, their position data and allocated empty list wrappers
separately from required rule and condition data.

A selected leaf view shares its `TaintConfiguration` with the original
provider. Selecting it after prescan does not release already populated
normal caches. The change must reach the existing early selection in
`JIRTaintAnalyzer`, before any prescan sink binding. Do not clear another
consumer's caches or replace the Spring wrapper with its base.

### Behavior boundary and required controls

This restores the existing empty-end-action contract. It does not change
exit placement, the ZERO-premise policy, controller annotation tests,
source/cleaner actions, GROUP selection or full mark declarations. Keep
the late defensive strip for unknown providers.

For valid ignored actions, the resulting conditions and active actions
must match the current selected view. An ignored action that currently
fails during binding will no longer cause that failure. This is an
explicit failure/admission change for ignored input. It is required by
the selected empty-end-action policy. It must not be hidden under a claim
of universal behavior equality.

Use a fresh, raw production provider stack in the future controls. The
existing wrapper metadata test preselects its leaf and never queries a
sink, so it does not cover this path. Check all three sink roles,
ordinary/controller methods, both `allRelevant` settings, annotation and
result-field behavior, generated exclusions, repository/concat behavior,
rule ordinals and unchanged raw mark declarations. A separately queried
legacy view must retain its full end actions. Do not add a Spring entry
or dispatcher exit rewrite.

Source evidence and 21 file hashes are in
`validation/spring-bidi-rule-view-analysis.json` and `.md` under the
measurement root. Its 48 checks validate source ranges, hashes and
composition claims; they are not JVM execution tests.

## 4. Measure before selection: merge pending callee inputs

The proposed first implementation applies to run-1 only. The later-run
key below records a future extension; it is not part of this iteration's
proposed change or its expected run-1 benefit.

### Current versus new

Current: each call offers a `LinkIn`, even when subscription admission
finds no fresh input. A remote offer gets its own unlimited-channel item,
priority-queue entry and work reservation. The callee deduplicates its
initials after it receives and projects the offer.

New: caller subscriptions still retain their exact bindings. Input
delivery uses one counted token for each pending callee-input key. Merge
its immutable payload until the callee takes the token. Remove the
pending key before running the handler. Future offers can create another
token. There is no completed-input archive.

```java
Object pass(Object p) { return p; }

// Many reached callers use the same complete callee key.
A: x = pass(sourceT());
B: y = pass(sourceT());
// ... 1,000 callers offer arg0/EXACT/T.
```

```text
Before:
  1,000 exact caller subscriptions
  1,000 callee-input offers -> 1,000 receiver admissions
  usually one fresh arg0/EXACT/T initial

After, while the offers overlap in the pending mailbox:
  1,000 exact caller subscriptions
  one pending input token -> one receiver admission
  the same arg0/EXACT/T initial
```

This example removes 999 receiver operations. It does not prove that the
real workload has 1,000 equal pending keys. If the offers span ten drain
windows, up to ten input tokens remain necessary.

### Run-1 input projection

The run-1 method emitter already forgets supplier geometry when it emits
nonstatic initials. Preserve exactly what that emitter uses:

```kotlin
data class Run1InputKey(val target: MethodKey, val base: AccessPathBase)

data class Run1InputBatch(
    val present: Boolean,
    val exactRootMarks: MarkSet,
    val starMarks: MarkSet,
    val sawDemandInput: Boolean,
)

// First apply the existing validation to EACH original offer.
// Use the selected runtime atoms; this works in GROUP and FULL mode.
fun project(input: Facts): Run1InputBatch = when (input) {
    is FlowTree -> Run1InputBatch(true, EMPTY, EMPTY, input.layer == DEMAND)
    is TaintTree -> Run1InputBatch(
        present = true,
        exactRootMarks = input.root.leaf.exact,
        starMarks = input.root.leaf.any + marksInAllChildren(input.root),
        sawDemandInput = input.layer == DEMAND,
    )
    is Reach -> existingZeroPresenceProjection(input)
}
```

`marksInAllChildren` unions exact and any marks below the root. Do not
compute it by subtracting root-exact marks from an all-marks set: one mark
can occur both at the root and below it. Both answers must then remain.

```text
Supplier p/EXACT/G        -> exactRootMarks={G} -> initial p/EXACT/G
Supplier p.f/EXACT/G      -> starMarks={G}      -> initial p/STAR/G
Supplier p/ANY/G          -> starMarks={G}      -> initial p/STAR/G
Both root and descendant -> both sets contain G; emit both initials
```

At consumption, use current authoritative requests to select concrete
answers from the two sets. Presence emits the same abstract/zero initial
as the current policy. Preserve the method's demand-arrival presence bit.
Keep the existing static-offer path for the first implementation; static
read/write/service handling is outside this projection change.

Do not move caller-specific facts into this mailbox. Existing caller input stores
must keep their full paths, exclusions, layers, call stages and originals.
They still join late requests and match caller summary applications. If
a request arrives after a batch was consumed, the existing registered
request replay joins it with those original N2 inputs. Do not add a second
standing supplier store in the method emitter.

### Later-run input key (deferred)

Later queries need their actual field geometry and headers. Their key is
the complete `MethodKey` plus exact `GroupKey`:

```kotlin
data class InputKey(val target: MethodKey, val header: GroupKey)
val pendingInputs: MutableMap<InputKey, Facts>

// Same key: union positions and their exact/any mark sets.
// CallerRef remains in the exact caller subscription, not this token.
```

```text
left/EXACT/T + right/EXACT/U + left/EXACT/U
    -> left:{T,U}, right:{U}
    -> no right/T leaf
```

Equal headers include base, kind, layer, tail, field exclusions and FLOW
mark exclusions. Do not weaken those headers while merging pending data.
For example, combining `E={f}, mx={}` with `E={}, mx={T}` by intersecting
headers can invent `p.f/T`. Exact `GroupKey` separation prevents that.
ZERO stays a distinct input kind. Full ND permission still needs every
member. Run-2 static completion still uses an actual same-field FULL
input and the same full mark.

### Work ownership

```kotlin
fun offer(key: InputKey, value: Facts): Boolean = admissionTransition {
    rejectIfRunEndedOrDeadlineExpired()
    pendingInputs[key]?.let {
        pendingInputs[key] = exactHeaderUnion(it, value)
        return@admissionTransition true
    }
    reserveWorkBeforeVisibility()
    pendingInputs[key] = value
    enqueueCountedToken(key) // rollback value and reservation on rejection
}

fun handle(key: InputKey) {
    val value = admissionTransition { pendingInputs.remove(key)!! }
    owningActor.admit(value)
    // Release the old reservation only after all sends/enqueues finish.
}
```

This is ownership pseudocode, not a decision to perform a large trie union
under the global admission lock. Choose one lock order and keep remote
producers away from actor stores. A racing offer after detach reserves a
new token before the old handler releases its reservation. This prevents
early COMPLETE and lost wakeups. Use the current cancellation and runner
join contract.

The new behavior is batching and scheduling. Semantic closure must stay
equal. Time-limited prefixes and batch-dependent diagnostic counts can
change. Initial shapes, layers, field writes, rule eligibility, source
hits and sink witnesses must not change.

## 5. Backfill only a newly permitted backward query

The first Stirling-PDF GROUP command completes run-1 in 25.349 s, then
reaches run-2 OOM. Its latest COMPLETE and accepted forward iteration is
run-1. In that failed run-2 prefix, `addLink` appears in 38.75% of Java CPU
samples and 32.32% of allocation weight. Raw-summary iteration accounts
for 16.02% and 17.64%, or 398.630 GB of allocation sample weight. These
inclusive contexts overlap. They strengthen the measured scope of this
later-run proposal, without establishing that all link or publication
work can be removed. This proposal still cannot improve run-1.

### Current versus new

Current: any fresh restricted query causes an enumeration of every raw
summary in the method. Already permitted rows are offered again and are
eventually rejected by publication storage.

New: backward summaries are unary. Visit only raw rows with the exact
newly admitted nonzero initial. Backward ZERO already has unconditional
publication permission. Leave the later-forward branch in place for
this narrow change.

```kotlin
val starts = queries.admit(added, guides) // installs the whole batch first
if (!forward) {
    for (initial in starts) {
        if (initial.isZero) continue
        for (raw in summaries.borrow(initial)) {
            queuePublications(initial, raw)
        }
    }
} else {
    existingForwardPermissionBackfill()
}
starts.forEach(::addInitial)
```

No new cache, fact archive or permission index is needed.

```java
Object id(Object p) { return p; }
// Backward queries for return/T1, then return/T2, ... return/T65.
```

```text
After Ti completes: summary return/Ti -> arg0/Ti.

Before, if queries arrive after preceding responses:
  T2 reoffers T1; T3 reoffers T1,T2; ...
  65 queries can add 2,080 old-response offers.

After:
  each fresh query looks up only its own initial.
  the ordinary new summary path still publishes its fresh response.
```

The source-level production invariant is stronger: each executable
nonzero restricted initial is admitted before it starts. Current run-2
therefore normally has no old raw response for a fresh initial. Keep the
keyed lookup because the supported internal late-guide test deliberately
starts an initial before permission; its old response still needs replay.
Deleting all backfill would break that contract.

Correctness follows from exact permission transitions. Membership of an
old unary nonzero initial can change only if that same canonical key is
in `starts`. ZERO permission did not change. All unrelated permitted rows
were already offered when produced. The existing raw-delta path remains
responsible for later growth. The lookup preserves the direct-initial
late-permission case without restarting it or manufacturing an answer.

## Proof and measurement gates

For temporary static supplies, model complete region keys and selected
atom sets. Prove that batch union preserves the service state and each
owner's reply memberships at fair closure. Include late reads, late
writers, requests before and after supplies, and both mark modes. Keep
producer validation before admission. Check the selected class-marker
domain separately; prove the ordered fallback for broader raw inputs.
Prove that work remains counted through commit, pending replies and
secured sends. Use the current producer-effect, erasure and run-2 query
completion controls as regression gates. No summary or demand edge may
disappear because its supply record was already admitted.

For GROUP work, prove selected TAINT cleaning is an identity before the
unchanged AP output policy. Prove that no request or partial-demand
callback is lost. Then prove receiving-manager projection idempotence
and each selected immediate callback contract. The larger internal change
needs a constructive producer-closure proof through every selected
filter, cut, cover and store operation. Keep generic paths for unproved
producers. Check decoded facts and callbacks for FULL/foreign inputs,
GEN seeds, shared DAGs and all contributing origins. Measure omitted
walks and their allocation; do not attribute the whole cleaner or
projection profile scope to removable work.

For the early rule view, use the raw production wrapper stack. Prove
role-preserving delegation for all active conditions and actions. Check
that primitive metadata, mark declarations and rule identities do not
change. Test normal and selected views independently, including Spring
controller rules and all three sink roles. State the ignored-action
binding-failure change explicitly. A cache-byte reduction alone does not
prove the rule view is correct.

For input batching, construct an executable decode from a pending batch
to the old initial set. Prove equality of the emitted initials for both
mark modes, including exact-root/descendant overlap. Prove request-before-
input, input-before-request and commit races against the existing N2
replay path. Prove counted-token admission, detach, cancellation and join
invariants. Include mixed headers, ZERO, ND members, source/rule observers
and same-unit versus remote delivery.

For backward backfill, prove the unary backward invariant at every
production summary producer. Preserve the late-guide regression test.
Check unrelated permitted initials, duplicate admissions, backward ZERO,
native records, source hits and both query-completion starts in one batch.
Follow the constructive Lean and TDD order in the proof guide before
changing the runtime.

Measure pending event types and retained owners before selecting the
mailbox key. Measure offers, merged tokens, drain windows, receiver
admissions and fresh initials with identical scopes. For backfill, count
raw keys/rows visited, permission transitions and publication input rows
versus fresh deltas. A credible 2x workload claim needs those counts and
replacement costs, not a faster generic collection or a profile sum.

The broader idea of sharing method transfers across scalar original
marks remains a later design candidate. It needs a measured original-
geometry multiplicity and an exact origin/output mark relation. A mark
set on each conclusion alone does not preserve that relation. It is not
part of the proposals above.

Evidence notes are under
`/drive-testcomp/opentaint-go-rules/run1-static-summary-erasure-20261022/validation/`:
`queue-investigation/`, `restricted-query-backfill-analysis.md`,
`run1-avoidable-work-source-analysis.md` and
`source-work-investigation-receipt.json`. Final heap results are in
`representative-heap-owner-index.json` and
`full1-end-action-measured/end-action-findings.json`. They support the
recommended first two proposals and the deferral of the Spring change.
They do not establish a whole-engine 2x saving.
