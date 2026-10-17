# Run-1 bottom-up propagation of concrete static marks

Date: 17 October 2026. Status: **implemented; core validation passed; project resource/acceptance gates failed**.
Scope: run-1 FORWARD, field limit 1. Run-2 and later use their ordinary
demand-driven analysis. IR storage and reference/cache managers are outside
scope.

Measured runtime: `6b9291d7d`. Latest reached/completed/accepted forward:
OWASP run-8/run-7/run-7; conductor run-1/none/none; ThingsBoard
run-1/none/none; Stirling-PDF run-2/run-1/run-1. See the
[current project report](../docs/reports/bidirectional-run1-bottom-up-concrete-static-20261017.md).
The 60-second run-1 engine target is not met. These results do not change
the selected semantic contract.

The next scheduling refinement is specified in
[batched static-need replay](run1-static-need-replay-batching.md). It replaces
section 5's immediate dispatch/per-source wave policy when implemented;
it preserves this document's analysis rules. The measured runtime still uses
the immediate replay baseline below.

This contract extends [bottom-up static FLOW](run1-bottom-up-static-propagation.md)
to concrete static marks. It replaces that contract's
concrete-static admission, static mark-request plan and static-origin request
transport. Its other requirements still apply. The complete selected workflow
is below. The [proof dossier](run1-bottom-up-concrete-static-proofs.md) states
the checked model and the implementation proof boundary. Follow
[proof-guide.md](proof-guide.md) when refining either model or implementation.

## 1. Selected behavior and changes

1. Do not propagate ordinary static-field mark requests. Do not retain a
   transitive frozen static mark plan in each method.
2. Admit concrete static initials from **actual supplied concrete marks**,
   without a static mark-request membership test.
3. Make all run-1 static-related propagation DEMAND, including concrete
   exact field/class initials and dependent nonstatic results. Demote before
   layer-sensitive evaluation. Preserve the entire AP payload.
4. Retain a local static mark need once at its source. Route its merged mark
   set bottom-up through actual FLOW call bindings. At a nonstatic original,
   offer an ordinary nonstatic mark request. This is the bootstrap for an
   abstract input written to a static field.
5. A need is an interest, never concrete evidence. It cannot create T, pass
   a source/sink condition or satisfy an ND premise.
6. Use the existing bottom-up effect transport for actual concrete static
   inputs. Execute ordinary operations at every effect boundary. Collapse
   only certified transparent segments.
7. Keep the universal ordinary `S.* -> S.* [DEMAND]` default in every reached
   method. Do not emit an S-root initial. Do not add a normal-exit gate.
8. Persist run-1 NORMAL summaries as usual. The static-dependent results are
   DEMAND; independent nonstatic NORMAL results remain eligible.

| Topic | Current specified/runtime behavior | New behavior | Intended benefit |
| --- | --- | --- | --- |
| Concrete field admission | Actual T plus a matching static mark request | Actual T alone, subject to ordinary geometry | Removes static request/answer rendezvous |
| Static mark plan | Symbolic inherited B plus dynamic static R/P | Local reached need sources plus a shared mark-independent route | Avoids inherited static mark memberships and repeated intermediate admission |
| Dynamic static-origin need | Ordinary request propagation at each call join | One source batch visits the shared route; only BaseSlot endpoints enter R/P | Avoids per-method static request stores |
| Abstract nonstatic origin written to C.f | Ordinary BaseSlot request | Same BaseSlot request at the W boundary | Keeps upstream concretization |
| Exact static concrete start | NORMAL | DEMAND, same exact payload | Uses the selected uniform static policy |
| Concrete static-to-local result | Ordinary layer joins | Sticky DEMAND dependency | Prevents a static dependency becoming NORMAL |
| Concrete transport through a pass-only region | Ordinary bound inputs/work | Lossless carrier plus certified relay route | Shares route structure across marks |
| Pure exact concrete field identity | May be stored separately | May be covered by root D default under the full identity test | Removes redundant identities |
| Concrete STAR initial to ANY conclusion | Ordinary effect | Retained as an effect | Preserves suffix information already lost by abstraction |

