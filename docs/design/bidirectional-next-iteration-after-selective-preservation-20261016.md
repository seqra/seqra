# Next analyzer-core iteration after selective preservation

Date: 16 October 2026. Measurement runtime: `28a123f0eac937e8f2feca9ee5dd84e38fa8f979`.
Updated: 17 October 2026. Selected design: equivalent bottom-up run-1 static
FLOW, universal DEMAND static defaults, mark requests only and ordinary NORMAL
persistence. No optimization in this document is implemented.

The [latest measurements](../reports/bidirectional-run1-selective-preservation-20261016.md)
replace earlier runtime reports. Latest reached/completed/accepted iterations
are OWASP **run-7 / run-6 / run-5**, conductor **run-2 / run-1 / run-1**,
ThingsBoard **run-1 / none / none**, and Stirling-PDF **run-2 / run-1 / run-1**.
OWASP times out; the other three run out of memory. Conductor and PDF meet
the 60-second run-1 engine target; OWASP and ThingsBoard do not. All three
normal heap reports and the targeted weighted-input/publication checks are
complete. There is no measurement of the new design.

The scope is analyzer execution/storage; IR storage is outside scope. The
[unified contract](../../spec/run1-field-writes.md) describes the measured
implementation. The [new static FLOW specification](../../spec/run1-bottom-up-static-propagation.md)
defines the proposed replacement and takes precedence for this next iteration.
The older normal-exit gate and virtual-availability approximations are withdrawn.

The [constructive proof dossier](../../spec/run1-bottom-up-static-proofs.md)
now supplies the scalar/finite-derivation theorem map and exact implementation
refinement boundary. The
[validation report](../reports/bidirectional-run1-bottom-up-static-proof-validation-20261017.md)
records its Lean build and complete declaration audits. This adds no analyzer
implementation or project measurement. Semantic observation identities and
rule occurrences are preserved; batch-dependent diagnostic callback counts
may differ, including `ExternalMethodRecord.callSites`.

## Selected: equivalent bottom-up static FLOW

**Current:** descendant read needs can start abstract field analysis throughout
the caller chain. Ordinary calls retain full field-input subscriptions and
propagate unchanged static carriers. Field identity summaries can duplicate
the ordinary root identity.

**New:** keep static reader effects in ordinary summary storage and compose
them bottom-up through exact call-input bindings. Intermediate transparent
methods keep symbolic entry bindings and shared call relations, not ordinary
field reader seeds or retained inherited-field inputs. Derived W, transformed
inputs, concrete TAINT and ordinary nonstatic results keep their full binding.
Only mark requests remain. Local read positions are computation metadata, not
field requests or a transitive initial-admission gate.

All static-related FLOW is DEMAND, including its static-to-local descendants.
The target is the **same normalized summary relation** as ordinary propagation
with that policy, modulo only redundant pure field identities represented by
the shared default. No ignored local overwrite, global static-value pool or
ZERO-based recreation of a killed input is allowed. Preserve method-entry
origin, actual call-stage reachability, full AP geometry, mark requests,
source/sink conditions and whole ND support.

The new specification supplies the equations, traces, transparent-relay rule,
nontransparent-input bridge, two-sided replay obligations and required
independent ordinary-engine comparison. It also records the performance
limits. In particular, a method with no local read can still overwrite f
before a reader call; that prefix must retain its ordinary kill semantics.

## Selected: universal DEMAND default and NORMAL persistence

Every reached run-1 method publishes the ordinary root default at owner
initialization, regardless of whether the method returns normally:

```kotlin
publishOrdinarySummary(staticRootIdentity, layer = Layer.DEMAND)
```

Do not export a normal-exit reachability bit, add an exit predicate, or select
this default's layer with StaticMayWrite. Continue to emit no S-root initial.
Every call, including one with no nonidentity reader effects, applies the
same stored default to its actual surviving static input. The result keeps
that field, tail and mx and joins the DEMAND layer. Default delivery and late
input replay use ordinary summary ordering.

