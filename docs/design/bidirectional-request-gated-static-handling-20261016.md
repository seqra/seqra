# Request-gated static handling on run-1

Date: 16 October 2026. Status: proposal; no implementation or new analyzer run.
This is the current proposal for static handling. It replaces the earlier
control-root, return-watcher and pending-frame candidates in the
[ThingsBoard investigation](../reports/bidirectional-thingsboard-static-workload-20261016.md).
It does not change the current normative specs or implementation.

Latest reached: **run-1, forward, limit 1; OOM**. No engine completes and no
forward report is accepted. Measurements execute `4acde739b`, captured at
`05cf9ad62`; current `aa844367d` has the same core tree.

## 1. Selected rules and goal

Reduce redundant static-field analyses, call deliveries, subscriptions and
retained facts. The user-selected rules are:

1. Collect static-field requests from **field reads and condition checks only**.
   Do not add requests for aliases or static-field write targets.
2. Emit abstract `C.f.* (*)` only when C.f has a field request.
3. Emit a concrete field answer only when its actual supplied mark has a
   static-mark request. The field and mark gates are separate.
4. Ordinary static stores are **weak on run-1**: retain the old destination
   value and add the written value.
5. Do not propagate the special abstract `(S, ., *)` fact on run-1. A static
   fact passes through a call without waiting for a callee summary.
6. Do not carry run-1 `(S, ., *) -> (S, ., *)` summaries into later iterations.

These changes apply to run-1. Later iterations keep their existing strong
store and call/return semantics. Explicit cleaners remain cleaners.

The parts below are ordered by expected effect on the repeated state:

| Part | Current behavior | Proposed behavior | Expected benefit |
| --- | --- | --- | --- |
| Stop root-driven field expansion | Special S-root travels through calls and can start the whole owned field basis | No special root input/fact; start only requested fields from supplied data | Avoid first field analyses and their downstream states |
| Limit callee data delivery | Every static fact reaching CALLEES is subscribed and linked to each target | Pass statics through locally; deliver only fields/marks requested by that target | Avoid irrelevant deliveries, exact input groups and index entries |
| Separate read requests from effects | Writes/aliases and global mark needs can enlarge structural selection | Reads/checks alone seed field requests; retain write/alias information in its proper role | Avoid demand created by write targets or alias inventories |
| Use weak run-1 static stores | Strong static stores remove old destination data | Keep old data and add new data; local call-to-return provides preservation | Avoid a static preservation-summary protocol on run-1 |

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

## 3. Field requests: reads and condition checks only

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

Only actual read/check geometry seeds the plan:

| Operation | Field-request contribution |
| --- | --- |
| `x = C.f` | C.f |
| `C.g = C.f` | C.f, because it is read; not the write target C.g |
| `C.f = x`, including constant writes | None from the target |
| Alias endpoint or persisted alias path | None |
| `ContainsMark(C.f, T)` in a source or sink condition | C.f |
| Other effective condition/input check that reads C.f | C.f |
| Cleaner, removal or generation target alone | None |
| Genuine class/root-descendant read/check | Symbolic class/all-field scope |
| Exact class-point check only | Separate class-point need, not all fields |
| Coordinate exists in the global universe | None |

Do not scan alias endpoints to add field requests. Alias handling stays in
ordinary transfer/resolution logic. Its coverage without alias-request seeds
is a proof obligation, not a reason to add those seeds back silently.

Compute transitive read/check needs on the complete prescan call graph:

```text
fieldRequests[M] = localReadAndCheckRequests[M]
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
the same read/check must not enlarge field scope. Genuine global read/check
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

Preserve changed-origin request discovery:

```text
A receives C.f data; R_A(C.f) is initially empty
A: C.g = C.f; call B
B: check ContainsMark(C.g, T)

