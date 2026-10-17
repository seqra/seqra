# Constructive proofs for run-1 concrete static propagation

Date: 17 October 2026. Scope: the proposed
[concrete-static contract](run1-bottom-up-concrete-static-propagation.md),
run-1 FORWARD, field limit 1. The analyzer is unchanged. This dossier follows
[proof-guide.md](proof-guide.md): define the ordinary concept, model the
optimization separately, construct witnesses in both directions, check
counterexamples, and state the remaining refinement obligations.

The [validation report](../docs/reports/bidirectional-run1-bottom-up-concrete-static-proof-validation-20261017.md)
records exact commands, checked sources and the axiom audit. Only `propext`
and `Quot.sound` are allowed. No classical choice, admitted proof or new axiom
is accepted.

Latest existing measured reached/completed iterations: OWASP **run-7/run-6**,
conductor **run-2/run-1**, ThingsBoard **run-1/none**, Stirling-PDF
**run-2/run-1**, at runtime `ab6c3cd00`. They are not measurements of this
proposal; see the [project report](../docs/reports/bidirectional-run1-bottom-up-static-20261017.md).

## 1. Objects and boundary

The two new modules are:

| Module | Checked responsibility |
| --- | --- |
| [BottomUpConcreteStatic](lean/ApSpec/BottomUpConcreteStatic.lean) | Actual suppliers, uniform static DEMAND, exact full-input codec, scalar concrete effects, ordinary/contracted transport, identity containment and chain counterexample |
| [BottomUpConcreteStaticNeeds](lean/ApSpec/BottomUpConcreteStaticNeeds.lean) | Actual FLOW route compiler, source mark union, BaseSlot boundary/reentry, expanded/fast closure equality, mark batching and snapshot replay |

The foundation remains the executable scalar AP calculus, the nine-constructor
`BottomUpStaticDomain.Initial`, layer-only summary application and the selected
`Run1StrongPreservation` operator. Reuse the finite graph/path portion of
`BottomUpStaticRequests`; its **old frozen B plan is not the new policy**.
Likewise, historical `Run1Requests.staticAbstractStarts` is not this proposal's
initial emitter. The selected contract never emits the S-root initial.

The model's `Input` contains a full port, whole original initial list, complete
current fact/layer, an ordinary-effect flag and immutable support IDs. The
carrier codec must reconstruct all of these. A route contains the full port,
abstract original slot and current bounded scalar FLOW geometry, including
its E/mx annotations. Reachability of that input is a prerequisite supplied
by the frontend, not inferred from the prescan call graph.

Graph/path certificates are data in `Type`. `compress`, `expand`, the carrier
decoder and `inheritedPath` compute witnesses. Logical existential statements
below are accompanied by these executable constructions; no witness is chosen
from a classical existence proof. Proof-only histories and finite-set oracles
are not proposed retained runtime state.

## 2. Legal supply without static requests

**Claim.** Concrete field admission requires actual T and matching geometry,
but no static request. It emits a legal EXACT or STAR initial with that T.

`supplyField` removes the request argument and computes the ordinary scalar
projection. `supply_field_is_actual_projection` establishes exact equality.
`supply_field_exists_exactly` proves both geometry directions.
`supply_field_legal_and_actual` gives legality and mark preservation for every
successful result. `supply_field_ignores_E` proves the stated projection rule.
The returned `Option PFact` is the executable admission witness.

`exact_field_supply` and `chain_field_supply` distinguish exact and nonexact
answers. The latter chooses STAR even when supplied data has ANY/must.
`direct_class_supply`, `supply_class_legal` and `supply_class_actual_mark` cover
the separate class projector. `class_exact_does_not_fabricate_field` excludes
an exact class ancestor as field evidence. A need is not an input to any of
these projectors, so it cannot supply a concrete mark.

The underlying `Initial` has exactly nine constructors. Its executable
classifier and `BottomUpStaticDomain.classifier_exact` establish the closed
grammar, including Empty E/mx and the forbidden ANY/root/class-abstract shapes.
All new starts use this same type. Enumeration of every actual legal JVM
coordinate/class point remains a frontend completeness obligation.