At every complete joined barrier, including run-1, use ordinary persistence:

```kotlin
shared.records.persist(
    config.direction,
    result.analyzers.asSequence().map { it.key to it.summaries },
)
val handOff = HandOff.of(config, result, report, shared)
```

Remove the first-run skip. All NORMAL summaries follow the same persistence
policy as later runs, without a static/run-1/normal-exit filter. Retain the
ordinary record format and its structural/native/reverse admission guards;
do not flatten ND support or add a separate record format in this change.
The static default and static-related FLOW are DEMAND and do not become
NORMAL records. Independently supported NORMAL static concrete or nonstatic
summaries remain reusable. No failed/OOM/timeout prefix is persisted as a
completed result.

Keep the existing typed demand projection and include relay-only owners in
handoff. Keep current-query admission, report confirmation and stop rules.
The transport change does not make a DEMAND result confirmed or promote it
to NORMAL. Concrete TAINT retains ordinary semantics; applying the DEMAND
default to an actual TAINT input can still demote that continuation normally.

### Proof and implementation order

1. Fix an ordinary run-1 oracle with universal DEMAND defaults and the same
   early static FLOW demotion. Layer-sensitive guards must see the same input
   layer in both procedures; final-row relabeling is not a sufficient oracle.
2. Prove exact identity-input reachability and transparent-relay factoring,
   then specify the nontransparent prefix/packed AP bridge. Activation alone
   is never a supplier or proof that a local overwrite was survived.
3. Prove both directions of normalized summary equality, final mark-request
   and observation equality, arrival-order closure and recursive completion.
4. Replace inherited carrier transport, remove field-request gates, and retain
   ordinary bound W/transformed/TAINT handling. Publish the unconditional
   DEMAND default and enable ordinary run-1 NORMAL persistence.
5. Validate native/reverse readers, handoff, failure behavior and independent
   differential controls, then repeat the fixed project configurations.

This is a proposal, not an implementation checklist already completed. The
compact nontransparent input adapter remains a required design bridge.
Measure local versus inherited reader work, transparent versus bound inputs,
identity publications, compact reachability state, request replay, record
reuse and net later refinement. Record every latest reached/completed/accepted
iteration and exact finding identity.

The memory candidates below retain their measured ordering. Their benefits
must be reassessed after this selected algorithm change removes workload;
none is permission to add a further semantic approximation. NORMAL persistence
alone does not suppress an ordinary later callee query or establish reduced
recomputation. No measured saving percentage or 60-second claim exists yet.

## Evidence and choice of approach

OWASP's run-1 has 61.36 million offered request members, including 61.01
million already pending. Cleaner handling appears in 53.41% of its full
run-1 Java execution samples and 70.37% of sampled allocation weight.
These measurements support avoiding duplicate computation and allocation.
Changing only the retained representation cannot remove this work.

Conductor's failed run-2 has 1.91 GB in the conclusion-bucket retained union,
1.32 GB in the method-directory union, and 1,485,029 pending publications.
The byte unions overlap. The pending-list retained union is only 43.7 MB;
its 1,485,029 fragments have 30,118 distinct full premises summed per actor.
These findings require analysis of state production and joins,
rather than assuming that a queue or request container is the main owner.

Output TAINT storage already merges marks in `TaintGroup` for a common
premise, base, tail and exclusion. A proposal to "merge output marks" would
repeat existing behavior. Different original concrete initial marks and
whole ND premises still identify different dependencies. Combining those
requires a relation that preserves input/output mark correlation.

