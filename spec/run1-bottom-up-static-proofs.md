# Constructive proofs for run-1 bottom-up static FLOW

Date: 17 October 2026. Scope: the selected
[static FLOW contract](run1-bottom-up-static-propagation.md), run-1 FORWARD,
field limit 1. The Lean model and its proof receipt are unchanged by the runtime
implementation. The [runtime refinement](../docs/design/bidirectional-bottom-up-static-implementation-20261017.md)
records the adapter implementation and finite test boundary.

This dossier follows [proof-guide.md](proof-guide.md). It distinguishes the
ordinary concept, the transport optimization, their constructive equivalence,
and the remaining implementation refinements. The
[validation report](../docs/reports/bidirectional-run1-bottom-up-static-proof-validation-20261017.md)
binds the checked sources and commands. Only `propext` and `Quot.sound` are
permitted; classical choice, admitted proofs and new axioms are forbidden.

Latest measured reached/completed/accepted iterations remain: OWASP
**run-7 / run-6 / run-5**, conductor **run-2 / run-1 / run-1**, ThingsBoard
**run-1 / none / none**, Stirling-PDF **run-2 / run-1 / run-1**. These are
measurements of `28a123f0e`, not of this design or its formal model.

## 1. Formal objects and exact scope

The checked model consists of six modules:

| Module | Objects and checked responsibility |
| --- | --- |
| [BottomUpStaticDomain](lean/ApSpec/BottomUpStaticDomain.lean) | Nine initial constructors, executable classifier/projectors, original-slot requests, sticky layers, selective writes, defaults and exact compaction predicate |
| [BottomUpStaticApplication](lean/ApSpec/BottomUpStaticApplication.lean) | Executed scalar primitives, conservative relay checker, full-result sequencing and arbitrary-supplier identity containment |
| [BottomUpStaticProtocol](lean/ApSpec/BottomUpStaticProtocol.lean) | Lossless call-input codec, whole-premise combinations, call/context isolation, persistence and diagnostic counterexample |
| [BottomUpStaticTransport](lean/ApSpec/BottomUpStaticTransport.lean) | Independent ordinary/contracted derivations, executable compression/expansion, recursion, mutual clauses, incremental joins, work accounting and a concrete cost family |
| [BottomUpStaticRequests](lean/ApSpec/BottomUpStaticRequests.lean) | Executable resolved-graph paths, symbolic frozen mark lookup, leastness, inheritance and no duplicate precomputed delivery |
| [BottomUpStaticModel](lean/ApSpec/BottomUpStaticModel.lean) | Scalar AP/event instantiation of transport, including requests emitted by a fact-killing operation |

The foundation is the existing executable scalar AP calculus and selected
layer-only/strong-preservation operators named in specification §0. This is
not a new claim that the entire analyzer implements that calculus. In
particular, historical demand-to-ANY and blanket weak-write modules are not
the selected local-write semantics.

A semantic input includes the entire original premise, AP payload, layer and
full call port. A port includes caller/target MethodKeys, statement and stage.
The model never identifies two inputs solely by a field or callee method.
Concrete ND support is retained as a whole list. An observation retains its
occurrence and actual original/current support.

`Forward` visits each ordinary edge. `BottomUp` has a different grammar: a
finite relay segment followed by a real effect, repeated, then a final relay
segment. Effects retain boundary, before/after payload and events. Both can
branch, kill, change marks, return or throw through their effect kernel.
Only a certified relay is identity and event-free.

Path and mutual-derivation certificates live in `Type`. `compress`, `expand`,
`Derivation.map`, `classify`, `inheritedPath` and `lookupWitness` are executable
functions returning actual data/witnesses. Theorems expressed as `Nonempty`
or logical membership are accompanied by these functions; no witness is
extracted using classical choice. Round closure is a declarative lifting of
those finite derivations, not an executable concurrent scheduler.

## 2. Domain closure and actual supply

