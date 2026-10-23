# Run-1 FULL return cleanup and static supply batching

Date: 23 October 2026. Status: **implemented; project measurements pending**.
Reference analyzer: `8ab1445b8167a4b2db73f97788653aa4e21a3933`.
Documentation parent: `7554c06fc32b202b292a9f418108206313dae460`.
Read the [proof dossier](run1-full-return-cleanup-and-static-supply-batching-proofs.md)
and [proof guide](proof-guide.md).
Read the [implementation validation](../docs/reports/bidirectional-full-work-batching-validation-20261023.md)
for the runtime adapters, exact-reference controls and remaining failed gates.

The reference already selects FULL when the run-1 flag is absent. The
[FULL investigation](../docs/reports/bidirectional-thingsboard-full-run1-investigation-20261023.md)
uses explicit FULL on measured runtime `b68b3a1c9`. Each Thingsboard FULL
attempt reaches **run-1 OOM**, with no COMPLETE or accepted forward run.
This specification adds no project measurement or performance acceptance.

## 1. Selected changes and unchanged behavior

Select exactly these two changes:

| Change | Current behavior | Required new behavior |
| --- | --- | --- |
| Temporary static supplies | Retain every computed region/atom packet until the queue-empty barrier; remove repeats in the global service | After the same actual binding and field limit, union FULL atoms by complete region in the temporary batch |
| FULL return cleanup | Execute selected-mark return cleaners one at a time; retain separate raw-step resume points | Execute a contiguous equal-geometry block as one exact FULL-mask operation; keep its requests, spatial results and suffix behavior |

Both optimizations execute only in forward run-1 with FULL marks. An
explicit GROUP selection keeps its scalar cleanup schedule and ordered
supply route. Later runs and the direct raw static-service API keep their
current behavior. This scope avoids a second mode change in this iteration.

The unchanged default schedule is run-1 forward/FULL/limit 1, run-2
backward/FULL/limit 1, run-3 forward/FULL/limit 2, then alternating direction
with increasing limits. A public field limit of zero is invalid.

The required semantic property is equality of supported completed
closures, including normal summaries, DEMAND summaries, source hits and
logical sink/rule occurrences. Callback packet size, temporary point IDs,
step counters and batch-dependent diagnostic counts can change. No
equality of failed prefixes, callback order or callback counts is required.

Keep these mechanisms:

- Full mark identity, rule owners, GeneratedMark identity, primitive
  tracking and existing source, sink and cleaner rule specialization.
- The field grammar, field limit, tails, field exclusions, mark
  exclusions and existing run-1 DEMAND policy.
- All contributing origins. A ZERO-origin result still needs a complete
  cube supported only by ZERO origins. Do not choose one representative.
- Actual-input binding, local micro-edge writer registration, first
  ordinary caller bindings and producer effects before S-to-S erasure.
- The three-set static service, canonical field answers, static call
  handling, run-2 query completion and later FULL execution.
- Controller exit rules, annotation checks, report admission and native
  record rules. `trackFactsReachAnalysisEnd` remains empty.
- The method-queue-empty barrier, admitted-work accounting, cancellation
  checks, static-disable flag and existing release order.

Do not implement pending-image cohorts from the previous proposal. Do not
add grounding support, field-sensitive writer eligibility, a global
concrete relation archive, a member census or a reference-manager change.
Do not filter Spring registry fields or change field processing.

## 2. Values and observations

A full mark is an immutable key `(topLevelRuleId, fullMarkName)`. Its local
runtime atom ID denotes that complete key. Equal names under different
owners remain different marks. A mask is a finite set of those atoms;
it is not a group wildcard.

A region is the complete immutable `Pattern` after clearing its concrete
mark to `STAR`. Its key includes base, complete accessor path, tail kind
and field exclusion. Marks move to the region's mask. Different tails,
fields or exclusions do not become one region.

The selected run-1 supply grammar contains named static fields and EXACT
class markers. Class accessors do not consume the field budget. The field
limit is one; field exclusions remain empty. An EXACT class marker is a
special global mark, not a class-wide mutable field. A broad class query
descriptor is not an emitted class-wide marked fact.

