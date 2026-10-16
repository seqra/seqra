# Request-gated static handling

Date: 16 October 2026. Status: proposal; no implementation or new analyzer run.
This is the current proposal for static handling. It replaces the earlier
control-root, return-watcher and pending-frame candidates in the
[ThingsBoard investigation](../reports/bidirectional-thingsboard-static-workload-20261016.md).
The selected [static contract](../../spec/static-handling.md) and
[proof dossier](../../spec/static-handling-proofs.md) now specify run-1 and
the strong run-2/later boundary. The newer [field-write contract](../../spec/run1-field-writes.md)
and [proofs](../../spec/run1-field-write-proofs.md) add DEMAND weak preservation
for every field write, Empty run-1 E and tagged default static templates.
Implementation remains unchanged.

Latest reached: **run-1, forward, limit 1; OOM**. No engine completes and no
forward report is accepted. Measurements execute `4acde739b`, captured at
`05cf9ad62`; current `bcc435310` has the same core tree.

## 1. Selected rules and goal

Reduce redundant static-field analyses, call deliveries, subscriptions and
retained facts. The user-selected rules are:

1. Collect static-field requests from **actual field reads**, including
   effective non-mark checks that read a value. `ContainsMark(C.f,T)` in a
   source or sink condition adds **only a static-mark request**, never a field
   request. Do not add field requests for aliases or static-field write targets.
2. Emit abstract `C.f.* (*)` only when C.f has a field request.
3. Emit a concrete field answer only when its actual supplied mark has a
   static-mark request. The field and mark gates are separate.
4. All static/nonstatic field writes are **weak on run-1**: retain affected
   old data at DEMAND and add independent pre-store value edges. All F1 field
   E stays Empty, including partial-cleaner residuals through a may cover.
5. Do not propagate the special abstract `(S, ., *)` fact on run-1. Apply a
   tagged default static identity at NORMAL if the method/callees cannot
   write statics, DEMAND otherwise. Join with the actual incoming layer.
6. Retain these default schemas symbolically as later query guidance. They
   are not executed root facts or executable strong records.
7. Use run-1 publications only as later guidance. Later actual queries start
   strong callee analysis independently of run-1 field needs or summaries.

Later iterations use strong stores and normal-return call semantics. Their
admission/response and persistence paths need the changes in §8 to preserve
those semantics after root removal. Explicit cleaners remain cleaners.

The parts below are ordered by expected effect on the repeated state:

| Part | Current behavior | Proposed behavior | Expected benefit |
| --- | --- | --- | --- |
| Stop root-driven field expansion | Special S-root travels through calls and can start the whole owned field basis | No special root input/fact; start only requested fields from supplied data | Avoid first field analyses and their downstream states |
| Limit callee data delivery | Every static fact reaching CALLEES is subscribed and linked to each target | Pass statics through locally; deliver only fields/marks requested by that target | Avoid irrelevant deliveries, exact input groups and index entries |
| Separate field reads from mark checks and effects | Writes/aliases and global mark needs can enlarge structural selection | Actual reads seed field requests; mark predicates seed mark interests only | Avoid abstract analyses created by mark-only checks, write targets or alias inventories |
| Use DEMAND weak writes and defaults | Strong writes add field exclusions; old bypass keeps NORMAL | Every field write demotes affected old preservation; precomputed static default joins N/D; F1 E stays Empty | Avoid exclusion variants and propagated root-preservation work while exposing refinement needs |

Weak stores can retain more data at a requested field. Their purpose here is
simpler run-1 semantics; they are not independently claimed to reduce facts.

## 2. Measurement basis

The latest ThingsBoard run-1 heap has these populations:

| Observation | Measurement |
| --- | ---: |
| Abstract static-field initial memberships | 2,332,098 |
| Distinct abstract static-field initial shapes | 3,900 |
| Method premise rows with a static origin | 99.76% |
| Occupied statement buckets with a static origin | 99.60% |
| Caller-input groups with a static origin | 94.66% |
| Actors with an all-static footprint | 25.04% |
| Initial memberships held by those actors | 97.50% |
| Concrete TAINT caller-input groups, all bases | 0.0623% |

The main population is repeated abstract static state, not concrete mark
multiplicity. At least 54.42% of structural initial attempts also create no
new initial. Counts do not establish static CPU share, exact unchanged-field
identity or removable bytes. No speedup is measured for this proposal.

## 3. Field requests: actual reads only

