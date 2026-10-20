# Reduce repeated GROUP work and static matching state

This earlier candidate list is superseded for next-iteration selection by
the [final selected proposal](bidirectional-run1-next-iteration-20261020.md).
Only its unchanged-GROUP normalization work is carried into that selection.
The other candidates below remain unselected. The semantic boundary below
describes this earlier proposal, not the selected enum hack and canonical
static-answer change.

Date: 20 October 2026. Status: proposal only. No runtime change in this file
is implemented or selected by publication of this proposal.
Use ASD-STE100 Simplified Technical English. Defined code names keep their meanings.
Follow [the proof guide](../../spec/proof-guide.md).

The implementation baseline is `029ee41fd6de17388c2f61d0f900cd00f6bd33f8`.
Keep [the current contract](../../spec/grouped-run1-statics.md) and
[immutable mark groups](../../spec/run1-group-policy-and-ownership-census.md).
The [fresh performance report](../reports/bidirectional-immutable-mark-groups-performance-20261020.md)
is the measurement authority. Earlier project reports are stale for this choice.

## Evidence and order

| Project | Latest reached | Latest complete engine | Latest accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-1, TIMEOUT | none | none |
| conductor | run-1, TIMEOUT | none | none |
| thingsboard | run-1, OOM | none | none |
| Stirling-PDF | run-2, OOM | run-1 | run-1 |

All four miss the 60-second run-1 engine target. Main completes all four.
No fresh B88 measurement exists. Do not claim that this study proves an
optimization speedup over B88 or that incomplete runs preserve every finding.

The priority order below combines run-1 memory risk and measured CPU cost.
It is not a predicted byte or speedup order. The final column gives the
information needed to make that prediction.

| Priority | Proposed change | Expected effect and evidence | Unmeasured part |
| --- | --- | --- | --- |
| 1 | Share all-static input binding admission by complete premise | Avoid repeated matching/index/delivery state within the 4.214 GB static service/point union in thingsboard run-1 | Number of writer endpoints per complete premise and bytes in their matching tables |
| 2 | Stop normalizing an immutable GROUP value when it cannot change | Avoid repeated set construction and catalog calls; OWASP run-1 normalization is 71.49% inclusive Java samples and 73.09% allocation weight | Redundant fraction and effect on total engine time |
| 3 | Coalesce equal complete continuations inside each call step | Avoid repeated downstream operations before method-store deduplication; exact source witness reduces quadratic work | Actual frequency and offer/unique ratio in these projects |
| 4 | Merge unpublished backward TAINT summaries before the existing barrier | Avoid retaining every publication delta until a busy method queue empties; PDF run-2 actor/caller owners contain 28.142 million wrappers | Exact pending-field partition, input-to-merged ratio, and run-1 benefit |
| 5 | Merge equivalent static repair points | Avoid point keys/continuations that differ only in ignored saved FLOW mx; thingsboard has 2.200 million point pairs | Number of keys per equivalent repair image |

The first, fourth and fifth entries target retained state. The second primarily
targets allocation and CPU; no large retained-heap saving is established.
The third can reduce both temporary work and repeated registration offers,
but the measured request duplicate rate is not its own fact duplicate rate.
Priorities 1 and 5 do not promise recovery of the full 4.214 GB union.

Thingsboard's ordinary request stores retain 11.9 MB, canonical AP vocabulary
1.3 MB, and MarkTable/declarations less than 0.5 MB. Its 42 used groups do
not bound method/site/output-qualified state: there are 3,010,244 WriterRows,
2,200,055 PointContinuations and 1,401,311 InterestRows. These are physical
objects, not distinct semantic facts. The owner unions overlap.

OWASP rejects 30,990,510 of 31,061,401 offered request memberships in the
existing temporary store. Request storage already merges. This supports
removing repeated work before admission; it does not justify another request
index or a new barrier. Conductor's native spin samples and MarkTable monitor
events support fewer repeated catalog calls. They do not justify attributing
all spin to MarkTable or selecting a lock-free rewrite.

Stirling-PDF's run-2 has a different ownership pattern. The separate report
records it. Items 1, 2, 3 and 5 target run-1 work; they do not prove a fix
for its FULL backward OOM. Item 4 is a separate backward publication change.

## Selected semantic boundary