Run-1 static initials retain the current named-field `C.f/*` descriptor
and exact class-marker forms. Nonstatic initials retain an empty abstract
tail. A named-field initial must not become `C.f/[any]`. Current bounded
conclusions can have EXACT, ANY or MUST annotations. This design changes
neither initial creation nor conclusion normalization.

Compare typed scalar observations. A conclusion observation contains its
complete original premise, method, base, path, tail, field exclusion,
mark and layer. Keep EXACT and MUST/ANY distinctions. A source or sink
observation also keeps the complete rule/action occurrence and location.
Combining packets must not combine origins or invent mark/path pairs.
Do not merge unequal specialized rule values or binding keys merely
because their top-level rule IDs and locations are equal. Preserve each
logical occurrence and its multiplicity. The callback-count allowance
does not authorize dropping report variants.

## 3. Temporary static supply storage

### 3.1 Producer boundary

Keep the reference pipeline:

```text
local executed micro-edge or first ordinary caller binding
  -> existing bounded static output
  -> existing computed-group actual binding
  -> existing AP composition and field limit
  -> supply admission below
  -> S-to-S row erasure, where applicable
```

Do not move admission before actual binding in this iteration. Do not use
summary admission as its replacement: an erased S-to-S row never enters
that store. Local FLOW writer admission and all other producer effects
remain independent of supply merging.

Use the new admission at each of the three reference append sites in
`Run1PointResume`: a bound known-static-input result, a ZERO-premise
result, and a completed fixed-mark binding in `publishMatched`.

### 3.2 Whole-batch profile

Choose the profile when the actor creates its batch:

```kotlin
enum class SupplyProfile { CANONICAL_RUN1_FULL, ORDERED_REFERENCE }

val profile = if (config.run1 && config.direction == Direction.FORWARD && mode.markMode == FULL)
    CANONICAL_RUN1_FULL else ORDERED_REFERENCE
```

The direct raw API defaults to `ORDERED_REFERENCE`. In that profile,
retain the reference list and its order. Do not switch profile per packet
or after merging has started: the original ordering can already be lost.
Carry the selected profile into every replacement batch.

The canonical production profile requires the grammar in section 2.
Validate it at the producer boundary. Do not repair an invalid region by
dropping exclusions or widening its path. The raw API remains available
for broader compatibility inputs.

### 3.3 Canonical delta

```kotlin
class MergedSupplies {
    private val rows = LinkedHashMap<Pattern, MarkSet>()

    fun offerBounded(region: Pattern, fullAtoms: MarkSet) {
        cancellation.checkpoint()
        if (fullAtoms.isEmpty) return
        val key = Run1StaticService.region(region)
        val old = rows[key] ?: MarkSet.EMPTY
        val fresh = fullAtoms - old
        if (!fresh.isEmpty) rows[key] = old + fresh
    }

    fun forEach(emit: (Pattern, MarkSet) -> Unit) {
        for ((region, atoms) in rows) {
            cancellation.checkpoint()
            emit(region, atoms)
        }
    }

    val isEmpty get() = rows.isEmpty()
}
```

This is pseudocode. The actual class must use owned immutable keys and
the existing mark-set/interner rules. It must retain no completed batch
history. Empty masks do not admit a write or an output row.

Keep the other `StaticBatch` lanes: reads, interests, reached writes,
static-origin FLOW writers and nonstatic-origin FLOW writers. This
selection does not merge or reorder those lanes.

### 3.4 Commit and the static service

Keep the current phase order:

```text
read registrations
mark interests
static-origin FLOW writer registrations
nonstatic-origin FLOW writer registrations
reached writes
computed supplies
secure addressed effect deliveries
```

For a named field, the service has disjoint sets:

```text
req:       requested, not yet supplied
reach:     supplied, not yet requested
processed: requested and supplied
```

A request answers from `reach union processed`, moves those atoms to
`processed`, and stores only unanswered memberships. New requests notify
existing writers once. A supply adds fresh atoms, moves matching `req`
atoms to `processed`, answers their pending owners and removes answered
memberships. An unrequested new supply does not notify old FLOW writers.
A new writer reads `req union processed` and retains its existing
once-admission set. These transitions do not change.

Field replies use the service's existing canonical field region. An atom
does not receive different reply geometry because a different supply
packet arrived first. Read activation still depends on reached writes.
EXACT class markers remain separate from fields. Under empty exclusions,
every compatible class-marker reply is that same EXACT point.

