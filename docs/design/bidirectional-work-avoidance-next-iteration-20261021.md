# Avoid repeated analyzer work: next iteration

Date: 21 October 2026. Status: **three items selected for the next iteration**.
The required all-origin correction, impossible exact-class/field routing
removal and canonical GROUP projection reuse are selected. Their complete
[implementation specification](bidirectional-run1-origins-routing-projection-implementation-20261021.md)
controls this iteration. The other three performance items remain future
candidates. No change in this document is implemented. The current runtime is
`51dbc4d66fb1bf1b56071dbf18c90c5909e3146b`.

Use the [fresh performance report](../reports/bidirectional-cleaner-work-performance-20261021.md)
as the measurement authority. Older candidate reports are stale. Artifacts
are in `/drive-testcomp/opentaint-go-rules/cleaner-work-20261021`.

| Project | Latest reached | Latest COMPLETE | Accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-6 TIMEOUT | run-5 | run-5 |
| conductor | run-2 OOM | run-1 | run-1 |
| thingsboard | run-1 OOM | none | none |
| Stirling-PDF | run-2 OOM | run-1 | run-1 |

**Explicit semantic correction:** run-1 rule results must retain every
contributing initial as a separate unary origin. This replaces the current
representative-only result and restores the omitted demand pairs. It can
add run-1 DEMAND edges and candidates. It is not an equivalence claim
against the representative-only runtime.

The five performance items must preserve the analysis contract in which
they operate. Keep complete condition admission, source/sink identities,
witness keys, request effects and later FULL analysis. No other field,
mark, rule-action, static-supply or layer change is proposed. Work order,
duplicate callback counts and progress before a resource limit can change.
Follow [proof-guide.md](../../spec/proof-guide.md). Do not change the IR,
reference managers or cache policy.

## Required correction: emit each contributing run-1 origin

This correctness change is a prerequisite for the next iteration. Its
priority does not come from a measured speedup. The measurements above
still describe the representative-only runtime.

### Current and new behavior

Keep the complete rule check. Let `S` be the distinct nonzero canonical
initials of one complete, compatible condition binding. Let `C` be the
result produced by its existing action.

```text
Reference ND dependency:  {I1, I2} -> C / DEMAND
Current run-1 result:     repr(I1, I2) -> C / DEMAND
New run-1 results:        I1 -> C / DEMAND
                         I2 -> C / DEMAND
```

Each new edge has one `InitialAp` premise. Do not construct a singleton
`PremiseSet`, retain an executable ND edge, or enter the run-1 E6 join.
Here, an ordinary edge means one premise; it does not mean Layer.NORMAL.
GROUP results remain DEMAND.

**Why:** the representative is a sound forward weakening of a completed
conjunction, but it omits the other origin labels. The ordinary handoff
can only export the origins that were retained. Emitting every member
keeps the forward approximation and restores all per-member demand pairs
of the reference ND result. More callers can apply the new unary arms;
these extra results remain candidates for later FULL refinement.

```java
Object relay(Object a, Object b) {
    Object r = combine(a, b); // configured source rule below
    return r;
}
// One top-level rule, group G:
// ContainsMark(arg0, (G,T)) AND ContainsMark(arg1, (G,U))
//     -> AssignMark(result, (G,V))
```

```text
Run-1 condition: CM(a,G) AND CM(b,G)
Accepted facts: I_a -> a/G, I_b -> b/G
Result C:       return/G, DEMAND

Before: I_a -> return/G
        backward guide: return/G -> I_a

After:  I_a -> return/G, I_b -> return/G
        backward guides: return/G -> I_a, return/G -> I_b

Only a's condition is satisfied: no result.
Run-2 uses full marks at limit 1 and can follow both origin demands.
Its actual rule reversal still requires the declared T and U semantics.
```

The missing pair is a structural guidance defect. This example does not
claim a newly reproduced project-level false negative or replace the
required end-to-end regression test.

### Emission and ownership

Both active local result producers need the change:

- `ConjunctionStore` for explicit conjunctive edges.
- `RulePredicateStore` for complete source predicate bindings.

Separate complete binding admission from result-origin emission. In
run-1, neither producer may first call `Run1Premises.combine` and lose
the other initials. Source and conjunction result callbacks emit each
origin. Sink callbacks still receive one complete binding, with all
checked inputs, proof choices and witness facts; do not create a partial
sink witness for each unary arm. Later FULL stores keep ExactPremises
and their existing ND behavior.

The integration owner is RunMethodLocalServices. Replace the run-1
single-value premise policy with origin emission at these result paths.
Remove the representative comparator when no production caller needs it.
Keep the shared ApManager.union and later ExactPremises unchanged; do not
put a mutable run mode in the AP manager.

The following pseudocode defines the emission for one accepted binding.
The inputs are already canonical atomic run-1 originals.

