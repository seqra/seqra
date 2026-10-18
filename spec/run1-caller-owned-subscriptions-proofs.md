# Proofs for caller-owned run-1 subscriptions

Date: 18 October 2026. Contract:
[run1-caller-owned-subscriptions.md](run1-caller-owned-subscriptions.md).
Status: **constructive model and protocol proofs; runtime adapters under validation**.
This dossier follows [proof-guide.md](proof-guide.md).

Latest measured runtime: `7abeecd5c`. OWASP reached **run-7**, completed
**run-6**, and accepted its **run-5** report before timeout. Conductor
failed in **run-1**, with no completed engine or accepted report. The
matrix stopped before ThingsBoard and Stirling-PDF. A concrete-answer
batch correction is under validation. These finite proofs do not certify
the Kotlin adapter or establish a timing or heap result.

## 1. Claim and proof boundary

Fix the selected ordinary run-1 reference: eager actual static admission,
the nine initial families, early static DEMAND, strong writes with Empty E,
relative callee starts, complete ordinary call bindings and the same local
operators/publication rules. Compare that ordinary request procedure with
the caller-owned procedure. Do not compare it with a historical NORMAL-static
or frozen-plan policy and attribute those policy differences to routing.

The main claim is constructive coverage of **each finite default derivation**:

```text
given: an ordinary derivation of full summary s
build: a caller-owned derivation of the same full summary s
```

The same construction covers local semantic observations. Prescan interest
reachability can admit more actual concrete starts. Thus the result is
inclusion, not reverse equality to the narrower ordinary request set. Using
the same widened interest relation gives transport equivalence, but is a
different comparison from the required default-coverage result.

“Same summary” includes full owner, premise/support, conclusion payload,
tail, layer and endpoint. These are derivable semantic atoms. An actual packed
store may subsume a row. Its representation must retain the applications of
the represented atoms; the theorem is not literal membership of an old
physical header after a store reduction.

Two new Lean modules separate the routing optimization from the surrounding
analysis calculus:

| Module | Checked scope |
| --- | --- |
| [CallerOwnedRequestRouting](lean/ApSpec/CallerOwnedRequestRouting.lean) | Actual-input request paths, fixed-CG contraction, retained productive bindings and finite source/view/callsite/input composition |
| [CallerOwnedSummaryCoverage](lean/ApSpec/CallerOwnedSummaryCoverage.lean) | Full typed analysis derivations, canonical descriptors, actual requested starts, n-ary call application, presence and observations; ordinary-to-caller coverage |

Complete method/entry/context identities and semantic objects can be encoded
by injective opaque IDs in these models. They are not lossy hashes. Proof
trees and delivered-pair lists are executable **proof witnesses**, not a
requirement to retain such histories in the runtime.

The local AP/JVM kernel is shared as explicit primitives. The theorem is
parametric in those primitives; it does not assume the conclusion that all
default summaries are covered. Kotlin producer validity, complete frontend
translation, packed-store refinement and concurrent scheduling remain adapter
obligations in §10. The finite proofs do not certify the runtime adapters.
The [implementation map](../docs/design/bidirectional-caller-owned-subscriptions-implementation-20261018.md)
records their code and validation.

## 2. Initial descriptors preserve admission and interpretation

Initial tags contain EXACT or STAR, never ANY. Their constructors cover ZERO,
abstract Base/field, and concrete Base/field/class with the two legal tails.
There is no constructor for an S-root initial or abstract class initial.
The full payload domain separately contains `Base.staticRoot` for legal
summary endpoints and broad supplied facts. This does not add a root initial.

Let `encode(i)` be the direct canonical descriptor, and `decode` its callee
decoder. Case analysis on the initial constructor gives:

```text
decode(encode(i)) = i
```

Consequently the owner, full initial tag, field/class coordinate and actual
mark are unchanged. This proof does not require another static projection.
The same ordinary start kernel then interprets exactly that initial.

The relevant interpretation distinction is:

```text
supplied current C.f ANY/T
  existing pre-boundary normalization -> initial C.f STAR/T
  encode/decode -> that same STAR initial
  ordinary start -> current ANY/T at DEMAND
```