This is a deliberate change of concept as well as a transport optimization.
The equality target is the new ordinary reference in §8, not the measured
runtime with its old admission/layer policy. More eagerly admitted actual
marks can increase local work. The model proves a reduction for a transparent
chain cohort; it does not prove a whole-project performance gain.

## 2. Data, origins and initial grammar

Use the scalar AP calculus in [ap.md](ap.md). An AP contains base, accessor
path, tail, mark/FLOW exclusions mx, field exclusions E and layer. `S` is the
static base, distinct from ZERO. `*` in a path denotes a correlated abstract
tail; `[any]` denotes an uncorrelated tail. These are different kinds.

A full MethodKey K includes method, entry and context. A call port contains
caller K, call statement, full callee K, call-plan stage and caller layer.
An input retains this port, its **complete original premise**, current AP,
layer and immutable support. Do not pool support by field, mark or callee.

The nine families below are exhaustive. All initial field E and FLOW mx are
empty. `b` is nonstatic/nonzero. A class accessor does not consume the field
limit. A supplied broad/descendant AP is not itself a new initial.

| Initial family | Initial shape | Started current fact | Admission |
| --- | --- | --- | --- |
| ZERO | ZERO exact | REACH, NORMAL | Actual reach entry |
| Nonstatic abstract | `b.* (*)` | FLOW, NORMAL | Actual base geometry |
| Static abstract | `S.C.f.* (*)` | FLOW, DEMAND | Actual field geometry |
| Nonstatic concrete exact | `b. (T)` | Same exact TAINT, NORMAL | Actual exact T and BaseSlot request |
| Nonstatic concrete chain | `b.* (T)` | TAINT `[any]`, DEMAND | Actual nonexact/descendant T and BaseSlot request |
| Static concrete exact | `S.C.f. (T)` | Same exact TAINT, DEMAND | Actual exact field T; no static request |
| Static concrete chain | `S.C.f.* (T)` | TAINT `[any]`, DEMAND | Actual compatible broad/descendant T; no static request |
| Direct class exact | `S.C. (T)` | Same exact TAINT, DEMAND | Actual exact class T |
| Direct class chain | `S.C.* (T)` | TAINT `[any]`, DEMAND | Actual broad supply at a legal structural class point |

Forbidden initials include `S.*`, an abstract class point, a concrete empty
static root, `[any]`/must, nonempty E/mx, a nonstatic field path and a second
field accessor. In particular, `(S, ., $, {}, T)` is outside the supplied
exact-root domain. No proof relies on that impossible run-1 initial.

Project fields against a finite legal coordinate inventory. A supplied leaf
matches a field if it is at/below that field or is a **nonexact** ancestor.
Wrong bases/classes/fields do not match; an exact class/root ancestor does
not supply a deeper field. Choose EXACT only for an exact leaf at the field
point; otherwise choose STAR. Keep the actual supplied mark T. E and mx do
not restrict **interest/projection**; ordinary concrete transfer still uses
its full AP semantics. FLOW has no concrete T to project.

Direct class components use the separate actual class projector. Retain the
legal structural class-point inventory for code/rule/model consumers, closed
over resolved callees. An exact class component is not a field component.
Broad root/class **actual TAINT** can project to legal field/class STAR
initials; it does not create an S-root initial or a root/class request slot.
Both reference and optimized procedures use the same legal inventory.

## 3. Uniform DEMAND and ordinary effects

```kotlin
fun normalize(input: Input): Input = input.copy(
    layer = input.layer or
        input.original.any { it.hasStaticBase() } or
        (input.current.base == STATIC)
)

fun advance(input: Input, next: Fact, operationLayer: Layer): Input =
    normalize(input.copy(current = next, layer = input.layer or operationLayer))
```

The pseudocode joins layers, where DEMAND dominates NORMAL. It copies the
entire AP and original/support. A nonstatic result that depends on a static
input stays DEMAND. For multi-premise operations, join all actual contributing
layers, including a static member of an ND premise. Independent derivations
keep their own support/layer. A static output created from ZERO also becomes
DEMAND before subsequent processing.

