# Remove repeated Thingsboard FULL run-1 work

Date: 23 October 2026. Status: proposal; no optimization below is implemented.
FULL is the selected run-1 default. GROUP remains an explicit experiment.
Measured runtime: `b68b3a1c9389c8d1b8ab6ad513af0c586a546895`.
Latest reached in each Thingsboard FULL attempt: **run-1 OOM**.
Latest COMPLETE and accepted forward iteration: **none**.
Read the [investigation](../reports/bidirectional-thingsboard-full-run1-investigation-20261023.md)
and [proof guide](../../spec/proof-guide.md).

This document replaces the previous next-iteration selection. In
particular, removal of GROUP normalization cannot fix the default FULL
path: FULL already skips that operation.

## Scope and unchanged behavior

The proposals below remove repeated work or factor an exact relation.
They must preserve the current FULL fact/demand closure. Diagnostic packet
counts can change. No equivalence of an OOM prefix is claimed.

Keep these contracts:

- Each full mark keeps its identity. Do not replace it with a rule group.
- Keep the same field grammar, limits, tails, exclusions and DEMAND policy.
- Keep every required request and its exact FLOW image relation.
- Keep all contributing origins and the exact all-ZERO rule condition.
- Keep local micro-edge writer registration, computed supplies before
  S-to-S erasure, canonical field answers and the three-set static service.
- Keep the method-queue-empty commit barrier and counted work ownership.
- Keep run-2 query completion, source replay and later-run rules.

Do not add grounding support, an actual-input field filter, an answered
request archive, a global concrete relation directory, or a new reference
manager. Do not filter synthetic Spring registry fields merely because
they are synthetic. Those would be separate design changes.

## Recommended order

| Priority | Proposal | Measured scope and remaining gate |
| --- | --- | --- |
| 1, select | Merge temporary static supplies | Exact duplicates exceed 99.95% in the two largest batches; all temporary batches retain 521.8 MB. Retain existing actual binding and barriers. |
| 2, measure eligible blocks before selection | Bulk FULL return cleanup | 782,080 cleaner point rows; cleaner-chain scope is 12.55% Java CPU and 24.10% allocation weight. The equal-geometry pure-block share is not measured. |
| Investigate | Exact pending-image cohorts | Call stages own 3,966,089 memberships. Per-point equal-root multiplicity is unknown because the final query failed. Do not predict a saving from global sharing. |

Only the first proposal has a measured duplicate population sufficient for
selection now. Its benefit is primarily less temporary retention and
fewer service offers. It does not prove that Thingsboard will finish
run-1, meet 60 seconds, or use half the whole-engine work. The next two
entries target larger repeated action or membership populations, with
explicit measurement gates before implementation selection.

## 1. Admit static supplies before retaining duplicate packets

Current: `computedGroups` secures a computed output, then appends one
`RegionGroups` per scalar leaf. The global service rejects duplicate
memberships only after the actor's queue-empty barrier.

New: after the same binding and bounding steps, union selected FULL atoms
by the complete output region in the temporary batch. Replace the batch
at the same barrier. Keep every full mark and all other batch lanes.

```java
C.out = C.in;
C.out = C.in;
```

```text
Computed bounded supplies:
  (C.out/[any], {T}), (C.out/[any], {U}), (C.out/[any], {T})

Before: retain three packets; submit all three to the service.
After:  retain C.out/[any] -> {T,U}; submit one region row.
```

```kotlin
fun offerBoundedSupply(region: Pattern, fullAtoms: MarkSet) {
    // Actual input binding and the existing field limit already succeeded.
    val key = Run1StaticService.region(region)
    temporarySupplies.addAll(key, fullAtoms)
}

val submitted = staticBatch
val effects = staticService.commit(submitted)
staticBatch = newStaticBatchWithSameProfile()
secureAllEffectDeliveries(effects)
```

Use the full base, path, tail and field exclusion in the key. Retain the
previous proposal's order-preserving compatibility route for broad raw-API
class inputs. The selected run-1 grammar has empty field exclusions and
EXACT class markers; do not generalize its commutation proof to arbitrary
raw geometry.

The exact new census proves 1,768,503 duplicate records in two batches.
Their 1,769,192 packets have only 689 distinct region/atom pairs. This
gives a 2.648x reduction bound across all pending supply records even if
every other batch is unique. It does not prove a 2x engine gain.

The producer-effect path is only 0.955% of sampled Java CPU in FULL 1.
The immediate purpose is to prevent the 521.8 MB temporary owner from
retaining repeated payloads and to remove repeated service offers.
Do not credit this change with all upstream composition work.

