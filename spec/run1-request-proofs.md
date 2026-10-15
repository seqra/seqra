# Run-1 request proof dossier

This document proves the semantic laws used by the selected
[run-1 request design](../docs/design/bidirectional-run1-mark-request-redesign-20261015.md).
The normative construction rules are in [run1-requests.md](run1-requests.md).
The measured runtime remains `27e22bb1ed871651143538783473cb0d4fcbcf3e`.
That runtime does not implement this design.

The proofs below concern a finite, accepted program and its request/initial and
component-summary semantics. They do not prove a JVM implementation, a physical
set layout, a 60-second bound, or exact confirmation of pooled ND candidates.
Request membership is not evidence that a source or sink predicate holds. The
existing occurrence, proof, premise, layer and report rules still apply.
Pooled ND candidates and any zero-GEN end actions keep their separately specified
candidate policy. The component/request laws do not certify those effects as a
same-call concrete trace and do not repair the measured precision regressions.

## 1. Definitions and explicit assumptions

Let `Method` be the finite set of methods in the complete resolved prescan graph.
Let `Key` be a full runtime MethodKey, including its method, entry and context.
No theorem below replaces a full Key by a method name. Effective forms and call
bindings are fixed for each Key during a run. Their call targets belong to the
prescan graph. Context-sensitive plans may be conservatively joined in prescan;
the runtime retains its actual contexts and full caller premises.

`Sigma` is the finite set of representable static `(class, field)` accessors in
the accepted IR, effective rules, native models and canonical accessors. It is
not a claim about all fields that arbitrary Java reflection could create. Every
accepted static input field belongs to Sigma. A new accessor outside Sigma is a
failed construction obligation, not permission to ignore the input. A class
point is separate from its field points.

The normal abstract initials have exactly these shapes:

```text
B(b)    = (nonStaticBase b, root, STAR, empty E, abstract mark)
F(C,f)  = (STATIC, [C,f], STAR, empty E, abstract mark)
```

The special abstract STATIC root is a summary seed, not a request slot. It does
ordinary identity/keep transfer but no rule evaluation. Its concrete summary
read is governed by the component composition in section 6, not its raw root
output. An exact concrete empty-root STATIC fact is outside the accepted input
grammar. Root/class ANY facts and exact concrete class-point facts remain legal.

There are two instances of the same RequestStorage class per Key. `R` is the
authoritative cumulative relation in SummaryStorage. `W` is the actor-owned
write-only temporary relation. Both map a normal slot to a finite set of concrete
marks. Producers union into W. At a method-queue-empty barrier, commit reads W
synchronously, installs fresh memberships in R, reserves queued notifications,
and returns. The actor then replaces W with an empty instance. No callback may
reenter the actor during this operation. R remains active after an answer.

The proof uses these external construction assumptions, which are not request
coverage hypotheses:

1. Prescan includes every effective form and resolved target used by the run.
2. The two normal shapes and the impossible exact STATIC empty-root case hold.
3. Each AP primitive uses the stated scalar reference semantics. Ordinary
   summary matching retains its original field E and full premise checks.
4. Incoming call input admission precedes callee input delivery. Request commit
   precedes its notification. All resulting deliveries are counted work.
5. For eventual closure, the accepted run has a finite reachable abstract state
   and fairly services its existing work. A cancelled or timed-out run need not
   reach that closure. There is no wall-time or bounded-delay theorem.

The following sections construct coverage from the finite forms, graph and
primitive cases. They do not assume a predicate called “the plan covers all needs”.

## 2. Exact two-slot request relation

First cut an incoming leaf to the run-1 geometry. A valid static leaf has path
`root`, `[C]` or `[C,f]`; a deeper accepted input is cut before this projector.
The original leaf annotation and layer are retained for ordinary AP matching.
Only the request test erases field E and FLOW mark exclusions.

For a concrete request mark T define `Match(slot, leaf)`:

| Slot and leaf | Match |
| --- | --- |
| B(b), nonempty leaf on b | Yes |
| B(b), different base or Reach | No |
| F(C,f), concrete/abstract leaf at `[C,f]` | Yes |
| F(C,f), ANY/STAR leaf at root or `[C]` | Yes |
| F(C,f), exact leaf at root or `[C]` | No |
| F(C,f), another class/field or non-static base | No |

