# Next iteration: reduce run-1 guard and propagation work

The [rule-provider and GroundingSupport removal proposal](bidirectional-run1-rule-provider-and-grounding-removal-20261019.md)
supersedes this document's guard/certificate retention and full-alias
designs. It removes `GroundingSupport` entirely and keeps the earlier
DEMAND field contract. The measurements below remain evidence; the other
optimization candidates are separate future work.

Proposal based on the [fresh performance report](../reports/bidirectional-grouped-statics-performance-20261019.md).
Measured runtime revision:
`ff2966455da2e5ffd0db655a1ff06c5954ed3c8a`.
No change proposed in this document is implemented.
The source, rules and input guard were unchanged during measurement.

## Current result

The target is a complete **run-1 engine within 60 seconds**.
Prescan, setup and report barriers have separate times.
Every result must state the latest reached iteration.

| Candidate project | Latest reached | Result | Engine seconds | Completed engine | Accepted forward report |
|---|---|---|---:|---|---|
| OWASP | run-1, forward, limit 1 | TIMEOUT | 908.766892375 | None | None |
| conductor | run-1, forward, limit 1 | TIMEOUT | 986.838065185 | None | None |
| thingsboard | run-1, forward, limit 1 | TIMEOUT | 700.235073167 | None | None |
| Stirling-PDF | run-1, forward, limit 1 | TIMEOUT | 1026.864846817 | None | None |

All eight reference and candidate scans are closed.
The final input guard passed.
All four candidates failed the run-1 target.
None reached a complete engine or an accepted forward report.
The candidate result is TIMEOUT in each case, not OOM.
Empty exported reports do not prove absence of findings.
Strict finding comparisons must retain these resource limits.

This proposal changes analyzer-core work and its rule adapters.
IR storage is outside its scope.
It does not change the reference manager or add a weak cache.
It does not change the selected empty end-action policy.
It keeps full rule/source identities and the limits `[1,1,2,3,...]`.

The main code targets are
[Run1Grounding.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1Grounding.kt),
[RunMethodAnalyzer.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt),
[MarkExclusions.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/MarkExclusions.kt),
[RuleMarkCatalog.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/RuleMarkCatalog.kt)
and [Filter.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Filter.kt).
The code below is pseudocode for the proposed contracts.

## Evidence and limits

The new priority is guard comparison and guard eligibility.
The closed Stirling-PDF normal CPU window has 14,716 weighted samples.
It covers 30 seconds, after run-1 starts.

- `GroundingSupport` or `GroundingRequirement` class frames occur on
  6,949 samples, or **47.22%**. This is an inclusive class union.
  Some cost is in called AP, mark or payload code.
- A broader substring match gives 11,124 samples, or **75.59%**.
  It includes wrappers such as `withGroundingSupport`.
  It is not a fraction spent only in guard algebra.
- Some stacks contain **1,024 nested `Or.equals` frames** at a recorded
  depth of 2,048. This measures an executing comparison.
  It does not count unique guard objects or prove an infinite loop.

The source also admits this recurrence:

```text
S0 = A
S(n+1) = S(n) OR (S(n) AND B)
```

All terms have the Boolean meaning `A`.
Their structures differ under the current constructors.
A finite AP domain does not bound that structural growth.
The [termination investigation](../reports/bidirectional-grouped-statics-termination-investigation-20261019.md)
now reproduces the feedback in the production method actor.
One group and one loop-header fact suffice, including a legal nonzero
initial with fixed-image certificates. Ten diagnostic methods pass.
They characterize the bug; they do not assert the desired quiescence.
A scoped constructive Lean model proves strict syntax growth for the
selected smart-constructor recurrence at every finite depth.
The exact loop has not been identified in a named project.
A large finite set of real alternatives can also cause deep comparisons.

The following terminal counters provide other evidence.
Each row refers to the stated **run-1** timeout prefix.

| Measurement | OWASP run-1 | conductor run-1 |
|---|---:|---:|
| Request offer calls | 30,026,921 | 3,688,500 |
| Offered request members | 30,040,101 | 3,722,858 |
| Duplicate members in temporary requests | 29,967,774 | 2,323,902 |
| Duplicate members in summary requests | 25,539 | 1,361,499 |
| Fresh temporary request members | 46,788 | 37,457 |
| TRUE cofactor rows | 1,934,330 | 61,555 |
| Conditional or certified cofactor rows | 424,585 | 1,176,171 |
| Cumulative predicate proof records | 2,314,878 | 825 |
| Static candidate visits | 0 | 37,310,934 |
| Static write / supplied regions | 0 / 0 | 1,217 / 1,205 |

A cofactor is one accepted position and layer with its support.
Support states which inputs must hold for that accepted value.
A fixed-image certificate also protects the geometry of a generated result.
TRUE and conditional rows are separate populations.
Do not describe all 2,358,915 OWASP rows as conditional or removable.
Their share used only for unchanged transport is unknown.

OWASP duplicate members are 99.84425% of offered members.
Temporary duplicates alone are 99.75923%.
Use member counts for both numerator and denominator.
The earlier 99.803% ratio mixes member counts and call counts.