```kotlin
fun forEachRun1Origin(inputs: List<Input>, emit: (InitialAp) -> Unit) {
    check(inputs.isNotEmpty())
    val nonzero = LinkedHashSet<InitialAp>()
    for (input in inputs) {
        cancellation.checkpoint()
        val initial = input.premise as InitialAp
        check(initial.manager === ap)
        check(initial.exclusion == ExclusionSet.Empty)
        if (!initial.isZero) nonzero += initial
    }
    if (nonzero.isEmpty()) emit(ap.zero)
    else for (initial in nonzero) emit(initial)
}

onCompleteSourceOrConjunction { binding ->
    // Keep the original MULTI/fixed-target action classification.
    val result = existingTarget(action, binding.layer)
    forEachRun1Origin(binding.inputs) { initial ->
        emit(initial, forceRun1Layer(initial, result))
    }
}
```

Keep the complete initial: base, path, tail, mark and E. Do not choose
by argument/static preference, merge different initials, change source
target geometry, or run a MULTI action as a unary AP transfer. Preserve
action/occurrence IDs, seed permissions, field processing and static
writer/read handling. Apply the existing output policy to each arm.
Method-edge and summary stores retain their current delta admission.
The ordinary HandOff and group-to-full demand matching need no new mode.

ZERO stays neutral within one binding. `{ZERO,I_a}` emits only `I_a -> C`.
Emit `ZERO -> C` **only if there is a complete compatible cube whose every
required condition can be satisfied by ZERO-premise edges**. Its action
must also pass the existing seed permissions. An all-ZERO accepted binding
is a witness for this rule. One ZERO input, control-flow reachability, or
ZERO inputs collected from different cubes is not such a witness. The
new unary projection must not introduce an extra ZERO-premise result.

### Avoid enumerating combinations of origin labels

The purpose is to avoid repeating the same result for every combination
of origin labels. It does not change how a complete condition is admitted.

```java
Object relay(Object a, Object b) {
    return combine(a, b); // CM(a,G) AND CM(b,G) -> AssignMark(result,G)
}
```

```text
One complete compatible cube; the result C and its layer are fixed:
  CM(a,G): I1 -> a/G, I2 -> a/G
  CM(b,G): I3 -> b/G, I4 -> b/G

Enumerate bindings, then split each binding:
  (I1,I3) -> emit I1 -> C and I3 -> C
  (I1,I4) -> emit I1 -> C and I4 -> C
  (I2,I3) -> emit I2 -> C and I3 -> C
  (I2,I4) -> emit I2 -> C and I4 -> C
  The edge store removes repeated arms after this work.

Keep origin choices separately, then emit their union:
  emit I1 -> C, I2 -> C, I3 -> C, I4 -> C

Both produce the same four arms. Neither produces ZERO -> C.
If CM(b,G) has no accepted input, neither produces any arm.
```

For independent slots with n and m choices, the first path visits n*m
bindings. The second visits n+m memberships. With many slots, the first
cost is a product and the second is a sum. This is the intended saving.
It is separate from replacing one representative with all contributing
origins, which can increase the number of stored edges.

The current ConjunctionStore uses KaryJoin, and RulePredicateStore.join
streams compatible binding products. Adding only a loop over a completed
binding fixes the omitted arms, but retains this enumeration. The run-1
result-producer path must use the factored projection below; do not put
the new loop after an unchanged origin Cartesian join and claim that
combinations were avoided.

Do not implement this change by enumerating all ND premise sets or
restoring Cartesian E6 bindings. For run-1 fixed-target result production,
retain origin choices in factored form. Keep the existing scope, action,
NNF proof/branch, leaf compatibility, effective-layer and fact-group
partitions. Within one such partition, each canonical leaf has a map
from canonical initial to its accepted input/version. Facts from distinct
initials must not be merged into one checked input.

For independent explicit slots, the condition is complete when every
slot has an accepted input. For an NNF proof, shared canonical leaves
must first pass the existing header checks; their origin choices must
agree. Intersect their candidate keys when the same leaf is joined, and
reject an empty intersection. Do not substitute "each leaf is nonempty"
for a complete compatible NNF branch or an action's seed permission.

After these checks, keep only choices that participate in a complete
compatible binding for this cube, action and result partition. The result
payload and its effective layer are fixed. Keep ZERO admission separate
from the union of nonzero origins:

```kotlin
fun publishReadyResult(cube: ReadyFixedTargetCube) {
    check(cube.completeAndCompatible)
    val origins = LinkedHashSet<InitialAp>()
    for (choices in cube.viableLeafChoices) {
        check(choices.isNotEmpty())
        for (initial in choices.keys) {
            cancellation.checkpoint()
            if (!initial.isZero) origins += initial
        }
    }
    for (initial in origins)
        emitOrdinary(initial, forceRun1Layer(initial, cube.result))

    // Admit a complete binding using ZERO-premise inputs only.
    // Preserve this cube's compatibility, layer and action/seed checks.
    val zeroBinding = cube.completeBindingUsingOnly(ap.zero)
    if (zeroBinding != null) {
        check(zeroBinding.inputs.all { it.premise == ap.zero })
        emitOrdinary(ap.zero, forceRun1Layer(ap.zero, cube.result))
    }
}
```