A FLOW leaf gives no concrete answer. For a TAINT leaf, T must be in that leaf's
actual exact/ANY mark set. R membership alone supplies no mark. The answer is:

| Concrete matching leaf | Answer initial for T |
| --- | --- |
| Non-static exact root | Exact root on the same base |
| Other non-static leaf | STAR root on the same base |
| Exact leaf at `[C,f]` | Exact `[C,f]` |
| ANY leaf at `[C,f]` | ANY `[C,f]` |
| Broad ANY ancestor of `[C,f]` | STAR `[C,f]` |

Every answer has empty E. There is no ANY_TAINT request answer; that input
annotation is normalized to ANY by the adapter. The ordinary initial start rule
still assigns the correct may/demand layer. In particular, the broad-ancestor
row does not assert a NORMAL exact field fact.

**Matcher theorem.** A request produces exactly the above answer alternatives
for its slot and the concrete marks present in an input, independently of E,
request role and FLOW exclusions.

**Proof.** Case-split on the base and the three possible static path lengths.
Different bases fail. At the same path, exact and ANY take their corresponding
rows. At a proper prefix, only the broad leaf denotes a possible continuation;
an exact prefix has no descendant. At a sibling, neither prefix relation holds.
The non-static slot has no path test beyond its base. Finally intersect the
selected leaf's actual concrete marks with the requested set. This enumerates
all accepted shapes; it creates no mark or conjunction. E is absent from each
test, so changing E cannot change this request result. FLOW has no concrete mark
set and cannot enter the answer branch. This is equality to the **new erased-E
request policy**, not equality to the old E-sensitive request cursor. Ordinary
summary guards are not erased by this theorem.

An exact class point `[C]/EXACT/T` is a separate direct concrete input. Preserve
its exact shape and layer; never project it onto a field. A broad supplier also
has a possible class-point component. If an effective class-level operation can
read that component, the direct class-component path must preserve its may layer.
It must not invent NORMAL exact class taint. Section 6 includes both components.

## 3. Obligation 1: complete request-producer inventory

The inventory is over effective operations, after native/generated/lowered form
construction. A surface action name is not a separate semantic producer.

| Effective operation on a FLOW fact | Request need |
| --- | --- |
| Source condition, sink condition, or conjunction ContainsMark literal | The literal's concrete input mark |
| ContainsMarkOnAnyField and its positive NNF leaf | The same concrete mark, with the ordinary predicate unchanged |
| Concrete input-mark gate of a source, pass or transfer microedge | The gate's input mark, not its generated output mark |
| Selected-mark cleaner, including generated returned-stage rewriters | Its selected mark on the original abstract initial |
| A primitive with an abstract target excluding marks | Every target-excluded mark, as an effective selected-cleaner need |
| Call/summary transfer of a preceding need | The same need, routed to the original caller slot |

The last exclusion-creator row is a required semantic side effect. The measured
[MarkGate](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/MarkGate.kt)
can form an excluded target mark in a generic compiled edge without itself
emitting a request. The selected semantics adds a same-origin request for each
target-excluded mark, or normalizes that edge to an equivalent explicit
selected-exclusion operation with that request. Legal hand-built edges are not
rejected to make the theorem true. The side effect belongs to the exclusion-producing
operation, not to inspection of a final mx header. Summary mx carries the earlier creator obligation
through call replay; it does not need a runtime provenance store.

The measured source anchors for the inventory are:

- [MarkCheck](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/MarkCheck.kt),
  `markCheckFacts`: ordinary concrete literal checking.
- [EdgeApplication](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/EdgeApplication.kt),
  `flowHit`, and MarkGate: concrete microedge input gates.
- [Clean](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Clean.kt),
  `FlowClean.run`: selected cleaner adds mx and emits the selected mark request
  whenever its base matches, including a spatially unrelated field.
- [RunMethodLocalServices](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt),
  `conjunction`, `sourcePredicate`, `checkSinks`: legacy and positive NNF leaves.
- [FormApplier](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/FormApplier.kt),
  `statement`, `stage`, `gen`, and
  [RunMethodCallServices](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodCallServices.kt),
  `applyStage`/`cleanChain`: effective entry, statement, bound/returned, native,
  generated, exit and end-GEN execution routes.
- [JIRRuleForms](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt)
  and [JIRCallPlanBuilder](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt):
  condition/action lowering, cleaner lowering and returned-stage rewriters.