Keep a shared immutable plan per CommonMethod. Full MethodKey contexts borrow
it. It contains field scopes, with mark identities erased:

```kotlin
data class StaticFieldRequests(
    val fields: Set<StaticFieldSlot>,
    val wholeClasses: Set<ClassAccessor>,
    val wholeStatics: Boolean,
) {
    fun requests(slot: StaticFieldSlot): Boolean =
        wholeStatics || slot.cls in wholeClasses || slot in fields
}
```

Only actual value-read geometry seeds the plan:

| Operation | Field-request contribution |
| --- | --- |
| `x = C.f` | C.f |
| `C.g = C.f` | C.f, because it is read; not the write target C.g |
| `C.f = x`, including constant writes | None from the target |
| Alias endpoint or persisted alias path | None |
| `ContainsMark(C.f, T)` in a source or sink condition | None; only a static-mark request for C.f/T |
| Other effective non-mark condition/input check that reads the value of C.f | C.f |
| Cleaner, removal or generation target alone | None |
| Genuine class/root-descendant value read | Symbolic class/all-field scope |
| Exact class-point check only | Separate class-point need, not all fields |
| Coordinate exists in the global universe | None |

Do not scan alias endpoints to add field requests. Alias handling stays in
ordinary transfer/resolution logic. Its coverage without alias-request seeds
is a proof obligation, not a reason to add those seeds back silently.

Compute transitive value-read needs on the complete prescan call graph:

```text
fieldRequests[M] = localValueReadRequests[M]
                  union union(fieldRequests[C] for resolved callees C of M)
```

Use the existing recursive-component closure and shared sets. No new request
index, per-actor copy, startup notification wave or dynamic field-request
protocol is proposed. A complete field plan is fixed before engine work.

Keep the global coordinate universe separate. Write targets and other legal
producer coordinates still belong there even when they add no field request.
For example, a constant weak PUTSTATIC may lower to a keep edge only; its LHS
must still be collected for legality independently of that lowered edge.

Do not derive `wholeStatics` from a nonempty mark set. A T-to-U mark change at
the same condition must not enlarge field scope. Genuine global value-read
geometry can remain global; preserve effective NNF, false-rule elimination,
source/sink roles and the selected condition policy.

## 4. Static-mark requests and the two emission gates

Static mark requests remain a separate relation:

```text
fieldRequests[M, C.f]       -> does this field need abstract analysis?
markRequests[fullKey, C.f]  -> which actual concrete marks need analysis?
```

A selected cleaner on C.f/T may add a mark request without adding a field
request. This gives a real use for concrete-only field analysis.

```kotlin
fun emitStaticField(slot: StaticFieldSlot, input: Facts) {
    if (!slotMatchesRequestGeometry(slot, input)) return

    if (fieldRequests.requests(slot)) {
        emit(slot.abstractInitial()) // (S, C.f, *, {}, *)
    }
    if (input is TaintTree) {
        forEachActualRequestedAnswer(input, requests.borrow(slot)) { initial ->
            emit(initial)           // (S, C.f, $ or *, {}, T)
        }
    }
}
```

| Field request | Request for actual supplied T | Normal field initials |
| --- | --- | --- |
| No | No | None |
| Yes | No | Abstract `C.f.* (*)` |
| No | Yes | Concrete answer for T |
| Yes | Yes | Abstract initial and concrete answer for T |

A request alone starts nothing. FLOW does not invent a concrete mark. Keep
request-side matching independent of field E and FLOW mark exclusions, while
ordinary transfer/summary semantics preserve them.
Validate supplier shape and all class/field coordinates against the closed
global domain before request filtering. A missing request must not hide an
invalid coordinate or undo the selected static-domain correctness repair.

Keep the existing EXACT/STAR projector, Empty initial E, no ANY/ANY_TAINT
initial, and no concrete empty-S-root initial. Direct concrete class points
keep their separate preservation path; they are not normal field answers.
The special abstract S-root initial is removed from the run-1 initial grammar.
Actual broad TAINT remains legal data and projects to requested fields/class
points; it must not recreate the removed abstract root.

Keep the existing B/D/P protocol:

```kotlin
val requests = RequestStorage(sharedBase = profile.staticMarkPlan)
var temporary = RequestStorage()

// Existing method-queue-empty barrier:
summaryStorage.commit(temporary)
temporary = RequestStorage()
```