**Claim.** Every admitted initial has exactly one of the nine shapes in
specification §3. No initial has ANY/must, a nonempty field E or FLOW mx, an
abstract static root/class, a concrete empty static root, a nonstatic field
path or a second field accessor.

**Construction and proof.** `Initial` has nine constructors. `encode` defines
their raw shapes. `classify` matches the complete raw shape, including tail,
mark and Empty E, and returns the constructor together with an equality
certificate. Case analysis proves `classify_encode` and `classifier_exact`;
the forbidden-shape results follow by eliminating those nine cases. The
nonstatic base encoding is disjoint from STATIC/ZERO.

`projectAbstract` tests supplied geometry and returns the slot's canonical
abstract initial. It has no field-request/local-reader argument. Thus removal
of F cannot reject an otherwise admitted field. `projectConcrete` additionally
requires an actual concrete mark and matching requested mark. It chooses
EXACT at an exact supplied point and STAR otherwise. `abstract_project_legal`,
`concrete_project_legal`, `concrete_answer_requires_actual_and_request` and
`no_request_no_concrete` establish both constructor legality and the actual
evidence requirement. FLOW/activation cannot fabricate T.

The ordinary direct-class projector is separate; `class_answer_legal`
establishes its shape. An exact class ancestor does not supply a field
(`exact_class_not_field`). Incoming E does not affect request projection
(`projection_ignores_E`). A concrete STAR initial can start an ANY DEMAND
*current fact* (`concrete_STAR_start_ANY_D`); that is not an ANY initial.

The production collector must still supply the complete finite legal
coordinate/class inventory. The model does not infer it from JVM forms.

## 3. Layers, strong kills and transparent sequences

**Claim.** Static-related FLOW is DEMAND before any layer-sensitive operation;
it stays DEMAND after leaving STATIC. Demotion preserves all AP information.
It does not defeat a definite overwrite.

**Proof.** `demote` joins the current bit with static original/current tests
and copies every payload field. `advance` additionally joins the operation
bit. `demotion_keeps_full_payload`, `static_origin_demand`,
`static_current_demand`, `advance_sticky` and `static_to_local_stays_demand`
prove the claims by Boolean case analysis. Concrete exact TAINT remains on
its ordinary path (`concrete_exact_not_blanket_demoted`).

Writes use the selected `Run1StrongPreservation` operator. Its classifier
distinguishes a fully overwritten region, a disjoint component and a surviving
ancestor that would need an exclusion. A fully overwritten old f has no
survivor, including at DEMAND (`overwritten_demand_is_killed`). A disjoint g
is unchanged. An exclusion-requiring survivor retains its payload with Empty
E and a DEMAND layer. RHS values/events were computed before the store and
are appended independently. Therefore f=f may regenerate the killed old
component (`self_assignment_uses_pre_store_value`). No later call/default
recreates a missing local branch.

The relay checker executes each supported primitive and accepts exactly one
unchanged full state with no requests/observations. It rejects an unknown
operation rather than guessing. `checked_primitive_exact` derives this law
from evaluation, and `checked_relay_executes_identity` inducts over the actual
instruction sequence. Its induction uses the exact unchanged state at the
next instruction. `checked_relay_has_no_events` proves event emptiness.

This is stronger than an absent GETSTATIC or a may-write bit. Disjoint writes,
a valid self-assignment and an ordinary root default on a canonical DEMAND
lane are certifiable; killing writes, observable actions and matched mark
cleaners are not. Even an idempotent cleaner must emit its immediate request.
`BottomUpStaticModel.checked_selected_kernel_identity` connects accepted
primitives at a normalized input to the actual scalar transport kernel.
Its `selectedKernel` normalizes static FLOW before evaluation and again before
output admission, preserving original/payload and an already DEMAND input.
This covers
an ordinary AP effect that first moves a nonstatic NORMAL FLOW to STATIC;
no later guard can inspect it as NORMAL. Concrete origins remain ordinary.
Pre-store values and observation supports must already be results of ordinary
evaluation under this policy, with their own original support and layer.
Opaque fallback and ND clients must preserve dependent DEMAND while allowing
independent derivations their own layer; normalizing current geometry alone
cannot recover a lost earlier static contact. These are explicit adapter
contracts, not consequences of arbitrary fallback code. Actual CFG/alias/model
lowering must establish the checker input; no theorem assumes whole-engine
equivalence.

