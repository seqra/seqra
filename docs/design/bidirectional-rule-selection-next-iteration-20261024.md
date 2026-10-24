# Avoid repeated ordinary FULL transport in run-1

Date: 24 October 2026. Status: **unselected next-iteration proposal**.
Measured runtime: `40e20d16f03ab046df75a53a489bb331b065a9c1`.
All fixed-project commands, profile scopes and six required OOM owner
scopes are closed. This proposal uses the corrected candidate measurements.
It changes no analyzer code. Read the
[performance report](../reports/bidirectional-rule-selection-performance-20261024.md).

The target is a complete run-1 engine in 60 seconds. Prescan and report
barriers are outside that target. Prior proposals are historical inputs.

## 1. What remains after rule reduction

| Project | Corrected run-1 engine | Latest reached / COMPLETE / accepted |
| --- | ---: | --- |
| Thingsboard | 197.026 s, OOM including 29.620 s heap capture | run-1 / none / none |
| Conductor | 13.640 s, COMPLETE | run-2 OOM / run-1 / run-1 |
| PDF | 25.269 s, COMPLETE | run-2 OOM / run-1 / run-1 |
| OWASP | 84.465 s, COMPLETE | run-11 COMPLETE / run-11 / run-11 |

The current Thingsboard heap has 1,687 global initial descriptors, but
6.7 million ConclusionGroups and 12.2 million TaintTrees across owners.
A small initial vocabulary does not imply few retained method/point/origin
relations. Current processing repeats the same geometry under many scalar
initial marks. Final TaintTree packing cannot cross those initial keys.

The exact MethodEdgeStore retained union is 2.558 GB. CalleeSubscriptions
retain 0.920 GB; the broader summaries/subscriptions union is 2.405 GB.
These overlap and are not an additive saving budget. Inside MethodEdgeStore,
disjoint shallow HashMap, ArrayList and Object-array classes use 1.131 GB
(44.21% of that one retained set). Removing repeated whole relation rows
can avoid their containers and payloads together. Small-container tuning
alone is not the selected proposal.

The current static service/point union is 0.485 GB, versus 0.877 GB at the
reference OOM. These unequal-prefix snapshots are consistent with request
reduction; they do not isolate a retained-byte saving. Only 1,126 global writer rows remain on the
reference. Writer factoring is not the large current lever. In the current
last 30 seconds before heap capture, GC pauses occupy 24.370 seconds. The
MemoryManager policy is inherited. Retained-state reduction must come first.

### Complete current input-axis census

Method stores have 875,915 premise rows: 793,890 concrete (90.64%),
81,244 FLOW and 781 ZERO. Concrete rows use 21,558,635 of 25,251,733
statement slots (85.37%). Within each method, erasing only the initial mark
gives 48,254 complete input geometries: 16.45 marks per geometry on average.
There are 33,851 width-18 families and 4,987 width-28 families. In all,
786,885 concrete rows belong to families of width at least two.

Concrete origins are 268,373 ARG, 332,820 STATIC and 192,697 THIS. This is
not only static handling. Static origins can appear on ordinary local
facts after a static read; they are different from propagating facts with
static bases. ZERO is not the dominant stored-input family.

The 745,636 potentially repeated input rows are only an input-header upper
bound. It does not establish identical output relations or saved operations.
The complete key retains manager, base, path, tail and field exclusion.
Only the mark axis is removed for this census.

### Complete output check within a bounded stress cohort

The current probe selects eight high-width stores with at most 64 statements:
1,735 premise arrays, 62,165 possible slots and 9,996 occupied cells. It
fully decodes 15,631 TAINT headers and 5,417 trie nodes, with zero unknowns.

Of 9,584 concrete cells, 9,548 have exact whole-image T-to-T relations.
Those images form 420 cohorts after aligned input/output mark substitution,
with up to 28 marks per cohort. The other 36 concrete cells have nonidentity
marks; 412 cells are FLOW. The decoder retains every output base, field,
leaf exact/any kind, layer, tail, exclusion, full mark and original premise.

