# Run-1 bottom-up static FLOW propagation

Date: 17 October 2026. Status: proposed specification; no runtime change.

This proposal replaces the static FLOW transport in the
[current run-1 contract](run1-field-writes.md). It also replaces the earlier
normal-exit gate and virtual-availability approximations. The
[next-iteration proposal](../docs/design/bidirectional-next-iteration-after-selective-preservation-20261016.md)
uses this contract. IR storage and reference/cache manager changes are outside
scope.

The [constructive proof dossier](run1-bottom-up-static-proofs.md) gives the
formal model, executable witnesses, theorem map, validation and exact limits.
Use [the proof guide](proof-guide.md) for every change to this contract.

## 0. Definitions and foundation boundary

This file specifies the complete transport protocol. Its AP foundation is the
scalar path calculus in [ap.md](ap.md): prefix-related paths, exact/abstract/
uncorrelated tails, concrete marks, FLOW mark exclusions mx and field exclusions
E. The model uses `PFact` and the executable micro-edge/summary operations in
`lean/ApSpec/Basic.lean` and `Core.lean`. For this feature, the layer-independent
wrapper in `Run1DefaultSummaryApplication.lean` and the selected overwrite
operator in `Run1StrongPreservation.lean` override historical demand-to-ANY and
blanket weak-write rules. They are declared primitive definitions, not a
reference to an unspecified previous optimization.

A full MethodKey K contains method, entry and context. A call port additionally
contains caller K, call statement, full target K, call-plan stage and caller
layer. An input contains that port, the complete original premise and current
AP payload/layer. Origins are ZERO, a singleton abstract initial, or a complete
concrete/ND premise. Never identify a port by method or field alone.

Every local primitive returns zero or more full-payload results plus explicit
request/observation events. Keep these events when a result is unchanged or
killed. A source/rule action is not an implicit identity operation. Requests,
sources, sinks, call boundaries and normal/exceptional exits are effect
boundaries; a transparent segment cannot skip them.

## 1. Selected contract

1. Run-1 has **mark requests only**. There is no static field-request relation,
   field-request storage, field-request propagation or field-request gate.
2. Every reached method stores the ordinary default `S.* -> S.* [DEMAND]`.
   Do not test normal-exit reachability or export an exit-reachability bit.
3. All static-related run-1 FLOW is DEMAND. Once DEMAND, a derivation stays
   DEMAND when its current value moves to a nonstatic base.
4. Compute static reader effects bottom-up, with the same input binding,
   statement order and AP operations as ordinary propagation. Activation
   schedules analysis; it cannot supply a missing call input.
5. Emit the same nonidentity summaries as ordinary propagation with that
   DEMAND policy. Remove only redundant pure field identities already
   represented by the root default. No new overwrite/control approximation
   is permitted.
6. At a complete run-1 barrier, submit all summaries to ordinary NORMAL
   persistence, just as in later runs. There is no run-1 quarantine,
   static-specific NORMAL filter or exit check.

The earlier draft incorrectly allowed an incoming static FLOW to survive a
local overwrite, and allowed activation to recreate it at later ZERO-reached
calls. Both rules are removed. The target is equivalent computation, not an
additional DEMAND overapproximation.

### Current versus proposed

| Topic | Measured runtime `28a123f0e` | Proposed |
| --- | --- | --- |
| Requests | Field-read plan F gates abstract static initials; mark requests use B/dynamic R | Only mark requests; local read locations are analysis metadata |
| Inherited static needs | Start field analyses in intermediate methods and retain caller inputs | Activate local readers; relay their effects through exact call-input bindings |
| Pure field identity | Can be computed and retained per field | Shared root default represents it |
| Static FLOW layer | NORMAL or DEMAND | Always DEMAND, including static-to-local descendants |
| Local definite overwrite | Kills a fully overwritten old component | Same kill; DEMAND does not revive it |
| Root default | Always present for a reached method; W selects its layer | Always present, always DEMAND; no exit check |
| Run-1 NORMAL summaries | Driver skips persistence | Ordinary persistence, without a run-index filter |

## 2. Exact reference and equality target

Let `OrdinaryD` be the ordinary run-1 procedure with the selected policies:

```text
ordinary statement/rule/AP and call-plan operators;
ordinary strong kills and the Empty-E field-write rules;
unconditional ordinary S.* -> S.* [DEMAND] default in each reached method;
mark requests only;
static-related FLOW demoted before processing/admission;
ordinary concrete TAINT, source/sink conditions, ND and request answers.
```

Reference admission is exhaustive and independent of local reader placement:

```text
abstract i_f: every legal field geometrically supplied by the actual input;
             no F, local-read or mark-request membership test;
concrete f/T: actual supplied T plus the matching mark request;
direct class components: the existing separate actual-supplier projector.
```

The reference can enumerate those initials and use ordinary per-input
subscriptions. It need not use the optimization's local reader gate. This
is a semantic oracle, not a proposed runtime strategy. Both procedures use
the same supplied-input domain and mark-request rules; the optimization
represents nonreader abstract entries symbolically rather than dropping them.

Define `Compact` on a method's summary relation: retain its root default and
all effects, but remove an unchanged singleton static FLOW identity already
represented by that default. Compare normalized AP relations, not the number
or insertion order of packed rows.

The executable compaction test is on one normalized summary leaf:

```text
premise = singleton abstract static field i_f
conclusion = FLOW at exactly i_f's base/path/STAR/EmptyE/* payload
conclusion layer = DEMAND
the owner's universal root default is present
```

All other rows are retained, including changed mark/tail/geometry, concrete
NORMAL identity and whole ND rows. A root default is a separate summary
constructor, not an admitted initial. Normalize duplicate/empty annotation
representations before comparison; compaction is not a field-name-only test.
Its denotation is the union with the already present root default. On a broad
supplier, root and field identities can produce different literal AP lists;
the root still supplies the field identity's transfer. Do not mistake those
lists for an equality proof of one field application.

For every reached full MethodKey K, the required result is:

```text
Compact(Summaries_BottomUp(K)) = Compact(Summaries_OrdinaryD(K))
```

Equality includes the complete premise, method entry/context, current base,
path, tail, marks/mx, field E, layer and whole ND support. Local source/sink
observations and the final mark-request/answer relations must also agree.
The optimization may change work order, duplicates and physical ownership.
It may not add or remove nonidentity transfer behavior.

Observation equality is equality of semantic identities: report/source/sink
identities, rule occurrence IDs and their actual premise/value support.
Preserve each derivation's before/after action ordering. Callback multiplicity,
global delivery order and batch-dependent diagnostic counts may vary. In
particular, `ExternalMethodRecord.callSites` counts growth callbacks, so one
packed two-fact update and two single-fact updates may count as 1 and 2.
This diagnostic difference is allowed by the selected contract; it must not
change the semantic external-method observations or findings.

The `Compact` qualification is necessary only because ordinary propagation
can materialize `C.f -> C.f` beside `S -> S`. If both implementations use the
same identity suppression, their normalized stored summary relations must
match directly.

**Demotion timing matters.** Compare two executions with the same static
DEMAND policy, not an old NORMAL execution with its final rows relabeled.
For example, `Guard.MemoryEffect.admits(identity, layer)` reads the current
layer in the existing call pipeline. Both procedures must use the same
layer at that guard. A layer change alone must not cause an AP tail rewrite.
Any further semantic change needs a separate proposal and oracle.

## 3. Domain and effect classification

Run-1 is forward, with field limit exactly **1**. Class accessors do not
count against the limit. S differs from ZERO. Preserve the closed legal
coordinate domain and existing initial families: no abstract S-root/class
initial, no concrete empty S-root initial with any tail, and no ANY/ANY_TAINT
run-1 initial. The supplied-input domain still excludes a tainted exact empty
S-root; actual broad nonexact static TAINT is legal supplied data.
Nonexact concrete field/class answers remain STAR initials, not `[any]`.

The following nine families are exhaustive; all initial E and mx are Empty.
`b` is a nonstatic/nonzero base, `C` a class accessor, and `T` a concrete mark.

| Family | Base/path | Initial tail/mark | Start | Admission |
| --- | --- | --- | --- | --- |
| ZERO | ZERO / [] | EXACT / zero mark | REACH, NORMAL | Actual root/reach entry |
| Nonstatic abstract | b / [] | STAR / * | FLOW, NORMAL | Actual supplied base geometry |
| Static abstract | S / [C,f] | STAR / * | FLOW, DEMAND | Actual supplied field geometry; symbolic in a nonreader |
| Nonstatic concrete exact | b / [] | EXACT / T | TAINT, NORMAL | Actual exact root T and mark request |
| Nonstatic concrete chain | b / [] | STAR / T | TAINT ANY, DEMAND | Actual nonexact/descendant T and mark request |
| Static concrete exact | S / [C,f] | EXACT / T | TAINT, NORMAL | Actual exact field T and mark request |
| Static concrete chain | S / [C,f] | STAR / T | TAINT ANY, DEMAND | Actual nonexact/ancestor T and mark request |
| Direct class exact | S / [C] | EXACT / T | TAINT, NORMAL | Actual exact class point; independent of field requests |
| Direct class chain | S / [C] | STAR / T | TAINT ANY, DEMAND | Actual broad supply at a selected structural class point |

