# Local static writers and summary aliases: proof dossier

Date: 22 October 2026. Contract:
[Run-1 local static writers and summary alias admission](run1-static-writers-and-summary-aliases.md).
Runtime base: `d7244c08cdaefbb157f42e4d89731d301b97e48f`.
The original proof delivery made no runtime change. See the subsequent
[implementation validation](../docs/reports/bidirectional-local-static-writers-aliases-validation-20261022.md).
Follow [proof-guide.md](proof-guide.md).

## 1. Proof boundary

There are two different comparisons:

1. **Writer protocol:** remove caller-created FLOW writer jobs while
   preserving local operations, ordinary input/request replay, summary
   promotion and computed ZERO/all-static group supply.
2. **Alias implementation:** implement the selected scalar no-memory-effect
   contract with a packed partition and persistent invocation flag.

The alias proof does not establish equality with the old guard that admits
every DEMAND result. That guard is explicitly replaced. The two changes
must not acquire an AP, field, rule or mark semantic change as a hidden
proof premise.

The checked models use finite derivation witnesses and concrete decidable
keys. They prove the local protocol, partition and invocation-state laws.
They do not model the whole Kotlin engine, every AP transfer, the JVM
interpreter, the scheduler, report confirmation or a 60-second bound.
Their production correspondence conditions below remain implementation
gates. This distinction is part of the specification, not a test waiver.

## 2. Constructive writer argument

Use these fixed reused contracts:

- Every promoted FLOW summary has a finite derivation from executed local
  transfers and ordinary caller composition. A promotion is not a new
  program write.
- The ordinary Base-request pipeline replays actual incoming group inputs
  for requests admitted after those inputs, and admits later inputs for
  old requests. A static-origin caller uses its existing saved point.
- Ordinary AP composition preserves every complete actual binding and its
  bounded image under the selected transfer rules. The source and output
  group of a mark-changing action remain declared by that action.
- Counted work and complete barriers process every admitted finite event
  chain. A missing group leaves dormant data, not an invented answer.

These are reused analyzer contracts. The new implementation must construct
their witnesses at the actual ports and operation stages. In particular,
it must not replace a field-sensitive image with a field-name-only match.

### P1. No registration from promotion

The promotion transition inserts a summary and performs its normal
publication/demand/supply processing. It does not change the local writer
set. Thus any number of wrapper promotions creates no new FLOW writer.
A local-result transition inserts into the writer set independently of
the summary insertion result. Equal summaries arriving first cannot block
the first local registration. Set admission makes repeated local results
idempotent. Local-result and equal-promotion arrival orders have the same
final summaries and local writers.

### P2. Construct a replacement for a caller writer job

Take one successful finite old supply derivation for output field f and
group g. Follow its ordinary summary derivation to the local FLOW operation
that writes f. Record the actual caller-to-callee Base bindings on this
route. An unanswered old job has no supply to preserve. Its request can
remain dormant under the existing protocol.

Construct the new work as follows:

```text
request f/g at the local writer
  -> Base request at the local operation's nonstatic original, if needed
  -> climb the finite ordinary input-binding route
  -> use its actual ZERO-rooted or static-pool group input
  -> replay group input down the same route
  -> execute the local bounded transfer
  -> promote its group result back through ordinary caller summaries
  -> publish the computed ZERO or all-static group supply
```

For a direct static-original operation, request its source static group and
compose the immutable local relation. No Base route is needed.
For n Base bindings, the route has n+1 endpoint memberships in request
order and n input-binding replay steps. The route has an explicit first
local transfer and actual endpoint; it is not an assumption that a request
can find an unspecified answer.

The model's `CallerWriter` contains that finite route as derivation
evidence. `replayCallerWriter` constructs the new group derivation.
`eliminateCallerWriters` recursively removes old caller-job steps from
any finite derivation. `includeNew` embeds each new derivation in the old
reference. Their composition proves equality of finite group closure in
the defined binding/local-transfer model.

