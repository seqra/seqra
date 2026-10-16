# Run-1 requests, field writes and static summaries

Date: 16 October 2026. Status: complete selected proposal; **not implemented**.
This document unifies the request handling in [static-handling.md](../../spec/static-handling.md)
with [run1-field-writes.md](../../spec/run1-field-writes.md). It is the single
proposal to use for this iteration. The specifications give normative details;
[request/static proofs](../../spec/static-handling-proofs.md) and
[write proofs](../../spec/run1-field-write-proofs.md) give their proof scopes.
No IR-storage, reference-manager or identity-cache rewrite is included.

Latest measured iteration: **run-1, FORWARD, limit 1; OOM**. No completed
iteration or accepted forward report. Measurements execute `4acde739b`, captured
at `05cf9ad62`; current `26ea71480` has the same core tree. No performance result
exists for this proposal. The target remains run-1 **engine time** within 60 seconds.

## 1. What changes, and what stays the same

“Previous selected specification” means the proposal at `26ea71480`, distinct
from the implemented analyzer. This update removes its general weak-write
policy; the request/default pipeline stays in force.

| Area | Previous selected specification | Updated proposal |
| --- | --- | --- |
| Broad ancestor `x.*`; write x.f | Retain at DEMAND with Empty E | Same result, because ordinary preservation needs E{f} |
| Destination `x.f.*` or exact old x.f/T | Retain at DEMAND | Kill, as ordinary strong preservation does |
| Disjoint `x.g.*`, f≠g | Preserve its layer | Same unchanged component |
| Already-DEMAND destination | Retain at DEMAND | Kill if wholly overwritten |
| Field-exclusion update | Treated full removal as a restriction requiring demotion | Only a surviving E-requiring residual demotes; complete removal stays a kill |
| RHS values | Computed independently from pre-store input | Same; self-assignment comes from this branch, not retained stale data |
| Known AP on demotion | Complete payload/tail retained | Same; DEMAND alone does not produce ANY |
| Requests and initials | Separate read/mark gates, R=B union dynamic, temporary P, nine families | Same behavior |
| Default statics and calls | Ordinary stored N/D identity, normal subscription/application, no S.* initial | Same behavior |
| Handoff and later runs | Ordinary selected-summary traversal; strong recomputation | Same behavior |

The call path stays ordinary. Prescan chooses the default edge; a reached
method publishes it through the normal summary path. The abstraction does
not emit S.*. There is no default-specific call or handoff mechanism.

| Implemented/base-spec behavior | Proposed run-1 behavior | Purpose |
| --- | --- | --- |
| Broad static profiles/root analyses can start many fields in callees | Read/mark gates select callee initials; no abstract root propagation | Avoid unnecessary method analyses and statement facts |
| Ordinary field stores can restrict old geometry with field E | Keep ordinary kills/disjoint outputs; demote only E-requiring residuals with Empty E | Avoid E-bearing residuals without retaining every overwritten component |
| Partial must cleaning can create field E | Keep exact E-free outcomes or a DEMAND may cover | Establish Empty E throughout F1 without inventing a NORMAL must guarantee |
| Static preservation relies on executed summaries/root mechanisms | Publish one ordinary N/D identity summary per reached method | Avoid root-preservation analysis while using ordinary call application |
| Handoff reads selected demand rows; native persistence accepts NORMAL singletons | Stored DEMAND defaults enter the same selection; the general F1 guidance-only boundary covers all summaries | Preserve required queries without treating weak results as strong proof |

The latest heap contains 2,332,098 abstract static-field initial memberships,
99.76% static-origin method rows and 94.66% static-origin caller-input groups.
These motivate gates on initial emission. Calls still register actual input
subscriptions and send their usual input; no reduction in retained caller-input
groups follows from a call-delivery filter. Empty E alone is not a measured
large saving; DEMAND alternatives can add refinement work. See the
[source-bound investigation](../reports/bidirectional-thingsboard-static-workload-20261016.md).

## 2. Domain and initial facts

Run-1 is forward with field limit exactly 1. Later limits are positive and
nondecreasing. S is static, ZERO is separate, and class accessors do not count
against the limit. Empty E means field exclusion, not FLOW mark exclusion mx.

A request is `(full MethodKey, normal abstract origin slot, set of marks)`:

```text
BaseSlot(b)    = (b, [], STAR, Empty, *)       // nonstatic, nonzero
StaticSlot(C,f)= (S, [C,f], STAR, Empty, *)
```

There is no class/root request slot, request tail, field E, mx or rule role.
Conditions retain their complete geometry/NNF and support in the forms.
Merged marks are independent interests, not proof that an AND condition is
satisfied. Source/sink NNF and full ND premises remain in the rule engine;
request handling does not expand conditions into cubes.
Normal abstract slots identify the ORIGINAL input, even if the current fact
has moved to another base, field or layer.

These run-1 method-entry families are exhaustive:

| Family | Location | Tail | Mark | Admission |
| --- | --- | --- | --- | --- |
| ZERO | zero / [] | EXACT | zero mark | Root/Reach input |
| Nonstatic abstract | b / [] | STAR | * | Actual nonstatic supply |
| Static field abstract | S / [C,f] | STAR | * | Actual field supply and field-read request F(C.f) |
| Nonstatic concrete | b / [] | EXACT or STAR | T | Actual TAINT T and mark request R(b,T) |
| Static field concrete | S / [C,f] | EXACT or STAR | T | Actual TAINT T and mark request R(C.f,T) |
| Direct concrete class component | S / [C] | EXACT or STAR | T | Separate actual direct/broad class projection |

There are nine families when alternative tails are counted separately. All
initial E is Empty, with no mx. No abstract S-root/class initial, concrete
empty S-root initial, ANY/ANY_TAINT initial or deeper normal-slot path is
admitted. A nonexact concrete supplier projects to STAR and starts as the
existing DEMAND/ANY fact. That is not an ANY initial. Actual broad TAINT
remains legal data. Sources/end actions can produce must facts later.

## 3. One prescan plan, separate inventories

Prescan already resolves the call graph. Keep these inventories distinct:

| Inventory | Contributors | Consumer |
| --- | --- | --- |
| Legal coordinate universe | Legal producers/consumers, including writes/models | Validate supplied/generated positions |
| Field reads F | Actual value reads and effective value-consuming checks/pass operations | Abstract static-field initial admission |
| Static-mark base B | Effective positive source/sink mark checks, scoped cleaners/excluded-mark creators | Concrete replay and unchanged-origin interests |
| Direct class needs | Actual class-point checks/actions | Separate class-component initial emission |
| StaticMayWrite W | Logical static mutations/unknown effects, closed over callees | N/D default layer |

```text
x=C.f                  -> field read f
C.g=C.f                -> field read f; no field request from target g
C.f=x                  -> static-write effect; no field request from target f
ContainsMark(C.f,T)     -> mark interest f/T ONLY, no field request
alias mentioning C.f   -> no field request from that endpoint
clean C.f/T            -> mark interest and static effect; no field-read seed from target
```

Keep genuine class/root-wide scopes symbolic. Nonempty mark interests do not
imply all fields are read. Signed/static folding precedes collection: false
conditions add nothing; P AND NOT P may fold false. Preserve the existing
residual-negative/entry eligibility policy; missing lazy state proves no
negative condition.

Compute least closures on the resolved graph, with recursive components:

```text
F(M) = localValueReads(M) union union(F(N), M calls N)
B(M) = localUnchangedStaticMarkNeeds(M) union union(B(N), M calls N)
W(M) = localStaticMutationOrUnknown(M) OR any(W(N), M calls N)
```

W includes direct/model/alias static writes, Clean/RemoveAll, cleaner/returned
rewriter writeback and generated static mutations, including class/root scope.
Include exceptional/finally paths conservatively. Unknown effects require a
read-only contract to use false. Pure reads/identity bindings are not writes.
Collect the operation before lowering: a constant store may become keep-only.

F/B/W belong to shared CommonMethod metadata borrowed by full method contexts.
An alias/write coordinate can be legal and contribute W without contributing F.
For an excluded-mark copy g→f, collect source interest g/T and route it to
its actual original input; do not substitute output f for the source.

## 4. Mark requests: storage, barriers and propagation

One authoritative relation lives in live SummaryStorage:

```text
R = shared immutable precomputed B union dynamicRequests
P = per-method temporary RequestStorage, writes only until commit
```

dynamicRequests is D in the proof notation R=B∪D; it is unrelated to the
DEMAND layer. B is borrowed, never copied into dynamicRequests/P and never
sent as an initial event wave.
Use the same RequestStorage class for R and P. At method-queue-empty:

```kotlin
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Commit unions mark sets, inserts only `P minus R` into dynamicRequests and notifies exact
caller-input subscribers of the fresh additions. Existing subscribers see
new requests; a new subscriber reads the full admitted R, including B. Count
pending requests/notifications before quiescence. No special index, additional
callee input history or fixed pending-memory threshold is selected.

`borrowMatchingRows(input)` borrows R rows for normal slots whose geometry
matches the supplied input. It ignores field E and FLOW mx; answers still
require actual marks. Run-1 inputs are simple, and broad field scopes can
remain virtual. Direct class points use their separate lane, not a fake
class request key. No request alone manufactures a concrete T.

For each matched `(slot, marks)` and actual caller-input relation:

```text
actual TAINT/current concrete origin -> answer actual requested marks; do not climb
FLOW with normal abstract origin    -> request those marks on ORIGINAL origin
```

An abstract origin remains eligible after its current conclusion becomes
DEMAND. Its known tail is retained; layer demotion is not a reason to suppress request propagation.
Concrete initials/current TAINT skip upstream request propagation. A creator
that excludes T requests T immediately on its actual abstract origin; later
request matching need not subtract mx again.

Unchanged static interests already inherited in B need no duplicate upstream
offer. Changed origins remain dynamic:

```text
A receives f; A: g=f; call B; B: ContainsMark(g,T)
read f -> abstract f -> g FLOW -> B's g/T interest
                           -> request T on A's ORIGINAL f
actual f/T -> concrete replay -> actual g/T
```

B's check adds only g/T, never an abstract g field request. The original f
read starts the abstract dependency even when R(f) was initially empty.
Both input-first/request-later and request-first/input-later produce the
same union through the existing retained caller-input subscriptions.

## 5. Initial emission and ordinary callee input

For a normal static slot, abstract and concrete gates are independent:

```kotlin
validateAgainstClosedDomain(input)
for (slot in suppliedInterestedSlots(input, fieldReads, requests)) {
    if (fieldReads.requests(slot)) emit(abstractStar(slot))
    for (mark in actualMarks(input, slot).intersect(requests.marks(slot))) {
        emit(projectExactOrStar(input, slot, mark))
    }
}
emitActualDirectClassComponents(input) // separate existing projector
```

| F(slot) | R(slot,T) plus actual T | Result |
| --- | --- | --- |
| No | No | No normal field initial |
| Yes | No | Abstract field initial |
| No | Yes | Concrete-only answer |
| Yes | Yes | Abstract plus concrete answer |

Validate legality before gating, so lack of a request cannot hide an invalid
coordinate. FLOW cannot invent T. A mark-only source or sink still receives
actual requested concrete data without starting abstract field analysis.

At a call keep the ordinary subscription and LinkIn path for the actual
input, with its original layer, caller premise and context. Do not filter
subscriptions or input delivery by F/B/R. The callee abstraction applies
the gates above when it emits initial facts. A callee with no matching gate
can emit no static initial and still answer the caller with its stored
default summary. Normal summary results do not change the input independently
supplied to the callee. Keep unlike marks/tails/layers separate.

## 6. Ordinary writes with exclusion-update demotion

**If ordinary preservation needs a field-exclusion update, move that edge to
DEMAND and keep Empty E.** Fully overwritten components are killed; disjoint
components propagate unchanged. This replaces the former general weak-write
policy. Do not use overlap alone to preserve every old component. Removing
fully overwritten states also avoids their downstream propagation and request
work; the expected saving is not yet measured.

| Old component; definite `x.f=y` | Run-1 preservation |
| --- | --- |
| `x.* NORMAL` | Same x.* at DEMAND, Empty E |
| `x.f.*`, NORMAL or DEMAND | Killed |
| exact x.f/T or deeper x.f.g | Killed |
| `x.g.*`, f≠g | Unchanged, including layer |
| exact ancestor x | Unchanged |

```kotlin
val kept = when (planOrdinaryPreservation(old.current, write)) {
    Drop -> null
    Unchanged -> old
    NeedsFieldExclusion -> old.copy(layer = DEMAND) // same AP/tail; Empty E
}
val values = valueEdgesFromPreStoreInput(old, write)
val result = listOfNotNull(kept) + values.map(::run1ProducerPolicy)
```

Classify each actual current component before packing. A syntactic keep edge
with `{f}` does not mean disjoint x.g gains an exclusion. A complete kill is
not an exclusion update. Existing alias/write-certainty rules still decide
which destinations are definite; possible-alias gen does not acquire a new
blanket strong kill or DEMAND old branch.

Demotion changes only the edge layer: path, tail, marks/mx, original premise
and context remain. Known x.* at DEMAND is still x.*, not ANY. An already-
DEMAND component can still be killed by overwrite. Genuine information-
losing transfers retain their own ANY rules.

Read RHS values from the pre-store state. Self-assignment kills the old
preservation component and derives the assigned value through the value
branch. An untainted constant adds no taint. Field RemoveAll and cleaner/
rewriter writeback use the same three cases; they do not restore an old
fully overwritten mark. Whole-base operations retain their ordinary rules.

All F1 intermediate field E remains Empty. Other partial cleaners/model/rule
producers can also need E: retain their ordinary exact kills/keeps and use
an explicit DEMAND E-free cover only for surviving E-requiring residuals.
Never erase E while keeping a NORMAL must guarantee. Preserve mx and its
immediate original-slot request. Choose this producer recipe before
allocating E, at every queue/store seam. Later phases keep ordinary E and
strong residuals. Logical occurrence IDs remain stable across form flavors.

## 7. Default statics use ordinary stored summaries

After prescan construct one identity **edge** for each method:

```text
Default(M) = (S, [], STAR, Empty, *) -> (S, [], STAR, Empty, *)
             edge layer = if W(M) then DEMAND else NORMAL