For a static slot [C,f], a leaf supplies its geometry if its own path is at
or below that slot, or its nonexact path is an ancestor of it. Wrong bases,
classes and fields do not match. An exact root/class ancestor supplies no
deeper field. Nonstatic projection requires the same base and produces its
empty-path initial. For concrete answers, retain actual T, require its mark
request, and use EXACT only for an exact supplied point at the initial path;
otherwise use STAR. Incoming ANY/must is supplied data, never a new initial
tail. No mark is obtained from FLOW.

Direct exact class points bypass normal-slot mark requests. Broad root/class
TAINT uses the structurally selected class-point inventory, projects every
actual mark at that point and uses STAR. It creates no root/class request
slot. That inventory contains actual code/rule/model class-point consumers
and effects closed over resolved callees, independently of field reads.
The same projector and selected inventory are used by both procedures.

For one static field:

```text
i_f = (S, [C,f], STAR, EmptyE, *)
R_f(K) = nonidentity summary effects whose singleton abstract premise is i_f
W_f(K,p) = effects at point p that produce C.f from a different input
```

A cross-field edge `f -> g` is both R_f and W_g. These are candidate
classifications of one stored effect. Do not duplicate the cumulative summary
relation. W includes copies, aliases, modeled/rule outputs and returned callee
values, not only STORE opcodes.

Compare full semantic transfers when deciding identity. Equal field names
are insufficient: a changed mx, tail, mark or geometry is an effect. A
same-origin input with such a change uses the bound-input path even if it
is not spatially off-diagonal. In particular, field-local mark conversion
must not disappear as an identity. Concrete and multi-premise effects retain
their ordinary handling.

A pure FLOW identity is elided only after its operation effects and immediate
mark requests have been processed. The root default preserves the supplied
payload at DEMAND. Do not elide a concrete NORMAL identity: its layer is not
represented by the DEMAND default.

### Static FLOW layer

```kotlin
// Specification pseudocode: the current value is FLOW, not concrete TAINT.
fun staticFlowLayer(origin: PremiseKey, current: FlowTree): Layer =
    if (origin.hasStaticAbstractMember() || current.base == STATIC)
        DEMAND
    else current.layer
```

Apply the policy at static start and before work, inputs, summaries and
publication. Ordinary layer joins keep a nonstatic-origin FLOW DEMAND after
it passes through a static field. Keep base/path/tail/mx and Empty field E;
DEMAND itself does not mean `[any]`.

Independent TAINT/ZERO source derivations retain their own support/layer.
A result that depends on DEMAND cannot become NORMAL by changing its premise
key or current base. Full ND support and source/sink NNF stay intact.

## 4. Prescan and mark requests

Prescan already resolves the entire call graph. Use that graph; do not resolve
calls again. Collect local static consumers, mark observations, effects and
legal coordinates. Local value-read locations tell the engine where a reader
can do useful work. They are **not field requests** and are not inserted into
RequestStorage or closed into a field-request admission gate.

```text
x = C.f                -> local value-read location
C.f = x                -> local write/effect; no field request
ContainsMark(C.f,T)     -> static mark request f/T; no field request
source condition on f  -> same mark-interest collection as a sink condition
clean/convert marks    -> actual mark interests/effects at their source
```

Use local read indices and the resolved graph to find/activate readers.
Traversal can be lazy and can share call-graph components. Do not materialize
`every method x every reachable reader x every field`. No transitive F
membership is required to admit an initial or request. Any optional graph
pruning metadata needs an exact completeness proof, not a new request role.

Only the mark relation remains:

```text
MarkRequests(K, originalSlot) = frozen static marks B + dynamic marks
Temporary(K, originalSlot)    = merged marks pending commit
```

The existing two slot kinds remain: a nonstatic BaseSlot and a StaticSlot
for one class/field. Requests contain neither field E nor mark exclusion mx.
Keep mark-set union, original-slot routing and the method-queue-empty barrier:

```kotlin
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Frozen static marks come from mark checks/cleaners/mark-sensitive operations,
not from a field-read request set. Keep wide/class scopes symbolic and resolve
against legal supplied/consumed slots without a field-by-mark expansion.
The logical frozen lookup must include every statically known matching mark
interest; it cannot depend on membership of a removed F plan. Borrow B once;
do not copy it into temporary/dynamic storage or send a startup wave.

Precisely, a local frozen contributor is `(scope,T)`, where a static scope is
all fields, one class's fields, or one class/field. Let `path(M,N)` be a finite
path in the resolved call graph, including the empty path. Then:

```text
B(M, StaticSlot(C,f), T) iff
  exists N, scope, path(M,N) and localMarkNeed(N,scope,T) and scope matches (C,f)