For canonical values, per-region atom union preserves final state and
atomic effects under arbitrary packet grouping. Effects run after the
complete commit. The proof does not require equal effect packet counts.

Do not apply that argument to unrestricted raw class inputs. For example:

```text
interest: C/EXACT/U
T and U are distinct FULL keys.
P: C/STAR/E={f}       Q: C/EXACT/E={}
old order: P/{T}, Q/{U}, P/{U}
unordered regrouping: P/{T,U}, Q/{U}
```

The first matching geometry for U can differ. Keep this input on the
unchanged ordered raw route. It is not a reason to alter class matching.

### 3.5 Barrier and lifetime

```kotlin
// Same owning-actor queue-empty boundary as the reference:
val submitted = staticBatch
val effects = staticService.commit(submitted)
staticBatch = StaticBatch(sameSupplyProfile)
for (effect in effects) check(sendStatic(effect))
// Only then may this Work reservation drain.
```

A quantum yield keeps the current batch. Include its merged rows in
`isEmpty` and pending-work tests. Do not add a size cap or an earlier
flush. Keep synchronous commit, replacement, secured sends and join/release
ownership. Cancellation cannot turn partially secured work into COMPLETE.

## 4. FULL return-cleanup blocks

### 4.1 Eligibility and barriers

Enable execution only for forward run-1 FULL and the return-rewriter
Actions stage `RETURNED -> REWRITTEN`. Keep the raw `Actions.steps` list,
stage ordinals and action occurrences unchanged.

A block is a maximal contiguous range of at least two eligible steps.
Each step must be a plain `CleanStep.Clean` with:

- A selected FULL mark, not RemoveAllMarks or a group selector.
- A base other than ZERO, STATIC or CleanerTemporary.
- The same complete base, path contents and `CleanReach` as every other
  step in the block.

A field read/clean/write step, Kill step, different geometry, unselected
mark or other effect ends the block. Never cross a stage, source action,
condition, sink observation, callee operation or memory-effect guard.
STATIC cleaners retain their Reach/MarkInput behavior on the scalar path.

### 4.2 Immutable metadata

```kotlin
data class FullCleanupBlock(
    val first: Int,          // original inclusive raw index
    val end: Int,            // original exclusive raw index
    val geometry: CleanerGeometry,
    val marks: MarkSet,      // complete FULL keys; no member enumeration
)
```

Keep one descriptor/mask per maximal block and a linear-size index from
raw step to its containing block. Do not store a mask for every suffix:
that can require quadratic space. For the normal entry at `first`, reuse
the block mask. For an entry `j > first`, compute the distinct marks in
raw `[j,end)` in temporary invocation-local storage.

Compile metadata deterministically from the owned raw steps. Cache rebuild
must recover the same raw range and geometry. Do not use arrival order,
mutable set iteration, fresh point IDs or current pending state in the
address. Reversed Actions keep their current raw scalar steps. Later FULL
runs do not execute this schedule, even if a shared FULL form has metadata.

### 4.3 Collector-fixed input gate

The serial cleaner includes `run1Policy`, `forceRun1Layer`, `run1Cover`
and the cleaner result dispatcher. A mark-set proof alone is insufficient.

Use the block only when every current PlanFact is fixed by this collector:

```text
FLOW/TAINT field E is empty.
TAINT is already DEMAND.
If the current base or original is static, the current layer is DEMAND.
FULL runtime atoms obey the existing manager/mark domain.
```

These are the current production invariants, not new field restrictions.
Reach keeps its existing layer, subject to the same static-original rule.
The gate needs only the existing headers and input contract; do not scan
the entire trie to establish it.

If the gate fails for any input, execute the old one-step loop for all
inputs. Recheck eligibility for the next raw suffix. Do not normalize
the input first to make it eligible: that could move an exclusion cut
ahead of a cleaner and change its spatial result.

### 4.4 Bulk AP operation

Let M be the distinct FULL marks in the active raw suffix `[j,end)`.
The operation has these cases:

| Input | Required operation |
| --- | --- |
| Different base | Return the unchanged value through the existing result dispatcher |
| Reach | Keep the existing no-request result and dispatcher |
| Same-base FLOW | Keep root, base, tail, layer and field E; add all M to mark exclusion; request all M |
| Same-base packed TAINT | Use `gone(S) = S intersect M` inside the existing spatial cleaner kernel for each exact/any payload |