ThingsBoard's failed run-1 has 2.32 GB in caller input storage, including a
1.44 GB positional index, and 2.39 GB in conclusion buckets. These unions
overlap. Its admitted and temporary request-map unions are only 82.6 MB and
10.1 MB. A large offer count is therefore not a large standing-request byte
estimate. It has 5,081,664 conclusion buckets and 4,571,798 FLOW groups.
The weighted census shows that 92.08% of its caller groups currently carry
statics, including 80.02% static FLOW. All 68,787 concrete initial memberships
are alone in their exact-geometry cohorts within their actors. This favors
investigation of abstract static propagation over merging concrete original
marks in run-1. It does not measure packed current TAINT mark widths.

All four frozen static catalogs report zero precomputed field-mark request
rows/members in this study. F is separate and nonempty; dynamic static
requests are also separate. Further factoring of an absent frozen product
is not a demonstrated memory opportunity for these workloads.

## Priority by expected memory impact

This order uses the measured owner sizes and how directly the code establishes
avoidable retention. Owner unions are opportunity bounds, not promised savings.
The conditional candidates require an extra collapse/rebuild measurement
before implementation selection.

| Memory priority | Proposal | Evidence and expected effect | Run-1 time effect |
| --- | --- | --- | --- |
| 1, conditional major design | Batch run-1 FLOW with origin labels | Targets repeated static FLOW method state and caller/index relations. Largest algorithmic opportunity on ThingsBoard; achievable packing width remains unmeasured. | Can avoid repeated traversal/transfer, not only allocation |
| 2 | Allocate conclusion containers only when needed | Millions of unnecessary empty containers; 10.24 million conclusion buckets on PDF. Clearest avoidable-object evidence across all three heaps. | Fewer allocations and less GC; transfer count stays the same |
| 3 | Make method-bound rule caches reclaimable | Up to 1.09 GB in the measured normal sink-cache union on ThingsBoard. Release fraction remains conditional on other owners. | Can cost time when bindings rebuild |
| 4, conditional smaller alternative | Normalize caller inputs and share lookup families | Targets ThingsBoard's 2.32 GB caller population. Header redundancy and posting collapse are unmeasured; overlaps the first proposal. | Can avoid replay work; family expansion can also cost time |
| 5 | Coalesce authorized summary growth | Pending unions are only 10.9–58.9 MB despite large fragment counts. Small OOM impact; fewer pieces can reduce later work. | Existing barrier already batches; benefit comes from fewer pieces |
| 6 | Reuse cleaner geometry and bypass idempotent FLOW updates | Strong evidence for allocation/GC reduction; persistent-byte savings are not established. | Highest measured CPU/allocation lead on OWASP |
| 7 | Admit mark sets once and suppress empty-delta wake-ups | Already-merged request stores are relatively small; avoids temporary work around them. | Direct duplicate-work lead on OWASP and ThingsBoard |

The selected normal-summary reuse change above has an unmeasured net memory
effect, so it is not assigned a speculative place in this measured-owner
ranking. Its selection is independent of the other candidate optimizations.
The first rank is potential impact, conditional on shared state/work widths;
it is not a demonstrated saving or an implementation-ready selection. If
that measurement gives little overlap, proceed with the narrower proposals.
The run-1 engine target gives a separate execution order for small changes:
first avoid redundant cleaner/request work, then reduce remaining storage cost.
Caller normalization is preferable to retaining obsolete values when its
equivalence and collapse are established. Compact buckets address the
required relation that remains. Cache reclamation is an explicit memory/CPU
tradeoff. These are proposed choices, not authorization to implement them.

## Batch run-1 FLOW with origin labels

**Current:** each abstract original initial has separate statement state and
work. Its FLOW fact can share immutable trie nodes, but the engine still
applies forms and retains caller relations separately. ThingsBoard has
372,664 static abstract initial memberships and 3,797,487 caller input groups
with static abstract origins.

**Proposed:** pack the run-1 FLOW relation into a trie whose leaves retain
their original-initial labels. Share path traversal and form application
across both equal and different paths, without inventing origin/output pairs.
Concrete facts, ZERO and complete ND premises retain their existing channel.
This is internal relation storage, not a new initial fact family. Keep F/R
emission gates, frozen B borrowing, dynamic P commits, allowed original slots
and the ban on abstract S-root/[any] run-1 initials unchanged.