The production adapter must extend the route construction through its
actual AP bindings, local copies, filters, summary stages and canonical
field covers. It must retain the complete path, mark exclusions and layer.
The theorem does not prove those Kotlin adapters by naming them.
The FLOW route model preserves the group at each transfer. Mark-changing
actions remain on the separate, unchanged computed-action/supply path;
their rule semantics and complete AP action adapters are not re-proved by
this FLOW closure theorem.

### P3. Actual supplies and empty cycles

A ZERO group result can supply its computed output. An all-static
conditional result requires a complete actual static binding. A hypothetical
Base/group result alone cannot supply the pool.

Each model group derivation ends at an actual source seed; extraction
recursively follows its input derivation. Therefore a network with no
seeds has no supplied group, even if copy relations form a cycle. A request
is not a source constructor. `set(src())` and `set(C.g)` construct different
supply endpoints, as required by the main specification.

### P4. Immutable writer validity

A writer stores the bounded scalar relation derived by its local operation.
The positive run-1 relation contains that witness after later insertions.
A merge or representation replacement that covers the old semantic
relation still covers this witness. The relation can therefore be used
without finding its exact old leaf in the current merged summary.

This proof uses semantic coverage, not exact leaf survival. It does not
permit ignoring the relation's tail, E, mx or layer. A group in mx remains
excluded from the transfer, even if a request for it was routed. Generic
fixed-group supply joins keep their actual binding checks.

### P5. Arrival order and work bound

Request and supply membership updates commute and are idempotent. The
existing service joins a new writer with existing requested groups and a
new request with existing writers. Thus either order enables the same
writer/group pair. An unrequested arrived group is not a reason to run all
writers. Keep the existing `req/reach/processed` implementation of these
logical bits and the once-per-writer/group admission rule.

For the wrapper example, promotions have zero writer effect. One local
relation with K requested groups therefore has at most K local writer/group
admissions, independent of wrapper count. This is a population bound, not
a whole-engine time theorem. Computed supply joins and summaries still cost
work and remain required.

## 3. Constructive alias argument

### P6. Scalar classification

The Boolean function first compares nonzero base and complete chain. FLOW
requires STAR in both field tails. TAINT requires equal actual marks and
does not inspect tails. It does not inspect layer, E or mx. All other
cases are effects. ZERO callee results are effects.

The model distinguishes GROUP from full marks and keeps full identities
separate. This checks the equality operator, but selects no later FULL
runtime change. Executable controls enumerate all 16 pairs of the four
tail kinds and check the selected FLOW and TAINT rules. These classifier
inputs do not expand the legal runtime fact grammar.

### P7. Packed and scalar alias selection agree

Decode each packed row to its actual path/mark payloads. Filter its marks
by the scalar predicate without changing the row's header. Decoding the
two packed selections equals scalar filtering.

The selections are exhaustive and disjoint by the Boolean predicate.
Their concatenation is a permutation of the original decoded payloads;
thus no leaf or multiplicity is lost. Each payload retains its exact
annotations. Mapping any supplied alias operation over the effect part
equals applying that operation only to effect leaves in the scalar walk.
Ordinary summary output and its selected demand callback use all leaves,
not just the effect part.

The production trie adapter must preserve this scalar correspondence. A
semantic subtraction, tail absorption across flags or one flag for a mixed
tree does not meet the theorem's premise.

### P8. Invocation provenance survives later stages

Classification uses the raw callee initial/output. Binding, cleaning,
rewriting, cuts and a saved/resumed call carry the flag; they do not rerun
classification on caller coordinates. An arg-to-return arm stays effect
even if both positions bind to one caller local. A new independent source
result gets its own effect classification.

The model carries the flag, caller layer and pending-demotion state through
an arbitrary finite stage list and a save/resume operation. The guard
depends only on the run-1 flag, so changing layer cannot bypass a no-effect
decision. Default unresolved/constructor classification is an explicit
existing producer policy; no new pass-rule classifier is proved or selected.
Later guards keep the old policy. Backward calls keep their unguarded walk.

### P9. Required semantic alias coverage

The selected contract concerns memory effect, not AP equality. An unchanged
argument arm does not copy its preservation result to aliases. Genuine
write arms, source outputs and return-base changes still receive aliases.
Ordinary results and their demand outputs remain available.

