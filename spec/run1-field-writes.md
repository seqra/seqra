# Run-1 field writes without field exclusions

Status: selected specification, **not implemented**. This contract overrides
run-1 field preservation in [AP](ap.md), [interpreter](interpreter.md),
[core](analyzer-core.md) and [static handling](static-handling.md). It replaces
the former general weak-write proposal. Later runs retain ordinary strong
stores and exclusions. The [proof dossier](run1-field-write-proofs.md) states
the constructive arguments and checked local scope.

The [complete proposal](../docs/design/bidirectional-request-gated-static-handling-20261016.md)
combines this rule with requests and ordinary default static summaries.
Latest measured iteration: **run-1, FORWARD, limit 1; OOM**. None completed;
no accepted forward report. This specification adds no runtime measurement.

## 1. Selected rules

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

The remaining selected rules stay in force: nine run-1 initial families,
layer-only demotion, independent field/mark emission gates, merged mark
requests, ordinary stored N/D default static summaries, no S.* initial
emission in F1, and strong later refinement through ordinary handoff.

## 2. Scope, layers and known AP information

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
relation. A default is not an executable reversed strong result. The run-1 complete
overwrite kills do not remove the need to refine partial residuals/defaults.

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