```text
Current scalar rows:               Packed relation:
origin_f -> C.f.*                  C.f.* -> {origin_f}
origin_g -> C.g.*                  C.g.* -> {origin_g}

Never: {origin_f, origin_g} × {C.f.*, C.g.*}
```

```kotlin
data class OriginLeaf(val at: OriginSet, val below: OriginSet)
// Bucket also fixes full MethodKey, node, base, layer, tail, E and mx.
fun offerFlow(node: CommonInst, origin: InitialAp, scalar: FlowTree) {
    val delta = labeledStore.add(node, origin, scalar) ?: return
    worklist.addOrMerge(node, scalar.header, delta)
}

fun requestAt(matchingLeaves: OriginTrie, mark: TaintMark) {
    for (origin in matchingLeaves.originals())
        offerRequest(normalSlot(origin), mark)
}
```

Process currently pending labeled deltas together; a later origin contributes
its own delta. Do not postpone work past the existing counted barriers or
add a new scheduler. Preserve the original-to-current relation through call
stages and ordinary publication. Expanding every batch back into scalar
links at each call would restore the existing caller/index cost; local
batching alone does not establish interprocedural memory savings.

The required simulation law is per origin, including effects:

```text
decode(o, packedTransfer(batch)) = scalarTransfer(o, decode(o, batch))
packedRequests = union of scalar (originalSlot, mark) requests
```

FLOW mark checks request concretization and do not yield concrete Holds/ND
support. This makes a separate FLOW channel plausible, but is not a proof
that every transfer can ignore origin labels. Required hard cases are:

- Apply unchanged-static and changed-origin gates with per-origin masks.
- Static cleaners need matched-origin sets; one global matched flag is wrong.
- Real cuts union only labels from overflowing subtrees.
- Preserve tails, layers, E/mx and T2/T4 normalization per decoded origin;
  unrelated origins must not inherit one another's less-excluded header.
- Calls retain full context, call-stage position, original caller layer and
  identityCandidate state. Map labels to exact caller references and callee
  initials; preserve both request/input arrival orders and late subscribers.
- Summaries preserve each supported origin/output pair. Concrete source/sink
  NNF evaluation and whole ND support remain unchanged.

Before selecting this major design, measure cohorts by full method/node,
base, layer, tail and E/mx: distinct origins, scalar trie nodes versus shared
labeled geometry, and label memberships. Measure pending work cohorts
separately; overlap in retained history does not prove simultaneous batching.
Compare the scalar and labeled closures/effects on independent exhaustive
models, including recursion, cleaners, changed origins, cuts and exceptions.

Expected benefit is fewer form/filter traversals, per-initial wrappers,
subscriptions and index entries while keeping the same supported relation.
Label storage has a cost, and all required summary dependencies remain.
The current counts establish neither an achievable packing factor nor a
60-second result. This proposal needs a separate detailed design before code.

## Allocate conclusion containers only when needed

**Current:** every `ConclusionGroup` eagerly allocates a reach BooleanArray
and both FLOW and TAINT HashMaps. FLOW/TAINT base groups each allocate two
layer lists with capacity one. Most premise kinds need only one fact kind;
many cells need only one base and layer. Conductor retains 4,347,435
conclusion buckets; ThingsBoard retains 5,081,664. Comparing the latter's
two eagerly allocated kind maps per bucket with all 4,571,798 FLOW groups
and 565,535 TAINT groups gives at least **5,025,995 empty kind maps**.
Groups owned elsewhere only make this lower bound more conservative.
Stirling-PDF's 10,235,671 conclusion buckets and 10,350,012 TAINT groups
similarly imply at least 10,121,330 empty kind maps. These are object-count
bounds, not byte savings.