Both procedures install this demotion before guards. The executable
`early_demotion_changes_guard` witness shows why post-hoc relabeling of an
old NORMAL execution is a different oracle.

## 4. Exact bindings and flow sensitivity

**Claim.** The compact representation supplies precisely the same call-stage
input to every ordinary operation, including request/source/sink clients.

**Construction and proof.** `encode` replaces only an input whose original is
the abstract static field and whose entire current value is that field's
canonical DEMAND value. Its token retains the full port plus class/field.
Every transformed, concrete, ND or nonstatic input stays fully bound. `decode`
reconstructs the canonical value and original slot. Case analysis proves
`decode_encode`; no reachability or transfer equality is assumed.

Rewriting by that equality proves `interpreter_preserved` for *any* ordinary
client, and `encoded_inputs_exact` for a list of inputs. Port, original
support and payload are therefore preserved, including before/after actions
and continuation selection. A token is not evidence that an input reached
its port: it may be created only from an already derived ordinary input or a
checked relay reconstruction. Activation is a separate scheduling fact.

For `C.f=constant; return readF()`, the local preservation rule produces no
old-f input at the readF call; therefore neither encoding nor bottom-up
delivery can produce an old-f return. In the reversed order, f can produce x
before the write and x survives. A DEMAND root default crosses an *actual*
call input; it is not a local overwrite bypass. No method-wide static pool,
ZERO reseed or speculative callee entry is introduced.

## 5. Transport equivalence in both directions

**Claim.** The two finite procedures derive the same full results and event
records, including cycles, call stages and normal/exceptional boundaries.

**Ordinary to bottom-up.** Induct on `Forward`. A relay is prefixed to the
next relay certificate. An effect becomes a bottom-up effect with an empty
prefix; its exact membership witness, kernel result and event list are
copied. The empty path becomes an empty final relay segment. This defines
`compress`, not just a logical existence argument.

**Bottom-up to ordinary.** `expand` walks each stored relay certificate and
then executes the same effect witness; recurse on the remaining effects.
Every reconstructed small step belongs to the original graph and kernel.
It cannot add an input after a kill or apply an effect at a different port.

`path_equivalence` follows. `compressWitness` preserves its target by
construction; `complete_result_preserved`, `full_payload_preserved` and
`boundary_and_observation_records_preserved` give equality of full payload
and ordered effect history. `recursive_finite_path_equality` and
`least_closure` establish finite-path least reachability without assuming an
acyclic graph or one topological pass.

This is instantiated with the scalar AP evaluator in
`BottomUpStaticModel.scalar_path_equivalence`. A scalar primitive can emit a
request while killing its last fact. `outcomes` therefore has a separate
event-only branch; `facts_outcomes` and `notices_outcomes` prove that neither
fact outputs nor emitted events are lost. A notice cannot propagate as an
actual TAINT fact. Unknown primitives remain ordinary effect boundaries with
the same opaque fallback on both sides.

## 6. Whole ND, mutual discovery and observation equality

**Claim.** Sharing transport does not split ND, mix calls, change actual rule
evaluation or lose recursively discovered work.

`products` constructs combinations containing one actual input per literal.
`mem_products_iff` proves the exact characterization; `product_has_all_members`
proves full arity and `empty_member_no_combination` rules out a missing-member
application. `matchedRows` requires the entire port and the unchanged
ordinary literal matcher. `rows_scope`, `joined_arity` and
`joined_codec_equal` prove call isolation and identical combinations after
decoding. This preserves ordinary premise support; it does not introduce
per-member implications for `f/T AND g/U`.