**No major analysis semantic change is proposed.** Only equivalent local
work and state can be shared. Preserve source/sink admission, original
ActionIds and operation sites, signed FULL folds before GROUP projection,
condition compilation, field transfer and kills, empty run-1 E, layer-only
demotion, typed exclusions, complete ND premises, actual-query permissions,
static order/context approximation, request commit/replace/barriers, later
FULL transfer, primitive policy and export identities.

Keep all required late replies. An equal current result does not close a
subscription. Do not introduce GroundingSupport, field-sensitive writer
validation, a broader static pool, persistent fact history, reference-manager
changes or IR storage changes. Batch-dependent diagnostic callback counts
can vary under the existing observation contract. Source/sink identities and
rule occurrences must be preserved.

The proofs below are implementation requirements. The existing Lean model
does not certify these new optimizations. Construct and check the reference
and optimized models before runtime changes. If that work requires a new
semantic contract, state it and obtain the intended behavior first.

## Share all-static input binding admission by complete premise

### Current versus new

Current: identical complete all-static premises repeat the input index postings, candidate tuple construction, current group vector and delivered map for every owner/output registration.

New: one shared matching row holds that complete premise's input dependencies and current binding table. Keep the original lightweight owner/output endpoints. Send the same complete bindings to those endpoints. Every owner still runs its currentValues check and its ordinary transfer. Do not move a local source, condition evaluation, source identity, field operation, or mixed caller binding into the shared matcher.

This first scope shares matching/admission state. It does not yet share different output transfers or bypass owner validation.

### Example under analysis

```java
void A() { C.out = C.f; }
void B() { C.other = C.f; }
```

Use these ordinary AP summary descriptors as the example:

```text
Method A publishes:
  premise P = { S.C.f.* / Group(G) }
  result  S.C.out.* / Group(G), DEMAND

Method B publishes the same complete P:
  result  S.C.other.* / Group(G), DEMAND

The actual pool has:
  S.C.f.$ / Group(G)
```

`$` is an exact leaf. `*` is the preserved initial tail. These are separate owner summaries and separate output leaves. Their ordinary publication and source/action identity are not merged.

Current matching state:

```text
WriterRow(A, idA): delivered[tuple(C.f exact)] = [G]
WriterRow(B, idB): delivered[tuple(C.f exact)] = [G]
```

Proposed matching state:

```text
MatchRow(P): current[tuple(C.f exact)] = [G]
  endpoint(A,idA)
  endpoint(B,idB)
```

Both owners still check their exact current output selector. A emits only C.out/G. B emits only C.other/G. The common input does not authorize one output under the other owner's unrelated premise.

For an ND example:

```text
P = { C.left / G, C.right / H }
```

The matching row needs both members in their canonical ordered tuple. It must not emit either output when only C.left/G is present. A C.right/G supply does not stand for C.right/H. Growth of one member must meet every standing compatible complete tuple and the current groups of the other members. Do not reduce the key to field+group pairs or method-wide root bits.

### Proposed key and storage

```kotlin
// Pseudocode. No runtime changes were made.
// The containing object fixes this run, AP manager and GROUP mode.
data class StaticMatchKey(val completePremise: PremiseKey)
data class OwnerWriter(val owner: MethodKey, val id: Long)

class SharedMatchRow(val premise: PremiseKey) {
    // Same image and group-vector semantics as current WriterRow.delivered.
    val current = Map<OrderedRegionTuple, CurrentGroupVector>()
    val endpoints = OrderedSet<OwnerWriter>()
}

val matching = Map<StaticMatchKey, SharedMatchRow>()
val dependents = SpatialIndex<StaticMatchKey>()
// Ordinary owner writerKeys/writers and summary stores stay in their owners.
```

Use the complete canonical premise. Keep every initial's path, tail, mark group, E and tuple membership. The enclosing service must fix the AP manager and run; no numeric ID key may cross analyses. Only nonempty all-static premises enter this path. ZERO supplies, nonstatic FLOW writer interests, mixed ND premises and ordinary N2 joins keep their current paths.

The field-limit and GROUP mode are equal within this run. If this matcher is ever reused across modes/limits, those values must become part of its scope or key. This proposal does not reuse it across runs.

### Registration and update workflow