B(M, BaseSlot(b), T) = false
```

This is a logical lookup, not materialized rows for every legal field. A
membership witness contains the call path and local contributor. A root/class
scope can be tested when a legal actual slot is supplied/consumed. Direct
class-point needs stay separate. For an edge M->N, prepend that edge to prove
`B(N,s) subset B(M,s)`. This law suppresses only unchanged-origin offers;
it says nothing about a request whose current field differs from its origin.

Request matching ignores incoming field E and FLOW mx. Actual summary and
condition evaluation retain their ordinary AP semantics. A request is an
interest, not evidence that T exists or that an NNF condition is true.

## 5. Method initials, activation and flow-sensitive call inputs

Distinguish three relations:

```text
EntryBinding(K,i_f)       // f is an actual method-entry abstract assumption
ReaderActive(K,f)         // canonical local reader computation is scheduled
AtCallee(K,c,N,pi,a)      // ordinary propagation delivers a under pi to
                         // N's callee stage at call c
```

An EntryBinding is fixed at method entry. A relay may represent it symbolically
without emitting an ordinary `addInitial(i_f)` work seed. This does not move
the value to later statements or change its origin. A reader summary remains
a dependency on that particular method initial, never on an application-wide
static pool.

Activation only schedules hypothetical reader computation. Actual incoming
FLOW **or TAINT** geometry can activate a reader before concrete mark filtering.
A source f/T must be able to activate a getter's abstract f-to-return dependency
so a later return/T check can request the real f/T. Activation itself proves
no concrete mark and applies no summary.

Entry admission is the same as in the ordinary reference:

```text
AtCallee(K,c,N,pi,a) AND ordinaryFieldProjection(a,f) exists
    => EntryBinding(N,i_f)
ReaderActive(N,f) AND EntryBinding(N,i_f) AND LocalValueRead(N,f)
    => start canonical i_f reader work at DEMAND
```

The field projection has no F/request-membership test. It still checks actual
supplied geometry: an exact ancestor does not supply a deeper field. Root
inputs establish their ordinary entry bindings directly.

A transitive activation must not publish a hypothetical callee summary for
an entry that the ordinary reference would never admit. For example, an
upstream local kill can prevent that callee's f entry altogether. Collapse
activation across transparent relays; at a nontransparent prefix, wait for
its exact input transfer. This preserves equality of the callee's own summary
relation as well as equality of effects applied in its callers. No separate
speculative reader-result cache is introduced.

On activation, traverse resolved/reached callee relations and start ordinary
abstract field work only at local value readers. Intermediate methods relay
effects and maintain the admitted symbolic entry binding. Local field
operations and call rules still need their ordinary input/effects even if the method has no
GETSTATIC. They can require local transfer work without a full reader seed.

### Call-stage eligibility

The application rule is exact:

```text
AtCallee(K,c,N,pi,a)
    AND (i_f -> b) in Summaries(N)
    AND ordinaryMatch(a,i_f)
 => ordinaryApply(a,i_f,b), with original caller premise pi
    followed by the ordinary continuation of c's callee stage
