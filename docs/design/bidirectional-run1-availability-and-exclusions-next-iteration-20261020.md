# Next iteration: static waits, caller-summary effects and immutable exclusions

Date: 20 October 2026. Status: next-iteration proposal; measurement review complete.
No optimization in this file is implemented. Follow
[the proof guide](../../spec/proof-guide.md) before implementation.

Use the [fresh performance report](../reports/bidirectional-run1-static-request-state-performance-20261020.md).
The normal measured runtime is `6ad77c6b1`; the permanent diagnostic flag is
in `0f7feafd8`. Older candidate reports are not the basis for this proposal.
Thingsboard and conductor reach run-1 and fail there. OWASP and Stirling-PDF
complete run-1, then fail in run-2. All four miss the 60-second run-1 target.

## Proposed order and semantic scope

Rank memory work first, then repeated CPU/allocation work. The thingsboard
F flag-off run-1 heap retains 4,875,984,808 bytes in the static-service/point
union, compared with 215,383,776 bytes in the method-edge union. These unions
overlap; do not add them. Complete actor directories contain 5,566,789 operation
points. This supports targeting unresolved continuations before changing
ordinary fact representation. The same-F flag pair ends OOM in run-1 on
both sides: 304.550 seconds normally and 664.799 seconds with real fields
disabled. These are failed durations, not completion times. The disabled
variant has no real-field pending state, so this first candidate cannot be
the complete run-1 solution. The disabled leaf's closed run-1 heap retains
1,934,496,744 bytes in ordinary predicate/conjunction owners, 1,647,320,184
in method-edge owners and 1,111,817,432 in caller/publication/request owners.
Its static-service/point union is only 58,746,112 bytes. These unions also
overlap. The remaining work is not a large queue: its whole heap has 4,501
`EdgeDelta` objects. It contains millions of standing bindings and per-site
current values. Ordinary completed-effect sharing is therefore a separate
candidate. Its actual signature-collision factor is not yet measured.
The control's predicate/conjunction union is only 15,403,752 bytes at its
earlier OOM. The second memory target is conditional: the diagnostic does
not prove equal ordinary growth in normal analysis. Measure normal-context
effect collisions before selecting it from the disabled heap alone.
Stirling-PDF's separate run-2 heap has no run-1 service or point owner;
method-edge owners retain 2,524,034,256 bytes with 1,138,023 premise-directory
entries for non-REACH conclusions, including any ZERO-premised TAINT entries.
The first candidate targets run-1. It does not resolve that later
OOM; later-run premise growth needs a separate investigation.

| Candidate | Current behavior | Proposed behavior | Expected benefit |
| --- | --- | --- | --- |
| Wait for static group availability before building image continuations | An absent field/group retains a separate continuation for each image, call binding or writer input | Keep a merged owner/field wait; reconstruct work from existing authorities when the group arrives | Avoid large unresolved continuation collections and their construction |
| Retain one record per completed caller-summary effect | Each E6 complete member tuple is retained and reapplied to growing conclusions | Keep exact member admission, then deduplicate by complete caller-premise union, layer and resume context | Avoid equal completed effects and repeated union work; actual collision count remains unknown |
| Use immutable exclusion metadata and direct GROUP set operations | Each projection scans atom keys; GROUP-only normalization rebuilds temporary collections | Record owner-qualified shape once; use identity or direct GROUP/ZERO set operations | Remove repeated scans, catalog locking and normalization allocation |

This is an order by memory opportunity. Exclusion metadata has the clearest
independent CPU evidence and can be tested separately. No candidate has a
measured completion-time gain yet.

All candidates must preserve the normal analyzer's semantic observations.
Only batch-dependent diagnostic callback counts can vary. No new source
validation, grounding support, field transfer, mark meaning, primitive policy,
caller merge, exclusion rule, layer rule or run schedule is selected.

Keep the current source/sink condition projection and simplification. Keep
selected-mark cleaner requests and DEMAND repair; a selected cleaner still
cannot clean on run-1. Keep `RemoveAllMarks` and structural kills. Keep empty
run-1 field exclusions and the existing overwrite/demotion rules. Keep current
static write publication, writer derivations, request matching, exact `S.C.$`
markers and class-ANY query metadata. Zero satisfies a static-field premise
only on run-1. Later runs still require the real premise. Keep the current
handoff and the full-mark backward run-2 at field limit 1.

