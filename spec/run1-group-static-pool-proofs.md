# Forward statics and group-demand coverage: proof dossier

Date: 18 October 2026. Status: **revised proposal; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined mathematical
terms and code names keep their stated meanings.

This dossier follows [proof-guide.md](proof-guide.md) and supports the
[static proposal](run1-group-static-pool.md). The user rejected backward
provider propagation inside run-1. Static reads now create conditional
ordinary forward edges only after a compatible FLOW or group-valued write
has been published. Reached reads without such a write stay pending. A
request uses an admitted edge's static initial to join
writer supply directly, then resumes at the current operation input.
The handoff also needs a forward group-guide closure: point refinement at
a sink alone does not supply the mark demands of getters and helpers.

The [immediate static-summary revision](run1-immediate-static-summaries.md)
separates another case: sink-origin wrapper requirements keep initial ZERO.
Their existing unrestricted backward publication and zero-only return need
no run-1 field carry demands. The actual-port carry theorem below remains
a sufficient construction for nonzero static-query guidance. It must not
be read as a requirement to add guides to every sink-origin wrapper.

The [universal review](run1-static-demand-completeness-review.md) now refutes
the stricter combination of per-method group gating and no static carry
authorization. A resolved pass-only callee needs a nonzero field query
but has no group demand. Current actual-input admission avoids this loss.
Neither a valid ZERO-wrapper theorem nor absence of another counterexample
establishes a general early-summary compiler theorem.

The selected [shared static GROUP default](run1-universal-static-carry.md)
closes this relay gate. It is implicit for all methods and matches actual
full queries against the complete group catalog. It is not a source-derived
per-field table or an executable identity. The local constructive proof
covers arbitrary finite same-group static identity/copy transport; productive
nonstatic, control-ZERO and cross-group results remain separate.

The selected static order/caller-context approximation stays in effect.
Every supplied group still needs a finite source/writer derivation.
Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
No analyzer implementation or project measurement is part of this work.

## 1. Concept and optimization

For a certified mark-preserving FLOW branch, the reference propagates a
compatible supplied field group through ordinary forward operations to S.
The direct procedure admits the read from a published write, then
propagates its conditional static-origin edge.
When S needs the group, it instantiates that edge at S from the same supply.

```text
reference:  C.f/G -> ordinary identity transports -> x/G at S
new:        C.f/FLOW -> ordinary identity transports -> x/FLOW at S
            + derived C.f/G + a request at S
            => C.f/G -> x/G at S [DEMAND]
```

Both procedures retain the same static initial, current AP and group in
this scope. The optimization changes when the group is instantiated. It
does not prove equality for arbitrary mark rewrites, kills or unresolved
guards. Those operations need the compiler contracts in section 7.

The gate intentionally removes conditional read edges and summaries for
fields with no published corresponding write. Equality is required for
supplied requested outcomes in the checked scope, not for every ungrounded
conditional FLOW summary of the former ungated procedure.

ZERO is control activation for a conditional static summary. It preserves
the static premise. It is not a supplied field group or a new source.

## 2. Active models and historical models