**Producer theorem.** Each mark-dependent primitive that cannot decide its
concrete input on a FLOW fact falls into a row above.

**Proof.** Enumerate the effective form grammar: plain edges, conjunction/NNF
conditions, selected/all cleaners, type filters, keep/strong-write operations,
and GEN. A concrete edge premise uses its input mark; an abstract premise needs
none, except newly introduced exclusions as stated above. A condition reduces
to its active positive leaves; each concrete leaf uses MarkCheck. OR changes
which proof can complete; AND stores compatible bindings. Neither introduces a
new kind of mark input. A selected cleaner uses its selected mark. A remove-all
cleaner tests no concrete mark and requests none. A zero-generation edge has a
zero input and does not request the generated mark. Type filters inspect type
and geometry, not a concrete mark predicate. Keep/strong-write geometry adds no
mark need. Calls compose these operations and route their existing needs. This
is an exhaustive structural induction on the effective form, not an enumeration
of only sink rules. Repeated canonical leaves and separate occurrences remain
in the predicate stores; unioning their request marks does not merge their proofs.
No mark request does not mean no structural footprint: a zero-generation target
or remove-all target still contributes every field its operation can affect to H.

## 4. Obligations 2 and 3: construct the plan and its dynamic residual

For each method m enumerate its finite effective forms. Construct:

```text
localH[m]        normal static fields read, written or otherwise affected
localN[m,f]      unchanged-field concrete needs found by section 3
localWide[m]     selected-mark/base/class needs requiring input activation
localPoints[m]   legal concrete class-point operations
```

`localH` includes semantic scopes, not just literal read positions. A wide
operation can own every field in its class or in Sigma. A selected-mark cleaner's
base-wide abstract exclusion requires its mark for each relevant normal-field
analysis, even if the cleaner is on another field. Keep that finite wide rule as
shared metadata; do not build every mark/field/MethodKey product in advance.

Compute the least closure by starting at the local sets and repeatedly unioning
the current sets of each resolved callee. Every insertion is a member of a finite
form-derived universe, so iteration terminates. Share immutable closed metadata
per method/component. When an actual input activates a field in a wide scope,
instantiate its known marks into that Key's temporary RequestStorage.

The reference construction can return actual path data without choosing a witness
from an existential proposition:

```text
for (method, formIndex, need) in enumerated local members:
    insertIfAbsent(method, need, path=[], formIndex)
while an inserted (callee, need, path, formIndex) remains:
    for caller in resolvedCallers(callee):
        insertIfAbsent(caller, need, path=[caller -> callee] + path, formIndex)
```

Each successful insertion consumes one previously absent `(method, need)` pair.
There are finitely many pairs. Each record contains a concrete finite edge list
and the actual local form index. This is a proof/reference construction; runtime
metadata need not keep these paths. The Lean reference closure enumerates finite
closed subsets instead. Its seed, edge-closure and leastness laws identify the
same least set; the path-producing worklist supplies the constructive certificate.

**Path-witness lemma.** A closed-plan member has an executable witness consisting
of a finite resolved call path ending in an enumerated local form member.

**Proof.** Give each initially inserted member the empty path and its local form
index. When propagating from callee c to m, prepend the actual edge m→c to the
callee's existing witness. This construction proves sound membership. Conversely,
induct on the length of any such call path. A length-zero member is inserted at
initialization. For m→c followed by a shorter path, the induction hypothesis puts
the member in c's closure, and edge propagation puts it in m's closure. This proves
total path coverage and leastness. A recursive component needs no acyclic graph
assumption: every finite derivation has a finite path witness.

**Static coverage theorem.** Every unchanged-field need found by a producer is
precomputed, or is a precomputed wide need activated when its input becomes relevant.

**Proof.** The producer theorem supplies a local form index, position/scope and
mark. A direct unchanged field enters localN. A wide scope enters localWide and
the input activation computes its exact field intersection with Sigma. The path
lemma propagates that entry through every resolved caller, including recursive
paths. Storage creation installs resolved memberships before exposing subscribers.
Later activation writes the same member to W and uses the late-request law in
section 8. The proof constructs the member; it does not assume plan coverage.

**Dynamic residual theorem.** A mark need under a normal abstract initial i is
not lost when its current checked position differs from i.

