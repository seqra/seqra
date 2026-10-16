# Reduce run-1 input-index and method-state costs

Date: 16 October 2026. Status: detailed proposal based on measured HEAD
`05cf9ad6246bf5071da5499318ed9a9354c1e430`, with runtime `4acde739b`.
All six required OOM snapshots have closed owner reviews. No change below is
implemented. The ranking uses these fresh measurements.

Use the [current performance report](../reports/bidirectional-static-domain-frozen-requests-performance-20261016.md).
Its evidence root is called B below.
The closed owner census and gross-bound derivations are in
`B/measurement-review/six-heap-owners-20261016T105655Z/`.
The [exception and frozen-request design](bidirectional-static-domain-and-frozen-requests-20261016.md)
defines the iteration just measured. Earlier proposals do not define the next
ranking. The 60-second target is completed run-1 engine time, with limit exactly
1. IR storage is outside this proposal.

## Current progress and decision

| Final project | Latest reached | Latest completed engine | Accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 |
| Conductor | run-1, forward; OOM | None | None |
| ThingsBoard | run-1, forward; OOM | None | None |
| Stirling-PDF | run-4, backward; OOM | run-3, forward | run-3 |

The current ThingsBoard run-1 heap contains 2,410,745 actor initial memberships
but only 3,936 canonical initial objects. Of those canonical initials, 3,919 have
the abstract mark; only 17 have a concrete mark, including zero. Thus there are
few canonical input shapes and many memberships across method actors. This is
not evidence that millions of different concrete input marks need propagation.
Output TAINT leaves already pack marks.

The principal measured input owner is the exact caller subscription relation,
called N2 in the code. Its retained union is 2,371,746,016 bytes; the positional
index selection is 1,621,602,496 bytes. Method fact directories retain
2,006,855,232 bytes. These unions overlap with other owners and are not amounts
that can all be removed. Pending worklists, events and R/P request stores are
smaller owners. The next iteration should address standing input and method
state, with a separate CPU investigation for form construction and AP operations.

Use an algorithmic change where it removes unnecessary index participation.
Use a smaller representation for entries that must remain. Moving an owner or
renaming a store does not remove its exact input relation.

| Priority | Proposal | Current behavior → new behavior | Expected effect and limit |
| --- | --- | --- | --- |
| 1 | Allocate conclusion containers only when used | Every conclusion bucket owns both kind maps, a reach array and eager layer lists → lazy kind/layer bodies and reach bits | Current empty-kind map shells account for at least 243 MB in ThingsBoard run-1, 180 MB in Conductor run-1 and 378 MB in PDF run-4; replacement costs remain to be measured |
| 2 | Store empty and singleton index postings directly | Every trie node owns a linked set/map → allocate a collection only for multiple postings | Empty/singleton-node shells account for at least 238 MB in ThingsBoard and 66 MB in Conductor; preserve all required postings |
| 3 | Route explicit untouched static fields through a frame lane | Every admitted input enters generic N2 and its path index → proven untouched field inputs wait for normal return in a compact frame relation | Avoid generic summary matching and positional trie entries for eligible inputs; conservative all-static footprints limit eligibility, and the live eligible fraction is not yet measured |
| 4 | Retain only selected bound rules; make the remaining bound-list cache reclaimable | Method-bound lists stay strongly cached before outer selection → apply the same selection before retention, with binding/validation preserved, and reclaim reproducible lists with the existing reference manager | Reduce part of the 1.09 GB normal sink-cache owner; selected/discarded fractions and rebuild cost need measurement |
| 5 | Defer persisted-alias edge construction until its guard is needed | Call-plan build materializes aliases before testing MemoryEffect → construct the same alias edges on the first admitted use | Reduce construction and temporary allocation; this is mainly a CPU candidate, with no measured retained-byte estimate |