The OWASP normal CPU window has 11,530 weighted samples.
Mark-exclusion frames occur on 1,838 samples (15.94%).
Normalization frames occur on 1,547 samples (13.42%).
These inclusive CPU families overlap.
They are not full-run CPU fractions.

The expanded JFR review uses recorded stacks up to 256 frames.
The sampled run-1 allocation estimates are 2.344 TB for OWASP,
2.161 TB for the retained conductor suffix, 0.782 TB for ThingsBoard,
and 0.689 TB for Stirling-PDF. TB uses decimal bytes here.
These are allocation weights, not live or retained heap.
Conductor retains only the last 174 seconds of its 986.838-second engine.
Its census and early run-1 events were removed by the recording limit.

| Inclusive allocation family | OWASP run-1 | conductor run-1 suffix | ThingsBoard run-1 | Stirling-PDF run-1 |
| --- | ---: | ---: | ---: | ---: |
| Exact normalizer methods | 76.36% | 1.12% | 32.17% | 8.52% |
| Support and requirement classes | 0.04% | 87.70% | 1.72% | 52.76% |

Families overlap. The percentages are not removable shares or speedups.
All sampled conductor Trie equality allocation has captured support frames
and an outward `GroundingRequirement.equals` parent.
This assigns that retained-suffix cost to guard comparison.
Stirling-PDF has 51,926 of 98,610 run-1 allocation traces truncated at
256 frames; missing outer frames limit its attribution.
No heap ownership census was taken because there was no OOM.
See the report and its `profile-review/consumer-v3/` evidence.

No current heap ownership analysis is available.
Current retained bytes and avoidable retained bytes are unknown.
Full-stack allocation samples now confirm large guard and normalization costs.
No speedup factor or byte saving is claimed below.
The CPU percentages cannot be added or converted into promised speedup.

OWASP has no static write or group supply in this prefix.
Global static supply is not its timeout cause.
Synthetic matcher-control state can use that pool on other workloads.
Do not infer its presence or cost from group counts alone.

## Priority and expected impact

The order uses the latest CPU and allocation evidence.
It prioritizes removal of repeated work before larger storage changes.
Expected impact describes a mechanism, not a measured saving.
The retained-memory ranking is provisional because owner bytes are unknown.

| Rank | Change | Expected impact | Confidence and remaining limit |
|---:|---|---|---|
| 1 | Restore finite support convergence, then reduce guard cost | Stop support-only work after semantic closure; reduce redundant guard state | Production core cycle with one group/fact; major sampled support cost; project loop identities unknown |
| 2 | Reuse canonical marks and current group-member lists | High allocation target; remove repeated normalization and whole-catalog scans | OWASP normalizer 76.36%; ThingsBoard normalizer 32.17%; families overlap |
| 3 | Keep unchanged guarded transport temporary | Possible removal of persistent transport-only rows | Potentially large memory scope; avoidable row share unknown |
| 4 | Check existing request membership before allocation | Fewer temporary request sets and rejected admission steps | Exact duplicate population; allocation cost per offer unknown |
| 5 | Avoid repeated direct-static joins | Fewer repeated joins and temporary binding results | conductor has measured visits; repeated share unknown |
| 6 | Reuse prescan ownership metadata | Less setup and repeated rule metadata work | Setup times measured; not a direct run-1 engine-time saving |
| 7 | Share group calculations with full proof aliases | Fewer repeated checks and unary AP calculations | Conditional on active alias multiplicity and allocation evidence |

## 1. Restore support convergence and reduce guard cost

### 1.0 Correct semantic progress before comparison speed

**Current behavior.** The fact store has a finite AP domain, but guard
syntax has no such bound. A marked-copy loop can form
`S = S OR (S AND B)` forever. The fact, group and semantic guard vocabulary
do not change. Structural inequality keeps scheduling more work.
This is now an executed core witness, rather than only a source hypothesis.

**Required behavior.** Emit support-only work only for a real new logical
or fixed-image alternative. Keep those two components separate.

```text
no new support iff
  incoming.logical implies stored.logical
  AND incoming.fixed implies stored.fixed
```

This condition preserves real certificate growth and new alternatives.
Never suppress all support-only deltas just because the AP value is old.
Do not weaken grounded writer checks, original ND bindings or proof identities.

A general correction needs a finite semantic support representation or
a finite keyed dependency graph with a proved solving procedure.
It must not unfold derivation history on each CFG cycle.
This choice needs a separate detailed design and constructive refinement
proof. No general representation is selected or implemented here.
Local absorption and exact ZERO folding close specific families; they
are not a proof that every support cycle terminates.
The executed nonzero case rules out ZERO folding as a complete correction.

The technical steps below can reduce the cost of exact operations.
They come after the semantic-progress design.
Faster equality and hash interning alone cannot close an unbounded sequence
of different expressions. Keep the new actor diagnostics and convert
them to quiescence regression tests when the correction is implemented.

This package has four separate steps.
Keep their proofs and measurements separate.
First make exact operations safe on deep shared graphs.
Then change the eligibility key and its accepted scalar payload key.
Then add the targeted constant and absorption laws.

### 1.1 Make exact structural operations avoid repeated walks

**Current behavior.** Guard nodes use recursive data-class equality and hash.
Containment also walks expressions recursively.
`rebind` memoizes outputs in a regular hash map.
Map access can therefore pay the recursive hash and equality cost again.

