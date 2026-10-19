# Mark groups and direct statics: proof dossier

Date: 19 October 2026. Status: **specification and finite constructive
proofs; runtime implementation has not started**. This dossier supports
[grouped-run1-statics.md](grouped-run1-statics.md). It follows
[proof-guide.md](proof-guide.md). It uses ASD-STE100 Simplified Technical
English. Defined technical terms and program names keep their meanings.

## 1. Baseline and claim

The implementation base is `a80d5c580b3b4715ab39679fbf66f0f190125fa0`.
Its core and rules equal the measured `28a123f0eac937e8f2feca9ee5dd84e38fa8f979`
version. The [manifest](baselines/pre-bottom-up-a80d5c580.json) pins both
runtime trees, document blobs and document SHA-256 values. The spec's section
17 states every selected behavior change. Ordinary actual-query, ZERO,
full-mark primitive, ND and record-reader contracts remain in force.

The selected reference now treats `trackFactsReachAnalysisEnd` as empty in
every run, for method, entry and exit sinks. Use the same effective rule
policy in both compared analyzers. Source actions and sink predicates/seeds
remain enabled. End-fact-dependent paths are outside this iteration's claim.
Earlier general models may contain GEN examples; they do not require a
waiting GEN runtime component under the selected empty policy.

The main claim is **finite reference-witness inclusion**. Start with a
finite full-mark witness in the compiled reference semantics. Construct its
group candidate and the full backward replay. Retain all positive guards,
actual primitive relations and exact source leaves. This is stronger than
assuming that all necessary helper guides already exist. It does not prove
that a real JVM compiler emits the complete reference program.

The checked scope is a finite positive semantic program plus local signed
NNF, AP, call-port, cleaner and queue models. A reference witness contains
the successful operation and all its positive children. Negative residuals
use the separate possible-condition lemma; they are not positive source
roots. The compiler must retain their original full predicates for later
confirmation. The local NNF proof has one fixed observation position.
Multi-position binding is an explicit adapter contract.

No theorem below proves all JVM front ends, arbitrary exceptional flow,
unbounded execution, wall-clock time or a whole project finding set. The
unchanged pinned primitive assumptions remain assumptions. A completed
formal model is not a completed runtime implementation.

## 2. Concept and optimization

The concept changes run-1 precision: each full mark becomes its owner group;
static order/context becomes conservative; early static effects are DEMAND.
The NORMAL part excludes those speculative effects. Run-2 uses exact full
marks, real CFG/call transfer and strong writes. Equality of the old and new
run-1 NORMAL/DEMAND sets is not a requirement.

Within the selected concept, compare a scalar implementation with a merged
implementation. Both have the same rooted supply, conditional read edge,
current-point refinement, source identity and full backward semantics.
Merging requests/groups and contracting identity relays must preserve the
decoded semantic answer relation. Diagnostic callback counts can differ.

The pool can derive a conservative group combination absent from one exact
context. That is an admitted DEMAND candidate. It cannot invent a group
from a request, conditional initial or empty copy cycle. Later full replay
still requires the actual full primitive and all its guards. These are two
different soundness duties: run-1 coverage and exact later confirmation.

## 3. Requirement-to-proof map

Names in this table refer to constructive Lean declarations. All model links
are under [lean/ApSpec](lean/ApSpec). The audit checks owned helper
declarations as well as public theorems.

