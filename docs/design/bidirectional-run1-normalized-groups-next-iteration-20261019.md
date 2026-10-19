# Next iteration: avoid redundant run-1 work

Date: 2026-10-19. This proposal uses the fresh measurements for
`b88c85d5b710d28293d7672af92b085a3838fb19` and grouped-statics revision 2.1.
No optimization below is selected or implemented. The implementation and
2,063-test gate are closed. Project acceptance fails.

OWASP and Conductor timed out in run-1. ThingsBoard reached the memory guard
in run-1. None completed or accepted run-1. Stirling-PDF completed and accepted
run-1 in 925.594 engine seconds, then timed out in run-2. No project met the
60-second run-1 engine target. See the [performance report](../reports/bidirectional-run1-normalized-groups-performance-20261019.md)
and [redundant-work investigation](../reports/bidirectional-normalized-groups-termination-investigation-20261019.md).
Earlier FF/Dac proposals are historical; their measurements do not set this order.

## Selected semantic boundary

This is a proposal to avoid work and materialization under the current
semantics. It proposes no new approximation. Keep:

- Ordinary AP geometry, tails, field exclusions, typed mark exclusions,
  layer transfer and field limits `1, 1, 2, 3, ...`.
- Complete original premises and actual query permission, including ND.
- GROUP conditions in run-1, FULL conditions later, original rule/ActionIds,
  source occurrences, source AP primitives, anchors and tails.
- The selected static order/context approximation, actual writer derivation,
  DEMAND forcing, cleaner group requests/repair and structural kills.
- Empty end-action lists in all runs, the method-queue-empty request barrier,
  current caller ownership, and the B88 static-alias Write metadata fix.

Do not restore GroundingSupport, add field-sensitive writer validation, keep
another fact history, change IR storage, rewrite SoftReferenceManager or add
WeakIdentityCache. A proof of a changed model does not authorize changed
semantics. Each implementation needs the local constructive Lean model,
proofs and tests required by [the proof guide](../../spec/proof-guide.md).
The proof obligations below are requirements, not claims that those proofs
already exist. Use the current path for every case outside a proved scope.

## Order by expected impact

The order first addresses retained collections that block ThingsBoard, then
measured engine work across the other projects. For a CPU-only first step,
start with the catalog directory and identity conversions. Do not add
retained unions or treat allocation weights as live bytes.

| Order | Proposal | Current evidence | Expected benefit and limit |
| --- | --- | --- | --- |
| 1 | Avoid eager bound-rule materialization during ownership census | Four bound-cache field families retain 3,160,452,352 B in ThingsBoard; 35,070,715 ContainsMark objects and 8,699,328 exit sinks | Avoid a large non-fact collection. Exact census-only share and net saving are not measured. Setup benefit is outside the 60-second engine clock. |
| 2 | Share static input selection by complete premise | 1,493,242 service writer rows versus 209,932 raw premise memberships; at least 1,283,310 output-specific registrations beyond one per writer premise | Remove repeated input subscriptions/matching while keeping every output. Static/point owners retain an overlapping 2,658,761,136 B; this is not the predicted saving. |
| 3 | Keep current eligible members by group in the catalog | `eligibleMembers` appears in 59.33% of PDF and 43.80% of Conductor run-1 Java samples | Avoid full-catalog scans and temporary member lists. Keep per-member policy work and late eligibility. Largest directly attributed scan candidate. |
| 4 | Avoid identity GROUP and exclusion conversion | Projection/policy/exclusion union: 86.24% OWASP, 63.24% Conductor, 77.10% PDF run-1 Java samples; OWASP normalization alone 69.23% | Avoid repeated walks, normalization and catalog lock calls. Categories overlap; removable fraction needs per-path proof and measurement. |
| 5 | Deduplicate normalized static operation inputs | Source witnesses map distinct read replies/point IDs to equal operation inputs | Avoid duplicate addressed execution and point keys. Project duplicate frequency is unknown. |
| 6 | Admit request memberships before constructing deltas | 30,643,891 OWASP offered memberships; 99.847% duplicate in R or P | Avoid duplicate set construction and pure request checks. CPU share is unknown; scheduling is already coalesced. |
| 7 | Share equivalent static-interest matching | 817,103 ThingsBoard point subscriptions, 29,038,614 static candidate visits | Avoid repeated pure matching. Distinct semantic query count is unknown; all continuations remain necessary. |
| 8 | Coalesce equal call-stage values after observations | Executed production-shaped control: three equal callbacks, one stored successor in both FULL/GROUP | Remove repeated suffix execution. Narrow local change gives three to two callbacks, not three to one. Project frequency is unknown. |
| 9 | Allocate conclusion containers on first use | Kind-body retained union 421,960,736 B; MethodEdgeStore union 149,233,832 B | Reduce empty container overhead. Occupancy was not measured; expected retained impact is below the larger auxiliary owners. |