Precompute scoped static interests after prescan. A field cleaner contributes
(C.f,T), not a whole-static T need. Keep genuine class/root-wide mark interests
symbolic and borrow them for matching supplied fields. Do not enumerate the
universe into dynamic wide offers, copy B into P/D, or create another cumulative
request store. Frozen lookup must cover genuine wide needs outside the old
explicit selected set when removing that old dynamic wide-offer path.

For generic excluded-mark copies use source geometry: `g -> f (* except T)`
reads g and needs source g/T, not a target-created field request for f. The
actual original input can lie farther upstream and retains its dynamic route.

Preserve changed-origin request discovery:

```text
A receives C.f data; R_A(C.f) is initially empty
A: C.g = C.f; call B
B: check ContainsMark(C.g, T)

read C.f seeds A's field request
B's check seeds ONLY mark interest g/T, inherited unchanged by callers
abstract f execution derives g FLOW
B's g/T interest requests T on A's ORIGINAL f slot
```

The write target g is not a request seed. Derived output g does not need an
abstract g initial merely because it is written. Keep both request/input
arrival orders, original-slot routing, set union and concrete propagation skips.

For a mark-only method `check ContainsMark(C.f,T)`, F(f) is empty. Actual
supplied T plus R(f,T) starts concrete-only analysis; FLOW does not create T.
Signed folding runs before collection. Under the current eligible call/exit
policy residual negative literals project to true, so a pure negative source
can become ZERO generation. It does not need an abstract f carrier. Entry
eligibility stays stricter. Never infer a negative literal from missing state.

## 5. Weak field writes and Empty E on run-1

All logical field writes, static or nonstatic, preserve affected old data at
DEMAND and add value edges from the pre-store input. A disjoint component
keeps its layer. Whole-base local assignment retains its separate strong
replacement rule. Array/model/alias field updates use the same phase rule.

```kotlin
fun run1Write(input: Edge, write: FieldWrite): List<Edge> {
    val kept = if (mayOverwrite(write.destination, input.current))
        demandNormalForm(input) else input
    return listOf(kept) + valueEdgesFromPreStoreInput(input, write)
        .map(::run1FactNormalForm)
}
```

```text
x.* NORMAL; x.f=y             -> retained x geometry DEMAND, Empty E
x.g/T NORMAL; x.f=constant     -> disjoint g remains NORMAL
C.f/T NORMAL; C.f=value/U      -> old T DEMAND, independent new U edge
already DEMAND; another write -> remains DEMAND
```

Keep W2/W8 canonicalization: demand STAR/must becomes may ANY, with the
actual path/mark/origin retained. Do not simply set weak=true: the current
weak lowering keeps NORMAL and does not encode this new overwrite debt.

Every intermediate run-1 fact has Empty field E, not merely store outputs.
Partial cleaners and field RemoveAll/cleaner writeback can also create E.
Keep exact exclusion-free results, but replace an E-requiring residual with
an Empty-E DEMAND may cover. Clearing E on NORMAL must would be unsound.
Mark exclusions and original-slot requests remain; no blanket mark erasure.

```text
partial normal must/E{f} -> may ANY, DEMAND, Empty E
partial FLOW STAR/E{f}   -> may ANY, DEMAND, Empty E
```

Use a phase recipe before allocating E, with checks on every queue/store/
summary publication seam. Later strong recipes retain their ordinary field
exclusions. Separate weak-F1 and strong-later cache payloads without changing
logical rule/action occurrence IDs or the managed-reference mechanism.

## 6. Precomputed default static summaries

After prescan compute a separate least effect closure:

```text
StaticMayWrite[M] = localStaticMutationOrUnknown[M]
                   OR any(StaticMayWrite[C], M calls C)
Default[M] = S.* -> S.* [if StaticMayWrite[M] then DEMAND else NORMAL]
```

Count all logical static effects: direct/model stores, possible static alias
writes, Clean/RemoveAll, cleaner/returned-rewriter writeback and generated
static mutations, including conservative unknown effects. Pure reads and
identity bindings are not writes. This inventory never seeds field requests.

The default is a tagged shared schema available for every prescan method,
even if no F1 actor, initial or execution summary exists. Summary lookup
borrows it without enumerating fields or starting actors. It is not an
InitialAp or an observed root-return summary.

```kotlin
fun enterRun1Static(ctx: CallContext, stage: Callees, fact: PlanFact) {
    for (target in stage.targetsIncludingUnknown()) {
        val template = prescan.defaultStaticSummary(target)
        val continued = applyIdentityToActualInput(template, fact)
            .copy(identityCandidate = true)
        enqueueContinuation(ctx, stage.to, continued)
        for (needed in selectRequestedFieldAndClassData(target, fact)) {
            subscribeExact(target, needed)
            linkInput(target, needed)
        }
    }
}
```