| Spec requirement | Checked construction | Scope and production contract |
| --- | --- | --- |
| Stable owner/full identity, section 3 | `RuleGroupMarks.own_valid`, `valid_pair_eq_iff_full`, `exact_selection_covered` | Provider adapter must register outer rule ownership before flattening; one join rule stays one group |
| Full identity after run-1 | `later_does_not_merge_full_marks`; `RuleGroupSchedule.extracted_complete` | Complete eligible catalog; no representative-only decoding |
| Positive schedule | `RuleGroupSchedule.limit_positive`, `full_from_run_two`, `no_strict_growth_first_boundary` | Driver must not stop at equal run-1/run-2 limits |
| Initial grammar, section 4 | `GroupedRun1Initials` parser; baseline `Run1InitialShapes` no-ANY/no-MUST and projection laws | Nine selected scalar families; root family removed; ordinary b excludes ZERO and cleaner temporary |
| Selective writes/empty E | `Run1StrongPreservation.definite_kill`, `exclusion_fallback`, `preservation_no_new_E`, `strong_write_survival_coverage` | Same baseline definite/may-alias classification; no generic weak-write rule |
| Tail-preserving layer demotion | `preservation_full_payload`; `Run1DefaultSummaryApplication.ordinary_root_STAR_layer_only` | Use the layer law only; do not restore the removed executable root default |
| Signed full literals/NNF | `RuleGroupConditions.exact_truth_implies_possible_truth`, `exact_full_literal_contradiction`, `same_group_distinct_pair_not_false` | Fixed-position NNF model; compiler keeps all actual positions and occurrence IDs |
| Selected cleaner repair | `RuleGroupMarks.exact_clean_partition`, `normal_complement_sound`; `RuleGroupRequestRepair.concrete_survivor_covered` | NORMAL complement plus DEMAND repair; later exact cleaning distinguishes T from U |
| RemoveAllMarks and real kills | `requested_input_reexecuted_through_remove_all_is_empty`; universal-carry `kill_still_kills` | Point reply reruns the operation; it cannot resume after a kill |
| Mixed old/new exclusions | `selector_inter_denotation`, `selector_union_denotation`, `inherited_group_is_not_a_single_full_exclusion` | Typed denotation, including native/reverse record readers |
| Merged ordinary requests | `RuleGroupRequestRepair.routeWitness`, `Handoff.survivorWitness`; initial-refinement merged join lemmas | Preserve baseline N2 actual inputs and complete ND original; no second input-history store |
| Rooted static group supply | `Run1StaticGroupDerivation.solution_exact`, `solutionRoot`, `solution_empty_without_seed` | Complete positive forward rule/support compiler; requests are not seeds |
| No static relay requirement | `compact_expanded_exact`, `strict_chain_saving` | Equality for the accepted identity-relay contraction scope, not old flow-sensitive context equality |
| Write-gated reads, section 8 | `Run1StaticReadAdmission` reached-read/write join and arrival controls; composition `rooted_write_admits_read` | Actual reached FLOW/group write; certified AP overlap/image; no prescan candidate admission |
| Original-slot/current-point refinement | `Run1StaticInitialRefinement.zero_keeps_static_premise`, `identity_refinement_commutes`, `materializationCertificate`, `materializationRoot` | Same operation input, group-refined premise, ordinary full guard/AP adapter |
| Merged static joins | `packed_scalar_equivalent`, `merged_arrival_commutes`, `merged_batch_associative`, `duplicate_batch_same` | Membership equality; not equal callback counts or arbitrary thread scheduling |
| Early source write and handoff | `Run1StaticEarlySummaryZero.writer_has_explicit_zero`; composition `reached_source_writer` | Raw/live ordinary summary publication; reached store and actual source permission/support |
| Static default, section 11 | `Run1UniversalStaticCarry.all_methods_same_view`, `emits_actual_full`, `every_finite_transport_path` | Implicit complete group catalog; no method-field product, no executable identity or taint creation |
| Actual queries/current publication | composition `actual_admission_and_publication`, `primitive_pair_executes`, `compileReplay` | Retain pinned StrongQueries and full current premise authorization; a query alone computes no result |
| ZERO wrappers/internal sinks | `Run1StaticEarlySummaryZero.runChain_result`, `nonzero_reach_no_zero_protocol`; composition `real_zero_wrapper_chain` | Actual balanced ports and local body survival; nonzero REACH cannot start unrelated ZERO discovery |
| Finite candidate and full replay | composition `compileCoverage`, `completeFiniteWitness`, `target_tree_is_admitted` | Constructed from one full positive tree; no premise that every helper guide exists |
| Exact source identity | composition `selected_source_confirms`, `collectAllSources`, `collect_visits_each_branch`, `matching_source_is_enumerated`, `source_leaf_keeps_every_occurrence` | Traverse all positive children and all matching source/action identities; retain exact full marks |
| NORMAL record export | cleaner NORMAL-complement law plus inherited pinned ordinary record contract | Export only complete independently valid NORMAL; new typed exclusion reader/repair checks required |
| Owner queues/completion | Existing request/register-before-snapshot contracts and finite join models | Atomic commit-before-send, counted work and cancellation remain implementation checks |