```kotlin
val mapped = HashMap<GroundingSupport, GroundingSupport>()
fun map(node: GroundingSupport): GroundingSupport = mapped.getOrPut(node) {
    recursivelyMapChildren(node)
}

fun contains(a: Support, b: Support): Boolean =
    a == b || (a is Or && (contains(a.left, b) || contains(a.right, b)))
```

**Proposed behavior.** Freeze all support-node and atom payloads.
Cache an exact structural hash on each immutable node.
A hash match is only a filter. It is not proof of equality.
Use operation-local identity memoization in `rebind`.
Use an explicit work stack for equality and containment.
Visit a node pair once in one comparison.
Compare immutable requirement lists by indexed element access.
Avoid constructing list iterators inside `Row.equals`.
Preserve the existing ordered-list equality in this first step.

```kotlin
class SupportNode(/* owned immutable fields */) {
    val structuralHash = hashFromCachedChildrenAndAtomFields()
}

fun rebind(root: SupportNode): SupportNode {
    val done = IdentityHashMap<SupportNode, SupportNode>()
    return mapPostorderWithExplicitStack(root, done)
}

fun exactEquals(a: SupportNode, b: SupportNode): Boolean {
    if (a === b) return true
    if (a.structuralHash != b.structuralHash) return false
    return compareWithExplicitStackAndIdentityPairMemo(a, b)
}
```

Keep the current exact comparison result in this step.
Do not use a hash collision as equality.
Use periodic cancellation checks in every work loop.
Release the memo tables when the operation ends.
Do not create a global node interner or expression history.

For graphs with `Va` and `Vb` nodes, the pair set has at most `Va*Vb` pairs.
This is not a general linear-time bound.
Tests must count visited pairs and check shared-graph reuse.
Do not return TRUE or FALSE because a work budget expires.
A bounded continuation must preserve the unfinished exact comparison.

This step targets the observed deep comparison path.
It does not remove semantically equal syntax or prove fixed-point convergence.
A cached scalar can also increase each node's size.
Net memory effect needs measurement.

### 1.2 Use a key for eligibility, separate from full rule proofs

**Current behavior.** `GroundingRequirement` includes `proof` and
`leafOrdinal` in structural equality.
It also retains the original full literal.
The eligibility consumers do not inspect those two annotation fields.
They check the original inputs, accepted geometry, literal and relation.
In run-1, they project the literal mark to its group during checking.

```kotlin
// Current annotation fields participate in the data-class key.
GroundingRequirement(original, accepted, fullLiteral,
                     proof, leafOrdinal, relationInitial)
```

**Proposed behavior.** Give run-1 eligibility its own scoped key.
Omit only `proof` and `leafOrdinal` from this key.
Project only the literal's selected mark through GROUP mode.
Keep all other fields exact.

```kotlin
data class EligibilityKey(
    val scope: ExactEvaluationScope,
    val original: PremiseKey,       // complete ND tuple
    val accepted: Facts,           // base/path/tail/layer/marks/E/mx unchanged
    val literal: Pattern,          // only selected mark projects to GROUP
    val relationInitial: InitialAp?,
)

fun key(row: GroundingRequirement, scope: ExactEvaluationScope) =
    EligibilityKey(scope, row.original, row.accepted,
        row.literal.withOnlySelectedMarkProjectedToGroup(),
        row.relationInitial)
```

The scope fixes the AP manager, catalog and GROUP execution mode.
It also qualifies the owner/context and the relation-evaluation state.
Either include this scope or enforce it through local ownership.
The key describes a Boolean function of a complete actual input binding.
It does not contain or certify an unknown future binding.
Evaluate truth separately for each actual caller/context/stage tuple.
Any temporary truth memo must include that tuple or end with that evaluation.
Never cache truth globally by geometry and group alone.
Keep control ZERO as ZERO when projecting the literal mark.

Keep the full `RulePredicate`, signed NNF and original leaf aliases.
Keep `ConjunctionStore` literal/ordinal keys and real join identity.
Keep sink `ProofKey`, alternative, occurrence and full witness observations.
Keep every `SourceActionId`, source occurrence, source hit and later seed.
Do not use eligibility equality to merge these observations.

Within the same complete binding, two equal keys have the same truth.
`rebind` must preserve that equivalence through the same actual arguments.
Use the caller's qualified scope for the rewritten eligibility rows.
Compute cached hashes from the key used by that representation.
Equal eligibility keys must have equal hashes after annotations are omitted.
It must still use the same stage-specific FLOW relation.
Different `relationInitial`, AP fields, layers, exclusions or ND inputs
must remain distinct.

Logical support and fixed-image support use separate formulas.
They can each use the eligibility key.
They cannot be replaced by one Boolean value.

This can remove eligibility alternatives that differ only by annotations
or same-group full literal names.
The duplicate population and retained-byte effect are not measured.
It does not remove distinct semantic requirements.
It does not remove all absorbing formula families.

### 1.3 Compare accepted scalar leaves without tree equality

**Current behavior.** `rows()` enumerates semantic leaves, then rebuilds
one `Facts` value for each requirement. Data-class equality compares the
accepted tree. The conductor suffix shows that this path allocates
inside tree equality. Some other constructors accept packed facts.
The single-leaf property does not hold for every requirement today.

