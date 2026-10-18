# Rule-group marks: constructive proofs and their scope

Date: 18 October 2026. Status: **selected spec; analyzer unchanged**.
This dossier follows [proof-guide.md](proof-guide.md) and supports
[rule-group-marks.md](rule-group-marks.md). It uses ASD-STE100 Simplified
Technical English. Code names and mathematical terms keep their defined
meanings.

Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
No analyzer run measures this design. No runtime source was changed.

## 1. Concept and optimization

The concept changes run-1 precision. It uses group-valued DEMAND, whole-
group FLOW exclusion and repair. Run-2 has full marks at the same field
limit 1. Equality to the old full-mark run-1 summary set is not a claim.

Within this concept, the storage optimization merges equal group interests.
The reference makes one scalar offer for each selected cleaner. The merged
store removes duplicate groups. Both must decode the same answer relation.
Neither can use a request as evidence that an actual taint member exists.

The formal scope is finite marks, finite catalogs, unchanged-AP selected
cleaner sequences, signed NNF at one fixed AP/observation position and
executable local transition systems. This scope
includes the same-group cleaner case that makes whole-group exclusion
incomplete. It does not include a complete JVM or AP implementation proof.

## 2. Checked models

| Model | Executable operations | Main guarantee |
| --- | --- | --- |
| [RuleGroupMarks.lean](lean/ApSpec/RuleGroupMarks.lean) | Owner assignment, exact/group selectors, actual supply filtering, FLOW complement, cleaner, remove-all and finite group cohorts | Exact identity, local coverage, valid NORMAL restriction and scoped mark-slot savings |
| [RuleGroupRequestRepair.lean](lean/ApSpec/RuleGroupRequestRepair.lean) | Scalar/merged requests, cleaner sequences, batch joins and domain-to-query conversion | Same merged answer relation; every concrete survivor has a NORMAL or DEMAND route |
| [RuleGroupSchedule.lean](lean/ApSpec/RuleGroupSchedule.lean) | Positive run IDs, limits, group decoder, finite paths and local simulation | Correct schedule, complete full-member queries and constructive finite-path lifting |
| [RuleGroupConditions.lean](lean/ApSpec/RuleGroupConditions.lean) | Full-literal signed NNF, possible group evaluation and source/observation candidates | Exact condition truth remains possible; group-dependent candidates stay DEMAND |

Natural-number IDs model stable interned identities. `OwnedMark` and
`FullMark` are separate record types in the two models. The executable
`Handoff.toFull`/`fromFull` functions preserve both fields. Their round-trip
proofs connect the models; the query conversion also preserves its payload.

The model's `Selector.any` denotes the whole finite ordinary-mark universe
for local reasoning. It is not an extra legal run-1 taint value. ZERO is
outside that universe. Ordinary FLOW remains distinct from group taint.

## 3. Ownership and selection

`own` obtains a mark's group from one owner registry. `own_valid` proves the
result has that owner. `valid_same_full_same_pair` and
`valid_pair_eq_iff_full` prove that adding the owner preserves exact full
identity. This excludes two valid pairs with the same old full ID and
different owners.

`exact_selection_covered` proves that every exact selected member passes the
run-1 group test. `later_does_not_merge_full_marks` proves that later exact
selection distinguishes different full IDs, including IDs in one group.
`materialize_no_fabrication` proves that group filtering cannot add a member
absent from actual supply. `materialize_keeps_owners` preserves valid owners.

The registry is an input to these proofs. The proofs do not recover the
outer rule ID from a flattened generated name. The provider must build the
registry from creation provenance before flattening. One join rule has one
group. A specialization or generated state does not allocate another group.

For signed NNF, `exact_truth_implies_possible_truth` proves coverage by
structural induction over the unchanged formula. Positive full literals
test owner-group presence. Eligible residual negatives are possible, not
proofs of absence. The input is already folded on full literal identities.
All literals in this local model refer to the same fixed AP/observation
position. The adapter must retain each position in a real multi-position
condition. Contains(x,T) and Contains(y,T) are different literals when their
positions differ. The model does not replace the eligibility/compiler pass.

`scalar_group_query_eq_dedup` equates scalar group-presence queries with a
deduplicated group projection. `possible_eval_dedup_projection` lifts this
equality to the complete unchanged local NNF. Thus merging group membership
preserves the **group** evaluator, while the earlier exact-to-group theorem
proves coverage of the more precise evaluator. It does not prove equality
to the old full-mark evaluator.

`exact_full_literal_contradiction` proves P(T) AND NOT P(T) false.
`folded_false_stays_false` preserves a folded contradiction.
`same_group_distinct_pair_not_false` supplies a concrete T-without-U case
even when T/U share a group. `raw_contradiction_can_be_possible` exposes the
bug if residual projection runs before full signed folding.

`exact_reached_emits_demand` covers source and observation roles in this
group-dependent lane. `emitted_candidate_keeps_role_condition_and_demand`
retains the original full condition and DEMAND layer. It does not authorize
a confirmed finding or prove an ND tuple compatible. Ordinary non-mark
conditions and their compiler remain separate adapter obligations.