```

Use the same summary representation, insertion, deduplication, publication,
subscription and application API as other summaries. There is no default tag
used by the call handler, separate lookup lane, local continuation or bypass.
The root pattern is an edge endpoint/premise key, not an emitted initial.
The nine-family rule constrains admitted method initials, not summary patterns.

The following code shows the required interface, not existing Kotlin names:

```kotlin
// Shared prescan result; constructing an edge does not start an analysis.
val defaultStatic = SummaryEdge(
    transfer = PathEdge.identity(STATIC, path = emptyList(), tail = STAR),
    layer = if (staticMayWrite) DEMAND else NORMAL,
)

// Once when a reached run-1 method starts, use the ordinary publication path.
publishSummary(defaultStatic)
// Same path as every other summary:
// retained summaries.add -> demand selection -> pending publication
// -> SummaryStorage.publish -> current-read/notification -> ordinary applySummary
```

`publishSummary` must retain the row in the actor's RunSummaryStore as well
as publish it to live SummaryStorage, just as normal summaries do. Current
`summaryDelta` is the common entry for executed summaries. Generalize that
entry to accept the edge form if needed; do not insert only into the live
index or append defaults during handoff. The live index is released before
handoff, while the retained actor store survives. Ordinary union/delta logic
handles repeated equal rows. Initialize on the first counted method work
after actor registration, before that method can become quiescent. Do not
publish from the actor constructor: current scheduling requires registration
before requesting work. An existing subscriber receives a fresh publication; a later subscriber reads the stored row. Neither order loses it.

Use the ordinary call pipeline without an F1 static branch:

```kotlin
// Existing call path, also for static data during run-1.
subscribe(callee, callRef, actualInput)
link(callee, callRef, actualInput)
// Existing summary handler, for defaults and computed summaries alike.
for (summary in matchingSummaries(actualInput)) {
    applySummary(actualInput, summary).forEach(::resumeCallPlan)
}
```

Callee initialization may follow the first subscription; its normal queued
publication then notifies that subscriber. Read/mark gates apply only inside
the callee abstraction (§5). Prefix actions, returned rules, binding back,
alias processing and cuts retain the ordinary call-plan order. Remove the
old root/footprint-complement adapter where it would mask this ordinary
application. Add no default-specific `identityCandidate`, return watcher or
independent NORMAL continuation.

### 7.1 Preserve the transfer until application

An edge's transfer and its layer are separate. Apply STAR identity to the
actual input and join the edge layer. Do not convert its known tail to ANY:

```text
C.f NORMAL + stored NORMAL identity -> C.f NORMAL
C.f NORMAL + stored DEMAND identity -> C.f DEMAND
C.f DEMAND + either identity        -> C.f DEMAND
```

The complete AP payload is retained, including the actual field/class path,
tail, mark and origin. Only the edge layer changes. No field or mark is
invented. W=true because of unrelated g can conservatively demote f; it does not widen f to all S.

This requires a generic representation/compilation check. At current source
`Summary.kt` converts a DEMAND FLOW conclusion to an ANY transfer target
before applying it. Encoding the default as a normalized root DEMAND Fact
would therefore apply `S.* -> S.[any]`, losing the C.f suffix. That is a
different edge. Keep STAR identity plus its separate edge-layer flag in the
ordinary summary representation until application. Existing edge application
accepts an independent `edgeDemand` flag, but its final normalizer also turns
STAR into ANY on demotion. Remove that layer-driven conversion too. Keep
ordinary ANY transfers when information is actually lost. This is shared
summary/AP support, not a special run-1 call handler or a new default operator.

The F1 default can be available before a callee return. NORMAL certifies the
write-closure classification only. It is not a strong normal-return witness;
the general F1 guidance/candidate boundary still applies.

## 8. Ordinary demand selection and handoff

No special handoff traversal is required. The DEMAND default is a stored
summary and is selected exactly like every other DEMAND summary. It remains
in the actor's retained selected collection after live storage is released.

At source `2d3436187`:

- [RunMethodAnalyzer.summaryDelta](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt) retains a delta, selects demand pieces and queues publication.
- [Demand.selectDemand](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Demand.kt) selects every DEMAND result.
- [HandOff.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/HandOff.kt) iterates result.analyzers and actor.summaries.demandEdges().
- [RunManager.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunManager.kt) releases live stores but retains completed actors' summary stores.

```kotlin
// Same traversal and projection for all selected summary edges.
for (actor in result.analyzers) {
    for (edge in actor.summaries.demandEdges()) {
        handoff.add(actor.key, toBackwardDemandPatterns(edge))
    }
}
```

NORMAL defaults follow the same rules as other NORMAL F1 summaries. Under
the selected guidance-only F1 boundary, any broader NORMAL guidance export
is a general policy over retained summaries, not a default-specific iterator
or appended metadata row. No `run1Guidance()` accessor that synthesizes
defaults is needed.

### 8.1 Trace with no static initial in the callee

```java
void main() {
    C.f = sourceT();
    helper();
    sinkT(C.f);
}
void helper() {
    C.g = constant; // g != f; no reads or mark checks
}
```

Prescan gives helper F=empty, B=empty and W=true. Its default is the ordinary
DEMAND S identity. The g target creates no field-read request.

| Step | Ordinary action |
| --- | --- |
| ZERO reaches the call | Ordinary LinkIn(Reach) creates the helper actor |
| Helper starts | Its default goes through normal retained-summary insertion and publication; no S.* initial is emitted |
| Caller C.f/T reaches the call | Register the ordinary subscription and send the actual input |
| Callee abstraction receives C.f/T | No F/B/R interest, so it emits no static field initial |
| Summary matching | The stored S.* premise matches C.f/T |
| Summary application | STAR identity retains C.f/T and the DEMAND edge layer demotes it |
| Caller resumes | The normal call-plan stages receive C.f/T at DEMAND |
| End of F1 | The helper's DEMAND default is already in demandEdges() |
| Handoff | The existing actor loop projects it into B2 demand |
| B2 | Strong g-write analysis retains unaffected f and reconstructs the required strong relation |

Whether publication or subscription happens first changes only how the same
stored edge is delivered. Helper needs no executed f-summary. ZERO supplies
its actor on reached call paths, and normal initialization supplies the
stored default. No missing-actor example, hidden descriptor or additional
global scan remains.

### 8.2 Later demand uses the ordinary restricted domain

The default becomes an ordinary broad static demand pattern. A later run
may use an S-root FLOW initial under the existing restricted grammar; the
ban on emitting S.* applies to **run-1**, not all later runs. A root demand
can thus drive strong S->S reconstruction with full field exclusions.
No field-specific default-query adapter is required by this change.

Keep the selected general current-query fallback and full-premise response
rules for work absent from F1 guidance, including mixed static/local calls.
Keep concrete current requirements and concrete guides additive: an abstract
FLOW query alone cannot cross concrete mark/GEN gates. For `f=source(U)`,
an actual backward f/U query must still reach the concrete-U source/ZERO
branch. Defaults authorize analysis; only strong computation proves the
response. `g=f` may answer a g requirement from f, a constant overwrite kills
the old-f preimage, and a nonreturning helper yields no strong normal-return
identity. These are ordinary later strong semantics.

## 9. Strong refinement and reporting boundary

F1 demand survivors, default summaries and other F1 publications are later
analysis guidance, not native/opposite executable records. The selected F1
may control path can cross a nonreturning read-only target even with an N
default. Thus N alone is not a strong return certificate. Preserve candidate
sink/source/action occurrences and full premise support across handoff.

All F1 sink matches are unconfirmed regardless of raw layer. The existing
unconfirmed report path can produce candidate seeds without changing raw
fact layers. Do not stop before required B2 refinement merely because the
old DEMAND-edge count is zero. Cancellation/OOM is not a complete handoff.

B2 executes strong stores/cleaners, ordinary E and actual return semantics;
F3/later forward runs use the corresponding strong relation. A current query
authorizes both start and computed response. Old guide absence or F1 read
ownership cannot veto it. Unknown means pending, not empty or identity.
Recursion uses fair monotone query/response closure with finite return
witnesses. Synthetic virtual exits alone do not prove return. Only later
strong publications from complete runs become reusable records under the
existing full-premise, limit, demand and may/must read-view contracts.

## 10. Proofs, implementation order and validation

The constructive laws are: separate least F/B/W closures; nine-family and
actual-supplier gates; merged request union and both arrival orders; original
slot routing across field/layer changes; complete-kill/disjoint/partial preservation coverage and Empty E;
layer monotonicity and default locality; and strong-query/preimage completeness.

The corrected handoff bridge has a direct proof: root ZERO starts the root
actor; local ZERO preservation and each actually reached resolved call send
Reach to its target; fair handling admits the target ZERO actor. By induction
on reached call paths, every needed F1 callee is in the completed actor set.
Its normal initialization stores/publishes the default independently of static
initials. DEMAND selection places it in the same retained collection as other
DEMAND edges. The ordinary actor iterator therefore exports the default.
No global method scan is a premise. Strong actual-query fallback handles
work not represented by an earlier reached call.

The selected local write proof is
[Run1StrongPreservation](../../spec/lean/ApSpec/Run1StrongPreservation.lean):
actual prefix/tail classification, complete kills, unchanged disjoint data,
partial residual coverage, unchanged pre-store value branches and Empty E.
The [new receipt](../reports/bidirectional-run1-selective-preservation-proof-validation-20261016.md)
records its aggregate build/audit and executable cases. The earlier
[ordinary identity proof](../../spec/lean/ApSpec/Run1DefaultSummaryApplication.lean)
continues to check exact payload/tail preservation with independent layer
composition. Existing request/closure models retain their stated scopes.
The blanket [weak-write model](../../spec/lean/ApSpec/Run1WeakFieldWrites.lean)
and its exact-destination-retention laws are historical and cannot certify
this update. Actor/publication and full producer/ND/seed integration remain
explicit source-bound obligations, not a checked Kotlin/driver simulation.

Implementation order:

1. Build shared read/mark/effect plans without changing legal producer coordinates.
2. Apply supplier gates and request B/D/P/arrival semantics; keep request propagation eligible after FLOW demotion.
3. Preserve ordinary complete kills/disjoint results; replace only E-requiring residuals with layer-only DEMAND, and retain independent pre-store values.
4. Preserve transfer plus edge layer in ordinary summary storage/application without layer-driven tail conversion; publish the N/D default through the same retained/live path as other summaries.
5. Keep ordinary call delivery, subscription, application, demand selection and handoff; remove old root/footprint shortcuts and retain the general candidate/record boundary.
6. Verify packed/model/alias/scheduler/ND/seed bridges, then run fixed OWASP and real-project configurations.

Count complete write kills, unchanged components, E-update demotions, field/mark
gates, fresh requests/batches, N/D default applications,
zero nonempty-E F1 payloads, zero propagated abstract roots, default summary
rows and notifications per actor, run-1 versus later root initials,
new analyses/input groups and retained bytes. Report reached, completed and
accepted iteration on every measurement. IR storage and new cache/reference
managers remain out of scope. Analyzer implementation is unchanged.