**Proposed behavior.** Use a lossless key for a normalized scalar leaf.
Keep the accepted payload for AP operations. Exclude it from scalar-key
equality only when the private factory proves it has one semantic leaf.
Retain exact packed-payload equality as an explicit fallback.

```kotlin
// Pseudocode. Fact payloads do not participate in Leaf equality/hash.
sealed interface AcceptedKey {
    data class Leaf(val pattern: Pattern, val layer: Layer) : AcceptedKey
    data class Packed(val exactFacts: Facts) : AcceptedKey
}
class AcceptedPayload private constructor(val key: AcceptedKey, val facts: Facts)

fun acceptedPayload(facts: Facts): AcceptedPayload {
    if (!hasCanonicalScalarRepresentation(facts)) return exactPackedPayload(facts)
    return when (val view = inspectLeafCount(facts)) {
        is ExactlyOne -> checkedLeafPayload(view, facts)
        else -> exactPackedPayload(facts)
    }
}
```

The leaf pattern keeps the full base, path, tail, mark, field exclusion
and FLOW mark exclusions. The layer is the actual normalized layer.
For example, normalization can turn an ANY/NORMAL input into DEMAND.
Do not key it by the requested input layer.
Keep the original payload unchanged. `rows()` already builds its accepted
payload through `factsOf`; inspect that actual result for the scalar key.
Do not add normalization to arbitrary direct or packed requirements.
Fall back to the original exact `Facts` key for an unproved representation.
Do not take the first leaf of a packed value.
Do not change general `TrieNode.equals` in this proposal.

Use `AcceptedKey` in the scoped eligibility key of section 1.2.
Prove that equal scalar keys reconstruct equal semantic accepted facts
and give equal checks, relation application and rebind results.
Test packed ND combinations, exact/ANY/STAR tails, masks, layer changes,
different fields and normalized unused-tail values.
The packed fallback must keep the current exact result.
This removes a measured representation cost without changing full proof
identities. The share that uses the scalar factory still needs measurement.

### 1.4 Fold exact ZERO requirements and add targeted absorption

**Current behavior.** A requirement with original ZERO is stored as a row.
`permits` later checks its immutable accepted facts against its literal.
It does not read an external input binding for that row.
Header merges can also construct equivalent larger support formulas.

```kotlin
if (row.original.isZero) {
    return checkMark(row.accepted, row.literal, currentMode) is Holds
}

next = old.or(old.and(B)) // structurally new, same Boolean meaning
```

**Proposed behavior.** Fold that exact ZERO row to its checked truth.
Do this only for the fixed catalog and GROUP mode covered by the proof.
Keep the inherited support.
Add exact absorption laws inside each Boolean component.
Compose the support pair first, then simplify its two component formulas.

```kotlin
fun eligibility(row: Requirement, inherited: Support): Support {
    val own = if (row.original.isZero)
        constant(exactCurrentCheck(row.accepted, row.literal, GROUP))
    else atom(scopedEligibilityKey(row))
    return inherited.and(own)
}

// a,b are Boolean component formulas, not whole WithFixed values.
boolOr(a, boolAnd(a, b)) == a
boolAnd(a, boolOr(a, b)) == a

fun simplifySupport(pair: Support): Support = withFixed(
    simplifyBoolean(pair.logical),
    simplifyBoolean(pair.fixedImageSupport))
```

ZERO alone is not permission to return TRUE.
A mismatch must remain false.
Do not fold the whole inherited guard to TRUE.
Do not erase fixed-image evidence:

```kotlin
WithFixed(logical = TRUE, fixedImageSupport = A)
    .certified() // must still require A
```

Whole-support absorption is invalid under the certificate algebra.
For `A = plain TRUE = (TRUE,FALSE)` and
`B = WithFixed(TRUE,TRUE)`, `A OR (A AND B)` has a TRUE fixed-image
certificate. Replacing the whole value with `A` would erase it.
Keep this case as a negative control.

The actual core investigation now reproduces this family:

```java
x = sourceT();
y = gate(x);       // gate is a retained conditional source
while (choice()) {
    y = gate(y);   // separate source occurrence
}
sinkT(y);
```

Keep AP, group, original input and occurrence identities fixed.
The current tests observe stable AP state and continuing syntax growth.
The corrected implementation must instead reach bounded quiescence.
Retain the exact nonzero/certificate case and extend it through real FLOW
and writer adapters.

Local ZERO folding and absorption do not establish general convergence.
Hash caching, identity memoization and hash-consing do not establish it either.
A general finite fixed-point claim needs a separate representation and proof.
No general canonical Boolean representation is selected here.

### Guard package proof and test gates

- Exact hash caching must preserve equality, including forced collisions.
  Test deep chains and shared graphs without recursive stack use.
- Compare the new exact operations with an independent small-tree reference.
  Check cancellation and per-operation memo release.
- Prove eligibility truth and `rebind` congruence in one complete binding.
  Keep negative tests for wrong field, context, stage and ND tuple.
- Prove GROUP literal projection only for eligibility.
  Sibling full marks must remain distinct in FULL execution.
