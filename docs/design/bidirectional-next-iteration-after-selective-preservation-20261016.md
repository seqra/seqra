# Next analyzer-core iteration after selective preservation

Date: 16 October 2026. Measurement runtime: `28a123f0eac937e8f2feca9ee5dd84e38fa8f979`.
Updated: 17 October 2026. Selected next-iteration changes: gate default summaries
on prescan normal-exit reachability and persist eligible run-1 NORMAL summaries.
The new static propagation design makes all static-related run-1 FLOW DEMAND.
Status: proposal only. All three normal heap reports and the targeted
weighted-input/publication checks are complete. No optimization in this
document is implemented.

The [new measurements](../reports/bidirectional-run1-selective-preservation-20261016.md)
replace earlier runtime reports. Latest reached/completed/accepted iterations
are OWASP **run-7 / run-6 / run-5**, conductor **run-2 / run-1 / run-1**,
ThingsBoard **run-1 / none / none**, and Stirling-PDF **run-2 / run-1 / run-1**.
OWASP times out; the other three run out of memory. Conductor and PDF meet
the 60-second run-1 engine target; OWASP and ThingsBoard do not.

The scope is analyzer execution and storage. IR storage is outside scope.
The [unified static/request/field-write contract](../../spec/run1-field-writes.md)
describes the measured implementation. The selected change below replaces
its unconditional default publication and blanket run-1 persistence ban.
The [bottom-up static FLOW specification](../../spec/run1-bottom-up-static-propagation.md)
now defines the proposed static transport: local reader activation, shared
call relations, bottom-up nonidentity effects and full-origin derived inputs.
It includes the new all-static-FLOW DEMAND rule, its static availability
approximation, gaps, proof obligations and measured opportunity. Concrete
TAINT and nonstatic field-write semantics retain their existing paths.

## Selected: gate defaults on normal-exit reachability and reuse NORMAL

**Current:** every reached run-1 method publishes an ordinary `S.* -> S.*`
default at initialization. StaticMayWrite chooses NORMAL or DEMAND, but
normal-exit reachability is not checked. A write-free method that cannot
return normally can therefore supply a NORMAL identity. Its callers can
derive further NORMAL summaries from that continuation. The driver avoids
cross-iteration reuse by skipping persistence for the whole first run.

**Proposed:** after a successful joined prescan, copy whether each method
entry has a ZERO-to-ZERO edge to its normal exit. Emit the static default
only when that bit is true. Under the new static FLOW rule this default is
always DEMAND, including for write-free methods. At the completed run-1
barrier, persist eligible NORMAL summaries through the ordinary record writer.
DEMAND summaries remain guidance for refinement. The gate and persistence
change needs no provisional-summary provenance bit or new request storage.
The separately specified bottom-up transport changes the FLOW call pipeline.

### Prescan result and lifetime

For the exact method entry/context key K, define:

```text
normalExitReachable(K) = exists prescan edge (K, ZERO) -> (normalExit(K), ZERO)
```

Inspect ZERO-to-ZERO edges, not ZERO-to-fact edges, call-graph membership,
an exceptional exit, or a syntactic return instruction. A path through a
local catch/finally block can qualify if it reaches the normal exit. A
backward discovery edge added to reach an exceptional exit is not evidence.

The existing prescan summary storage retains ZERO-to-ZERO exit statements.
Add a narrow value exporter beside the resolved-graph export. It must not
materialize all tainted summaries through `getSummaries()` just to read
one reachability bit. The names below denote proposed adapter methods:

```kotlin
ifdsEngine.requirePrescanJoined() // also require successful prescan status
val calls = ifdsEngine.prescanResolvedGraph()
val normalExits = ifdsEngine.copyNormalExitReachability()
bidi.gather(analysisManager, startMethods, calls, normalExits)
analysisManager.releasePrescan()
ifdsEngine.releasePrescan()
```