`Derivation` additionally models mutual input/summary/request/observation
production as finite proof trees. A rule constructor requires derivations of
*all* its listed premises. `Derivation.map` recursively compresses or expands
every transport branch while copying the complete rule. Thus
`mutual_finite_derivation_equivalence` is not limited to a pre-existing fixed
summary set. The scalar model instantiates it in `scalar_nary_equivalence`.

`RoundProgram` can recompute graph, kernel, seeds and full clauses from each
current snapshot. `snapshot_round_equivalence` is derived from the finite
proof-tree conversions. Induction gives `snapshot_iterates_equal`, hence
`finite_round_closure_equal`. `PositiveProgram` states the local edge/output/
seed/clause extension laws; these yield `positive_program_monotone` and
`positive_bottom_up_least`. It does not assume global equivalence as a premise.

Projecting equal reached states gives `observable_projection_equality`.
Occurrence, actual support and per-derivation action order remain in the
payload/history. Complete ordinary NNF evaluation is retained as an effect
client; the codec passes it identical actual inputs. The model does not
re-prove that the runtime NNF/ND compiler implements those ordinary clients.
In particular, lazy absence cannot justify a negative mark test.

The user selected semantic observation equality, allowing batch-dependent
diagnostics to vary. `diagnostic_final_cells_equal` and
`diagnostic_counts_differ` construct a distinguishing witness: one update
`[1,2]` and two updates `[1]`, `[2]` have the same final cells and callback
counts 1 versus 2. This includes `ExternalMethodRecord.callSites`. Findings,
source/sink identities and rule occurrences remain in the equality target.

## 7. Identity compaction and the universal default

**Claim.** Removing only canonical static field identities preserves the
summary relation with its mandatory DEMAND root default, including arbitrary
actual suppliers. It removes no NORMAL record or nonidentity effect.

`compactable_exact` characterizes the complete predicate. It includes origin,
base/path, STAR, empty mx/E and DEMAND, not just equal field names. The stored
root default is independent of normal exit and is not an S-root initial.
On a canonical field input, `discarded_identity_supplied_by_default` uses the
ordinary scalar root application and returns exactly the discarded value.

For a broad supplier, literal result-list equality is false; an executable
counterexample is `broad_results_are_not_literal_AP_equality`. The correct
proof uses the scalar location relation `den`. A field identity preserves
its location and concrete mark. The root identity contains that same pair
(`field_identity_contained_in_root`). Given an actual supplier witness, reuse
its intermediate location to derive
`arbitrary_supplier_field_identity_contained`. No new supplier is chosen.

The summary relation is the union of root default and stored field effects.
For each removed member its pair is already in the root branch; all retained
members are unchanged. This proves `compaction_preserves_full_relation` in
both directions. Reusing the actual supplier and joining the same layer bits
proves `compaction_preserves_arbitrary_supplier`. NORMAL TAINT, ND and changed
mark/tail/location effects do not satisfy the deletion predicate.

Combining this law with transport equality proves the required equality of
compacted nonidentity relations in the declared model. This is equality with
full support/layer, not merely coverage of true flows. An implementation that
compacts during packed evaluation must preserve this semantic quotient and
the exact nonidentity/observation clients. The theorem is not a license to
treat arbitrary broad AP lists as interchangeable literal rows.

## 8. Frozen and dynamic mark coverage without field requests

**Claim.** Every legal statically known static mark need is available by
borrowed lookup; unchanged static origins need no duplicate delivery. Every
remaining abstract-origin need follows the ordinary dynamic path.

`Scope` has root/class/field forms and matches static field slots only.
`inheritedPath` computes caller-to-declaration paths on the supplied finite
resolved graph. Its reverse saturation records a `CallPath` for each newly
reached owner and terminates by decreasing the remaining owner list. Direct
induction proves path soundness/completeness (`inheritedPath_exact`); recursive
SCCs need no guessed depth bound.

`compileDescriptors` stores symbolic scope/mark contributors with those
paths, independently of fields. `lookupWitness` searches a legal actual slot
and returns declaration, scope match and path data. `borrowMarks_exact`
proves exactly the B equation in specification §4. `borrowMarks_least` proves
the least inherited relation; `borrowMarks_nodup` handles overlapping root,
class and field contributors. Root/class needs do not become forbidden
root/class initial/request slots. `legalSlots` is the accepted coordinate
domain, not a reintroduced F/read set.