- Prove the exact ZERO check commutes with `rebind` and projection.
  Keep mismatch and inherited-guard negative controls.
- Test `WithFixed(TRUE,A)`, certificate combinations and real late alternatives.
  A new eligibility alternative must still propagate without an AP delta.
- Preserve two equal-output source actions and their exact later seeds.
  Preserve every original source/sink proof observation.
- Convert the reproduced guarded loop into a quiescence regression.
  Do not claim it caused the sampled timeout from source reasoning alone.

See `Stirling-PDF-grounding-native-review.json`,
`grounding-eligibility-key-review.json`, and
`grounding-growth-source-review.json`.

## 2. Reuse canonical marks and current group-member lists

### 2.1 Normalize and project exclusions once

**Current behavior.** Equivalent immutable exclusions can be normalized again.
Repeated GROUP projection can construct the same symbolic value.
Empty and identical set operations can enter construction paths.

```kotlin
val attached = normalize(exclusions, catalog)
val grouped = projectToGroups(attached)
val next = normalize(grouped union more, catalog)
```

**Proposed behavior.** Return an already canonical same-catalog value.
Make group projection idempotent.
Apply safe empty and identity cases before set construction.

```kotlin
fun MarkExclusions.withCatalog(c: MarkTable): MarkExclusions {
    checkCatalogCompatibility(c)
    if (catalog === c && isCanonical) return this
    return normalizeOnce(c)
}

fun projectGroups(x: MarkExclusions): MarkExclusions {
    if (x.isCanonicalGroupValue) return x
    return projectOnce(x)
}
```

Check catalog compatibility before every shortcut.
Keep symbolic group exclusions in FULL runs.
Do not expand them to the current full-member inventory.
Do not infer request coverage from an excluded group.
Add no global transition cache.

This targets repeated work on a sampled CPU and allocation path.
Its avoidable allocation share and retained-byte effect are not measured.

**Gates.** Prove attachment identity and projection idempotence.
Prove symbolic union, intersection and subset laws.
Test mixed catalogs, ZERO and distinct group/full selectors.
Keep AP geometry, tails, layers, requests and old record behavior unchanged.
See `mark-exclusions-source-profile-review.json`.

### 2.2 Reuse atoms and sets that are already in GROUP form

**Current behavior.** `runtimeAtom(GROUP)` calls `groupOf()` and then
`group()`, even for an existing group atom. This constructs wrapper keys
and can box IDs. `runtimeMarks` also maps and interns an already grouped
immutable set again.

**Proposed behavior.** Return the same handle or immutable set when its
runtime form is already correct. Keep FULL validation and control ZERO.

```kotlin
fun runtimeAtom(atom: TaintMark, mode: MarkMode): TaintMark {
    if (atom == ZERO) return atom
    if (mode == FULL) {
        require(!isGroup(atom))
        return atom
    }
    if (isGroup(atom)) return atom
    return group(groupOf(atom))
}

fun runtimeMarks(set: MarkSet, mode: MarkMode): MarkSet {
    if (mode == GROUP && set.all { it == ZERO || marks.isGroup(it) }) return set
    return existingCheckedProjection(set, mode)
}
```

Use the existing synchronized catalog access. Validate atom ownership
under the same contract as the original API. This adds no transition
cache or weak cache. Preserve immutable ownership of every returned set.
Prove value equality and projection idempotence. Test mixed full/group
sets, ZERO, empty sets and FULL rejection of executable group handles.

### 2.3 Index current eligible members by group

**Current behavior.** Every type-policy group query scans all eligible
full marks and allocates filtered and mapped lists.

```kotlin
@Synchronized fun eligibleMembers(group: GroupId): List<TaintMark> =
    eligible.filter { owner(it) == group }.map { fullAtoms[it]!! }

val members = eligibleMembers(group)
keep = members.isEmpty() || members.any { policy.keeps(it, level) }
```

**Proposed behavior.** Maintain immutable current lists under the existing
`MarkTable` lock. Return the current list without a whole-catalog scan.
Keep the policy check itself unchanged.

```kotlin
// State owned by MarkTable, not a history or a policy-result cache.
private var membersByGroup: List<List<TaintMark>> = emptyList()

@Synchronized fun finishOwnershipCensus() {
    assignMissingOwnersAsToday()
    membersByGroup = buildInGlobalEligibleInsertionOrder(eligible)
    isOwnershipCensusComplete = true
}

@Synchronized fun onEligibilityOrOwnerMutation(changed: FullMarkId) {
    if (!isOwnershipCensusComplete) return // initial build covers it
    updateAffectedCurrentSnapshotInEligibleOrder(changed)
}

@Synchronized fun eligibleMembers(group: GroupId): List<TaintMark> {
    if (eligible.isEmpty()) return emptyList()
    check(isOwnershipCensusComplete)
    return currentImmutableMembersOrEmptyForUnknownGroup(group)
}
```

The sketch shows responsibilities, not a second census or a changed
eligibility rule. Build or update the index only when membership changes.
Build the initial index from the private assigned-owner map under the
same lock. Public `owner()` rejects calls before census completion.
Preserve the existing empty result before census when no mark is eligible.
Also return empty for an unknown group ID after census; do not index an
array with an unchecked negative or out-of-range group ID.
Hook `observeModelReference`, `markEligible`, owner assignment for an
already eligible name, fixture `reserveFull`, and fixture finalization.
Production `reserveFull`, `register` and `declareOwner` alone must not
make an ineligible name eligible.
Eligibility can grow after the census. Do not assume a frozen inventory.