| Active model | Scope | Constructive result |
| --- | --- | --- |
| [Run1StaticReadAdmission.lean](lean/ApSpec/Run1StaticReadAdmission.lean) | Reached-read/published-write join, fresh admitted regions and group-publication invariant | No edge without a matching write, all arrival/batch orders, no group fabrication and no loss of supplied requested outputs |
| [Run1StaticDemandGateGap.lean](lean/ApSpec/Run1StaticDemandGateGap.lean) | Strict run-2 group-mark gate and selector-preserving handoff | Executable missing getter/helper demand; FLOW and normalization cannot supply G; G admits all its full members |
| [Run1StaticDemandCoverage.lean](lean/ApSpec/Run1StaticDemandCoverage.lean) | Forward original-slot masks, reached call bindings and frozen identity summary/point/writer rows | Equal eager/interface group guides; every nested helper has its group demand; field-specific carry without an S-root |
| [Run1StaticDemandTreeCoverage.lean](lean/ApSpec/Run1StaticDemandTreeCoverage.lean) | Finite positive full-mark witnesses, group projection and frozen guide pairs | Explicit demand witnesses for every rule/guard premise, including different input/output groups; separate target point demand |
| [Run1StaticReadWritePath.lean](lean/ApSpec/Run1StaticReadWritePath.lean) | Finite ordinary reverse paths through real local/call ports, factored static carry and a captured ordinary writer endpoint | Constructs every carry guide from frozen field/group supply and closed method metadata; retains exact moves, full mark and read origin; separates writer RHS permission from static identity |
| [Run1StaticEarlySummaryZero.lean](lean/ApSpec/Run1StaticEarlySummaryZero.lean) | Early static source effects and finite sink-origin wrapper paths under existing backward ZERO rules | Computes zero-only returns and surviving wrapper-body transfers without run-1 wrapper demands; distinguishes explicit ZERO exits from absent exits and rejects ZERO activation from nonzero queries |
| [Run1StaticEarlySummaryGap.lean](lean/ApSpec/Run1StaticEarlySummaryGap.lean) | Finite full source/sink flow through resolved empty static relays under two admission policies | Executable counterexample refutes universal strict local-gate coverage; actual-input admission preserves the same query through every finite empty relay chain |
| [Run1UniversalStaticCarry.lean](lean/ApSpec/Run1UniversalStaticCarry.lean) | Shared all-method static GROUP permission, canonical actual inputs and finite identity/copy bodies | Closes the prior relay counterexample; derives admission and publication from the shared view; keeps actual AP/full marks and real body kills |
| [Run1StaticInitialRefinement.lean](lean/ApSpec/Run1StaticInitialRefinement.lean) | Identity FLOW templates, ZERO call activation, direct supply/subscription joins and merged batches | Same identity result as forward instantiation, static-premise preservation, rooted DEMAND replies at the subscribed point, arrival/merge equivalence and no duplicate delivery |
| [Run1StaticGroupDerivation.lean](lean/ApSpec/Run1StaticGroupDerivation.lean) | Finite positive group rules, source seeds, transforms, multi-input guards and static relay contraction | Rooted exact closure, executable proof trees, no invented groups and equal projected reference/direct derivations |
| [Run1GroupStaticHandoff.lean](lean/ApSpec/Run1GroupStaticHandoff.lean) | Closed-catalog full-member decoding and finite writer/reversal search | Full-member B2 query coverage, original-read retention and intermediate writer guards |

The direct-refinement model reuses the source/hop `Derivation` type and root-extraction
lemmas from `Run1StaticGroupProtocol`. These witnesses describe forward
source-to-field supply. It does not use that module's backward request
actions, saturation procedure or provider-needs scheduler.

`Run1StaticGroupProtocol` and `Run1StaticGroupRequestSlice` remain historical
algorithm models. The previous 1,332-declaration/90-control receipt applies
to the rejected backward-provider revision. It does not certify this new
pipeline. The still older coarse FLOW-plus-need pool/trace models also stay
historical. The new focused audit has a separate command and receipt.

The [mark dossier](rule-group-mark-proofs.md) still supplies ownership,
full-literal identity, cleaner and group-decoder laws. Run-1 is GROUP/limit 1;
run-2 is BACKWARD/FULL/limit 1. No run or field limit 0 is admitted.

## 3. Write admission, conditional forward transport and ZERO

### 3.1. Reached read plus published write

The read-admission model stores reached templates and published write
coordinates. Coordinates stand for adapter-certified AP overlap cells.
`admitted_iff` proves the exact join:

```text
admitted(read) iff reached(read) AND publishedWrite(read.originalField)
```

`read_without_write_pending` and `no_write_no_edge` exclude conditional
read emission when no write exists. `admissionWitness` returns the admitted
template from the two concrete membership witnesses. `late_write_admits`,
`late_read_admits` and `read_then_write_same_state` cover both event orders.
The merge/packing laws preserve the join. `fresh_after_commit_empty` and
`no_duplicate_admissions` suppress repeated admissions.