It is not an ANY initial, an exact initial or a pure identity. Broad root/class
and descendant normalization remains before this boundary. Existing scalar
AP laws in [BottomUpConcreteStatic](lean/ApSpec/BottomUpConcreteStatic.lean),
such as `chain_field_supply`, `decode_encode`, `chain_static_start_ANY_D`,
`chain_not_compactable` and `nary_stays_bound`, check these representative
AP/codec distinctions. This dossier does not reuse that older model's
superseded static-request routing rules.

For actual nonstatic requested starts, an explicit guard requires the same
callee Base slot and an actual matching concrete mark on a derived input.
The shared projector chooses the legal exact/chain shape. Need T alone is
insufficient. Actual static marks use the same eager guard and descriptor
path as the reference; they do not wait for static requests.

Relative layers are part of interpretation. Static initials and nonstatic
concrete STAR starts are DEMAND. An independent nonstatic abstract or exact
start is NORMAL. A DEMAND caller input remains in the caller's actual binding
and joins back on application. Copying that caller layer into every relative
nonstatic start would fail to preserve ordinary NORMAL callee summaries.

## 3. P/A frontiers: every admitted pair, once

For one owner, let I be admitted semantic input candidates, P pending marks
and A processed marks for a slot. A request offer M admits `M - A - P`.
A drain unions P into A before outward work, then resets P.

The invariants are:

```text
A intersect P = empty
A union P = all admitted request marks
fresh request marks join cumulative I
fresh input candidates join cumulative A
```

Take an admitted pair `(input,T)`. If T is in A when the input arrives, the
fresh-input frontier handles it. Otherwise T is still pending or arrives
later; its unique fresh-request drain handles the already stored input.
An input arriving while T is pending does not also join P on arrival.
Conversely either frontier requires both an admitted input and offered T.

Uniqueness follows from the two freshness checks: neither an old input nor
an already admitted mark can create the same pair again. A real new header,
support or mark delta is a new semantic candidate where the ordinary relation
requires it; batching cannot discard such growth.

The [IncrementalRequestTable](lean/ApSpec/IncrementalRequestTable.lean) model
proves this for arbitrary finite event traces and a final drain. The routing
module exports `caller_view_both_arrival_orders` and
`caller_view_no_pair_twice` at exact retained Binding objects. Its proof
history is ghost state; no runtime per-source×input delivered-pair cache is
required.

This is a pair-delivery lemma, not a concrete truth lemma. Interest geometry
ignores E/mx, while actual concrete admission remains separate. Marks are
independent requests, not members of an actual ND premise.

## 4. Three-stage source/view/callsite/input composition

A local P/A lemma alone does not prove the shared-view architecture. The new
model composes three calculated tables:

```text
committed source request + registered reachable view
  -> successful source/view pair
  -> compute marksAtView from those successful pairs

marksAtView + registered full callsite subscriber
  -> successful mark/subscriber pair
  -> compute marksAtSubscriber from those successful pairs

marksAtSubscriber + retained actual input
  -> successful mark/input pair
```

The second and third batches are **computed from upstream deliveries**. The
theorem does not assume that the desired final endpoint is already delivered.
Source/view event order is arbitrary. Each downstream subscriber/input can
register before or after its incoming batch; repeated marks are merged.

`Pipeline.source_view_callsite_input_coverage` constructs the final pair from
source membership, view membership, complete reachability, subscriber
registration and input registration. `Pipeline.certified_pipeline_hop` also
requires `ActualSubscriberInput`: the retained binding belongs to the view,
has the exact subscriber port and has the correct caller owner. Its result
contains both the calculated delivery and the optimized same-mark route.

For the runtime, linearizable register-and-snapshot plus delta notification
implements these membership conditions. If registration occurs first, the
future delta finds it. If commit occurs first, the snapshot includes it.
At their boundary both may fire; receiving unions reject duplicates. There
is no order in which both are absent. Callbacks outside locks preserve this
argument while avoiding reentrant mutation under a directory lock.

The finite model checks each batch handoff. Fair servicing of repeated batches
and arbitrary coroutine interleavings needs the owner/reservation adapter;
the model does not claim an executable proof of all JVM schedules.

The exhaustive table's pair lists are proof-oracle state. Runtime directories
store references and use a field-first/SCC-second gate; they do not materialize
the proof model's Cartesian source/view list.

## 5. Same-field contraction preserves ordinary request endpoints

### 5.1 Exact relation

An ordinary reverse binding is an actual caller input dependency:

```text
callee entry slot -> caller original slot
```

It retains a complete descriptor/port/payload/support snapshot. Its nodes
contain full method keys and either Base or static-field slots. A binding is
contractible only when it is a certified covered same-field static relay.
Every such actual relay must induce a caller-to-callee edge in the fixed
prescan graph (`WorldValid`). The method graph can be lifted to full-owner
IDs by projecting those owners to their underlying methods. This does not
require a context-sensitive prescan graph.

An optimized path alternates:

```text
same-field fixed-CG coverage -> exact actual productive binding -> ...
```

Changed fields and every Base transition are productive. A field equality
alone is insufficient if the coverage certificate is absent. Such a row
stays in the fallback path.

### 5.2 Executable contraction

Induct over the finite ordinary path. For a productive first binding, retain
that exact binding and recursively translate its suffix. For a covered first
binding, its fixed-CG edge gives a coverage witness; prepend it to the
recursively translated suffix. Coverage concatenation uses reflexivity and
path concatenation, with no choice axiom or depth cutoff.

The executable function is `compress`; `compressRequestRoute` additionally
retains the mark. The proved equation is:

```text
productiveBindings(compress(path))
  = filter(noncontractible, ordinaryBindings(path))
```

This is stronger than endpoint-count equality. It preserves every retained
actual binding object, including its port, support and canonical descriptor.
`canonical_descriptors_pass_unchanged` follows by mapping that equation.

`every_base_endpoint_covered` returns an optimized witness for the exact
ordinary `(full owner, Base slot, mark)` endpoint. There is no reverse
equality claim for interests. The fixed graph may connect more source/view
pairs than the actual default input path.

### 5.3 Entry ancestry and recursion

Coverage alone cannot reach a different Base endpoint:
`no_base_endpoint_from_coverage_alone`. Thus a static-to-Base ordinary route
must contain a productive actual binding. `static_base_entry_ancestry`
constructs its first such binding, the preceding same-field CG coverage and
the optimized continuation. An unseeded same-field cycle supplies none.

This formal ancestry result concerns actual finite route witnesses. Runtime
view construction must retain/register their productive entries. Synthetic
abstract starts lacking producer coverage keep ordinary fallback service.
Ancestry need not be retained as a path object per fact.

Arbitrary finite cycles are covered because recursion follows the finite
derivation witness, not a topological method order. The prescan gate uses
reflexive reachability in recursive SCCs. In particular, a Base need under a
static original can reenter the directory and reach a view in that same
method. A blanket `original is Static -> skip` is unsound.

## 6. From requests to every ordinary summary

The summary model has independent typed atoms for facts, actual call inputs,
canonical initials, input presence, requests, summaries and observations.
Each payload retains full AP fields and support. Shared local and publication
clauses can require **all** of a finite list of facts/presence atoms. Call
application also requires all input members at the same compatible invocation
and the shared ordinary matcher/application kernel. Member-specific caller
layers remain distinct and may differ in a valid ND tuple; join them as in
the ordinary application, rather than requiring their equality.

The program's routing graph is a finite inventory, not an axiom that all
inventory inputs have arrived. An ordinary route must carry derivations of
the actual inputs used by its bindings. The optimized route needs those for
its retained productive bindings. The filter equation in §5 transfers those
input derivations. This prevents a circular proof that assumes the optimized
inputs or summaries it is supposed to derive.
For a runtime instantiation, `bindingInput` must identify that exact binding's
ordinary input, with its owners, canonical slot and complete port/payload.
An arbitrary already-derived row cannot stand in for another binding.

Construct the caller-owned derivation by structural recursion on the ordinary
derivation:

| Ordinary last step | Caller-owned construction |
| --- | --- |
| Seed | Keep the same seed and full payload |
| Actual call input | Translate its producing fact; use the identical call-stage producer |
| Input presence | Translate the actual input; preserve presence even with no initial |
| Unconditional initial | Translate its actual input; send/decode its identical canonical descriptor |
| Start | Translate the initial; use the identical relative start kernel |
| Local fact/request/event | Translate every required fact/presence member; use the same local clause |
| Backward request route | Translate the source need and used actual inputs; apply constructive contraction |
| Requested nonstatic initial | Translate the request and actual supplier; preserve actual-mark guard and canonical tag |
| Summary publication | Translate every required local fact/presence atom; publish the same full summary |
| Summary application | Translate the summary, all actual input members and invocation presence; retain invocation compatibility, each member's layer/support and all ordinary output channels |