**Proof.** Normalize i only to B(b) or F(C,f), preserving the full Key. If the
required member is already in R, persistence discharges it. Otherwise union it
into W at that original slot. Do not test the current output path to choose an
origin. In `x=C.g; C.f=x; check(C.f,T)`, the residual is g/T, regardless of the
precomputed f/T member. The same argument covers argument→static, static→local,
and a caller FLOW input. A concrete current fact does not use this branch; its
actual T can answer directly. The abstract root seed uses section 6's field-owner
counterpart, not a guessed root request. Thus the two normal shapes exhaust the
remaining abstract-origin cases.

## 5. Obligations 4, 5 and 6: two counterexamples that fix root composition

The impossible exact STATIC root assumption alone does not justify reuse of a
raw abstract-root summary for concrete inputs. Both following inputs are legal.

**Counterexample A: cleaned taint is restored.** Let a method contain only
`Clean(C.f,T)` and receive exact `C.f/T`. Its concrete f/T analysis correctly
returns no f/T. A root summary that skips the cleaner is identity. Unioning that
identity result with the concrete result restores f/T. Adding a concrete analysis
does not repair an unrestricted root read.

**Counterexample B: an untouched sibling is lost.** Strongly write C.f and receive
exact `C.g/T`, g≠f. A coarse root keep can exclude the whole class C. Applying that
raw root summary drops g/T even though the write cannot change g. A literal
footprint `{f}` and a raw-root complement are therefore insufficient.

The selected repair is **structural ownership plus a proved untouched identity
frame**. It is not an extra request store or a supplier cache.

## 6. Constructive static component composition

Partition a static input into these disjoint semantic components:

1. Exact class-point components, one per representable class.
2. Field components, one per `(C,f)` in Sigma, retaining their suffixes, actual
   marks, ordinary E and layer.
3. Any explicit residual outside the finite named prefix cells. Such a residual
   is permitted only when the accepted adapter proves no form can observe it.

For a broad root/class ANY leaf, compute the corresponding component restrictions.
This is a transient read-time partition. A field restriction of a broad may leaf
retains its uncertainty; it is not a new NORMAL exact leaf. Do not flatten every
method's input relation into a permanent method×field table.

Let H[m] be the closed semantic footprint from section 4, with wide scopes expanded
over Sigma and relevant input fields. It is a set of geometries, not request marks.
Use this ownership rule:

```text
field h in H[m]       use normal field analyses (abstract STAR always,
                     plus supplier-backed requested concrete analyses)
field h not in H[m]   use the untouched-component identity frame
class-point input    preserve its direct concrete component and effects
```

Do not additionally apply the raw root summary to either field class. A missing
result from an owned field branch is a real empty result; it is not a reason to
fall back to identity. Read-time ownership must also govern native and persisted
opposite-direction summary views. Otherwise either counterexample recurs later.
The identity frame is available only along an actual normal-return derivation.
It cannot invent a return when a path has no normal return. The root seed keeps
its ordinary control/summary role for this purpose; its raw data result is not
the proof of the frame.

**Partition lemma.** The components cover every accepted static input location
and do not assign a field location to two owners.

**Proof.** Every accepted static address is a class point, starts with exactly
one `(C,f)` from Sigma, or belongs to the explicitly unobservable residual. These
cases are disjoint by path length and first class/field accessors. H membership
is decidable, so each field has one owner. Restricting each concrete may-location
to its case and unioning the results recovers the input's location denotation.
This statement does not promote an existential/may leaf to certainty. Physical
tree encoding and layer annotations must implement these scalar restrictions.

**Untouched-frame lemma.** If h∉H[m], any finite normal-return derivation of m
and its resolved callees leaves h's input component unchanged and cannot read it
into a rule result. This lemma is conditional on the return derivation, not on
the existence of a raw root output.

**Proof.** A primitive that reads, writes, cleans or otherwise affects h would
place h in localH, contradicting h∉H[m]. A broad operation is not an exception:
its scope expansion would also insert h. An unrelated keep, transfer, type filter
or strong write is identity on h by its scalar AP row. A call has a static identity
binding. If a resolved callee could observe h, the path-witness lemma would insert
h in H[m]. Induct on a finite instruction/call derivation, using these primitive
cases; a recursive call is a smaller finite subderivation. Therefore its component
result is exactly identity. In particular, a coarse raw E{C} is not used to prove
identity on g: the primitive disjoint-write row proves it.