An earlier producer gate is a separate extension: it must preserve the
actual binding or prove that canonical-input substitution leaves the
bounded output unchanged. Do not add a permanent relation history or
skip required effects merely because the summary row will be erased.

## 2. Compile equal-geometry FULL return cleaners as a bulk operation

Current: generated return rewriters can clean many full marks at the same
position in separate ordered steps. Each step builds headers and action
results. A static-origin FLOW request gets a different cleaner-step
resume point. A concrete input can walk cleaners for marks it lacks.

New: compile a contiguous block of plain selected-mark cleanup steps with
the same complete `(base,path,reach)` into one exact FULL mark set. Keep
all requested marks. Process the shared spatial geometry once.

Initially restrict this to nonstatic `CleanStep.Clean` return-rewriter
blocks. Stop a block at different geometry, RemoveAllMarks, a field
read/write step, source action or other effect. STATIC cleaners have an
extra Reach/MarkInput route; they remain outside this first design.

```text
Block at one nonstatic position p, EXACT:
  old steps: RemoveMark(T), RemoveMark(U), RemoveMark(V)
  new block: RemoveMarks({T,U,V}) at the same p/EXACT geometry

FLOW p/*:
  old: copy mx three times; save three step points; request T, U, V.
  new: one mx union; one block point; request the SAME {T,U,V}.

Concrete p/[any]/T:
  old: clean T, then walk no-op U and V cleaners.
  new: clean the intersection {T}; keep the same DEMAND complement.
```

On a static answer T, use the existing canonical initial for T, preserving
its tail, and apply T's existing spatial cleaner. Continue after the block. Other distinct-mark
cleaners cannot change that scalar-T image. For a packed input containing
several block marks, clean the full intersection; do not use the scalar
shortcut for that input.

Prove selected-mark cleaner commutation for identical geometry, including
partial ANY/MUST results, headers and layers. Prove stable block IDs and
suffix resumption across form-cache rebuilds. Preserve every request,
origin and observer effect. Do not suppress requests because an EXACT
cleaner has an empty path: it is partial for an abstract subtree.

Cleaner points account for 782,080 rows but only 782,080 of 4,840,208
pending memberships. The FULL cleaner-chain profile covers 12.55% of
Java CPU and 24.10% of allocation weight, including other cleaner work.
The qualifying block share and row savings still need a census. This
proposal can remove whole action steps; its whole-engine impact is not
yet quantified.

## 3. Measure before selection: factor pending images by exact mark sets

### Current versus new

Current: each saved point stores `mark -> PendingFlowImages`. A request
for many marks creates one map membership and holder per mark. Updating
the same image for several marks repeats the same FLOW union.

New: store disjoint mark sets grouped by their current immutable FLOW
root. Each mark must still denote exactly one current merged image.
Update each affected image cohort once. Detach answered atoms before
callbacks, as now. Do not take an independent union of marks and paths.

The measured largest membership population is call-stage input:
24,499 PointRows contain 3,966,089 mark/image memberships. Cleaner points
have many more rows but only one membership per row. The per-point
root-cohort census did not produce a result: its correlated OQL query
failed validation. The factorable count is unknown. Global root sharing
alone is insufficient, so do not select this representation yet.

```java
Object t = C.f;
check(t); // condition: ContainsMark(t, T) AND ContainsMark(t, U)
```

Both checks belong to one rule condition at the same saved operation
point. T and U are distinct full marks of the same group. Separate
program statements remain separate points.

```text
At one point K, incoming FLOW image A = {t.left.*}:
  before: T -> A; U -> A
  after:  A -> {T,U}

Later, only T requests image B = {t.right.*}:
  before: T -> union(A,B); U -> A
  after:  union(A,B) -> {T}; A -> {U}

Answer T: resume only union(A,B) with original C.f/T.
Answer U: resume only A with original C.f/U.
Never create U/t.right by multiplying independent unions.
```

The example is a relation illustration. The original method, operation,
site, current base, tail, field exclusion, layer and call-resume state
remain in the point key. Its request selector still has no exclusions.

