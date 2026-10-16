# Run-1 weak field writes and default static summaries

Status: selected specification, **not implemented**. This contract overrides
the run-1 field-write, field-exclusion and static-summary rules in
[AP](ap.md), [interpreter](interpreter.md), [core](analyzer-core.md) and
[static handling](static-handling.md). Later runs retain strong logical
stores, ordinary exclusions and the strong query/return contract. The
[proof dossier](run1-field-write-proofs.md) states constructive proofs and
the checked reference-model scope.

The [single complete proposal](../docs/design/bidirectional-request-gated-static-handling-20261016.md)
combines this contract with request ownership, emission gates and static
handling. Its §1 distinguishes the previous selected design from implemented
behavior; §8 traces the ordinary handoff and corrects the former global-view
requirement.

Latest measured iteration: **run-1, FORWARD, limit 1; OOM**. No completed
iteration or accepted forward report. This update adds no runtime measurement.

## 1. Selected rules

1. Every run-1 heap field write is weak, for static and nonstatic fields.
2. When logical field exclusion/restriction is needed, move the old edge to
   DEMAND instead. For a field overwrite this includes full old-value removal,
   not just appending an E element. Preserve its actual location and premise.
3. Do not add a field exclusion. **Every admitted run-1 fact, initial and
   summary payload has Empty field exclusion**, at every processing stage.
4. Add the new written-value edges from the pre-store state. They keep their
   own transfer layer; they are not the retained-old-value branch.
5. After prescan, define a default static identity summary for every method:
   NORMAL if neither the method nor any callee may write statics; DEMAND otherwise.
6. Publish it when the reached method starts, through the ordinary summary path.
   Apply it through ordinary subscriptions and summary application. The abstraction
   emits no S.* initial; there is no special run-1 call handling.
7. Carry selected demand edges into run-2 through the ordinary handoff.
   Run-2 computes strong summaries; it does not treat the defaults as their proof.

The change is an intentional may abstraction. A DEMAND survivor can represent
an old value that a strong write would remove. Its field path is preserved;
its survival has not been proved. Do not describe it as an exact strong edge.

## 2. Scope, layers and normal form

The selected run-1 limit remains exactly 1. Its nine initial families stay
unchanged. S differs from ZERO, class accessors do not consume the field
limit, and no abstract S-root initial is admitted. Actual broad static TAINT
data and separately supplied direct class components remain legal.

Let N=normal and D=demand, with `N ≤ D`. Layer join is logical OR:

```text
N join N = N
N join D = D join N = D join D = D
```

DEMAND never becomes NORMAL along the same propagation derivation. A separate
new-value/source derivation can have its own NORMAL edge; do not merge the
derivations in a way that promotes uncertain old data.

**Layer demotion preserves the complete AP payload.** Move the edge to
DEMAND without changing its base, path, tail, mark, mark exclusions, original
initial, full premise, context or call identity. In particular, `x.* [N]`
becomes `x.* [D]`, not `x.[any]`. The field information has not been lost.

This overrides the older W2/W8 rules where they converted a tail solely
because the edge became DEMAND. STAR and layer are independent. A genuine
operation that loses field/correlation information can still produce ANY;
that is a separate transfer, not a consequence of demotion. This does not
change the existing projection of nonexact concrete suppliers to legal
run-1 initials or their start-fact representation.

Empty E means **field exclusion**, not FLOW mark exclusion mx. Selected mark
cleaning and original-slot requests still use mx. Their request matching
rules are unchanged. No run-1 initial has mx. Full rule geometry/NNF and
signed eligibility remain in forms.

## 3. Local field-write transfer

A field write names a destination cylinder D_w: the locations at/below the
written field. Use the existing alias/array overlap semantics. The relevance
test is against the **current conclusion**, not its original premise.

General rule: **when run-1 would need a field-exclusion update, move the
affected edge to DEMAND instead and keep Empty E**. For a field overwrite,
the logical exclusion step includes a complete destination kill. Testing
only whether an E object gains an accessor would miss exact old x.f/T,
which is still retained at DEMAND under the selected weak-write policy.