The first two changes avoid huge collections. The next two avoid work inside
common operations. The remaining rows have weaker project attribution or
smaller measured ownership. This order is an engineering estimate, not a
measured speedup or a promised byte saving. None proves a universal 60-second
bound.

## 1. Avoid eager bound-rule materialization during ownership census

Current: census queries all eight rule roles and both relevance flags over
graph methods and call targets. A miss resolves method-specific full rules
and retains their condition ASTs and metadata in hard configuration maps.
GROUP form lowering occurs later. It does not release those FULL trees.

```kotlin
for (query in censusQueries) {
    val bound = provider.rulesForMethod(query) // cache miss retains full list
    bound.forEach(::observeActiveFullMarks)
}
```

The final heap headers show one configuration and eight role storages.
Normal/allRelevant maps hold 940,406 method/role/view key memberships, which
include cached empty lists. All no-end maps are empty. A strong main-thread Java-local
path retains the provider chain. These are actual populated views, not four
copies or GROUP/FULL duplication. The no-end factory change alone can populate
additional caches beside existing ordinary maps; coordinate it with census
lifetime. Do not estimate its saving from the whole 3.16 GB union.

New: add a census capability for known provider chains. Borrow an existing
bound list when present. On a miss, observe the same ordinary resolver stream
without inserting the bound list into the runtime cache. Drop transient bound
objects after observation. Unknown providers retain their current calls/order.

```kotlin
interface CensusMarkVisitor {
    fun visit(query: CompleteRuleQuery, emit: (FullName, OriginalOwner) -> Unit)
}

fun visit(query, emit) {
    existingBoundList(query)?.let { return observeExactly(it, emit) }
    resolveOrdinaryStream(query) { rule -> observeExactly(rule, emit) }
    // No new entry in the long-lived bound-rule cache.
}
```

This first step avoids census cache insertion, but still constructs bound
ASTs. Repeated graph/declared/resolved target visits can now bind again instead
of hitting the current cache. For a proved method-only provider chain, reuse
only the neutral ordered census result under the complete role/relevance/
method/provider-selection key. Keep late metadata invalidation and owner
observation order. Otherwise measure the added rebind cost and retain the
current provider fallback. Avoided insertion alone is not avoided binding.
A later template visitor can avoid constructing ASTs at all, after it has a
separate equivalence proof.
Do not register every serialized mark: false-rule folding, binding, role
admission and ignored end actions affect actual eligibility. Eligibility
changes group policy, so an enlarged census is a semantic change.

The current broad accepting MethodExit templates help explain the large
method/template/view population. They often already use Result after the
end-position rewrite. Do not attribute the heap to an unmeasured AnyArgument
Cartesian product. Empty end-action lists do not remove exit-sink conditions.

Use immutable metadata and unchanged template subtrees where possible:

```kotlin
val template = compileOriginalFullTemplate(serializedRule)
val condition = template.bind(method, context, relevantPolicy)
val sink = bindOriginalIdentity(template, method, condition, endPolicy)
// Share immutable metadata; retain original full condition and occurrence ID.
```