This is a proposed result-producer interface, not an existing API. The
ZERO-only check returns an actual complete cube binding, not a test for
ZERO's presence in a pooled set. It uses the existing condition and seed
checks with the inputs restricted to ZERO-premise edges. For a fixed cube
with independent slots, it reduces to checking that every required slot
has a ZERO input. Shared leaves must also pass the existing agreement
checks. Do not combine ZERO evidence across OR branches or incompatible
headers. This check does not require enumerating nonzero origin products.
Empty or unconditional predicates keep their existing source/GEN path.

```text
Condition: A AND B
  A: {ZERO},    B: {I_b}       -> I_b -> C; no ZERO -> C
  A: {ZERO},    B: {}          -> no result
  A: {ZERO},    B: {ZERO}      -> ZERO -> C
  A: {ZERO,I_a}, B: {ZERO,I_b} -> ZERO -> C, I_a -> C, I_b -> C
                                ZERO is valid because (ZERO,ZERO)
                                satisfies this complete cube.

Condition: (A AND B) OR (C AND D)
  A: {ZERO}, B: {I_b}, C: {I_c}, D: {ZERO}
  -> I_b -> result, I_c -> result; no ZERO -> result
     Neither cube is satisfied entirely by ZERO-premise edges.
```

On the first complete partition, offer all supported origins. A later
origin must also get an arm even when C was already emitted for another
origin. A later conclusion delta must reach all supported origins.
Use existing local version/delta state and ordinary edge admission; do
not add a global origin history or a shadow store of ND combinations.

The factored representation removes products of origin choices for these
result edges. It does not claim to remove every NNF proof-shape product
or complete sink-witness combination. Keep those compatibility and witness
contracts. The two producer adapters must prove that their factored
projection equals emission over every compatible complete binding;
grouping only by rule ID or group G is insufficient.

### Constructive correctness laws

For one completed binding B define `origins(B)` as its distinct nonzero
initials, or `{ZERO}` if every input origin is ZERO. Define:

```text
split(B,C) = { (I,C,DEMAND) | I in origins(B) }

H({I1,...,Ik} -> C) = { (demand(C), demand(Ij)) | 1 <= j <= k }
H(split(B,C))       = the same set of complete demand pairs
```

H uses the existing leaf extraction, tail normalization and mark-exclusion
normalization in `HandOff.toBackward`. Equality is over the full entry
and exit pair, including the mark group. Equal guide-entry sets alone
are insufficient. Group G at either end still permits the corresponding
full marks of G in run-2 through existing matching.

The proof is constructive: each ND member has its own unary arm, and
each arm names a member of that completed binding. Apply the same handoff
normalization to the same C/member pair in both directions. ZERO uses
the existing canonical union-neutral rule. No new handoff inference or
omitted-origin recovery assumption is needed.

This is equality of the direct clause projection. For stored results,
also prove equality of the denoted demand relation after the existing
merge/subsumption rules. Equal meaning does not require equal physical
rows or normalization partitions.

Let B_C be the finite set of complete compatible bindings for the same
scope, cube, action and fixed result C. Then:

```text
union over B in B_C of split(B,C)
  = { I -> C | I != ZERO and I occurs in some B in B_C }
    plus ZERO -> C iff some B in B_C uses only ZERO-premise inputs
```

In each direction, the same accepted binding is the witness. A nonzero
arm names one of its inputs. A ZERO arm requires every input in that
binding to have a ZERO premise. Conversely, a completed all-ZERO binding
emits ZERO by the scalar emission rule. Thus the projection cannot create
a ZERO result in the absence of an all-ZERO cube witness.

For independent choices A1,...,An within one fixed compatible cube,
B_C = A1 x ... x An. Its viable nonzero origins are the union of those
choices, and an all-ZERO binding exists exactly when every Aj contains
ZERO. Construct it by choosing ZERO at each slot. To witness a nonzero
arm, choose its named input and one input at each other slot. These
witnesses use finite nonempty lists; no classical choice is required.
For shared leaves or other compatibility constraints, prove the viable
choices and ZERO-only check equivalent to B_C before using the projection.
Nonempty unfiltered slot pools alone do not prove this property.

Additional required laws are complete-condition admission, atomic-origin
closure through propagation/calls, current representative-result inclusion,
per-member caller-binding coverage, sticky DEMAND, and unchanged full sink
evidence. A complete reference ND caller binding supplies every arm, so
it still produces C. Applying an arm with fewer inputs can only add a
run-1 candidate. Later FULL conjunctions must retain all actual premises.

Restore per-member guidance wherever the reference ND summary exists;
do not describe this local equality as a proof of the whole analyzer.
Internal sinks/dead locals still use the existing complete witnesses and
seed path, and need integration tests. This correction alone does not
prove termination, completed project parity or every driver adapter.

### Proof and implementation gate

Extend the constructive model with all-origin emission, factored-choice
projection, ZERO alternatives and equality of complete handoff pairs.
The existing `Run1RepresentativePremises.lean` representative membership
and guide-entry theorem do not prove these new requirements. Keep their
historical scope; add the new laws and audit them before implementation.