Layers are relative to each method's own summary premise. A callee's fresh
nonstatic initial can have a valid independent NORMAL summary. Applying it
to a DEMAND caller input still joins to DEMAND; the caller's static history
cannot be cleared by that call. Preserve the caller layer in the full port.

Apply normalization at starts, local results, prepared call inputs, summary
application and publication, **before** any guard reads the layer. Merely
relabeling an old NORMAL execution's final summaries is not this policy.
Demotion alone does not change EXACT/STAR to ANY.

Retain the selected strong run-1 field-write behavior. For `x.f = y`:

```text
old x.*   -> survivor at DEMAND, same payload and Empty E
old x.f.* -> killed if definitely overwritten
old x.g.* -> unchanged if disjoint
```

The general rule remains: a surviving edge that would need a field-exclusion
update moves to DEMAND and keeps Empty E. This does not turn a killed edge
into a survivor. Apply ordinary alias precision. Evaluate RHS/source/rule
results before the store and retain their own outputs/events; self-assignment
can regenerate the killed region from the pre-store value.

Source and sink NNF conditions, exclusions, concrete cleaners, mark changes,
strong kills, full ND conjunctions and call-plan stages remain ordinary
effect operations. A concrete excluded/killed T cannot become evidence for a
hit merely because a conservative need for T was routed earlier.

## 4. Prescan, need creation and retained requests

Prescan already resolves calls. Collect legal coordinates, local static
read/effect locations and local mark-sensitive operations. These are local
analysis metadata. Do not build static field requests, inherited static mark
sets B, per-field/per-mark transitive closures or a startup request wave.
Raw local templates do not prove that a value reached their statement.

Execute ordinary FLOW mark-interest production at its actual operation point.
This includes source **and** sink conditions, reads to locals followed by
conditions, cleaners, rewrites/conversions, exclusions and call/rule effects.
Keep interests emitted by an operation even if it has no surviving fact.
Keep the ordinary component selection and immediate excluded-target interest
rule; do not turn an optimization of matching into a new truth rule.

Partition an emitted interest by the **original**, not the current base:

```kotlin
fun need(original: Original, marks: MarkSet) = when (original) {
    is AbstractBase -> temporary.offer(original.base, marks) // ordinary R/P
    is AbstractStaticField -> staticNeedSources.union(original.field, marks)
    is ConcretePremise, Zero -> Unit
}
```

`staticNeedSources` belongs to the full source MethodKey. Union marks at that
source. It is the authoritative interest record, not a second ordinary
RequestStorage. Do not copy its mark set into intermediate methods.
Concrete/ND premises do not propagate mark requests. A nonstatic abstract
origin still produces BaseSlot requests when its **current** value is static.
Conversely, a static abstract origin uses the fast route when its current
value is nonstatic.

Retire the unchanged-static-origin/same-FLOW-leaf suppression whose proof
relied on inherited B. A direct `ContainsMark(C.f,T)` on canonical abstract
`i_f` must register its need. This registration does not assert the condition.

Ordinary R/P now have only `BaseSlot(b)` keys. They retain union, exclusions-free
matching, original-base routing and the existing method-queue-empty commit:

```kotlin
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

There is no mark-exclusion subtraction from a need batch. Field E is ignored
by requests. FLOW mx records excluded concrete marks; it is not a concrete
mark and does not remove interest. Excluding T already emits T's immediate
interest. Unioning repeated interests is valid even if mx later differs.

## 5. Fast bottom-up routing to W boundaries

Define an interest node as `(full K, abstract original field C.f)`. Define a
terminal as `(full K, abstract original nonstatic base b)`. No root/class or
concrete-origin node is needed by the initial grammar.

The shared route is **mark independent**. Compile its relation from already
derived ordinary FLOW inputs at a callee stage:

```text
prepared input:
  caller K, statement c, target N, stage s, caller original o,
  current FLOW a, full payload/layer/support

for each legal callee field f geometrically supplied by a:
  callee node (N,f) --reverse input binding--> caller original o