Do not collapse normal and allRelevant behavior. Actual source/cleaner
rewriting uses allRelevant. A void Result, invalid argument, static This,
type/annotation check or binding context can change a condition. Share only
unchanged immutable pieces. Live forms/findings can retain bound sinks even
when a raw cache entry is absent; cache removal is not a whole-payload saving.

The Spring wrapper stops the known-provider bidirectional factory. Restore
that capability through the wrapper, with all its role overrides:

```kotlin
class SpringRuleProvider(private val base: TaintRulesProvider, private val ctx: Ctx)
    : TaintRulesProvider by base, BidirectionalRulesProviderFactory {
    override fun bidirectionalRulesView() =
        SpringRuleProvider(base.forBidirectionalCore(), ctx)
    // Preserve controller, generated-method, cleanup and repository behavior.
}
```

This avoids binding ignored end actions before later removal. It does not
remove the dominant condition ASTs by itself. A finalized selected-rule filter
can also precede expensive binding only if it preserves original selection,
allRelevant access, null-ID models and ordering. The resolver's generated-ID
counter depends on earlier successful bindings. Reserve original IDs/ordinals
or retain the numbering pass; do not replace IDs with hashes.

Proof/validation gates:

- Same ordered census names, original owners, eligibility and owner conflicts
  for every complete query, including late/generated/model/controller cases.
- Same bound outputs, FULL conditions, source action ordinals, metadata,
  relevant view and no-end policy. Unknown provider behavior stays unchanged.
- Cache hit and uncached rebuild have the same logical identities. Do not
  mutate a shared provider or clear its unrelated caches.
- Measure census insertions, bound objects built/retained by role/view and
  rebuild cost. Compare strict report identities after the selected gate.

If source-only census cannot preserve a provider's behavior, use its current
binding path. This proposal changes materialization and lifetime, not rule
admission or ownership semantics.

## 2. Share static input selection by complete premise

Current: one method can register many output-specific writer rows for the
same premise. Each row selects and matches the same supplied input again.
Its key includes output Pattern and layer.

```text
(owner, premise P, output Y1) -> select/match P -> apply Y1
(owner, premise P, output Y2) -> select/match P -> apply Y2
...
```

The complete actor tables give a conservative bound:

```text
service writer rows                         1,493,242
all actor raw premise memberships             209,932
extra output-specific registrations >=      1,283,310
```

All raw premises upper-bound distinct writer premises. The outputs can be
necessary. The avoidable structure is repeated input subscription/matching,
not the output effects. No retained PremiseSet exists in this snapshot, so
singleton writers are the first scope. Do not lead with an ND product rewrite.

New: register input selection once per complete premise, then use existing
raw summaries to enumerate its current outputs. Do not copy the output store.

```kotlin
data class WriterInputKey(val owner: MethodOwner, val premise: PremiseKey)

onNewStaticSummary(p, freshOutput) {
    inputs.getOrCreate(WriterInputKey(owner, p)).subscribeOnce()
    // Old supplies must also reach this newly published output.
    joinCurrentSupplyWithFreshOutput(p, freshOutput)
}

onFreshSupply(p, freshInput) {
    val binding = ordinaryMatchCompletePremise(p, freshInput) ?: return
    rawSummaries.borrowCurrentStaticOutputs(p).forEach { output ->
        composeAndPublishThroughExistingOps(binding, output)
    }
}
```

For P with M outputs and r supplied images, current input matching can occur
M*r times. Factoring makes shared input matching proportional to r, while
retaining the required M*r output compositions. Neither the snapshot nor
this formula measures the actual matching/composition cost split.

Keep tails, E, typed FLOW mx, mark selectors, layers and run domain in the
semantic input key. If matching needs additional binding state, include it.
A spatial index hit is not a complete match. Use the existing AP operations.
The publication selector can change after semantic replacement. Borrow the
current value; never reconstruct an obsolete output from a stale key.

Both arrival orders are mandatory: new supply joins current outputs, and new
output joins current supply. Retain completion/ack accounting, original raw
summaries, demand guides and all source/rule identities. Do not retain another
history of full Facts or suppress valid output growth.