The composition module is
[GroupedRun1StaticComposition.lean](lean/ApSpec/GroupedRun1StaticComposition.lean).
The selected initial grammar is
[GroupedRun1Initials.lean](lean/ApSpec/GroupedRun1Initials.lean).

## 4. Constructive coverage argument

First, retain the reference witness's original full mark identities and
ordered positive children. `projectTree` maps each leaf/output to its group.
It keeps each node and every positive premise. Exact condition truth remains
possible under the signed-NNF group evaluator. A selected cleaner keeps a
group candidate even when it cannot distinguish a surviving full member.
A definite kill has no successful reference branch to project.

Second, solve the finite grouped positive program to its least fixed point.
`solution_contains_tree` includes the projected target and every projected
static subtree. `solution_exact` constructs a grouped derivation for every
solution member. With no source seeds the solution is empty. A membership
request is not a derivation. A copied static requires the source field;
a guarded source/writer requires all positive supports.

Third, construct a publication from a reference static-write subtree.
`publishWitness` returns that field/group row. `compileStaticCoverage`
extracts it from an actual static `Subtree` of the same full target tree.
Its publication also admits the compatible reached read. The local
read-admission model covers old/new
write/read orders and duplicate batches. A read cannot disappear because its
writer arrives late. For symbolic APs, the adapter must certify the actual
intersection and current image. It cannot use class-point equality as a
proof of field coverage.

Fourth, a surviving conditional FLOW edge keeps its original static slot.
Local copies and call/return bindings change its current image, not that
original. `identity_refinement_commutes` proves that a direct original-slot
answer has the same group/AP image as identity-path refinement. The joined
reply is enqueued at the operation input and executes that operation again.
The reply does not create a method-entry analysis or bypass a cleaner.

Fifth, `compileCoverage` constructs a supported target group permission,
all positive branch certificates and exact backward `Replay`. Each reverse
primitive returns only a declared exact full preimage. Its complete child
family remains required. Actual-query admission and publication remove the
need for a per-helper old-guide veto. Ghost productive pairs preserve proof
indices; they are not a new required runtime store or barrier pass.

Sixth, the implicit shared GROUP demand retains each actual static full query
through a finite sequence of real identity/copy bodies. It cannot execute
those bodies itself. Body kills still return no result. Productive nonstatic,
cross-group and control-ZERO results use the complete actual premise rule,
not an identity permission that pretends to describe them.

Finally, only an exact actual reversed source produces CONTROL_ZERO.
`sourceWitness` selects a registered eligible full source without classical
choice. `selected_source_confirms` retains the original query. `Replay`
confirms every source leaf. `compileExactSource` extracts one leaf as a
convenient witness; it is not the complete source set of an ND rule.
`collectAllSources` traverses every positive child. `collect_visits_each_branch`
proves that this traversal omits no child. `collected_sources_confirm`
preserves the registered exact source result for each returned leaf.
At a source leaf, `matchingSources` enumerates every registered eligible
source/action that produces that exact fact. `source_leaf_keeps_every_occurrence`
proves inclusion even when two actions have the same point and full mark.
The control keeps source IDs 1000 and 2000 without duplicating a taint fact.
The runtime must collect all exact hits with their original forward PathEdge
and issue the corresponding exact `Seed.Source` permissions.

This argument proves finite inclusion for the compiled semantic program.
The actual JVM/AP/call compiler contract is separate. It must not be replaced
by an assumption that the handoff already contains every needed demand.

## 5. ZERO, wrappers and the connected control

ZERO can activate a C.f-premised conditional summary only in run-1. In
run-2 and later, a C.f premise requires an actual matching C.f fact. The
ZERO wrapper protocol below applies to ZERO-premise summaries; it does not
replace static-premise matching. Shared static demands grant permission,
not a matching input fact.