A bounded design check passed 4680 independent-slot cases and 512 shared-
leaf cases against a scalar complete-binding oracle. It includes empty
slots and ZERO alternatives. This checks the abstract origin projection;
it is not a Lean proof or validation of the NNF, AP or driver adapters.

When this correction is implemented, update `ap.md` §0.4,
`grouped-run1-statics.md` §0.1 and the representative-premise design's
handoff argument. They currently specify the running implementation.
This proposal supersedes that policy for the next iteration only; the
normative runtime specifications are not silently changed by this edit.

Required independent controls:

- Two and three different initials produce every unary arm; missing
  conditions produce none. Repeated initials produce one arm.
- All-ZERO, mixed ZERO/nonzero, and factored alternatives that allow both
  an all-ZERO binding and nonzero bindings retain exactly the required arms.
- ZERO inputs in different OR cubes, incomplete cubes, or incompatible
  bindings never produce a ZERO-premise result. Check ZERO admission
  against a scalar complete-cube oracle, separately from nonzero arms.
- Late origins, reversed arrival orders, annotation growth, OR branches,
  shared leaves and incompatible repeated-leaf headers preserve the scalar
  complete-binding projection. No unrelated branch/origin is included.
- MULTI source geometry, source action/seed identities and complete sink
  witnesses stay intact. Keep nonstatic, static-field and exact static-class
  originals, without applying the old representative preference.
- HandOff pairs equal the complete-ND reference for every result leaf.
  A run-1/2/3 trace must require the former discarded input's full mark;
  run-2 gets its group demand and later FULL analysis restores the complete
  conjunction. Include an internal sink with a dead local result.
- Run-1 allocates no PremiseSet/E6 origin products. Later FULL tests keep
  their exact ND labels and caller joins. No runtime or benchmark has been
  changed or rerun for this proposal edit.

For k distinct contributing initials and one fixed C, output can grow
from one arm to k arms. With n_j compatible choices per independent leaf,
the origin representation uses `O(sum n_j)` memberships instead of
enumerating `product n_j` origin tuples. Proof-shape/witness costs remain
separate. More unary edges can increase run-1 time and storage; this is a
required guidance correction, not a promised memory optimization.

## Expected impact and selection order

Apply the required ND guidance correction above before accepting the
next iteration. The following optimization order then gives priority
to the run-1 engine target and later refinement. Expected effect is
distinct from measured saving.

| Rank | Candidate | Expected effect | Evidence and confidence |
| --- | --- | --- | --- |
| 1 | Remove impossible exact-class/field routing pairs | Avoid serialized run-1 static matching and temporary unions | Thingsboard run-1: static service 41.80% and answerClass 33.76% of Java samples. Current invariants make the selected pairs disjoint. The removable fraction of those stacks is unmeasured. |
| 2 | Reuse unchanged canonical GROUP projections | Basic identity reuse reduces allocation; a proven optional certificate can also skip traversal | OWASP run-1: 22.96% of Java samples and 35.96% of allocation weight have runtimeFact ancestry. Avoidable fraction is unmeasured; a certificate adds retained memory. |
| 3 | Catch up summaries only when premise permission changes | Avoid repeated payload traversal, publication allocation and duplicate admission in later runs | Conductor run-2 addLink: 31.27% of Java samples. PDF/conductor have 25.17/2.35 million pending publication entries. Replay share and saving remain unmeasured. |
| 4 | Allocate conclusion containers only when used | Reduce retained memory and allocation in all runs | Conductor method-store cohorts support at least a 229.75 MB layout-conditional estimate. Highest quantified retained-memory opportunity; no claim that it alone prevents OOM. |
| 5 | Admit static supplies before temporary batch allocation | Avoid repeated supply allocation/dispatch in run-1 | Thingsboard has 2.10 million pending supplies. Distinct membership/pattern counts are unavailable, so select only after a bounded batch census. |

Ranks 1–3 remove work before tuning its representation. Ranks 1–2 target
the two failing run-1 cases. Rank 3 targets the later conductor/PDF OOM
pattern and can also reduce later OWASP work. Rank 4 has the strongest
current retained-memory lower bound. Rank 5's bulk variant has an
unresolved API effect proof and is not ready to implement. No fixed
speedup, removable percentage or completed-analysis outcome is promised.

## Remove impossible exact-class and field routing pairs

**Current:** exact class-marker requests scan field states. Field supplies
scan pending class interests. Field writers scan class requests. These
pairs always fail the existing intersection test. Some loops also create
temporary group unions before testing the pair.

**New:** omit these all-failing scans. For an exact class request, use the
existing trie prefix lookup instead of enumerating descendant writers.
Keep the existing matcher and group admission for every possible match.

**Reason:** avoid whole families of comparisons and temporary unions.
This is work elimination. It adds no cumulative store and does not change
class-marker or field semantics.

```java
class C { static Object payload; }
void use() { C.payload = source(); sink(C.payload); }
```

A rule can separately use the exact global marker at `S.C`. This marker
is not the value of `C.payload`.