A second, separate step can share a pure relation across methods when the
complete premise/output semantics agree. Keep live owner references and all
ordinary per-method summaries. Repeated equation families were not counted,
so cross-method sharing is less certain than the measured per-premise factor.
Ordinary Base writers and caller/mixed-premise joins keep current ownership.

Proof gates: per-owner decoded output and demand-guide equality, new input/
new output orders, replacement, disappearing selectors, exact AP composition,
late growth and counted completion. For ND, preserve the full compatible tuple;
do not replace a conjunction with independent member existence unless its
output is separately proved constant. Fallback is the current per-output row.

## 3. Keep current eligible members by group

Current: each `eligibleMembers(g)` scans all eligible full marks and builds
lists. PDF starts with 1,172 full marks in 51 groups. One visit can inspect
1,172 owners to answer one group's policy check. Fewer propagated marks do
not remove these internal scalar operations.

New: maintain the same ordered membership directory in the catalog owner.
Publish immutable snapshots under the existing lock. Refresh on all relevant
late registration/ownership/eligibility mutations. Do not freeze census.

```kotlin
// Under the current catalog lock.
onEligibleOrOwnershipGrowth(full) {
    keepExistingOwnershipRules(full)
    updateOrderedGroupDirectory(full)
    replaceAffectedImmutableSnapshot(full)
}

eligibleMembers(g) = currentSnapshot(g)
keepGroup(g, policy, level) = eligibleMembers(g).let { members ->
    members.isEmpty() || members.any { policy.keeps(it, level) }
}
```

Current lookup scans E eligible marks; new lookup obtains one snapshot, then
performs the same policy checks on Mg group members. Keep insertion order,
including eligibility-before-ownership cases. Keep accepted/rejected additions,
conflicts, fallback owners and old borrowed snapshot immutability. A mutable
policy still runs; do not cache its truth or combine independent existential
masks for an AND.

Proof: at every catalog linearization point, the directory equals the current
ordered scalar filter. Test all mutation paths, empty/unknown groups, FULL/
GROUP, model declarations, same-member conjunction and mutable policy. Retain
the scalar path for uncovered cases. Ownership conflicts remain errors.
Measure lookup count, inspected full members, list allocations and lock time;
59.33% sampled inclusion does not promise a 59.33% speedup.

## 4. Avoid identity GROUP and exclusion conversion

Current: generic result paths can project an already selected GROUP trie.
Exclusions can normalize again against the same catalog. This repeats walks,
sorting, temporary allocation and synchronized catalog calls.

New: retain canonical immutable values when the conversion is proved to be
an identity. Keep actual domain boundaries and DEMAND forcing.

```kotlin
fun runtimeAtom(atom, mode): TaintMark {
    if (atom == ZERO) return atom
    if (mode == FULL) {
        require(!isGroup(atom)) // existing executable-atom contract
        return atom
    }
    if (isGroup(atom)) return atom
    return group(groupOf(atom))
}

fun MarkExclusions.withCatalog(table): MarkExclusions {
    require(catalog == null || catalog === table)
    if (isEmpty) return EMPTY
    if (catalog === table) return this // canonical-construction invariant
    return normalize(atoms, table)
}

// Only at a callback with a proved GROUP-closure law:
emitKnownRuntime(forceSelectedRun1Layer(original, ordinaryResult))
```

Whole-fact shortcuts require a per-operation closure proof. Raw factories,
shared subtrees and generic adapters do not all enforce the GROUP domain.
Keep start/source/target projection, cover and field-limit behavior, mixed-
catalog rejection and noncanonical fallbacks. FULL executable atoms/targets
still reject group selectors. Persisted GROUP FLOW exclusions and demand/
premise guides remain legal in their current later-run readers; do not add
blanket FULL validation at unrelated boundaries.

Proof/tests: projection idempotence, canonical exclusion construction, each
AP callback's closure, ZERO, source ANY/ANY_TAINT, selected cleaners, field
writes, point resumes, summary application and later FULL group readers.
Measure actual skipped walks and normalizations. Allocation/sample categories
overlap with catalog policy and cannot be added as separate savings.