o = abstract field g : continue at (K,g)
o = abstract base b  : emit BaseSlot(K,b) need at this W boundary
```

The stored binding retains the full port and actual current geometry. The
visited interest key can be `(K, original field)` because requests already
merge at that semantic key; it is not a key for pooling actual truth. Full
ports remain essential to construct valid edges and apply concrete effects.
If several valid ports induce one logical edge, their provenance can share
an immutable relation. Do not create a second copied caller-input history.
Use existing bound W inputs and packed identity relations where possible.

Geometry matching here ignores E and FLOW mx. It is the same interest-side
matching as ordinary request joins. An actual static `g -> f` input maps a
need on f back to **g**, not f. A nonstatic `b -> f` input terminates at b.
These W boundaries include copies, returned callee values, models and rule
outputs, not only static STORE opcodes.

Only actual post-binding, post-cleaner, post-guard inputs make routes. A
definite overwrite that leaves no matching input makes no route. The resolved
prescan call graph alone, ZERO reachability or a method-wide static pool
cannot replace this relation. A concrete input supplies its actual marks;
it cannot propagate a need backwards as if it were FLOW.

An ordinary BaseSlot join can map back to a static original. In that case
offer the resulting interest to the static source/router again. The pipeline
can alternate ordinary nonstatic segments and fast static segments. Do not
drop such an interest merely because ordinary R/P rejects static keys.

### Incremental delivery and cycles

```kotlin
onSourceDelta(source, newMarks) {
    commitUnionAtSource(source, newMarks)
    enqueueRoute(source, newMarks, currentBindingSnapshot())
}

onNewBinding(binding) {
    registerBinding(binding) // before replay
    // Correct baseline: replay committed sources with a fresh traversal.
    for (source in committedSources)
        enqueueRoute(source.key, source.marks, currentBindingSnapshot())
}

route(source, marks, graph) {
    val visited = HashSet<InterestNode>() // per traversal, not permanent
    visitReverseBindings(source) { endpoint ->
        when (endpoint) {
            is StaticOrigin -> visitOnce(endpoint, visited)
            is BaseOrigin -> enqueueBaseRequest(endpoint, marks)
        }
    }
}
```

This is semantic pseudocode. Route the immutable batch reference through
intermediate nodes; union/enumerate marks at the source and ordinary BaseSlot
boundary. Do not expand each mark into a request at each relay.

Each finite snapshot has a finite visited traversal. Cycles require least
closure, not one topological pass. A permanent `visited(K,f)` would lose late
marks and late graph edges. New source deltas use the current graph; graph
growth replays existing committed sources through a fresh traversal. This
simple baseline establishes both arrival orders. A more selective incremental
replay index needs its own proof before selection.

The method owner commits source batches at the method-queue-empty barrier,
as it does ordinary temporary requests. Account for pending batches, route
messages, BaseSlot commits and actual-mark replays before declaring a complete
run-1 barrier. Register child work before releasing its parent work token.
Use owner-queued messages; publication must not mutate another unit's state.
There is no extra arbitrary pending-memory cap in this contract.

### Bootstrap trace

```java
A(p) { C.f = p; B(); }
B()  { check ContainsMark(C.f, T); }
```

```text
1. A starts abstract p. The write derives W_f: p -> C.f [D].
2. Its actual input at B's call supplies B's abstract i_f.
3. B's reached check records Need(B,f,{T}); no concrete hit is emitted.
4. The reverse input binding reaches A's original p at W_f.
5. Offer ordinary BaseSlot(A,p)/T. Ordinary nonstatic propagation reaches
   the supplier of p. An actual p/T replays the write and supplies C.f/T.
6. B admits the actual field/T initial without a static request gate.
   Ordinary concrete evaluation can now satisfy the check.