```text
F = (S, C.payload, STAR, {}, FLOW)   field region
Q = (S, C,         EXACT, {}, G)    exact global marker

Supply F/G, then request Q/G:
  before: scan field states, construct supplied group unions, try Q/F;
          all Q/F intersections fail, so Q/G stays pending
  after:  omit those attempts; Q/G still stays pending

Supply the actual Q/G:
  before: scan descendant fields, reject them, answer Q/G
  after:  omit the failed field scan, answer the same Q/G
```

The service enforces these invariants itself. Each `classInterests` and
`classRequested` pattern is STATIC with one accessor and EXACT tail.
Each stored `FieldState.region` is STATIC with two accessors and STAR
tail. A raw field/deeper supply or writer has at least two accessors.
If the first accessors differ, the common-prefix check fails. Otherwise,
the shorter exact path ends first and the exact-ancestor check fails.
Exclusions and groups cannot make these pairs overlap.

The small scope has six local changes in `Run1StaticService`:

```kotlin
// requestClass: delete the all-failing fields.values answer loop.
// Keep its broadGroups answers and later writer notification.

// commit: keep write/read presence and field group state first.
if (fieldKey(position) == null) answerExistingClassInterests(position, supply.groups)

// registerWriter: keep ordinary field matching and askOnce.
if (fieldKey(row.region) == null) matchExistingClassRequests(row)

// Regions: expose the existing PathTrie prefix operation.
fun prefixes(p: Pattern): List<V> =
    index.lookupPrefixes(p.fact.base, p.fact.path.toIntArray()).distinct()
// requestClass: prefixes include root and same-path writers, not descendants.
for (key in flowWriterIndex.prefixes(interest.region)) {
    val writer = flowWriters.getValue(key)
    if (requestMatches(writer.region, interest.region)) askOnce(writer, interest.wanted, out)
}

// commit: exact class supply cannot supply any FieldState.
if (!isExactClassPoint(position)) runExistingBroadSupplyFieldLoop(position, fresh)

// field creation: cheap filter before intersecting old broad entries.
for ((position, groups) in broadGroups)
    if (!isExactClassPoint(position) && intersect(canonical, position) != null)
        state.reach += groups
```

The helpers above are pseudocode for existing loops, not new state or
changed callback APIs. Preserve the current loop order, supplied group
arguments, row removal and `askOnce`. The field-creation filter still
visits every broad entry; it is not elimination of that entire scan.

The service's broad root/class supply, write, read and writer fallbacks
remain. A nonexact `S.C.*` supply can overlap a field or an exact marker
when its E permits it. This candidate does not create such an actual
run-1 initial; it preserves the existing service API behavior. Broad
root/nonexact-class interest requests remain rejected as before. Do not
skip all short paths, remove class routing or remove group filtering.

For one exact-class request, the first change avoids `fields.size` failed
attempts and their group unions. For a field supply, it avoids up to
`classInterests.size` failed attempts. For a field writer, it avoids
`classRequested.size` attempts. These are source formulas. The snapshot has 88 fields and 1673 pending exact-class rows. These are
actual populations, but their historical Cartesian work is unmeasured;
no retained-byte saving is established.

The closed thingsboard run-1 JFR has 24,205/57,913 Java samples with
static-service ancestry (41.80%), 19,551 with answerClass ancestry
(33.76%), and 13,712 exact intersect leaf samples (23.68%). These overlap.
Recorded waits attributed to commit total 2557.346 blocked-thread seconds
across concurrent threads; this is not wall time, CPU or monitor hold time.
Shortening lock-owned work may reduce that serialization. None of these
shares is the removable-loop fraction. The existing candidateVisits
counter omits answerClass attempts. Evidence:
`validation/thingsboard-profile-review.md`.

Prove exact-shorter-path disjointness for arbitrary supported paths,
tails and E. Prove the private map invariants across all commit orders.
Then prove that removing all-null/all-false loops preserves effects,
pending groups and writer admissions. Prove that prefix lookup retains
every possible match to an exact point. Compare old and new service
closures with independent finite cases: exact class/field separation,
genuine class supply, partial groups, both arrival orders, broad root and
class fallback with blocking E, writer activation, read presence and
clear. Retain synchronization and suitable cancellation checks.

Evidence: `validation/exact-class-routing-next-candidate.md`.

## Avoid repeated GROUP fact projection

**Current:** GROUP output adapters call `ApManager.runtimeFact`. Its TAINT
branch maps the trie leaves, interprets their mark sets, interns payloads
and constructs a wrapper, even for an already projected GROUP value.
The selected header optimization does not remove this fact walk.

**New:** first reuse exact unchanged canonical payloads and wrappers.
Consider a result-local immutable projection certificate only if the
measured traversal cost justifies its memory cost.

**Reason:** the same fact can cross several normalization boundaries.
Basic identity reuse reduces allocation while retaining the TAINT walk.
A proven optional certificate can remove that walk at later boundaries.
Both forms keep the existing boundary effects.

```java
Object wrap(Object p) {
    Box b = new Box();
    b.value = p;
    sanitizeSelectedT(b.value);
    return b.value;
}
```