The diagnostic flag is outside the equivalence contract: when enabled it
intentionally removes flows. Its timing is not a prediction for the
preserving candidates. None changes IR storage, SoftReferenceManager,
rule census, mark-member enumeration, or MarkTable ownership.

## Wait for static group availability

The F thingsboard control ends run-1 with 8,310,678 pending continuation/group
memberships, including special class interests. Its snapshot has only 1,320
requested-but-unwritten normal field/group pairs and 36 processed pairs.
Conductor's normal `6ad77c6b1` run-1 has 3,139,662 pending memberships.
The F heap's complete actor-directory sums separate 5,566,789 points,
798,593 writer inputs, 47,222 calls, 2,019 direct inputs and 608 join inputs.
These are actor directories, not a join
to committed service IDs; do not treat their sum as the service-row count.
The two largest field states each have 34 requested-but-unwritten groups.
The three-set protocol can release answered work, but these waits still need
future notification. Keeping that logical need does not require a separate
saved image for every operation.

The current key is effectively `(method owner, continuation ID)`. An
operation point additionally retains its site, original premise, current
FLOW leaf, layer and call state. Several current leaves can require the same
static field/group. MethodEdgeStore deduplication does not merge these wait
payloads.

```java
Object get() { return C.f; }
void use() {
    Object x = get();
    a.left = x;
    b.right = x;
    sink_G(a.left);
    sink_G(b.right);
}
```

Assume a FLOW write has admitted the read, but group G is not yet available.
The dependency can have initial `P = (S, C.f, *, {}, FLOW)` and current FLOW
images `x.*`, `a.left.*`, `b.right.*` and call arguments. Each edge with this
static initial is DEMAND. The facts are legal and distinct. The repeated
logical need is `(C.f, G)`.

```text
Before:
    retain P -> current images in ordinary stores
    also retain Point(site1, P, image1, callState1) waiting for G
    also retain Point(site2, P, image2, callState2) waiting for G
    ... retain another wait payload for each admitted image

After, while G is absent:
    retain the same ordinary dependencies
    retain waiting[C.f][owner] += G
    retain no image-specific absent-G payload

After G first arrives:
    notify owner once for the selected field/groups
    borrow the authoritative current inputs
    execute the existing repair at its original operation/stage
    admit outputs through the ordinary deduplicating stores
```

Keep the service and actor ownership separate. The service owns global field
availability and merged waits. It must not inspect or mutate MethodEdgeStore.
The method actor owns replay and borrows its own current inputs.

| Authority | Data reused for replay |
| --- | --- |
| MethodEdgeStore | Current statement/premise values |
| Existing read-admission rows | Original read operation/site and delivered FLOW slices |
| PublicationIndex / SummaryStorage | Complete current publications and conclusions |
| CalleeSubscriptions | Exact caller ports, inputs, statement, stage and layers |
| RunSummaryStore and actor writer selectors | Complete writer premise and current output identity |
| Static-service FLOW-writer rows | Already-admitted writer/group masks |
| Existing ordinary ND stores | Complete binding-qualified conjunction state |

### Smaller point-key change to test first

The point repair has one useful equivalence: it discards the current FLOW
mx before it applies the answer. All other uses of the pending row inspect
its waiting mask or key, not that mx. For valid run-1 inputs, two points with
the same site, original initial, current AP geometry, field exclusions,
layer and call state therefore give the same repair if only current mx
differs. Their service query is the same original initial.

```text
Same operation, initial and call state; both request H:
    P -> x.* / FLOW, mx={G}
    P -> x.* / FLOW, mx={G,J}
Current: two pending rows; each later repairs x/H with empty mx
New:     one pending row; the same x/H repair
```

```kotlin
// After the existing input/catalog validation; point repair only.
val repairImage = image.copy(fact = image.fact.copy(mark = MarkSlot.STAR))
val key = PointKey(at.site, original, repairImage, flow.layer, at.callState)
// Store the same mx-empty repair relation in the point payload.
```

This can merge equivalent waiting masks without owner replay. It does not
merge sites, callers, layers, field geometry or original premises. It must
not change a writer key, stored ordinary FLOW header or writer-summary mx.
Statement anchors still have their actual mx; admitted read injections start
with empty mx. Do not assume that every anchor is already canonical.

Prove repair equality, catalog validation, both-order replies, merged waiting
masks and closed-row removal before this change. The distinct-key count for
a fixed offered point set cannot increase under this quotient. The current
heap packet does not measure how many keys differ only in mx, so its actual
saving is unknown. Use it as a small independently measured step, not a claim
that it removes the measured 4.88 GB graph or replaces the replay proof.