```

An intermediate read-free H adds a shared relay to step 4, not an ordinary
static request store. If no actual p/T exists, step 5 produces no T and the
check remains unsatisfied. B need not return normally to emit a need or a
local hit reached before a loop/throw.

## 6. Actual concrete transport and summary publication

Keep three distinct relations:

```text
EntryBinding(K,initial)        actual method-entry assumption admitted by §2
ReaderActive(K,coordinate)    scheduled local work, not concrete evidence
AtCallee(port,original,value) ordinary value at that exact call-plan stage
```

Actual FLOW or TAINT geometry can activate abstract dependency computation.
Only supplied TAINT supplies T. Schedule concrete work where an operation
needs it; represent transparent intermediate entries symbolically. Do not
filter admission or discard summary effects because a method has no static
read opcode. Cleaners, rule actions, writes, class components and summary
exits are also effect boundaries.

Project actual static T when it arrives. There is no unmatched-static-T memo
waiting for a later static request. Keep ordinary initial deduplication and
the exact bindings required by transport. The retained nonstatic request and
concretization mechanism is unchanged.

A compact concrete field carrier reconstructs exactly:

```text
full port + field(C,f) + actual mark T + initial kind(EXACT or STAR)
  -> complete original initial and its canonical DEMAND current value
```

Encode only if decoding equals the **whole** original input, including
support and effect flags. EXACT and STAR are different tags. A set of marks
is a set of diagonal carrier members, not a Cartesian product of input and
output marks. W inputs, cross-field edges, T-to-U conversions, changed tails,
nonstatic current values and whole ND premises keep their ordinary full
binding. The first version also keeps direct class inputs on the scalar
bound path; admitting them without requests is still required.

Before replacing an operation sequence with a relay, execute/check that each
component produces exactly one unchanged full input and no interest or
observation. Unknown operations use the ordinary fallback. An absent
GETSTATIC is insufficient. A cleaner can emit an interest despite leaving
the final fact unchanged; that makes it an effect. An operation with no fact
outputs can still emit a request/source/sink event; preserve its event channel.

At a reader/effect boundary, decode and apply the ordinary matched operator
under the full port and premise. A published callee effect replays against
both existing and future matching inputs. A new input replays against both
existing and future effects. Neither delivery order may lose an application.
Whole ND matching uses all literals at the same ordinary call port, never
independent truths pooled from different calls/contexts.

### Identities and a required nonidentity case

Retain the existing pure abstract field identity compaction. A concrete exact
singleton field identity can also be elided if its whole conclusion equals
the same exact field/T at DEMAND, with Empty E, unchanged support and no
transformation/effect metadata, and the owner has the root D default.
Local observations have already been retained. A NORMAL contribution never
passes this test. Other concrete/class/ND effects remain stored.

Do **not** compact this ordinary concrete chain result:

```text
initial:    C.f.*     (T)
conclusion: C.f.[any] (T) [D]
```

For distinct suffixes a and b, it relates `C.f.a/T` to `C.f.b/T`. The root
identity preserves the suffix and cannot do that. Retain this effect even
in a read-free method that returns. Symbolic transport may produce it at
the actual ordinary exit; it cannot invent it for a method with no such exit.
That method still has its unconditional root D default.

## 7. Required edge cases

| Case | Required result |
| --- | --- |
| B reads C.f into x, then checks x/T | Static origin f creates the same fast need; actual T is still required |
| A copies C.g to C.f before B | Need on f maps to original g; a changed field is an effect |
| A's abstract p feeds C.f | Fast need terminates as BaseSlot(p)/T at that W input |
| A BaseSlot callee request maps to caller static g | Reenter the fast router at g |
| T is cleaned before a call | Conservative interest may remain; concrete replay applies the cleaner and cannot report killed T |
| C.f is definitely overwritten before a call | No old-f input/route at that call; root default cannot recreate the killed local branch |
| Read C.f into x, then overwrite C.f | Previously derived x retains its actual origin/order and can survive |
| Same callee at different calls/contexts | Separate full ports for truth and effects; only logical interest keys merge |
| ND or mark-dependent source condition | Complete ordinary premise and NNF evaluation; a need is not a satisfied literal |
| Concrete source/model creates C.f/T | Actual supported output feeds concrete transport directly; no upstream request required |
| Broad class/root actual T | Legal projections only; exact class does not imply exact field; no concrete root initial |
| Returning read-free method receives concrete field STAR | Retain STAR-to-ANY effect; root identity alone is insufficient |
| Check before a nonreturning loop | Need/actual local observation is emitted at the check, not at method exit |
| New mark or binding arrives after a route finished | Fresh traversal/replay reaches all new Base endpoints |

## 8. Reference, equality and proof obligations

Define `OrdinaryConcreteD` independently of the optimization:

```text
same legal actual supplier inventory and all nine initial families;
all actual static T admitted without a static request gate;
early/sticky static DEMAND, ordinary strong kills and Empty-E survivors;
ordinary statement/rule/call operations, root D default and whole ND support;
local needs from reached FLOW operations;
ordinary expanded reverse request propagation along each actual FLOW binding
  until a nonstatic BaseSlot boundary;