```text
At one actual suffix point:
  F = I -> temp/G, TAINT, DEMAND, empty field exclusion

Before: selected GROUP cleaning preserves the mark, but output adapters
        can map all the same trie leaves again.
After:  unknown values follow the old projection; a proven unchanged
        canonical result can reuse its value at the next projection.
```

The closed full JFR run-1 has 2683/11686 Java samples under runtimeFact
(22.96%) and 204,601,255,600/568,979,695,536 allocation-weight bytes
(35.96%). These include valid first projections and descendants; neither
is an avoidable-work fraction. This item targets GROUP run-1 work; it does not explain the FULL
run-2 conductor OOM.

Start with local identities after the same normalization and receiving
manager interning. Reuse a FLOW wrapper only when its required layer,
canonical mx and E are unchanged. A TAINT leaf still needs the receiving
leaf interner, even when its mark-set references are unchanged. Preserve
the exact root, tail, E and layer normalization rules.

A narrower no-token path can be designed at an immediate AP callback:
`applyCompiledEdge -> run1Policy -> core.collect` currently projects the
same result at both boundaries. Prove that specific producer/consumer
contract before bypassing the second mark walk. Keep cover, static
routing and premise-dependent demotion. All other collect callers retain
their existing projection. This is an alternative proof task, not a
blanket run-1 shortcut or a new public caller promise.

The producer audit rejects a blanket skip: raw ZERO GEN can still have
NORMAL layer, generic/custom forms can contain FULL constants, and a
GROUP root from another manager can need receiving-leaf interning.
Evidence: `validation/group-producer-invariant-feasibility.md`.

An optional complete traversal skip needs a stronger invariant. DEMAND
alone is insufficient: an unknown DEMAND value can contain FULL atoms.
One design uses a private manager-unique opaque token on the final
immutable projected TAINT result. The token has no manager backpointer,
guard, caller, support condition or fact history. Ordinary construction
defaults to unknown. Equality, hashing, premise matching and publication
ignore it. Only successful projection can certify its own result.

This token is additional representation state. A field on every
`TaintTree` can increase retained memory in later FULL runs too. Measure
the wrapper layout/counts and certificate reuse before selecting it.
Do not introduce a global identity cache or silently treat equal unknown
values as certified.

Keep run-1 cover, field limits, static routing, premise-dependent layer
demotion and all requests. A selected GROUP TAINT primitive may separately
return its unchanged input through the existing output adapter. It must
not bypass Field READ/CLEAN/WRITE or RemoveAll. Unknown/FULL-valued inputs
still need the original GROUP projection.

Prove projection idempotence and receiving-manager canonicalization,
certificate production, mismatch fallback and conservative reset. Test
two managers sharing a mark table, separate tables with the same numeric
IDs, uninterned equal leaves, mixed FULL/GROUP/zero atoms, exact/any leaf
placement, covers and static-premise demotion. Keep FULL execution intact.

Evidence: `validation/group-normalization-next-candidate.md` and
`validation/OWASP-profile-review.md`. Current
source: `ApManager.runtimeFact`, `ApOut.run1Policy`, `TaintClean`,
`run1CoverFacts` and `RunMethodAnalyzer.forceRun1Layer`.

## Catch up raw summaries only when permission changes

**Current:** each later-run input batch that admits a fresh current query
rescans raw summaries and queues all permitted conclusions. It includes
conclusions that were already offered. Global publication admission
removes duplicates after the actor flush.

**New:** examine raw premise keys first. Expand a premise's old conclusions
only when that premise becomes permitted in this admission batch. Keep
the ordinary path for every new raw summary delta.

**Reason:** avoid repeated conclusion enumeration, publication wrappers
and downstream duplicate admission. The smallest variant needs no new
permanent index, fact history or queue.

Conductor's run-2 native capture shows `addLink`, summary and subscription
work. The source contains this repeated path. The run-2 heap has 2,348,062 pending publication entries. PDF has
25,171,733. These populations support the priority, but do not identify
the replay share or measure its expected saving.

```java
Object id(Object p) { return p; }
void first()  { sinkT(id(sourceT())); }
void second() { sinkU(id(sourceU())); }
void third()  { sinkV(id(sourceV())); }
```

```text
Backward run-2:
  P_T = (return, ., $, {}, T)
  raw answer: P_T -> (arg0, ., $, {}, T, DEMAND)

Before:
  admit T: offer the T answer
  admit U: offer T again, then any permitted U answer
  admit V: offer T and U again, then any permitted V answer

After:
  admit T: catch up the old T answer
  admit U: catch up only premises newly permitted by U
  admit V: catch up only premises newly permitted by V
  new raw answers: retain the ordinary publication path
```

At this revision, `StrongQueries.permits(P)` requires all complete
canonical members of nonzero `P` in the admitted set. For ZERO, it requires
at least one admitted query. The predicate does not inspect the conclusion
or a mark/path selector. Queries only grow during the run.

Let `fresh = Q_after - Q_before`. For a nonzero premise:

```text
newlyPermitted(P) = permits(P, Q_after)
                   AND some member of P is in fresh
```