Instantiating the identity keeps the actual input geometry and original
caller premise. Join layers before W2/W8 normalization: NORMAL C.f plus a
DEMAND default gives DEMAND C.f; DEMAND plus NORMAL stays DEMAND. Never
instantiate a generic root may result first and lose the actual field path.

This lane replaces the old independent NORMAL static bypass. It is available
without waiting for a callee return, including for a nonreturning target;
NORMAL means no closed static-write contributor, not proof of return. Keep
F1 defaults inside the unconfirmed/guidance-only boundary. Actual callee
outputs still add results. Nonstatic call inputs keep their existing protocol.

Place it across CALLEES after local prefix actions and before returned rules,
bind-back, aliases and cuts. Keep identityCandidate explicit and preserve
actual caller/context/premise correlation. Do not run a second unconditional
continuation which could mask the demand default.

Callee input gates stay separate from the write effect:

```kotlin
fun needsData(slot: StaticFieldSlot): Boolean =
    fieldRequests.requests(slot) || staticMarkPlan.hasScopedInterest(slot)
```

Requested FLOW can reach the target even with an empty current R, allowing
late original-slot request discovery. Preserve separate direct class-point
delivery and actual broad-TAINT projection. Unneeded fields use only the
default continuation and create no input group/LinkIn. All F1 selected facts
have Empty field E; retain distinct marks, tails and layers.

There is no propagated abstract S-root, root-driven field expansion, return
watcher or pending static frame. Root-shaped default schema syntax is legal;
it does not create a root initial, rule event or field-universe enumeration.

## 7. Cleaner and mark-effect boundary

Explicit cleaners retain their semantics inside an analyzed method and in the
local CallPlan stages when their result is exclusion-free. An E-requiring
partial residual uses the DEMAND may-cover rule in §5. The default continuation
keeps old caller statics at the joined layer; possible static mutation forces
DEMAND. A read-only nonreturning target can still have an N default under the
selected F1 may control policy; this does not prove normal return.

Narrow static mark collection spatially. The current selected-mark FLOW cleaner
operates across base S and relies on unrelated concrete analyses for repair.
A cleaner on f must not request T on unrelated g merely because both use S:

```kotlin
val (affected, untouched) = splitAtFirstStaticField(input, cleaner.scope)
emit(untouched)
applySelectedMarkClean(affected, cleaner, collectOriginalSlotRequest)
```

For packed input, retain different mx headers/layers in separate facts. Within
an affected first-field cylinder, keep exact exclusion-free results and concrete
replay; an E-requiring partial residual uses the DEMAND cover. Genuine broad cleaners keep
their actual scope. A cleaner target alone adds mark interest, not a read request.

Do not apply a target-scope disjoint shortcut to generic transfers. For
`C.g -> C.f (* except T)`, applicability comes from SOURCE g: keep the copy,
attach exclusions to its affected OUTPUT f, and request T on the ORIGINAL g
origin. Knowing output f is disjoint from incoming g never permits dropping
that transfer. Generated rewriters and every excluded-mark creator require the
same source/output/origin distinction.

For an unread field, no abstract initial is required just to preserve other
marks through a caller's call: the default continuation covers them at its
joined layer. A requested
actual T can still start concrete-only analysis. True source/GEN output keeps
its ZERO or actual input origin; no request or removed root creates a source.
Do not infer a negative mark condition from missing lazy state.

## 8. Strong run-2 and later static handling

The user-selected boundary is:

```text
run-1 live state: no special abstract (S, ., *) propagation
run-1 handoff:    tagged default N/D schemas retained as query guidance only
later runs:      query-led strong stores and normal-return call semantics
```

Do not export ordinary synthetic root-execution summaries as if a root
initial had been analyzed. Tagged default schemas are now explicitly allowed
and must not be deleted by the old blanket root-pattern filter. Retain a
shared default-plan view for the entire prescan graph, independently of
executed analyzers. DEMAND defaults supply coarse static demand; NORMAL
defaults remain available for actual queries into skipped read-only helpers.

```kotlin
val executed = rejectOrdinarySyntheticRootExecution(run1Snapshot)
val handoff = HandOff.guidanceOnly(executed,
    defaults = prescan.defaultStaticSummaries, includeNormalSingletons = true)
// No executable F1 native/opposite records.
```