Each recursive call uses a strictly smaller finite proof tree. No step assumes
whole-summary coverage. Nonreturning paths can still derive local needs and
observations because neither constructor requires an exit summary.

The summary conclusion is one-way no-loss for the ordinary calculus. The
optimized calculus can have additional conservative interest derivations.
It still cannot derive a concrete requested initial from a need alone: the
actual input/mark guard is required independently.

The checked public interface is:

```lean
def promote {p : Program} (valid : CallerOwnedRequestRouting.WorldValid p.world)
    {atom : Atom} (proof : Ordinary p atom) : Caller p atom

theorem default_summary_coverage {p : Program}
    (valid : CallerOwnedRequestRouting.WorldValid p.world) (summary : Summary) :
    Nonempty (Ordinary p (.summary summary)) ->
    Nonempty (Caller p (.summary summary))
```

`promote` returns the proof witness itself; the theorem is its set-membership
corollary. It also translates every fact/input/need/observation atom, not just
the summary projection of the closure.

## 7. ND, events, defaults and compaction

ND member truth is not shared by a request view. The summary calculus requires
every actual member of the same ordinary full-port application. Distinct
compatible original members can combine, as they do in the default; their
layers/premises are joined by the same kernel. Incompatible contexts, stages
or calls do not become compatible because their request marks were unioned.

The shared application kernel has outputs independent of surviving value
facts. In particular, an immediate excluded-target request or an observation
must survive when value application yields no fact. Local kill/no-return
events are similarly independent. Keeping only fact outputs would not prove
the statement in §1.

Presence is a separate atom. A default-publication clause can require reached
input presence and zero facts. It therefore publishes `S.* -> S.* [D]` for a
real input with an empty start batch. It has no normal-exit premise. The
runtime must initialize the analyzer, not merely allocate SummaryStorage.
The published edge has **one abstract static-root premise**. Having no fact
precondition for its publication does not make the summary a ZERO-premise
source edge. Applying it still matches an actual compatible static input.

Finally apply an identical semantic publication/identity-compaction predicate
to both summary derivation sets. Inclusion is preserved by that same filter:
retain the translated derivation and the same eligibility witness. This does
not prove an arbitrary new compaction algorithm. The root-default full guard,
STAR-to-ANY distinction and packed-store application coverage remain required.

## 8. Distinguishing controls and refinements

The models and review use cases that distinguish this design from simpler
incorrect variants:

| Case | Bug excluded |
| --- | --- |
| Changed original g/current f | Routing f/T to f rather than original g loses upstream concretization |
| Static original copied to Base and requested there | Cutting every static original loses Base-to-static reentry |
| Same-field cycle plus productive continuation | Acyclic-only traversal or premature visited cutoff misses the endpoint |
| Uncovered same-field binding | Field equality alone is not a coverage certificate |
| Source/view and subscriber/input in both orders | One-way notifications lose late registrations |
| Four sources share T and four inputs at one view | Source×original fan-out is merged before the input join |
| Nonstatic concrete STAR start | Starting it NORMAL changes the selected relative layer |
| Canonical static STAR token | Retagging it ANY or treating its ANY result as identity changes summaries |
| Requested concrete start without actual T | An interest cannot supply a mark |
| Empty initial batch | Input presence/default publication cannot depend on an emitted initial |
| Kill/no return | Requests/observations cannot wait for surviving facts or a normal summary |
| Complete invocation ND tuple, including mixed layers, versus split calls | A shared request set cannot replace actual conjunction support |

These controls refined the model before runtime implementation. They do
not replace project regression tests, source-adapter tests or measurements.

## 9. Constructivity and validation

The new audit entry points are
[routing audit](lean/caller-owned-request-routing-audit.lean) and
[summary audit](lean/caller-owned-summary-coverage-audit.lean). They enumerate
module-owned declarations and reject axiom dependencies other than Lean's
`propext` and `Quot.sound`. In particular `Classical.choice`, `sorryAx` and
new unproved axioms are rejected. Witness-producing functions construct finite
paths and derivations directly.

```bash
cd spec/lean
lake build ApSpec.CallerOwnedRequestRouting ApSpec.CallerOwnedSummaryCoverage
lake env lean caller-owned-request-routing-audit.lean
lake env lean caller-owned-summary-coverage-audit.lean
```