The list order must equal the existing global eligible insertion order.
An eligible mark can receive its owner after another mark. Appending by
owner-assignment time can reverse `[a,b]` into `[b,a]`.
Use the existing ordering or an explicit eligibility ordinal.
Publish a new immutable snapshot only for a changed group; keep no old
snapshot directory. A caller may hold its current snapshot transiently.

For `E` eligible marks and `G` groups, this adds `O(E+G)` current entries.
An unchanged lookup returns the current list in `O(1)` instead of scanning
`E` names. The remaining policy scan costs `O(members(group))`.
Late updates can cost `O(E)` until measurements justify a more complex
update index. Net retained-byte effect is unknown.
The initial shallow-export ThingsBoard run-1 summary attributes 108.37 GB,
or 13.85%, to `eligibleMembers`. The expanded family reader did not count
this method separately. This is a target population from the initial
estimate, not a recomputed full-stack total or promised byte savings.

**Gates.** Compare exact lists after every supported mutation sequence.
Test late names, late eligibility, fixture owners, duplicate registration,
conflicting owners, empty groups and both insertion orders.
An empty group currently passes policy filtering. Later members can change
the answer. Never cache the policy Boolean without a membership revision.
Use no policy truth cache in this proposal.
See `group-mark-idempotence-and-member-index-review.json`.

## 3. Keep unchanged guarded transport temporary

**Current behavior.** Plain TRUE transport has a temporary fast path.
Guarded or certified unchanged work can enter the persistent edge store.
An existing support directory can also make later TRUE transport enter it.

```kotlin
if (input.support != TRUE || hasSupportDirectory(input)) {
    addSupported(input) { enqueue(it) }
} else continueTemporaryBurst(input)
```

**Proposed behavior.** Carry exact support on temporary unchanged work.
Store support at actual changed-value and accepted-effect boundaries.
Retain the source, sink, request, call-input and writer checks.

```kotlin
fun forwardUnchanged(input: TaggedInput) {
    burst.push(input.node, input.premise, input.facts,
               input.support, input.fixedImageCertificate)
}

fun onChangedValueOrAcceptedEffect(input: TaggedInput) {
    addSupported(input) { enqueueOrPublish(it) }
}
```

The temporary repeat check includes geometry and complete support.
It must survive quantum yields for the same burst.
It must accept real support growth on an unchanged AP value.
It must not become a history of old facts or formulas.
Never replace missing support with TRUE.

This can remove persistent rows used only for unchanged transport.
Their number and byte ownership are unknown.
The measured cofactor population is not a removable-row estimate.
The guard-package proofs come first.

**Gates.** Classify unchanged-only and changed/effect row use.
Prove equal tagged closure and support unions at each accepted effect.
Test loops, quantum yields, late requests and complete ND bindings.
Keep wrong-field, wrong-context and uncertified-TRUE controls.
See `mark-exclusions-source-profile-review.json`.

## 4. Check request membership before allocation

**Current behavior.** A singleton can become a set before duplicate checks.
Batch admission can build a set that temporary storage then rejects.
Most measured OWASP offers add no temporary membership.

```kotlin
val offered = RequestMarkSet.of(mark)
val notInSummary = offered.minus(summaryRequests.borrow(slot))
val fresh = temporary.addAll(slot, notInSummary)
requestWork()
```

**Proposed behavior.** Check the two existing stores before construction.
Allocate a delta only for fresh members.
Keep existing summary requests and temporary requests.
Add no mirror or request cache.

```kotlin
fun collectRequest(slot: BaseSlot, mark: TaintMark) {
    checkpoint()
    if (mark in summaryRequests.borrow(slot)) return // R first
    if (mark in temporary.borrow(slot)) return
    temporary.addAll(slot, RequestMarkSet.of(mark))
    startPendingTimerIfUnset()
    requestWork()
}
```

For a batch with unique callbacks, test both stores in one pass.
Create the builder at the first fresh member.
Keep the existing path for a cursor that can repeat members.
The sketch omits reviewed counters and cancellation handling.
Keep them in the implementation.

Every required logical request is still offered.
The first fresh remote request must still reserve Work.
A duplicate must not reset the pending timer.
Successful commit must precede temporary-store replacement.
The method-empty barrier policy stays unchanged.

This avoids rejected set construction and admission work.
It does not remove AP transfer, cleaners or accepted fact rows.
Queued flags already reject duplicate Work reservations.
Do not claim fewer actual queue events from fewer scheduling attempts.

**Gates.** Compare memberships and completed-admission counters.
Test duplicates before and after commit, quantum yields and remote delivery.
Test cancellation, failed commit and notification ownership.
Do not use `mx` as proof that a request has been answered.
See `request-admission-design-review.json` and
`mx-request-coverage-hypothesis.json`.

## 5. Avoid repeated direct-static joins

**Current behavior.** The service uses positional indexes and dirty dependents.
It is not a full global cross product on every commit.
Repeated unchanged interests can still rescan old supplies.
Writer registration can repeat old binding work.
Different dependents can repeat an identical binding query.