Prepending a real call edge proves `same_slot_frozen_inherits`. Borrowed B
rows are immutable shared base rows. `shared_row_initialise_no_copies` and
`same_slot_no_precomputed_delivery` instantiate the existing lifecycle:
effective read is B union committed dynamic marks, pending offers subtract B
and committed marks, and commit replaces temporary storage. B is never
copied into dynamic/pending stores or sent as a startup delta. The reused
`Run1PrecomputedRequests` trace laws prove this after every collect/commit.

For f<-g, a check of f/T routes to the actual original g slot
(`request_original_field`, `changed_origin_not_checked`). Inherited f/T is
not evidence for g/T. `changed_origin_collects_fresh` constructs the fresh
dynamic notification from a newly bound row when g/T is absent from its B.
For existing D/P, the reused collect/commit laws deliver only the still-fresh
residual. Base origins use their BaseSlot.
Concrete origins have no upstream request (`concrete_origins_do_not_request`).

This exhausts abstract origins because §2 has exactly two abstract initial
families. For a legal static origin, lookup decides B membership: present
means already borrowed, absent means ordinary dynamic collection. Nonstatic
origins have no B row and use dynamic collection. No tainted exact S-root
case or abstract class origin is admitted. Incoming E/mx is irrelevant to
interest routing (`request_ignores_E_mx`); actual concrete answers still
require T. An excluded mark creates its request immediately, including an
already-excluded cleaner case. No mx subtraction is needed at union storage.

Frontend completeness of local *effective* mark contributors remains a
collector refinement. Both source and sink conditions, matched cleaners,
converters and excluded-target operations must use it. A field read contributes
no mark by itself and no field request is reconstructed anywhere in this proof.

## 9. Incremental replay, recursion and completion

**Claim.** Every finite arrival order delivers exactly the ordinary joins;
completion cannot ignore queued, active or newly created work.

For actual bindings I and available effects E, define the ordinary join as
all matching `(input,effect,ordinary output,events)` tuples. `inputArrives`
adds delta-I joined with all E. `effectArrives` adds all I joined with delta-E.
List distributivity proves `input_arrival_complete`,
`effect_arrival_complete` and, by induction, `incremental_closure`.
`both_arrival_orders` compares membership with the same full join, allowing
duplicate callbacks. `late_input_emits` and `late_effect_emits` provide actual
outputs. `delivered_has_ordinary_effect` supplies an ordinary small-step
witness; `emitted_has_registered_binding` prevents ambient/ZERO supplies.

Use this same two-sided construction for effective mark rows versus inputs
and actual concrete suppliers versus requested marks. Frozen B is visible
before input replay and sends no delta. Temporary commit contributes only
fresh dynamic marks. Every local request/source/sink event remains an effect
boundary. Recursive finite derivations are covered by §6, so emptiness of a
current summary set cannot be mistaken for a completed negative response.

`enqueue_accounted`, `take_accounted` and `finish_accounted` preserve
`reserved=queued+active`. `enqueue_before_release` proves children remain
reserved after the active parent finishes. `no_early_completion` proves
zero reservations iff neither queued nor active work remains. The actual
queue adapter must also count remote deliveries and flush pending commits.

Histories are ghost witnesses and may grow without bound around cycles.
Finite graph shape alone therefore proves neither finite runtime state nor
termination. For a finite normalized semantic quotient, monotone fresh
admission and fair counted processing reach its least closure. No bound on
wall time or on arbitrary projects follows from these proofs.

## 10. NORMAL persistence and later-run handoff

**Claim.** Choice of transport and identity compaction cannot change ordinary
NORMAL persistence or the data supplied to ordinary typed handoff.