```

`AtCallee` is the ordinary CFG result **after** before-callee bindings,
cleaners, sources and guards. It can be represented by existing caller-owned
state or an equivalent compact identity descriptor. It must not be inferred
from ZERO reachability or EntryBinding alone in a method that changes f.

For example:

```java
// Analyze M with the initial i_f.
Object M() {
    C.f = constant;
    return readF();       // readF returns C.f
}
```

The local overwrite kills M's old f before this call. Neither procedure
may derive `i_f -> return` from that killed input. M's stored `S -> S [DEMAND]`
is a separate default summary, not a local CFG bypass. Changing the result
layer to DEMAND would not make an extra return edge equivalent.

Conversely, `x = readF(); C.f = constant; return x` can retain the earlier
f-to-x dependency. A later write cannot retroactively supply or kill that
local x. This preserves flow sensitivity and explains why the method-entry
origin and the call-stage input are both required.

## 6. Avoiding carriers without losing their reachability

### Transparent relay methods

A method is transparent for a canonical field lane when every relevant local
operation and call prefix/suffix preserves that lane, with no omitted rule,
mark or observation effect. Prove this from the actual local forms/models.
Unknown effects are not transparent. Transparency can be field-specific:
a write to disjoint g can preserve f.

Every callee already supplies `S -> S [DEMAND]`. Thus a transparent relay's
unchanged f crosses calls through the same ordinary default as in the reference.
The identity lane can be reconstructed from its entry binding and ordinary
control reachability. It needs no field initial, per-CFG-node FLOW fact or
retained ordinary inherited-field input subscription.

This is the fast path targeted by the optimization. Use one field-independent
call relation per reached call/target/context. Callee R_f growth wakes that
relation; the relay checks the corresponding entry/input binding before
application. Late entry binding replays already stored R_f effects.

### Methods that change the field lane

When transparency is not established, preserve the exact reference transfer
to each relevant call/consumer. One possible logical representation is:

```text
IdentityInput(K,i_f,call,target,stage)
AtExit(K,originalPremise,currentValue)
```

IdentityInput is valid only if ordinary propagation delivers the canonical
f/STAR, EmptyE/empty-mx/DEMAND input there. It is a reachability descriptor,
**not** a field request or a new actual supplier. An implementation may
reconstruct it from an exact shared transfer graph instead of storing all
descriptors. AtExit carries the full ordinary current value and original
support, including noncanonical results.

Use ordinary primitive transfer rules:

```text
local definite f overwrite -> kill the old canonical lane
local disjoint g overwrite -> preserve it
f = f                     -> include the RHS regeneration as well as the kill
changed mx/tail/path       -> retain a full noncanonical bound input
independent source/write  -> keep its own original support and statement
```

A method-wide may-write bit cannot decide this relation. A Boolean token
cannot stand for a changed full AP payload. Do not blindly preserve a lane
because it is DEMAND, or regenerate it at a ZERO-reached call.

Exact transfer must cover local consumers and method exits as well as calls.
For example, a method that only cleans f/T has no value read and no callee.
Its admitted symbolic i_f still runs the ordinary clean transfer, emits the
same original-slot request, and reaches the ordinary normal exit with the
same nonidentity `i_f -> f except T [DEMAND]` result. Publish that effect from
AtExit after the ordinary exit actions. Do not publish a local intermediate
effect that never reaches a summary exit; local observations/requests remain
possible before an exceptional exit. The universal root default is inserted
independently and requires no AtExit witness.

The physical choice between existing state, a shared transfer graph and
compact descriptors remains an implementation design bridge. Whichever is
chosen must decode to exactly the reference input relation. Do not claim
that all logical call-by-field reachability has been removed.

For the formal executable reference, a local instruction has a full-result
evaluator: input -> list of (output, request/observation events). Pass, selected
overwrite plus pre-store values, matched mark clean, ordinary scalar AP
effect, root-default application, observable action and unknown fallback are
separate constructors. An instruction is certified transparent only when its
evaluated full result is exactly one unchanged input with no event. Unknown
operations cannot be certified. Sequence composition then derives the relay
law. The implementation must refine these instructions; it cannot assume
the final relay/input equality as its proof premise.

The scalar transport normalizes static FLOW before primitive evaluation and
again before output admission. Pre-store values and their observation supports
are already evaluated under the same policy with their own original/layer.
Opaque ordinary fallbacks and ND clients must join dependent layers correctly;
an admission check on the current base cannot reconstruct lost static history.
Independent source/RHS derivations retain their own support and layer.

### Derived W and ordinary inputs

A derived `a -> C.f` value keeps a's actual original premise and its program
point. Retain its ordinary bound input at each call it actually reaches,
subscribe to matching R_f/default/request responses, then use ordinary AP
application. Do the same for transformed same-origin static inputs and
concrete TAINT. Ordinary nonstatic call inputs also retain their existing
path, even when their original premise is static.

No W notification creates an entry supply in its own caller, an earlier
call, or a sibling. A callee can share reader computation among these
bindings; it cannot merge their actual supplies or premise support.

## 7. Bottom-up effects and the universal default

Store each reader effect once in the ordinary summary storage. Use batched
fresh publications over reverse reached call relations. For an unchanged
inherited field, use its exact identity input descriptor instead of retaining
an ordinary full input subscription. Derived W/transformed/TAINT inputs keep
their ordinary subscriptions.

```kotlin
// Logical pseudocode. Inputs are after ordinary before-callee stages.
fun onReaderEffect(port: CallPort, effect: Publication) {
    for (input in exactIdentityInputsMatching(port, effect.premise)) {
        applyOrdinarySummaryThenResume(port, input, effect)
    }
    // Full bound inputs use their ordinary subscription path.
}
```

Before-callee work is admitted when its real input reaches the stage. It
must run even if no reader effect arrives. Response replay uses the prepared
input; it must not repeat source-hit/cleaner side effects for every delta.
After application, resume at the callee stage's output and run rewriters,
bind-back, aliases, end actions, cuts and successor processing as usual.
Do not enqueue a raw returned AP directly after the call.

### Every method has the default

```kotlin
// Once, when the ordinary reached method owner starts.
publishOrdinarySummary(staticRootIdentity, layer = DEMAND)
```

There is no exit predicate and no special pass-only-call case. A method with
no R_f effects still supplies this default. It applies to every actual static
input that reaches its call, preserving field, tail and mx at DEMAND.

An input that arrives before owner/default publication is covered by ordinary
register-before-replay ordering. That scheduling order is not semantic absence
of the default. Shared call relations observe it without a per-field result
cache. A local reader's existing static input can continue through an empty
helper with the ordinary default, exactly as in OrdinaryD. A pure relay need
not materialize the resulting field identity.

The default is present even for a nonreturning method. This is the selected
run-1 DEMAND policy in **both** procedures; bottom-up propagation must add no
further control approximation. No S-root initial is emitted.

### Trace through relay methods

```java
Object A() { Object x = B(); return x; }
Object B() { Object y = Reader(); return y; }
Object Reader() { return C.f; }
```

For A's actual entry f, with transparent A/B call prefixes:

```text
activate Reader; retain A/B's symbolic entry/input bindings;
Reader starts i_f [DEMAND] and derives i_f -> return.* [DEMAND];
B applies it at its actual Reader call: i_f -> y.* [DEMAND];
B propagates nonstatic y and publishes i_f -> return.* [DEMAND];
A applies B's effect: i_f -> x.* [DEMAND], then its own return.
```

A/B need no ordinary f reader seed, inherited-field subscription or computed
f identity summary. Their nonstatic results still use ordinary propagation.
If a local operation changes f before a call, the transparent shortcut is
unavailable and §6 supplies the exact input or absence of input.

## 8. Mark requests, concrete answers and full conditions

Removed inherited input subscriptions used to carry request replay too.
Replace that replay over the same exact identity inputs:

```text
new exact identity input -> join current reader effects AND mark requests
new mark-request delta   -> visit matching identity descriptors and bound inputs
new W/TAINT input        -> ordinary current-request join
```

An unchanged static original uses the caller's original f slot. A changed
origin uses the full bound input:

```java
// A starts with i_g.
C.f = C.g;
B();                     // B checks ContainsMark(C.f,T)
```

B's f/T need maps to **A's original g/T**. A late actual g/T answer traverses
the copy and the real call stages. A nonstatic supplier maps to its original
BaseSlot. A killed FLOW branch sends no later request from that branch.
Concrete origins answer actual marks and skip upstream request propagation.

Unchanged frozen B membership is already inherited through the resolved
call graph; skip its duplicate upstream offer after validating the origin.
Changed origins and dynamic requests use ordinary merged temporary/commit
handling. Keep requests after one answer for later suppliers. No second
supplier history or request-by-input matrix is introduced.

Actual TAINT keeps exact caller input, strong kills, cleaners and current/late
request answer handling. Never infer T from activation, an identity descriptor
or a FLOW summary. No spatially off-diagonal W is needed to bootstrap an
actual source f/T or a concrete f-to-f input subscription.

A mark-only method need not start an abstract value reader, but its source
and sink conditions still evaluate actual requested TAINT. Keep complete NNF,
occurrence IDs and ND support. `f/T AND g/U -> h/V` stays a whole same-call
premise, not independent per-field implications. Missing lazy state proves
no negative condition. Pure identity suppression cannot remove operation
requests, mark conversions or source/sink observations.

## 9. Arrival orders, recursion and completion

Every incremental join handles both arrival orders:

```text
entry binding / local consumer / reached call relation
reader activation / admitted callee entry binding
exact identity input / stored default or reader effect
bound W or TAINT input / stored summary
identity or bound input / effective mark-request row
requested mark / actual concrete supplier
```

Register before current-state replay; store fresh state before notifying
existing consumers. A later input replays stored effects. A later effect
visits already admitted inputs. ZERO discovery alone never becomes the
second side of a fact-input join.

Merge activation fields/publications per destination owner and send counted
work. Use fresh deltas and ordinary method-queue-empty barriers. Recursive
call components compute a monotone least fixed point, not one topological
pass. An empty current R_f set is not a completed no-response result.

All local/remote activation, input transfer, publication, request commit and
child delivery count before quiescence. Keep owner/lock boundaries and
cancellation behavior. No new scheduler, pending-memory threshold or
per-publication seen history is required. Finite fair processing proves a
fixed point, not a 60-second bound.

The work-accounting invariant is `reserved = queued + active`. Reserve child
work before making it visible or releasing its active parent. Completion
requires zero reserved work, flushed temporary request rows and no uncounted
delivery. The finite fixed-point claim concerns normalized semantic facts,
not an ever-growing trace of repeated loop visits. Proof histories are ghost
certificates; they are not new retained runtime collections.

## 10. Persistence and later runs

At a complete joined run-1 barrier, remove the driver-wide first-run skip:

```kotlin
shared.records.persist(
    config.direction,
    result.analyzers.asSequence().map { it.key to it.summaries },
)
```

The persistence policy is the same as for every other run: keep NORMAL
summaries through the ordinary writer. There is no static/first-run/exit
filter. Existing record-format admission and native/reverse guards still
apply; do not flatten an ND row or add an unrelated new record format.
Static-related FLOW and the universal default are DEMAND and do not become
NORMAL records. Independently supported NORMAL concrete/static or nonstatic
summaries are not discarded because their run index is 1.

Preserve complete raw summary access, ordinary record reader semantics,
current-query admission, and the existing report/stop rules. Persist no
failed/OOM/timeout prefix as a successfully completed run.

DEMAND handoff includes relay-only owners and their original premise support.
Keep executable AP payloads unchanged. The existing typed forward-to-backward
demand projection can map STAR to ANY; this does not create an ANY run-1
initial. Later runs use their ordinary actual demand and strong semantics.
No special exit check or static return-evidence store is added.

## 11. Required proofs and differential controls

The [proof dossier](run1-bottom-up-static-proofs.md) discharges these obligations
for the declared scalar-instruction/finite-derivation model. Its theorem map
and [validation receipt](../docs/reports/bidirectional-run1-bottom-up-static-proof-validation-20261017.md)
state the checked scope. Frontend/packed/scheduler/driver refinement remains
required before claiming correctness of an implementation.

1. **Domain and constructor closure.** Removing F changes no legal input,
   mark-request origin or initial tail. Activation never supplies a concrete
   mark. Local reader seeds and symbolic relay premises preserve full K.
2. **Identity-lane equality.** Decode every transparent shortcut/descriptor
   to the exact OrdinaryD input at each relevant CFG node and call stage.
   Induct over ordinary preservation plus RHS regeneration. No input exists
   after a full kill without an independent real derivation.
3. **Transparent relay lemma.** Derive the shortcut from actual local forms,
   complete alias/model effects and universal callee defaults. Do not assume
   an absent read or may-write bit proves arbitrary prefix transparency.
4. **Effect equality, both directions.** Each bottom-up application has the
   same reference input, original premise, summary and continuation. Each
   ordinary application is represented by an identity descriptor or full
   bound input. Prove both inclusion directions, not just true-flow coverage.
5. **Identity compaction.** On a canonical field lane the root default returns
   that exact payload at DEMAND. For an arbitrary broad supplier, the field
   identity's denotation is contained in the already present root default;
   unioning with that default preserves the complete relation. Literal AP
   result lists need not match. Same-position transforms, NORMAL TAINT and
   ND do not satisfy the compaction predicate.
6. **Mark/observation equality.** The new input representation yields the same
   final requests and actual answers, original-slot routing, full NNF/ND joins
   and source/sink observations. Preserve before/after action ordering.
7. **Layer and guard consistency.** Both procedures apply static demotion
   at the same points, keep it sticky and evaluate every layer-sensitive
   guard identically. No AP information changes merely due to demotion.
8. **Incremental fixed-point equality.** Input-first, effect-first and
   request-first executions converge to the same normalized relation,
   including recursion, multiple contexts and counted completion.
9. **Persistence and handoff.** Ordinary NORMAL reuse is unaffected by the
   choice of transport; DEMAND static FLOW stays out. Every relay dependency
   reaches ordinary typed handoff and later actual-query analysis.

Use an independent ordinary propagator as the oracle. Compare complete
normalized summary leaves, final request/answer relations and observation
identities; comparing only vulnerability counts or a few positive examples
cannot establish equality.

Required controls include:

* Transparent A -> B -> Reader: only Reader starts field reader work; A/B
  relay the same nonidentity effects as OrdinaryD.
* No local read but a definite f write before a reader call: no old-f return
  dependency; disjoint g write preserves f; f=f can regenerate f.
* No value read/call but a selected mark cleaner: exact symbolic transfer to
  exit publishes the same nonidentity mx result and original-slot request.
* Read before write versus write before read, branches, loops and repeated
  calls: no retroactive effects and exact call-stage eligibility.
* Empty, always-throwing and nonreturning callees: all have the same stored
  DEMAND root default in both procedures; no exit bit or conditional default.
* Source f/T before any mark request, followed by a getter and return/T
  check: activation bootstraps the hypothetical reader and real answer.
* f->g->h and nonstatic->f->local: full origin and sticky DEMAND survive;
  dynamic T requests map back to the actual original slot.
* Same-field mx/mark/tail transformation: retain full bound effects, not a
  Boolean unchanged input; emit the same immediate requests.
* Source/sink mark-only conditions, contradictory/negative NNF and full ND:
  no field requests and no lost concrete evaluation or premise support.
* Relevant call prefix/suffix cleaners, rewriters, guards, aliases and cuts,
  including a guard that selects different behavior for NORMAL/DEMAND.
* Every late-input/effect/request order, relay-only owners, native/broad/class
  inputs, unknown effects and recursive SCCs with multiple entry contexts.
* Ordinary NORMAL persistence and reverse/native readers; no static FLOW
  NORMAL row; unchanged demand projection and later positive limits.

The compact nontransparent-input adapter and packed call/AP decomposition
remain implementation design bridges. They must be specified and checked
against these equations before runtime changes. They cannot be replaced by
an extra DEMAND approximation to make the implementation simpler.

## 12. Expected improvement and measurement limits

Latest measurements are for runtime `28a123f0e`, before this proposal:

| Project | Latest reached / completed / accepted report | Run-1 engine seconds |
| --- | --- | ---: |
| OWASP | run-7 / run-6 / run-5; timeout | 263.421, complete |
| conductor | run-2 / run-1 / run-1; OOM in run-2 | 49.567, complete |
| ThingsBoard | run-1 / none / none; OOM | 176.913, incomplete |
| Stirling-PDF | run-2 / run-1 / run-1; OOM in run-2 | 36.586, complete |

The failed ThingsBoard timer includes 37.94 seconds of heap capture. See the
[closed report](../docs/reports/bidirectional-run1-selective-preservation-20261016.md)
for configuration, failures, strict finding identities and engine/barrier scope.
No measurement of this proposal exists.

The old field-request metadata shows the inherited workload to replace:

| Project | Local method/field read memberships | Transitive memberships |
| --- | ---: | ---: |
| OWASP | 2,148 | 6,255 |
| conductor | 6,192 | 1,298,007 |
| ThingsBoard | 33,240 | 15,706,500 |
| Stirling-PDF | 19,874 | 40,182 |

These are historical CommonMethod metadata counts, not runtime contexts,
initials, transfer counts or bytes. The source is `Bidi static catalog setup`
(`directAbstractions`, `transitiveAbstractions`) in
`selected-current/projects/{OWASP,conductor,thingsboard,Stirling-PDF}/bidi/analyzer.log`
under `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-selective-preservation-preparation/verification-20261016T202647.993220Z`.
The new design does not retain this transitive relation as a field-request gate.

ThingsBoard's run-1 heap has 372,664 static abstract initial memberships
(69.57% of 535,668), and 3,797,487 caller-input groups with static abstract
origins (80.66% of 4,707,790). Currently static FLOW accounts for 3,767,181
groups (80.02%). These overlap and are not saving estimates. There are also
45,964 currently nonstatic FLOW groups with static abstract origins; their
nonidentity value propagation must remain.

The caller-store union retains about 2.323 GB, including 1.443 GB of indices.
The conclusion union retains about 2.391 GB. Those owner views overlap.
Conductor/PDF heaps are from run-2 and OWASP has no corresponding run-1 heap;
do not apply ThingsBoard's percentages to those projects.

Expected benefit, in order:

1. Remove inherited full static FLOW input groups/index postings where a
   transparent identity binding suffices; retain real W/transformed inputs.
2. Avoid field reader seeds and CFG carrier work in intermediate transparent
   methods. Compute reader effects locally and compose them bottom-up.
3. Remove redundant field identity summaries, publications and joins.
4. Remove the transitive static field-request plan/gate and its maintenance.
   Local read indices and mark requests still have their own costs.

The local-versus-inherited initial split, transparent-versus-nontransparent
call split and resulting net saving are not measured. Count compact reachability
state, reverse fanout, reader summaries, bound W inputs and later refinement
as costs. No memory percentage or 60-second prediction follows from the old
metadata ratio. Equality forbids improving run-1 time by dropping flow-sensitive
input distinctions or adding extra DEMAND summaries.

Use the same fixed project configurations and strict main comparison. Record
latest reached, completed and accepted iterations for every outcome. Measure
run-1 engine time separately from prescan/setup/report barriers, and compare
full normalized relations on completed finite differential controls first.
This update adds executable Lean proofs and controls, recorded in the
[validation report](../docs/reports/bidirectional-run1-bottom-up-static-proof-validation-20261017.md).
It changes no analyzer implementation and adds no analyzer run or benchmark.