Write presence does not imply group presence. `gate_does_not_fabricate_supply`
proves that the gate leaves the supplied group relation unchanged. A FLOW-
only write admits its read but cannot answer a group request by itself.
`gated_output_iff` extends the direct-refinement oracle with the write
condition. `gatedCertificate` still returns source-rooted group evidence.

The combined `Store` maintains `HasWriteForSupply`: every supplied field
atom has a corresponding published write coordinate. The empty store meets
this invariant. `publishGroup` requires a rooted derivation and commits
write presence before its group row. `publish_group_preserves_write_invariant`
and `publishGroupSafe` constructively preserve both event and source safety.
`gate_preserves_supplied_outputs` then proves that gating loses no supplied
requested identity-template output. It does not assume whole-engine
completeness or prove discovery of all producer operations.

The model's pending reads cover conditional dependencies only. ZERO,
control returns, independent source actions and negative/non-mark rule
handling remain outside this gate. The runtime adapter must preserve them.
A broad read may have several overlap cells; later writes admit new cells.
A single whole-read boolean does not implement this finite-cell oracle.

### 3.2. Ordinary transport after admission

`Template` stores the original field, current point and current AP.
`IdentityTransport` has same-value, local-copy and call-binding forms.
These forms are semantic certificates supplied by the reached-operation/AP
adapter. They are not a backward dependency graph.

`advance` changes only the point/current AP. `advance_preserves_original`
and `compose_preserves_original` prove that the original field survives
ordinary transport and a finite forward trace.

`identity_refinement_commutes` proves:

```text
materialize(advance(template), G)
    = advance(materialize(template, G))
```

This is the local equivalence needed for point injection. Its composition
retains the field coordinate across the getter's local copy and caller
return binding. It does not require a reverse traversal of those steps.

`applyConditionalFromZero` performs the same forward call binding.
`zero_keeps_static_premise` proves that its output still names C.f.
Registration changes no supplied groups. Conditional summary publication
therefore cannot manufacture a marked ZERO-to-value source.

The model takes reached call/return bindings as inputs. It does not prove
normal-return control reachability or actual JVM output projection. Those
remain explicit adapter obligations, including no normal continuation for
a nonreturning callee.

## 4. Direct join, soundness and completeness

A subscription contains one conditional template and one group. Its supply
key is exactly `(template.originalField, group)`. `direct_lookup_only`
proves this equality. No preceding statement or callee-return search is
part of the query.

`output_iff` proves the exact scalar oracle:

```text
emitted(edge) iff
    edge.layer = DEMAND
    AND its subscription is present
    AND its conditional template is registered
    AND its original field/group has supplied evidence
```

`refinement_complete` constructs the output from those three registered
inputs. Thus a late available answer is not lost. This is completeness for
registered, requested, group-preserving templates. It is not a theorem that
the real analyzer creates every required template or request.

`Safe` maps each supplied atom to a Type-valued source-rooted derivation.
`offerSafe`, `forwardWriterSafe`, `registerSafe`, `subscribeSafe` and
`mergeSafe` preserve it. An offered source requires real evidence; requests
and templates cannot add supply. `materializationCertificate` returns the
field derivation for each emitted edge. `materializationRoot` returns an
explicit registered source with the same group in this identity scope.

`no_source_no_output` proves that a safe state with an empty seed catalog
has no refined output. Registered FLOW, ZERO, requests and a source-less
copy cycle cannot bootstrap a group. `forwardWriter` publishes only from
an existing derivation through a registered forward writer/copy edge.

`materialize_demand` and `no_normal_output` exclude NORMAL replies.
`output_at_subscribed_point` preserves the current point and current AP.
The getter control injects at the consumer point 30 and emits no point-0
entry replay. Runtime need not store the formal derivation tree.

## 5. Merged storage, arrival orders and duplicates

The scalar executable oracle uses finite lists. `pack` and `merge` erase
duplicates. Production GroupSets can replace these lists under membership
refinement; they need not build a scalar object for every full mark.