**Proposed:** use a reach bit mask, allocate kind/layer storage on first use,
and keep a single base directly until a second distinct base requires a map.
Keep the existing normalized FLOW/TAINT value algebra.

```kotlin
private var reachMask = 0
private var singleBase: AccessPathBase? = null
private var singleBucket: BaseBucket? = null
private var multipleBases: MutableMap<AccessPathBase, BaseBucket>? = null

fun add(input: Facts): Facts? = when (input) {
    is Reach -> addReachBit(input.layer)
    else -> bucketFor(input.base, input.kind).add(input)
}
// bucketFor creates only the required kind; a second base promotes storage.
// Each bucket creates a layer list only when that layer first receives data.
```

This representation change targets repeated headers and empty containers,
not the semantic fact relation. The measured 1.91/2.39 GB conclusion-owner
unions include necessary trees and leaves, so they are not removable totals.
The millions of eager containers make this a stronger lead than dense
statement arrays, whose class-wide shallow totals are only 52.6/44.9 MB.

Prove identical T2/T4 insertion deltas and enumeration through empty,
single-base, multi-base and both-layer transitions. Preserve REACH, complete
premises, all tails/exclusions, interning and cancellation. Do not add a
second semantic cache or change mark correlations. Measure surviving shape
occupancy and container shallow bytes after avoiding redundant history.
Generic groups must still support mixed kinds: a ZERO premise can have both
REACH and TAINT. Lazy allocation must not assume one kind globally. Preserve
enumeration order where callers use it for deterministic processing.

## Make method-bound rule caches reclaimable

**Current:** normal call, entry and exit sink binding caches strongly retain
320.7 MB on conductor run-2 and 1,092.0 MB on ThingsBoard run-1. Method-bound
sources also have strong caches. The resolver already simplifies and drops
method-bound false conditions; later call-site rejection cannot safely delete
a rule for the entire method.

**Proposed:** store rebuildable method-bound source and sink lists through
the existing managed soft-reference mechanism. Keep serialized definitions,
matching rules, role/method keys and effective compiled forms. Preserve normal
and allRelevant binding modes separately. Do not rewrite SoftReferenceManager
or introduce WeakIdentityCache.

```kotlin
// Inside one role/mode storage; the legacy strong-cache path stays available.
private val rebuildable = HashMap<JIRMethod, Reference<List<T>>>()

@Synchronized
fun managedBindings(method: JIRMethod): List<T> {
    rebuildable[method]?.get()?.let { return it }
    val bound = resolveMethodItems(method).adjustEmptyList()
    refs.createRef(bound)?.let { rebuildable[method] = it }
    return bound // createRef may decline caching under memory pressure
}
// Existing manager and reference policy; no new cache-identity mechanism.
```

The selected bidirectional prescan must use the same policy: it shares the
configuration and can fill these caches before the new engine starts.
Changing only runtime access would leave that retention intact. Legacy
behavior and the separately keyed allRelevant source/cleaner path must remain
correct. Apply the policy consistently to complex source and sink conditions;
retain signed NNF without cube expansion.
Pass the existing analysis RefManager into the selected binding view/factory
before prescan. Do not create an unregistered private manager or retain the
same lists in both strong and managed caches. Define the provider-view
lifetime when the supplied provider is shared across analyses.

Expected benefit is reclaiming bindings no longer held by an effective form,
witness or report. Effective SinkRule objects retain their original bound
rule, so the whole measured cache union cannot be promised as savings.
Reconstruction costs CPU, and rebuilding can duplicate a still-live effective
binding. Measure both costs and rebuild counts before selection.