```kotlin
register(registration):
    require(nonemptyAllStatic(registration.premise))
    row = matching.getOrCreate(completePremise)
    if row was created:
        index every original premise member once
        mark row dirty for bootstrap against the existing pool
    if endpoints.add(original owner,id) succeeds:
        record it as a new endpoint for this commit
    // A repeated registration is not a new endpoint and must not bootstrap again.

commitPoolGrowth(changedRegion):
    mark the matching rows reached by the existing spatial index dirty

finishCommit():
    for each dirty row:
        // Preserve current candidateBindings exactly in the first implementation.
        // Full AP rechecking still runs at each owner after the lock is released.
        compute candidates once for the complete premise
        update each ordered image tuple's current group vector
        retain the genuinely fresh/grown tuples for this commit

    for each existing endpoint:
        queue WriterReady for this commit's fresh/grown complete tuples
    for each new endpoint:
        queue WriterReady for every current complete tuple
        // Do not also deliver that tuple through the existing-endpoint loop.
```

A new output or a new owner must receive all current matching bindings, even if the input memberships arrived earlier and are no longer a fresh delta. A new pool group must reach every existing endpoint. Process owner callbacks after releasing the service lock, as now.

Keep one current table per shared matcher, not a log of old group-vector versions. Keep only a temporary new-endpoint set and delta tuple set for one commit. A single-endpoint case can retain the existing inline/row representation or an equivalent small representation; do not require extra empty maps for every row merely to support possible future sharing. The final representation needs measurement, not a manager/cache rewrite.

### currentValues and replacement are essential

An old owner selector can become obsolete after ordinary summary canonicalization. The current owner deliberately checks currentValues before realizing a WriterReady. Shared matching must preserve that check. It must not replace it with an old copied Facts root.

The owner's original writer selector and output remain separate from the matcher key. A stale endpoint may still receive a harmless reply and stutter, as now. Initial sharing need not invent a withdrawal protocol or a permanent history of accepted input Facts.

A larger second scope could share an identical transfer under:

```text
(run/AP/mode/limit scope, complete premise, exact output Pattern, layer)
```

That includes output kind, base/path/tail, group or FLOW mx, field E, and layer. It must retain an accepted reached derivation. It also needs either a live owner-selector protocol or a proved monotone-summary replacement law that makes a prior transfer certificate valid after canonicalization. Neither follows just from equal field and group names. A first owner that stops matching does not prove that every equal owner transfer is obsolete. This larger transfer factoring is not needed for the bounded matching proposal and is not treated as design-complete here.

### Required proofs and controls

1. For a fixed complete premise, candidateBindings is independent of owner and output selector. State the exact same run/AP/mode scope.
2. Give an executable list-of-owner-rows reference and a shared-row model. Prove equal complete input binding notifications up to allowed duplicate callbacks, plus equal eventual owner-checked pool supplies.
3. Preserve register-before-supply, supply-before-register, late endpoints, group growth, and multiple new endpoints in one commit. Prove no missing old/new ND group products and no duplicate callback needed for progress.
4. Preserve currentValues replacement and stuttering. Test obsolete FLOW mx selectors, exact versus broad output tails, layer distinctions, and late new output selectors under an old premise.
5. Keep ordinary owner summaries, source/action occurrence permissions, sink observations, normal/FULL later analysis and exact AP transfer unchanged.
6. Keep callbacks outside the service lock, cancellation checks and complete tuple matching. Do not reuse one member's acceptance for another member.
7. Measure registrations, unique complete premises and binding table entries before predicting memory savings. Do not infer duplicate rates from the Q33 object counts.

Source/test anchors: `Run1StaticService.kt:61-79,104-115,142-154,205-228`; `Run1PointResume.kt:91-130,170-176`; `RunMethodAnalyzer.kt:268-298,510-532`; `DirectStaticServiceTest` all-static ND/both-arrival-order controls; `GroupedDirectStaticsEngineTest`; ordinary complete-ND and static pipeline suites. Add controls to these boundaries without weakening source/field/query semantics.


## Normalize an immutable value only when it changes


Current: runtime GROUP projection maps already-GROUP atoms and constructs
new sets. An attached normalized exclusion passes through normalization
again at nested output adapters. Private factories and immutable ownership
already provide the invariant needed for a same-catalog identity path.

New: use the typed atom key once; return an existing GROUP atom. Map a set
lazily and keep `intern(set)` for unchanged public inputs. Return an exclusion
already normalized for exactly the same catalog. Preserve every output
adapter, layer forcing, field cover and cut.

This avoids repeated scans/arrays/sets/interner work at existing boundaries.
It does not change group eligibility, declarations or primitive policy.

### Example

```text
cleanSelected(p, G)
input:  p.* FLOW DEMAND, E={}, mx={G}
output: p.* FLOW DEMAND, E={}, mx={G}
request: (original premise I, G)
```