## 3. Uniform static DEMAND, effects and writes

`normalize` joins the layer bit with static original/current tests.
`normalization_full_payload` proves that port, entire premise, fact, effect
flag and support do not change. `normalization_idempotent` permits repeated
admission checks. `static_original_demand` and `static_current_demand` cover
both contact cases. `static_history_stays_demand` proves the sticky join after
a later operation moves to a nonstatic base.

`exact_static_start_D` proves exact static TAINT starts with its **same exact
payload** at DEMAND. `chain_static_start_ANY_D` proves that a STAR concrete
initial starts an ANY current fact at DEMAND; the initial itself stays STAR.
`exact_class_start_D` and `chain_class_start_ANY_D` prove the class cases.
`independent_nonstatic_exact_start_N` preserves the independent nonstatic
NORMAL start. Early normalization is part of the scalar evaluator, not a
postprocessing label change.

The evaluator's write primitive executes `Run1StrongPreservation.preserve`.
Its prior proofs establish: a definitely overwritten old component has no
survivor even at DEMAND; a disjoint component is unchanged; an
exclusion-requiring survivor preserves payload with Empty E and DEMAND.
Pre-store regenerated outputs are separate ordinary effects. The concrete
controls exercise a killed static input; no routing/identity rule revives it.

`actual_check_no_request` uses the actual matching concrete mark and emits an
observation with the full normalized input. `concrete_clean_kills` executes
the exact-point matching cleaner. Partial broad cleaners require ordinary
fallback (`broad_clean_requires_fallback`); unknown instructions also require
fallback. This avoids treating mere spatial overlap as a full concrete kill.
The model does not claim to compile every JVM/NNF/ND operation into these few
scalar instructions.

## 4. Carrier correctness and transparent transport

`encode` packs only an exact equality with the canonical decoded field input.
It keeps EXACT and STAR tags separate. A change of mark, field, current tail,
support or effect status prevents this equality. Multi-premise inputs are not
packed as singleton carriers.

`decode_encode` is proved by constructor/decidable-equality cases.
`interpreter_preserved` rewrites that equality for any ordinary client;
`encoded_inputs_exact` extends it to input lists. `effect_stays_bound`,
`nary_stays_bound` and `nonidentity_exact_stays_bound` prove the fallback cases.
`same_port_nary_codec` establishes equality of the actual whole-premise join
after decoding. Full caller/callee entry/context, statement and stage are
therefore preserved; no cross-call truth pooling is licensed.

The scalar `checkPrimitive` executes the operation and accepts precisely one
unchanged full input with no events. `checked_primitive_exact` proves this law.
`check_never_transparent` and `unknown_never_transparent` reject those effects.
`outcomes` has a separate event-only branch;
`events_survive_without_facts` proves that killing the last fact cannot erase
an already emitted event.

The transport grammars are independent: ordinary `Forward` visits every
relay, whereas `BottomUp` stores a finite relay segment before each real
effect and after the last effect. `compress` constructs the latter from the
former; `expand` walks every relay certificate back into ordinary steps.
Both execute the same scalar kernel at the same effect boundary, with its
same payload and observations. They cannot skip a cleaner, kill or event.

`concrete_transport_equivalence` instantiates that two-direction proof.
`concrete_nary_equivalence` lifts it to finite mutual/ND derivations.
`concrete_finite_round_closure` supports snapshot-dependent discovery through
the shared positive round constructors. These are finite derivation/round
results, not a proof that arbitrary engine work terminates. The model's
executed read-to-local/check witness is compressed and expanded as data.

## 5. Need routing and the bootstrap

The retained request type `BaseRequest` has only owner/base/mark;
`only_base_request` proves its ordinary Question is a BaseSlot. The concrete
module's `request_has_base_origin`, `base_request_even_current_static` and
`no_static_origin_request` establish the ordinary-request partition.
Static-origin interests go to the separate source/router, not that function's
ordinary R/P output. Concrete premises produce no such requests.
The need module's `produce` computes the complete partition;
`producer_base`, `producer_static`, `producer_concrete_none` and
`producer_zero_none` prove it independently of current geometry or old B.