`packed_scalar_equivalent`, `merged_arrival_commutes`,
`merged_batch_associative` and `duplicate_batch_same` prove equal semantic
outputs after packing, different batch grouping or duplicate batches.
`subscribe_groups_union` proves that a merged group request has the same
membership as the union of its offered groups.

`offer_subscription_arrivals` covers source-before-need and need-before-
source. `flow_subscription_arrivals` covers template-before-subscription
and subscription-before-template. `same_atoms_outputs` extends the result
to states with the same three registered relations.

`fresh_iff` selects only outputs not already delivered.
`fresh_after_commit_empty` proves that another drain after commit produces
no repeated answer. In the runtime, commit delivered bits before scheduling
owner callbacks and retain ordinary worklist deduplication too.

These are membership and sequential join laws. They do not prove the live
actor lock protocol, work reservations, cancellation or fairness. A complete
barrier must cover publication, registrations, supply, ordinary P/R and
point replies. Waiting subscriptions alone do not queue infinite work.

## 6. Writer groups and relay contraction

The reused positive calculus has explicit reached source/boundary seeds.
Every productive rule has nonempty premises. A conditional ordinary input
is a premise, not a root. All guard premises must be grounded before a
writer conclusion can supply a field group.

`solution_exact`, `solutionRoot`, `rootWitness` and `empty_seed_closure`
prove rooted least closure and executable source witnesses. `solve` fires
each positive rule at most once. This covers conditional groups, group-
changing forward operations and conjunctive guards at their stated points.
It is a finite oracle, not a proposed full-rule scan in the engine.

`compact_expanded_exact`, `contract` and `expand` preserve projected group
derivations when the only erased steps are static identity availability
relays. Productive local transfers, call bindings and guard premises stay
present. Both sides use the same accepted static order/context policy.

The new direct-refinement model proves only same-group writer composition.
The positive calculus handles the wider forward semantics. Their production
adapter must agree; no whole-engine compiler equivalence is assumed.

## 7. Required compiler and runtime controls

| Adapter | Required contract |
| --- | --- |
| Static reads | Register reached reads, admit only regions with a compatible published FLOW/group write, and emit legal DEMAND edges; no group is required for admission |
| Write events | Real published writer effects only; exclude prescan candidates, pass-through/identity/default summaries; group supply commits write presence first |
| Control and late admission | Pending reads do not block ZERO, normal returns or independent sources; late write admission publishes conditional summaries to existing call interests |
| Calls/returns | Publish conditional ordinary summaries; ZERO triggers only static-premised application, retains other guards/support and uses the real reached return binding |
| Frozen call ports | Retain caller before/after, callee entry/exit, target context and exit kind independently of marked run-1 subscriptions; real reverse paths use ordinary compatible call matching |
| Frozen static carries | Close the default-method set over all control-eligible methods and port endpoints; join it symbolically with grounded field/group supply; query permission never executes an identity through a kill |
| Writer guides | Capture the real FLOW writer row before static-output interception; generate its distinct paired original/RHS guide without requiring a run-1 normal-exit summary |
| FLOW refinement | Accept only a certified mark-preserving FLOW branch; preserve original scope, current image and operation input stage |
| Mark-changing actions | Register all required input-group interests at that action, then run the operation forward; never equate original g with rewritten h |
| Source/model/boundary supply | Validate reached permission and explicit groups; a hypothetical input or request is not an actual root |
| Ordinary writer input | Join the known original p/G through existing P/R; retain actual caller/source grounding before publishing C.f/G |
| Static-origin writers | Subscribe eagerly to bulk grounded original-field groups and execute the real forward store; no reverse consumer traversal through copies |
| NNF/ND | Keep original signed full literals, bound positions, all positive antecedents and ordinary support; late guard supply reevaluates retained ordinary rule state |
| Selected cleaners | Retain DEMAND group repair independently of NORMAL mx; a selected cleaner cannot kill the group |
| RemoveAll/field kills | Keep true-condition admission and real surviving geometry; no template may inject an old value across a whole-hit kill |
| AP scopes | Match at one location; partition supplied/delivered groups by overlap cell/current image so later regions are not suppressed; keep canonical EXACT/STAR tags and no ANY/S-root initial |
| Queues/storage | Refine the membership oracle with GroupSets, owner commits, counted callbacks, cancellation and a complete barrier |