**Owned-component lemma.** For h∈H[m], normal field analysis plus the request
coverage of sections 3–4 and 8 supplies every concrete branch needed for h.

**Proof.** Start the abstract h initial unconditionally. Induct over its finite
transfer derivation. Mark-independent primitives preserve the scalar component
transfer. A concrete gate or predicate leaf supplies a request by section 3; an
already concrete branch evaluates its actual mark. A selected cleaner always
registers its selected T for the original normal h initial, even when its concrete
spatial effect is disjoint. Static plan/wide activation or dynamic residual keeps
that membership. The arrival theorem supplies the concrete h/T initial from any
matching supplier. Its concrete cleaner row decides inside/partial/disjoint and
therefore removes f/T but preserves disjoint g/T. A call uses the same argument in
its field-owner counterpart, with the full caller premise and layer intact.

Conjunctions require a closure induction, not distributivity of AND over a single
input. The existing predicate store joins the component branches under the same
occurrence/proof and compatible complete binding rules. Thus the individual leaf
inputs still produce every previously permitted complete binding. Their request
sets are independent alternatives and supply no new conjunction evidence.

**Static composition theorem.** The partition, owned-component lemma and identity
frame preserve the scalar static component transfer under the selected request
policy. They resolve both counterexamples.

**Proof.** Case-split each input component by its unique owner. For f/T in A,
the concrete owned branch is empty and no root result is admitted, so T cannot
reappear. For g/T in B, either g is owned and its disjoint concrete/abstract row
preserves it, or g is unowned and the frame returns it. The same case proof applies
to broad supplier components and to later summary reads. Exact class points keep
their direct input and legal class-level operations; they are neither dropped nor
broadened to fields. The forbidden exact empty root is not used in any case.

This theorem is about the specified component composition. It is not a theorem
about the measured root summary implementation. An implementation must refine the
transient partition, uncertainty annotations and owner selection. In particular,
old descendant-only summary candidate selection cannot silently exclude a projected
broad ancestor. The read adapter must pass the matching component to the full AP
guard. No new same-call confirmation theorem follows for pooled ND candidates.

## 7. Obligation 8: concrete-initial safety

K6 gives a FLOW fact a single abstract-mark initial. A concrete fact retains its
actual zero, concrete, or multiple-concrete premise; an invalid pairing fails the
invariant rather than disappearing.

**Concrete safety theorem.** No upstream mark request is needed from a concrete
current fact or concrete-mark original initial, but concrete answers and ordinary
transfer must remain enabled.

**Proof.** A concrete predicate/gate reads the current exact/ANY mark sets. A
selected cleaner removes actual marks by its concrete spatial rows. Neither has
an unknown mark to obtain by restarting that already concrete initial. A current
concrete fact can still answer a callee request for T precisely when it carries T
and matches section 2. The caller's original concrete/ND premise is retained for
ordinary summary application. For an abstract FLOW premise the earlier producer
and call-path proofs continue to supply requests. These cases exhaust K6. A
source-generated concrete mark is in the concrete case, even when the method also
analyses abstract initials; it is not a reason to raise a request for
the source's output mark. Nothing here skips source actions, F75 residual effects,
predicate bindings, GEN, concrete summaries or cancellation checks.

## 8. Obligation 7: no-memo arrival theorem

Let A be the linearized admission/update of an actual caller input, E its callee
abstraction read of R, Q a request commit, and V the corresponding caller-input
visit. Registration precedes A; `A < E` and `Q < V`. Queued same-unit events obey
the same order. A caller visit observes its current immutable input value for
the accepted full header. No input is reconstructed from a request key.

**Arrival theorem.** Every accepted request/input pair is processed by E or V.

**Proof.** If Q<E, E reads persistent R and handles the input. If E<Q, then
`A<E<Q<V`, so V sees the admitted input. Equivalently, both misses require
`A<E<Q<V<A`, a strict-order cycle. Registration races do not add a third gap:
if an earlier commit missed the not-yet-registered subscriber, its later E read
sees R. A late input update is itself another A/E pair. A new request delta is
another Q/V pair. The proof covers different callers, contexts, cycles and
input growth because it is per full admitted input event, not per first caller.

