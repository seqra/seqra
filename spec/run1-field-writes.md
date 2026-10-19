# Run-1 requests, field writes and static summaries

> For the selected mark/static change, use [Mark groups and direct static handling](grouped-run1-statics.md)
> and its pinned pre-bottom-up baseline. It overrides older proposal links
> at the listed phase seams. Other ordinary contracts below remain in force.
> This specification work does not change the runtime.

Status: single selected specification. This document
unifies static field/mark requests, run-1 initial emission, field writes,
ordinary static defaults and strong later refinement. It takes precedence
for these topics over [AP](ap.md), [interpreter](interpreter.md),
[core](analyzer-core.md) and the historical parts of [F78](run1-requests.md).
[Static handling](static-handling.md) is a compatibility index, not a second
normative contract. The general weak-write proposal is superseded.

The [implementation proposal](../docs/design/bidirectional-request-gated-static-handling-20261016.md)
uses this complete contract. [Request/static proofs](static-handling-proofs.md)
and [field-write/default proofs](run1-field-write-proofs.md) state the
constructive laws, checked models and remaining integration obligations.
IR storage and cache/reference manager changes are outside this contract.

Latest measured runtime: `28a123f0e`. Reached/completed/accepted iterations:
OWASP **run-7 / run-6 / run-5**; conductor **run-2 / run-1 / run-1**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
See the [project measurements](../docs/reports/bidirectional-run1-selective-preservation-20261016.md)
for failures, engine times and proof-to-runtime limits.

## 1. Selected rules

The complete workflow is:

1. Prescan resolves calls and collects separate legal coordinates, value-read
   fields F, static mark interests B, direct class needs and static effects W.
2. Compute their least closures on that graph; share frozen plans by CommonMethod.
3. Each live full MethodKey borrows B into its authoritative request view R.
   Dynamic marks merge into temporary P, then R at the method-queue-empty barrier.
4. Supplied input and F admit abstract field initials. Actual concrete marks
   and R admit concrete initials independently. F1 emits no S.* initial.
5. Ordinary caller subscriptions retain actual inputs for summary application
   and late request replay. Changed origins route requests to the original slot.
6. Apply the field-write cases below. Field E stays Empty; demotion keeps the AP.
7. Publish the precomputed N/D S.* identity through ordinary summary storage.
8. Ordinary selected-summary handoff supplies later strong analysis; F1
   publications/reports remain provisional under §7.

For a definite field write `x.f = y`, apply ordinary strong preservation:

| Old component | Run-1 old-value result |
| --- | --- |
| `x.* [NORMAL]` | `x.* [DEMAND]`, Empty E |
| `x.f.*`, in either layer | Killed |
| Exact `x.f/T` or any descendant in the overwritten subtree | Killed |
| `x.g.*`, f≠g | Propagated unchanged, including its layer |
| Exact ancestor point `x` | Propagated unchanged |

**If a surviving preservation result needs a field-exclusion update, move
that edge to DEMAND and keep Empty E.** Full removal is a kill, not an
exclusion update. This is not a rule to retain every overlapping old fact.

All run-1 fact/initial/summary field E remains Empty. Add value transfers
from the pre-store input independently. Keep ordinary alias/write-strength
rules: a possible alias is not a definite overwritten destination.