These controls remain required before implementation. The Lean model does
not prove arbitrary AP projection, operation compilation or the existing
engine's summary composition. This dossier makes the practical proof
boundary explicit instead of treating those parts as proved assumptions
about the production analyzer.

## 8. Group-demand coverage and full-mark confirmation

The [coverage specification](run1-static-demand-coverage.md) defines the
strict mark gate. A group G demand admits an actual full mark T with
owner(T) = G. A FLOW demand or a G demand in another method is insufficient.
No exact full mark in the run-1 guide is required.

The demand-gap model proves that the previous proposal can leave the getter
and an ordinary identity helper without G guidance. A marked sink edge,
source group supply and abstract getter summary do not fill that gap.
`normalization_does_not_fill_gap` excludes a repair by AP normalization alone.

The forward interface model supplies the correction for identity FLOW.
`compileTrace`, `every_visited_mask` and `requiredGuide` construct frozen
group guides for every visited helper from actual roots and reached forward
call-input bindings. `eager_and_frozen_guides_equal` compares them with eager
same-group interface propagation. `explicit_group_emits_full` preserves an
incoming full mark, while `fullMemberQuery` decodes every eligible member.
`backward_patterns_have_group` retains G on both paired ends. Static carries
use actual field coordinates and require no marked S-root initial.

The actual-port corridor model covers the omitted static transport between
the read and write. `builder_defaults_closed` constructs method coverage
from primitive inventory metadata. `compileCorridor` derives a field/group
carry admission at every visited port; it does not assume each guide exists.
`compile_uses_real_moves` retains the exact local/call move list. Calls resume
before the real caller statement or enter the actual callee exit. The full
mark and original read remain unchanged. A compatible writer lookup is not
a path constructor.

`writerEndpointCoverage` derives the distinct ordinary writer pair from its
captured FLOW row and rooted forward input-interface trace. The store and
writer row must have matching primitive bindings. `coverReadToWriter`
composes this endpoint with a path indexed from the actual read to the actual
store. `static_identity_rejects_ordinary_output` proves why the carry pair
cannot replace the writer pair. Carry admission is query permission only;
ordinary reverse writes, kills and call matching still decide actual paths.

A source-generated writer also needs its real RHS/source point and helper
guides. The positive-witness model covers their group-demand construction
when those primitives are compiled. The corridor module's `reverseSource`
only checks an exact local source action and returns unmarked ZERO. It does
not prove a general RHS-to-source CFG interpreter or group-decorate ZERO.
The ordinary adapter must preserve real AP overlap, operation stages and
well-bracketed call bindings. This is coverage of every finite real compiled
route, not a claim that all composable inventory routes are feasible.

The ZERO-wrapper model proves the separate path in the new revision.
`runChain` computes actual zero-only replies followed by field-preserving
wrapper-body transfers. `runChain_result` and `runChain_matches_certificate`
retain the full sink requirement and real call-port list. No run-1 nonzero
wrapper guide is an input. Each body must preserve the requirement to its
entry; a definite overwrite blocks that publication. Getter and writer
nonzero queries keep their separate permissions. `writer_has_explicit_zero`
retains the writer demand's control ZERO exit. The source law requires the
actual full mark, keeps the original initial, and cannot turn a nonzero
query with current REACH into genuine ZERO discovery.

The positive-tree model extends the concept to group-changing rules and
multiple guard premises. It compiles a finite full witness to a grouped
witness, computes source-rooted forward closure, and freezes the backward
pair for every supported rule/premise. Its full-catalog construction retains
original rule and ordered premise indices. Group equality does not erase
full source/sink occurrence identity. A separate frozen point demand supplies
the actual target seed, including a source/check at the same stage.

These are discovery permissions. Exact full-mark preimages still need the
ordinary reverse primitive; a group guide cannot invent a full mark or an
ND confirmation. Paired membership is required at publication as well as
initial admission. Complete production interface collection, AP encoding,
internal-point starts and full-rule/IR compilation remain explicit adapters.