Copy immutable keys and Boolean results only. Do not retain a runner,
subscription, analysis context, ZERO edge, AP store or callback into released prescan
owners. Attach the bit to the immutable method-entry plan. F/B/W can still
share their existing CommonMethod plans; the entry/context identity of the
reachability result must not be lost by an unchecked method-wide union.

The export must cover all method entries admitted by the new core, including
root/callee and modeled empty/native entries. Prove the prescan-to-runtime
key mapping. A missing entry is not a known false bit: validate completeness
before starting run-1 instead of silently suppressing a default or assuming
return. Existing prescan model results supply modeled return reachability.
No second call resolution or new whole-program reachability solver is proposed.

**Reachability boundary:** this is reachability in the prescan control-flow
model, not concrete termination or path feasibility. Current JVM prescan
`propagateZeroToZero()` emits both CallToReturnZeroFact and CallToStartZeroFact.
Thus a caller's ZERO edge can include a call-to-return bypass; it is not a
balanced concrete return witness for every nested call. Preserve and test
that existing approximation. Do not claim that this gate removes every
transitive non-termination or exception false positive. Non-termination FPs
remain acceptable; the reuse proof below is relative to the stated prescan
control model and ordinary analyzer approximations.

### Ordinary default publication

Keep the existing retained/live publication path. The new static FLOW rule
supersedes the earlier StaticMayWrite-based NORMAL/DEMAND choice in run-1:

| normalExitReachable(K) | Default |
| --- | --- |
| False | None |
| True | `S.* -> S.* [DEMAND]` |

```kotlin
// Called once by the reached actor's owner, as today.
if (config.run1 && entryPlan.normalExitReachable) {
    summaryDelta(staticIdentityPremise, staticIdentityFacts(Layer.DEMAND))
}
```

The gate applies to the default, now always DEMAND in run-1. It does not gate actor
creation, ZERO discovery, local sources/sinks, ordinary input delivery,
request handling or computed summaries. A method without a normal exit can
still contain a real sink. Continue to emit no abstract `S.*` initial and
apply stored defaults through ordinary subscriptions and summary handlers.
For a DEMAND default, preserve the actual static field/tail and join layers
as today; do not convert the supplied field into `[any]` merely on demotion.

### Persist run-1 NORMAL summaries

Remove the driver-wide `if (!config.run1)` guard around ordinary persistence:

```kotlin
// At the existing successfully completed run barrier, including run-1.
shared.records.persist(
    config.direction,
    result.analyzers.asSequence().map { it.key to it.summaries },
)
val handOff = HandOff.of(config, result, report, shared)
```

Keep the existing writer's eligibility and raw-summary source: NORMAL
singleton premises, including eligible ZERO/concrete/nonstatic FLOW records.
It skips DEMAND and multi-premise rows. This proposal adds no persistent ND
format and never flattens a whole ND premise. A concrete STAR run-1 initial
starts at DEMAND and cannot become a NORMAL record through promotion.
The root premise of a default summary is legal in summary storage even though
it is not a legal run-1 method initial. This run-1 default is DEMAND and does
not pass NORMAL record eligibility.

Eligible computed NORMAL summaries use the same native
and opposite-direction readers as later records. Preserve their geometry,
tail, mark exclusions mx, field annotations, method/entry/context and caller
application rules. Do not erase mx, convert a record to a demand projection,
or add a second default-specific persistence route. The default gate already
acts at emission; persistence needs no extra reachability filter per row.
Opposite views retain the existing reversal admission and null-result guards;
persisting a record does not force a reversed view for an unsupported shape.

Persist only a complete joined run. Failed, cancelled, OOM and timeout
prefixes supply no executable records. The later run receives the existing
read-only record view. Keep demand handoff, actual current-query admission,
late-response replay and complete-premise checks: records are reusable
responses, not a complete catalog of every required response.

Report confirmation and forward stop rules are separate. This selected
change does not automatically confirm run-1 findings or remove mandatory
refinement of its candidates. DEMAND remains guidance only; NORMAL is no
longer discarded solely because its run index is 1.

### Required proofs and controls

Update the affected specification/proof claims before implementation:

1. **Export fidelity and coverage.** Each copied true bit has the exact
   prescan ZERO-to-normal-exit edge for that entry; each such edge yields
   true. Known false entries are complete prescan results. Key translation,
   boundary classification, native models and release preserve this result.
2. **Default classification.** A default exists exactly when the bit is true.
   All run-1 static FLOW defaults are DEMAND, including in write-free methods.
   Prove the static availability cover and sticky layer rule in the new
   specification. DEMAND defaults do not enter executable persistence.
3. **NORMAL derivation reuse.** Prove by induction that a run-1 NORMAL
   derivation refines the ordinary transfer relation under that control model.
   Static-related FLOW, exclusion-requiring residuals and genuine cuts go to
   DEMAND; input, edge
   and rule-premise layers join monotonically. A DEMAND dependency cannot
   supply a NORMAL result. Independent NORMAL source generation must keep
   its own supported derivation. Requests admit actual concrete suppliers;
   they are not evidence for a source/sink condition. Preserve full ND support.
4. **Native and opposite reads.** Prove that eligible persisted NORMAL
   summaries retain their transfer when read later, including at a larger
   positive field limit. Retain suffix correlation, mx, input/output layers
   and normal R1 eligibility. Missing F1 responses remain computable by actual
   later queries; an old request/read inventory cannot veto them.
5. **Coverage after suppressing defaults.** With no prescan normal-exit edge,
   default removal must not remove a returning flow in the selected model.
   Preserve local findings and exceptional/catch paths. Check the model's
   call bypasses explicitly; do not substitute a concrete-return theorem.

These are proof obligations, not claims established by the existing blanket
quarantine proofs. Replace those claims rather than cite them as evidence
for the new rule.

Required controls include straight return; pure infinite loop; always-throw;
throw caught locally before return; caller with a catch; recursive SCC with
and without a returning branch; distinct entries/contexts; modeled native
return; static write and selected static cleaner; broad nonstatic write
demotion versus disjoint NORMAL survival; nonstatic NORMAL FLOW mx reuse;
static-to-local sticky DEMAND; ZERO generation; full ND
support; both arrival orders; larger later limits; and failed prescan/run.
Change the old tests that require no F1 records to check eligible NORMAL
native/opposite reuse and absence of DEMAND records. Keep the exact report
location and precision checks; the accepted non-termination FP does not
permit changing comparison identities.

### Expected effect and implementation order

The change makes eligible run-1 NORMAL transfers available to ordinary record reads
in run-2/run-3 and later iterations. Gating defaults can also avoid their
continuations at entries with no normal-exit reachability. Current call
handling still sends LinkIn and admits actual callee queries even when a
record answers; replaying a record does not cancel that work. Persistence
alone therefore does not establish avoided recomputation. Suppressing a
covered query would need a separate design and is not added here.

Measure response reuse, duplicate derived work and later state before
claiming a time or memory gain. Persistent records retain memory across
iterations. This selected reuse change has an unmeasured net memory effect
and is separate from the other candidates' memory ranking. It promises no
run-1 60-second result.

Implementation order: update the contract and discharge the local laws;
add the narrow joined-prescan export and complete key mapping; gate ordinary
default initialization; enable ordinary F1 NORMAL persistence; update native,
opposite, handoff and failure controls; then repeat the fixed project runs.
Measure exported return bits, suppressed defaults, persisted NORMAL rows,
native/opposite record hits, later derived-state/allocation changes and peak
memory, in addition to every reached/completed/accepted iteration and full
sink identities. No runtime implementation is changed by this document edit.

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
The selected normal-exit gate and F1 NORMAL reuse must pass their proof and
control obligations above before implementation measurements can replace
the current results.
Measure run-1 engine separately, every later reached/completed/accepted
iteration, complete command time, strict full sink locations, multiplicity,
GC/allocation and OOM ownership. The current application precision failures
and all 16 missing main locations remain visible. No further analyzer change
is authorized merely by listing it in this proposal.