Rank by supported memory opportunity on the final workloads. The first two
changes have measured container costs and preserve the algorithm. Prefer the
algorithmic frame route if its cohort count and proof establish a larger useful
reduction. Current all-static footprints prevent that assumption, so frame
routing is a gated candidate rather than a promised saving from the 2.37 GB
input union. Cache selection and alias deferral also need their eligible shares.
No retained owner union is a predicted saving.

## Lazy conclusion storage

The all-bucket union contains 4,947,165 ConclusionGroup objects, 9,894,330
HashMaps, 9,658,816 ArrayLists and 4,829,408 per-layer directory arrays in the
ThingsBoard snapshot. The current constructor allocates a reach BooleanArray
and both FLOW/TAINT maps. Each base group allocates two layer lists. These
containers exist even when only one kind or layer is used.
The class census and constructor establish at least 5,064,922 empty kind maps,
with 243,116,256 current shallow bytes, in ThingsBoard. The corresponding
Conductor lower bound is 179,719,200 bytes. PDF's run-4 bound is 378,101,616
bytes; it measures the later backward state, not run-1. Each nonempty kind map
needs at least one instantiated base group; subtracting all base groups from the two maps per
conclusion gives this conservative bound. It is not the whole bucket payload.

```kotlin
class ConclusionGroup {
    private var reachBits: Int = 0
    private var flow: MutableMap<AccessPathBase, FlowGroup>? = null
    private var taint: MutableMap<AccessPathBase, TaintGroup>? = null

    fun add(f: Facts): Facts? = when (f) {
        is Reach -> addReachBit(f)
        is FlowTree -> flowGroups().getOrPut(f.base, ::newFlowGroup).add(f)
        is TaintTree -> taintGroups().getOrPut(f.base, ::newTaintGroup).add(f)
    }
}
```

Keep the existing algebra in FlowGroup/TaintGroup. Initially change only empty
kind and layer allocation and reach-bit storage. A singleton base body can be a
separate follow-up after a cardinality census. Preserve base iteration order,
layer order, the exact T2 annotation-merge rules, deltas and cancellation. A
group's absence must mean empty state, not a missing first transfer.
Read optional bodies at the same traversal time as the old sequence: do not
capture an empty sequence before a later permitted insertion becomes visible.

Compare against the old implementation on independently generated sequences
with both layers, E/mx changes, multiple bases, FLOW/TAINT and REACH. Check each
returned delta and complete state after every insertion. Reuse existing AP,
summary and ND tests. Measure live bytes and allocation, not just class counts.

The 206,959,448 bytes of dense statement arrays are a different target. Their
occupancy is unknown and they are only 1.81% of the ThingsBoard reachable heap.
Do not combine an array rewrite with this bucket change or promise the full
2.01 GB fact-directory union. Keep sparse statement arrays for a later measured
iteration.

## Compact index postings

ThingsBoard's input index contains 5,852,153 trie nodes and the same number of
linked posting-set/map shells, for 5,082,315 postings. Children maps are already
lazy; this proposal concerns the eager `values` container.

```kotlin
private class Node<V : Any> {
    var count = 0
    var payload: Any? = null // null, V, or an owned ordered set
    var children: Int2ObjectOpenHashMap<Node<V>>? = null

    fun add(value: V): Boolean = when (count) {
        0 -> { payload = value; count = 1; true }
        1 -> if (payload == value) false else {
            payload = linkedSetOf(singleValue(), value)
            count = 2
            true
        }
        else -> manyValues().add(value).also { if (it) count++ }
    }
}
```

The count distinguishes states even when V itself is a collection. Keep current
equality, insertion order, positional duplicates and owner-only mutation. Every
lookup/visit must yield exactly the same values in the same order, with existing
cancellation checkpoints. Public snapshots must not expose a mutable set.

First use this representation for the existing generic index. A primitive-int
specialization for N2 handles is a separate measured step; it can remove boxed
handles but must preserve order and duplicate suppression. Test promotion,
duplicates before/after promotion, one value at multiple positions, prefix and
extension queries, empty paths, and mutation guards during request visits.