The need graph compiler uses `Incoming.flow` bindings. Concrete, ZERO and
killed inputs have no route by their separate constructors. `routes` checks
the caller's original node, full callee owner and current geometry. It
retains the call port in the binding rather than using an unqualified method
edge. `compiled_edge_exact` gives an actual member input for each compiled
edge and compiles every matching member input. `route_original`,
`route_callee` and `route_base_owner` prove origin and owner preservation.
`compiled_base_is_endpoint` proves Base nodes cannot be intermediate static
sources in a compiled route.

The request-side geometric predicate ignores E/mx
(`matching_ignores_annotations`). A static g-to-f input therefore routes f's
need to g even if T is in mx. This is **interest coverage**, not a theorem
that T survives. `base_request_reenters_static` computes the reverse join
when an ordinary BaseSlot need meets a static-origin FLOW input.

`register` unions marks at their source; `registered_marks` proves exact
membership. It does not inspect old inherited B
(`no_catalog_suppression`). `unchanged_canonical_producer_registers` prevents
the former unchanged-origin optimization from dropping the bootstrap need.
Source IDs in the generic model can distinguish events; production unions
events at the same full owner/original field. `same_origin_union` proves
that merging two such sources into one changes no Base request membership.
`source_union_distributes` proves the corresponding per-source batch law.
Actual rule-operation contributor completeness is still an adapter obligation.

For a finite graph, the reference `ordinary` uses `Run1Requests.closure`, an
independent exhaustive finite-set least-closure oracle. The optimized witness
search uses the executable finite visited/path construction.
`closure_iff_path` proves both directions: the path-closed discovered set
contains the least closure, and every discovered path is ordinary reachable.
`fastNodes_eq_closure` proves literal equality of the canonical node lists,
and `fast_eq_ordinary` proves literal equality of the resulting Base request
lists. `fast_matches_expanded_requests` exposes the membership form.
`fast_witness` gives source/node/path membership;
`inheritedPath` returns the executable path itself. There is no appeal to an
assumed global request-coverage invariant.

`no_invented_mark` proves every emitted mark came from a source batch.
`batch_distributes_to_scalar` equates batch membership with the scalar
reference; `source_union_distributes` proves union compatibility, and
`fast_nodup` proves final output deduplication. The controls compile the user's
W input and execute its route to the upstream Base request, as well as a
cross-field route and a cycle.

This proves the routing half of the bootstrap. Actual T must then arrive from
ordinary nonstatic concretization. Section 2 supplies that actual T to the
callee; §4 preserves its concrete effect application. No theorem turns the
interest record into a concrete fact or a successful check.

## 6. Incremental arrival, recursion and barriers

`fast_mono` proves graph/source growth cannot remove a reachable Base need.
`duplicate_source_membership` makes repeated source batches harmless.
`graphGrowth` and `sourceGrowth` commute (`both_arrival_orders`), and
`recompute` evaluates the complete new snapshot. This exactly models the
specified correctness-first fresh replay on graph growth. Controls exercise
source-before-edge, edge-before-source and a late mark. A permanently cached
visited bit is deliberately absent.

`callClosure` terminates by erasing a remaining finite vertex for each new
visit; returned certificates witness real finite paths, including cycles.
It has no arbitrary depth/fuel cutoff that can truncate a required route.
The wrapper computes one shared closure per source and then enumerates its
members canonically. The exhaustive ordinary closure remains a proof oracle.
The prescribed runtime uses one visited traversal per source batch. No
linear-time implementation bound is asserted for the Lean list searches.

For actual inputs/effects, `concrete_both_arrival_orders` instantiates the
two-sided join theorem: new inputs replay existing effects and new effects
replay existing inputs. Existing transport work-token lemmas establish the
abstract counted-work invariant when child reservation precedes parent
release. Refining owner queues, temporary commits, cancellation and all
pending route/actual-replay work into that invariant is required of runtime
implementation. An empty local method queue alone does not prove global
completion.