### Availability and queue-empty commit

```kotlin
fun needAtPoint(at: OperationInput, field: Field, wanted: GroupSet) {
    val ready = service.peekAvailable(field, wanted) // read-only, monotone
    if (!ready.isEmpty) retainExistingTransientPoint(at, field, ready)
    temporary.ownerFieldNeeds.add(field, wanted - ready)
    // Neither path mutates global requests or repairs synchronously.
    requestWork() // also required for a request-only delivery event
}

fun methodQueueEmpty() {
    summaryStorage.commit(temporaryOrdinaryRequests)
    temporaryOrdinaryRequests = RequestStorage()
    val effects = service.commit(owner, temporary) // existing static barrier
    temporary = StaticBatch()
    effects.forEach(::sendReservedStatic) // outside the service lock
}

fun commit(owner: MethodKey, batch: StaticBatch): List<ReservedStaticEffect> {
    val effects = mutableListOf<ReservedStaticEffect>()
    synchronized(serviceLock) {
        commitExistingReadyContinuationsAndOtherEffects(batch, effects)
        for ((field, wanted) in batch.ownerFieldNeeds) {
            admitLogicalNeedAndWriterClosure(field, wanted, effects)
            val ready = wanted intersect available(field)
            reserveOwnerReplay(owner, field, ready, effects)
            waiting[field][owner] += wanted - ready
        }
    }
    return effects
}

fun firstGroupWrite(field: Field, arrived: GroupSet) {
    val effects = mutableListOf<ReservedStaticEffect>()
    synchronized(serviceLock) {
        val fresh = admitWriterDerivation(field, arrived)
        for ((owner, groups) in waitingOwners(field, fresh)) {
            reserveOwnerReplay(owner, field, groups, effects)
            removeWaitingMemberships(owner, field, groups)
        }
    }
    effects.forEach(::sendReservedStatic)
}

fun onAvailable(field: Field, groups: GroupSet) {
    replayQueue.merge(field, groups)
    requestWork()
}
```

The snippets specify behavior, not a final locking API. `peekAvailable` does
not admit a request. Ready inputs use the current exact transient continuation
and queued reply; they do not trigger a new owner-wide scan. Missing inputs
keep only a merged temporary need. If a write arrives between peek and commit,
commit queues owner replay for that need. Otherwise it installs the absent
watch. Both paths admit the logical request at the same queue-empty barrier.
Apply this split to each covered continuation family, not points alone.
Preserve the other existing work-drain and publication steps at that barrier.
Keep the availability probe small. It can borrow a service-owned immutable
availability snapshot. A stale omission is safe: commit rechecks under the
lock and queues replay. A positive result must always name an actually
written group. Do not add a synchronized catalog scan per input to replace
the saved allocation with another hot lock.

Queue actor effects outside the service lock, with a secured work reservation before the wait is
removed. An unanswered need holds no work reservation. Preserve both arrival
orders, late publications/subscribers and the existing method-queue-empty
commit barrier. Do not add a global barrier or pending-memory cap.

Keep disjoint `req`, `reach` and `processed`. A new unrequested group does
not wake existing FLOW writers. A request moves a reached group to processed
and asks eligible writers once. A new FLOW writer asks for `req + processed`.
Canonical field answers remain `S.C.f.*/G`, DEMAND, with empty exclusions.
Writer/group admission masks belong to the static service's FLOW-writer rows,
not the actor's `WriterKey`. On input availability, replay only already
admitted output groups from that authority. Do not treat every existing
writer as newly admitted or wake it for an unrequested output group.

### Reconstruction is the main proof obligation

Re-adding an unchanged fact is not replay: MethodEdgeStore deduplication
suppresses it. Add explicit actor replay work that executes the existing
interpreter on borrowed inputs. New facts after the replay cut use the normal
path and see the now-available group.

A scan of MethodEdgeStore alone is incomplete. ReadOutput, BoundaryInput,
CallStageInput and CleanerInput can be transient. Reuse existing read and
caller/publication anchors. Rebuild forms through their current scoped cache.
Do not store a full plan. If an input family cannot be reconstructed, retain
one minimal operation-family replay anchor independent of requested groups;
prove its coverage before deleting that family's old payloads.