Measure empty/singleton/multiple node counts and actual object layout. Do not
predict a saving from node count alone or claim all 1.62 GB is container overhead.
There can be at most half as many multiple-posting nodes as total postings.
The actual ThingsBoard census therefore gives at least 3,310,996 empty/singleton
nodes and 238,391,712 bytes of their current set/map shells. Conductor's lower
bound is 65,649,960 bytes. These are gross structural bounds, not measured
cardinality distributions or net replacement savings.

## Explicit untouched static fields

Current input delivery retains the whole post-ADDED subscription and indexes all
its new leaf positions. Owned-field versus untouched-frame projection occurs
later, during summary or record application. The core already handles untouched
identity correctly; the proposal changes how it waits for that result.

Start with a narrow classifier. It accepts only a static tree under one explicit
class/field branch, without a broad root/class leaf or a direct class point.
The frozen callee footprint H must prove that this field is unowned. Keep every
other input on the existing path.

The current footprint is conservative. `freezePlan` starts root needs with
`cleanerMarks`, then sets `footprint.allStatic` when that root is nonempty or
`effectAllStatic` holds. For such a callee there is no untouched field under the
current proof. Static-input dominance does not establish an eligible cohort.
Narrowing this footprint would need a separate metadata and effect-coverage
design; it is not part of the frame route.

Existing heap address joins classify all 68,822 ThingsBoard actor/emitter
borrowers: 17,233 use an all-static footprint and 51,589 do not. Conductor has
27 all-static borrowers and 17,438 others. These are borrower counts, not input
rows or their bytes. A non-all-static footprint may still own every field in
its actual inputs. The eligible subscription cohort remains unmeasured.
The joined evidence is `B/heap-analysis/operations/remaining-five-20261016T100346.967803Z/footprint-owner-scope.json`.

```kotlin
// Design types and helpers; not current production APIs.
fun register(input: Subscription) {
    // Register before reading cumulative publications. A return may already
    // exist before the first frame input and need never be notified again.
    val summaries = ensureRegisteredWithSummaryStorage(input.callee)
    if (summaries.hasPublishedNormalStaticReturn())
        releaseRegisteredFrames(input.callee)
    // One exact accumulator owns both lanes. Classify its CURRENT full header,
    // never only the arriving fragment. Preserve its original admitted key.
    val admitted = inputs.admit(input) ?: return
    val field = explicitSingleStaticField(admitted.current.added)
    val frozenFootprint = frozenFootprintOrNull(input.callee)
    if (!run1 || routes.isGeneric(admitted.handle) ||
        field == null || frozenFootprint == null || frozenFootprint.owns(field)) {
        if (routes.promoteToGeneric(admitted.handle)) {
            frames.removeHandle(admitted.handle)
            genericIndex.addAllPositions(admitted.current)
            replayGeneric(admitted.current)
        } else {
            genericIndex.addNewPositions(admitted.fresh)
            replayGeneric(admitted.fresh)
        }
    } else {
        frames.addHandle(input.callee, field, admitted.handle)
        if (normalReturns.contains(input.callee))
            queueExistingFrameEffect(admitted.fresh, identityCandidate = true)
    }
    if (run1 && !admitted.current.zeroOnly &&
        admitted.current.added is FlowTree &&
        normalRun1Slot(admitted.current.ref.premise) != null)
        replayCurrentRequests(admitted.current) // same current FLOW header in either lane
    // Keep original callee input emission, including the special static root.
    // This sketch omits the existing separate LinkIn delivery, not its work.
}

fun onNormalReturn(callee: MethodKey, evidence: Publication) {
    if (!existingSpecialRootReturnPredicate(evidence)) return
    releaseRegisteredFrames(callee)
}

fun releaseRegisteredFrames(callee: MethodKey) {
    if (!normalReturns.add(callee)) return
    frames.visit(callee) { queueExistingFrameEffect(it.current, identityCandidate = true) }
}

fun onFreshRequests(callee: MethodKey, rows: RequestRows) {
    genericInputs.joinRequests(callee, rows)
    frames.visitMatchingFieldRows(callee, rows) { input, matchingRows ->
        existingRequestJoin(input.current, matchingRows)
    }
}
```