B2 intersects a default guide with the actual required field/class component
before selecting its query initial. The wildcard guide itself must not be
passed to flowForm as a root initial. The computed response can have a different
source field or a nonstatic/ZERO outcome. The actual all-base query authorizes
that response; the default is never its reverse executable answer.

The new write rule demotes old f/T→f/T across `f=constant`; it fixes the old
unannotated weak-store case. It does not prove every other F1 NORMAL result
strong-valid: a no-write default can cross a nonreturning helper, and its
outputs can later reach nonstatic endpoints. The current record has no
semantic-phase provenance. Retain the all-F1 guidance-only boundary.

Therefore **all run-1 publications are guidance only**, not executable native
or opposite records. This is the safe selected phase boundary; it does not
add a new cache or IR representation. Preserve coarse entry/exit hints and
candidate seeds, including NORMAL singletons omitted by the current
`demandEdges()` path. Reusable strong records begin with completed run-2.
Diagnostic run-1 evidence cannot certify a later edge or normal return.

This includes sink matches: retain NORMAL and DEMAND F1 matches as candidates
and seed B2 from both. The existing demand-only seed extraction and stop
checks cannot skip strong refinement merely because all F1 matches/edges are
NORMAL. Keep rule/action occurrence IDs stable; semantic cache flavor must
not rename the source or sink that a later seed refers to.
Use `confirmed=false` report witnesses so the existing DEMAND candidate
path can supply seeds. Keep the raw fact layers unchanged. Replace the
`demandLayerEdges == 0` stop shortcut while such candidates need B2.

### Actual queries recover missing S→S edges

Consider `GEN f/T; call helper; check f/T`. Run-1 can skip a pure helper,
so its summaries contain no f→f. Run-2 must nevertheless analyze its actual
f/T output demand. Adding identity for every missing summary would be wrong
for `helper { f=constant }` or a helper with no normal return.

```kotlin
fun queryLater(call: Call, actualRequirement: Facts) {
    val q = subscriptions.register(call, actualRequirement, fullPremise)
    callee.startStrongRestricted(q.requiredEntry, oldGuide.hintsFor(q))
    callee.allowResponseFor(q) // guide absence vetoes neither start nor response
    // Subscribe; only a proven normal-return response crosses the call.
}
```

Apply this to all actual nested bindings, including static→local→argument→
return→static paths. It is ordinary restricted analysis, not run-1 requests.
Use existing N2/subscriptions and fact/summary deduplication where possible.
Do not add a universal S-root seed. Do not apply F1 field-ownership or
footprint-complement identity shortcuts to later strong queries. An unread
field can still be overwritten; F is not a preservation mask.

### Strong relation within the selected AP semantics

| Current-query callee behavior | Run-2 relation to infer in reverse |
| --- | --- |
| Pure helper that returns | f/T←f/T |
| `g=f`, f≠g | f/T←f/T and f/T←g/T; old g does not survive |
| `f=untainted` | No f/T←old-f/T; disjoint fields survive |
| Actual `source(T)` into f | ZERO←f/T, a source hit rather than S identity |
| No modeled normal return | No normal-return static continuation |

The table writes `input preimage ← output requirement`. Forward run-3 uses
the corresponding strong forward relation. Preserve full
residual geometry: killing f retains both other classes and siblings in C,
not just `S.* except C`. Exact class points are separate from field descendants.
Ordinary E/mx and full-premise guards remain effective. Unknown queries stay
pending; recursive analysis computes the least query/response fixed point.
Strong summary reuse is optional acceleration, with ordinary AP read views;
missing records fall back to current-query execution.
Keep actual modeled-return evidence distinct from the synthetic virtual exits
used for backward CFG reachability. A synthetic exit alone cannot authorize
a strong return response, including ZERO/generated and ND outputs. Local
source/sink work before a nonreturning exit remains valid on its own path.