The nonstatic FLOW primitive currently ignores path/reach after its base
match. Keep that behavior. Do not add a spatial filter. Request all M,
including atoms already in incoming mx; do not compute `M - mx`.

For TAINT, retain `cleanSpine`, `cleanPosAt` and the current partial-result
cases. Do not call `withoutMarks` on the entire tree. Keep EXACT, MAY/ANY
and MUST payload carriers and every DEMAND complement. Pack outputs only
under the same existing result key `(base, layer, E, anyTail)`.

In particular:

```text
Input:    p/[any]/T, DEMAND
Cleaner:  RemoveMark(p, T, EXACT)
Required: p/[any]/T, DEMAND   // possible descendants remain
```

An EXACT root cleaner is partial for an abstract subtree. An empty path
does not authorize removal of its request or complement.

The BELOW case can replace a selected abstract leaf at the cleaner point
with an EXACT leaf at that point. That EXACT leaf is disjoint from BELOW.
A MUST leaf immediately above a non-EXACT cleaner can retain a DEMAND
MUST complement and add the stable BELOW exact-point exception. Keep both
reference results. These cases make repeated selected cleans idempotent
without replacing the field operation with a new approximation.

### 4.5 Existing result dispatch is mandatory

```kotlin
fun acceptCleanerResult(input: PlanFact, result: Facts) {
    // Existing FULL projection/layer/cover policy still applies.
    existingCollect(input, result) { premise, value ->
        if (value.base == STATIC) promoteStaticSummary(premise, value)
        else next.add(planFact(premise, value, input.identityCandidate))
    }
}
```

For a STATIC input and a nonstatic cleaner, the base mismatch returns the
same value. The old first step promotes and consumes it. The bulk path
must do the same; it must not append that input to the next-list or call
suffix. An empty suffix has no cleaner dispatch. Do not confuse it with
a nonempty block whose marks do not occur in the input.

Within a pure block there are no source/sink observations or field cuts.
Empty-E FULL TAINT inputs are DEMAND, and selected cleanup preserves the
collector-fixed domain. Thus replacing intermediate collector/dedup passes
with the final pass preserves normalized scalar results. Keep the old
dispatcher, not a new pass-through shortcut.

## 5. Requests, saved images and resumption

### 5.1 One active suffix address

The active entry index is j. It can be the first step or a middle step.
Use the existing site type:

```kotlin
val site = ResumeSite.CleanerInput(
    operation = operation(node, rawStageOrdinal),
    step = j,
    phase = CleanerPhase.CLEAN,
)
```

The suffix mask is exactly the marks in `[j,end)`. Do not restart at the
original block start. Do not include a mark that occurs only before j.
Do not renumber raw stages/steps or create a rule occurrence for the block.

Keep the original premise, current base/tail/E/layer, `callerLayer`,
`identityCandidate` and `pendingDemotion`. Preserve the existing static-
disable gates when delivering a mask to the request owner.

### 5.2 Request owner and storage

```kotlin
// Schematic owner seam, with the existing gates and mark domain:
fun needFullMask(at: OperationInput, mask: MarkSet) {
    if (!config.run1 || at.facts !is FlowTree) return
    val initial = at.premise as? InitialAp ?: return
    if (initial.mark !is MarkSlot.Star || dropsStaticField(initial)) return
    if (initial.base == STATIC) pointResumes.request(at, mask)
    else addOrdinaryMarkRequests(initial, mask)
}
```

This is a set overload of the current owner route, not a new request
manager. Ordinary requests still use temporary/summary RequestStorage and
the current queue-empty commit. Static-origin requests still use the
existing PointRow map `atom -> PendingFlowImages`. No image-cohort storage
change is selected.

During a nonstatic selected FLOW block, each raw step has the same input
root and non-address point headers. Its raw step address differs, and mx
grows. The existing point key and image payload intentionally omit mx.
Register each requested atom with
that same immutable root at active entry j. Do not multiply an independent
path set and mark set. Keep empty-row removal and detach-before-callback.

### 5.3 Reply and suffix execution