## 7. Compaction, persistence and the counterexample

`exact_summary_contained_in_root` proves denotational containment of a same
exact field/T identity under the correlated root default, for arbitrary
before/after concrete locations. `exact_compaction_full_guard` establishes
the complete singleton/exact/D payload test. `compactable_pair_covered`
combines them. `normal_not_compactable` excludes a NORMAL contribution;
`nary_not_compactable` and the ordinary-only/support tests keep dependent
effects bound. Pure abstract identity containment remains its existing proof.

The constructive counterexample `chain_summary_not_root_identity` supplies
distinct suffixes a and b. A concrete field STAR-to-ANY summary relates the
two, but root identity requires them equal. `chain_not_compactable` therefore
rejects that proposed shortcut. This is why a returning read-free method can
still have a required concrete static effect. The first version keeps class
effects ordinary rather than extending the field compaction proof by analogy.

Ordinary persistence filters by NORMAL/eligibility, not by run number.
`BottomUpStaticProtocol.normal_persistence` and `demand_not_persisted` specify
that filter. Every newly removable concrete identity is D
(`compacted_exact_is_D`), so removing it cannot remove a NORMAL persisted row.
All static-dependent results stay D by §3. The remaining normal nonstatic rows
can be persisted as usual. The new module's
`concrete_compaction_preserves_normal_persistence` proves literal equality
of the NORMAL filter before and after the concrete exact compaction.
Driver handoff and selected-demand construction
still need runtime refinement; no new static handoff algorithm is specified.

## 8. Complexity and composition limits

`actual_concrete_chain_reduction` counts layouts with actual full concrete
bindings: `(h+1)*m` ordinary slots versus `h+2*m` shared-route/endpoint slots.
It gives strict inequality for h at least 3 and m at least 2. This is a
practical transparent-chain family, not a byte/time measurement or a universal
asymptotic claim for every call graph. W/effect/ND bindings remain full.

Source-only need storage avoids retaining every source mark at each static
relay. `expanded_need_rows_length` counts h*m cells and `source_once_slots`
counts m+h cells in the declared source-once layout.
`need_chain_storage_strict` proves strict improvement for h at least 3 and
m at least 2. Required Base endpoints still retain ordinary requests. Replaying all
sources on every new graph edge can be expensive; it is the selected simple
correctness baseline, not a measured optimized scheduler.

The checked pieces compose under explicit interfaces: actual suppliers feed
the same projector; actual FLOW input bindings generate the same routes;
local primitives produce the same source needs; Base endpoints invoke the
same nonstatic mechanism; transparent edges pass the checked identity test;
effects/whole ND execute the same full-port kernel. Induction over finite
positive discovery rounds then replaces each reference request derivation by
its route witness and each fact derivation by its compressed witness; expand
in the reverse direction. The generic finite-round model covers the shared
constructor case. There is **no checked theorem for the complete coupled
Kotlin request/fact scheduler** in these modules.

Implementation must discharge these remaining adapters:

1. Enumerate every legal supplied coordinate, actual post-binding FLOW input
   and local interest contributor, including sources, sinks, cleaners/models,
   transformed origins and event-only results. Label every reachable full
   owner/slot in the finite graph. Do not infer inputs from ZERO/call reach.
2. Compile relay/effect boundaries and full AP/NNF/ND operations faithfully;
   preserve early/static-history DEMAND and all actual supports. No blanket
   read-free shortcut or field-name-only identity test is valid.
3. Implement mark-independent sharing without copied caller histories, and
   fresh replay for late graph/source growth. Preserve all ordinary BaseSlot
   joins and static reentry. Keep activation distinct from actual T.
4. Refine packed stores, summary/default application, observation readers,
   concurrency, barrier work accounting and later-run persistence/handoff.

These are finite implementation/test obligations before adoption. The model
establishes soundness/completeness of its scalar and finite-route fragments
relative to the explicitly defined ordinary concept. It does not prove
whole-JVM analysis correctness, arbitrary workload termination or the
60-second run-1 engine target.