Prove stable occurrence IDs, rule order/ordinals, metadata, actions, context
specialization, source/sink eligibility and prescan F/B/W extraction across
rebuilds. Preserve bindings still needed by reports and traces. Do not use
new object identity to change a logical rule occurrence. Verify exception
and concurrent rebuild behavior through the existing cache contract. Preserve
provider composition/relevance selection and candidate order before form
validation, AnyArg specialization order and generated fallback IDs. Binding
must finish inside its synchronized operation; no lazy iterator may escape
and later use shared pattern/mark managers outside that protection.

## Normalize caller inputs and share lookup families

**Current:** `AddedFactAccumulator` keeps one stable entry for each exact
`CallerRef` and `GroupKey`. The header includes kind, layer, base, tail, E and
mx. It merges paths/marks only within that exact header. An older header can
remain after a later input covers it. The positional index posts each exact
entry independently. ThingsBoard retains 4,707,790 entries and 18,819,408
postings.

**Proposed:** first normalize run-1 FLOW values within one exact caller,
premise, call, base and link layer using the existing FLOW join laws. Retain
the complete caller relation. Retire a covered header only when the normalized value
has the same denotation as their union. Index a lookup family once per
position, then expand it to the surviving exact header values for full
matching. This avoids obsolete header history and repeated position entries.

```kotlin
data class LookupFamilyKey(val ref: CallerRef, val base: AccessPathBase)

fun admitRun1Flow(ref: CallerRef, input: FlowTree): Admission? {
    val family = families.getOrPut(LookupFamilyKey(ref, input.base))
    val delta = family.addWithExistingJoinLaws(input) ?: return null
    // Index all current geometries; every surviving header keeps its layer.
    index.addFreshPositions(family.handle, delta)
    return Admission(family.handle, delta) // immutable replay delta
}

fun candidates(publication: Publication): Sequence<Subscription> = sequence {
    for (family in index.candidateFamilies(publication))
        for (value in family.currentValues())
            yield(exactSubscription(family.ref, value))
    // Existing complete AP and whole-ND matching still applies.
}
```

The sketch needs a versioned/stable family admission view. Today's
`Admission.current` points at a stable exact-entry handle; deleting or
reusing that handle would be incorrect. Preserve immutable queued deltas and
read the current normalized family when replay requires current values.
Capture candidate values before callbacks can change their owner directory.
Keep the exact callee directory boundary, original premise, call and context.
No input from a different caller or ND premise may participate in a join.
Keep the new-input LinkIn path and later strong-query admission unchanged,
even when normalization adds no new caller-store state. Do not expand this
first scope to TAINT/must/ANY normalization without their additional reader,
emitter and query-admission equivalence proofs.

Do not erase mx, merge mark dependencies, or replace several supported
premises by their union. Header normalization must use the existing T2/T4
algebra and republish affected merged values when an annotation coarsens.
Requests still use their separate E/mx-independent matching rule.

Expected memory benefit is less accumulator history and fewer index value
memberships. Family factoring alone does not remove trie positions/topology;
it adds family-member metadata and can increase candidate expansion CPU.
The full 2.32 GB or 1.44 GB is not a saving estimate. Before selecting it,
measure header redundancy and compare current postings with the sum of
distinct positions per proposed family. If that difference is small, omit
family factoring rather than adding another lookup layer.

Prove union equivalence under every permitted header replacement, identical
summary/request replay for both arrival orders, stable admissions across
growth, layer-specific ND bindings, full-premise authorization, candidate
completeness and counted work through late subscriptions and cancellation.
This is an algorithmic candidate whose avoidable fraction is not yet known.

## Coalesce summary growth before publication

**Current:** raw summary storage merges new facts, but each admitted delta
also creates a `Publication` in an actor list. Publication waits until the
method fact queue is empty. `PublicationIndex` then merges those fragments
again. The current engine already has the method-queue-empty barrier; adding
another barrier is not the proposed change.

**Proposed:** merge already-authorized pending pieces by whole premise at
the same production point and flush them at the same barrier. Let the
existing publication index subtract its already-published state. Avoid
retaining each intermediate fragment and sending it through later joins.