For the user example, a getter reverses `return/T` to `C.f/T`. An internal
sink in a deep wrapper starts under real ZERO discovery. Its requirement
returns through balanced zero-only replies and ordinary wrapper body transfer.
No returned finding or dead-local summary is needed to activate the seed.
A preceding pass-only call receives the actual static query. The shared
demand and actual-query rules admit it; the real body preserves or kills it.
The actual setter reversal then reaches its original full source.

The `Controls.mixed_example` kernel theorem executes one connected finite
getter/wrapper/relay/setter scenario. It is an adapter-level example, not
the general JVM compiler theorem. The runner requires a registered unary
getter and the exact static read admission. Its registered caller corridor
links the wrapper-before port to the writer-after port. Each intervening
relay has its actual call port and a registered field-survival body. Relay
order must match the executed relay list. The connected example uses identity
relays; the separate universal theorem also covers copies.

Negative controls reject a missing field publication even when another rule
supports the target, a writer after the wrapper, a killing relay, wrong
relay order, an unregistered getter and an unchecked extra getter guard.
Arbitrary inventory jumps to a matching writer are not a valid substitute.

The all-positive-tree compiler is the general finite-program theorem.
The wrapper/default models prove the stated transport laws. Their composition
does not claim that every possible Java CFG has already been compiled and
verified. These scopes must remain visible in implementation review.

## 6. NORMAL soundness and record reuse

All grouped taint and static-dependent/early effects are DEMAND. Thus the
accepted static order/context approximation adds no such NORMAL record.
An independently valid generic NORMAL FLOW result keeps the pinned semantics.
For a selected cleaner, whole-group exclusion restricts its domain; it cannot
certify a full member it excludes. The exact-clean partition constructs the
separate survivor repair. The complete barrier must drain that repair.

Selective field writes retain definite kills, disjoint identity and the full
payload of demoted residuals. The existing strong-preservation model proves
survivor coverage and empty E for this phase. A later full query in excluded
group g cannot be closed by a NORMAL record that excludes g. Mixed selector
intersection preserves the actual denotation, not the raw integer keys.

These facts justify the new NORMAL-only export rule relative to the inherited
ordinary record contract. Production record indexing, native/reverse readers,
cache lifecycle and SourceHit export still need tests. The formal result does
not assert that those future Kotlin changes are already correct.

## 7. Savings and limits

For K distinct full marks with one owner, group storage uses one membership
instead of K. `homogeneous_strict_slot_saving` proves strict savings when
K is greater than one. More generally, the mark component for a fixed AP,
premise and support key has at most the number of distinct owner groups.
Do not merge unequal ND/call support merely because groups are equal.

For a pure chain with H identity static relays, the expanded scoped model
performs H relay steps; contraction has no relay rule. `compact_expanded_exact`
constructs both witness directions, and `strict_chain_saving` proves the
reduction when H is positive. This equality is within the accepted grouped
global-static concept. It does not equate the new run-1 candidates to the old
flow-sensitive candidate set.

Each region/group supply bit and continuation-region-image/group delivery
bit grows once. No method-by-field-by-group product, reverse static request
graph, method-entry replay wave or extra helper-permission pass is required.
Ordinary N2/ND storage remains. AP regions, distinct reached supports and
ordinary work can still be large. A whole-project strict memory improvement
and the 60-second run-1 engine target require measurement.

## 8. Active and historical evidence

The unified audit includes earlier models for regression and contrast. An
audited theorem does not make its old algorithm a selected requirement.

| Evidence | Use in this spec |
| --- | --- |
| Four `RuleGroup*` models | Active mark domain, cleaner, NNF and schedule laws |
| `Run1StaticGroupDerivation`, `Run1StaticInitialRefinement`, `Run1StaticReadAdmission` | Active rooted closure, direct join and read admission laws |
| `Run1StaticEarlySummaryZero`, `Run1UniversalStaticCarry` | Active ZERO and actual static transport laws |
| `GroupedRun1StaticComposition`, `GroupedRun1Initials` | Active combined finite program and selected initial grammar |
| `Run1StaticDemandTreeCoverage` | Full positive tree and ghost rule/premise certificates; no required runtime ghost-pair pass |
| `Run1GroupStaticHandoff` | Catalog decoding laws; old inventory-search solver is not selected |
| `Run1StaticDemandCoverage`, `Run1StaticReadWritePath` | Earlier sufficient guide/corridor constructions; do not require their helper-guide builder |
| `Run1StaticDemandGateGap`, `Run1StaticEarlySummaryGap` | Counterexamples to a strict old-guide veto; not demonstrated baseline runtime losses |
| `Run1InitialShapes`, `Run1StrongPreservation`, `Run1DefaultSummaryApplication` | Baseline shape, field-write and layer laws; remove the old root initial/executable default |