For concrete TAINT, E emits requested answers and V handles late marks. The extra
fresh-TAINT question replay arm is redundant. For FLOW, the input-admission side
still joins current R and forwards a set of needs; it is not a concrete answer.
Remove no ordinary summary replay. InitialFactStore may deduplicate duplicate
answers. R is not consumed by one answer, so future inputs still see it.

No pending answer memo is needed. This conclusion fails if a test or adapter
delivers a nonzero incoming input without paired caller input admission. Such
delivery must provide the same order contract. Internal zero/GEN facts are normal
transfers, not an omitted incoming-supplier channel.

## 9. Obligation 9: ignored mark exclusions are redundant requests

Use a proof-only provenance relation `Need(Key, originalSlot, T)`. It records an
eventual request obligation, not another runtime store. Define its constructors:

1. A selected cleaner or generic abstract-target exclusion creator emits that same Key/slot/T need before
   publishing an output containing newly excluded T.
2. Mark-independent transfers retain the original Key/slot.
3. A callee obligation is routed over its full caller FLOW input to the caller's
   original normal slot. Root-seed duplicates use the proved field-owner
   counterpart, not a root request.
4. A union/subsumption of FLOW branches cannot introduce a mark exclusion that
   was absent from every contributing derivation. Dropping exclusions by
   intersection can remove obligations from a header, not invent a new T.

**Exclusion provenance lemma.** A reachable excluded T on a normal abstract-origin
FLOW route has a finite derivation of Need for the same original destination used
by an unfiltered climb. A special root route uses its field-owner counterpart;
it is not evidence of a nonexistent root request.

**Proof.** Initial FLOW exclusions are empty. At first creation, constructor 1
applies. Transfer and summary composition use constructors 2 and 3. Recurse through
the finite callee derivation until the creator is reached. Header merging uses
constructor 4. Native/generated cleaner steps are first creators, not unexplained
exceptions. A preloaded arbitrary mx value with no creator is outside this proof.

**Erasure theorem.** Removing mx tests from request propagation leaves the eventual
cumulative request union unchanged, although it may add duplicate work.

**Proof.** The filtered request steps are a subset of the unfiltered steps. Each
extra step carries an excluded T. The provenance lemma supplies the same eventual
destination obligation. Sections 4 and 8 deliver it through the ordinary method
barrier. Therefore the added membership is already in the filtered closure. Union
idempotence makes the two least request relations equal. This is not equality of
event order, notification counts, temporary sizes or pre-timeout prefixes. Incoming
field E is erased by the new request definition; its erasure is not derived from
the mx provenance lemma. Ordinary E and mx transfer semantics remain unchanged.

## 10. Merged marks, batching and early admitted-set subtraction

All marks in one request row are independent. Let R be the admitted set and W
the temporary contribution for a slot. Commit computes `D = W - R` and installs
`R' = R union D = R union W` before notification. Replacement clears only W.
No request is removed from R.

**Batch theorem.** Any finite grouping/order of contributions gives the same
eventual R as immediate singleton admission.

**Proof.** Expand every finite set into its members. Union is associative,
commutative and idempotent, so the final relation is the initial R union all
members offered. D notifications contain exactly first admitted memberships for
that commit. A delayed batch can change when a concrete branch starts; the arrival
theorem prevents loss. It does not prove equal scheduling or a time bound.

On a FLOW route use `offered - targetR[originalSlot]` before per-mark callbacks.
There is no mx filter in this selected request policy. A stale immutable R is a
subset of current R and can only allow duplicates. Subtraction is safe because
every removed membership remains persistent at that exact destination. A
different MethodKey, slot, or callee's R is not a valid subtraction owner. Do not
query W as a second membership oracle. Different matching inputs with the same
destination union their contributions. Do not subtract the union of exclusions,
and do not turn multiple requested marks into an AND condition.

Both storage instances have the same class. During commit only the method actor
can write W, and notifications are queued. Thus no actor write occurs between
the synchronous read of W and its replacement. A later event writes the new W.
Commit stores immutable admitted sets and does not retain the mutable W instance.
This is the required non-reentrance proof; no extra takeBatch protocol is assumed.

## 11. Method-empty completion and cancellation

Maintain the existing counted Work reservation while a method drains facts,
publishes summaries, commits W, replaces W and reserves notification children.
A request-only event also schedules that existing Work. It needs no synthetic
fact. Same-unit effects remain queued.