```kotlin
fun queuePublications(premise: PremiseKey, delta: Facts) {
    val selected = selectedAsToday(premise, delta)
    for (piece in published(premise, delta)) {
        if (selected) summaries.addDemand(premise, piece)
        pendingMerged.add(premise, piece) // existing conclusion normalization
    }
    if (!pendingMerged.isEmpty) ensureExistingCountedMethodWork()
}

// Existing method-fact-queue-empty barrier.
val detached = pendingMerged
pendingMerged = PendingPublications()
publicationSink.publish(key, detached.immutablePublications())
// PublicationIndex emits only growth beyond its previous merged state.
```

This is an algorithmic change in delta production. Expected benefits are
fewer temporary fragments, smaller pending state, and fewer repeated caller
insertions. Conductor's pending roots retain only 43.7 MB; the 1,485,029
fragments cover 30,118 full premises summed per actor. The OOM impact is
therefore small even if most fragments can merge. Account for the replacement
groups and their normalization work when measuring the net benefit. It does not eliminate
the final required conclusion relation.

Required proofs cover equality of the authorized relation under T4 E/mx
coarsening, eventual published growth, late subscribers, multiple flushes,
complete-premise authorization, current-query growth, original initial
identity and cancellation. Keep counted work through
detachment, publication and secured notifications. Preserve default summaries
as ordinary summaries and retain selected guidance when it is produced.
No mutable pending-store cursor may escape the owner or publication lock.

Do not substitute the selected-guidance store for pending publications:
ordinary NORMAL scalar publications can be absent there. Do not reread only
raw summaries for dirty-on-add keys. Query growth currently republishes
existing raw summaries without a new raw delta; it must still pass through
this common queue point. Keep restriction and guidance-selection timing as
today. On publication failure, restore detached work under the existing
failure policy before propagating the error.

Use independent closure comparison for diamonds, recursion, overwrite kills,
sources and sinks with complex conditions, and multi-premise summaries.
Compare full locations and premise support, not only the number of summaries.

## Reuse cleaner geometry and bypass idempotent FLOW updates

**Current:** cleaner execution can rebuild Results containers and repeat
type/path filtering even when a selected FLOW mark is already excluded.
Field cleaner programs also execute typed read, clean and write steps.

**Proposed:** avoid a value reconstruction when the selected FLOW operation
is already idempotent. Preserve its request effect. Reuse immutable checked
geometry within the same exact filter/owner scope for repeated mark handling.

```kotlin
// Plain selected FLOW cleaning; field program steps still execute.
if (selectedMark in input.markExclusion) {
    if (run1) output.markRequest(selectedMark)
    output.result(input)
    return
}
```

Place the guard before eager `FlowClean`/`Results` allocation where possible.
STATIC additionally needs the existing spatial-overlap decision before it
can request a mark. A disjoint field must not create a request.

```kotlin
val geometry = checkGeometry(inputShape, exactFilterOwner)
for (markBatch in selectedMarkBatches) {
    applyMarkEffect(geometry, markBatch, originalPremise, output)
}
```

The geometry lifetime is the existing operation/form scope. The key must
include every input that affects the filter: base/path/tail, exclusions,
method/context and filter/alias state as required by the actual helper.
Use an immutable value or an operation-local result, not a global identity
cache. This proposal changes neither IR storage nor reference management.

Expected benefit is less type/geometry work and allocation, especially in
OWASP run-1. The inclusive sample percentages overlap; they are opportunity
evidence, not a promised speedup or retained-memory saving.

Do not skip a whole field cleaner because its mark is absent or excluded.
Its typed read/write, cut, temporary removal, identity candidate and other
outputs still matter. Preserve concrete partial-cleaning behavior, layer-only
demotion, real cuts, original request slots, and cancellation checkpoints.
Verify the independent scalar cleaner relation and the existing JVM cases.

## Admit mark sets once and suppress empty-delta wake-ups