Preserve the original complete premise, method context, statement, call stage,
caller/link layer, identityCandidate, pendingDemotion and cleaner phase.
Replaying a different entry point or merging callers would be a semantic
change. One satisfied ND member must not fire a complete conjunction.
Ordinary ND stores do not automatically reconstruct the separate all-static
fixed-group `ConditionalJoin`. Prove how its complete premise is recovered
before deleting its member waits or output-specific payload.

In GROUP mode, the current `MarkGate.flowRequest` ignores mx. Do not infer
that a request disappears when a later FLOW value excludes G. Preserve the
existing request-before-exclusion rule. Point repair clears mx only on its
repair relation; writer composition keeps the actual summary mx. Do not
introduce a new exclusion-history store or change either matching rule.

Keep ordinary FLOW dependency propagation. Dropping it merely because G is
absent can break request bootstrap or condition evaluation. This candidate
removes duplicate unresolved-work payloads; it does not remove those semantic
dependencies or invent early marked propagation.

For each removed old continuation, construct a witness authority and replay
step that produces the same effects after availability. Prove current merged
inputs cover old images, both-order delivery, complete ND/caller matching,
quiescence, and packed/leaf observation equivalence. Also prove per-method
DEMAND handoff coverage, including getter/helper/dead-local paths. An equal
final static pool or equal final sink set alone is insufficient.

For P missing-group memberships, let R be distinct owner/field pairs and M
distinct owner/field/group memberships. The new wait directory has O(R) rows
and O(M) membership space. With A reconstruction anchors, D transient ready
continuations and I in-flight work, the bound is O(R + M + A + D + I), plus
unchanged ordinary and writer stores. Prove and measure these terms before
claiming a total memory reduction.
This does not bound the ordinary fact stores. Replay can rescan work; no
universal runtime speedup follows from the directory bound. Measure both
replay after first arrival and the common never-reached case.

## Retain one record per completed caller-summary effect

The field-disabled run-1 forms 8,148,071 E6 complete tuples from 8,452,002
fresh member admissions. These are cumulative counters, not live counts.
The closed diagnostic heap confirms a large predicate/conjunction union:
1,934,496,744 bytes, including 8,217,737 `NdBinding` shells and 8,407,378
unmodifiable-list wrappers. The whole heap has 81,775 E6 join/key objects;
the effect quotient is local to each exact key. These physical class counts are not complete
tuple identities. The union includes ordinary local predicates, per-slot
member sets and per-join conclusions. This candidate keeps those stores.
It does not claim to remove the full union or the millions of per-slot
bindings. Exact global markers can participate, so this is not exclusively
a nonstatic-base mechanism.

Current `NdSummaryJoin` keeps per-member inputs and every completed tuple.
It applies each growing conclusion to each tuple. `applyCombination` then
unions the caller premises and reduces the contribution layers again.

```java
Object pair(Object a, Object b) { return modeledCombine(a, b); }
void caller(Object p, Object q) {
    Object x = choose() ? p : q;
    sink_G(pair(x, x));
}
```

Assume the model has the ND summary `{a/G, b/G} -> ret/G`. Let caller
originals be P and Q. Both argument slots can match either original. With
equal layers and resume metadata:

```text
member tuples: (P,P), (P,Q), (Q,P), (Q,Q)
effect premises:   P,   {P,Q},   {P,Q},   Q

Current: retain four tuples; apply each new conclusion four times
New:     retain three effect keys; apply each new conclusion three times
```

Keep the complete `{P,Q}` effect. This is equality of effects, not premise
subset pruning. G and H remain distinct original marks. The local rule
predicate join retains its existing behavior; its inputs can carry facts
that this quotient does not cover.

For one actor and fixed `NdKey`, the exact effect signature is:

```kotlin
data class CallResumeKey(
    val caller: MethodKey, val call: CommonInst,
    val stageOrdinal: Int, val callerLayer: Layer
) // no ref.premise: the complete union is stored separately

data class NdEffect(
    val premise: PremiseKey,       // canonical union, all original members
    val linkLayer: Layer,          // DEMAND if any member contribution is D
    val resume: CallResumeKey      // slot-0 caller context/call/stage/layer
)

fun effectOf(parts: List<NdBinding>): NdEffect = NdEffect(
    premise = unionAllExactlyAsToday(parts.map { it.ref.premise }),
    linkLayer = if (parts.any { it.linkLayer == DEMAND }) DEMAND else NORMAL,
    resume = exactResumeKey(parts.first().ref)
)

fun addMember(slot: Int, input: NdBinding) {
    for (tuple in members.add(slot, input)) { // existing full member matching
        val effect = effectOf(tuple)
        if (!completedEffects.add(effect)) continue
        for (current in conclusions.all()) applyEffect(effect, current)
    }
}

fun addConclusion(incoming: TaintTree) {
    val delta = conclusions.add(incoming) ?: return
    for (effect in completedEffects) applyEffect(effect, delta)
}
```