The transport theorem preserves the complete result. Applying the same
writer-eligibility function to equal result relations therefore gives equal
records. More directly, `pure_identity_demand` proves every compacted record
is D; `normal_not_compacted` and `persistence_compaction_equal` prove that
filtering NORMAL records commutes with compaction. The eligibility function
stands for the existing structural record-format admission, not a new
first-run/exit/static filter. `normal_persistence` and `demand_not_persisted`
give positive and negative selected controls.

Static FLOW/defaults are D and never become NORMAL cache records. Independently
supported NORMAL concrete/nonstatic summaries use the ordinary writer. The
model needs no normal-exit bit and inserts no S-root initial.

Typed handoff is a deterministic projection of the same complete summary and
original-support relation, including relay-only owners. Equality is preserved
under that projection just as under observation projection. Its existing
STAR-to-ANY query projection does not alter executable AP payloads or initial
legality. This proves transport independence; it does not prove the later
strong engine or the current driver's owner inventory. Actual driver
enumeration must include every reached logical owner, not only materialized
reader seeds. Failed/OOM prefixes remain ineligible as completed results.

## 11. Constructive cost difference and practical relevance

Let n be a transparent chain's relay count and m its distinct full input
bindings. `chainWitness` constructs a real n-edge relay path;
`chain_ordinary_steps` proves n ordinary steps and
`chain_has_no_skipped_effect` proves the contracted path has zero real effects.
No meaningful instruction is removed to obtain that saving.

`ordinaryLayout` is an executable list with one binding at each of n+1
positions. `contractedLayout` has one shared n-edge route plus m entry and m
exit bindings. Length theorems give `(n+1)*m` versus `n+2*m` cells.
`concrete_chain_layout_reduction` proves strict reduction for n>=3,m>=2.
The declared traversal model is `n*m` versus `n+2*m`, strictly smaller for
n>=5,m>=2 (`chain_traversal_strict`). These are a concrete practical family,
not a theorem that the optimized procedure is better on every graph.

ThingsBoard has 3,797,487 static-abstract-origin caller groups (80.66% of
4,707,790), including 45,964 currently nonstatic groups whose real effects
must remain. This makes shared transparent transport relevant. The fraction
that is actually transparent is unmeasured; bound/transformed/TAINT inputs,
reader work, reverse fanout and shared route metadata still cost resources.
The model predicts no physical byte percentage or 60-second completion.

## 12. Refinement boundary and runtime adapter obligations

The checked result is sound and complete for the declared scalar instructions,
their full event results, lossless input coding, finite transport/mutual
derivations, symbolic mark lookup and relational identity quotient. It is not
a claim of whole-JVM soundness or a verified running optimized analyzer.

The following adapters are required for a runtime refinement:

1. Lower every actual local/call/exit rule, alias/model effect and RHS generation
   into the declared evaluator or an unchanged ordinary boundary. Reject unknown
   transparency; retain event-only outputs and pre-store ordering.
2. Produce complete reached ports/entries and legal coordinates from prescan
   and actual inputs. No activation-only callee initial or post-kill token.
3. Refine packed AP/summary storage and on-the-fly compaction to the proved
   full-payload/denotational relations, including broad suppliers and layers.
4. Compile whole ordinary NNF/ND/call-stage clients with the same occurrence
   and support keys. Prove positive snapshot extension where least-fixed-point
   scheduling is used; no negative conclusion from missing lazy state.
5. Refine actual publication, request barriers, owner locking/cancellation,
   counted work, persistence and typed handoff to the declared protocols.

These are implementation proof obligations, not unresolved choices of desired
behavior. The only policy ambiguity found during this review was diagnostic
callback-count equality; the user resolved it as recorded in §6 and the spec.
No additional runtime policy is silently selected by these formal proofs.

The runtime implementation now uses conservative scalar fallback at effect
boundaries and packed descriptors for certified identity members. Finite
differential, request-order, call-stage, whole-ND and persistence tests check
these adapters. This is evidence for the selected frontend and fixtures, not
a formal proof of all Kotlin paths, JVM lowering or concurrent executions.
The Lean theorem and declaration audits retain their original source scope.
