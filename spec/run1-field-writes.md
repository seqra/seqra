# Run-1 weak field writes and default static summaries

Status: selected specification, **not implemented**. This contract overrides
the run-1 field-write, field-exclusion and static-continuation rules in
[AP](ap.md), [interpreter](interpreter.md), [core](analyzer-core.md) and
[static handling](static-handling.md). Later runs retain strong logical
stores, ordinary exclusions and the strong query/return contract. The
[proof dossier](run1-field-write-proofs.md) states constructive proofs and
the checked reference-model scope.

Latest measured iteration: **run-1, FORWARD, limit 1; OOM**. No completed
iteration or accepted forward report. This update adds no runtime measurement.

## 1. Selected rules

1. Every run-1 heap field write is weak, for static and nonstatic fields.
2. Preserve an old fact that the write can overwrite, but move its propagation
   edge to DEMAND. Preserve its actual location and original premise.
3. Do not add a field exclusion. **Every admitted run-1 fact, initial and
   summary payload has Empty field exclusion**, at every processing stage.
4. Add the new written-value edges from the pre-store state. They keep their
   own transfer layer; they are not the retained-old-value branch.
5. After prescan, install a default static identity template for every method:
   NORMAL if neither the method nor any callee may write statics; DEMAND otherwise.
6. Apply that template to actual caller statics without propagating S.*.
7. Carry the demand edges/templates into run-2 as analysis requirements.
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

Keep the existing W2/W8 normal form. Moving a correlated FLOW STAR conclusion
to DEMAND represents it with may ANY; moving a must ANY_TAINT conclusion to
DEMAND also gives may ANY. EXACT remains EXACT. Base, path, concrete mark or
mark-exclusion payload, original initial, full premise, context and call
identity stay attached to their actual derivation. Thus `x.* [D]` below is
semantic shorthand, not permission for an illegal scalar DEMAND/STAR fact.

Empty E means **field exclusion**, not FLOW mark exclusion mx. Selected mark
cleaning and original-slot requests still use mx. Their request matching
rules are unchanged. No run-1 initial has mx. Full rule geometry/NNF and
signed eligibility remain in forms.

## 3. Local field-write transfer

A field write names a destination cylinder D_w: the locations at/below the
written field. Use the existing alias/array overlap semantics. The relevance
test is against the **current conclusion**, not its original premise.

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
same base/path/mark and original premise, and apply W2/W8 normalization.
Do not merely clear E on a NORMAL must fact: that would assert the removed
locations carry the mark. Preserve mark exclusions and issue their existing
original-slot requests before publishing the excluded-mark output.