## 5. Deduplicate normalized static operation inputs

Current source traces show distinct service replies becoming equal before
operation execution:

```text
EXACT read overlap, ANY_TAINT overlap -> same STAR FLOW operation input
point image mx={g}, point image mx={} -> same marked input after mx clearing
```

New: key freshness by the effective per-leaf materialized operation input at
the existing delivered/point owner. Keep ordinary Facts unchanged.

```text
readDeliveryKey = canonicalReadOperationInput(overlap)
pointKey = (site, initial, actualNormalizedLeafRelation, layer, callState)
```

This can remove duplicate addressed execution and point IDs. It needs no
second permanent processed-fact history. Project frequency and byte savings
are unknown. Do not equate overlapping inputs; prove equal materialization
for every future allowed supply. Keep complete original premise, cleaner
phase, field/tail geometry, caller state, wanted-group growth, source identity,
DEMAND forcing and current selector validity. Preserve both arrival orders.
Use the original key for any case without that equivalence proof.

## 6. Admit request memberships before constructing deltas

Current: requests already merge in R/P, but producers can repeatedly build
and offer the same member. OWASP has 23,873 R duplicates, 30,573,022 P
duplicates and 46,996 fresh P admissions. This is 99.847% duplicate membership;
it is not a measured CPU percentage or another queued task per duplicate.

New: check the existing owners first, then construct a fresh delta once.

```kotlin
collectOne(slot, group) {
    initializeAndCheckActiveRun1Owner()
    checkpointAndCountOffer()
    if (R.contains(slot, group)) return countRDuplicate()
    if (P.contains(slot, group)) return countPDuplicate()
    P.addOne(slot, group)
    startAgeIfFirstFreshMember()
    requestWork()
}

summaryStorage.commit(temporary)
temporary = RequestStorage()
```

Keep the same barrier, two stores, cancellation/release checks and pending
age meaning. Bulk/custom cursors retain current deduplication when needed.
A later shortcut may skip a pure ordinary abstract check only if its entire
effect is None or a retained request for the original Base/group. Never skip
a whole cleaner, source, Holds, TAINT, ND or static operation on that basis.
Cleaner complement and DEMAND repair still execute.

Proof: decoded R union P grows by exactly offered members. Test duplicate
classification, repeated IDs, commit/replace, both orders, late input,
request-only scheduling and cleanup. Do not run old geometry merely to
reproduce admitted batch-dependent diagnostic counts.

## 7. Share equivalent static-interest matching

Current: the path index narrows candidates but indexes each owner/id interest.
Equal queries can repeat AP overlap and group selection.

New: share the pure query relation, then deliver to every actual continuation.

```text
shared[completeStaticQuery].join(freshPoolMembership)
for each subscriber with fresh wanted-and-available membership:
    deliver its original owner/id reply
```

Keep CallerRef, stage, layer, complete premise/member, result leaf, wanted
growth, both arrival orders and owner-side ordinary matching. Shared
availability is current monotone state, not a history log. This does not
remove 817,103 continuations merely because some queries can be equal.
Prove identical per-subscriber effects and demand guides, late subscribers,
copies/cycles, cleanup and counted completion. Use current matching when the
complete sharing key is unproved. Measure distinct keys before promising
large savings.

## 8. Coalesce equal call-stage values after observations

Current: stage lists can carry equal complete values to the next stage before
successor admission merges them. The actual production-shaped control has
three equal final callbacks and one stored value in both FULL and GROUP.
`JIRRuleForms.mark` projects GROUP before the builder's edge set; configured
T/U copies do not all survive as separate GROUP edges. The generic five-fork
API control is not a project workload or a production JVM chain.

New: coalesce exact complete stage values in one existing frame, after all
required producing action/request/static observations.

```kotlin
data class StageValue(val premise: PremiseKey, val facts: Facts,
                      val identityCandidate: Boolean?)

// Node, stage, caller layer and pendingDemotion are fixed by this frame.
val out = LinkedHashSet<StageValue>()
applier.stage(summary, input.premise, input.facts, place) { p, f, edge ->
    out += StageValue(p, f, kind.identityCandidateOf(edge, input.identityCandidate))
}
return out.toList()
```