For one current-source callback branch, the same value passes through eight
normalizer calls: add G, interning/results/factory attachment, AP run-1 output
projection, then actor output projection. A same-catalog attachment identity
reduces the source-derived count to three. An attached GROUP-only projection
identity leaves one explicit add normalization. These are counts for that
source branch, not measured project-wide calls or a speedup bound. The FLOW
request remains.

```kotlin
fun withCatalog(table: MarkTable): MarkExclusions {
    require(catalog == null || catalog === table)
    if (catalog === table) return this
    return normalized(atoms, table)
}
```

Keep the validation before the identity branch. Equal atom IDs from another
catalog do not justify identity. A null-catalog legacy value still needs its
first normalization. Keep symbolic GROUP exclusion of a later first-use FULL
member and the rule that a finite FULL set does not cover a GROUP.

For `projected(GROUP)`, an unchanged attached same-catalog value can return
itself after a typed scan proves no FULL atoms. Every other case uses the
existing mapping/normalization fallback. Existing foreign-catalog GROUP
projection currently remaps numeric atoms under the target table; do not add
a new rejection contract. The existing ApManager.runtimeMarks(FULL) and runtimeFact(FULL) helpers
are identity functions; do not turn them into new validators. In contrast,
MarkExclusions.projected(FULL) still calls withCatalog and must retain its
validation and first-attachment normalization. `runtimeAtom(FULL)` retains its GROUP
rejection.

Keep TAINT `mapLeaves` traversal in the first change. DEMAND is not a proof
that every leaf is GROUP. Whole-tree skipping needs a sound closure proof
and a separate design; do not retain a history of Facts as a closure cache.
FLOW/TAINT wrapper identity is permitted only after complete factory header
normalization, including tails, E, mx and static/group layer demotion.

### Proof and test gate

Constructively prove typed projection idempotence, same-catalog normal form
and denotation equality, including delayed first use. Compare new set/Facts
projection to an independent old map/deduplicate/normalize reference. Cover
mixed FULL/GROUP/ZERO, noncanonical equal sets, incompatible catalogs and the
unchanged foreign GROUP fallback. Cover FULL values on DEMAND facts, static
premises with nonstatic results, packed exact/any siblings, factory E/tail
normalization, resumed/static/ND paths and existing requests. Preserve
concurrent equal-key interning. Test work reduction on the scoped nested
callback, independently of whole-project measurements.


## Coalesce equal facts inside a call


Current: each cleaner step and call stage appends results to a raw list.
Equal results repeat the downstream suffix before MethodEdgeStore rejects
equal semantic results.

New: keep the first equal complete continuation value at each selected local
boundary. Keep each original producer and its action/operation observations.
Run the downstream suffix once for each distinct continuation value.

This avoids work before request production, projection, cuts and final
storage. It does not require a new persistent store or mark representation.
The expected whole-project benefit depends on the measured duplicate rate.

### Code and facts under analysis

Consider one call with these admitted selected cleaner actions:

```text
clean(p)
  action 1: RemoveMark(T1, argument0.f)
  action 2: RemoveMark(T2, argument0.f)
  action 3: RemoveMark(T3, argument0.f)
  owner(T1) = owner(T2) = owner(T3) = G
```

Each field action retains its read/clean/write lowering and original action
identity. Let I be the same complete nonstatic original premise. In run-1:

```text
r0 = (I, p.*       FLOW NORMAL, E={}, mx={})
r  = (I, p.*       FLOW DEMAND, E={}, mx={})
a  = (I, p.f.[any] FLOW DEMAND, E={}, mx={G})

first field cleaner: r0 -> [r, a]
later field cleaner: r  -> [r, a]
                     a  -> [a]
```

These are conclusion facts. `[any]` is not an initial fact. The existing
exclusion-to-DEMAND rule applies to r. The strong write kills the retained
p.f branch from a and writes back one equal a. Field exclusions stay empty.

```text
Current after step 1: [r, a]
Current after step 2: [r, a, a]
Current after step 3: [r, a, a, a]

New after every step: [r, a]
```

For k steps this witness has k(k+1)/2 input visits now and 2k-1 visits after
exact coalescing. Raw list growth is linear; work is quadratic. This is not
an exponential-growth or nontermination claim.

### Implementation boundary

Start with completed cleaner-step outputs and completed applyStage outputs
in `RunMethodCallServices`. Do not merge different continuations.