For `x.f=y`, old `x.*` overlaps D_w; old exact `x.f/T` is inside; old exact
`x.g/T` with f≠g is disjoint. An exact point x is not changed by assigning a
descendant field. For a broad conclusion containing both changed and
unchanged locations, retain its whole existing geometry at DEMAND when an
exact exclusion-free split is unavailable. Do not enumerate all fields.

```kotlin
fun preserveAcrossFieldWrite(old: Edge, write: FieldWrite): Edge =
    if (mayOverwrite(write.destination, old.current))
        demandNormalForm(old) // same current path and original premise, Empty E
    else
        old

fun run1Write(input: Edge, write: FieldWrite): List<Edge> =
    listOf(preserveAcrossFieldWrite(input, write)) +
        valueEdgesFromPreStoreInput(input, write).map(::run1FactNormalForm)
```

Apply this to each affected conclusion component, including possible alias
targets. Packed components whose layers differ remain separate. Do not
discard a disjoint NORMAL leaf merely because a sibling broad leaf demotes.

| Input and operation | Retained-old-value result |
| --- | --- |
| `x.* [N]; x.f=y` | Same root x geometry, DEMAND, Empty E |
| `x.f/T [N]; x.f=constant` | Old x.f/T retained, DEMAND, Empty E |
| `x.g/T [N]; x.f=constant`, f≠g | x.g/T, NORMAL |
| `C.f.* [N]; C.f=y` | Same C.f geometry, DEMAND, Empty E |
| Already DEMAND input | Remains DEMAND |
| Definite disjoint base/location | Preserve its current layer |

An untainted/untracked RHS adds no tainted value edge. For `x.f=x.f`, read
the pre-store value first: a valid value-transfer branch can coexist with
the DEMAND old-preservation branch. New-value transfer does not kill the
preserved branch. Source/ZERO origins remain separate from old input origins.

This rule covers ordinary instance/static field stores, array-element writes
under the selected field-accessor model, modeled field stores and alias value
updates. A nonstatic **whole-base local assignment** `x=y` is not a heap field
write: it keeps its existing strong whole-base replacement semantics, which
does not require a field exclusion.

A field RemoveAll lowered as a field overwrite and a field-cleaner/rewriter
writeback also use this rule, even for an exact old destination fact. For a
read-clean-writeback action, cleaning its temporary can remove selected T
exactly; the final weak field write still retains old destination T at DEMAND.
Direct selected-mark Clean and whole-base cleanup are distinct operations,
not ordinary field overwrites. Classify the logical action before lowering.

## 4. Empty field exclusions throughout run-1

Weak ordinary stores alone do not establish this invariant. Partial cleaners
and RemoveAll/cleaner writeback can also create E, including a source-generated
ANY_TAINT fact even though no ANY_TAINT initial is legal.

Retain exact exclusion-free selected-mark cleaner/filter outcomes when
available. Their complete kill can remain a kill. This does not exempt a
field overwrite/writeback from §3's weak preservation. When a residual requires nonempty
E, replace that residual with a **DEMAND may cover**, with Empty E. Keep the
same base/path/tail/mark and original premise. Clearing the required field
restriction is a separate conservative cover; changing layer alone never
changes the tail.
Do not merely clear E on a NORMAL must fact: that would assert the removed
locations carry the mark. Preserve mark exclusions and issue their existing
original-slot requests before publishing the excluded-mark output.

```text
reference partial result: x.p.[any-taint] / {f}, NORMAL, T
run-1 cover:              x.p.[any-taint], Empty E, DEMAND, T

reference correlated residual: x.p.* / {f}, NORMAL, *\X
run-1 cover:                   x.p.*, Empty E, DEMAND, *\X
```

Explicit field RemoveAll and cleaner/rewriter field writeback already use
§3; any remaining E-requiring cleaner residual uses this cover too.
Fully handled direct mark cleaners retain their local semantics; the
partial residual is conservative rather than restoring a NORMAL guarantee.
Static selected-mark cleaning still partitions at the first static field:
a cleaner on f must not request T on disjoint g. Generic excluded-mark copies
still match their source and request their original input, not their target.