## 4. NORMAL exclusion and DEMAND repair

Let T belong to group g. The exact cleaner removes only T. The NORMAL FLOW
complement excludes all of g. This is safe for each admitted member m:

```text
NORMAL admits m
  => owner(m) != g
  => m != T
  => the exact cleaner preserves m
```

`normal_complement_sound` proves this implication with the incoming FLOW's
admitted relation. `complement_preserves_layer` also proves that exclusion
does not promote a DEMAND input to NORMAL. A group exclusion restricts a
valid NORMAL relation; it does not prove every excluded mark was killed.

There are two different DEMAND stages:

| Stage | Members | Proof |
| --- | --- | --- |
| Coarse run-1 repair | Actual supplied members in a requested group; this can still include T | `concrete_survivor_covered`, `normal_or_demand_exact_input` in `RuleGroupRequestRepair` |
| Full-mark replay after cleaning T | Actual admitted members of g other than T | `exact_clean_partition`, `partition_disjoint`, `cleaned_literal_never_repaired` in `RuleGroupMarks` |

The domain function `demandRepair` denotes the **second** row. The sequence
function `demandRepairs` denotes the **first** row. Filtering an incoming
concrete relation by `FlowAllows` is not permission to filter a request by
FLOW exclusions. `interest_ignores_exclusions` and
`excluded_group_still_requested` make the independent interest explicit.

For an unchanged-AP cleaner sequence, `normal_record_sound` proves that
every NORMAL member survives all exact cleaners. `concrete_survivor_covered`
proves that every exact survivor is in NORMAL or coarse DEMAND. The NORMAL
and DEMAND union can retain a killed T only in DEMAND. This is the intended
run-1 over-approximation.

`routeWitness` computes which channel covers a member. The
`Handoff.survivorWitness` function computes either a valid NORMAL member or
a full-member next-run query with its catalog-membership proof. It does not
choose a witness from a classical existence statement.

## 5. Cleaner and exclusion operations

`cleanGrouped` preserves a selected-cleaner group value.
`cleanGroupFact` preserves that value and makes its layer DEMAND. Their
coverage laws apply even when the catalog currently has one member.
They do not infer that a selected cleaner removes a whole group.

The user permits `RemoveAllMarks` to kill. The `removeAllActual`,
`removeAllGrouped` and `removeAllFlow` operations remove a whole-hit value
and preserve a disjoint value. The hit/miss laws and
`request_after_remove_all_is_empty` show that later group filtering does
not resurrect the killed value. The AP adapter must supply the correct
whole-hit/disjoint geometry and ordinary residual handling.

The exclusion union/intersection laws interpret excluded **members**.
Run-1 group exclusion sets use union for composition and intersection for
compatible alternatives. Later typed group/full filters must use semantic
intersection. In particular, Group(g) intersect Full(g,U) still excludes U.
Typed-ID intersection would incorrectly admit a NORMAL U. The mixed-filter
`selector_inter_denotation` theorem and controls check this case and the
disjoint-owner case. `mixed_union_absorption` proves whole-group subsumption;
`inherited_group_is_not_a_single_full_exclusion` rejects later narrowing.

## 6. Requests, batches and completion

`merged_requests_exact` proves that scalar and merged offers have the same
group membership. `merged_and_scalar_request_decode` proves the same answer
relation for actual supplies. `duplicate_interests_same_answers` gives
literal answer-list equality after deduplicating interests.

`supply_batch_distributes` handles a new supply batch. The semantic
`request_batch_distributes` law handles a new interest batch.
`both_arrival_routes` equates the new-request/old-supply join with the
new-supply/old-request join for each matching atom. These laws justify both
arrival orders when the scheduler joins every new batch against the other
side. They do not prove the Kotlin queue or cancellation implementation.
The lists are a finite join oracle, not a required retained input history.

No-fabrication and empty-supply controls permit an unanswered request.
Completion requires committed interests, all joins with available supply
and drained generated work. It does not require a taint answer to every
interest. A later supplier must still join the retained interest.

The request adapter must select the **original abstract FLOW slot**. It
must record the group interest even if exclusions or value deduplication
leave no new value. For group-taint input it must instead retain the local
forward-demand obligation. A group-taint original is not a backward request
carrier. These geometry, publication and lifecycle duties remain adapters.

## 7. Full-member run-2 and the schedule

`extracted_sound` and `extracted_complete` prove both directions for the
closed-catalog decoder:

```text
q in extract(catalog, groupDemand)
  iff q.mark in catalog
      and owner(q.mark) = groupDemand.group
      and q.payload = groupDemand.payload
```

The decoder constructs each full query. It does not depend on marks seen
in run-1, choose one representative or require an equal full-mark run-1
guide. `first_boundary_keeps_full_query` retains every original full query
through group abstraction and decoding. `later_catalog_member` shows why
catalog extension needs replay. `Handoff.fullQueryWitness` makes the domain-
to-decoder result executable without an exact run-1 guide.