```kotlin
data class ContinuationKey(
    val premise: PremiseKey,
    val facts: Facts,
    val identityCandidate: Boolean?,
)

class LocalBatch {
    private val values = LinkedHashMap<ContinuationKey, PlanFact>()
    fun offer(p: PremiseKey, f: Facts, identity: Boolean?) {
        cancellation.checkpoint()
        val key = ContinuationKey(p, f, identity)
        if (key !in values) values[key] = planFact(p, f, identity)
    }
}

// Each original producer still executes its observations before offer.
val next = LocalBatch()
for (input in current) executeStep(input) { next.offer(it.premise, it.facts, it.identityCandidate) }
current = next.valuesInFirstSeenOrder()
```

The local scope fixes method actor, call, direction, form, exact stage target
and CallPoint, step/phase, trigger state, callerLayer and pendingDemotion.
Keep native-demotion and resumed-phase
scopes separate. Use the complete canonical premise, including all ND
members, and exact Facts equality. Preserve NORMAL/DEMAND, tails, E, mx,
whole tries and nullable identityCandidate. PlanFact currently has reference
equality, so `distinct()` on PlanFact is insufficient.

Do not deduplicate source ActionIds or static operation registrations by
their outputs. Source permission/hit and static read/write/MarkInput hooks
must run at their original points. The selected FLOW cleaner still requests
G when G is already excluded. Only repeated continuation values are removed.
Preserve first-seen order and cancellation checks. Diagnostic callback counts
can change under the existing observation contract.

Keep all standing saved-point, read/writer and call-interest registrations,
their resume state, wanted-group unions and late-reply behavior. Equal current
outputs do not make a subscription complete. Do not coalesce resumeQueue
events across invocations or retain a global seen set. A later supply must
still activate every original operation route that it activated before.

### Proof and test gate

Before implementation, construct independent list and exact-set transition
models in Lean. Prove equal reachable complete continuation values and equal
semantic observation sets, plus equivalent standing registrations and late
reply transitions. Final result sets alone are insufficient. Model producer
action observations separately from continuation values. Prove the r/a
relation from the current AP rows
and the scoped work bounds above. Do not claim general JVM termination.

Use real JVM field-cleaner lowering and two distinct source actions that
produce equal GROUP results. Require both source ActionIds. Compare all
facts, requests, static registrations, source/sink occurrences and summaries.
Negative controls must retain unequal premises/ND tuples, identity flags,
layers, tails, paths, E/mx, caller bindings and phases. Preserve remove-all
kills, field kills, aliases, static resume and native demotion.

Add a late-supply control in which two distinct producer operation sites
emit equal continuation facts. Both saved routes must remain registered and
must receive their corresponding later supply. No original operation site is
removed by downstream fact coalescing.
Also test repeated equal registration at one site: wanted groups must union
and delivered-state deduplication must keep its existing behavior.

State the observation theorem for the selected production services and their
idempotent semantic observations. Arbitrary injected callbacks such as onCut
can count invocations or trigger cancellation; exact arbitrary callback
sequences are outside this theorem. Preserve the existing cancellation checks
and prompt cancellation. Do not use this scope limit to remove production
source, sink, request or static-route observations.

Add bounded offer/unique counters only at selected local boundaries. Report
their ratio and input-visit counts. Prove no persistent fact history is added.


## Merge unpublished backward TAINT summaries before the existing barrier

Current: each fresh selected summary piece appends a Publication wrapper to
an actor list. A quantum yield preserves the whole list. The actor flushes
it only when its fact queue is empty. The destination PublicationIndex merges
later. A long-busy method can retain many earlier deltas before that merge.

New: in a backward run, merge selected TAINT and REACH pieces in an actor-owned
temporary map as they arrive. Keep every FLOW and forward publication on its
existing ordered path. Keep the same queue-empty publication barrier. Construct wrappers
only for the detached current merged rows at flush. Do not publish earlier
or change the request barrier.

PDF's actual run-2 heap contains 28,194,786 Publication objects. Its actor and
caller retained union contains 28,141,646; the queue-owner selection contains
314, and the merged PublicationIndex selection contains none. Source shows
the actor pending list and detached delivery batches as retaining paths.
The sampled headers do not give complete pending-list occupancy. Do not
claim that every wrapper is pending, duplicate or recoverable. Source and
heap justify the proposed early merge; they do not quantify its saving.

### Example and storage