The reused alias contract must cover actual mutations at their executed
write arms and keep aliases of unchanged inputs. For TAINT, a tail change
alone is an abstract geometry change, not permission to infer a new memory
write. This is why the user-selected predicate ignores TAINT tails.

The local formal model proves implementation equivalence to that selected
scalar contract. It does not prove that the predicate alone establishes
unrestricted JVM alias soundness. Existing alias-analysis limits remain;
the real mutator, alias, tail-change and later-demand controls are required
production gates. Do not cite this proof as equality to the old DEMAND
alias closure or as a completed whole-engine no-FN proof.

### P10. Combined guidance and observation argument

The local-writer change preserves ordinary summary/demand publication and
computed supply. Its request replay must retain the same complete group
and AP coordinates needed by run-2. The alias implementation preserves
ordinary scalar results and demands while applying exactly the selected
alias rule. Their composition therefore preserves that selected handoff
contract, subject to the stated AP/request/producer correspondence gates.

Group closure alone does not prove this handoff claim. The production
adapter must also preserve the ordinary method summary/demand pairs at
each caller on a replacement route, and preserve the sink witness from
which run-2 starts. Retain the complete original, output AP, group and
selected layer before the existing handoff normalization. If a sink uses a
local value that never becomes a return summary, keep its ordinary witness
and the existing implicit static demand permission. Do not invent a
return summary or a new point-addressed demand store for that case.

These are A4/A8 obligations. The finite FLOW network has no program-point
or handoff transition and does not discharge them. The alias comparison
still uses the selected scalar contract when it checks these obligations.

This is not a proof that every old alias-generated false candidate remains.
The existing implicit static permission still applies to actual run-2
inputs. FULL marks match their run-1 group demands. No new run-1 S-root
initial, run-2 ZERO/static match or markless permission is introduced.

## 4. Formal files and audit

The constructive models are:

- [Run1OperationStaticWriters.lean](lean/ApSpec/Run1OperationStaticWriters.lean).
- [Run1SummaryAliasAdmission.lean](lean/ApSpec/Run1SummaryAliasAdmission.lean).

The focused [audit](lean/run1-static-writers-and-summary-aliases-audit.lean)
imports both modules, rejects any owned declaration that depends on an
axiom other than `propext` or `Quot.sound`, and evaluates their executable
controls. `Classical.choice`, `sorryAx`, native decision axioms and explicit
new axioms fail this gate. All existence claims above have finite route,
source or partition witnesses. The models do not enumerate runtime marks
or add a `groupAtoms` catalog.

The writer model's finite accepted-group lists describe an abstract matched
transfer domain. They are not new runtime key fields or cached member
arrays. Its operation ID is a producer witness that the admission-key
projection removes. Production retains the existing complete relation key;
it adds no rule census, `knownMembers` or per-operation key dimension.

```sh
cd spec/lean
lake build ApSpec.Run1OperationStaticWriters ApSpec.Run1SummaryAliasAdmission
lake env lean run1-static-writers-and-summary-aliases-audit.lean
```

### Checked declaration map

Names in this table use namespace `ApSpec.Run1OperationStaticWriters` for
P1--P5 and `ApSpec.Run1SummaryAliasAdmission` for P6--P8.