```text
reference partial result: x.p.[any-taint] / {f}, NORMAL, T
run-1 cover:              x.p.[any], Empty E, DEMAND, T

reference correlated residual: x.p.* / {f}, NORMAL, *\X
run-1 cover:                   x.p.[any], Empty E, DEMAND, *\X
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

## 6. Default static summary templates

After closure make one shared default template available for **every** method,
including methods with no run-1 actor, no static input and no summary from
execution. It has symbolic source/target S and the layer:

```text
Default(M) = S.* -> S.* [if StaticMayWrite(M) then D else N]
```

This is a **tagged default summary schema**, not an InitialAp, a propagated
root fact or an observed normal-return publication. Summary storage exposes
it independently of executed-initial rows. All full method keys borrow the
method plan; do not create actors, per-field rows or a startup notification
wave just to install it. Ordinary field-specific callee summaries remain.

For an actual caller static component a, instantiate identity at a's actual
geometry and join its layer with Default(M). Then use the ordinary W2/W8
normal form and its Empty E. Do not apply a generic root-to-root may relation
that forgets which static field a supplied.

```kotlin
fun applyRun1Default(method: Method, callerEdge: Edge): Edge {
    require(callerEdge.current.base == STATIC)
    val layer = callerEdge.layer join method.defaultStaticSummary.layer
    return run1FactNormalForm(callerEdge.copy(layer = layer))
}
```

| Actual arriving component | Default | Result |
| --- | --- | --- |
| C.f at NORMAL | NORMAL | C.f at NORMAL |
| C.f at NORMAL | DEMAND | C.f at DEMAND |
| C.f at DEMAND | Either | C.f at DEMAND |

No new field, class or mark is invented. Retain full caller premise/context,
mark exclusions and supplied class-point identity. The single Boolean can
demote g because a callee might write unrelated f; this is conservative layer
precision. It does not widen g into f or all statics.

Use this lane at the CALLEES stage, after local prefix actions and before the
existing returned-rule/bind-back/alias/cut stages. Set `identityCandidate=true`
for the instantiated identity. It replaces the prior unconditional NORMAL
static bypass; do not run both. Actual callee outputs still add results.
Deliver actual data to callees under the existing read/mark/class gates.
Use the original pre-default data and its layer for that delivery; the
default's joined layer belongs only to the local preservation branch.
Nonstatic call inputs keep their existing call protocol.

This F1 default is immediately available, including for a nonreturning target.
Its NORMAL label certifies only the absence of a discovered static-write
effect in the closed plan, **not normal return**. All F1 publications/defaults
remain inside the existing guidance-only, unconfirmed-candidate boundary.
A default is never an executable later strong record.

## 7. Handoff and strong run-2 analysis

Preserve DEMAND write-survival edges and tagged defaults in the demand handoff.
Do not apply the old blanket ban on all root-shaped identity patterns: a
permitted default schema has root-shaped syntax without root propagation.
Reject ordinary synthetic root-execution artifacts and all F1 executable
native/opposite records; retain the default descriptors as **guidance**.

HandOff has both executed publication guidance and a borrowed default-plan
view over the entire prescan graph. Iterating only `result.analyzers` is
insufficient. Keep NORMAL default descriptors available too: a read-only helper
with no F1 actor still needs a strong identity query when actual demand arrives.
One shared default lookup is enough; no per-method-per-field expansion.

On a run-2 static requirement q:

1. Look up its callee's tagged default guide independently of F1 execution.
2. Intersect the symbolic S scope with **q's actual field/class geometry**.
3. Normalize that actual requirement for the ordinary strong backward run.
4. Admit its callee query and authorize computed responses by the current
   query, even if no old field-specific summary or guide row exists.
5. Execute strong operations and ordinary exclusions. Publish only witnessed
   strong responses under the full premise and modeled return contract.

The default identity is not its own reverse answer. For a write `g=f`, an
actual requirement for g can produce source f; the guide must not constrain
the answer to g. Source-hit/ZERO outcomes and nonstatic intermediates remain
legal. An overwrite can remove the old-field preimage; a nonreturning method
has no normal-return answer. Reuse the existing all-base actual-query fallback
for local/argument/return paths and ND responses: S→S templates alone do not
cover those paths. They are ordinary restricted queries, not run-1 requests.

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
| Empty E | All closed run-1 producers and stored/queued payloads preserve Empty E; must guarantees weaken before E is forgotten |
| Static effect closure | Least call-graph closure, including cycles and conservative unknown effects; writes do not create read requests |
| Default application | Actual component retained with exact layer join, no field enumeration or propagated root |
| Default availability | Available for every prescan method, including no actor/no initial/no execution publication |
| Handoff coverage | Default guide plus actual query admits both start and computed response; demand and NORMAL defaults are retained symbolically |
| Strong refinement | Run-2 recomputes preservation/copy/kill/source/return behavior; no weak default becomes its executable proof |

Prove these in the selected finite AP model, then verify the producer, packed
representation, scheduler, full-premise/ND and seed bridges during runtime
implementation. No field-exclusion-free claim applies to run-2 or later.
Analyzer implementation is unchanged. Runtime validation follows implementation.
The performance target remains run-1 engine time within 60 seconds.