```kotlin
row.wanted += interest.wanted
for (position in groupIndex.around(interest.region)) join(row, position)

for (dependent in dirty)
    for (binding in candidateBindings(dependent.owner, dependent.premise))
        applyOwnDeliveryCheck(dependent, binding)
```

**Proposed behavior.** Join new wanted groups only.
New supply still joins standing interests through the opposite index.
Register a writer once. Replay on real support growth.
Share exact binding enumeration inside one locked commit.

```kotlin
val freshWanted = incomingWanted - row.wanted
if (!freshWanted.isEmpty) {
    row.wanted += freshWanted
    joinExistingSupply(row, freshWanted)
}

for ((query, dependents) in dirty.groupByExactOwnerAndPremise()) {
    val bindings = candidateBindings(query.owner, query.premise)
    for (dependent in dependents)
        applyOwnRevisionGuardAndDeliveryChecks(dependent, bindings)
    // Drop this query's temporary bindings now.
}
```

Keep writer, continuation and qualified call-input rows separate.
Keep complete ND inputs within one caller/context/stage port.
Do not drop a real support-only input revision.
Keep no cross-commit binding cache or old input versions.

Conductor has 37,310,934 candidate visits in run-1.
At most 8,873,147 can be read/write-pair visits in this counter scope.
At least 28,437,787 belong to interest or binding enumeration.
That lower bound does not measure redundant visits.
Reply counts have different units and do not give a miss ratio.

**Gates.** Count new, growing and unchanged registrations separately.
Compare dirty dependents with distinct exact queries.
Prove closure for both arrival orders and support-only growth.
Test late supply, pending replies and per-port ND correlation.
Joining only changed port/member deltas is a larger later option.
See `direct-static-candidate-work-review.json`.

## 6. Reuse prescan ownership metadata

**Current behavior.** Census scans method bodies again after prescan.
It queries eight roles with two relevance settings per target.
Targets include bodies, declared calls and resolved call edges.
The observer also walks effective conditions and actions on later queries.

```kotlin
for (body in bodies) {
    censusMethod(body.method, body.first)
    for (call in body.declaredCalls) censusMethod(call.target, call.statement)
    for (read in body.staticReads) censusStaticSource(read)
}
for (call in resolvedCalls) censusMethod(call.callee, call.statement)
```

OWASP census takes 58.787882075 seconds.
Conductor census takes 68.588516295 seconds.
These are setup times, outside the 60-second engine target.
The resolved-edge passes alone make 161,264 and 452,240 role requests.
These are not fresh specialization counts.
The provider already caches method/role/relevance results.

**Proposed behavior.** Collect candidate references during prescan.
Retain unique targets under an explicit provider invariance contract.
Collect effective mark metadata when a binding is built.
Reuse it when the final catalog is installed.

```kotlin
prescanCandidates.observeDeclaredTarget(target, site)
prescanCandidates.observeResolvedTarget(target, site)
prescanCandidates.observeStaticSource(field, site)
onEffectiveBindingBuilt { effectiveMarks.add(it.activeMarkMetadata) }

catalog.installOwners(generatedMarkOwners)
catalog.observeEligible(effectiveMarks)
catalog.finishOwnershipCensus()
```

Keep selected and allRelevant roles.
Keep unresolved declared targets and static-field sources.
Ignored end-only marks must remain ineligible.
Do not merge a site-sensitive provider by method alone.
Keep explicit ownership and provider-extension registration.
Do not run GROUP mode before the census is complete.
Avoid a new identity cache that pins bound rules or IR.

This can remove repeated scans and metadata walks.
Current timing does not assign cost to each step.
Keep this below engine work until setup attribution confirms the work
that a metadata change can avoid.

**Gates.** Compare exact eligible names, owners and group membership.
Test joins, model fallback, conflicts and unresolved calls.
Keep every source/entry/exit/static/cleaner/rewriter role.
Test managed form rebuild and unchanged full handoff.
Count unique targets, repeated visits, cache misses and mark walks.
See `grouped-mark-census-source-analysis.json`.

## 7. Share group calculations and keep full proof aliases

**Current behavior.** One group can match several full leaves.
Each unary source leaf repeats every action calculation.
Sink and multi-source paths keep original proof alternatives.
The current proposal forbids deleting those alternatives.

```kotlin
for (fullLeaf in matchingOriginalLeaves(input)) {
    for (action in source.actions) {
        val checked = checkMark(input.facts, fullLeaf)
        val support = originalAliasGuard(fullLeaf, checked)
        applyOriginalSourceEdge(input.facts, fullLeaf, action, support)
    }
}
```

**Proposed behavior.** Add a GROUP execution index.
Use exact projected position and group as its key.
Keep base, complete path, tail and spatial exclusion.
Keep ZERO separate.
Retain each full ordinal and its unary/multi roles.

```kotlin
for (bucket in groupExecutionBuckets(input)) {
    val checked = checkMark(input.facts, bucket.representativeFullLiteral)
    feedOriginalMultiAndSinkAliases(bucket, checked)
    if (bucket.originalUnaryAliases.isEmpty()) continue
    for (action in source.actions) {
        val raw = applyPureUnaryTransferOnce(input.facts, bucket, action)
        collectActualRequests(raw.requests)
        for (alias in bucket.originalUnaryAliases)
            replayWithOriginalGuardAndActionId(alias, action.id, raw.results)
    }
}
```