This proves a local snapshot opportunity of 22.73 times fewer image rows,
with all member marks retained. It does not prove global eligibility, bytes
saved, eliminated historical operations or an engine-time gain. The cohort
is deliberately biased toward high-width stores. Equal current images do
not certify that a future type filter, guard or primitive rule is mark-blind.

A separate historical reference probe tests a different idea: sharing an
exact current caller Facts image after erasing only its caller premise.
It finds 1,867 duplicate component images among 20,261 entries in four large
directories (9.21%, maximum width five). Pointer-unequal structural matches
are unknown. This is weaker evidence for generic exact-image join factoring.
It keeps the current full mark, so it does not measure aligned mark-relation
batching in caller storage and cannot bound that adapter's opportunity.

## 2. Ranked choices and evidence gate

| Rank | Proposal | Current → new | Expected impact and limit |
| --- | --- | --- | --- |
| 1 | Process exact FULL mark relations in batches | Repeat the same AP transport under scalar initial marks → share the transport with an exact input/output mark relation | Can remove whole transfer, store and join operations when many marks use the same geometry. The eligible weighted fraction must be measured. |
| 2 | Keep execution facts only at required boundaries of plain linear regions | Store changed/touched intermediate values and enqueue per-statement work → apply the same plain steps locally and store boundary results | Can remove actual intermediate admissions and queue operations. Unchanged emission already bypasses MethodEdgeStore. Required boundary coverage and the eligible retained fraction are not measured yet. |

These are algorithmic work-removal proposals. Neither has a measured
two-times whole-engine result yet. Do not infer that result from the global
mark-family sizes or from 97 million form accesses. The second choice also
affects some state targeted by the first choice, so their byte savings cannot
be added. Current input weights and the bounded output probe support rank 1. A global
output/operation estimate remains a separate gate. Prioritize rank 1; rank 2
needs its own eligible-point measurement before selection.

Do not select a MemoryManager rewrite, IR storage change, new mark-eligibility
filter, or another static writer index from these measurements. The slow
Thingsboard tail is inherited full-GC pressure. Reduce the admitted state
which causes that pressure.

## 3. Exact FULL mark relations

### Current and new behavior

`TaintTree` can already pack several conclusion marks. The initial premise
still has one concrete mark. That separate initial key prevents two equal
transports from sharing one MethodEdgeStore row.

```java
Object pass(Object p) {
    Object x = p;
    Object y = x;
    return y;
}
```

At one program point, two current edges are:

```text
I(p, *, T) -> x/[any]/T, DEMAND
I(p, *, U) -> x/[any]/U, DEMAND
```

The proposed value is one exact relation:

```text
input geometry:  I(p, *)
output geometry: x/[any], DEMAND
input marks:     {T,U}
output mark:     same as that row's input mark

decode = { I(p,*,T) -> x/[any]/T,
           I(p,*,U) -> x/[any]/U }
```

It does not contain `I(p,*,T) -> x/U` or `I(p,*,U) -> x/T`. A mark mask
must not become an independent input/output Cartesian product. FULL names,
owners and primitive properties stay exact. These masks are storage values;
they are not new taint marks or GROUP wildcards.

### Storage and propagation

Start with the strict same-mark case. All structural fields must agree.
ZERO, FLOW, cross-mark outputs and unsupported shapes keep their existing
path. Do not combine different initial bases or fields merely because they
have the same output.