Keep callee, complete callee premise, publication layer, caller call and
stage in the existing `NdKey`. Keep caller ownership explicit. The resume
key retains the exact slot-0 caller layer and method context. The current
adapter uses no individual matched fact after admission; it reduces link
layers by OR and resumes with `identityCandidate=false` and the existing
default demotion state. Refactor the E6 apply helper to accept this exact
resume key and complete premise. Do not use a method-only key or a union
of unlabelled group sets.
The code above states the complete semantic signature. A physical record
can share the caller method, call and stage once in the existing actor/NdKey
scope; only the slot-0 caller layer varies within that scope. Do not allocate
another full resume object per effect merely to restate fixed key fields.
`applyEffect` keeps the conclusion base, root, field exclusions and tail.
Its output layer is DEMAND if either the conclusion or effect link layer
is DEMAND. It then uses the same collector and call-resume path.

Keep the existing member sets and per-join conclusion accumulator. A newly
formed tuple constructs an effect; later conclusion growth visits effect
keys. There is no persistent partial-product store. The tuple enumeration
cost remains in this first step. Computing each union once also avoids
rebuilding it for every later conclusion update.

Prove `applyCombination(tuple, conclusion)` equals
`applyEffect(effectOf(tuple), conclusion)` with the actual collector/resume
adapter. Give an executable matched-tuple witness for every admitted effect.
The witness is a model/test obligation; the runtime effect stores the
sufficient signature. If a runtime witness is retained, include its O(kE)
references in the memory bound instead of claiming signature-only space.
Prove complete member matching, both arrival orders, late conclusion growth,
layers, ZERO union behavior, caller isolation and per-method demand handoff.
Use an independent tuple-based reference. Tests must include tuples with
equal unions but different slot-0 layers, distinct callers/stages, mixed
link layers, and G/H premise correlations.

For B member records, T complete tuples of arity k and E distinct effects,
the old completed store has O(kT) references; the proposed completed store
has O(E) signature records plus shared canonical premise keys, with E <= T.
The member stores remain O(B). Count signature duplicates before claiming
an actual reduction: all tuples can have different effects. A hash-based
effect set and per-effect resume records can then use more bytes per completed
item than the current tuple list. The measured large owner region establishes a target, not a proved
collapse factor. Measure old retained tuple/list bytes, distinct effect keys,
signature/set bytes and canonical union growth before selection. Current source
also allocates a transient product list before copying the retained tuple;
reusing a frozen list alone removes allocation, not a second retained tuple
history. No such second history exists in `KaryJoin`.

## Use immutable exclusion metadata and direct GROUP sets

In the current measured run-1, exclusion normalization/projection are 39.39%
of OWASP Java leaf samples and 47.68% of conductor's. Normal native ancestry
also contains catalog-lock descendants. These samples do not partition the
individual source branches or establish an exact speedup.
OWASP completes run-1 with 13,897 pending static subscriptions and 25,245
pending memberships, much less than thingsboard. A static-wait-only change
does not explain or address its 885-second engine. Keep this independent
CPU/allocation candidate in the iteration.
The same-F thingsboard diagnostic reinforces this priority. With real static
fields disabled and all real-field pending counters zero, run-1 still OOMs.
Its normal native window has 66.05% exclusion ancestry and 28.55% `TrySpin`
leaf samples. These are overlapping sample shares, not exclusive time or a
predicted speedup. The same runtime header paths remain expensive outside
the real-field pipeline.
Its complete heap has only 438 live `MarkExclusions` wrappers. This candidate
targets repeated CPU, catalog locking and allocation, not a large retained
exclusion-wrapper population.

```java
void use(Object x) {
    remove_T(x);  // selected-mark cleaner for group G
    pass(x);
    pass(x);
    sink_T(x);
}
```

The normal FLOW branch can carry `mx = {G}`. The cleaner still requests G
and preserves its existing complementary DEMAND path. At each unchanged
output/transfer boundary, current code scans the exclusion atom keys to
prove that no FULL atom needs projection. Other set operations normalize
already GROUP-only results again.