ordinary BaseSlot propagation and actual replay.
```

The expanded reference may copy a static need into intermediate logical
request states. The optimized engine retains only its source batch and
traverses the shared graph. Those virtual reference states are not optimized
storage requirements or actual TAINT.

For each full MethodKey and each finite positive closure of these rules:

```text
Compact(Summaries_bottomUp) = Compact(Summaries_ordinary)
BaseRequests_bottomUp      = BaseRequests_ordinary
ActualInitials_bottomUp    = ActualInitials_ordinary
SemanticObservations_bottomUp = SemanticObservations_ordinary
```

Summary equality includes full premises, AP payloads, layers and supports.
`Compact` removes only §6 identities whose denotation is already supplied by
the owner's default. Observations preserve report/source/sink identities,
rule occurrences, actual support and each derivation's action order.
Global scheduling, duplicates and batch-dependent diagnostic callback counts
may differ, as previously selected for `ExternalMethodRecord.callSites`.

Required constructive arguments are: legal actual supply/no fabricated mark;
layer preservation; exact carrier codec; effect/ND boundary preservation;
ordinary-to-contracted expansion/compression in both directions; original-slot
need partition; complete Base endpoints of expanded versus fast request
closure; finite-cycle termination for each routing snapshot; mark-union
distribution; both source/graph and input/effect arrival orders; valid identity
compaction and the STAR-to-ANY counterexample; ordinary persistence/handoff;
and a declared complexity family. The [dossier](run1-bottom-up-concrete-static-proofs.md)
maps these to checked theorems and names the remaining runtime adapters.

The Lean models check scalar AP and finite route/transport fragments. The
frontend must still prove/test that actual FLOW bindings, local interest
contributors and packed summary application refine these definitions. Do not
claim whole-Kotlin/JVM soundness or analyzer termination from graph-path
equivalence. Once those adapters hold, witness expansion/compression composes
with the ordinary concept's soundness within the stated AP scope.

## 9. Resource effect and validation gate

The target redundancy is static request membership and per-mark relay work.
With h transparent relays and m concrete carrier members, the checked layout
uses `(h+1)*m` ordinary slots versus `h+2*m` shared-route/endpoint slots.
Strict improvement is proved for h at least 3 and m at least 2. A source batch
can traverse a static route without storing its marks in every intermediate
method. A second checked layout counts h*m expanded static need rows versus
m+h source-mark/shared-route slots, with strict improvement at the same
thresholds. Required Base endpoints still pay ordinary mark admission cost.

Snapshot routing can cost O(V+E) visits per source batch plus endpoint mark
delivery. A shared route costs O(E); source records retain their local marks.
The correctness-first replay baseline may revisit every source on graph growth.
No universal reduction, byte saving or 60-second guarantee follows. Measure
route growth, source marks, endpoint offers, repeated replay and concrete
effect work before selecting a more complex incremental route cache.

Before runtime acceptance: implement adapters using the checked model as the
oracle; test §7 plus concrete-before-need and need-before-concrete orders;
retest OWASP and the configured real projects; compare accepted semantic
findings with the baseline; capture profiles and OOM heaps as required by
task.md. Always include latest reached/completed iterations in reports.

Latest existing measurements use runtime `ab6c3cd00`: OWASP reached **run-7**
(completed run-6); conductor **run-2** (completed run-1); ThingsBoard **run-1**
(none completed); Stirling-PDF **run-2** (completed run-1). These are **not**
measurements of this proposal. See the
[project report](../docs/reports/bidirectional-run1-bottom-up-static-20261017.md)
and the [new proof validation](../docs/reports/bidirectional-run1-bottom-up-concrete-static-proof-validation-20261017.md).