The existing exact input table remains the sole payload owner. The two routes
retain handles, not a second copy of the subscription facts. A flat field row
can own frame handles directly; it does not need generic ancestor/descendant
trie nodes. Generic rows stay generic after promotion. If a frame row grows
beyond the narrow shape, remove its frame handle, index every current leaf once
and replay the cumulative input through the generic path. Already queued frame
effects keep their immutable snapshots and remain valid monotone effects.
Do not add a full input mirror at the abstraction or callee owner.

The input table, routes, frame handles and return flags belong to the same
caller runner as current N2. Publisher threads queue notifications and never
read or mutate that directory. The authoritative cumulative return read is
under SummaryStorage's existing lock and applies the same special-premise and
NORMAL root-leaf predicate. A monotone derived flag may implement that read;
it stores no second publication or input payload. Clear it at the existing run
lifetime boundary. Read it only after subscriber registration.
The existing notification join must finish before freeze clears inputs, route
handles, frame rows and return flags. None of these extends the run lifetime.

`queueExistingFrameEffect` means the current static-frame effect at the callee
stage's exit. It preserves the original caller premise, caller layer, current
E/mx and the current frame identity flag `true`. The caller then executes the remaining
cleaners, rewrites, bind-back and result filters in their existing order. It
does not jump directly to the next CFG statement.

Requests ignore field exclusions and FLOW mark exclusions. Therefore a broad
residual such as root/E{owned fields} cannot be silently removed from request
replay. The narrow classifier avoids that case. Even its frame lane must
perform the same current-input/current-R join and fresh-request replay; do not
assume requests for an unowned field never arrive. Concrete-origin inputs still
cannot climb requests. Keep the inherited-B and changed-origin rules.

Required proof and tests:

1. H is frozen and complete. Unowned means no relevant callee access or rule
   effect, independent of current request membership. Class points stay separate.
2. Only actual normal-root return evidence releases a frame. A nonreturning
   callee releases none. Full context and entry remain part of the callee key.
3. For each admitted exact input, the union of generic and frame effects equals
   current summary/request effects. Preserve the original GroupKey before delta
   projection and every original caller/call/layer.
4. Both orders are complete: register before replaying a known return/request;
   commit a new return/request before visiting registered inputs. Input growth
   reads current headers. Queue all effects after a finite visit and account for
   delivery before releasing in-flight work.
5. No broad root/class, mixed field or class-point input enters this first lane.
   Test E/mx, frame-to-generic promotion, changed original slots, fields outside S, late requests,
   concrete no-climb, two callers, cycles, cancellation and rejected sends.
6. Keep native/opposite-record paths unchanged in the first run-1-only version.
   A later migration needs its own provenance and return-guard proof.

Before selection, count eligible live rows, their index postings and payload
bytes on the measured workloads. Measure both setup and joins. Reject a design
that replaces a trie with repeated full-frame scans.

## Bound-rule retention

The measured 1,092,007,080-byte owner is the normal call/entry/exit **sink**
configuration caches. The allRelevant caches retain only 144 bytes in this
snapshot. The whole Spring provider is a broader owner. Source-cache savings
are not measured by the sink selection.

The method binder already rejects statically false conditions. Adding the same
test again is not an optimization. Later call and boundary rewriting can reject
more rules, but one calling context's rejection cannot delete a method-wide
cache entry. In particular, an entry rule's `condition.isTrue` eligibility is
not a proof that every nontrue condition is globally false.