read C.f seeds A's field request
B's check seeds g and propagates that read/check need to callers
abstract f execution derives g FLOW
B's g/T interest requests T on A's ORIGINAL f slot
```

The write target g is not a request seed. Derived output g does not need an
abstract g initial merely because it is written. Keep both request/input
arrival orders, original-slot routing, set union and concrete propagation skips.

## 5. Weak ordinary static stores on run-1

For an ordinary logical static store, retain the incoming destination and add
all existing value edges. Do not add a store-induced destination kill or E.
Do not remove E/mx already present from previous operations.

```kotlin
val weak = existingWeak || (semantics.isRun1 && isLogicalStaticStore)
builder.write(destination, path, weak = weak, values = values, ...)
```

`MicroEdgeBuilder.write` already has keep-plus-value behavior for weak writes.
The direct JVM seam is `JIRStatementForms`: static assignments currently take
its strong path. Apply the rule to ordinary static stores produced by other
models too, using their logical operation kind before lowering.

Do not weaken every edge whose destination happens to be S. Explicit Clean,
RemoveAll and cleaner/rewriter writeback are different logical operations.
Existing alias value updates already use weak target updates; alias metadata
still does not seed field requests.

```text
old C.f/T; C.f = untainted     -> old T remains on run-1
old C.f/T; C.f = value/U      -> old T and new U remain
clean C.f/T; C.f = untainted  -> the weak store does not resurrect removed T
```

F1 weak and F3 strong cannot share one mutable forward statement payload.
Direction alone is insufficient. Select an explicit run-1 store flavor in the
form recipe/cache key, or an equivalent immutable execution mode. Preserve the
current managed-reference mechanism; no reference-manager or identity-cache
rewrite is part of this proposal. The independent metadata builder must use
the same semantics while collecting only read/check requests.

## 6. Static facts pass through calls on run-1

A static fact no longer needs a special S-root supplier or a normal-return
summary to cross a call. Add a run-1 static call-to-return continuation.

```kotlin
fun enterCallees(ctx: CallContext, stage: Callees, fact: PlanFact) {
    if (!mode.isRun1 || fact.facts.base != STATIC) {
        enterCalleesExisting(ctx, stage, fact)
        return
    }

    val preserved = fact.copy(identityCandidate = true)
    enqueueContinuation(ctx, stage.to, preserved) // immediate static bypass

    for (target in stage.callees) {
        val parts = selectRequestedFieldData(target, fact) +
            selectDirectClassComponents(target.classPointNeeds, fact)
        for (needed in normalizeAndDeduplicate(parts)) {
            subscribeExact(target, needed)
            linkInput(target, needed)
        }
    }
}
```

This sketch changes only the static path. Nonstatic inputs keep the current
callee/return protocol. Callee outputs are still processed and add results;
they do not retract the static bypass. A nonreturning callee does not block
this run-1 static continuation. This is a selected may abstraction, not proof
that the callee returns.

Place the bypass across the CALLEES region. Keep BIND_IN, local call-site
sources/sinks/cleaners, returned rewriters, bind-back, aliases and result cuts
in their ordinary order. Do not bypass the whole CallPlan or restore a value
removed by an earlier local call-site cleaner. Preserve the existing guards.
The bypass must set its return identity classification explicitly: the ADDED
fact can have a null `identityCandidate`, while returned alias guards require
a value. This preservation branch uses `true`; actual callee outputs keep
their existing identity/effect classification. Reuse local unresolved/constructor identities
where applicable instead of adding a second unconditional continuation.

For each actual target, deliver fields with a field request or a possible
static-mark need. Requested FLOW must still reach the target when its current
mark set is empty, so it can discover later requests. Do not decide delivery
from current R emptiness alone.

```kotlin
fun needsData(slot: StaticFieldSlot): Boolean =
    fieldRequests.requests(slot) || staticMarkPlan.hasScopedInterest(slot)