The [static specification](../../spec/static-handling.md#6-strong-static-handling-on-run-2-and-later)
pins admission, response, reversal, return and persistence behavior. The
[proof dossier](../../spec/static-handling-proofs.md) gives constructive
coverage, soundness and completeness arguments and the Lean/local scope.

## 9. Proof obligations and validation

Update the normative AP/interpreter/core/request specs and reference model
before runtime work. The current F75/F78 proofs do not certify these changes.

| Obligation | Required checks |
| --- | --- |
| Read/mark separation | Only actual value reads seed F; effective source/sink ContainsMark checks seed only mark interests; no alias/write/cleaner target seeds; transitive cycles and genuine broad scopes covered |
| Legality separation | Keep write/alias/model producer coordinates in the accepted domain without turning them into field requests |
| Two emission gates | All four field/mark cases; actual supplier required; mark-only cleaner case; no invented mark or ANY/empty-static-root initial |
| Discovery coverage | Empty R(f), f-to-g copy, downstream g/T, nonstatic/static crossings and both request/input orders still work |
| Weak stores and Empty E | All field stores retain affected old data at DEMAND; pre-store values independent; every F1 producer is E-free; partial must residuals weaken safely; later caches separated |
| Default static templates | Least semantic write closure; N/D default for every method without an actor; actual geometry plus layer OR; one continuation; post-prefix and return-stage order |
| Target delivery | Unrequested fields create no input groups/LinkIn; frozen domain covers late marks; full caller/premise/layer correlation retained |
| Root removal | No special abstract root initial/queue item/subscription; no root field expansion or root-return data route |
| Spatial mark effects | Disjoint/mixed fields, source-to-target excluded-mark copies, within-field exact/below, class points and ZERO generation |
| Iteration handoff | Tagged N/D defaults retained symbolically over all prescan methods; actual B2 query initials; no executable F1 records; normal singleton guidance retained |
| Later S→S | Actual-query start and response independent of F1 inventory/summary availability; strong keep/copy/kill/source; complete nested bindings; modeled return; residual E and exact converse |
| Observables | Preserve real source-to-sink derivations and main finding recall; quantify any added run-1 FP and later refinement |

Use finite-derivation induction and fair monotone replay for cycles. Count every
queued bypass, input, request and summary delivery before quiescence. Source
and sink NNF, signed conditions, ND proof support and action order remain intact.

Important tests include:

```text
C.g = C.f; call(check g/T)       // f read request, not a write-seeded g request
C.f = constant                 // no field request from the target; weak update
old f/T; f = value/U            // retain T and add U on run-1 only
clean f/T; f = untainted        // do not resurrect cleaned T locally
call(no return); read C.f       // static bypass on run-1; ordinary later behavior
call writes f; caller reads f   // retain old f on run-1; handoff precision audit
exact class-point T            // no manufactured field or root initial
late mark after earlier FLOW   // retain requested input for replay
```

## 10. Implementation order and measurement gates

1. Use the new static specification and proofs to pin the read/mark split,
   weak run-1 behavior and strong later query/record boundary. Keep checked
   local laws distinct from Kotlin refinement and complete-engine proofs.
2. Separate metadata value-read needs from legality, mark interests and
   write/alias effects. Keep the existing shared request storage and barriers.
3. Add phase-safe DEMAND weak field stores and all-producer Empty-E rules.
   Remove root propagation; install N/D defaults and actual-input application
   with request-gated callee delivery.
4. Add scoped cleaner/mark changes, guidance-only F1 export, all-base later
   query admission/response and strong record separation as one coherent
   implementation; do not enable partial semantics.
5. Run core/JVM/application checks, OWASP and the selected real projects with
   their fixed configurations. Use fresh main for finding comparison and the
   source-bound current runtime for the performance control.

Record separate field, mark and data counters:

```text
field requests: local READ / CONDITION, closed explicit/class/root scopes
field initials: attempted / fresh / rejected by field request
special abstract static-root initials and propagations: must be zero
static mark requests: frozen logical / offered / fresh D / outgoing batches
static calls: NORMAL defaults / DEMAND defaults / requested deliveries / skipped fields
run-1 field facts, summary payloads and queued inputs with nonempty E: zero
method rows / occupied buckets / N2 groups / index nodes by origin/current base
propagated root initials and ordinary synthetic root-execution summaries: zero
shared default templates: NORMAL / DEMAND / missing (missing must be zero)
B2 actual-geometry queries from defaults / unintended wildcard-root starts
executable F1 native/opposite records after handoff: must be zero
later actual queries / missing-guide starts / delivered strong responses
```

Compare processed steps, new analyses, deliveries, subscriptions and retained
bytes. Report latest reached, completed and accepted iteration. The target is
**run-1 engine time within 60 seconds**; prescan, metadata, heap capture and
report time remain separate. Do not claim exact old-report parity or that
later strong passes necessarily remove every weak run-1 false positive.

IR storage, new reference managers/identity caches, generic scheduling and a
global taint pool are outside this proposal. Analyzer implementation is unchanged.