```kotlin
data class InitialGeometry(
    val base: Base, val path: Path, val tail: Tail, val exclusion: FieldExclusion,
)

data class SameMarkTransport(
    val initial: InitialGeometry,
    val output: UnmarkedConclusionImage, // every base, leaf, layer, E and tail
    val marks: FullMarkSet,
)

fun decode(row: SameMarkTransport): Sequence<OrdinaryEdge> =
    row.marks.asSequence().flatMap { t ->
        row.output.withMark(t).map { c ->
            OrdinaryEdge(ap.initial(canonicalPattern(row.initial, t)), c)
        }
    }

fun admit(at: ProcessingAddress, offered: SameMarkTransport) {
    val family = store[at, offered.initial]
    var newMarks = offered.marks
    for (old in family.snapshot()) {
        val common = old.marks intersect offered.marks
        if (common.isEmpty()) continue
        newMarks -= common
        val (joined, delta) = addWithExistingStoreLaws(old.output, offered.output)
        if (delta.isEmpty()) continue
        family.removeMarks(old, common)
        family.mergeEqualImage(joined, common)
        pending.add(at, offered.initial, delta, common)
    }
    if (newMarks.isNotEmpty()) {
        family.mergeEqualImage(offered.output, newMarks)
        pending.add(at, offered.initial, offered.output, newMarks)
    }
}
```

The method actor owns the mutable store and pending masks. A batch carries
no borrowed form payload or mutable caller collection. It keeps the same
method/context ownership as the existing solver. Request storage already
merges mark sets; this proposal does not add another request copy.
Within one family, input masks are pairwise disjoint: each mark has one
complete current image. `ProcessingAddress` includes the node and complete
logical processing stage. Keep resume site, cleaner step/phase, caller
layer, identity candidate and pending demotion separate when they apply.
Equal node and fact values alone do not make two continuations interchangeable.
The first implementation can leave all such continuations on the old path.
`InitialAp` remains canonical and identity-equal. Decode through the owning
ApManager; do not create noncanonical initial objects or change InitialAp
equality to erase marks. Geometry/mask keys are separate immutable values.
They are scoped to the owning ApManager. Never combine numerical mark IDs
from different managers.
An update can split a cohort when only some marks gain an output. Equal
complete images can join again. Never give another member a late output
which belongs to only one mark. The pseudocode does not authorize replacing
the existing subsumption and annotation-change delta laws with plain union.
The family snapshot contains immutable row values, not views whose masks
change during the loop. Mixed scalar and packed values need one authoritative
current relation and delta admission. If a member gains a cross-mark output,
migrate its complete image to the scalar path atomically, without publishing
old values as new deltas. Keep every residual mark. The first adapter can
keep that member scalar for the rest of the run.

Apply one known plain transfer to the common geometry. Use exact mask
operations for a mark-dependent filter only after its correspondence is
proved. Expand unsupported cases into the existing scalar path.

```kotlin
fun step(row: SameMarkTransport, operation: Operation) {
    when (operation) {
        is CertifiedPlainTransfer -> {
            val out = operation.applyGeometry(row.output)
            if (!out.isEmpty()) emit(row.copy(output = out))
        }
        else -> decode(row).forEach(existingEngine::step)
    }
}
```

This is contract pseudocode. `CertifiedPlainTransfer` requires a proved
correspondence to the existing AP operation. A condition, cleaner, source,
ND join, static operation or observation is a scalar boundary until a
separate exact batch adapter is proved. Do not replace condition evaluation
with a mark-mask membership shortcut without that proof.
Preserve per-leaf exact and any/MUST occupancy, not only a global tail.
Partition primitive-sensitive filters exactly or leave them scalar. Each
plain step must retain the original cuts, run1Cover, limit handling and
forceRun1Layer at the same logical point. Current run-1 TaintTree results
are DEMAND; this proposal does not strengthen them to NORMAL.

The first storage adapter can target MethodEdgeStore. Extend summary and
caller-input storage only when they preserve the same complete relation.
For a summary lookup on T, intersect the mask with `{T}` and expose the
existing T premise. Handoff must expose every original NORMAL summary and
every marked DEMAND pattern to run-2. It must not substitute a group demand.

### Why it can save work