```text
// Code under analysis
loadPair(p) { return pair(p.left, p.right); }

// Two summary growth events with the SAME complete premise I:
I = return / Full(G,T)
I -> parameter0.left / Full(G,T), DEMAND
I -> parameter0.right / Full(G,T), DEMAND
```

For this backward example, use I as the complete return-mark premise and
L/R as the parameter-field results selected by the current published/restrict
path. The field reads and transfers are unchanged.

```text
Current pending list: [Publication(I,L), Publication(I,R)]
New pending map:      I -> ConclusionGroup containing the union of L and R
```

The map can contain several rows for different bases, layers and TAINT
headers. FLOW pieces use the original ordered path. It does not reduce an ND premise to its members or merge
unrelated original I values.

```kotlin
// Pseudocode. Reuse ordinary ConclusionGroup union and private store interners.
class PendingPublications(private val ap: ApManager) {
    private val interners = ap.newInterners()
    private val rows = LinkedHashMap<PremiseKey, ConclusionGroup>()

    fun offerBackward(premise: PremiseKey, selected: Facts) {
        require(selected !is FlowTree)
        rows.getOrPut(premise) { ConclusionGroup(ap, interners) }.add(selected)
    }

    fun detach(): FrozenRows = freezeAndReplaceRows()
}

fun queuePublications(premise: PremiseKey, facts: Facts) {
    val selected = config.run1 || ops.demandPart(premise, facts, config.direction) != null
    for (piece in published(premise, facts)) {
        // Keep this action at its existing point and with its existing predicate.
        if (selected) summaries.addDemand(premise, piece)
        if (config.direction == BACKWARD && piece !is FlowTree)
            pending.offerBackward(premise, piece)
        else
            pending.appendUnchanged(premise, piece)
    }
    if (!pending.isEmpty) requestWork()
}

fun flushPublicationsAtExistingBarrier() {
    val detached = pending.detach()
    try { publicationSink.publish(key, detached.toPublications()) }
    catch (error: Throwable) {
        pending.restoreBeforeCurrent(detached) // preserve every detached/new row
        throw error
    }
}
```

Use the current exact selected predicate, complete premise and Facts union.
FrozenRows and the detach/restore helpers are pseudocode for owned batch
transfer, not a new permission stage. Keep first-seen premise order, cancellation checkpoints,
counted work and exception/cancellation behavior. Do not retain an eligible backward TAINT/REACH piece in both the old
append list and the new map. Do not add a positional index to this write-only
temporary store. The destination summary storage remains authoritative for
late subscriber replay.

### Proof and test gate

Model the original delta-list barrier and the merged-row barrier independently.
Prove equal publication denotation and equal selected semantic observations
for the production subscriber protocol. This needs union-compatible singleton
and complete ND application, not only equality of final store bytes. Keep
late subscribers, new demand admission, ZERO backward summaries, FULL marks,
layer distinctions and synchronous counted sends. Prove equal observable
backward caller behavior through summaryParts and all later stages. Do not
assume literal identity/effect-tag partition equality: a NORMAL TAINT join
can change header E and that partition. Reversed call stages clear the
MemoryEffect guard. The proof must show that the changed internal partition
has no observable backward effect, or restrict merging to a proved safe
subset. Keep the forward guard and all field/alias semantics unchanged.

Do not extend this change to NORMAL FLOW. A prefix with mx={G} can be tagged
as an effect, while a later empty-mx union can be tagged as identity. Replacing
both by the final FLOW changes alias-stage admission even if untagged fact
sets match. Preserve the existing ordered FLOW events. Any broader proposal
needs a separate tag-preservation design, not a change to alias semantics.

Test a method that yields at several quanta before its queue empties, new
summary pieces at each yield, mixed complete premises, identical rows,
packed siblings and the unchanged ordered FLOW fallback, including the
mx={G} then empty-mx counterexample. The old/new reference must give
equal actual summaries, caller continuations, source/sink occurrences and
late replay. Inject publish failure and preserve every detached/current row
without an early notification or a second persistent history.

Measure offered pieces, merged current rows, retained pending bytes and
maximum queue residence. First make a complete bounded pending-count census
for all 7,258 PDF RunMethodAnalyzer objects counted by complete Q01; do not extrapolate the 80 sampled headers. Keep the
same project budgets. This is a separate change and is not required for the
first run-1 static matching patch.

## Merge equivalent static repair points

### Current source equivalence

`PointKey(site, initial, image, layer, state)` uses a Pattern image. A FLOW image's mark slot includes its mx. Two otherwise equal points can therefore have separate keys, ids and continuations only because their FLOW mx differs.