The older backward-provider/request-slice and post-baseline bottom-up runtime
algorithms are not certified as the selected implementation. Keep useful
pure laws; do not port their obsolete state or protocols. The spec's removal
table distinguishes baseline removal from post-baseline code not ported.

## 9. Reproduction and remaining gates

Run the following commands in `spec/lean`:

```sh
lake build
lake env lean grouped-run1-statics-audit.lean
```

The audit enumerates owned declarations, collects their transitive axioms,
and rejects every axiom except standard `propext` and `Quot.sound`.
In particular, it rejects `Classical.choice` and `sorryAx`. Controls execute
merged/scalar joins, exact/wrong groups, kills, late arrival, zero-only return,
source identity and the combined example. The evidence receipt pins source
hashes before and after the commands.

Lean 4.33.1 builds **159 jobs**. The unified audit checks **6,812 owned
declarations** across 20 modules, passes **461 controls**, and finds
**zero unexpected axioms**. These totals include the historical/ghost
comparison models listed in section 8. They are not a whole-engine claim.

The [validation receipt](/drive-testcomp/opentaint-go-rules/grouped-run1-statics-20261019/validation/20261019T002423.078469Z/receipt.json)
has SHA-256
`5cd85c97a5daba265700a2a6f834072f397435a75dc818b09333e224772302ed`.
It pins every Lean source, the aggregate, audit, toolchain and baseline
manifest. Sources stayed unchanged during the commands. The
[build log](/drive-testcomp/opentaint-go-rules/grouped-run1-statics-20261019/validation/20261019T002423.078469Z/build.log),
[audit log](/drive-testcomp/opentaint-go-rules/grouped-run1-statics-20261019/validation/20261019T002423.078469Z/audit.log)
and [baseline checks](/drive-testcomp/opentaint-go-rules/grouped-run1-statics-20261019/validation/20261019T002423.078469Z/baseline-checks.json)
retain the exact outputs. Baseline document hashes, both runtime trees and
the absence of current runtime edits all pass.

Independent subagent reviews checked the pin/compatibility map, removal
scope and the combined proof. The proof review first found an unregistered
getter, missing read gate and missing caller-order certificate in the example.
These controls were added and the final scoped review passed. The spec also
now distinguishes selected guidance from raw/live summary publication.
The final source-occurrence refinement adds all-match enumeration and keeps
two source action IDs for the same exact fact. Its new inclusion lemmas and
controls pass in the final audit above.

Before implementation acceptance, use the baseline common/JVM/application
gates. Add integration tests for every new adapter in section 16 of the spec.
Compare findings against the same reference and record reached/completed/
accepted runs, time, memory, summaries, stored memberships and queued work.
The existing 12 ZERO/StrongQueries runtime controls verify current behavior;
they do not implement this feature or constitute a new project run.

## 10. Measurement state

No analyzer measurement in this task uses the new design. The selected base
reached/completed/accepted **OWASP run-7/run-6/run-5**, **conductor run-2/run-1/
run-1**, **ThingsBoard run-1/none/none**, and **Stirling-PDF run-2/run-1/run-1**.
Conductor's base run-1 completed in 49.567 seconds.

The latest measured post-refactoring runtime remains
`63d5481e4e4b69ecf042c1149bb574ad40fd1624`: **OWASP run-7/run-6/run-5**,
**conductor run-1/none/none**, **ThingsBoard run-1/none/none**, and
**Stirling-PDF run-2/run-1/run-1**. These are different checkpoints. The
specification's section 18 retains the baseline timing and finding caveats.