Enforce the invariant for every operation result before it becomes a queued
fact, a call input, a summary, a source/end-action result or an indexed/stored
fact. This includes direct/model writes, aliases, rules, partial clean,
bindings, summary application, tree operations and cuts. Constructors which
only preserve Empty E cannot introduce it. A producer with an explicit
nonempty E result must use the proved may-cover rule, not silently publish it.

The mathematical proof can describe an excluded reference residual. The
implementation should choose the exclusion-free run-1 recipe before building
it; no temporary E allocation is required. Strong later-run recipes can still
contain field exclusions. Pattern/compiled-kernel syntax for later runs is
not a propagated run-1 fact.

Run-1 starts with empty previous-run records/handoff and Empty-E initials.
Supplied internal data must come from the same closed producer set. Assert
that boundary; an unexpected external E-bearing fact is not silently treated
as exact Empty-E input. Its only permitted admission is through a proved
DEMAND cover. No shared factory change may erase E in later runs.

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
writes; it does not prove return. The general F1 guidance-only boundary still
applies to all its summaries, including these defaults.

## 7. Ordinary handoff and strong run-2 analysis

The default is already stored. Ordinary selection retains a DEMAND default
in the actor's demandEdges(), and ordinary handoff projects it with the other
selected edges. No custom default iterator, virtual descriptor export,
whole-prescan scan or post-run default insertion is needed. The common
publication path must update both retained actor summaries and live storage;
live SummaryStorage is released before handoff.

On completed reached F1 paths, ZERO creates callee actors even when F/B is
empty. Their normal initialization publishes the default without a static
initial. Thus ordinary result.analyzers traversal suffices. See the
[ordinary pipeline trace](../docs/design/bidirectional-request-gated-static-handling-20261016.md#8-ordinary-demand-selection-and-handoff).
NORMAL defaults follow the same general F1 guidance policy as other NORMAL
summaries; they have no special export or persistence exception.

Run-2 is strong backward analysis. It uses the projected demand as usual,
including a broad static demand and S-root FLOW initial when ordinary later
restricted emission selects one. Only run-1 forbids that initial. Strong
field writes retain full residual exclusions and infer the required S->S
relation. A default is not an executable reversed strong result.

Preserve the general all-base current-query fallback, source-hit/ZERO cases,
concrete mark/GEN query branches and full ND support. Ordinary strong copy
can answer a g requirement from f; a constant f overwrite kills its old-f
preimage; lack of a modeled return gives no strong normal-return answer.
No default-specific query specialization or handoff logic is required.

All F1 sink matches remain unconfirmed candidates regardless of raw layer.
Preserve their seeds/support; no zero-DEMAND-edge shortcut may skip required
strong refinement. Strong records begin with completed later runs and keep
the existing demand/limit/read-view contracts. Synthetic virtual exits alone
do not prove a strong return.

## 8. Correctness and implementation obligations

The proof dossier must establish:

| Law | Required result |
| --- | --- |
| Weak write coverage | Every strong surviving/value output has a run-1 may witness; overwritten old data cannot gain a NORMAL guarantee |
| Layer and geometry | Affected preservation demotes, disjoint data keeps its layer, value branches stay independent; no D→N; actual path/premise retained |
| Empty E | All closed run-1 producers/stores preserve Empty E; an E-requiring residual is explicitly a DEMAND cover, never a NORMAL guarantee |
| Static effect closure | Least call-graph closure, including cycles and conservative unknown effects; writes do not create read requests |
| Default application | Complete actual AP, including tail, retained with exact layer join; no ANY conversion, enumeration or propagated root |
| Default availability | Normal method initialization publishes one ordinary row through retained/live stores; independent of static initials; no forced actors |
| Handoff coverage | DEMAND defaults enter normal demand selection/projection; NORMAL defaults follow the general NORMAL-summary policy |
| Strong refinement | Run-2 recomputes preservation/copy/kill/source/return behavior; no weak default becomes its executable proof |

Prove these in the selected finite AP model, then verify the producer, packed
representation, scheduler, full-premise/ND and seed bridges during runtime
implementation. No field-exclusion-free claim applies to run-2 or later.
Analyzer implementation is unchanged. Runtime validation follows implementation.
The performance target remains run-1 engine time within 60 seconds.