```text
canonical field/class supply for T
  -> existing PointRow detaches T and its exact pending root
  -> existing per-leaf materialization
  -> existing canonical initial for T, with its existing tail
  -> CleanerInput(raw stage, active entry j, CLEAN)
  -> current FULL bulk suffix [j,end)
  -> ordinary execution from end of block
```

A static reply materializes one selected atom T at a time. Distinct-mark
cleaners before the first T in the suffix cannot change that scalar-T
input. Repeated T cleaners are idempotent under the current spatial laws.
The result at block exit therefore equals the old continuation from T's
raw request step. Keep the PART DEMAND result where required.

An ordinary invocation can contain several marks. It must process the
complete intersection with the suffix mask, not only one arriving atom.
No concrete input is allowed to generate a mark request.

### 5.4 Relation between old and new pending points

Old requests use each raw step k; new requests use the active entry j.
One old row can merge images from several invocations, while the new
rows can separate those images by entry j. Numeric point IDs can also
change. Do not assert a bijection of whole PointRows.

Use atomic request attempts as proof data:

```text
(method, operation, active entry j, old occurrence k,
 complete point header, selected atom T, immutable FLOW image)
```

For each old attempt, k belongs to `[j,end)` and selects T. The new suffix
mask contains T. Conversely, each new T has an executable raw-occurrence
witness k in that suffix. The image is unchanged. After materialization,
both continuations have the same block-exit scalar observations.

These witnesses are formal proof data. Do not add a runtime history,
support label, old-to-new-ID table or answered-request archive.

### 5.5 Owning call-loop seam

The following pseudocode replaces only the eligible range of the existing
`cleanChain` loop. Keep its scalar step body as the fallback.

```kotlin
var j = firstStep
var current = listOf(inputPlanFact)
while (j < actions.steps.size) {
    cancellation.checkpoint()
    val block = fullSchedule?.blockContaining(j)
    if (enabledRun1FullReturnCleanup && block != null &&
        current.all { collectorFixed(it) }) {
        val mask = block.marksFrom(j) // cache only the full block mask
        val next = LinkedHashSet<PlanFact>()
        for (input in current) {
            val at = cleanerInput(input, rawStageOrdinal, j, CLEAN)
            cleanSelectedFullMask(input.facts, block.geometry, mask,
                request = { marks -> needFullMask(at, marks) },
                result = { value -> existingCleanerDispatch(input, value, next) })
        }
        current = next.toList()
        j = block.end
    } else {
        current = existingScalarStep(j, firstPhaseAt(j), current)
        j++
    }
}
emitExistingStageOutputs(current)
```

For plain Clean steps, INPUT and CLEAN entry use the same old cleaner
operation. READ/WRITE phases belong to Field steps and stay on their
existing path. A scalar Field continuation can finish, then enter a later
eligible plain block. No phase is reinterpreted as a different operation.

`cleanSelectedFullMask` must have an explicit selected-mask argument. Do
not encode its geometry with `Cleaner.mark = null`: null means
RemoveAllMarks in the current primitive. Its result wrapper remains the
existing FULL run-1 policy and dispatcher. Its TAINT implementation reuses
the spatial kernel with a mask intersection; its FLOW implementation
shares the input root and emits the complete request mask.

No new rule-provider layer or form-cache owner is needed. The selected
implementation seams are `StaticBatch`/its producer appends, the service's
supply iterator, return Actions metadata, the FULL cleaner kernel, the
call-loop adapter and a mask overload of the existing request-owner route.

## 6. Soundness and later-run guidance

The [dossier](run1-full-return-cleanup-and-static-supply-batching-proofs.md)
proves the local laws and their composition. The formal models separately
check supply state/effects, cleanup kernels/suffixes and finite derivation
transport. They are not a mechanized proof of the Kotlin adapters. Their
runtime correspondence checks are recorded in the implementation validation.

For each finite supported reference derivation, construct a new one:

1. Keep unchanged program, call, rule, source and origin steps.
2. Replace a supply-list membership by its equal merged membership.
   Canonical service transitions preserve the corresponding atomic effect.
3. Replace a pure cleanup segment by its equal typed scalar output set.
4. For a request/reply crossing a block, use the raw occurrence and image
   witness from section 5, then the suffix equivalence.