**Current:** R and temporary P deduplicate storage, but redundant offers still
reach subtraction, insertion and scheduling. `collectRequests` calls
`requestWork()` even if P accepted no new mark. OWASP has 61.01 million
already-pending offered members; ThingsBoard has 206.39 million admitted or
pending duplicates.

**Proposed:** admit a borrowed mark set for one validated original slot in
one owner operation. Perform the R/P freshness decision inside that operation,
avoid constructing empty deltas, and schedule only the first transition to
nonempty P. The temporary storage remains write-only for answering purposes.

```kotlin
val wasEmpty = temporary.isEmpty
val grew = temporary.addFresh(slot, offeredMarks, authoritativeRequests)
if (grew && wasEmpty) ensureExistingCountedMethodWork()

// The existing barrier remains unchanged.
summaryStorage.commit(temporary)
temporary = RequestStorage()
```

Batch only requests with the same original slot. Keep actual NNF rule
evaluation, source/sink roles, actions and full ND support separate. Concrete
facts do not propagate requests. Matching continues to ignore field E and
FLOW mx, while ordinary summary application retains their normal semantics.

Expected benefit is fewer scalar admission operations, temporary allocations
and scheduler calls. Storage already merges memberships, so this does not
promise to remove all request-production cost or all retained request marks.
The first fresh offer must reserve counted work even when no fact was added.
Prove that a pending nonempty P keeps that reservation across quantum yields,
commit, replacement and notification. A duplicate-only offer must not delay
the existing method-queue-empty flush or create a quiescence gap.

## Correctness prerequisite: preserve concrete exit locations

PDF misses two exact main locations. Real returns feed one synthetic normal
exit; its witness is exported as Simple, and the location resolver chooses
the last return without a trace. The earlier return spans remain strict
losses. Conductor separately drops one candidate with an invalid synthetic
location. These are not reasons to relax comparison identity.

A separate correctness design must retain or resolve the concrete return
occurrence and original call location through provenance. Verify all return
branches, exception paths, lambdas, method-exit rules, and source spans against
main. This is an acceptance blocker, not a retained-memory optimization.
The current proposal does not implement a trace or location rewrite.

## Ideas that are not selected

Erasing FLOW mx is not equivalent to the current F1 contract. For example,
an abstract summary for `clean T(p); return p` uses mx to remove actual T
when that summary executes inside F1. Erasing mx and adding DEMAND returns
T/DEMAND instead and can enable additional sources and sink candidates.
The measured implementation's cross-iteration quarantine does not prevent
those within-F1 effects. Planned NORMAL reuse also requires retaining mx
across iterations. A new
may-abstraction would need separate coverage/precision proofs and measured mx
diversity. No such change is selected here.

A SoftReferenceManager rewrite and a WeakIdentityCache remain possible future
cache-policy work, not part of this iteration. Current cumulative cache
counters do not distinguish first builds from rebuilds and do not establish
their retained-memory benefit. Any future selection needs a specific design,
cache-key/lifetime proofs and source-bound measurements. Reclaimable form
payloads already use the existing managed reference mechanism.

## Measurement and selection gate

All three retained-owner reviews and the weighted static census are complete.
The origin-label and header-family overlap/rebuild measurements above remain
selection gates for their conditional proposals. Prefer removing duplicate
work or unnecessary retained relations; use representation changes where the
remaining relation is required and measured occupancy justifies them.

For a selected change, keep the fixed configurations and direct Gradle task.
The selected static FLOW equivalence and ordinary F1 NORMAL reuse must pass
their proof and control obligations above before implementation measurements can replace
the current results.
Measure run-1 engine separately, every later reached/completed/accepted
iteration, complete command time, strict full sink locations, multiplicity,
GC/allocation and OOM ownership. The current application precision failures
and all 16 missing main locations remain visible. No further analyzer change
is authorized merely by listing it in this proposal.