This is exactly a false-to-true permission change. A fresh member was
absent before; conversely, a newly permitted nonzero premise must contain
one. Forward ZERO changes with the first nonempty query batch. Backward
ZERO already publishes unconditionally.

Implementation sketch, using the existing publication and failure paths:

```kotlin
fun hasFreshMember(p: PremiseKey, fresh: Set<InitialAp>): Boolean {
    for (i in 0 until p.size) if (p.member(i) in fresh) return true
    return false
}

val hadQuery = currentQueries.permits(ap.zero)
val starts = currentQueries.admit(added, guides) // installs the whole batch
if (starts.isNotEmpty()) {
    val fresh = starts.toHashSet() // temporary canonical identities
    for (p in summaries.rawPremises()) { // proposed iterator over existing keys
        val changed = if (p.isZero) forward && !hadQuery
            else currentQueries.permits(p) && hasFreshMember(p, fresh)
        if (!changed) continue
        for (facts in summaries.borrow(p)) queuePublications(p, facts)
    }
    starts.forEach(::addInitial)
}
```

The snippet is pseudocode. `rawPremises()` must expose actor-owned raw
keys without expanding conclusion groups first. Preserve the existing
iteration checks and canonical identities. Header scanning still costs
`O(batches × raw premise-member count)`. This variant removes repeated
payload expansion, not every scan. A reverse missing-member index is a
possible later extension; it costs persistent memory and is not required
for this first variant.

Prove old-answer catch-up, future-delta coverage and equality of selected
guidance as well as published values. The secured catch-up and every later
ordinary raw delta must absorb the current raw relation, including T2
field/mx annotation replacements, both layers and previously restricted
guide pieces. Boolean permission equality alone is insufficient. Keep subscribe-before-link, late
subscriber replay, full ND permission, ZERO behavior, counted Work and
flush failure restoration. Test each ordering of raw row creation, query
admission, raw growth and flush. A future conclusion-dependent permission
predicate would invalidate this formula and require a new design.

Evidence: `validation/later-run-replay-next-candidate.md` and independent
`validation/replay-proposal-review.md`. Current source:
`RunMethodAnalyzer.addLink`, `StrongQueries.permits`, `RunSummaryStore`,
`queuePublications` and `SummaryStorage.publish`.

## Allocate conclusion containers only when used

**Current:** every ConclusionGroup allocates both family maps and a two-bit
BooleanArray. Every FlowGroup/TaintGroup allocates an outer layer array
and two capacity-1 lists, even when only one family/layer receives facts.

**New:** keep nullable FLOW/TAINT maps, two REACH bits in an Int and named
nullable NORMAL/DEMAND lists. Allocate a map or list on its first use.
Keep the existing add/merge/subsumption/interner algorithm.

**Reason:** remove unused objects without changing the number or meaning
of retained facts. This is a representation change for all runs.

```java
Object relay(Object x) { Object y = x; return y; }
```

```text
run-2: (return, ., $, {}, T) -> (y, ., $, {}, T, DEMAND)

Before: ConclusionGroup has empty FLOW map, used TAINT map, reach[false,false].
        Its TaintGroup has empty NORMAL list and one-element DEMAND list.
After:  FLOW=null, TAINT map unchanged, reachBits=0.
        NORMAL=null; DEMAND contains the same exact value and add delta.
```

Conductor run-2 Q13 has 3,191,016 ConclusionGroups, 6,382,032 maps and
3,369,318 TaintGroups with 6,738,636 lists. MethodEdgeStore enforces K6
for one fixed premise; REACH bypasses these groups. Thus at least one
48-byte map header per measured method ConclusionGroup is unused.
This does not justify restricting the generic ConclusionGroup API to one
family. Other owners can still use both.

```kotlin
private var reachBits = 0
private var flow: HashMap<AccessPathBase, FlowGroup>? = null
private var taint: HashMap<AccessPathBase, TaintGroup>? = null

// Inside each layer group; no lazy delegate or new wrapper:
private var normal: ArrayList<TaintTree>? = null
private var demand: ArrayList<TaintTree>? = null
private fun values(layer: Layer): ArrayList<TaintTree> = when (layer) {
    NORMAL -> normal ?: ArrayList<TaintTree>(1).also { normal = it }
    DEMAND -> demand ?: ArrayList<TaintTree>(1).also { demand = it }
}
// Use values only on add. Keep the old add loop and returned T4 delta.
```

At the measured layout, lazy family maps plus in-object REACH bits have
an estimated lower bound of 229,753,152 bytes saved in the conductor
method-store cohort, if ConclusionGroup stays 32 bytes. Named layer
lists add an estimated 53,909,088 bytes net after expected group growth
from 24 to 32 bytes. Each unused layer removes at least another 48 bytes;
its count is unmeasured. Confirm object layouts before claiming an
achieved saving. These are disjoint container cohorts within Q13; do not
add retained unions or claim this saving makes run-2 complete.