```text
Before: same mx -> scan all keys under MarkTable monitor -> same mx
After:  same-owner GROUP_OR_ZERO metadata -> same mx

Before: union/add returns the same raw set -> rebuild normalization anyway
After:  validate owners -> return the existing exclusion object
```

Record immutable shape metadata at the private factory, not per projection.
A trusted GROUP/ZERO-only result can use direct immutable set algebra.
No metadata can be inferred from a DEMAND layer alone.
Operate on existing opaque atom IDs in `MarkSet`, not group IDs in `GroupSet`.
The trusted factory returns the global catalog-neutral EMPTY for an empty
result, including a disjoint intersection. FULL exclusion projection still
accepts an owned symbolic GROUP exclusion; executable FULL atom projection
has its existing different rule.

```kotlin
private enum class Shape { UNBOUND, GROUP_OR_ZERO, GENERIC }

fun projected(table: MarkTable, mode: MarkMode): MarkExclusions {
    if (this === EMPTY) return this // canonical, catalog-neutral empty
    if (mode == FULL) return withCatalog(table) // existing validation
    if (catalog === table && shape == GROUP_OR_ZERO) return this
    return existingGroupProjectionFallback(table)
}

fun union(other: MarkExclusions): MarkExclusions {
    val table = tableWith(other) // validate before any identity return
    if (this === EMPTY) return other
    if (other === EMPTY) return this
    if (sameOwnerGroupOrZero(table) && other.sameOwnerGroupOrZero(table)) {
        val merged = atoms + other.atoms
        if (merged === atoms) return this
        return trustedGroupOrZero(merged, table)
    }
    return existingGenericUnion(other, table)
}
```

The current raw `MarkSet.plus` can return its left operand or a new value.
The snippet uses only that existing identity path. A right-superset shortcut
would need an explicit test. Current intersection always allocates; implement
an explicit equal-result reuse check if intersection identity is selected.
A real changed set still requires set work; this does not make every union
constant time. It removes repeated type scans, catalog lookup, boxing and
second normalization from the closed case. Keep public MarkSet interning
and all output factories, AP cuts, field covers and layer adapters.

Assign metadata only after factory validation/normalization. Trusted closed
GROUP/ZERO algebra can carry it forward without another scan. Shape and
catalog must not enter the existing numeric-atom equality or hash code.
Keep manager interning. The metadata must be owner-qualified and immutable.
Nonempty null-catalog and mixed values retain their current fallback; catalog
attachment can normalize them even if raw set union returned an operand.
Foreign-catalog GROUP projection
retains its existing numeric remapping fallback; do not add a rejection.
FULL attachment/projection still rejects a foreign nonempty catalog,
including a ZERO-only value. EMPTY
stays catalog-neutral. Keep ZERO behavior. A finite FULL set must not become
a group exclusion. Symbolic group exclusion must cover FULL members first
used later. Do not enumerate known members or load/specialize rules to
classify an immutable runtime value.

Prove the metadata invariant, identity denotation, ownership/error behavior,
closed GROUP/ZERO algebra, delayed FULL use and reference interning behavior.
Compare against the current generic algorithms with constructive finite
models and executable counterexamples before runtime changes. Extend the
existing normalization reference tests, then run the full required gates.

## Implementation and measurement gate

After selection, write the complete spec/model before runtime work. Stage
availability replay by input family; retain the old path until each family's
coverage proof and controls pass. Do not use missing reconstruction as a
reason to change field/source semantics. Implement exclusion metadata as a
separate small change so its effect can be measured.
Keep the caller-summary effect quotient separate too. Measure its actual
collision rate and representation overhead; do not infer a saving from the
1.93 GB union or 8.15 million cumulative combinations alone. Its minimal
form still enumerates complete tuples.

Run the four fixed projects on the committed implementation. Report run-1
engine time, outcome, latest reached/completed/accepted iterations, strict
finding identities, profiles and every guard heap. Compare actual allocated
work, pending populations and retained owners. Keep the diagnostic static
flag default-off; its off/on experiment isolates cost, not soundness.

Do not select ordinary duplicate-admission tuning as a leading fix from the
37.98 million offers alone. Temporary deduplication already works and the
current CPU evidence points to larger costs. Keep soft-reference policy,
IR storage, eager bound-rule retention and other old proposals outside this
iteration unless fresh measurements justify a separate selection.