First move immutable serialized-rule-ID selection before normal cache insertion.
Keep current binding and validation before that filter. The existing Semgrep
reduction includes auxiliary rule dependencies;
use that exact retained-ID set, not just requested sink IDs. Rules without an ID
keep their current unconditional selection. `allRelevant` deliberately bypasses
this filter and must still see the complete definition storage.

```kotlin
// Conceptual dependency order, not a new production API.
val selection = FrozenRuleSelection.fromExistingDependencyReduction(provider)
fun cacheMiss(method: JIRMethod, role: RuleRole, allRelevant: Boolean): List<BoundRule> {
    val bound = resolveAndValidateExactlyAsNow(method, role, allRelevant)
    if (allRelevant || !normalSelectionAppliesTo(role)) return bound
    return bound.filter { selection.includesOrHasNoId(it.serializedId) }
}
// Preserve the old selected, method-bound order for occurrence ordinals.
// Do not compact the complete definition storage or replace local ordinals.
```

The null filter means select all; it differs from an explicitly empty set.
Unselected invalid definitions must still raise their current validation errors,
including unsupported static-field source conditions. This first step avoids
retention, not initial binding. A later pre-bind filter requires proof that all
validation still occurs and that item IDs correspond to resolved IDs.
Keep complete definitions and the
separate allRelevant path. The current adapter filters source/sink queries but
does not apply this selection to cleaners or pass-through rules. Preserve that
role distinction; do not install a universal filter in shared storage.
Freeze selection before cache use. If the API changes selection later, invalidate
normal cached lists or key them by selection generation; a previously rejected
rule must be recoverable under the new selection. Already borrowed old-run forms
retain their valid lifetime.
Only move a filter when it depends on immutable
selection, not on a
fact, alias result, call context or runtime mark condition. Apply the design to
source and sink providers, with each role's actual semantics. Keep signed NNF
potential-mark inventory and live source/sink needs. Do not convert NNF to cubes
or drop an unknown branch. Prove equal full rule IDs and occurrence/action keys;
list compaction must not silently renumber them.

For still-useful lists, a separate option is a reclaimable cache using the
existing SoftReferenceManager:

```kotlin
fun rulesFor(method: JIRMethod): List<BoundRule> =
    withManagedPayload(method) { cached -> cached ?: bindSelected(method) }
```

The cache needs a pure, reproducible builder, one-owner construction, strong
borrowing during use and correct disabled-manager behavior. Rebuild must keep
full rule equality, generated IDs, order and occurrence ordinals. Preserve
already borrowed forms and in-flight rule effects. A cleared value is a cache
miss, not permission to skip a rule. Compare forced-clear/uncached executions
with the current cache, including source predicates, empty-action sources,
empty-end sinks, call/entry/exit eligibility and negative mark literals.

This trades memory for binding work. Record selected and discarded live lists,
their exclusive structures, hits, rebuilds and time by role before choosing it.
The full 1.09 GB union is not a safely releasable estimate. Existing FormsCache
already uses managed references; adding them there again is not a proposal.

## Deferred alias construction

Call-plan construction accounts for 41.70% of final ThingsBoard run-1 Java CPU
samples and 28.52% in OWASP. Alias acquisition accounts for 26.08%/13.52%, with
overlap. The AA owner computes lazily once per method. These shares include
queries, intersections and path conversion, not just AA construction.

The current builder materializes persisted-alias edges, then puts them behind
Guard.MemoryEffect. Investigate constructing that edge payload only when the
guard is admitted:

```kotlin
// A detached recipe; do not capture the whole call-plan builder/provider.
data class AliasRecipe(val method: CommonMethod, val call: CommonInst,
                       val localTargets: List<AccessPathBase.LocalVar>)

fun applyAliasStage(recipe: AliasRecipe, state: CallState) {
    if (state.forward && !existingMemoryEffectGuard(state)) return
    withAliasPayload(recipe, state.direction) { exactOldEdges ->
        applyExistingEdges(exactOldEdges, state)
    }
}
```