```

This helper covers fields only. Keep direct class-point delivery separately:
an exact class C/T can satisfy a callee class check/action without any field
slot. Broad TAINT can also supply possible direct class components. Retain
actual marks and the existing EXACT/STAR and NORMAL/DEMAND projector; do not
filter these components by a field-mark row or create a class request key.
Both selectors return lists of Facts. An explicit tip with E and a broad
ancestor projection with Empty E may require separate parts. Deduplication
must not merge them by clearing E or widening their headers.

Prove that dynamic static-origin requests are covered by this frozen domain:
those requests require an actual abstract static origin, admitted through the
field-request gate. Mark-only static slots are covered by their frozen mark
scopes. If a runtime request violates this coverage, diagnose the construction
error; do not silently drop it or promote all fields.

Fields with neither need get the local continuation and no callee subscription,
input index entry or LinkIn. Preserve exact original caller premises, contexts,
layers and E/mx for delivered fields. Actual broad TAINT is projected using
its supplied marks and the target's requests, not treated as abstract control.

Remove the old special-root mechanisms together:

- `ops.policy(staticInput)` must not admit the special abstract S-root.
- No control-root FLOW enters calls, field selection or mark expansion.
- No root-origin ordinary field-summary application reconstructs local data.
- No root-return class/field residual is needed to provide call preservation.

There is **no return watcher, control LinkIn, pending frame store or new
preservation-mask lattice** in this run-1 design. Local continuation provides
preservation. Ordinary weak stores add outputs through the existing kernels.

## 7. Cleaner and mark-effect boundary

Explicit cleaners retain their semantics inside an analyzed method and in the
local CallPlan stages. The static bypass deliberately keeps old caller statics
even when a deeper callee would overwrite, clean or not return. That precision
loss is part of the run-1 call abstraction; do not present it as exact old
summary behavior.

Narrow static mark collection spatially. The current selected-mark FLOW cleaner
operates across base S and relies on unrelated concrete analyses for repair.
A cleaner on f must not request T on unrelated g merely because both use S:

```kotlin
val (affected, untouched) = splitAtFirstStaticField(input, cleaner.scope)
emit(untouched)
applySelectedMarkClean(affected, cleaner, collectOriginalSlotRequest)
```

For packed input, retain different E/mx headers in separate facts. Within an
affected first-field cylinder, keep existing EXACT/BELOW behavior and concrete
replay unless a separate finer proof is supplied. Genuine broad cleaners keep
their actual scope. A cleaner target alone adds mark interest, not a read request.

Do not apply a target-scope disjoint shortcut to generic transfers. For
`C.g -> C.f (* except T)`, applicability comes from SOURCE g: keep the copy,
attach exclusions to its affected OUTPUT f, and request T on the ORIGINAL g
origin. Knowing output f is disjoint from incoming g never permits dropping
that transfer. Generated rewriters and every excluded-mark creator require the
same source/output/origin distinction.

For an unread field, no abstract initial is required just to preserve other
marks through a caller's call: the bypass already preserves them. A requested
actual T can still start concrete-only analysis. True source/GEN output keeps
its ZERO or actual input origin; no request or removed root creates a source.
Do not infer a negative mark condition from missing lazy state.

## 8. Do not export run-1 static-root identity

The user-selected boundary is:

```text
run-1 live state: no special abstract (S, ., *) propagation
run-1 handoff:    no (S, ., *) -> (S, ., *) summary or reversal
later runs:      normal strong-store and call/return semantics
```

The removed root summary must not survive through SummaryStorage snapshots,
native/opposite records, reversed views, seed extraction or cached transfer
views. Apply the filter to the barrier's summary snapshot before BOTH native
record persistence and handoff construction. The current driver persists
records before `HandOff.of`; filtering only inside HandOff is too late.
Build reversal/closure views from the filtered snapshot. Assert the forbidden family,
including packed root components. Do not drop ordinary field summaries, ZERO
source output or concrete class points under this rule.

```kotlin
val exported = run1Snapshot.withoutSpecialStaticRootIdentity()
assertNoSpecialStaticRootIdentity(exported)
nativeRecords.persist(exported)
val handoff = HandOff.of(exported)
```

This filter applies to method-summary instances, not compiled `keep(S)`
microedges. Those operators still preserve an actual C.f fact during weak
stores and binding; they do not require a special S-root initial or fact.

A root-only export filter is not by itself a proof for all weak-write results.
Run-1 field summaries and bypass-derived outputs are may facts. Current records
have no run/store-flavor provenance, and the persistent store accepts NORMAL
singletons; guidance-only use is not an established property. Audit the
handoff so these results cannot certify later strong survival or normal return.
Prove the necessary refinement or specify restricted/provenance-aware reuse
before implementation. This is a required spec-stage decision, not a claim
that the current record path is already safe for the new semantics.
Do not assume later strong execution automatically removes an earlier weak
alternative. Diagnostic trace data must not be a reusable transfer edge.

## 9. Proof obligations and validation

Update the normative AP/interpreter/core/request specs and reference model
before runtime work. The current F75/F78 proofs do not certify these changes.

| Obligation | Required checks |
| --- | --- |
| Read/check coverage | Only actual static reads and effective source/sink/other condition checks seed field requests; no alias/write/cleaner target seeds; transitive cycles and genuine broad checks covered |
| Legality separation | Keep write/alias/model producer coordinates in the accepted domain without turning them into field requests |
| Two emission gates | All four field/mark cases; actual supplier required; mark-only cleaner case; no invented mark or ANY/empty-static-root initial |
| Discovery coverage | Empty R(f), f-to-g copy, downstream g/T, nonstatic/static crossings and both request/input orders still work |
| Weak stores | Old plus new data for direct/model static stores; existing E/mx retained; explicit cleaners not weakened; F1 and F3 caches separated |
| Static call bypass | No wait for any target, including a nonreturning target; post-prefix placement and post-return stages preserved; no bypass of nonstatic data |
| Target delivery | Unrequested fields create no input groups/LinkIn; frozen domain covers late marks; full caller/premise/layer correlation retained |
| Root removal | No special abstract root initial/queue item/subscription; no root field expansion or root-return data route |
| Spatial mark effects | Disjoint/mixed fields, source-to-target excluded-mark copies, within-field exact/below, class points and ZERO generation |
| Iteration handoff | No root identity export/reversal; weak/bypass outputs cannot certify later strong transfer or normal return |
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

1. Specify the selected read-only request plan, weak stores, root-free call
   bypass, emission gates and handoff filter. Prove the reference laws and
   update the initial grammar and native/record contract.
2. Separate metadata read/check needs from legality, mark interests and
   write/alias effects. Keep the existing shared request storage and barriers.
3. Add phase-safe weak static stores, remove root construction/propagation,
   add local static call continuation and request-gated target delivery.
4. Add the scoped cleaner/mark changes and forbidden-root export checks as
   part of the same coherent implementation; do not enable partial semantics.
5. Run core/JVM/application checks, OWASP and the selected real projects with
   their fixed configurations. Use fresh main for finding comparison and the
   source-bound current runtime for the performance control.

Record separate field, mark and data counters:

```text
field requests: local READ / CONDITION, closed explicit/class/root scopes
field initials: attempted / fresh / rejected by field request
special abstract static-root initials and propagations: must be zero
static mark requests: frozen logical / offered / fresh D / outgoing batches
static calls: local bypasses / requested deliveries / skipped target fields
method rows / occupied buckets / N2 groups / index nodes by origin/current base
handoff root-identity summaries and reversals: must be zero
```

Compare processed steps, new analyses, deliveries, subscriptions and retained
bytes. Report latest reached, completed and accepted iteration. The target is
**run-1 engine time within 60 seconds**; prescan, metadata, heap capture and
report time remain separate. Do not claim exact old-report parity or that
later strong passes necessarily remove every weak run-1 false positive.

IR storage, new reference managers/identity caches, generic scheduling and a
global taint pool are outside this proposal. Analyzer implementation is unchanged.