The routing build and focused audit passed: **532 declarations, zero
unexpected axioms, 10 executable controls**. The summary build and focused
audit also passed: **1,224 declarations, zero unexpected axioms, 27 executable
controls**. The combined checks cover **1,756 module-owned declarations and
37 controls**; imported dependencies are checked through each declaration's
axiom dependency closure. The source-bound receipts and logs are under
`/drive-testcomp/opentaint-go-rules/static-propagation-regression-20261018/caller-spec/`.

| Obligation | Checked definition/theorem |
| --- | --- |
| Direct static descriptor and batch round trip | `CallerOwnedSummaryCoverage.decode_encode`, `batch_decode_encode`, `descriptor_preserves_full_initial` |
| Same full Base endpoint and mark | `CallerOwnedRequestRouting.every_base_endpoint_covered` |
| Exact retained productive inputs | `compression_preserves_exact_productive_bindings`, `canonical_descriptors_pass_unchanged` |
| Constructive first productive ancestry | `static_base_entry_ancestry`, `no_base_endpoint_from_coverage_alone` |
| No lost source/view/callsite/input pair | `Pipeline.source_view_callsite_input_coverage`, `Pipeline.certified_pipeline_hop` |
| One semantic request/input pair at either frontier | `caller_view_both_arrival_orders`, `caller_view_no_pair_twice` |
| Actual T required for a requested start | `requested_start_has_actual_mark`, `requested_answers_exact` |
| Complete compatible ND bundle can be considered | `complete_bundle_enumerated`, `complete_nary_arity`, `exact_nary_port_guard` |
| Old/new summary and complete input bundle orders | `summary_answers_exact` |
| Every ordinary finite analysis derivation | `promote`, `finite_recursive_closure_coverage` |
| No missed raw or identically selected published summary | `default_summary_coverage`, `same_compaction_visible_coverage` |
| All semantic output atom kinds | `every_default_observation_covered` |

## 10. Remaining implementation refinements

The formal proof is conditional on these explicit primitive/adapter contracts,
rather than on the desired whole-summary result:

```text
producer:
  every actual call stage, local effect and need has its ordinary typed rule
  each omitted binding has valid same-field prescan coverage
  every productive view/port refers to the retained actual input relation
  bindingInput preserves its binding's owners, slots and full descriptor

admission and codec:
  canonical descriptors decode to the ordinary selected initial
  admitted input deltas preserve headers, marks and complete ND support
  requested concrete starts require actual matching T
  input presence initializes an actor even when starts are empty

ordinary kernel and representation:
  retain every value/request/observation channel, including application kills
  retain the full ordinary ND/call matcher, relative layers and start grammar
  packed union/subsumption represents every covered semantic application
  use the same full identity/publication guard in the comparison

schedule and lifetime:
  register before replay; union fresh deltas under each owner
  reserve descendant work before parent release; final drains are counted
  fair finite handoffs reach source, view, caller, callee and summary owners
  keep active services for late inputs/answers until run teardown
```

The existing finite accounting lemmas in
[BottomUpStaticTransport](lean/ApSpec/BottomUpStaticTransport.lean), including
the enqueue-before-release/no-early-completion laws, give an arithmetic oracle.
They do not prove the new coroutine adapter automatically.

For a nonstatic concrete input with base `b`, an eligible request must
have the same base. Every StaticField slot has base STATIC, which differs
from `b`. Therefore removing StaticField rows before concrete matching
preserves exactly the eligible rows and their mark sets. Base rows for
other arguments are also rejected by base equality. Intersecting the
remaining requests with actual marks preserves the same legal initial
tags and cannot manufacture a requested mark. This reasoning applies at
both P/A frontiers. It does not remove static rows from the shared batch
or from productive FLOW joins. The concrete visitor's former whole-batch
Base assertion did not follow from this lemma and failed on conductor.
The mixed-batch regressions check that runtime refinement directly; the
formal models and their checked declarations remain unchanged.

No universal work/heap saving follows from functional inclusion. Coarse
reachability can add requested actual starts; overlapping views and cache
memberships cost space. The scoped relay-join comparison in the spec must be
measured against ordinary incremental propagation, including its frozen
coverage where applicable. This dossier makes no 60-second completion claim.