Share a transfer only for the same original `SourceActionId`.
Do not merge occurrences or join operands.
Use original input Facts, not a checked slice.
Do not skip unary transfer on `None` or generate its requests from the check.
Today its AP edge remains the authority for requests and results.

Keep full proof traversal and multi-input joins unchanged.
Keep target geometry, filters and fixed-image evidence.
Share only inside the same exact operation input and cofactor.
FULL backward and later forward execution remain unchanged.
Full source-hit and seed identities remain exact.

For one bucket with `k` unary aliases and `a` actions,
AP calculations can change from `k*a` to `a`.
Original alias support remains.
Actual bucket multiplicity and allocation cost are not measured.
Selection is conditional on that evidence.
This does not remove the measured predicate-proof population.

**Gates.** Prove identical checks and pure GROUP transfers per bucket.
Compare exact full proof and source action observations after replay.
Test mixed unary/multi roles and equal targets with distinct actions.
Test paths/tails, split-call guards and signed complements.
Normalize full signed literals before projection.
`P(T) AND NOT P(T)` is false.
Do not fold `P(T) AND NOT P(U)` to false just because their groups match.
Test runs 1, 2 and 3 with limits `[1,1,2]`.
See `group-predicate-quotient-design-review.json`.

Removing full proof alternatives needs a later design.
It requires compact proof families and matching observation APIs.
The eligibility-key change in section 1 does not authorize that change.

## Proof, implementation and measurement plan

1. Use the closed CPU and expanded allocation evidence.
   Bind every result to source, capture interval and counter scope.
   Keep conductor suffix attribution separate from full-engine claims.
2. Use the reproduced core guard-growth cases as failing-behavior witnesses.
   Design finite semantic support progress before technical cost changes.
   Keep them separate from claims about any named project's recurrence.
3. Update the spec and proofs for the complete selected package.
   State the local scope and all preserved observations.
   Follow [proof-guide.md](../../spec/proof-guide.md) before runtime changes.
   Use constructive Lean models with executable finite witnesses.
   Model logical support and fixed-image certificates separately.
   For the selected semantic-progress design, prove a finite state bound
   and that an accepted change adds a real component alternative.
   Prove closure across transfer, storage, rebinding and publication.
   Prove exact DAG equality, hash consistency, containment and rebind
   refinement before their implementation. Include collision controls and
   the visited-pair bound. Keep the model-to-Kotlin adapter obligations
   explicit; the Lean model alone does not prove the runtime adapter.
   Prove the equality, rebind and truth invariants for each selected key
   quotient. Prove the scalar-key reconstruction law and packed fallback.
   For the mark package, prove projection idempotence and current-member
   index equality with the ordered-filter reference after every mutation.
   Refine the model when a counterexample breaks a proposed law.
   Run the constructive axiom audit with each model change.
   A small mirrored Boolean test is not a proof of the JVM implementation.
4. Implement the selected semantic-progress correction first, after its
   design and proofs are complete. Convert the reproduced cases into
   bounded-quiescence tests. Retain real new-alternative and certificate
   growth controls.
5. Implement exact structural cost changes, the scoped eligibility key
   and targeted laws as separate work.
   Test cached hashes, collisions, identity memoization and deep graphs.
   Test complete bindings, certificates and no-value-delta updates.
   Make no general convergence claim from local absorption.
6. Select further proposals using the new evidence and proof readiness.
   Keep each change reviewable. Run its independent scalar/protocol controls.
7. Run the full required core, JVM and source/action identity gates.
   Keep the strict full finding and negative controls.
8. Repeat only the four authorized project configurations.
   Record latest reached, latest complete engine and accepted report.
   Keep engine, setup and barrier times separate.
   Compare exact main finding identities and raw locations without filtering.

A passing core suite does not establish project completion or finding parity.
A timeout prefix does not establish a completed-work improvement.
No retained-byte or speedup estimate is established by this proposal.
The ranking can change after implementation and retained-memory measurements.

## Evidence files

- `comparison-acceptance-plan.json`.
- `Stirling-PDF-grounding-native-review.json`.
- `grounding-eligibility-key-review.json`.
- `grounding-growth-source-review.json`.
- `guard-accepted-leaf-key-review.json`.
- `mark-exclusions-source-profile-review.json`.
- `group-mark-idempotence-and-member-index-review.json`.
- `request-admission-design-review.json`.
- `mx-request-coverage-hypothesis.json`.
- `direct-static-candidate-work-review.json`.
- `grouped-mark-census-source-analysis.json`.
- `group-predicate-quotient-design-review.json`.

All evidence names above are relative to the external evidence root in the
[performance report](../reports/bidirectional-grouped-statics-performance-20261019.md),
under `reviews/`. Use the per-project manifest, summary and analyzer log
together. The final guard is `validation/benchmark-guard/equality.json`.
Full-stack allocation and native-family results are in
`profile-review/consumer-v3/<project>/attribution.json`.
The recording hashes and exact event ranges are retained there.