The request/initial rules in §2 are part of this specification, not optional
references. The default and later-phase rules are in §§5–7.
For the complete static request lifecycle, see [§2.2.1](#221-static-request-lifecycle).

## 2. Request handling, initial facts and AP information

Run-1 has limit exactly 1. Later limits are positive and nondecreasing.
Class accessors do not count toward the limit. S differs from ZERO; broad
supplied TAINT and separate concrete class components remain legal.

Layers satisfy `NORMAL ≤ DEMAND`; composition uses OR. Demotion changes
only the layer, retaining base/path/tail, marks/mx, original initial, whole
premise, context and call identity. Known `x.*` stays `x.*` at DEMAND; no
information was lost that would require `[any]`. This supersedes old W2/W8
conversions driven solely by the layer. Real cuts/uncorrelated transfers keep
their own information-losing rules. Concrete-supplier initial projection is
unchanged.

A DEMAND input is still subject to a complete overwrite kill. Layer
monotonicity constrains surviving derivations; it does not require their
survival. Independent RHS/source derivations have their own layer.

Empty E means field exclusion, not FLOW mark exclusions mx. Request matching
still ignores E/mx, and excluded-mark creators still request the concrete
mark on the original abstract slot. Source/sink NNF and full ND premises stay.

### 2.1 Request domain and all initial families

Let K be the full MethodKey (method, entry and context), and M its shared
CommonMethod. A request contains a normal abstract original slot and a mark
set. There are exactly two normal slot kinds:

```text
BaseSlot(b)     = (b, [], STAR, Empty, *)  // nonstatic, nonzero, not a cleaner temporary
StaticSlot(C,f) = (S, [C,f], STAR, Empty, *)
request relation: (K, slot) -> set of concrete marks
```

There is no root/class request slot, query tail, field E, mx, caller,
callsite or rule role in the request key. Mark sets are independent interests,
not an AND premise. Complete NNF conditions, locations, actions, occurrence
IDs and ND support stay in the rule engine. Validate the actual premise/fact
pairing before deciding request eligibility; the current work item's initial
cannot replace an edge's real origin.

These method-entry initial families are exhaustive. Each alternative tail
is a separate family. All initial field E and mx are Empty.

| Family | Base/path | Tail | Mark | Admission |
| --- | --- | --- | --- | --- |
| ZERO | zero / [] | EXACT | zero mark | Root/Reach input |
| Nonstatic abstract | b / [] | STAR | * | Actual nonstatic supply |
| Static field abstract | S / [C,f] | STAR | * | Actual supplied field and C.f in F(M) |
| Nonstatic concrete | b / [] | EXACT or STAR | T | Actual T and R(K,BaseSlot(b)) contains T |
| Static field concrete | S / [C,f] | EXACT or STAR | T | Actual T and R(K,StaticSlot(C,f)) contains T |
| Direct concrete class component | S / [C] | EXACT or STAR | T | Actual direct/broad class supplier; separate projector |

There are nine families. No abstract S-root/class initial, concrete empty
S-root initial, ANY/ANY_TAINT initial or deeper normal-slot initial is admitted.
The supplied domain also excludes tainted exact empty S-root
`(S,[],EXACT,Empty,T)`. Broad nonexact actual TAINT remains legal input.
Nonexact concrete field/class suppliers project to STAR, not ANY: never
admit `C.f.[any](T)` as an initial. Existing concrete STAR initials start as
DEMAND/ANY TAINT; this initial-projection rule is separate from layer-only
edge demotion. Abstract STAR starts FLOW/NORMAL, exact concrete starts
TAINT/NORMAL, and ZERO starts REACH/NORMAL. Caller input layers still govern
ordinary summary application; a start assumption does not promote the caller.
Direct class components are not abstract class slots or requests. A root
summary endpoint/premise key is not an admitted root initial.

### 2.2 Prescan field and static-mark requests

Prescan already resolves the entire call graph. Do not resolve calls again.
Collect effective forms, including **source and sink conditions**, after
signed/static folding. Keep these inventories separate:

| Inventory | Contributors | Use |
| --- | --- | --- |
| Legal coordinates | All legal producers/consumers, including constant writes and models | Validate supplied/generated positions |
| Field-read requests F | Actual code/rule value reads and effective value-consuming checks/pass operations | Abstract static-field initial gate |
| Static mark base B | Effective positive static mark checks, scoped selected cleaners and excluded-mark creators at their source | Concrete mark interest and unchanged-slot routing |
| Direct class needs | Actual legal class-point checks/actions | Separate concrete class projector |
| StaticMayWrite W | Logical mutations/unknown effects, including callees | Default summary layer; detailed in §5 |

F is frozen read-plan metadata, not a dynamic request kind. Runtime request
storage and notifications contain mark interests only.

```text
x = C.f                -> F: C.f
C.g = C.f              -> F: C.f; no field request from target C.g
C.f = x                -> no field request from target C.f; contributes W
ContainsMark(C.f,T)     -> B: C.f/T ONLY; contributes no F
alias mentioning C.f   -> no field request from the alias endpoint
clean C.f/T            -> mark interest C.f/T and static effect; no field-read seed
```

A genuine value read inside a rule contributes F even if it is not an IR
GETSTATIC. A mark predicate alone does not. A false condition contributes
nothing. Fold `P AND NOT P` to false before residual negatives project away.
The existing eligible call/exit rule policy can make lone `NOT P`
unconditional ZERO work; entry eligibility keeps its stricter policy.
Neither missing lazy state nor a request proves a negative literal.
Do not expand NNF conditions into cubes for request collection.

For resolved M->N compute least closures, including recursive components:

```text
F(M) = localValueReads(M) union union(F(N), M calls N)
B(M) = localUnchangedStaticMarkNeeds(M) union union(B(N), M calls N)
W(M) = localStaticMutationOrUnknown(M) OR any(W(N), M calls N)
```

F/B/W are shared immutable plan data; full contexts borrow them. Construct
frozen mark membership from a selected slot set, not every legal coordinate:

```text
frozenSlots(M) = fieldSlots(F(M)) union exactFieldMarkSlots(closedMarkNeeds(M))
B(M,s) = union of marks from closed field/class/root scopes matching s,
         if s is in frozenSlots(M); otherwise empty
```

`closedMarkNeeds` is the least union of effective static mark scopes along
the resolved call graph. Its resolved field needs add their own field slot.
Wide/class scopes alone add no field slot. Bare class points still use their
separate direct projector. This definition and F's closure imply both
`frozenSlots(callee) subset frozenSlots(caller)` and inherited B membership.

Keep wide/class mark scopes symbolic and activate them only at selected
slots; do not materialize a field-by-mark product. Mark interests never
imply all fields are read. If an actual supplied field outside frozenSlots
resolves a relevant wide descriptor, that newly resolved interest is not
already a B row: offer it through P/dynamic R below. It adds no F and no
class request slot. Unresolved metadata alone never suppresses a dynamic
offer or counts as admitted R membership.

Collect source geometry for excluded-mark transfers: `g -> f (* except T)`
needs source g/T and later original-slot routing, not a mark request invented
on target f. Writes/alias coordinates can be legal and contribute W without
contributing F. Direct class points stay on the separate supplied-data route.

#### 2.2.1 Static request lifecycle

The following steps apply to both source and sink conditions. They are part
of the same run-1 contract as field preservation and static defaults.

| Step | Field-read plan F | Static mark base B |
| --- | --- | --- |
| Prescan | Collect actual value reads; omit write targets, alias endpoints and mark predicates alone | Collect effective static mark interests with their source geometry |
| After prescan | Compute transitive read closure on the resolved call graph | Close mark scopes on that graph; resolve selected slots and retain wide scopes symbolically |
| Method owner creation | Borrow the immutable read plan | Construct R with the immutable B as its borrowed base, before publishing input/subscription access |
| Actual input arrives | A supplied selected field admits its abstract STAR initial | Actual requested T admits its concrete EXACT/STAR initial; FLOW cannot answer T |
| Same-slot FLOW dependency | No runtime field request is sent | Caller already borrows the callee's frozen membership; skip its redundant upstream offer after origin validation |
| Changed-origin FLOW dependency | The real read that made the dependency supplies F | Offer the marks on the original abstract slot through P; commit fresh marks into dynamic R and notify callers |
| Concrete answer arrives later | Reuse the same field gate | Reuse current R and the same concrete projector; ordinary initial deduplication prevents a second analysis of an equal initial |

F is never inserted into RequestStorage. B has no startup commit or
notification wave and is never copied into P or dynamic R. A late subscriber
reads effective R, including B. A late dynamic request notifies existing
subscribers. These are the two ordinary arrival orders, not two stores of
the input or request. Concrete supplier data remains in the ordinary caller
input subscription; the callee creates no added-fact history for requests.

For example, if A calls B and B checks `ContainsMark(C.f,T)`, prescan gives
both methods frozen C.f/T membership. It adds no F(C.f). Actual C.f/T can
therefore produce a concrete-only initial in either method without a request
propagation wave. If A first copies C.g to C.f, that copy contributes F(C.g).
Its FLOW dependency has original slot C.g, so B's frozen C.f/T still causes
a dynamic C.g/T offer in A. Frozen membership on C.f cannot discharge a
different original slot. Sections 2.3–2.6 specify batching, matching, replay
and coverage; the [proof dossier](static-handling-proofs.md#41-frozen-request-lifecycle-and-no-duplicate-propagation)
states the corresponding closure and arrival arguments.

### 2.3 One effective request relation and temporary batching

Each live SummaryStorage owns the authoritative request relation:

```text
R(K,s) = B(M,s) union dynamicRequests(K,s)
B(M,s) intersect dynamicRequests(K,s) = empty
P(K,s) = owner-local temporary mark sets
```

Use the same RequestStorage class for R and P. R borrows frozen B; P has an
empty base. Dynamic R and P are local to the full MethodKey even when B is
shared between contexts/SCC members. Do not copy B into dynamic R, P or a
per-subscriber map. Do not send a startup B propagation wave: prescan closure
already supplies unchanged static needs to callers. Borrow B before any
subscription/input is visible. An input always reads the full effective R.

```kotlin
// Offer against authoritative R; repeated pending offers merge in P.
fun offer(slot: Run1Slot, marks: MarkSet) { // called by the method owner
    val wasEmpty = temporary.isEmpty
    temporary.addAll(slot, marks.minus(summaryStorage.borrowRequest(slot)))
    if (wasEmpty && !temporary.isEmpty) requestNormalCountedWork()
}

// Barrier: this method's normal work queue is empty.
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Commit unions marks, stores only `P minus R` in dynamicRequests under the
ordinary storage lock, then queues counted notifications of fresh additions
after unlocking. No inline solver callback is made while locked. Existing
subscribers see fresh deltas; later subscribers read stored R. Pending P and
notification work count before quiescence. A first nonempty offer schedules
ordinary counted method Work, even with no new edge. Keep its reservation
through commit, temporary replacement and securing child notifications. A
quantum yield can retain P; the actual method-queue-empty barrier flushes it.
No new scheduler is required. No fixed pending-memory threshold,
new request index, deferred-answer memo or input-by-request history is needed.
A no-op/duplicate commit emits no new request delta.

### 2.4 Matching rows and independent initial-emission gates

`borrowMatchingRows(input)` returns one borrowed effective B/dynamic row per
matching normal slot, without copying its combined mark set. Capture these
immutable row descriptors and the current immutable dynamic mark segments
under the existing SummaryStorage lock. No mutable-map iterator or lazy
Sequence may escape that lock. Release it before answer/replay effects. It ignores
incoming field E and FLOW mx. That omission neither invents a concrete mark
nor relaxes ordinary fact/summary matching. A request has neither exclusion.

```kotlin
fun matchesSlotIgnoringRequestExclusions(input: Facts, slot: Run1Slot): Boolean {
    if (input.base != slot.base) return false
    return input.leaves.any { leaf ->
        slot.path.isPrefixOf(leaf.path) ||
            (leaf.path.isProperPrefixOf(slot.path) && leaf.tail != EXACT)
    }
}

// Called with the existing SummaryStorage lock held; build descriptors eagerly.
fun RequestStorage.borrowMatchingRows(input: Facts): List<RequestedRow> = buildList {
    if (input.base != STATIC) {
        if (input.base != ZERO && !input.base.isCleanerTemporary) {
            val slot = BaseSlot(input.base)
            if (matchesSlotIgnoringRequestExclusions(input, slot)) {
                borrowImmutableNonEmptyRow(slot)?.let { add(RequestedRow(slot, it)) }
            }
        }
        return@buildList
    }
    val slots = if (input.hasNonexactAncestorAboveStaticField())
        logicalStaticRowSlotsOnce() // stored keys/resolved frozen scopes, cancellation-aware
    else
        input.distinctClassFieldSlots()
    for (slot in slots) {
        if (matchesSlotIgnoringRequestExclusions(input, slot)) {
            borrowImmutableNonEmptyRow(slot)?.let { add(RequestedRow(slot, it)) }
        }
    }
}
```

SummaryStorage captures this list under its lock, then consumes the borrowed
immutable descriptors outside the lock. A descriptor borrows B and immutable
dynamic segments; it does not copy their union or keep a live mutable row.
The narrow route uses direct field rows. A broad ancestor scans only logical
requested slots once, with virtual frozen scopes as needed; it does not
materialize marks or require a persistent index. Exact ancestors supply no
deeper field. Wrong bases/classes/fields do not match. Bare class points use
the direct projector. Keep cancellation checks while scanning and iterating
borrowed mark segments; do not retain borrowed cursors/history after use.

For actual input, field and concrete-mark gates are independent:

```kotlin
validateAgainstClosedDomain(input)
for (slot in suppliedFieldSlots(input, fieldReads, requests)) {
    if (fieldReads.requests(slot)) emit(abstractStar(slot))
    for (mark in actualMarks(input, slot).intersect(requests.borrow(slot))) {
        emit(projectExactOrStar(input, slot, mark))
    }
}
emitActualDirectClassComponents(input)
```

| F(C.f) | R(C.f,T) and actual T | Initial output |
| --- | --- | --- |
| No | No | None for this field |
| Yes | No | Abstract C.f.* only |
| No | Yes | Concrete C.f/T only |
| Yes | Yes | Abstract and concrete initials |

Validate legality before gating. A mark-only source or sink gets the actual
requested concrete mark without forcing abstract field analysis. FLOW never
answers T. No request or frozen plan entry generates a concrete supplier.
Both current-R input handling and late-request replay use the same projector
and nine-family admission/deduplication rules. No S.* initial is emitted.

### 2.5 Dynamic routing back to the caller

Calls retain the ordinary exact input subscription, caller premise/context
and input layer **before LinkIn**. Do not gate call delivery/subscription
by F/B/R. The callee abstraction gates only its initial emission. Summary
application uses ordinary summaries, including the default in §6.

```text
new TAINT input   -> read current R -> answer actual requested marks
new FLOW input    -> join current R -> offer marks on the original abstract slot
new request delta -> notify subscribed callers -> revisit retained exact inputs
```

On a caller owner, match the delta against each retained input to that
callee/call relation. TAINT provides actual concrete answers; FLOW routes
interests onto the caller edge's **original** abstract slot, then offers
those merged marks to caller P. This repeats through the normal counted
barriers/notifications until a real concrete supplier answers. Answers go
through the ordinary callee initial-emission path, with grammar validation
and deduplication. The callee retains no second input/answer table.

A concrete current fact or concrete origin does not propagate requests
upstream. A normal abstract origin remains eligible when its current FLOW
is DEMAND or has moved to another field/base. Excluding T from FLOW emits
its original-slot request immediately (into counted P); later matching need
not subtract mx. The immediate contribution and later offers merge by union.

For a frozen B row on an unchanged static slot, resolved-call closure proves
`B_callee(s) subset B_caller(s) subset R_caller(s)`. After origin validation,
the same-slot route can skip that B segment before enumerating its marks.
This is per queried row, not per whole input/plan. A different current slot
still routes to the actual original slot. Dynamic additions and late-resolved
scopes use the ordinary R difference; the B theorem does not suppress them.

```text
A receives C.f; A: C.g = C.f; call B; B: ContainsMark(C.g,T)
F(A) contains f; B's check gives only mark interest g/T
A's abstract f -> g FLOW -> B's g/T row -> offer T on A's ORIGINAL f
actual f/T -> ordinary concrete replay -> actual g/T -> condition evaluation
```

Inherited g/T does not establish f/T. Do not suppress that request merely
because both positions are static or the g/T row was frozen. Similarly,
a nonstatic original copied into a static field routes to BaseSlot(original).
A fully overwritten FLOW component emits no later request from that killed
branch; independent RHS/value branches keep their own actual origins.

Registration precedes current-row reads and exact input retention precedes
LinkIn; commit precedes fresh notification. These orders cover input-first,
request-first and registration races without a duplicate supplier store.

### 2.6 Static/dynamic split and coverage obligations

| Need | Handling |
| --- | --- |
| Value of C.f read by this method/callee | Frozen transitive F; gates abstract field emission only |
| Resolved static mark need, unchanged original slot | Frozen B borrowed into R; input reads/answers it; no B copy or propagation wave |
| Frozen static mark row reached through a changed origin | Ordinary FLOW join offers marks on the different original slot |
| Nonstatic mark need | Dynamic original BaseSlot request |
| Late dynamic interest/unresolved scope activation | P -> commit -> dynamic R -> counted notification/replay |
| Concrete current fact/origin | Answer actual requested marks; skip upstream propagation |
| Direct concrete class point | Separate actual projector; no class/root request slot |

Under the domain assumption that a tainted exact empty S-root is impossible,
every request-capable abstract original is one of the two normal slot forms
in §2.1. For an effective mark need, either its admitted static membership
already exists in B on that same original slot, or the ordinary origin join
offers it dynamically on a normal slot. The latter includes changed origins
and late-resolved scopes. Concrete origins need no upstream interest. Direct
class components use their actual separate supply. This exhausts the selected
request origins without adding an abstract root or class initial.

For each real concrete supplier and requested mark, take its finite dependency
path. Same-slot frozen steps use call-closure inclusion; changed/nonstatic
steps use the original-slot offer. The two arrival orders above ensure every
necessary join is processed and the actual supplier produces the same legal
answer. Union removes duplicate marks while rule evaluation retains complete
NNF/ND support. Selective overwrite kills remove only branches with no strong
old survivor (§3); partial DEMAND FLOW remains request-eligible.

Prove this split without assuming that every needed request/summary already
exists. The [request/static dossier](static-handling-proofs.md) gives the
constructive read/mark closure, supplier and observation arguments, and
scoped checked local laws. Actual producer completeness, packed origin
routing, scheduling, full-premise and frontend/alias bridges remain explicit
implementation obligations. Missing lazy facts never prove negative literals.

## 3. Preservation and pre-store values

Classify the ordinary old-value preservation for each current component:

```kotlin
sealed interface FieldPreservation {
    data object Drop : FieldPreservation
    data object Unchanged : FieldPreservation
    data object NeedsFieldExclusion : FieldPreservation
}

fun run1Keep(old: Edge, write: FieldWrite): Edge? =
    when (planOrdinaryPreservation(old.current, write)) {
        Drop -> null
        Unchanged -> old
        NeedsFieldExclusion -> old.copy(layer = DEMAND) // same AP, Empty E
    }

fun run1Write(input: Edge, write: FieldWrite): List<Edge> {
    val values = valueEdgesFromPreStoreInput(input, write)
    val kept = run1Keep(input, write)
    return listOfNotNull(kept) + values.map(::run1ProducerPolicy)
}
```

These are proposed interfaces, not runtime changes. The plan uses actual
base/path/tail geometry and ordinary write certainty, without allocating E.
A keep operator that mentions `{f}` syntactically does not imply that a
particular input acquires an exclusion: disjoint `x.g.*` stays unchanged.

For a definite destination cylinder D_w:

1. A component wholly inside D_w has no old-value result.
2. A disjoint component is unchanged.
3. A broad ancestor that needs a partial residual with nonempty E retains its
   original component at DEMAND with Empty E.

The third case covers the strong residual but can include removed locations
as unresolved demand. It does not revive a separately represented component
killed in the first case. Classify current conclusions, not their original
premises. Partition packed components independently and preserve headers and
layers; no sibling leaf can suppress or promote another leaf's result.

For `x.f=x.f`, the old x.f component is killed by preservation, while the
pre-store read supplies the new-value result. There is no duplicate retained
old branch. A constant untainted RHS supplies no taint. Source/ZERO generation
keeps its own support. Broad ancestors can still have a DEMAND residual
alongside an independently derived value result.

Use the same rule for definite static/nonstatic stores, selected array
accessors, modeled field stores, field RemoveAll lowered as overwrite and
cleaner/rewriter field writeback. In read-clean-writeback, a selected mark
removed from the temporary is not restored by preserving an old component
wholly inside the destination. A partial ancestor can still need refinement.
Whole-base assignment/cleanup retains its separate ordinary kill semantics.
Possible-alias gen/preservation follows the existing alias contract; this
change does not upgrade it to a definite write or blanket DEMAND retention.

## 4. Empty field exclusions throughout run-1

The field-write rule does not cover every E producer. Partial cleaners and
rule/model results can also need an exclusion, including source-generated
ANY_TAINT even though no ANY_TAINT initial is admitted.

Use ordinary operation results first: an empty result stays empty and an
E-free unchanged/exact result stays valid. Only a surviving residual that
would require nonempty field E takes an explicit DEMAND cover with Empty E,
retaining its base/path/tail/mark and support. A layer-only demotion itself
changes no AP payload. Clearing a required E is a separate approximation.

```text
reference partial result: x.p.[any-taint] / {f}, NORMAL, T
run-1 cover:              x.p.[any-taint], Empty E, DEMAND, T
reference STAR residual:  x.p.* / {f}, NORMAL, *\X
run-1 cover:              x.p.*, Empty E, DEMAND, *\X
```

Never clear E while asserting the original NORMAL must guarantee. Preserve
mx and its immediate original-slot request. Static selected-mark cleaners
partition by their actual first field: cleaning f does not request T on
disjoint g. A g-to-f excluded-mark copy tests source g and requests T on the
original g origin; target disjointness cannot discard a real transfer.

Enforce Empty E before results enter any queue, input relation, index or
summary. Cover local/model/rule/alias/binding/summary/cut producers and source/
end actions, not just store lowering. Choose recipes before allocating E.
Empty-E starts plus closed producers/packing/delivery establish the invariant
by induction. An external E-bearing input needs the explicit cover boundary;
no global factory may erase E in later strong runs.

## 5. Prescan static-write closure

Maintain a separate effect inventory; it does not create field-read requests.
An effective ContainsMark still contributes only a mark interest. Alias
endpoints and static write targets still do not seed abstract field reads.

`LocalStaticWrite(M)` is true if any selected operation may modify static
data: direct or modeled stores; possible static alias write targets;
Clean/RemoveAll and cleaner/return-rewriter writeback; pass/model/source/end
actions that mutate a static field, class or whole S scope. Count semantic
operations before lowering; a constant store can lower to keep-only code.
Identity bindings and pure reads are not writes. Include exceptional/finally
paths conservatively. Unknown call/model/alias effects are potential writes
unless a read-only effect contract proves otherwise. A missing body is not
such a proof.

With the complete resolved call graph, compute the least Boolean closure:

```text
StaticMayWrite(M) = LocalStaticWrite(M)
                   OR any(StaticMayWrite(N), M calls N)
```

Use recursive components. A write-free cycle stays false; a component that
can reach a write becomes true. The existing complete prescan graph is the
input; do not rediscover calls during this closure. Share the final plan by
CommonMethod; full MethodKey instances borrow it. This Boolean is separate
from legal coordinates, field reads F, mark interests B and class components.

## 6. Ordinary default static summaries

After closure, define one ordinary static identity edge per method:

```text
Default(M) = S.* -> S.*
edge layer = if StaticMayWrite(M) then DEMAND else NORMAL
```

The STAR endpoints describe identity; the edge layer is independent. On
reached run-1 method initialization insert this edge through the same
retained-summary, demand-selection and live-publication path as every other
summary. Do not create an S.* initial or process an S.* method fact to derive
it. A summary premise key can contain the root pattern without admitting it
to InitialFactStore. Do not create actors for unreachable methods to publish
defaults. Shared prescan metadata supplies the edge when an actor starts.

```kotlin
// Interface pseudocode; this is the common summary publication API.
publishSummary(SummaryEdge(
    transfer = identity(STATIC, emptyPath, STAR),
    layer = if (staticMayWrite) DEMAND else NORMAL,
))
```

Ordinary summary deduplication/notifications handle the inserted edge. Calls
keep their actual input subscriptions and LinkIn delivery. The callee
abstraction applies field/mark gates to initial emission, not to delivery.
An ordinary S.* summary can match an actual C.f input even when the callee
emits no static initial. Apply the stored edge through the usual summary
handler, then resume the existing call plan. Do not add a local continuation,
separate default lookup, F1 call bypass or default-specific call branch.

| Actual arriving component | Stored edge layer | Ordinary identity result |
| --- | --- | --- |
| C.f at NORMAL | NORMAL | C.f at NORMAL |
| C.f at NORMAL | DEMAND | C.f at DEMAND |
| C.f at DEMAND | Either | C.f at DEMAND |

Preserve caller premise/context and the complete AP payload, including its
tail. Join only the input and edge layers; do not run a layer-driven tail
conversion. If the write bit comes from unrelated g, f can conservatively demote without moving
to g or all statics. Independent callee value computations retain the original
supplied input and their own derivations.

**Representation obligation:** retain STAR identity and its edge-layer bit
until application. A normalized root DEMAND FLOW Fact denotes a different,
uncorrelated ANY target. Current Summary.kt compiles such a payload to root
ANY and loses the actual field suffix. The ordinary summary representation/
compiler must preserve the transfer and the separate layer. Existing edge
application accepts an edgeDemand flag, but its final normalizer also changes
STAR to ANY on demotion. Both layer-driven conversions must be removed for
the selected behavior. Preserve actual ANY transfers when information is
really lost. This is a shared summary/AP requirement; no static/run-1 branch
belongs in calls.

The default can be published before a callee returns. NORMAL classifies
writes; it does not prove return. Ordinary live summaries, including these
defaults, execute inside F1. The guidance-only boundary below restricts their
reuse in later iterations.

## 7. Ordinary handoff and strong run-2 analysis

Run-1 results are **guidance only** for later analysis. Ordinary live
SummaryStorage publications still execute inside F1. Do not persist or read
any run-1 publication as an executable native/opposite transfer record for
later iterations. This
restriction covers all endpoint bases: a provisional static value can become a
nonstatic return, argument or rule result. Retaining only such nonstatic
records would still be unsafe.

Defaults are already present in ordinary retained summary storage. Do not
append them from metadata at handoff. Ordinary demand selection includes
DEMAND defaults; the existing result.analyzers/demandEdges traversal projects
them as usual. ZERO creates reached callee actors and normal initialization
publishes their defaults even without any static initial. Live SummaryStorage
is released before handoff, so normal publication must also retain the row
in the actor's summary store. No separate iterator or global scan is needed.

NORMAL defaults follow the same general F1 guidance policy as all other
NORMAL summaries. Include permitted NORMAL singleton publications in coarse
guidance without treating them as executable records. For every retained
publication preserve each premise member, direction, entry/output geometry
and concrete mark under ordinary demand projection. Canonical demand
projection remains separate from layer-only transfer demotion. It does not
rewrite an executable identity into an ANY transfer.

```kotlin
// Ordinary selection, also for stored defaults; no default-specific branch.
for (actor in result.analyzers) {
    for (edge in actor.summaries.demandEdges()) {
        handoff.add(actor.key, toBackwardDemandPatterns(edge))
    }
}
// The selected general F1 boundary also keeps permitted NORMAL guidance.
// Neither collection becomes executable native/opposite F1 records.
```

Run-2 can use an ordinary broad static demand and S-root FLOW initial under
the later restricted grammar. The S.* initial ban applies to run-1. No special
default query adapter is required. Keep concrete guides/current requirements
additive: FLOW alone cannot cross concrete mark/GEN gates. Keep the general
all-base current-query fallback and full-premise response contract for work
absent from old guidance, including mixed static/local paths.

Guidance may enlarge a query; it must not veto an actual current query's
start or response. Keep the existing source/sink seed-coverage contract.
Filtering a provisional edge must not remove its candidate seed or necessary hint.
Treat **all run-1 sink matches**, NORMAL as well as DEMAND, as candidates
for strong refinement. Build B2 sink seeds from both groups, preserving rule
occurrence, positive support, statement and full context. A NORMAL run-1
label is not a strong confirmation under E-free partial covers and early default summaries.
Publish these report witnesses with `confirmed=false`; the existing report
can classify them as DEMAND candidates. Keep their raw fact/edge layers
unchanged. This report classification does not demote NORMAL transfer facts
or authorize an executable DEMAND summary.

The old forward stop checks for no DEMAND vulnerability or no DEMAND edge
must not prevent B2 when a run-1 candidate still needs strong refinement.
All-NORMAL run-1 work can still need B2. Candidate count, pending strong
refinement and the retained guide/query seeds determine this phase decision;
do not pretend a coarse guide is a new executable DEMAND edge. An explicit
time/policy limit can still stop with a provisional/incomplete result.
Only a complete run hands off state. Partial/OOM state is not a completed
proof or a strong record.

### 7.1 Strong later-phase domain

Run-2 is backward. It uses the **converse of the strong forward relation**,
with ordinary AP reversal and annotations. It uses complete strong residuals, not F1 E-free covers or the
run-1 prepublished defaults, run-1 field gates or run-1 mark-request propagation.
Run-3 and later forward runs use the strong forward relation. Strong-phase
forms and records cannot share an approximated run-1 payload/cache key.

### 7.2 Current queries supply missing callee analyses

When a later call has an actual forward input or backward output requirement,
register that requirement with the callee even if run-1 analyzed no matching
input and published no matching summary. This rule applies to **all bases**:
static data can cross into returns/arguments, and NORMAL singleton nonstatic
summaries also cease to be executable after the run-1 boundary.
Close this rule under actual nested call bindings, including a path such as
`C.f -> local -> argument -> return -> C.g` and its backward traversal.

```kotlin
fun queryStrongCallee(call: Call, requirement: Facts) {
    val q = existingSubscriptions.register(call, requirement, fullPremise)
    callee.startRestricted(q.requiredEntry, guide.hintsFor(q))
    callee.allowResponseFor(q) // current query authorizes start AND response
    // If no proven response exists yet, subscribe and wait.
}
```

This is a semantic interface, not a new mandated retained input store.
Existing caller N2/subscriptions and initial/summary deduplication can own
it. In backward mode `requiredEntry` is at the forward exit. The query's
opposite endpoint is computed by strong analysis; a missing old guide does
not restrict it to the empty set. Keep source-hit/ZERO outcomes and all
complete-premise outcomes. Ordinary demand/limit abstraction still applies.

A response is applicable by the current query's full geometry, annotations
and whole premise. A run-1 `owns(field)` check or an old DemandStore row must
not reject it. Prior guidance can schedule/enlarge work but cannot filter
these current-query responses. Current requirements and ordinary projected demand authorize later work.
A broad static demand may admit a root FLOW query under the later restricted
grammar; that grammar is not the run-1 initial grammar.

Disable the old footprint-complement/root-summary shortcut for strong
queries. F is a read inventory, not a write/clean/return preservation mask.
Its complement cannot authorize identity across a write or nonreturning
call. Strong responses use their proven full relation and ordinary AP read
views, even when F(callee) is empty.

The implementation uses actual current queries as its later-run starts. Old
rows remain phase guidance and seed coverage; an old broad row does not start
an additional wider executable transfer for a precise actual input. This is
a general rule for all bases, not a static-root adapter. Concrete guide
intersections with the actual bounded input may add more precise concrete
starts; they retain the actual supplied mark and cannot create FLOW or
authorize an input region outside that intersection. Register the entire
current-query initial batch before starting any of its members. If a newly
authorized initial already has a computed raw response, publish that response
through ordinary publication deduplication without repeating its analysis.

For fresh production driver runs, later nonzero inputs and initials carry
concrete marks. The induction starts with ZERO and concrete source/sink
requirements. Current-query projection retains the actual mark, and K6 keeps
TAINT/concrete support through statements, calls, records and ND joins. Thus
old abstract run-1 root guidance cannot produce a later FLOW analysis. This
is a production reachability invariant. It does not remove legal external
FLOW queries from the AP grammar or prove an exact correlated residual for
such an external abstract-root query. Keep explicit controls for both scopes.

Unknown is pending, not empty or identity. Publish fresh proven responses
as soon as found. Recursive queries remain in the ordinary worklist until
the simultaneous query/response closure reaches a fixed point. Fair work and
accounting include new queries, responses, source hits and notifications.
An empty result is final only at that fixed point or under an independent
local proof. Cancellation yields an incomplete run.

Backward ZERO discovery retains interpreter I11(e): every forward-reachable
node has a path to a discovery exit. The JVM backward graph connects code
that reaches no original exit to the exceptional exit before reversal.
ZERO starts at both exits; nonzero callee queries start only at the normal
exit. Thus ZERO can discover nested calls and activate their ordinary sink
seeds at BOUND inside a nonreturning loop. A seed's local backward dependency
can reach the method entry and its caller without a forward return from that
method. The forward graph and its modeled normal-return paths stay intact.
This is the existing graph/seed contract, not a new seed scheduler or static
call handler. Finite validation fixtures must supply the same discovery
coverage; an isolated normal exit alone does not satisfy I11(e).

### 7.3 S→S relation

Let D_f be all represented locations at/below C.f. For a local fact set X,
an ordinary strong store `C.f = v` denotes:

```text
store_f,v(X) = (X outside D_f) union valueTransfer_v_to_f(X)
```

All marks and the existing cut/layer policy remain in the value transfer.
For `C.g = C.f`, retain old f when f≠g, kill old g, and copy source f to g.
For f=g, read the pre-store input before killing/replacing; it is a value
assignment, not a read of an already empty destination. A constant untainted
write has no tainted value output. Explicit cleaners remove only their own
scope and marks. A true source contributes ZERO→destination, not S→S.

| Strong method behavior on an actual normal-return path | Required static relation |
| --- | --- |
| No relevant effect | f/T→f/T, for the admitted actual f/T query |
| `g=f`, f≠g | f/T→f/T and f/T→g/T; no old-g identity |
| `f=untainted` | No old-f→f edge; disjoint fields retain identity |
| Selected `clean f/T` | Remove T only in its scope; retain unaffected marks/locations |
| `f=source(T)` | ZERO→f/T; no fabricated old-f origin |
| No normal-return derivation | No caller continuation via a callee return summary |

Identity is computed from the method's actual strong path. It is not a
default answer to a missing summary. Branches join their proven alternatives;
a branch that returns may preserve f even if another branch kills it.
Only paths that reach the modeled normal exit contribute normal summaries.
Exceptional paths use the existing exceptional-return contract, not a normal
identity. Apply call-prefix and return actions in their existing order.

Normal-return evidence means a modeled return action. Virtual-exit reachability
used by AP S11/interpreter I11(e) to totalize backward traversal is not such
evidence. Artificial completion of code that never returns cannot by itself
publish a strong normal-return summary. The transfer adapter must retain this
distinction without changing IR storage. Local source/sink queries inside a
method do not require that the method later returns; only a continuation back
to its caller requires the corresponding return witness.
Forward ZERO/source/end-generation and ND outputs need the same witness for
an executable normal return to a caller. A backward local sink dependency
under §7.2 is not such a return. Reaching a synthetic exit or creating a
candidate is insufficient to prove an executable normal return.

Preservation above a field must retain the **full residual**, not only an
S-root with class C excluded. One lowering of the untouched part is:

```text
S.* except C  union  S.C.* except f
```

Intersect both with incoming geometry and E. This keeps sibling C.g and
other classes while removing D_f. Exact class points are separate from
descendants: writing C.f does not kill an exact point C. Mark exclusions
retain their ordinary denotation. Request-side exclusion erasure is not
available for these operations. Cross-class copies retain their actual
source/destination coordinates. Whole-base cleaners retain their specified
whole-fact behavior; no residual bypass restores removed data.

### 7.4 Calls, reversal and persistent records

Later static facts have **no unconditional call-to-return bypass**. Their
continuation uses a proven strong callee response under the complete caller
premise and an actual modeled return path. A pure helper obtains identity
through §7.2 even if F(helper) is empty. A helper that overwrites f obtains
the kill relation, and a helper that never returns obtains no normal static
continuation. A recursive method returns only through a finite derivation
with a real base return; self-subscription alone creates no identity.

For strong relation R_M, backward answers Q by relational preimage:

```text
pre_M(Q) = { input | exists output in Q, R_M(input, output) }
```

For conjunctive rules replace the scalar input with the complete premise;
do not apply a member independently. ZERO generation remains a source-hit
case. The current native/opposite AP read-view rules, including may/must
and exclusions, must refine this relation rather than blindly swap stored
tuples. No provisional run-1 edge enters that reversal.

Persist only proven strong publications from complete later runs. Tag or
structurally separate their semantics from run-1 guidance. Reuse them only
under the existing direction, limit, demand, full-premise and read-view
contracts. Lack of a reusable record always falls back to the current query;
it never manufactures preservation. This specification does not assert that
a bounded abstract S→S edge proves exact concrete JVM memory behavior.

## 8. Correctness and implementation obligations

The two linked proof dossiers must establish the complete request/write/static contract:

| Law | Required result |
| --- | --- |
| Read/mark plan closure | Least F/B closure includes source and sink needs; writes/aliases/mark predicates do not create F |
| Static/dynamic coverage | Every eligible need is frozen same-slot membership or a dynamic original-slot route under the no-tainted-root assumption |
| Request ownership | Shared B plus disjoint dynamic R; empty-base P; no startup wave or duplicate cumulative request store |
| Barriers and replay | Both arrival orders, same-slot B skip and changed-origin propagation; ordinary exact caller input retained before LinkIn |
| Initial grammar and gates | Nine families; actual supply plus F for abstract, actual T plus R for concrete; no root/class abstract seed |
| Preservation cases | Full overwrite kills; disjoint data is unchanged; only an E-requiring partial residual demotes, with a witness for every strong survivor |
| Layer and geometry | Surviving AP/tail/premise retained; no D→N; killed DEMAND components stay killed; pre-store value branches independent |
| Empty E | All closed run-1 producers/stores preserve Empty E; an E-requiring residual is explicitly a DEMAND cover, never a NORMAL guarantee |
| Static effect closure | Least call-graph closure, including cycles and conservative unknown effects; writes do not create read requests |
| Default application | Complete actual AP, including tail, retained with exact layer join; no ANY conversion, enumeration or propagated root |
| Default availability | Normal method initialization publishes one ordinary row through retained/live stores; independent of static initials; no forced actors |
| Handoff coverage | DEMAND defaults enter normal demand selection/projection; NORMAL defaults follow the general NORMAL-summary policy |
| Strong refinement | Run-2 recomputes preservation/copy/kill/source/return behavior; no provisional default or E-free cover becomes its executable proof |

Prove these in the selected finite AP model, then verify the producer, packed
representation, scheduler, full-premise/ND and seed bridges during runtime
implementation. No field-exclusion-free claim applies to run-2 or later.
Analyzer implementation is unchanged. Runtime validation follows implementation.
The performance target remains run-1 engine time within 60 seconds.