On a PointReady reply, the current code constructs its relation as:

```kotlin
core.ap.flowTree(point.current.base, point.current.layer,
    point.current.exclusion, MarkExclusions.EMPTY,
    point.current.root, point.current.tail)
```

It deliberately clears mx before materialization. The request's query region is the same original.toPattern(). The ignored saved mx does not select pool membership or the saved continuation. The future supplied groups are still rooted writer/source groups.

This gives a narrow equivalence. It does not permit dropping mx from ordinary FLOW facts, writer outputs, the AP interner, or FULL analysis.

### Example under analysis

At exactly the same check/cleaner input site and saved call state:

```text
original I: S.C.f.* FLOW abstract initial
point A current image: x.* FLOW DEMAND, E={}, mx={G}
point B current image: x.* FLOW DEMAND, E={}, mx={H}
request at both points: Group(J)
```

Current: the image mark slots differ, so two point keys subscribe to the same original C.f query. When actual C.f/J arrives, both clear their saved mx, use the same exact ordinary relation, and install the same concrete original/result/site/state.

Proposed: both register one repair key. Union their wanted groups. J still needs an actual writer/source supply. The ordinary complementary FLOW edges with mx={G} and mx={H} stay distinct in ordinary AP storage. Only the saved DEMAND repair continuation is shared.

### Proposed key and workflow

```kotlin
fun repairImage(image: Pattern): Pattern {
    require(image.fact.mark is MarkSlot.Star)
    // This is exactly the erasure already performed on PointReady.
    return image.copy(fact = image.fact.copy(mark = MarkSlot.STAR))
}

request(at, groups):
    require(the existing static abstract-original/FLOW-input guards)
    for image in ordinaryLeafViews(at.facts):
        repaired = repairImage(image)
        key = PointKey(at.site, original, repaired, at.facts.layer, at.callState)
        id = existingOrCreate(key)
        retain the repaired image/one-leaf current relation for that exact site
        append StaticInterest(owner,id,original.toPattern(),groups)
```

The shared service already unions wanted groups for a repeated `(owner,id)`. Preserve that union and late membership joins. Do not replace a wanted group with an arbitrary group token. Do not assume all declared groups are requested or already supplied.

Keep site (including operation/form/stage/cleaner step/phase), complete original, image base/path/tail/E, layer and CallResumeState. CallResumeState includes callerLayer, nullable identityCandidate and pendingDemotion. Owner and AP manager stay fixed in this actor directory. Different sites, callers, current geometry, tails, layers or states do not share this key.

The proposal needs no history of old Facts. It can retain the already normalized repair image or reconstruct its one-leaf FlowTree. Do not add a second all-point memo beside the existing point directory.

### Required proofs and controls

1. Prove that PointReady materialization depends only on repairImage, original, site, layer/state, and the actual RegionGroups reply. Derive this from the ordinary applySummary API; do not assume group/field denotation alone implies an equal transfer.
2. Prove that quotienting point keys preserves wanted-group union and every late reply outcome up to semantic id/duplicate renaming. Preserve queued reply routing and owner lifetime.
3. Add a source/actor control with same site and geometry but different mx. Compare the two old materializations with the one shared row. Include a request for another group after an earlier answer, and a late supply after both keys have registered.
4. Negative controls: different original, path, tail, E, layer, source/operation site, cleaner phase, callerLayer, identityCandidate or pendingDemotion remain distinct. Keep original source ActionIds and ordinary FLOW mx untouched.
5. Verify that FULL later runs, remove-all/field kills, conditional source unary/MULTI AP primitives and complete ND application are unchanged. This is only the run-1 point-repair directory.
6. Measure the number of current point keys per exact repair key. The 2.2m paired point-family count does not reveal that ratio.

Source anchors: `Run1PointResume.kt:20-23,76-89,143-168`; `RunMethodAnalyzer.kt:221-237,300-306`; `StaticOperation.kt` exact ResumeSite fields and `Run1PointResume.kt:7-8` CallResumeState; existing delayed static source/read/check, grouped static pipeline, source-action identity and phase-resume controls.


## Other measured owners and later work

Thingsboard retains 1.201 GB under bound rule-list owners. These are ordinary
runtime bindings, not the removed ownership census. Keep the current caches
in this iteration. A later design can reduce retained duplicate conditions
or lazily construct role views, but it must preserve complete cache keys,
original FULL signed normalization, GROUP/FULL forms, provider selection,
relevance/END views, action identities and concurrent first use. Replacing
FULL names with groups before signed folding is not an equivalent shortcut.
A new managed reference cache needs its own proposal; no SoftReferenceManager
or WeakIdentityCache rewrite is selected.