Prove equal contents, returned deltas and borrowed-view behavior after
every add. Keep complete E/mx, tails, interner identity, layer separation,
REACH/FLOW/TAINT and NORMAL/DEMAND enumeration order. A sequence created
before the first insertion must see that insertion when consumed later.
Preserve generic mixed-family inputs and owner mutation boundaries.
Use constructive operation-sequence models and existing independent T4
controls, including duplicates, annotation replacement and subsumption.

Direct singleton storage and promotion remain a separate extension;
there is no layer-size census to justify its extra complexity. No cache,
IR or fact-history change is included. Evidence:
`validation/conclusion-container-next-candidate.md` and
`validation/conductor-ownership-review.md`.

## Admit static supplies before batch allocation; investigate bulk packing

**Current:** static summary application expands GROUP leaves into singleton
RegionGroups and appends each to an actor ArrayList. The service removes
repeated membership after the queue-empty barrier, after allocation and
routing work have occurred.

**New, small variant:** admit exact complete `(Pattern, group)` membership
inside the temporary batch before constructing a repeated payload. Keep
first-membership order and the existing commit barrier. Offer leaves
directly instead of constructing a temporary slices list.

**Reason:** equal output supplies can come from different, valid writer
contexts. They need distinct writer continuations, but an equal supply
membership needs only one global admission in the same batch.

```java
void copy() { C.f = C.g; C.f = C.h; }
void consume() { sink(C.f); }
```

```text
Keep both FLOW writers:
  C.g/*/FLOW -> C.f/*/FLOW, DEMAND
  C.h/*/FLOW -> C.f/*/FLOW, DEMAND

Their GROUP answers can offer the same complete P = C.f/*, E={}:
  before: [(P,{G}), (P,{G})]; service dispatches twice
  after:  [(P,{G})]; same supply admission and answered memberships
```

Use the complete Pattern, including base, path, tail and E. Do not merge
writer IDs, request IDs, premises or different continuation stages.
An ordered Pattern set can similarly remove exact duplicate write
presence. A read-registration key must also retain owner and ID.

Thingsboard run-1 Q26 has 2,102,252 pending supply entries across 6036
actors; the largest actor has 4428. This is distributed state. The fresh
snapshot does not measure distinct `(Pattern, group)` membership, so the
small variant's saving is unknown. A seen map adds memory when offers
are unique. Measure offered/fresh membership and complete-pattern width
per batch before selecting a representation.

A stronger variant packs `Pattern -> union(GroupSet)`. It also combines
different groups: `[(P,{G1}),(P,{G2})]` becomes `[(P,{G1,G2})]`. This can
avoid dispatch even with no duplicate memberships. It reorders some
supply memberships. **This bulk variant is not ready for selection.**
Before implementation, prove equal final req/reach/processed partitions,
remaining pending rows, writer admissions and complete materialized
owner effects for all supported broad/class APIs. An exact class request
can consume its group from the first overlapping supply; different E
values can affect that actual overlap. Pattern/group set equality alone
is not a proof of equal answers. Keep the scalar path or first-answer
provenance where the stronger law is unavailable. Do not change the
static supply contract to close this proof gap.

The small variant has a constructive stuttering proof: registrations
precede the supply stage; a repeated same-P/same-group supply cannot
create a new write/read overlap, fresh field membership, pending class
answer or writer admission before another registration. Prove that law
against the actual service and owner handlers. Test duplicate and partial
groups, all arrival orders, field partitions, late writers, broad roots,
exact class markers, E/tails, cancellation and counted effects. Compare
complete effects, not callback counts. Evidence:
`validation/thingsboard-static-batch-next-candidate.md`.

## Separate correctness follow-up: preserve actual exit locations

The fresh strict comparison has two missing PDF return coordinates and
525 rejected virtual exit records. The selected five optimizations did
not change this export integration. A future fix must retain or recover
the actual return-specific witness through boundary/Simple export and
resolve synthetic exits explicitly. It must not report all returns or
add comparison aliases. This changes reported observations and needs its
own design and tests; it is not silently included in an optimization.
The OOM runs do not prove coverage of the two exact main paths.

## Implementation gate

The ND correction explicitly changes the retained run-1 origin/demand
relation. The five performance items preserve the corrected contract;
they must not retain representative-only behavior as their equivalence
reference. Select their scope before implementation. First prove the
stated local laws
constructively in Lean, then add independent differential controls and
implement within the existing owners. Compare complete facts, selected
guidance, request effects and witness identities at completed barriers.
Do not treat a changed approximation as an optimization detail.

For rank 1, keep broad supply APIs and exact class markers. For rank 2,
begin with exact local canonical identities; the optional certificate
is separate. For rank 3, retain the ordinary raw-delta and failure paths.
For rank 4, verify actual JVM layouts and borrowed-view behavior. For
rank 5, measure batch widths and prove the proposed effect law before
selecting the bulk representation.

Use the same fixed project/configuration set for a selected iteration.
Report every engine time, latest reached/COMPLETE/accepted iteration,
strict missing-location result, OOM owner and profile phase. Keep the
separate return-location correctness follow-up visible. No new runtime
change is authorized or implemented by this proposal.
