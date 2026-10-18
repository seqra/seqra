# Forward static edges and direct refinement: proof dossier

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
| [Run1StaticInitialRefinement.lean](lean/ApSpec/Run1StaticInitialRefinement.lean) | Identity FLOW templates, ZERO call activation, direct supply/subscription joins and merged batches | Same identity result as forward instantiation, static-premise preservation, rooted DEMAND replies at the subscribed point, arrival/merge equivalence and no duplicate delivery |
| [Run1StaticGroupDerivation.lean](lean/ApSpec/Run1StaticGroupDerivation.lean) | Finite positive group rules, source seeds, transforms, multi-input guards and static relay contraction | Rooted exact closure, executable proof trees, no invented groups and equal projected reference/direct derivations |
| [Run1GroupStaticHandoff.lean](lean/ApSpec/Run1GroupStaticHandoff.lean) | Closed-catalog full-member decoding and finite writer/reversal search | Full-member B2 query coverage, original-read retention and intermediate writer guards |

The new model reuses the source/hop `Derivation` type and root-extraction
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

## 8. Handoff and confirmation

The reused handoff model decodes every eligible full group member, not one
representative. `every_full_group_member_gets_seed`, `open_writer_complete`,
`compilePath`, `compileGraphPath` and
`every_finite_reference_trace_discovered` cover their finite catalog/trace
scope. Keep the original static read and each intermediate guard.

Writer lookup uses the complete inventory, not F1 supplied groups as a
negative filter. Openings are query-only DEMAND. Actual internal-point
query admission, ordinary incoming call-port expansion, session scope and
source permissions remain runtime adapters.

Later full forward analysis must reconstruct the real path and satisfy
the existing confirmation certificate. Neither a source-rooted run-1 group
in the approximated static pool nor an isolated writer query confirms a
finding. All static/group-dependent run-1 results remain DEMAND.

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
lake env lean run1-static-initial-refinement-audit.lean
```

The focused audit visits all declarations owned by the four active models,
including generated helpers. It collects transitive theorem axioms and
admits only standard `propext` and `Quot.sound`. No classical choice,
`sorry` or added axiom is admitted. It then executes every named control.

Final check on 18 October 2026: **PASS**. The build completes **150 jobs**.
The active audit checks **1,384 declarations**, finds **zero unexpected
axioms**, and passes **113 executable controls**.

| Module | Audited declarations | Passing controls |
| --- | ---: | ---: |
| Run1StaticGroupDerivation | 495 | 35 |
| Run1StaticInitialRefinement | 300 | 28 |
| Run1StaticReadAdmission | 144 | 27 |
| Reused Run1GroupStaticHandoff | 445 | 23 |
| Total | 1,384 | 113 |

The [validation receipt](/drive-testcomp/opentaint-go-rules/run1-static-read-admission-20261018/validation/20261018T220546.126961Z/receipt.json)
has SHA-256 `90fcb5276f600ae60c7db57d71b0dc2570307b7ce7cda4131f3f5c5eaccd89b6`.
It binds stable proof-source hashes to the
[build log](/drive-testcomp/opentaint-go-rules/run1-static-read-admission-20261018/validation/20261018T220546.126961Z/build.log)
and [audit log](/drive-testcomp/opentaint-go-rules/run1-static-read-admission-20261018/validation/20261018T220546.126961Z/audit.log).
No runtime tests or project benchmarks were rerun. Latest measured run
status remains the one at the start of this dossier.