**Completion safety.** Global completion cannot occur while an accepted request
or its result is owned by W, commit or a queued notification.

**Proof.** Before commit the method's Work reservation owns W. During commit that
reservation remains live. Before it is released, every child delivery obtains a
counted reservation. Afterwards a child or subsequent method Work owns each effect.
No effect crosses a zero-reservation interval. A duplicate suppressed by R has no
new effect to own. Completion also requires the method's fact/summary work to be
empty. Request-only arrivals and writes queued during flush therefore cannot be
mistaken for an idle method.

With finite accepted work and fair service, a busy method eventually empties its
fact queue and submits W. A cycle/SCC changes the finite fixed point, not this
ownership argument. There is no maximum request-age guarantee while it is busy.
Cancellation or rejected delivery does not prove a completed closure. Preserve
the existing failure/cancellation path, release W/borrowed views, and do not publish
a false COMPLETE state. A fresh run starts with fresh stores and reservations.

## 12. Executable witness matrix and formal scope

The constructive Lean work is split across
[Run1Requests](lean/ApSpec/Run1Requests.lean) (slot/projector and form/graph closure),
[Run1RequestsSafety](lean/ApSpec/Run1RequestsSafety.lean) (component ownership/frame
and cleaner counterexamples), and
[Run1RequestProtocol](lean/ApSpec/Run1RequestProtocol.lean) (storage/arrival/protocol
laws). The first two modules passed their selected build, declaration audit and
executable vectors on 15 October 2026:

```text
cd spec/lean && lake build ApSpec.Run1Requests ApSpec.Run1RequestsSafety
cd spec/lean && lake env lean /tmp/run1-requests-selected-audit.lean
cd spec/lean && lake env lean /tmp/run1-requests-selected-vectors.lean
```

The selected build completed 5 jobs. The audit checked 963 declarations with zero
unexpected axiom dependencies; allowed dependencies were only `propext` and
`Quot.sound`. Executable vectors passed. The source/log-hashed receipt is under
evidence root B from the current performance report:

```text
report-preparation/run1-request-proofs-20261015/
  lean-20261015T184059.935017Z/receipt.json
SHA-256 d3109371a385bf6d47419cdd2a9db04377cb6e39459bc41060a541867bc98d09
```

The full-library check also passed on 15 October 2026, including all three F78
modules and their executable audit controls:

```bash
cd spec/lean
lake build
lake env lean audit.lean
```

The build completed 123 jobs. The audit checked 21,118 declarations and found zero
unexpected axioms; only `propext` and `Quot.sound` were permitted. Both F78
executable audit groups passed. The full source/log-hashed receipt is:

```text
report-preparation/run1-request-proofs-20261015/
  full-20261015T184238.844907Z/receipt.json
SHA-256 b97dfe560d1be29db6cac603722abcdb7468e2a231189b5fe680db478296ea6b
```

Both receipts are under evidence root B from the current performance report.
They are proof-build evidence, not runtime tests or JVM conformance. The runtime
diff against measured `27e22bb1e` is empty for `core` and `rules`.

The statement map is below. Names are in the corresponding `ApSpec` namespace.