Stirling-PDF reaches run-2 with 28,194,786 physical Publication wrappers.
Its actor/caller union retains 4.443 GB and its method-edge owner union retains
2.402 GB. These views overlap. Keep this problem separate from run-1 static
subscriptions. The current summary index already merges publications.
The early-merge candidate above keeps the existing barrier. Measure complete
pending-publication counts to confirm its target partition and savings.
Do not call all wrappers queued work, unique summaries or avoidable duplicates.
Any later batching design must preserve late subscriber replay, complete
premises/layers, ZERO backward permission, summary selection and counted work.

PDF's complete statement-directory selection also has 5,465,778 ConclusionGroup
buckets, two maps per bucket, and two layer lists per TaintGroup. Their fixed
container overhead is material. Lazy kind/layer containers are a possible
later representation change. First measure empty occupancy and preserve the
existing T1/T2/T4 joins and delta replacement. These counts are storage buckets,
not rule groups. This change is not part of the first run-1 matching scope.

Generic answered-point retirement is not selected. For C.f/STAR requesting J,
a C.f/ANY/J supply can create a different canonical concrete original and
result from a later C.f/EXACT/J supply. One J answer does not cover both.
A safe retirement protocol would also need new-group reactivation, in-flight
reply safety, and actual index deletion or a proved bounded rebuild. Current
PathTrie has no removal operation. Keep the existing interest lifetime.

Equal-root FLOW exclusions already have complement joins. A source witness
can bypass them during incremental root growth, but the thingsboard AP
vocabulary is small in the actual heap. Do not rank an exponential-subset
hypothesis as its measured memory cause. Keep that item for future evidence.

## Implementation and validation plan

1. Pin source 029, complete premise/fact equality, selected semantic
   observations and callback/lifetime contracts. Add the independent Lean
   list/row reference models. Prove equivalence and the scoped work bounds.
   Do not use final fact-set equality as a substitute for late-route proofs.
2. Implement the shared static matching row alone. Keep owner endpoints,
   currentValues, ordinary AP transfer and current output publication.
   Add both arrival orders, new endpoint replay, group growth, replacement,
   complete ND and cancellation controls before the runtime patch.
3. Implement the same-catalog normalization identities alone. Keep public
   interning and every field/layer/output adapter. Check FULL and foreign
   catalog compatibility against the independent old implementation.
4. Implement local call coalescing alone. Preserve producer observations
   before coalescing. Test standing registrations and late replies, plus
   every key distinction. Measure input visits, not just final store size.
5. Implement backward TAINT/REACH publication merge separately, after the
   delta-list/union protocol proof and complete pending-count check. Keep the existing barrier.
6. Implement the repair-key quotient only after its equivalence proof and
   duplicate-rate check. Preserve ordinary mx and all exact resume state.
7. Run the common/JVM and selected application gates. Check the existing
   source/action, GROUP/FULL, static pipeline and ND regression controls.
   Commit each accepted change with its proof/test evidence.
8. Repeat only the authorized four-project/main study with fixed inputs,
   heaps and timeouts. Serialize workloads/readers. Retain original profiles
   and actual OOM snapshots. Report latest reached, complete and accepted
   iteration, run-1 engine time, exact finding identities and resource failures.

Use bounded counters at the ownership boundaries, with no retained fact log:

```kotlin
// Shared matching
writerEndpoints++
uniqueCompletePremises = matching.size
candidateTupleVisits += currentCommitTupleVisits
bindingEntries = sumCurrentBindingEntries()

// Local call batches
offeredContinuations += offered
uniqueContinuations += unique

// Repair directory
originalPointKeyOffers++
uniqueRepairKeys = pointKeys.size

// Projection: scoped test counters or sampled diagnostics only
normalizationAttempts++
sameCatalogIdentities++
```

Do not rescan every map on the hot path to maintain these counters. Keep
incremental sizes or collect them at a report barrier. Counters must not
create a second history store or change scheduling.

The selection gate is semantic equivalence plus a measured reduction in the
target work/state. Full acceptance still requires the run-1 engine target,
no resource-censored baseline findings, strict location review and a bounded
number of additional report candidates. Faster incomplete prefixes alone do
not pass that gate.