The narrow local change reduces the pass branch from two equal values to
one. The parallel default frame still emits one: whole-call callbacks fall
from three to two. Cross-frame merging needs a separate complete-state proof.
Do not key on Facts alone: the executed metadata control distinguishes
intermediate identity states. Two source ActionIds can emit one equal fact;
both source identities must remain.

Proof: an equal complete stage value has the same future semantic effects
in this frame. Preserve all source/rule occurrences, no-result requests,
static effects, complete ND bindings, first-occurrence order and trigger
behavior. Keep the list path for an unproved non-idempotent observation.
Count raw stage attempts versus distinct complete values in a future fixed
study. No project-wide multiplier is inferred from the synthetic API test.

## 9. Allocate conclusion containers on first use

Current: ConclusionGroup eagerly creates reach/kind maps. FlowGroup and
TaintGroup create both layer lists. The current heap proves ownership, not
empty/single-base occupancy.

New: allocate a kind/base/layer bucket only at its first value. An optional
single-base slot can promote to the existing map after separate validation.

```kotlin
class ConclusionGroup {
    private var reachMask = 0
    private var flow: MutableMap<Base, FlowGroup>? = null
    private var taint: MutableMap<Base, TaintGroup>? = null
    // Missing kind/layer decodes to empty, never to a fact.
    // Use the same existing merge/subtract algorithms in populated buckets.
}
```

Public add/delta/borrow results stay the same. Keep annotation replacement,
T2/T4, tails, E, typed mx, layers, interner behavior and complete premises.
A duplicate must not allocate an unrelated kind. Prove scalar old/new traces,
all admitted mixtures, empty/one/many bases, annotation broadening and borrowed
lifetimes. Use ordinary maps for mixed cases. Measure occupancy before a byte
estimate. This is representation work, not fact reduction or subsumption.

## Future candidates

Constant-image all-static ND writers can use a complete existence witness
instead of enumerating all candidate tuples only when the AP image is proved
independent of the selected binding. Keep ANY_TAINT/FLOW binding-sensitive
processing. The fresh heap has zero PremiseSet objects, so ND products are
not its current retained pattern. Defer this optimization until measured
transient tuples or a later heap justify it. `candidateVisits` is not a tuple
counter; ordinary N2 E6 is not the direct static ND path.

Managed payload lifetime can trade memory for rebuild cost using the current
managed-reference mechanism. First measure actual rebuilds and strong owners.
A form-build count alone does not prove eviction. Keep GROUP/FULL isolation,
stable logical keys, live continuations and exact source/ActionIds after
rebuild. No SoftReferenceManager rewrite or WeakIdentityCache is proposed.
This is a separate future option, not a substitute for avoiding eager payload.

Provider/form calculation sharing must retain the original full condition,
source AP selection and occurrence identities. Do not reintroduce condition
cubes, full alias proof products or shared mutable actions. Ownership setup
was 253.969 seconds on ThingsBoard, outside the engine target. Census/cache
work can reduce this setup and the starting live set; report those benefits
separately from engine time.

## Implementation and measurement gates

Select a narrow change, prove its local equivalence and complexity bound,
then implement only that change. Compare the same ordered AP/rule observations,
complete demand guides and strict report identities. Use the selected gate,
commit, then measure the same four fixed projects and configurations.

Add counters at the avoided work boundary: raw attempts, distinct complete
inputs, actual matching/normalization/catalog work, emitted deltas and retained
rows. Do not infer AP-operation counts from `steps`: addressed resumes bypass
it. Do not infer an infinite loop from flat completed-work reservations.
A diagnostic count may vary only within the user's admitted batching policy;
semantic source/sink identities and occurrences must remain.

Keep main as the strict finding reference. Report latest reached/completed/
accepted iteration, engine/setup/barrier clocks, OOM/timeout and export errors.
A smaller timed-out prefix is not a speedup or no-FN acceptance. The current
study is complete; its runtime acceptance remains failed.