Query payload contains immutable geometry/occurrence IDs, signed full
condition literals and whole support IDs. `extraction_payload_unchanged`
preserves them; the model does not prove their AP or ND interpretation.
`full_phase_rejects_group_wildcard` rejects concrete group-taint carriers
from run-2 onward. `ordinary_flow_is_not_a_group` still admits ordinary FLOW.

`zero_not_a_run`, `number_positive` and `limit_positive` exclude run/limit 0.
`limit_one_based_formula` gives `max(1,r-1)`. Direction alternates; full
mode starts at run-2. The first boundary has equal limits; strict growth
starts after run-2. A stop test based only on equal field limits is invalid.

Finite derivation lifts use primitive local step obligations, not a premise
that already asserts whole-engine coverage. `group_seed_derivation_coverage`
lifts a finite full-query derivation to the decoded seed set in the same
kernel. It does not equate forward and backward kernels.

`simulateFinitePath` computes a target path from executable seed and single-
step simulation witnesses. `scheduledRefinement` supplies the positive,
nondecreasing limits. It permits changed representations and equal limits.
Literal fact inclusion is not claimed when a different AP limit changes
geometry. The real forward/backward reversal and F72 primitive witnesses
must still be supplied. The bounded-step controls distinguish limits 1/2;
the representation-change control exercises an equal-limit local bridge.

## 8. Scoped cost result

`homogeneous_strict_slot_saving` compares a finite cohort of distinct full
IDs with the same owner. For m > 1 full marks, the exact representation has
m mark memberships and the grouped representation has one. They cover the
same scalar group-selection relation. Request deduplication also preserves
the decoded answer relation.

This is a mark-membership cost result for that cohort. It is not a byte,
allocation, CFG-work or elapsed-time result. Grouping can activate more
condition alternatives. The closed catalog remains metadata; decoding it
at each run-1 fact would lose the intended saving. New project measurements
must report the latest reached run and test the run-1 engine target.

## 9. Remaining adapters and acceptance gate

| Adapter | Required witness or check |
| --- | --- |
| Provider and owner catalog | Stable outer-rule provenance; every produced/tested/removed full mark included; SharedModel tagged separately; late extensions replay queries |
| Run-1 producers | Group-only taint values, always DEMAND, for initials, source/ZERO generation, calls, ND, copies and replay |
| AP and cleaner geometry | Legal initial grammar; F74/F75 action order; selective structural kills; Empty-E demotion; unchanged tail on mark projection |
| Signed conditions | Fold full literals before grouping; keep residual negatives, original NNF and bound occurrences; group equality cannot prove concrete truth or ND compatibility |
| Requests and owner queues | Original-slot routing; local demand recording even without value delta; merged commits; both arrival orders; fair complete barrier and cancellation |
| Persisted records | Group exclusion tested by owner in native/reversed readers; partial NORMAL domain cannot close excluded-group queries |
| Handoff and driver | All eligible full members admitted without exact F1 guide; AP/F72 reversal, source permissions and support retained; equal-limit first transition permitted |
| Proposed static pool | Direct read/write demand delivery and backward writer discovery; mark decoding alone does not reconstruct removed relay provenance |

These are implementation obligations. No theorem here proves all JVM
transfers, all NNF/ND derivations, confirmed reports, the new static pool,
termination or completion within 60 seconds. Independent later full-mark
validation remains required before a group candidate can confirm a finding.

## 10. Reproduction and evidence

Use Lean `leanprover/lean4:v4.33.1` from `spec/lean`:

```sh
lake env lean --version
lake build
lake env lean rule-group-marks-audit.lean
```

The [focused audit](lean/rule-group-marks-audit.lean) visits every declaration
owned by the four new modules, including generated helpers. It rejects
all axioms except the standard `propext` and `Quot.sound`. It also executes
every named positive/negative control. The final validation receipt records
the exact source hashes, tool version, commands, exit codes and log hashes.

Final result on 18 October 2026: **PASS**. `lake build` completes all 142
jobs. The focused audit checks **1,090 declarations**, finds **zero unexpected
axioms**, and executes **80 passing controls**. The only admitted axioms
are the standard `propext` and `Quot.sound`; no classical choice is admitted.

| Module | Audited declarations | Passing controls |
| --- | ---: | ---: |
| RuleGroupMarks | 365 | 42 |
| RuleGroupRequestRepair | 62 | 8 |
| RuleGroupSchedule | 450 | 20 |
| RuleGroupConditions | 213 | 10 |
| Total | 1,090 | 80 |

The [validation receipt](/drive-testcomp/opentaint-go-rules/rule-group-mark-abstraction-20261018/validation/20261018T184022.470021Z/receipt.json)
has SHA-256 `6562f277ab112916a81bf7ee56965abc98350f97fb88d34b1cb3c0a8909f3769`.
It binds the stable proof sources to the
[build log](/drive-testcomp/opentaint-go-rules/rule-group-mark-abstraction-20261018/validation/20261018T184022.470021Z/build.log)
and [audit log](/drive-testcomp/opentaint-go-rules/rule-group-mark-abstraction-20261018/validation/20261018T184022.470021Z/audit.log).
The earlier failed attempt remains in the evidence directory. Runtime tests
and project benchmarks were not rerun because the analyzer did not change.