For k marks and one common plain transfer, the current path performs k
shape transfers and admits k scalar initial rows. The proposed path performs
one shape transfer and a mask operation. It can also replace k equal store
headers with one header. Some bitset work still grows with k; do not call
it constant-time for arbitrary masks.

For a workload fraction f with average eligible width k, the ideal number
of shape applications changes by `1 / (1 - f + f/k)`. This is a conditional
work model, not a measured engine speedup. CPU outside that lane, decoding
at boundaries and memory/GC still matter.

## 4. Plain linear regions

### Current and new behavior

```java
void copy(Object p) {
    this.left = p;
    this.right = p;
    this.extra = p;
}
```

For `I(p,*,T)`, each assignment creates a field relation. With the current
LIFO queue, a new `this.left/T` delta can walk the following writes before
the p delta creates the right/extra relations. Each walk performs form,
queue and store work. The store can pack fields after they arrive, while
the worklist still processes separate packets.

The proposed engine enters a certified plain region once for the current
batch, applies the same transfers in CFG order, and retains the required
boundary relation. It does not keep every intermediate point value merely
to feed a single known successor. Field writes keep their existing rules:
an exclusion update moves the edge to DEMAND with empty E, an overwritten
exact field is killed, and a disjoint field passes unchanged. No field
information is replaced by `[any]` as a scheduling optimization.

```text
Before: node1 admission -> queue -> step1 -> node2 admission -> queue
        -> step2 -> node3 admission -> queue -> step3 -> boundary admission

After:  region-entry admission -> step1 -> step2 -> step3
        -> boundary admission
```

The steps remain. The intermediate durable store cells and queue protocol
are avoided. A stronger AP composition can be considered later; it is not
part of this first proposal.
Current untouched emission already skips MethodEdgeStore, so those events
can contribute queue savings but not another store-cell saving.

Do not defer the existing cut to the region exit. For a valid FLOW input
`I(p,*,*) -> p.* NORMAL`, the code `p.f = clean; return p.f` moves the root
to DEMAND with empty E at the write. Keeping an intermediate `{f}` exclusion
until the region ends can instead kill the read and lose guidance. The new
region must execute the old layer/exclusion decision before the read.

### Required boundaries

Keep entries, exits, joins, loop boundaries and every possible direct resume
point. Calls, source/sink checks, predicates, cleaner continuations, static
operations, exceptional control flow and unknown callbacks are boundaries
until the adapter proves otherwise. A one-predecessor/one-successor test
alone is insufficient. The selected executable and metadata forms determine
effects; raw JVM syntax alone cannot prove a node plain.
Likewise, base-not-touched is not sufficient: FormApplier's untouched branch
still invokes reached, conjunction and source hooks.
Every SCC, including a non-returning one, needs a deduplicated boundary.
For example, fusing `while (true) { x = x; }` into an internal loop must not
turn finite solver saturation into endless region execution. Keep the
existing repeat suppression and cancellation semantics.

MethodEdgeStore's current runtime consumers use admission; `edgesAt` is
used by tests in the inspected source. Before implementation, verify all
production consumers again, including trace and debug adapters. Preserve a
constructive expansion path for any semantic observer of an internal point.
Do not silently remove a future trace/proof requirement.

## 5. Semantic scope and required proofs

The intended semantic changes are **none**. FULL stays the default. Keep
initial grammar, field limits, primitive rules, field updates/exclusions,
layers, all origins, exact all-ZERO admission, selected conditions/actions,
static-pool transitions, controllers, exit rules and later-run behavior.
Do not put transfer-support state into MethodEdgeStore or revive
GroundingSupport. Compiler labels must not alter configuration equality.

Follow the [proof guide](../../spec/proof-guide.md) before implementation.
The constructive models must provide executable decoders and witnesses:

1. Packing/decoding preserves the complete ordinary edge relation. It never
   creates a cross-mark pair or combines independent origins into an ND edge.