| Obligation | Checked witness or law |
| --- | --- |
| P1: operation-only admission | `promoted_no_writer`, `promoted_summary_retained`, `local_writer_even_after_promotion`, `local_admission_independent_of_freshness`, `local_promotion_both_orders` |
| P1: no operation-ID storage dimension | `operation_witness_not_key`, `equivalent_local_sites_one_writer` |
| P2: finite replacement route | `replayCallerWriter`, `replayJob`, `replay_has_finite_requests`, `eliminateCallerWriters`, `includeNew`, `finite_closure_equivalence` |
| P3: actual group derivation | `sourceWitness`, `no_source_free_cycles`, `computed_supply_requires_source`, `hypothetical_base_cannot_supply` |
| P4: immutable relation coverage | `covered_after_merge`, `covered_after_semantic_replacement`, `excluded_group_cannot_transfer` |
| P5: arrival order | `arrival_both_orders`, `request_once`, `arrive_once`, `request_not_supply`, `unrequested_arrival_not_ready`; existing three-set `ask_once` remains the writer/group admission reference |
| P6: exact selected classifier | `scalar_classifier_exact`, `classification_ignores_layer_and_headers`, `taint_classification_ignores_both_tails`, `distinct_actual_marks_are_effects`, `zero_summary_is_effect` |
| P7: lossless packed partition | `packed_noEffect_exact`, `packed_effect_exact`, `partition_lossless`, `partition_union`, `partition_disjoint`, `partition_preserves_leaf` |
| P7: scalar outputs and guidance | `scalar_packed_alias_equivalence`, `selected_scalar_packed_outputs`, `summary_retention_unchanged`, `demand_outputs_unchanged` |
| P8: call invocation state | `stages_keep_admission`, `stages_keep_caller_layer`, `save_resume_keeps_full_state`, `raw_effect_survives_binding`, `noEffect_stays_skipped_after_stages` |
| P8: explicit guard scope | `demand_does_not_bypass`, `unclassified_never_skips`, `later_guard_unchanged`, `selected_differs_from_old_demand_bypass` |
| P9--P10: production correspondence | The preservation arguments above plus A1--A9 below; no whole-engine theorem is claimed |

### Final check receipt

Lean version: **4.33.1**. The focused two-module build passes. The aggregate
`lake build ApSpec` passes all **171 jobs**. Its historical modules retain
their existing informational output and linter notes; the selected focused
modules compile without errors. The combined audit passes **1,490 owned
declarations** (759 writer, 731 alias) and **46 executable controls**
(19 writer, 27 alias). The writer module also contains 24 kernel-checked
example controls; the alias module contains the named control theorems.
Do not add overlapping control populations as independent tests.

Only `propext` and `Quot.sound` occur in allowed logical dependencies.
The finite closure equivalence, source extraction and semantic replacement
coverage proofs need no axioms. Runtime tests and project measurements are
not part of this specification-only delivery.

| Artifact | SHA-256 |
| --- | --- |
| `ApSpec/Run1OperationStaticWriters.lean` | `c6ccd86eacbfe57eaffa17a03d706dd2903a8c92b875b08f94ad76a9adf27acc` |
| `ApSpec/Run1SummaryAliasAdmission.lean` | `fc50eba187b0862c5334966f19ff7aa54ded23b90295c2a6117a4640698a0ec3` |
| `run1-static-writers-and-summary-aliases-audit.lean` | `121ca69dff4daec902180fe51d6f85e543c18b0674cb8c17a151c44e34fdac5b` |

## 5. Production correspondence gates

| Gate | Required production evidence |
| --- | --- |
| A1: local production | A completed permitted micro-edge/local action and actual filtered bounded field result; a completed conjunction matches only an unattributed descriptor with its exact target; no promotion, direct cleaner or reached-only descriptor may register |
| A2: independent admission | A promoted summary arriving first cannot suppress local writer admission; exact writer-key dedup remains separate |
| A3: immutable relation | Composition uses the complete original bounded relation after summary subsumption; no cumulative root retention or lost mx check |
| A4: request/input replay | Construct the actual Base route or static saved-point route for old call-writer work, including all arrival orders and multiple callers |
| A5: computed supply | Preserve ZERO and complete all-static group joins; a Base/group hypothesis or request alone supplies nothing |
| A6: structural partition | Decode optimized trie parts to exactly the scalar payload partition; retain every path/mark/tail/header and producing flag |
| A7: guard lifetime | Preserve raw classification through bindings, cleaners, rewriter, cuts and replies; source outputs use separate effect flags |
| A8: publication/handoff | Keep each replacement route's ordinary method summary/demand pairs, local sink witnesses, implicit static permission, source/sink identities and run-2 group matching under the selected contract |
| A9: unchanged scope | Existing FULL/native/reversed, field, rule, exact-class and ablation gates remain valid |

No production gate passes merely because its abstract counterpart compiled.
Implementation must run the complete focused validation table in section 10
of the specification, then the required existing and approved project gates.
Report latest reached/completed/accepted run for every project measurement.