Prove that every admitted forward and reversed use forces the same edges with
the same AP manager, call site and target geometry. Preserve stage ordering,
normal/exception paths, cancellation and cache borrow lifetime. Metadata still
must inventory every potential producer and effect under its unknown-alias
policy. A runtime optimization must not shrink H or Sigma.
Backward stages are currently unguarded. They must force the reversed payload;
do not copy the forward MemoryEffect test into backward execution.

First count guard-admitted versus unused alias stages and separate first AA
compute, query/conversion, template construction and cache rebuilds. Current
form counters do not distinguish cold builds from rebuilt slots. If nearly all
stages are used, this can add indirection without removing work. A shared
method/call/local-target payload is an alternative only after proving context
independence; retain context-dependent call resolution and rule rewriting.

## Further candidates and exclusions

An origin-labelled abstract FLOW representation could share mark-neutral
transfers across different original static initials. This has greater
algorithmic reach, but needs a separate design and actual cohort measurement.
Its labels are **alternative origins**, not a PremiseSet conjunction. The
required law is:

```text
project(origin, groupedTransfer(state))
    = currentScalarTransfer(origin, project(origin, state))
```

Keep output-path/original-premise correlation, E/mx/layer partitions, exact
source/sink effects, requests, summaries and AND support. A flat union of facts
cannot supply every output to every original premise. Concrete-mark-only
grouping is not the leading hypothesis for the measured canonical vocabulary.

Moving the join to the callee owner can consolidate broadcasts and duplicated
prefixes. It still requires the full input-to-caller relation and does not
remove all N2 rows. Measure that duplication before selecting an ownership
migration; do not add a second retained input directory during the move.

A SoftReferenceManager rewrite or a new WeakIdentityCache remains a possible
future cache optimization, with its own measured contention/rebuild/lifetime
case and design. Neither is needed by the proposals above. No rewrite or new
identity cache is implemented or implied. IR storage remains outside scope.

## Implementation and verification order

1. Use the closed six-heap comparison and this ranking as the control. Keep
   reached, completed and accepted runs with each measurement. Before selecting a frame
   or cache change, count eligible frame rows and selected rule-cache shares.
   Measure posting cardinalities and bucket occupancy for the selected layouts.
2. Implement lazy bucket bodies first if selected. Preserve the old algebra and
   prove equal deltas/state with an independent operation sequence. Measure the
   changed object layout; do not claim the gross shell bound as net savings.
3. Implement compact postings as a separate change if selected. Prove equal
   ordered visits for prefix/extension queries and request joins, including
   duplicate insertions and collection-valued payloads.
4. For frame routing, first write the local simulation proof against the current
   generic protocol. Include known-return registration, late requests, cumulative
   growth, promotion, identity provenance and shutdown. Implement only when its
   measured cohort justifies the change and the design is selected.
5. Keep rule-cache policy and alias deferral separate, with their own selection,
   validation, rebuild and reverse-stage controls. Do not combine a new owner,
   scheduler or reference-manager policy with these changes.
6. Run existing common/JVM and strict application gates. Preserve every failure,
   exact rule ID, raw span and multiplicity. Do not relax current eight strict
   application failures or replace earlier return locations with later ones.
7. Repeat the same four fixed projects through direct `runProjectAnalyzer` with
   their current memory/time/model/rule settings. Compare against freshly pinned
   main and the immediate pre-change control. Measure every engine and barrier,
   setup, reached/completed/accepted runs, normal/late CPU windows and OOM owners.

Correctness blockers stay explicit. Two PDF location losses come from synthetic
method-exit reporting. Actual observations also show unconditional returned-source
cleaners removing the baseline URL route's ARGS in forward and backward passes.
Fixing those behaviors needs its own design and regression cases; none of these
memory proposals justifies changing source/sink semantics. Resource-failed
Conductor/ThingsBoard reports do not establish baseline recall.