2. Every certified plain operation commutes with decoding, including kills,
   layer changes, exact/any tails and primitive filters.
3. Mixed packed/scalar admission and replay reach the same finite closure.
   Fair batching must not hide pending work from global termination. Masks,
   decoded members and region continuations remain in counted fact work,
   cancellation, quantum and zero-phase accounting. They must drain before
   the existing request/static commit barriers, not only before the final
   termination check. Stored current masks and standing unanswered
   subscriptions are not queued work and must not keep a completed solver
   alive.
4. At each rule/observation boundary, preserve complete binding identities,
   all contributing origins and the all-ZERO cube test. Preserve source,
   sink and report occurrence identity; callback counters are separate.
5. A linear-region step expands to an executable old step sequence. Every
   external resume and observable point has a retained boundary or an exact
   replay witness. Preserve exceptional and non-returning paths.
6. Handoff yields the same NORMAL summaries and FULL marked demand patterns.
   For every old run-2 start, construct the corresponding new start.
7. Prove reduced operation counts for explicit finite eligible workloads.
   State the scope and bitset cost. Do not claim a whole-project 2x bound.

These are proof obligations, not completed Lean claims. If a proof requires
a changed field, source, cleaner, static or exit contract, stop and specify
that major change before implementation. An approximation is not an exact
batch adapter.

## 6. Implementation and acceptance plan

The corrected measurements are sealed. Extend the bounded stress result
with a representative weighted output/operation sample before setting a
whole-project savings target. Measure the share at certified plain points
separately for rank 2. A top-store sample is not a global saving estimate.
Reject a proposal whose eligible share is small.

Then build the constructive model and RED controls. Cover same-mark versus
cross-mark pairs; full mark owners; primitive/nonprimitive filters; ZERO;
field overwrite/disjoint/prefix cases; NORMAL/DEMAND; late point resumes;
recursive calls; conditional sources; cleaners; ND origins; and marked
run-2 guidance. Compare decoded logical relations and observation multisets.

Implement one adapter at a time behind a run-1 gate. Keep the old path for
unsupported cases. Measure shape applications, queue items, retained cells,
decoded scalar boundaries and peak bytes. Do not count a wider packet as
less work if its implementation still repeats every old operation.

Finally repeat the fixed FULL/statics-ON/default-AA project campaign with
the same heap/time limits. Require unchanged finding identities and
occurrences, complete latest-run labels, under-load profiles and exact OOM
owners. The important target remains Thingsboard run-1 completion; a local
two-times kernel result alone does not meet that target.

## 7. Evidence

The current snapshot is Thingsboard **run-1 OOM**, with no COMPLETE or
accepted forward run. The input census and output probe bind that exact
heap and source epoch:

- [Complete input-axis census](../../../run1-rule-selection-equality-fix-20261024/validation/heap-candidate-thingsboard-mark-axis-maps/input-axis-summary.json).
- [Complete bounded output relation probe](../../../run1-rule-selection-equality-fix-20261024/validation/heap-candidate-thingsboard-full-cohort/full-cohort-diagonal-summary.json).
- [Exclusive MethodEdgeStore container control](../../../run1-rule-selection-equality-fix-20261024/validation/heap-candidate-thingsboard-mark-axis-owners/exclusive-method-store-class-controls.json).
- [Profile comparison and actual iteration windows](../../../run1-rule-selection-equality-fix-20261024/validation/profile-analysis/comparison-summary.json).
- [Six final OOM scopes and source-bound validation](../../../run1-rule-selection-equality-fix-20261024/validation/heap-reference-corrected-comparison.json).

The performance report keeps the failed acceptance gates: Thingsboard has
no accepted result; PDF misses two strict main return locations; OWASP
exports 9,179 entries versus CI's expected 4,112 despite matching all main
locations. These proposals target run-1 workload. They do not claim to fix
those separate trace/export contracts or the later run-2 memory failures.