The reused handoff model then supplies full-member catalog decoding and
finite compatible writer lookup. `every_full_group_member_gets_seed`,
`open_writer_complete`, `compilePath`, `compileGraphPath` and
`every_finite_reference_trace_discovered` retain the original static read
and each intermediate writer guard in their finite inventory scope.
They start from an existing query; they did not prove its group admission.

Complete inventory enumeration is broader than marked witness coverage.
The new positive proof covers every real finite witness in its compiled
calculus. It does not say every false or unsupported speculative writer
candidate executes. No missing group demand can be replaced by a generic
unpruned-scope permission. Actual backward source permissions and later
forward confirmation still require the real path, full conditions and ND
support. This is not a checked complete JVM iteration.

## 9. Scoped savings and remaining cost

`request_has_one_lookup` and `request_independent_of_forward_depth` prove
that the defined refinement query has one direct field/group lookup for
any preceding ordinary identity-template trace. Its operation language has
no backward step or method-entry replay. This is a query-operation count,
not a proof of constant-time hashing, callback cost or total analysis time.

`identity_refinement_commutes` permits this answer placement without replay
of that trace. The reused relay contraction removes pure availability
steps. GroupSets replace the full-mark coordinate with group membership.

A read with no published matching write retains only a pending descriptor,
not its downstream conditional path edges, summaries or group interests.
This is a scoped workload saving; pending-read/index storage still costs
memory. The gate can admit a field with no actual group when a FLOW write
exists. That field may still generate conditional forward work.

The proposal retains static initial coordinates in ordinary productive
edges/summaries. It also stores field supply and point subscriptions.
Distinct field/current/point combinations and eager static-copy work remain
real costs. A new global static fan-out can still be large. No byte-saving,
project speedup or 60-second run-1 completion theorem is claimed.

## 10. Reproduction and checked evidence

Use Lean `leanprover/lean4:v4.33.1` in `spec/lean`:

```sh
lake build
lake env lean run1-static-demand-coverage-audit.lean
```

The focused audit visits all declarations owned by the eleven active models,
including generated helpers. It collects transitive theorem axioms and
admits only standard `propext` and `Quot.sound`. No classical choice,
`sorry` or added axiom is admitted. It then executes every named control.

Final check on 18 October 2026: **PASS**. The build completes **157 jobs**.
The active audit checks **4,201 declarations**, finds **zero unexpected
axioms**, and passes **299 executable controls**.

| Module | Audited declarations | Passing controls |
| --- | ---: | ---: |
| Run1StaticGroupDerivation | 495 | 35 |
| Run1StaticInitialRefinement | 300 | 28 |
| Run1StaticReadAdmission | 144 | 27 |
| Reused Run1GroupStaticHandoff | 445 | 23 |
| Run1StaticDemandGateGap | 328 | 27 |
| Run1StaticDemandCoverage | 473 | 20 |
| Run1StaticDemandTreeCoverage | 343 | 28 |
| Run1StaticReadWritePath | 572 | 33 |
| Run1StaticEarlySummaryZero | 474 | 34 |
| Run1StaticEarlySummaryGap | 244 | 25 |
| Run1UniversalStaticCarry | 383 | 19 |
| Total | 4,201 | 299 |

The earlier read-admission audit remains a narrower **1,384-declaration /
113-control** checkpoint. It does not prove the new demand-generation rule.
The counterexample and wider group-demand proofs have the new receipt below.

The [validation receipt](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/receipt.json)
has SHA-256 `04545b213180c2c9556a42567923ba24775c6582d61c3a3e0548cd6a567e5403`.
It binds stable proof-source hashes to the
[build log](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/build.log)
and [audit log](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/audit.log).
The four existing `CallZeroTest` and eight `StrongQueriesTest` runtime controls pass. The proposed early
write publication is not implemented. No project benchmarks were rerun. Latest measured run
status remains the one at the start of this dossier. These finite proofs
are not a certificate that the current Kotlin implementation has the new
snapshot compiler or complete JVM query coverage.