| Obligation | Lean statements and their actual scope |
| --- | --- |
| Exact slot matcher | `Run1Requests.project_exists_iff`, `projector_ignores_field_E`, `projector_keeps_mark`; finite leaf projector, not Kotlin tree traversal |
| 1: producer inventory | `effective_leaf_collected`, `excluded_target_member_collected`, `zero_and_removeAll_not_mark_producers`, `generated_targets_keep_structure`; effective finite form grammar, with frontend extraction as a bridge |
| 2: static plan | `closure_seed`, `closure_call`, `closure_least`, `recursive_call_coverage`, `unchanged_static_plan`, `wide_activation_complete`, `effective_static_dispatch`; executable finite graph/activation |
| 3: dynamic residual | `static_dynamic_cover`, `changed_slot_retains_dynamic`, `changed_static_origin`; full logical owner and original slot |
| 4: root safety | `Run1RequestsSafety.primitive_footprint_collected`, `reachable_primitive_footprint`, `primitive_frame`, `frame_identity`, `transitive_plan_frame`, `transitive_plan_composition`, `root_field_composition`, `returned_composition_exact`, `no_return_no_frame`; executable primitive traces tied to effective bodies and resolved call paths, with an explicit return guard |
| 5: broad/class input | `Run1Requests.broad_root_projects`, `broad_class_projects`, `exact_class_not_field`, `direct_class_preserved`, `broad_root_class_answer`, `broad_class_answer_starts_demand`, `static_root_unconditional`; `Run1RequestsSafety.finite_partition_exact`, `partition_disjoint`, `broad_partition`; scalar locations, not a full must/layer codec theorem |
| 6: summary safety | `cleaner_removes_target`, `cleaner_preserves_sibling`, `relation_composition`, `native_and_opposite_read_preserved`, `persisted_return_guard_preserved`; same finite relation/read view, not production RecordStore refinement |
| 7: arrival | `Run1RequestProtocol.answer_witness`, `both_arrival_orders`, `registration_race`; computed answer witness and logical linearization order |
| 8: concrete initial | `Run1Requests.concrete_never_requests`, `special_root_never_requests`; origin dispatch, with primitive concrete evaluation proved in section 7 |
| 9: exclusions | `Run1RequestProtocol.excluded_has_emitted_need`, `excludedNeedWitness`, `derived_flow_exclusion_erasure`, `excluded_forwarding_same_union`; executable normal-origin creator/transfer/call/join induction and resulting union law, not an assumed provenance predicate |
| Batching | `collect_holds`, `commit_holds`, `commit_membership`, `commit_temporary_empty`, `fresh_not_admitted`, `second_commit_no_notification`, `filter_destination_safe`, `two_batches_one_barrier`, `barrier_placement_same_union`, `post_commit_collect_uses_new_temporary` |
| Prefix/completion | `trace_encodes`, `every_prefix_retains`, `completed_admits_all`, `completion_empty_owners`; finite logical cells and explicit owner predicates, not runtime scheduler termination |

The executable Lean witnesses include
`Run1Requests.Examples.recursive_seed_reaches_caller`,
`class_exact_is_not_a_field_answer`,
`wide_cleaner_covers_unrelated_incoming_field`, and
`Run1RequestsSafety.Counterexamples.raw_root_cleaner_leak`,
`raw_root_write_loses_sibling`, `constructive_frame_repairs_both`,
`changed_g_to_f_keeps_original_input_owner`. The protocol's
`batch_vector_members` computes an actual merged set and empty replacement.

Each executable control must test the actual result below, not assume coverage:

| Control | Required witness/result |
| --- | --- |
| Mark T at exact non-static root; mark U below it | T gives exact root, U gives STAR root; no invented mark |
| Root/class ANY T; exact class point T | Broad field answer stays may; exact class point is direct and does not taint its fields |
| f/T cleaner; g/T and g/U input | f/T gone; g/T and g/U retained; unrestricted root composition fails |
| Strong write f with coarse root E{C}; g/T input | Identity frame or owned g preserves g; raw root read fails |
| Base-wide F75 from a cleaner on f | A relevant g analysis registers g/T; concrete g/T survives a disjoint clean |
| g→f, argument→static, static→local | Request targets the original slot, not the checked position |
| Source/sink NNF AND/OR, repeated canonical leaves | All required marks requested; complete bindings/proofs remain separate |
| Exact/ANY supplier; E changed; FLOW mx contains T | New matcher ignores E/mx, FLOW never answers, missing concrete T never appears |
| Generic target exclusion | Same-origin creator need, also for a legal hand-built edge; carried summary mx reuses that obligation |
| Diamond and recursive call graph | Closure contains exactly local members reachable by finite paths |
| Request first/input first; subscribe and update during queued flush | At least E or V handles each pair; duplicates do not restart initials |
| T answered, then a new caller/supplier U | R remains active; U independently answers if requested |
| Request-only method; final flush; cancellation | Counted ownership prevents early COMPLETE; cancelled run makes no closure claim |
| Native and opposite-direction persisted summary read | Same component ownership/frame, ordinary full guards preserved |

The theorem family deliberately stops before physical AP tree/mask refinement,
whole-driver source/seed equality, pooled ND confirmation, and fixed-project
no-FN/precision/resource acceptance. Those are separate implementation and
validation gates. In particular, equal request closure does not imply equal
timeout prefixes or repaired findings in the measured runtime.