5. Apply the same collector, dispatcher and ordinary storage admission.

The construction works in both directions. It preserves every required
join member; an empty premise list stays empty. It preserves logical
source/sink occurrence keys. It does not preserve diagnostic callback
multiplicity or internal intermediate point addresses.

Consequently, completed run-1 has the same retained normal and DEMAND
summary rows and the same logical sink seeds. The unchanged handoff
normalizes each retained row to the same demand entries. Its implicit
static query-completion policy is also unchanged. Run-2 sees the same FULL
mark requirements and actual-input starts. Replaying the same later FULL
rules gives each reference source hit and later witness. This is relative
preservation; it does not repair existing baseline precision failures or
prove termination for arbitrary projects.

## 7. Implementation order and required checks

The implementation follows this order. The validation record identifies
the checks that have run and the remaining project measurements:

1. Build and audit the constructive models and proof controls.
2. Add differential tests against the unchanged scalar/list reference.
3. Add the merged canonical supply lane and preserve the raw profile.
4. Add deterministic linear-size block metadata, then the FULL bulk kernel.
5. Add the mask request route and raw-suffix resume adapter.
6. Run source-to-runtime adapter checks and actual handoff tests.
7. Run the fixed project measurements and report the results separately.

| Area | Required controls |
| --- | --- |
| Supply admission | Repeats, overlapping masks, empty masks, complete region keys, same name/different owners, input binding before offer |
| Static service | Request/supply in both orders; late read/writer; answered removal; req/reach/processed disjointness; unrequested arrival does not wake old writers |
| Raw compatibility | Broad-class first-hit counterexample stays on the ordered path; field and EXACT class separation |
| Block compiler | Barriers, singleton fallback, repeated FULL atoms, same name/different owner, different base/path/reach, deterministic rebuild, linear metadata size |
| Bulk AP | Base mismatch, Reach, packed exact/ANY/MUST, empty selected intersection, existing mx, EXACT-root PART, BELOW exact exception, idempotent repeated marks |
| Collector/dispatcher | Empty-E fixed domain, nonfixed fallback, all-current-input gate, STATIC pass-through promotion/consumption, no new field cut |
| Requests/resume | Ordinary and static origins, packed/scalar replies, raw middle entry, mark only before entry, duplicate marks, late images, detach before callback, complete call state |
| Semantic integration | Each contributing origin, exact all-ZERO rule case, source/sink occurrence IDs, actual run-2 marked source and run-3 witness, normal and DEMAND rows |
| Lifetime | Quantum yield, request-only work, commit/replacement, cancellation before/after sends, no false COMPLETE, release after join |

Compare normalized typed scalar observations and logical occurrences,
not only endpoint counts. Keep both-mode and later-run regressions.
Do not update a failed expectation merely because the optimized result
has fewer rows. A mismatch requires an adapter correction or a stated
behavior decision.

## 8. Work and measurement claims

Supply storage changes from one packet per offer to one row per complete
region and its distinct atom set within the batch. Equal-output service
offers disappear; actual binding and composition still run. The measured
two-batch duplicate count is 1,768,503 records. The 2.648x bound applies
only to all pending supply records in that FULL run-1 snapshot.

For k equal-geometry cleanup steps and n trie nodes, scalar execution can
visit the geometry k times and copy progressively larger mx headers.
The bulk kernel visits that geometry once per input and applies the exact
mask. Mask construction, result packing and per-origin materialization
remain work. Space includes raw steps plus linear block metadata; do not
trade the scalar loop for quadratic suffix masks.

Count eligible block lengths, suffix entries, scalar fallbacks, node
visits, header copies, request atoms, pending rows, supply offers, fresh
memberships, commits, owner effects and form rebuilds. Preserve the
existing fixed configurations, rule inputs, aliases and static setting.
Measure FULL reference versus FULL optimized; do not substitute a GROUP
control. Use run-1 engine time for the 60-second target.

Report latest reached, latest COMPLETE and latest accepted forward run
for every command. Take the normal profile window only if run-1 is still
active. Keep diagnostic heap capture separate from useful-work timing.
Investigate every OOM; a smaller failed prefix is not acceptance. Neither
the local proofs nor the allocation scopes establish a whole-engine 2x
gain or guarantee run-1 completion within 60 seconds.