```kotlin
data class ImageCohort(val root: FlowNode, val marks: MarkSet)

// Invariant: cohort mark sets are pairwise disjoint.
// decode(T) is the root of the unique cohort containing T.
class PendingImages {
    var waiting: MarkSet = MarkSet.EMPTY
    var cohorts: List<ImageCohort> = emptyList()

    fun add(images: FlowNode, offered: MarkSet): MarkSet {
        val freshSubscription = offered - waiting
        val next = CohortBuilder() // merge equal root identities, not paths
        for (old in cohorts) {
            cancellation.checkpoint()
            val affected = old.marks intersect offered
            next.add(old.root, old.marks - affected)
            if (!affected.isEmpty) {
                // Once per affected root cohort, not once per mark.
                next.add(FlowAlgebra.mergeAdd(old.root, images, cancellation), affected)
            }
        }
        next.add(images, freshSubscription)
        cohorts = next.freeze()
        waiting += freshSubscription
        return freshSubscription
    }

    fun detach(answered: MarkSet): List<ImageCohort> {
        check(answered.isSubsetOf(waiting))
        val detached = ArrayList<ImageCohort>()
        val next = CohortBuilder()
        for (old in cohorts) {
            cancellation.checkpoint()
            val selected = old.marks intersect answered
            if (!selected.isEmpty) detached += ImageCohort(old.root, selected)
            next.add(old.root, old.marks - selected)
        }
        cohorts = next.freeze()
        waiting -= answered
        return detached // immutable roots and marks; no callback yet
    }
}
```

`CohortBuilder` drops empty sets and unions marks only when root identity
is equal. Unequal roots are not declared equal by matching a header.
Structural interning is not required. Do not retain historical image
packets: after every update, each waiting mark belongs to one current
root cohort. Otherwise old versions could grow beyond the current map.

After detach, remove an empty point and key before callbacks. Materialize
each atom with its own canonical initial, as now. A packed request does
not authorize a conclusion containing another origin's marks.

Accumulate equal `OperationInput` request callbacks into a mark-set builder
within the existing operation invocation. Submit that set before leaving
the invocation. This lets a multi-mark condition use the cohort operation
once. Preserve the complete site/header/call-state key. Do not combine
different FLOW inputs by unioning their marks and paths independently.
This is not a new global barrier.

### Required proof and cost gate

Define an executable decode to the old finite `mark -> root` relation.
Prove that `add` gives exactly the old per-mark FLOW union, that mark sets
remain disjoint, and that `detach` returns the same immutable roots for
each atom. Prove request-before-answer, extra images before answer,
new requests after detach, empty-row removal and packed/scalar answers.
Keep the same counted-work lifecycle.

The gain is fewer retained per-mark memberships and fewer equal image
unions. Per-origin concrete materialization remains required. Measure
cohorts visited, FLOW unions, singleton updates, packed answers and
MarkSet copy cost. If singleton replies cause costly repeated set copies,
refine the builder before acceptance. Do not claim that the full
965,508,752-byte point-directory union is removable.

## Other candidates and rejected shortcuts

Mark-aware ordinary summary candidate lookup can avoid candidates with
the wrong FULL mark before materializing their publication/input rows.
Keep STAR premises and the complete AP predicate. Keep the special
run-1 ZERO/static-control route separate. The saved matcher scope is
5.791% of Java CPU and 106.690 GB allocation weight; accepted/rejected
shares are unknown. Do not call the entire replay scope avoidable.

An early call-operand-base guard can avoid loading a full call plan when
the current path will immediately pass the input unchanged. Prove that
the lightweight base set equals `plan.touched`. ZERO, STATIC and relevant
receiver/argument/return inputs must keep ordinary handling. The actual
irrelevant-input/miss count is unknown. The full call-plan profile share
is not the saving of this guard.

Do not delete currently unanswered marks, assume an EXACT root cleaner
kills all descendants, or discard Spring registry alias targets. These
shortcuts can remove required guidance or selected behavior. Form-cache
rebuild counts do not justify a reference-manager rewrite or unlimited
strong caching. No IR change is selected.

## Validation for the selected next iteration

Follow the proof guide before implementation. Establish constructive
decode/equivalence models, then old-versus-new executable controls.
Keep FULL same-group marks distinct and test real run-1 to run-2 source
replay and run-3 witness creation. Preserve actual-input query completion,
all-origin rules, the all-ZERO case, field grammar and exact class markers.

Measure removed operations and replacement costs separately. Count
point/image cohorts, image unions, request memberships, committed supplies,
block-eligible cleaner steps, queue reservations and form rebuilds.
Then repeat the fixed Thingsboard FULL experiment. Always report latest
reached, latest COMPLETE and latest accepted forward iterations. Do not
claim success from a smaller failed prefix or a faster generic collection.
